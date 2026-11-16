package com.imagemanager.app.ui.piles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.imagemanager.app.data.PhotoActions
import com.imagemanager.app.data.db.PileDao
import com.imagemanager.app.data.db.PileEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PileWithCount(val pile: PileEntity, val photoCount: Int)

class PileViewModel(
    private val pileDao: PileDao,
    private val photoActions: PhotoActions,
) : ViewModel() {
    val piles: StateFlow<List<PileWithCount>> = pileDao.observeAll()
        .map { piles ->
            piles.map { pile -> PileWithCount(pile, photoActions.countPhotosIn(pile.folderRelativePath)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            syncPilesWithGalleryFolders(pileDao, photoActions)
        }
    }

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
