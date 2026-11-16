package com.imagemanager.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pile")
data class PileEntity(
    @PrimaryKey(autoGenerate = true) val pileId: Long = 0,
    val name: String,
    val folderRelativePath: String,
    val createdAt: Long,
)
