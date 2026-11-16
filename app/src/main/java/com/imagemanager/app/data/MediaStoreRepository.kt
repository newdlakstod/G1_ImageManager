package com.imagemanager.app.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.imagemanager.app.data.db.PileEntity

@RequiresApi(Build.VERSION_CODES.R)
class MediaStoreRepository(private val context: Context) : PhotoActions {

    private val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

    private val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_MODIFIED,
        MediaStore.Images.Media.IS_FAVORITE,
    )

    fun listPhotos(offset: Int, limit: Int): List<Photo> =
        query(selection = null, selectionArgs = null, offset = offset, limit = limit)

    fun listFavorites(offset: Int, limit: Int): List<Photo> =
        query(
            selection = "${MediaStore.Images.Media.IS_FAVORITE} = 1",
            selectionArgs = null,
            offset = offset,
            limit = limit,
        )

    fun listAllPhotos(): List<Photo> = query(
        selection = null,
        selectionArgs = null,
        offset = 0,
        limit = Int.MAX_VALUE,
    )

    private fun query(
        selection: String?,
        selectionArgs: Array<String>?,
        offset: Int,
        limit: Int,
    ): List<Photo> {
        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        val queryArgs = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
            putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
        }
        val photos = mutableListOf<Photo>()
        context.contentResolver.query(collection, projection, queryArgs, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val favCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.IS_FAVORITE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                photos += Photo(
                    mediaId = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    displayName = cursor.getString(nameCol),
                    dateModified = cursor.getLong(dateCol),
                    isFavorite = cursor.getInt(favCol) != 0,
                )
            }
        }
        return photos
    }

    override fun toggleFavorite(mediaId: Long, favorite: Boolean): IntentSender? {
        val uri = ContentUris.withAppendedId(collection, mediaId)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.IS_FAVORITE, if (favorite) 1 else 0)
        }
        return try {
            context.contentResolver.update(uri, values, null, null)
            null
        } catch (e: SecurityException) {
            MediaStore.createWriteRequest(context.contentResolver, listOf(uri)).intentSender
        }
    }

    override fun requestTrash(mediaIds: List<Long>): IntentSender {
        val uris = mediaIds.map { ContentUris.withAppendedId(collection, it) }
        return MediaStore.createTrashRequest(context.contentResolver, uris, true).intentSender
    }

    override fun movePhotoToPile(mediaId: Long, pile: PileEntity): IntentSender? {
        val uri = ContentUris.withAppendedId(collection, mediaId)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.RELATIVE_PATH, pile.folderRelativePath)
        }
        return try {
            context.contentResolver.update(uri, values, null, null)
            null
        } catch (e: SecurityException) {
            MediaStore.createWriteRequest(context.contentResolver, listOf(uri)).intentSender
        }
    }
}
