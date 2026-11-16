package com.imagemanager.app.domain

import com.imagemanager.app.data.Photo
import com.imagemanager.app.data.db.DuplicateGroupDao
import com.imagemanager.app.data.db.DuplicateGroupEntity
import com.imagemanager.app.data.db.PhotoHashDao
import com.imagemanager.app.data.db.PhotoHashEntity
import com.imagemanager.app.hashing.HashGrouping
import com.imagemanager.app.hashing.HashedPhoto

fun interface PhotoSource {
    fun listAll(): List<Photo>
}

fun interface PhotoHasher {
    fun hash(photo: Photo): Long?
}

class DuplicateScanner(
    private val photoSource: PhotoSource,
    private val hasher: PhotoHasher,
    private val hashDao: PhotoHashDao,
    private val groupDao: DuplicateGroupDao,
) {
    suspend fun scan(threshold: Int = 10): List<List<Long>> {
        val photos = photoSource.listAll()
        val cached = hashDao.getAll().associateBy { it.mediaId }
        val hashedPhotos = mutableListOf<HashedPhoto>()

        for (photo in photos) {
            val existing = cached[photo.mediaId]
            val hash = if (existing != null && existing.dateModified == photo.dateModified) {
                existing.hash
            } else {
                val computed = hasher.hash(photo) ?: continue
                hashDao.upsert(PhotoHashEntity(photo.mediaId, photo.dateModified, computed))
                computed
            }
            hashedPhotos += HashedPhoto(photo.mediaId, hash)
        }

        val groups = HashGrouping.group(hashedPhotos, threshold)
        groupDao.clearAll()
        groupDao.insertAll(
            groups.map { mediaIds ->
                DuplicateGroupEntity(mediaIds = mediaIds, computedAt = System.currentTimeMillis())
            },
        )
        return groups
    }
}
