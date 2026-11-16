package com.imagemanager.app.ui.piles

import android.content.IntentSender
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.imagemanager.app.data.GalleryFolder
import com.imagemanager.app.data.PhotoActions
import com.imagemanager.app.data.db.AppDatabase
import com.imagemanager.app.data.db.PileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
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
class PileViewModelTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
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

    private class FakePhotoActions(private val counts: Map<String, Int>) : PhotoActions {
        override fun toggleFavorite(mediaId: Long, favorite: Boolean): IntentSender? = null
        override fun requestTrash(mediaIds: List<Long>): IntentSender =
            throw UnsupportedOperationException("not exercised by this test")
        override fun movePhotoToPile(mediaId: Long, pile: PileEntity): IntentSender? = null
        override fun listGalleryFolders(): List<GalleryFolder> = emptyList()
        override fun countPhotosIn(relativePath: String): Int = counts[relativePath] ?: 0
    }

    @Test
    fun piles_pairsEachPileWithItsPhotoCount() = runTest {
        db.pileDao().insert(PileEntity(name = "Camera", folderRelativePath = "DCIM/Camera/", createdAt = 1000L))
        db.pileDao().insert(PileEntity(name = "Trip", folderRelativePath = "Pictures/Piles/Trip/", createdAt = 2000L))
        val actions = FakePhotoActions(
            counts = mapOf("DCIM/Camera/" to 42, "Pictures/Piles/Trip/" to 3),
        )

        val viewModel = PileViewModel(db.pileDao(), actions)
        val result = viewModel.piles.first { it.size == 2 }

        assertEquals(42, result.first { it.pile.name == "Camera" }.photoCount)
        assertEquals(3, result.first { it.pile.name == "Trip" }.photoCount)
    }
}
