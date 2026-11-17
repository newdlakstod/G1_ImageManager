package com.imagemanager.app.ui.compare

import android.app.Activity
import android.content.IntentSender
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.imagemanager.app.data.Photo

private val KEEP_COLOR = Color(0xFF2E9E5B)
private val DELETE_COLOR = Color(0xFFD64545)

/**
 * Side-by-side comparison for picking the best shot: tap the photo(s) to keep, and everything
 * else is marked for deletion. The bottom button sends the unkept ones to the trash in one go.
 * Long-press a photo to view it large (pinch to zoom, tap to close).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ComparisonScreen(
    photos: List<Photo>,
    requestTrash: (List<Long>) -> IntentSender,
    onFinished: () -> Unit,
) {
    var keep by remember { mutableStateOf(setOf<Long>()) }
    var preview by remember { mutableStateOf<Photo?>(null) }

    val trashLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> if (result.resultCode == Activity.RESULT_OK) onFinished() }

    val toDelete = photos.filter { it.mediaId !in keep }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "남길 사진을 탭하세요 · 길게 누르면 크게 보기",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
        ) {
            items(photos, key = { it.mediaId }) { photo ->
                val kept = photo.mediaId in keep
                val markedForDelete = keep.isNotEmpty() && !kept
                val shape = RoundedCornerShape(8.dp)
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .aspectRatio(3f / 4f)
                        .clip(shape)
                        .background(Color.Black.copy(alpha = 0.05f))
                        .border(if (kept) 4.dp else 1.dp, if (kept) KEEP_COLOR else Color.LightGray, shape)
                        .combinedClickable(
                            onClick = { keep = if (kept) keep - photo.mediaId else keep + photo.mediaId },
                            onLongClick = { preview = photo },
                        ),
                ) {
                    AsyncImage(
                        model = photo.uri,
                        contentDescription = photo.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().alpha(if (markedForDelete) 0.4f else 1f),
                    )
                    if (kept || markedForDelete) {
                        Surface(
                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                            shape = RoundedCornerShape(50),
                            color = if (kept) KEEP_COLOR else DELETE_COLOR,
                            contentColor = Color.White,
                        ) {
                            Text(
                                if (kept) "✓ 남김" else "삭제 예정",
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
        Button(
            enabled = keep.isNotEmpty() && toDelete.isNotEmpty(),
            onClick = {
                trashLauncher.launch(
                    IntentSenderRequest.Builder(requestTrash(toDelete.map { it.mediaId })).build(),
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = DELETE_COLOR),
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) {
            Text(
                when {
                    keep.isEmpty() -> "남길 사진을 선택하세요"
                    toDelete.isEmpty() -> "삭제할 사진이 없어요"
                    else -> "${keep.size}장 남기고 ${toDelete.size}장 삭제"
                },
            )
        }
    }

    preview?.let { FullPreview(it, onDismiss = { preview = null }) }
}

@Composable
private fun FullPreview(photo: Photo, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = photo.uri,
                contentDescription = photo.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { onDismiss() })
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, panChange, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            pan = if (scale > 1f) pan + panChange else Offset.Zero
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = pan.x
                        translationY = pan.y
                    },
            )
        }
    }
}
