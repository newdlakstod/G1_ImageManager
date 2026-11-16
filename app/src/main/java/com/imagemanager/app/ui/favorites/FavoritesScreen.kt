package com.imagemanager.app.ui.favorites

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.imagemanager.app.data.Photo
import com.imagemanager.app.ui.grid.PhotoGrid

@Composable
fun FavoritesScreen(viewModel: FavoritesViewModel, onPhotoClick: (Photo) -> Unit) {
    var refreshSignal by remember { mutableIntStateOf(0) }
    val confirmationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.onConfirmationResult(result.resultCode == Activity.RESULT_OK)
        refreshSignal++
    }

    PhotoGrid(
        photos = viewModel.photos,
        onPhotoClick = onPhotoClick,
        onPhotoLongPress = { photo ->
            viewModel.removeFavorite(
                photo = photo,
                onNeedsConfirmation = { sender ->
                    confirmationLauncher.launch(IntentSenderRequest.Builder(sender).build())
                },
                onApplied = { refreshSignal++ },
            )
        },
        refreshSignal = refreshSignal,
    )
}
