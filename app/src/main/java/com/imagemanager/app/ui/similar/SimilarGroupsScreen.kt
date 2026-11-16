package com.imagemanager.app.ui.similar

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.imagemanager.app.data.db.DuplicateGroupEntity

@Composable
fun SimilarGroupsScreen(
    viewModel: SimilarGroupsViewModel,
    photoUriFor: (Long) -> Uri,
    onGroupClick: (DuplicateGroupEntity) -> Unit,
) {
    val groups by viewModel.groups.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    Column {
        Button(
            onClick = { viewModel.rescan() },
            enabled = !isScanning,
            modifier = Modifier.padding(16.dp),
        ) {
            Text(if (isScanning) "스캔 중..." else "다시 스캔")
        }
        LazyColumn {
            items(groups, key = { it.groupId }) { group ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onGroupClick(group) }
                        .padding(8.dp),
                ) {
                    LazyRow {
                        items(group.mediaIds.take(4)) { mediaId ->
                            AsyncImage(
                                model = photoUriFor(mediaId),
                                contentDescription = null,
                                modifier = Modifier.size(72.dp).padding(2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
