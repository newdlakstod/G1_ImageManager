package com.imagemanager.app.ui.grid

import android.net.Uri
import androidx.paging.PagingSource
import com.imagemanager.app.data.Photo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PhotoPagingSourceTest {

    private fun fakePhoto(id: Long) = Photo(
        mediaId = id,
        uri = Uri.EMPTY,
        displayName = "photo_$id",
        dateModified = id,
        isFavorite = false,
    )

    @Test
    fun load_firstPage_returnsItemsWithNextKey() = runTest {
        val allPhotos = (1..10L).map { fakePhoto(it) }
        val source = PhotoPagingSource(pageSize = 4) { offset, limit -> allPhotos.drop(offset).take(limit) }

        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 4, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page

        assertEquals(4, result.data.size)
        assertEquals(4, result.nextKey)
        assertEquals(null, result.prevKey)
    }

    @Test
    fun load_lastPage_returnsNullNextKey() = runTest {
        val allPhotos = (1..5L).map { fakePhoto(it) }
        val source = PhotoPagingSource(pageSize = 4) { offset, limit -> allPhotos.drop(offset).take(limit) }

        val result = source.load(
            PagingSource.LoadParams.Append(key = 4, loadSize = 4, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page

        assertEquals(1, result.data.size)
        assertEquals(null, result.nextKey)
    }
}
