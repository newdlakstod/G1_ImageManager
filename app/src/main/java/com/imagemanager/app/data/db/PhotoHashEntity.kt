package com.imagemanager.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "photo_hash")
data class PhotoHashEntity(
    @PrimaryKey val mediaId: Long,
    val dateModified: Long,
    val hash: Long,
)
