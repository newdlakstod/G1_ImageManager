package com.imagemanager.app.ui.months

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.YearMonth

/** Lists every month that has photos ("yyyy-MM" keys); tapping one starts swiping through it. */
@Composable
fun MonthListScreen(
    loadMonths: () -> List<Pair<String, Int>>,
    onMonthClick: (String) -> Unit,
) {
    val months: List<Pair<String, Int>>? by produceState<List<Pair<String, Int>>?>(null) {
        value = withContext(Dispatchers.IO) { loadMonths() }
    }

    val list = months
    if (list == null) {
        Text("불러오는 중…", modifier = Modifier.fillMaxSize().padding(16.dp))
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(list, key = { it.first }) { (key, count) ->
            val ym = YearMonth.parse(key)
            ListItem(
                headlineContent = { Text("${ym.year}년 ${ym.monthValue}월") },
                trailingContent = { Text("($count)") },
                modifier = Modifier.clickable { onMonthClick(key) },
            )
        }
    }
}
