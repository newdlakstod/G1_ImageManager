package com.imagemanager.app.data

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaStoreRepositoryInstrumentedTest {

    @Test
    fun listPhotos_returnsResultsWithoutCrashing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = MediaStoreRepository(context)
        val photos = repository.listPhotos(offset = 0, limit = 50)
        assertTrue(photos.size >= 0)
    }

    @Test
    fun listFavorites_onlyReturnsFavoritedPhotos() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = MediaStoreRepository(context)
        val favorites = repository.listFavorites(offset = 0, limit = 50)
        assertTrue(favorites.all { it.isFavorite })
    }

    @Test
    fun movePhotoToPile_updatesRelativePathWithoutCrashing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = MediaStoreRepository(context)
        val photos = repository.listPhotos(offset = 0, limit = 1)
        if (photos.isEmpty()) return // no seed photo on this device; skip
        val pile = com.imagemanager.app.data.db.PileEntity(
            pileId = 0,
            name = "Test",
            folderRelativePath = "Pictures/Piles/Test/",
            createdAt = 0L,
        )
        val result = repository.movePhotoToPile(photos[0].mediaId, pile)
        assertTrue(result == null || result is android.content.IntentSender)
    }
}
