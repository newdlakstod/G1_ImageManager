package com.imagemanager.app.domain

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.imagemanager.app.data.Photo
import com.imagemanager.app.data.db.AppDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DuplicateScannerTest {
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

    private fun photo(id: Long, dateModified: Long) = Photo(
        mediaId = id,
        uri = Uri.EMPTY,
        displayName = "photo_$id",
        dateModified = dateModified,
        isFavorite = false,
    )

    @Test
    fun scan_groupsSimilarPhotosAndPersistsHashesAndGroups() = runTest {
        val photos = listOf(photo(1L, 100L), photo(2L, 100L))
        val hasher = FakeHasher(
            mapOf(1L to 0x00000000000000F0L, 2L to 0x00000000000000F3L),
        )
        val scanner = DuplicateScanner(
            photoSource = { photos },
            hasher = hasher,
            hashDao = db.photoHashDao(),
            groupDao = db.duplicateGroupDao(),
        )

        val groups = scanner.scan(threshold = 10)

        assertEquals(1, groups.size)
        assertEquals(setOf(1L, 2L), groups[0].toSet())
        assertEquals(0x00000000000000F0L, db.photoHashDao().getByMediaId(1L)?.hash)
        assertEquals(2, hasher.callCount)
    }

    @Test
    fun scan_doesNotRehashPhotoWhenDateModifiedUnchanged() = runTest {
        val photos = listOf(photo(1L, 100L))
        val hasher = FakeHasher(mapOf(1L to 0x1111L))
        val scanner = DuplicateScanner(
            photoSource = { photos },
            hasher = hasher,
            hashDao = db.photoHashDao(),
            groupDao = db.duplicateGroupDao(),
        )

        scanner.scan()
        scanner.scan()

        assertEquals(1, hasher.callCount)
    }

    @Test
    fun scan_rehashesPhotoWhenDateModifiedChanges() = runTest {
        val hasher = FakeHasher(mapOf(1L to 0x1111L))
        val scanner = DuplicateScanner(
            photoSource = { listOf(photo(1L, 100L)) },
            hasher = hasher,
            hashDao = db.photoHashDao(),
            groupDao = db.duplicateGroupDao(),
        )
        scanner.scan()

        val rescanner = DuplicateScanner(
            photoSource = { listOf(photo(1L, 200L)) },
            hasher = hasher,
            hashDao = db.photoHashDao(),
            groupDao = db.duplicateGroupDao(),
        )
        rescanner.scan()

        assertEquals(2, hasher.callCount)
        assertEquals(200L, db.photoHashDao().getByMediaId(1L)?.dateModified)
    }

    @Test
    fun scan_skipsPhotoWhenHasherReturnsNull() = runTest {
        val photos = listOf(photo(1L, 100L), photo(2L, 100L))
        val hasher = FakeHasher(mapOf(2L to 0x1111L))
        val scanner = DuplicateScanner(
            photoSource = { photos },
            hasher = hasher,
            hashDao = db.photoHashDao(),
            groupDao = db.duplicateGroupDao(),
        )

        scanner.scan()

        assertNull(db.photoHashDao().getByMediaId(1L))
        assertEquals(0x1111L, db.photoHashDao().getByMediaId(2L)?.hash)
    }

    private class FakeHasher(private val hashes: Map<Long, Long>) : PhotoHasher {
        var callCount = 0
            private set

        override fun hash(photo: Photo): Long? {
            callCount++
            return hashes[photo.mediaId]
        }
    }
}
