package com.imagemanager.app.ui.cardswipe

import android.app.Activity
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.imagemanager.app.ui.common.FavoriteBadge
import com.imagemanager.app.ui.common.VideoBadge
import com.imagemanager.app.ui.common.VideoPlayOverlay
import com.imagemanager.app.ui.piles.PilePickerSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val UNDO_WINDOW_MS = 4000L
private const val PINCH_TO_GRID_SCALE_THRESHOLD = 0.6f
private const val MIN_PHOTO_SCALE = 0.3f
private const val MAX_PHOTO_SCALE = 4f

private val SWIPE_THRESHOLD_DP = 96.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CardSwipeScreen(viewModel: CardSwipeViewModel, onCompareRequested: (List<Long>) -> Unit = {}) {
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
    LaunchedEffect(viewModel) {
        viewModel.favoriteToggledEvents.collect {
            val vibrator = context.getSystemService(Vibrator::class.java)
            vibrator?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showGrid) {
            val gridState = rememberLazyGridState()
            val coroutineScope = rememberCoroutineScope()
            var selectionMode by remember { mutableStateOf(false) }
            var selectedIds by remember { mutableStateOf(setOf<Long>()) }

            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.photos, key = { it.mediaId }) { gridPhoto ->
                    val isSelected = gridPhoto.mediaId in selectedIds
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .padding(1.dp)
                            .then(
                                if (isSelected) {
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.primary)
                                } else {
                                    Modifier
                                },
                            )
                            .combinedClickable(
                                onClick = {
                                    if (selectionMode) {
                                        selectedIds = if (isSelected) {
                                            selectedIds - gridPhoto.mediaId
                                        } else {
                                            selectedIds + gridPhoto.mediaId
                                        }
                                    } else {
                                        viewModel.jumpTo(gridPhoto.mediaId)
                                        showGrid = false
                                    }
                                },
                                onLongClick = {
                                    viewModel.toggleFavoriteFor(gridPhoto.mediaId) {
                                        confirmationLauncher.launch(IntentSenderRequest.Builder(it).build())
                                    }
                                },
                            ),
                    ) {
                        AsyncImage(
                            model = gridPhoto.uri,
                            contentDescription = gridPhoto.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (gridPhoto.isFavorite) {
                            FavoriteBadge(modifier = Modifier.align(Alignment.TopStart))
                        }
                        if (gridPhoto.isVideo) {
                            VideoBadge(modifier = Modifier.align(Alignment.BottomEnd))
                        }
                    }
                }
            }
            VerticalFastScrollBar(
                itemCount = state.photos.size,
                onJumpTo = { index -> coroutineScope.launch { gridState.scrollToItem(index) } },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
            AssistChip(
                onClick = {
                    selectionMode = !selectionMode
                    if (!selectionMode) selectedIds = emptySet()
                },
                label = { Text(if (selectionMode) "선택 모드 끄기" else "여러 장 선택") },
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
            )
            if (selectedIds.isNotEmpty()) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    shape = RoundedCornerShape(50),
                    shadowElevation = 8.dp,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp)) {
                        Text("${selectedIds.size}장 선택됨")
                        TextButton(onClick = { selectedIds = emptySet() }) { Text("취소") }
                        Button(
                            onClick = { onCompareRequested(selectedIds.toList()) },
                            enabled = selectedIds.size >= 2,
                            modifier = Modifier.padding(end = 8.dp),
                        ) { Text("비교") }
                    }
                }
            }
        } else {
            val selectedPile = piles.find { it.pileId == state.currentPileId }

            val photo = state.currentPhoto
            if (photo == null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Text("더 이상 사진이 없습니다")
                    if (state.photos.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { viewModel.movePrevious() }) {
                            Text("이전으로")
                        }
                    }
                }
            } else {
                var dragOffset by remember(photo.mediaId) { mutableStateOf(Offset.Zero) }
                var panOffset by remember(photo.mediaId) { mutableStateOf(Offset.Zero) }
                var scale by remember(photo.mediaId) { mutableFloatStateOf(1f) }
                AsyncImage(
                    model = photo.uri,
                    contentDescription = photo.displayName,
                    modifier = Modifier
                        .fillMaxSize()
                        .offset {
                            IntOffset(
                                (dragOffset.x + panOffset.x).roundToInt(),
                                (dragOffset.y + panOffset.y).roundToInt(),
                            )
                        }
                        .scale(scale)
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
                            // Pinching past the base scale (< threshold) opens the grid,
                            // same as Google Photos' pinch-out-to-grid gesture.
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.size >= 2) {
                                        scale = (scale * event.calculateZoom())
                                            .coerceIn(MIN_PHOTO_SCALE, MAX_PHOTO_SCALE)
                                        if (scale <= PINCH_TO_GRID_SCALE_THRESHOLD) {
                                            showGrid = true
                                            scale = 1f
                                            panOffset = Offset.Zero
                                        } else if (scale > 1f) {
                                            panOffset += event.calculatePan()
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
                if (photo.isFavorite) {
                    FavoriteBadge(modifier = Modifier.align(Alignment.TopStart))
                }
                if (photo.isVideo) {
                    VideoPlayOverlay(videoUri = photo.uri, modifier = Modifier.align(Alignment.Center))
                }
            }

            AssistChip(
                onClick = { showPilePicker = true },
                label = { Text(selectedPile?.name ?: "폴더 선택") },
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
            )

            if (state.photos.size > 1) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                ) {
                    Text("${state.currentIndex + 1} / ${state.photos.size}")
                    Slider(
                        value = state.currentIndex.toFloat(),
                        onValueChange = { viewModel.jumpToIndex(it.roundToInt()) },
                        valueRange = 0f..(state.photos.size - 1).toFloat(),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                }
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
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 16.dp),
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF323232),
                    contentColor = Color.White,
                    shadowElevation = 8.dp,
                ) {
                    TextButton(
                        onClick = {
                            viewModel.undoLastTrash {
                                confirmationLauncher.launch(IntentSenderRequest.Builder(it).build())
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                    ) {
                        Text("실행 취소")
                    }
                }
            }
        }
    }
}

@Composable
private fun VerticalFastScrollBar(
    itemCount: Int,
    onJumpTo: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (itemCount <= 1) return
    val density = LocalDensity.current
    val thumbHeight = 40.dp
    var trackHeightPx by remember { mutableFloatStateOf(0f) }
    var thumbOffsetPx by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .width(28.dp)
            .fillMaxHeight()
            .onSizeChanged { trackHeightPx = it.height.toFloat() }
            .pointerInput(itemCount) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val thumbHeightPx = with(density) { thumbHeight.toPx() }
                    val maxOffset = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
                    val rawOffset = change.position.y - thumbHeightPx / 2
                    thumbOffsetPx = rawOffset.coerceIn(0f, maxOffset)
                    val progress = if (maxOffset > 0f) thumbOffsetPx / maxOffset else 0f
                    onJumpTo((progress * (itemCount - 1)).roundToInt())
                }
            },
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, thumbOffsetPx.roundToInt()) }
                .width(6.dp)
                .height(thumbHeight)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)),
        )
    }
}
