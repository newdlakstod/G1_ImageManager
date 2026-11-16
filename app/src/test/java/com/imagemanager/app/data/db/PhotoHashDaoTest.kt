package com.imagemanager.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PhotoHashDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: PhotoHashDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.photoHashDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun upsert_thenGetByMediaId_returnsStoredHash() = runTest {
        dao.upsert(PhotoHashEntity(mediaId = 1L, dateModified = 1000L, hash = 42L))

        val result = dao.getByMediaId(1L)

        assertEquals(42L, result?.hash)
    }

    @Test
    fun upsert_withSameMediaId_replacesExistingEntry() = runTest {
        dao.upsert(PhotoHashEntity(mediaId = 1L, dateModified = 1000L, hash = 42L))

        dao.upsert(PhotoHashEntity(mediaId = 1L, dateModified = 2000L, hash = 99L))
        val result = dao.getByMediaId(1L)

        assertEquals(2000L, result?.dateModified)
        assertEquals(99L, result?.hash)
    }
}
