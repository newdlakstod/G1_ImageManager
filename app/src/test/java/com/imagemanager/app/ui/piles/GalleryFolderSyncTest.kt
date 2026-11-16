package com.imagemanager.app.ui.piles

import android.content.IntentSender
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.imagemanager.app.data.GalleryFolder
import com.imagemanager.app.data.PhotoActions
import com.imagemanager.app.data.db.AppDatabase
import com.imagemanager.app.data.db.PileEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GalleryFolderSyncTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private class FakePhotoActions(private val folders: List<GalleryFolder>) : PhotoActions {
        override fun toggleFavorite(mediaId: Long, favorite: Boolean): IntentSender? = null
        override fun requestTrash(mediaIds: List<Long>): IntentSender =
            throw UnsupportedOperationException("not exercised by this test")
        override fun requestUntrash(mediaIds: List<Long>): IntentSender =
            throw UnsupportedOperationException("not exercised by this test")
        override fun movePhotoToPile(mediaId: Long, pile: PileEntity): IntentSender? = null
        override fun listGalleryFolders(): List<GalleryFolder> = folders
        override fun confirmTrash(mediaIds: List<Long>) = Unit
        override fun confirmUntrash(mediaIds: List<Long>) = Unit
    }

    @Test
    fun sync_insertsEachGalleryFolderAsAPile() = runTest {
        val actions = FakePhotoActions(
            listOf(
                GalleryFolder(name = "Camera", relativePath = "DCIM/Camera/"),
                GalleryFolder(name = "Screenshots", relativePath = "Pictures/Screenshots/"),
            ),
        )

        syncPilesWithGalleryFolders(db.pileDao(), actions)

        val piles = db.pileDao().observeAll().first()
        assertEquals(2, piles.size)
        assertEquals(setOf("Camera", "Screenshots"), piles.map { it.name }.toSet())
    }

    @Test
    fun sync_doesNotDuplicateFolderAlreadyPresentByRelativePath() = runTest {
        db.pileDao().insert(
            PileEntity(name = "Camera", folderRelativePath = "DCIM/Camera/", createdAt = 1000L),
        )
        val actions = FakePhotoActions(
            listOf(GalleryFolder(name = "Camera", relativePath = "DCIM/Camera/")),
        )

        syncPilesWithGalleryFolders(db.pileDao(), actions)

        val piles = db.pileDao().observeAll().first()
        assertEquals(1, piles.size)
    }

    @Test
    fun sync_doesNotTouchCustomPilesWithDifferentPaths() = runTest {
        db.pileDao().insert(
            PileEntity(name = "My Trip", folderRelativePath = "Pictures/Piles/My Trip/", createdAt = 1000L),
        )
        val actions = FakePhotoActions(
            listOf(GalleryFolder(name = "Camera", relativePath = "DCIM/Camera/")),
        )

        syncPilesWithGalleryFolders(db.pileDao(), actions)

        val piles = db.pileDao().observeAll().first()
        assertEquals(2, piles.size)
        assertTrue(piles.any { it.name == "My Trip" })
        assertTrue(piles.any { it.name == "Camera" })
    }
}
