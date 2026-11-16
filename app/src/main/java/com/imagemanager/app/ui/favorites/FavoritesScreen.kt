package com.imagemanager.app.ui.favorites

import androidx.compose.runtime.Composable
import com.imagemanager.app.data.Photo
import com.imagemanager.app.ui.grid.PhotoGrid

@Composable
fun FavoritesScreen(viewModel: FavoritesViewModel, onPhotoClick: (Photo) -> Unit) {
    PhotoGrid(photos = viewModel.photos, onPhotoClick = onPhotoClick)
}
