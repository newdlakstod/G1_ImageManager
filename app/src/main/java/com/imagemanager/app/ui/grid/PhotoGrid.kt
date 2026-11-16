package com.imagemanager.app.ui.grid

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil.compose.AsyncImage
import com.imagemanager.app.data.Photo
import com.imagemanager.app.ui.common.FavoriteBadge
import com.imagemanager.app.ui.common.VideoBadge
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoGrid(
    photos: Flow<PagingData<Photo>>,
    onPhotoClick: (Photo) -> Unit,
    onPhotoLongPress: (Photo) -> Unit = {},
    refreshSignal: Int = 0,
) {
    val items = photos.collectAsLazyPagingItems()
    LaunchedEffect(refreshSignal) {
        if (refreshSignal != 0) items.refresh()
    }
    LazyVerticalGrid(columns = GridCells.Fixed(3)) {
        items(count = items.itemCount, key = items.itemKey { it.mediaId }) { index ->
            val photo = items[index]
            if (photo != null) {
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .padding(1.dp)
                        .combinedClickable(
                            onClick = { onPhotoClick(photo) },
                            onLongClick = { onPhotoLongPress(photo) },
                        ),
                ) {
                    AsyncImage(
                        model = photo.uri,
                        contentDescription = photo.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (photo.isFavorite) {
                        FavoriteBadge(modifier = Modifier.align(Alignment.TopStart))
                    }
                    if (photo.isVideo) {
                        VideoBadge(modifier = Modifier.align(Alignment.BottomEnd))
                    }
                }
            }
        }
    }
}
