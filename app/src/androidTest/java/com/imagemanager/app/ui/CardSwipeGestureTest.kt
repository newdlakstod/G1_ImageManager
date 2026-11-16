package com.imagemanager.app.ui

import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.imagemanager.app.data.Photo
import com.imagemanager.app.data.PhotoActions
import com.imagemanager.app.data.db.AppDatabase
import com.imagemanager.app.data.db.PileEntity
import com.imagemanager.app.ui.cardswipe.CardSwipeScreen
import com.imagemanager.app.ui.cardswipe.CardSwipeViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CardSwipeGestureTest {
    @get:Rule
    val composeRule = createComposeRule()

    private class NoOpPhotoActions : PhotoActions {
        override fun toggleFavorite(mediaId: Long, favorite: Boolean) = null
        override fun requestTrash(mediaIds: List<Long>) =
            throw UnsupportedOperationException("not exercised by this test")
        override fun movePhotoToPile(mediaId: Long, pile: PileEntity) = null
        override fun listGalleryFolders() = emptyList<com.imagemanager.app.data.GalleryFolder>()
        override fun countPhotosIn(relativePath: String) = 0
    }

    @Test
    fun swipeLeft_advancesToNextPhoto() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val photos = listOf(
            Photo(1L, Uri.EMPTY, "a", 1L, false),
            Photo(2L, Uri.EMPTY, "b", 2L, false),
        )
        val viewModel = CardSwipeViewModel(NoOpPhotoActions(), db.pileDao(), photos)

        composeRule.setContent { CardSwipeScreen(viewModel) }
        composeRule.onRoot().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertEquals(1, viewModel.state.value.currentIndex)
    }
}
