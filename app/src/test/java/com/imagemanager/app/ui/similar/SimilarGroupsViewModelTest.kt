package com.imagemanager.app.ui.similar

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.imagemanager.app.data.Photo
import com.imagemanager.app.data.db.AppDatabase
import com.imagemanager.app.domain.DuplicateScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Executor

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SimilarGroupsViewModelTest {
    private lateinit var db: AppDatabase
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val immediateExecutor = Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .setQueryExecutor(immediateExecutor)
            .setTransactionExecutor(immediateExecutor)
            .build()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun rescan_populatesGroupsFromScanner() = runTest(testDispatcher) {
        val photos = listOf(
            Photo(1L, Uri.EMPTY, "a", 1L, false),
            Photo(2L, Uri.EMPTY, "b", 1L, false),
        )
        val hashes = mapOf(1L to 0x00000000000000F0L, 2L to 0x00000000000000F3L)
        val scanner = DuplicateScanner(
            photoSource = { photos },
            hasher = { hashes[it.mediaId] },
            hashDao = db.photoHashDao(),
            groupDao = db.duplicateGroupDao(),
        )
        val viewModel = SimilarGroupsViewModel(scanner, db.duplicateGroupDao())
        viewModel.groups.launchIn(backgroundScope) // activates WhileSubscribed sharing for this test

        viewModel.rescan()
        advanceUntilIdle()

        assertEquals(1, viewModel.groups.value.size)
        assertEquals(setOf(1L, 2L), viewModel.groups.value[0].mediaIds.toSet())
    }
}
