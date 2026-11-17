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

/** A photo that was just moved into a pile; [originalPath] is where it came from, used by undo. */
data class PhotoMoved(val photo: Photo, val pileName: String, val originalPath: String?)

data class CardSwipeUiState(
    val photos: List<Photo> = emptyList(),
    val currentIndex: Int = 0,
    val currentPileId: Long? = null,
    val lastMoved: PhotoMoved? = null,
) {
    val currentPhoto: Photo? get() = photos.getOrNull(currentIndex)
    val isExhausted: Boolean get() = currentIndex >= photos.size
}

class CardSwipeViewModel(
    private val photoActions: PhotoActions,
    private val pileDao: PileDao,
    initialPhotos: List<Photo>,
    private val lastPositionStore: LastPositionStore? = null,
    private val refreshPhotosProvider: () -> List<Photo> = { initialPhotos },
) : ViewModel() {

    private val _state = MutableStateFlow(CardSwipeUiState(photos = initialPhotos))
    val state: StateFlow<CardSwipeUiState> = _state

    val piles: StateFlow<List<PileEntity>> = pileDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _trashConfirmedEvents = MutableSharedFlow<Photo>(extraBufferCapacity = 1)
    val trashConfirmedEvents: SharedFlow<Photo> = _trashConfirmedEvents

    private val _favoriteToggledEvents = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val favoriteToggledEvents: SharedFlow<Boolean> = _favoriteToggledEvents

    private val _pileMovedEvents = MutableSharedFlow<PhotoMoved>(extraBufferCapacity = 1)
    val pileMovedEvents: SharedFlow<PhotoMoved> = _pileMovedEvents

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

    /**
     * Re-queries MediaStore for the current source (all photos / a pile / a group) and
     * replaces the in-memory list with it, keeping the current photo in view by mediaId
     * where possible. Without this, a photo that Google Photos sync restores after being
     * deleted (or any other out-of-app change) never reappears, since [initialPhotos] is
     * only fetched once when this ViewModel is created.
     */
    fun refresh() {
        val latest = refreshPhotosProvider()
        val current = _state.value
        val currentMediaId = current.currentPhoto?.mediaId
        val matchedIndex = currentMediaId?.let { id -> latest.indexOfFirst { it.mediaId == id } } ?: -1
        val newIndex = (if (matchedIndex >= 0) matchedIndex else current.currentIndex)
            .coerceIn(0, (latest.size - 1).coerceAtLeast(0))
        setState(current.copy(photos = latest, currentIndex = newIndex))
    }

    // No undo for trash: the photo stays recoverable from the device trash instead.
    fun requestTrash(onNeedsConfirmation: (IntentSender) -> Unit) {
        val photo = _state.value.currentPhoto ?: return
        val sender = photoActions.requestTrash(listOf(photo.mediaId))
        pendingConfirmation = {
            removeCurrentPhoto()
            _trashConfirmedEvents.tryEmit(photo)
        }
        onNeedsConfirmation(sender)
    }

    fun undoLastMove(onNeedsConfirmation: (IntentSender) -> Unit) {
        val moved = _state.value.lastMoved ?: return
        val originalPath = moved.originalPath ?: return
        // Moving back is just another move, to a throwaway "pile" pointing at the original folder.
        val origin = PileEntity(name = "", folderRelativePath = originalPath, createdAt = 0L)
        val sender = photoActions.movePhotoToPile(moved.photo.mediaId, origin)
        if (sender == null) {
            restoreMovedPhoto(moved.photo)
        } else {
            pendingConfirmation = { restoreMovedPhoto(moved.photo) }
            onNeedsConfirmation(sender)
        }
    }

    fun dismissUndo() {
        if (_state.value.lastMoved != null) {
            _state.value = _state.value.copy(lastMoved = null)
        }
    }

    private fun restoreMovedPhoto(photo: Photo) {
        val current = _state.value
        val updated = current.photos.toMutableList()
        updated.add(current.currentIndex.coerceIn(0, updated.size), photo)
        setState(current.copy(photos = updated, lastMoved = null))
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
        val moved = PhotoMoved(photo, pile.name, photo.relativePath)
        if (sender == null) {
            onMoved(moved)
        } else {
            pendingConfirmation = { onMoved(moved) }
            onNeedsConfirmation(sender)
        }
    }

    private fun onMoved(moved: PhotoMoved) {
        removeCurrentPhoto()
        _state.value = _state.value.copy(lastMoved = moved)
        _pileMovedEvents.tryEmit(moved)
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
