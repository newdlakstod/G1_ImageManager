package com.imagemanager.app.ui.favorites

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.imagemanager.app.data.MediaStoreRepository
import com.imagemanager.app.data.Photo
import com.imagemanager.app.ui.grid.PhotoPagingSource
import kotlinx.coroutines.flow.Flow

class FavoritesViewModel(private val repository: MediaStoreRepository) : ViewModel() {
    val photos: Flow<PagingData<Photo>> = Pager(
        config = PagingConfig(pageSize = 60, enablePlaceholders = false),
        pagingSourceFactory = { PhotoPagingSource(pageSize = 60, queryPage = repository::listFavorites) },
    ).flow

    private var pendingApplied: (() -> Unit)? = null

    fun removeFavorite(photo: Photo, onNeedsConfirmation: (IntentSender) -> Unit, onApplied: () -> Unit) {
        val sender = repository.toggleFavorite(photo.mediaId, false)
        if (sender == null) {
            onApplied()
        } else {
            pendingApplied = onApplied
            onNeedsConfirmation(sender)
        }
    }

    fun onConfirmationResult(confirmed: Boolean) {
        if (confirmed) pendingApplied?.invoke()
        pendingApplied = null
    }
}
