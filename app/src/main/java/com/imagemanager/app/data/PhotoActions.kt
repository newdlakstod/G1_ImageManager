package com.imagemanager.app.data

import android.content.IntentSender
import com.imagemanager.app.data.db.PileEntity

interface PhotoActions {
    fun toggleFavorite(mediaId: Long, favorite: Boolean): IntentSender?
    fun requestTrash(mediaIds: List<Long>): IntentSender
    fun requestUntrash(mediaIds: List<Long>): IntentSender
    fun movePhotoToPile(mediaId: Long, pile: PileEntity): IntentSender?
    fun listGalleryFolders(): List<GalleryFolder>
}
