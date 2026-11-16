package com.imagemanager.app.ui.grid

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil.compose.AsyncImage
import com.imagemanager.app.data.Photo
import kotlinx.coroutines.flow.Flow

@Composable
fun PhotoGrid(photos: Flow<PagingData<Photo>>, onPhotoClick: (Photo) -> Unit) {
    val items = photos.collectAsLazyPagingItems()
    LazyVerticalGrid(columns = GridCells.Fixed(3)) {
        items(count = items.itemCount, key = items.itemKey { it.mediaId }) { index ->
            val photo = items[index]
            if (photo != null) {
                AsyncImage(
                    model = photo.uri,
                    contentDescription = photo.displayName,
                    modifier = Modifier
                        .padding(1.dp)
                        .clickable { onPhotoClick(photo) },
                )
            }
        }
    }
}
