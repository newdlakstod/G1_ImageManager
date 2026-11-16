package com.imagemanager.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DuplicateGroupDao {
    @Query("SELECT * FROM duplicate_group ORDER BY computedAt DESC")
    fun observeAll(): Flow<List<DuplicateGroupEntity>>

    @Query("DELETE FROM duplicate_group")
    suspend fun clearAll()

    @Insert
    suspend fun insertAll(groups: List<DuplicateGroupEntity>)
}
