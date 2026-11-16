package com.imagemanager.app.ui.piles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.imagemanager.app.data.db.PileEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PilePickerSheet(
    piles: List<PileEntity>,
    onSelect: (PileEntity) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newPileName by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("이동할 폴더 선택", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                items(piles, key = { it.pileId }) { pile ->
                    ListItem(
                        headlineContent = { Text(pile.name) },
                        modifier = Modifier.clickable { onSelect(pile) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row {
                OutlinedTextField(
                    value = newPileName,
                    onValueChange = { newPileName = it },
                    label = { Text("새 폴더 이름") },
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { onCreate(newPileName); newPileName = "" }) {
                    Text("만들기")
                }
            }
        }
    }
}
