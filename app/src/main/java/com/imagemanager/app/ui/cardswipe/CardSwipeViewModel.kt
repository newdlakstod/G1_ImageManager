package com.imagemanager.app.ui.cardswipe

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.imagemanager.app.data.LastPositionStore
import com.imagemanager.app.data.Photo
import com.imagemanager.app.data.PhotoActions
import com.imagemanager.app.data.db.PileDao
import com.imagemanager.app.data.db.PileEntity
import com.imagemanager.app.ui.piles.PilePaths
import com.imagemanager.app.ui.piles.syncPilesWithGalleryFolders
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CardSwipeUiState(
    val photos: List<Photo> = emptyList(),
    val currentIndex: Int = 0,
    val currentPileId: Long? = null,
    val lastTrashed: Photo? = null,
) {
    val currentPhoto: Photo? get() = photos.getOrNull(currentIndex)
    val isExhausted: Boolean get() = currentIndex >= photos.size
}

class CardSwipeViewModel(
    private val photoActions: PhotoActions,
    private val pileDao: PileDao,
    initialPhotos: List<Photo>,
    private val lastPositionStore: LastPositionStore? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(CardSwipeUiState(photos = initialPhotos))
    val state: StateFlow<CardSwipeUiState> = _state

    val piles: StateFlow<List<PileEntity>> = pileDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _trashConfirmedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val trashConfirmedEvents: SharedFlow<Unit> = _trashConfirmedEvents

    private val _favoriteToggledEvents = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val favoriteToggledEvents: SharedFlow<Boolean> = _favoriteToggledEvents

    private var pendingConfirmation: (() -> Unit)? = null

    init {
        lastPositionStore?.load()?.let { mediaId -> jumpTo(mediaId) }
        viewModelScope.launch {
            syncPilesWithGalleryFolders(pileDao, photoActions)
        }
    }

    fun moveNext() {
        if (_state.value.currentIndex < _state.value.photos.size) advance()
    }

    fun movePrevious() {
        val current = _state.value
        if (current.currentIndex > 0) setState(current.copy(currentIndex = current.currentIndex - 1))
    }

    fun jumpTo(mediaId: Long) {
        val index = _state.value.photos.indexOfFirst { it.mediaId == mediaId }
        if (index >= 0) setState(_state.value.copy(currentIndex = index))
    }

    fun jumpToIndex(index: Int) {
        val current = _state.value
        val clamped = index.coerceIn(0, (current.photos.size - 1).coerceAtLeast(0))
        setState(current.copy(currentIndex = clamped))
    }

    fun selectPile(pileId: Long) {
        _state.value = _state.value.copy(currentPileId = pileId)
    }

    fun createAndSelectPile(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = pileDao.insert(
                PileEntity(
                    name = name.trim(),
                    folderRelativePath = PilePaths.relativePathFor(name),
                    createdAt = System.currentTimeMillis(),
                ),
            )
            selectPile(id)
        }
    }

    fun requestTrash(onNeedsConfirmation: (IntentSender) -> Unit) {
        val photo = _state.value.currentPhoto ?: return
        val sender = photoActions.requestTrash(listOf(photo.mediaId))
        pendingConfirmation = {
            removeCurrentPhoto()
            _state.value = _state.value.copy(lastTrashed = photo)
            _trashConfirmedEvents.tryEmit(Unit)
        }
        onNeedsConfirmation(sender)
    }

    fun undoLastTrash(onNeedsConfirmation: (IntentSender) -> Unit) {
        val photo = _state.value.lastTrashed ?: return
        val sender = photoActions.requestUntrash(listOf(photo.mediaId))
        pendingConfirmation = { restoreTrashedPhoto(photo) }
        onNeedsConfirmation(sender)
    }

    fun dismissUndo() {
        if (_state.value.lastTrashed != null) {
            _state.value = _state.value.copy(lastTrashed = null)
        }
    }

    private fun restoreTrashedPhoto(photo: Photo) {
        val current = _state.value
        val updated = current.photos.toMutableList()
        updated.add(current.currentIndex.coerceIn(0, updated.size), photo)
        setState(current.copy(photos = updated, lastTrashed = null))
    }

    fun requestMoveToCurrentPile(
        onNeedsConfirmation: (IntentSender) -> Unit,
        onNoPileSelected: () -> Unit,
    ) {
        val photo = _state.value.currentPhoto ?: return
        val pile = piles.value.find { it.pileId == _state.value.currentPileId }
        if (pile == null) {
            onNoPileSelected()
            return
        }
        val sender = photoActions.movePhotoToPile(photo.mediaId, pile)
        if (sender == null) {
            removeCurrentPhoto()
        } else {
            pendingConfirmation = { removeCurrentPhoto() }
            onNeedsConfirmation(sender)
        }
    }

    fun toggleFavorite(onNeedsConfirmation: (IntentSender) -> Unit) {
        val photo = _state.value.currentPhoto ?: return
        toggleFavoriteFor(photo.mediaId, onNeedsConfirmation)
    }

    fun toggleFavoriteFor(mediaId: Long, onNeedsConfirmation: (IntentSender) -> Unit) {
        val photo = _state.value.photos.find { it.mediaId == mediaId } ?: return
        val newFavorite = !photo.isFavorite
        val sender = photoActions.toggleFavorite(mediaId, newFavorite)
        if (sender == null) {
            applyFavoriteFor(mediaId, newFavorite)
        } else {
            pendingConfirmation = { applyFavoriteFor(mediaId, newFavorite) }
            onNeedsConfirmation(sender)
        }
    }

    fun onConfirmationResult(confirmed: Boolean) {
        if (confirmed) pendingConfirmation?.invoke()
        pendingConfirmation = null
    }

    private fun applyFavoriteFor(mediaId: Long, favorite: Boolean) {
        val current = _state.value
        val index = current.photos.indexOfFirst { it.mediaId == mediaId }
        if (index < 0) return
        val updated = current.photos.toMutableList()
        updated[index] = updated[index].copy(isFavorite = favorite)
        setState(current.copy(photos = updated))
        _favoriteToggledEvents.tryEmit(favorite)
    }

    private fun advance() {
        setState(_state.value.copy(currentIndex = _state.value.currentIndex + 1))
    }

    private fun removeCurrentPhoto() {
        val current = _state.value
        val index = current.currentIndex
        if (index !in current.photos.indices) return
        val updated = current.photos.toMutableList()
        updated.removeAt(index)
        setState(current.copy(photos = updated))
    }

    private fun setState(newState: CardSwipeUiState) {
        _state.value = newState
        newState.currentPhoto?.let { lastPositionStore?.save(it.mediaId) }
    }
}
