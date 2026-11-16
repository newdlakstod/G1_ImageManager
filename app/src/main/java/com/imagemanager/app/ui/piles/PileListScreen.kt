package com.imagemanager.app.ui.piles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.imagemanager.app.data.db.PileEntity

@Composable
fun PileListScreen(viewModel: PileViewModel, onPileClick: (PileEntity) -> Unit) {
    val piles by viewModel.piles.collectAsState()
    var newPileName by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row {
            OutlinedTextField(
                value = newPileName,
                onValueChange = { newPileName = it },
                label = { Text("새 폴더 이름") },
                modifier = Modifier.weight(1f),
            )
            Button(onClick = {
                viewModel.createPile(newPileName)
                newPileName = ""
            }) { Text("추가") }
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn {
            items(piles, key = { it.pileId }) { pile ->
                ListItem(
                    headlineContent = { Text(pile.name) },
                    modifier = Modifier.clickable { onPileClick(pile) },
                )
            }
        }
    }
}
