package com.imagemanager.app.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.imagemanager.app.data.db.PileEntity

@RequiresApi(Build.VERSION_CODES.R)
class MediaStoreRepository(private val context: Context) : PhotoActions {

    private val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

    private val mediaTypeFilter = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN " +
        "(${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"

    private val projection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.DISPLAY_NAME,
        MediaStore.Files.FileColumns.DATE_MODIFIED,
        MediaStore.Files.FileColumns.IS_FAVORITE,
        MediaStore.Files.FileColumns.MEDIA_TYPE,
    )

    fun listPhotos(offset: Int, limit: Int): List<Photo> =
        query(selection = null, selectionArgs = null, offset = offset, limit = limit)

    fun listFavorites(offset: Int, limit: Int): List<Photo> =
        query(
            selection = "${MediaStore.Files.FileColumns.IS_FAVORITE} = 1",
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

    fun uriFor(mediaId: Long): Uri = ContentUris.withAppendedId(collection, mediaId)

    fun listByIds(mediaIds: List<Long>): List<Photo> {
        if (mediaIds.isEmpty()) return emptyList()
        val placeholders = mediaIds.joinToString(",") { "?" }
        return query(
            selection = "${MediaStore.Files.FileColumns._ID} IN ($placeholders)",
            selectionArgs = mediaIds.map { it.toString() }.toTypedArray(),
            offset = 0,
            limit = mediaIds.size,
        )
    }

    fun listByRelativePath(relativePath: String): List<Photo> = query(
        selection = "${MediaStore.Files.FileColumns.RELATIVE_PATH} = ?",
        selectionArgs = arrayOf(relativePath),
        offset = 0,
        limit = Int.MAX_VALUE,
    )

    private fun query(
        selection: String?,
        selectionArgs: Array<String>?,
        offset: Int,
        limit: Int,
    ): List<Photo> {
        val combinedSelection = if (selection != null) "($mediaTypeFilter) AND ($selection)" else mediaTypeFilter
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        val queryArgs = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, combinedSelection)
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
            putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
        }
        val photos = mutableListOf<Photo>()
        context.contentResolver.query(collection, projection, queryArgs, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val favCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.IS_FAVORITE)
            val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                photos += Photo(
                    mediaId = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    displayName = cursor.getString(nameCol),
                    dateModified = cursor.getLong(dateCol),
                    isFavorite = cursor.getInt(favCol) != 0,
                    isVideo = cursor.getInt(typeCol) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO,
                )
            }
        }
        return photos
    }

    override fun toggleFavorite(mediaId: Long, favorite: Boolean): IntentSender? {
        val uri = ContentUris.withAppendedId(collection, mediaId)
        val values = ContentValues().apply {
            put(MediaStore.Files.FileColumns.IS_FAVORITE, if (favorite) 1 else 0)
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

    override fun requestUntrash(mediaIds: List<Long>): IntentSender {
        val uris = mediaIds.map { ContentUris.withAppendedId(collection, it) }
        return MediaStore.createTrashRequest(context.contentResolver, uris, false).intentSender
    }

    override fun movePhotoToPile(mediaId: Long, pile: PileEntity): IntentSender? {
        val uri = ContentUris.withAppendedId(collection, mediaId)
        val values = ContentValues().apply {
            put(MediaStore.Files.FileColumns.RELATIVE_PATH, pile.folderRelativePath)
        }
        return try {
            context.contentResolver.update(uri, values, null, null)
            null
        } catch (e: SecurityException) {
            MediaStore.createWriteRequest(context.contentResolver, listOf(uri)).intentSender
        }
    }

    override fun listGalleryFolders(): List<GalleryFolder> {
        val folderProjection = arrayOf(
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.RELATIVE_PATH,
        )
        val queryArgs = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, mediaTypeFilter)
        }
        val seenPaths = LinkedHashSet<String>()
        val folders = mutableListOf<GalleryFolder>()
        context.contentResolver.query(collection, folderProjection, queryArgs, null)?.use { cursor ->
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                val path = cursor.getString(pathCol) ?: continue
                if (seenPaths.add(path)) {
                    folders += GalleryFolder(name = cursor.getString(nameCol) ?: path, relativePath = path)
                }
            }
        }
        return folders.sortedBy { it.name }
    }

    override fun countPhotosIn(relativePath: String): Int = listByRelativePath(relativePath).size
}
