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

    // Used only for querying across both images and videos at once. createDeleteRequest/
    // createWriteRequest reject URIs built from this generic collection ("All requested
    // items must be Media items"), so every per-item action below resolves the correct
    // type-specific collection (Images.Media / Video.Media) first.
    private val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
    private val imagesCollection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    private val videoCollection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

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

    fun uriFor(mediaId: Long): Uri = typeSpecificUri(mediaId)

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
                val isVideo = cursor.getInt(typeCol) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                photos += Photo(
                    mediaId = id,
                    uri = ContentUris.withAppendedId(if (isVideo) videoCollection else imagesCollection, id),
                    displayName = cursor.getString(nameCol),
                    dateModified = cursor.getLong(dateCol),
                    isFavorite = cursor.getInt(favCol) != 0,
                    isVideo = isVideo,
                )
            }
        }
        return photos
    }

    /**
     * MediaStore.createDeleteRequest/createWriteRequest only accept URIs from the
     * type-specific collections, not the generic Files collection used for querying
     * across both images and videos. This looks up a single row's MEDIA_TYPE and
     * returns the matching Images.Media/Video.Media URI for it.
     */
    private fun typeSpecificUri(mediaId: Long): Uri {
        val genericUri = ContentUris.withAppendedId(collection, mediaId)
        val isVideo = context.contentResolver.query(
            genericUri,
            arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE),
            null,
            null,
            null,
        )?.use { cursor ->
            cursor.moveToFirst() && cursor.getInt(0) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
        } ?: false
        return ContentUris.withAppendedId(if (isVideo) videoCollection else imagesCollection, mediaId)
    }

    override fun toggleFavorite(mediaId: Long, favorite: Boolean): IntentSender? {
        val uri = typeSpecificUri(mediaId)
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

    // Permanent delete (not createTrashRequest's soft/recoverable trash) so that Google
    // Photos' own backup sync recognizes the file as genuinely removed and mirrors the
    // deletion into its own cloud trash, matching Samsung Gallery's delete behavior.
    override fun requestTrash(mediaIds: List<Long>): IntentSender {
        val uris = mediaIds.map { typeSpecificUri(it) }
        return MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
    }

    override fun movePhotoToPile(mediaId: Long, pile: PileEntity): IntentSender? {
        val uri = typeSpecificUri(mediaId)
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
