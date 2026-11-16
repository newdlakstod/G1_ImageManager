package com.imagemanager.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "duplicate_group")
data class DuplicateGroupEntity(
    @PrimaryKey(autoGenerate = true) val groupId: Long = 0,
    val mediaIds: List<Long>,
    val computedAt: Long,
)
