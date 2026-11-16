package com.imagemanager.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PileDao {
    @Query("SELECT * FROM pile ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<PileEntity>>

    @Insert
    suspend fun insert(pile: PileEntity): Long

    @Query("SELECT * FROM pile WHERE pileId = :pileId")
    suspend fun getById(pileId: Long): PileEntity?
}
