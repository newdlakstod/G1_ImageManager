package com.imagemanager.app.ui.cardswipe

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.imagemanager.app.data.Photo
import com.imagemanager.app.data.PhotoActions
import com.imagemanager.app.data.db.PileDao
import com.imagemanager.app.data.db.PileEntity
import com.imagemanager.app.ui.piles.PilePaths
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CardSwipeUiState(
    val photos: List<Photo> = emptyList(),
    val currentIndex: Int = 0,
    val currentPileId: Long? = null,
) {
    val currentPhoto: Photo? get() = photos.getOrNull(currentIndex)
    val isExhausted: Boolean get() = currentIndex >= photos.size
}

class CardSwipeViewModel(
    private val photoActions: PhotoActions,
    private val pileDao: PileDao,
    initialPhotos: List<Photo>,
) : ViewModel() {

    private val _state = MutableStateFlow(CardSwipeUiState(photos = initialPhotos))
    val state: StateFlow<CardSwipeUiState> = _state

    val piles: StateFlow<List<PileEntity>> = pileDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var pendingConfirmation: (() -> Unit)? = null

    fun moveNext() {
        if (_state.value.currentIndex < _state.value.photos.size) advance()
    }

    fun movePrevious() {
        val current = _state.value
        if (current.currentIndex > 0) _state.value = current.copy(currentIndex = current.currentIndex - 1)
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
        pendingConfirmation = { advance() }
        onNeedsConfirmation(sender)
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
            advance()
        } else {
            pendingConfirmation = { advance() }
            onNeedsConfirmation(sender)
        }
    }

    fun toggleFavorite(onNeedsConfirmation: (IntentSender) -> Unit) {
        val photo = _state.value.currentPhoto ?: return
        val newFavorite = !photo.isFavorite
        val sender = photoActions.toggleFavorite(photo.mediaId, newFavorite)
        if (sender == null) {
            applyFavorite(newFavorite)
        } else {
            pendingConfirmation = { applyFavorite(newFavorite) }
            onNeedsConfirmation(sender)
        }
    }

    fun onConfirmationResult(confirmed: Boolean) {
        if (confirmed) pendingConfirmation?.invoke()
        pendingConfirmation = null
    }

    private fun applyFavorite(favorite: Boolean) {
        val current = _state.value
        val index = current.currentIndex
        val updated = current.photos.toMutableList()
        updated.getOrNull(index)?.let { updated[index] = it.copy(isFavorite = favorite) }
        _state.value = current.copy(photos = updated)
    }

    private fun advance() {
        _state.value = _state.value.copy(currentIndex = _state.value.currentIndex + 1)
    }
}
