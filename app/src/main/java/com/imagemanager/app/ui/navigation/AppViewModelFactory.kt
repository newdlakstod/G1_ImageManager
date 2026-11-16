package com.imagemanager.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.imagemanager.app.ImageManagerApp
import com.imagemanager.app.data.Photo
import com.imagemanager.app.ui.cardswipe.CardSwipeViewModel
import com.imagemanager.app.ui.favorites.FavoritesViewModel
import com.imagemanager.app.ui.piles.PileViewModel
import com.imagemanager.app.ui.similar.SimilarGroupsViewModel

class AppViewModelFactory(
    private val app: ImageManagerApp,
    private val cardSwipePhotos: List<Photo> = emptyList(),
    private val restorePosition: Boolean = false,
    private val refreshCardSwipePhotos: () -> List<Photo> = { cardSwipePhotos },
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = when (modelClass) {
        FavoritesViewModel::class.java ->
            FavoritesViewModel(app.mediaStoreRepository) as T
        PileViewModel::class.java ->
            PileViewModel(app.database.pileDao(), app.mediaStoreRepository) as T
        SimilarGroupsViewModel::class.java ->
            SimilarGroupsViewModel(app.duplicateScanner, app.database.duplicateGroupDao()) as T
        CardSwipeViewModel::class.java ->
            CardSwipeViewModel(
                app.mediaStoreRepository,
                app.database.pileDao(),
                cardSwipePhotos,
                lastPositionStore = if (restorePosition) app.lastPositionStore else null,
                refreshPhotosProvider = refreshCardSwipePhotos,
            ) as T
        else -> throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
    }
}
