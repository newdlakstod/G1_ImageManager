package com.imagemanager.app.ui.grid

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.imagemanager.app.data.Photo

class PhotoPagingSource(
    private val pageSize: Int,
    private val queryPage: (offset: Int, limit: Int) -> List<Photo>,
) : PagingSource<Int, Photo>() {

    override fun getRefreshKey(state: PagingState<Int, Photo>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Photo> {
        val offset = params.key ?: 0
        return try {
            val items = queryPage(offset, pageSize)
            LoadResult.Page(
                data = items,
                prevKey = if (offset == 0) null else (offset - pageSize).coerceAtLeast(0),
                nextKey = if (items.size < pageSize) null else offset + pageSize,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
