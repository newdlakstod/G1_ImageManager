package com.imagemanager.app.ui.cardswipe

import android.app.Activity
import android.os.VibrationEffect
import android.net.Uri
import android.os.Vibrator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
private val GRID_COLUMN_LEVELS = listOf(3, 5, 7)
private const val GRID_PINCH_STEP_THRESHOLD = 0.75f
private val SELECTION_COLOR = Color(0xFF2F80ED)
private const val FLY_AWAY_MS = 320
private const val TRASH_TOAST_MS = 2000L

private enum class FlyDirection { UP, DOWN }
private data class FlyingPhoto(val uri: Uri, val direction: FlyDirection, val key: Long)
private data class ActionToast(val text: String, val undoable: Boolean, val key: Long)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CardSwipeScreen(viewModel: CardSwipeViewModel, onCompareRequested: (List<Long>) -> Unit = {}) {
    val state by viewModel.state.collectAsState()
    val piles by viewModel.piles.collectAsState()
    var showPilePicker by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(false) }
    var flying by remember { mutableStateOf<FlyingPhoto?>(null) }
    var toast by remember { mutableStateOf<ActionToast?>(null) }
    var heartPop by remember { mutableIntStateOf(0) }
    val swipeThresholdPx = with(LocalDensity.current) { SWIPE_THRESHOLD_DP.toPx() }

    val confirmationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onConfirmationResult(result.resultCode == Activity.RESULT_OK) }

    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.trashConfirmedEvents.collect { photo ->
            val vibrator = context.getSystemService(Vibrator::class.java)
            vibrator?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
            val key = System.nanoTime()
            flying = FlyingPhoto(photo.uri, FlyDirection.UP, key)
            toast = ActionToast("휴지통으로 이동", undoable = false, key = key)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.favoriteToggledEvents.collect { favorite ->
            val vibrator = context.getSystemService(Vibrator::class.java)
            vibrator?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
            if (favorite) heartPop++
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.pileMovedEvents.collect { moved ->
            val vibrator = context.getSystemService(Vibrator::class.java)
            vibrator?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
            val key = System.nanoTime()
            flying = FlyingPhoto(moved.photo.uri, FlyDirection.DOWN, key)
            toast = ActionToast("${moved.pileName}(으)로 이동", undoable = moved.originalPath != null, key = key)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(viewModel, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showGrid) {
            val gridState = rememberLazyGridState()
            val coroutineScope = rememberCoroutineScope()
            var selectedIds by remember { mutableStateOf(setOf<Long>()) }
            // Selecting starts with a long-press; once anything is selected, taps toggle photos.
            val selectionMode = selectedIds.isNotEmpty()
            var columnLevel by remember { mutableIntStateOf(0) }

            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(GRID_COLUMN_LEVELS[columnLevel]),
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        // Watches (never consumes) two-finger pinches: pinching in shrinks the
                        // thumbnails one level (more columns), spreading enlarges them again.
                        // One level per gesture, so scrolling and taps are unaffected.
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            var zoom = 1f
                            var stepped = false
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.size >= 2 && !stepped) {
                                    zoom *= event.calculateZoom()
                                    if (zoom < GRID_PINCH_STEP_THRESHOLD) {
                                        columnLevel = (columnLevel + 1).coerceAtMost(GRID_COLUMN_LEVELS.lastIndex)
                                        stepped = true
                                    } else if (zoom > 1f / GRID_PINCH_STEP_THRESHOLD) {
                                        columnLevel = (columnLevel - 1).coerceAtLeast(0)
                                        stepped = true
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    },
            ) {
                items(state.photos, key = { it.mediaId }) { gridPhoto ->
                    val isSelected = gridPhoto.mediaId in selectedIds
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .padding(1.dp)
                            .then(
                                // Blue outer ring + white inner ring: visible on dark and bright photos alike.
                                if (isSelected) {
                                    Modifier
                                        .border(4.dp, SELECTION_COLOR)
                                        .padding(4.dp)
                                        .border(2.dp, Color.White)
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
                                    selectedIds = if (isSelected) {
                                        selectedIds - gridPhoto.mediaId
                                    } else {
                                        selectedIds + gridPhoto.mediaId
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
                        if (isSelected) {
                            Box(modifier = Modifier.fillMaxSize().background(SELECTION_COLOR.copy(alpha = 0.25f)))
                            Surface(
                                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp),
                                shape = CircleShape,
                                color = SELECTION_COLOR,
                                border = BorderStroke(2.dp, Color.White),
                            ) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "선택됨",
                                    tint = Color.White,
                                    modifier = Modifier.padding(3.dp),
                                )
                            }
                        }
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
                                // Tap the left half for the previous photo, the right half for the next.
                                onTap = { tap ->
                                    if (tap.x < size.width / 2f) viewModel.movePrevious() else viewModel.moveNext()
                                },
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

            flying?.let { f ->
                FlyAwayPhoto(f, onDone = { if (flying?.key == f.key) flying = null })
            }

            if (heartPop > 0) {
                HeartPop(key = heartPop, modifier = Modifier.align(Alignment.Center))
            }

            toast?.let { t ->
                LaunchedEffect(t.key) {
                    delay(if (t.undoable) UNDO_WINDOW_MS else TRASH_TOAST_MS)
                    toast = null
                    viewModel.dismissUndo()
                }
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp),
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF323232).copy(alpha = 0.6f),
                    contentColor = Color.White,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            t.text,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(
                                start = 12.dp,
                                end = if (t.undoable) 0.dp else 12.dp,
                                top = 6.dp,
                                bottom = 6.dp,
                            ),
                        )
                        if (t.undoable && state.lastMoved != null) {
                            TextButton(
                                onClick = {
                                    toast = null
                                    viewModel.undoLastMove {
                                        confirmationLauncher.launch(IntentSenderRequest.Builder(it).build())
                                    }
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            ) {
                                Text("실행 취소", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Plays a one-shot fly-away of the photo that was just trashed/moved, over the next card. */
@Composable
private fun FlyAwayPhoto(flying: FlyingPhoto, onDone: () -> Unit) {
    val progress = remember(flying.key) { Animatable(0f) }
    LaunchedEffect(flying.key) {
        progress.animateTo(1f, tween(FLY_AWAY_MS))
        onDone()
    }
    AsyncImage(
        model = flying.uri,
        contentDescription = null,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = progress.value
                when (flying.direction) {
                    // Trash: flies up and off the screen.
                    FlyDirection.UP -> {
                        translationY = -size.height * 0.8f * p
                        scaleX = 1f - 0.2f * p
                        scaleY = 1f - 0.2f * p
                    }
                    // Move to folder: shrinks and sinks away.
                    FlyDirection.DOWN -> {
                        translationY = size.height * 0.3f * p
                        scaleX = 1f - 0.7f * p
                        scaleY = 1f - 0.7f * p
                    }
                }
                alpha = 1f - p
            },
    )
}

/** Big heart that pops and fades, replayed each time [key] changes. */
@Composable
private fun HeartPop(key: Int, modifier: Modifier = Modifier) {
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { progress.animateTo(1f, tween(600)) }
    Text(
        "♥",
        color = Color(0xFFFF4D6D),
        fontSize = 120.sp,
        modifier = modifier.graphicsLayer {
            val p = progress.value
            scaleX = 0.5f + 0.7f * p
            scaleY = 0.5f + 0.7f * p
            alpha = 1f - p
        },
    )
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
