package com.imagemanager.app.data

import android.content.IntentSender
import com.imagemanager.app.data.db.PileEntity

interface PhotoActions {
    fun toggleFavorite(mediaId: Long, favorite: Boolean): IntentSender?
    fun requestTrash(mediaIds: List<Long>): IntentSender
    fun requestUntrash(mediaIds: List<Long>): IntentSender
    fun movePhotoToPile(mediaId: Long, pile: PileEntity): IntentSender?
    fun listGalleryFolders(): List<GalleryFolder>

    /**
     * On some OEM builds, when the app already holds MANAGE_EXTERNAL_STORAGE,
     * the system resolves the createTrashRequest/createWriteRequest confirmation
     * intent instantly (no dialog shown) and only grants write access, without
     * actually applying the requested state change. Call these right after a
     * successful [requestTrash]/[requestUntrash] confirmation to guarantee the
     * state change lands regardless of which path the OS took.
     */
    fun confirmTrash(mediaIds: List<Long>)
    fun confirmUntrash(mediaIds: List<Long>)
}
