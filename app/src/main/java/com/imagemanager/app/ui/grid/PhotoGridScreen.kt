package com.imagemanager.app.ui.grid

import androidx.compose.runtime.Composable
import com.imagemanager.app.data.Photo

@Composable
fun PhotoGridScreen(viewModel: PhotoGridViewModel, onPhotoClick: (Photo) -> Unit) {
    PhotoGrid(photos = viewModel.photos, onPhotoClick = onPhotoClick)
}
