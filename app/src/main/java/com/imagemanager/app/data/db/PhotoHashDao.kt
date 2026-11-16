package com.imagemanager.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PhotoHashDao {
    @Query("SELECT * FROM photo_hash WHERE mediaId = :mediaId")
    suspend fun getByMediaId(mediaId: Long): PhotoHashEntity?

    @Query("SELECT * FROM photo_hash")
    suspend fun getAll(): List<PhotoHashEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PhotoHashEntity)
}
