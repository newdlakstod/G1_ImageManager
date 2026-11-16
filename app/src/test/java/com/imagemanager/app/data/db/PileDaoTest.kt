package com.imagemanager.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PileDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: PileDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.pileDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insert_thenObserveAll_emitsInsertedPile() = runTest {
        dao.insert(PileEntity(name = "Trip", folderRelativePath = "Pictures/Piles/Trip", createdAt = 1000L))

        val piles = dao.observeAll().first()

        assertEquals(1, piles.size)
        assertEquals("Trip", piles[0].name)
    }
}
