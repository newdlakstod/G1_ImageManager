package com.imagemanager.app.ui.cardswipe

import android.app.Activity
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.imagemanager.app.ui.piles.PilePickerSheet
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private const val UNDO_WINDOW_MS = 4000L
private const val PINCH_TO_GRID_ZOOM_THRESHOLD = 0.6f

private val SWIPE_THRESHOLD_DP = 96.dp

@Composable
fun CardSwipeScreen(viewModel: CardSwipeViewModel) {
    val state by viewModel.state.collectAsState()
    val piles by viewModel.piles.collectAsState()
    var showPilePicker by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(false) }
    val swipeThresholdPx = with(LocalDensity.current) { SWIPE_THRESHOLD_DP.toPx() }

    val confirmationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onConfirmationResult(result.resultCode == Activity.RESULT_OK) }

    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.trashConfirmedEvents.collect {
            val vibrator = context.getSystemService(Vibrator::class.java)
            vibrator?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showGrid) {
            LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize()) {
                items(state.photos, key = { it.mediaId }) { gridPhoto ->
                    AsyncImage(
                        model = gridPhoto.uri,
                        contentDescription = gridPhoto.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .padding(1.dp)
                            .clickable {
                                viewModel.jumpTo(gridPhoto.mediaId)
                                showGrid = false
                            },
                    )
                }
            }
        } else {
            val selectedPile = piles.find { it.pileId == state.currentPileId }
            AssistChip(
                onClick = { showPilePicker = true },
                label = { Text(selectedPile?.name ?: "폴더 선택") },
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
            )

            val photo = state.currentPhoto
            if (photo == null) {
                Text("더 이상 사진이 없습니다", modifier = Modifier.align(Alignment.Center))
            } else {
                var dragOffset by remember(photo.mediaId) { mutableStateOf(Offset.Zero) }
                AsyncImage(
                    model = photo.uri,
                    contentDescription = photo.displayName,
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
                        .pointerInput(photo.mediaId) {
                            detectDragGestures(
                                onDragEnd = {
                                    val offset = dragOffset
                                    val isVerticalGesture = abs(offset.y) > abs(offset.x)
                                    when {
                                        isVerticalGesture && offset.y <= -swipeThresholdPx -> viewModel.requestTrash {
                                            confirmationLauncher.launch(IntentSenderRequest.Builder(it).build())
                                        }
                                        isVerticalGesture && offset.y >= swipeThresholdPx -> viewModel.requestMoveToCurrentPile(
                                            onNeedsConfirmation = {
                                                confirmationLauncher.launch(IntentSenderRequest.Builder(it).build())
                                            },
                                            onNoPileSelected = { showPilePicker = true },
                                        )
                                        !isVerticalGesture && offset.x >= swipeThresholdPx -> viewModel.movePrevious()
                                        !isVerticalGesture && offset.x <= -swipeThresholdPx -> viewModel.moveNext()
                                    }
                                    dragOffset = Offset.Zero
                                },
                            ) { change, amount ->
                                change.consume()
                                dragOffset += amount
                            }
                        }
                        .pointerInput(photo.mediaId) {
                            detectTapGestures(
                                onLongPress = {
                                    viewModel.toggleFavorite {
                                        confirmationLauncher.launch(IntentSenderRequest.Builder(it).build())
                                    }
                                },
                            )
                        }
                        .pointerInput(photo.mediaId) {
                            // Only reacts once a second finger is down, so single-finger
                            // swipes above are never consumed/interfered with by this block.
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                var zoom = 1f
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.size >= 2) {
                                        zoom *= event.calculateZoom()
                                        if (zoom < PINCH_TO_GRID_ZOOM_THRESHOLD) {
                                            showGrid = true
                                            zoom = 1f
                                        }
                                        event.changes.forEach { change ->
                                            if (change.positionChanged()) change.consume()
                                        }
                                    }
                                    if (event.changes.none { it.pressed }) break
                                }
                            }
                        },
                )
            }

            if (showPilePicker) {
                PilePickerSheet(
                    piles = piles,
                    onSelect = {
                        viewModel.selectPile(it.pileId)
                        showPilePicker = false
                    },
                    onCreate = { name -> viewModel.createAndSelectPile(name) },
                    onDismiss = { showPilePicker = false },
                )
            }

            if (state.lastTrashed != null) {
                LaunchedEffect(state.lastTrashed) {
                    delay(UNDO_WINDOW_MS)
                    viewModel.dismissUndo()
                }
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                    shape = RoundedCornerShape(50),
                    shadowElevation = 6.dp,
                    tonalElevation = 3.dp,
                ) {
                    TextButton(onClick = {
                        viewModel.undoLastTrash {
                            confirmationLauncher.launch(IntentSenderRequest.Builder(it).build())
                        }
                    }) {
                        Text("실행 취소")
                    }
                }
            }
        }
    }
}
