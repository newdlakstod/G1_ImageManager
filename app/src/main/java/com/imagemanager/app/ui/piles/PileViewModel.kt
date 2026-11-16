package com.imagemanager.app.ui.piles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.imagemanager.app.data.db.PileDao
import com.imagemanager.app.data.db.PileEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PileViewModel(private val pileDao: PileDao) : ViewModel() {
    val piles: StateFlow<List<PileEntity>> = pileDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createPile(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            pileDao.insert(
                PileEntity(
                    name = name.trim(),
                    folderRelativePath = PilePaths.relativePathFor(name),
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }
}
