package com.imagemanager.app.ui.compare

import android.app.Activity
import android.content.IntentSender
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.imagemanager.app.data.Photo

private val DELETE_COLOR = Color(0xFFD64545)
private val DELETE_BUTTON_COLOR = Color.White
private val HOLD_BUTTON_COLOR = Color.White
private val PROMOTE_BUTTON_COLOR = Color.Black
private const val MAX_ZOOM = 6f

/** Zoom/pan shared by both panes so the same spot of both photos is always on screen. */
private class SyncedZoom {
    var zoom by mutableFloatStateOf(1f)
    var pan by mutableStateOf(Offset.Zero)

    fun reset() {
        zoom = 1f
        pan = Offset.Zero
    }

    fun transform(zoomChange: Float, panChange: Offset, paneSize: IntSize) {
        zoom = (zoom * zoomChange).coerceIn(1f, MAX_ZOOM)
        pan = if (zoom <= 1f) {
            Offset.Zero
        } else {
            val maxX = paneSize.width * (zoom - 1f) / 2f
            val maxY = paneSize.height * (zoom - 1f) / 2f
            Offset((pan.x + panChange.x).coerceIn(-maxX, maxX), (pan.y + panChange.y).coerceIn(-maxY, maxY))
        }
    }
}

/**
 * Tournament comparison: the main photo (left in landscape, top in portrait) is compared to one
 * challenger at a time. Per challenger: delete, hold, or promote it to main. Nothing is trashed
 * until the summary page, which asks the system once for all the deleted photos.
 */
@Composable
fun ComparisonScreen(
    viewModel: CompareViewModel,
    requestTrash: (List<Long>) -> IntentSender,
    onFinished: () -> Unit,
) {
    val session by viewModel.session.collectAsState()
    val sync = remember { SyncedZoom() }

    val trashLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> if (result.resultCode == Activity.RESULT_OK) onFinished() }

    HideSystemBars()

    val challenger = session.challenger
    if (challenger == null) {
        SummaryPage(
            session = session,
            onUndo = { sync.reset(); viewModel.undo() },
            onTrash = {
                trashLauncher.launch(
                    IntentSenderRequest.Builder(requestTrash(session.deleted.map { it.mediaId })).build(),
                )
            },
            onDone = onFinished,
        )
        return
    }

    // Full screen: the photos fill everything; progress and undo float on top of them.
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val landscape = maxWidth > maxHeight
            val actions: @Composable BoxScope.() -> Unit = {
                ChallengerButtons(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onDelete = { sync.reset(); viewModel.delete() },
                    onHold = { sync.reset(); viewModel.hold() },
                    onPromote = { sync.reset(); viewModel.promote() },
                )
            }
            if (landscape) {
                Row(modifier = Modifier.fillMaxSize()) {
                    ZoomPane(session.main, "주", sync, Modifier.weight(1f).fillMaxHeight())
                    ZoomPane(challenger, "도전", sync, Modifier.weight(1f).fillMaxHeight(), actions)
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    ZoomPane(session.main, "주", sync, Modifier.weight(1f).fillMaxWidth())
                    ZoomPane(challenger, "도전", sync, Modifier.weight(1f).fillMaxWidth(), actions)
                }
            }
        }
        FloatingPill(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
        ) {
            Text("${session.position} / ${session.total}", fontSize = 13.sp, color = Color.White)
        }
        if (session.canUndo) {
            FloatingPill(
                onClick = { sync.reset(); viewModel.undo() },
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp),
            ) {
                Text("이전으로", fontSize = 13.sp, color = Color.White)
            }
        }
    }
}

/** Small translucent pill that floats over the photos. */
@Composable
private fun FloatingPill(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    val clickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.55f))
            .then(clickable)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) { content() }
}

/** Hides the status/navigation bars while this screen is shown (swipe from the edge to peek). */
@Composable
private fun HideSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

@Composable
private fun ZoomPane(
    photo: Photo,
    label: String,
    sync: SyncedZoom,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier = modifier.padding(2.dp).clipToBounds().background(Color.Black)) {
        AsyncImage(
            model = photo.uri,
            contentDescription = photo.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, panChange, zoomChange, _ ->
                        sync.transform(zoomChange, panChange, size)
                    }
                }
                .graphicsLayer {
                    scaleX = sync.zoom
                    scaleY = sync.zoom
                    translationX = sync.pan.x
                    translationY = sync.pan.y
                },
        )
        Surface(
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            shape = RoundedCornerShape(50),
            color = Color.Black.copy(alpha = 0.6f),
            contentColor = Color.White,
        ) {
            Text(label, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
        }
        overlay()
    }
}

@Composable
private fun ChallengerButtons(
    onDelete: () -> Unit,
    onHold: () -> Unit,
    onPromote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(10.dp),
    ) {
        CircleActionButton(Icons.Filled.Delete, "삭제", DELETE_BUTTON_COLOR, Color(0xFF222222), onDelete)
        CircleActionButton(Icons.Filled.Pause, "보류", HOLD_BUTTON_COLOR, Color(0xFF222222), onHold)
        CircleActionButton(Icons.Filled.EmojiEvents, "우선", PROMOTE_BUTTON_COLOR, Color.White, onPromote)
    }
}

/** Round icon button without an outline. */
@Composable
private fun CircleActionButton(
    icon: ImageVector,
    description: String,
    background: Color,
    iconTint: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = background,
        shadowElevation = 4.dp,
        modifier = Modifier.size(52.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(icon, contentDescription = description, tint = iconTint)
        }
    }
}

@Composable
private fun SummaryPage(
    session: CompareSession<Photo>,
    onUndo: () -> Unit,
    onTrash: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(24.dp),
    ) {
        Text("비교 완료", fontSize = 20.sp)
        Spacer(Modifier.height(16.dp))
        AsyncImage(
            model = session.main.uri,
            contentDescription = session.main.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier.weight(1f, fill = false).height(280.dp).fillMaxWidth().background(Color.Black),
        )
        Spacer(Modifier.height(12.dp))
        Text("최종 남김 1장 · 보류 ${session.held.size}장 · 삭제 ${session.deleted.size}장")
        Spacer(Modifier.height(20.dp))
        if (session.deleted.isEmpty()) {
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("완료") }
        } else {
            Button(
                onClick = onTrash,
                colors = ButtonDefaults.buttonColors(containerColor = DELETE_COLOR),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("삭제 ${session.deleted.size}장 휴지통으로 보내기") }
        }
        TextButton(onClick = onUndo, enabled = session.canUndo) { Text("이전으로") }
    }
}
