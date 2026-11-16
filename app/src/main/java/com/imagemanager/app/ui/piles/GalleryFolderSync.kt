package com.imagemanager.app.ui.piles

import com.imagemanager.app.data.PhotoActions
import com.imagemanager.app.data.db.PileDao
import com.imagemanager.app.data.db.PileEntity
import kotlinx.coroutines.flow.first

suspend fun syncPilesWithGalleryFolders(pileDao: PileDao, photoActions: PhotoActions) {
    val folders = photoActions.listGalleryFolders()
    val existingPaths = pileDao.observeAll().first().map { it.folderRelativePath }.toSet()
    folders.filter { it.relativePath !in existingPaths }
        .forEach { folder ->
            pileDao.insert(
                PileEntity(
                    name = folder.name,
                    folderRelativePath = folder.relativePath,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
}
