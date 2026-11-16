package com.imagemanager.app.ui.cardswipe

import android.content.IntentSender
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.imagemanager.app.data.Photo
import com.imagemanager.app.data.PhotoActions
import com.imagemanager.app.data.db.AppDatabase
import com.imagemanager.app.data.db.PileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Executor

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CardSwipeViewModelTest {
    private lateinit var db: AppDatabase
    private val testDispatcher = StandardTestDispatcher()

    private fun photo(id: Long) = Photo(id, Uri.EMPTY, "photo_$id", id, isFavorite = false)

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

    private class FakePhotoActions(
        private val trashSender: IntentSender? = null,
        private val favoriteSender: IntentSender? = null,
        private val moveSender: IntentSender? = null,
    ) : PhotoActions {
        var trashCallCount = 0
        var favoriteCallCount = 0
        var moveCallCount = 0

        override fun toggleFavorite(mediaId: Long, favorite: Boolean): IntentSender? {
            favoriteCallCount++
            return favoriteSender
        }

        override fun requestTrash(mediaIds: List<Long>): IntentSender {
            trashCallCount++
            return trashSender ?: throw IllegalStateException("no sender configured")
        }

        override fun movePhotoToPile(mediaId: Long, pile: PileEntity): IntentSender? {
            moveCallCount++
            return moveSender
        }
    }

    @Test
    fun moveNext_and_movePrevious_onlyChangeIndex_noRepositoryCalls() {
        val actions = FakePhotoActions()
        val viewModel = CardSwipeViewModel(actions, db.pileDao(), listOf(photo(1), photo(2)))

        viewModel.moveNext()
        assertEquals(1, viewModel.state.value.currentIndex)

        viewModel.movePrevious()
        assertEquals(0, viewModel.state.value.currentIndex)

        assertEquals(0, actions.trashCallCount)
        assertEquals(0, actions.favoriteCallCount)
        assertEquals(0, actions.moveCallCount)
    }

    @Test
    fun toggleFavorite_whenAppliedDirectly_updatesStateWithoutConfirmation() {
        val actions = FakePhotoActions(favoriteSender = null)
        val viewModel = CardSwipeViewModel(actions, db.pileDao(), listOf(photo(1)))
        var confirmationRequested = false

        viewModel.toggleFavorite { confirmationRequested = true }

        assertFalse(confirmationRequested)
        assertTrue(viewModel.state.value.currentPhoto!!.isFavorite)
    }

    @Test
    fun requestTrash_advancesOnlyAfterConfirmationResultIsTrue() {
        val fakeSender = Mockito.mock(IntentSender::class.java)
        val actions = FakePhotoActions(trashSender = fakeSender)
        val viewModel = CardSwipeViewModel(actions, db.pileDao(), listOf(photo(1), photo(2)))
        var capturedSender: IntentSender? = null

        viewModel.requestTrash { capturedSender = it }
        assertEquals(fakeSender, capturedSender)
        assertEquals(0, viewModel.state.value.currentIndex)

        viewModel.onConfirmationResult(confirmed = false)
        assertEquals(0, viewModel.state.value.currentIndex)

        viewModel.requestTrash { capturedSender = it }
        viewModel.onConfirmationResult(confirmed = true)
        assertEquals(1, viewModel.state.value.currentIndex)
    }

    @Test
    fun requestMoveToCurrentPile_withNoPileSelected_callsOnNoPileSelected() {
        val actions = FakePhotoActions()
        val viewModel = CardSwipeViewModel(actions, db.pileDao(), listOf(photo(1)))
        var noPileCalled = false

        viewModel.requestMoveToCurrentPile(
            onNeedsConfirmation = { },
            onNoPileSelected = { noPileCalled = true },
        )

        assertTrue(noPileCalled)
        assertEquals(0, actions.moveCallCount)
    }

    @Test
    fun createAndSelectPile_insertsPileAndSelectsIt() = runTest(testDispatcher) {
        val actions = FakePhotoActions()
        val viewModel = CardSwipeViewModel(actions, db.pileDao(), listOf(photo(1)))

        viewModel.createAndSelectPile("Trip")
        advanceUntilIdle()

        val piles = runBlocking { db.pileDao().observeAll().first() }
        assertEquals(1, piles.size)
        assertEquals("Trip", piles[0].name)
        assertEquals(piles[0].pileId, viewModel.state.value.currentPileId)
    }
}
