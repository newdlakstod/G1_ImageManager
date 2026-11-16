package com.imagemanager.app.ui.cardswipe

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.imagemanager.app.ui.piles.PilePickerSheet
import kotlin.math.roundToInt

private const val SWIPE_THRESHOLD_PX = 300f

@Composable
fun CardSwipeScreen(viewModel: CardSwipeViewModel) {
    val state by viewModel.state.collectAsState()
    val piles by viewModel.piles.collectAsState()
    var showPilePicker by remember { mutableStateOf(false) }

    val confirmationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onConfirmationResult(result.resultCode == Activity.RESULT_OK) }

    Box(modifier = Modifier.fillMaxSize()) {
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
                                when {
                                    offset.y <= -SWIPE_THRESHOLD_PX -> viewModel.requestTrash {
                                        confirmationLauncher.launch(IntentSenderRequest.Builder(it).build())
                                    }
                                    offset.y >= SWIPE_THRESHOLD_PX -> viewModel.requestMoveToCurrentPile(
                                        onNeedsConfirmation = {
                                            confirmationLauncher.launch(IntentSenderRequest.Builder(it).build())
                                        },
                                        onNoPileSelected = { showPilePicker = true },
                                    )
                                    offset.x >= SWIPE_THRESHOLD_PX -> viewModel.movePrevious()
                                    offset.x <= -SWIPE_THRESHOLD_PX -> viewModel.moveNext()
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
    }
}
