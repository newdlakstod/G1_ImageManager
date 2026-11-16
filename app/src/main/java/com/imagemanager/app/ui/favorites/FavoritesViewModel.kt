package com.imagemanager.app.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.imagemanager.app.data.MediaStoreRepository
import com.imagemanager.app.data.Photo
import com.imagemanager.app.ui.grid.PhotoPagingSource
import kotlinx.coroutines.flow.Flow

class FavoritesViewModel(repository: MediaStoreRepository) : ViewModel() {
    val photos: Flow<PagingData<Photo>> = Pager(
        config = PagingConfig(pageSize = 60, enablePlaceholders = false),
        pagingSourceFactory = { PhotoPagingSource(pageSize = 60, queryPage = repository::listFavorites) },
    ).flow
}
