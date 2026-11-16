package com.imagemanager.app.domain

import android.content.Context
import android.util.Size
import com.imagemanager.app.data.Photo
import com.imagemanager.app.hashing.computeDHash
import java.io.IOException

class BitmapPhotoHasher(private val context: Context) : PhotoHasher {
    override fun hash(photo: Photo): Long? = try {
        val bitmap = context.contentResolver.loadThumbnail(photo.uri, Size(128, 128), null)
        val hash = bitmap.computeDHash()
        bitmap.recycle()
        hash
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}
