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
}
