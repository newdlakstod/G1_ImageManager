package com.imagemanager.app.ui.cardswipe

import android.content.IntentSender
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.imagemanager.app.data.LastPositionStore
import com.imagemanager.app.data.Photo
import com.imagemanager.app.data.PhotoActions
import com.imagemanager.app.data.db.AppDatabase
import com.imagemanager.app.data.db.PileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
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
        private val untrashSender: IntentSender? = null,
        private val favoriteSender: IntentSender? = null,
        private val moveSender: IntentSender? = null,
    ) : PhotoActions {
        var trashCallCount = 0
        var untrashCallCount = 0
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

        override fun requestUntrash(mediaIds: List<Long>): IntentSender {
            untrashCallCount++
            return untrashSender ?: throw IllegalStateException("no sender configured")
        }

        override fun movePhotoToPile(mediaId: Long, pile: PileEntity): IntentSender? {
            moveCallCount++
            return moveSender
        }

        override fun listGalleryFolders(): List<com.imagemanager.app.data.GalleryFolder> = emptyList()
        override fun countPhotosIn(relativePath: String): Int = 0
    }

    private class FakeLastPositionStore(initial: Long? = null) : LastPositionStore {
        var saved: Long? = initial
            private set

        override fun save(mediaId: Long) {
            saved = mediaId
        }

        override fun load(): Long? = saved
    }

    @Test
    fun init_withLastPositionStore_jumpsToSavedMediaId() {
        val store = FakeLastPositionStore(initial = 3L)
        val viewModel = CardSwipeViewModel(
            FakePhotoActions(),
            db.pileDao(),
            listOf(photo(1), photo(2), photo(3)),
            lastPositionStore = store,
        )

        assertEquals(2, viewModel.state.value.currentIndex)
        assertEquals(3L, viewModel.state.value.currentPhoto?.mediaId)
    }

    @Test
    fun navigating_withLastPositionStore_savesCurrentMediaId() {
        val store = FakeLastPositionStore()
        val viewModel = CardSwipeViewModel(
            FakePhotoActions(),
            db.pileDao(),
            listOf(photo(1), photo(2)),
            lastPositionStore = store,
        )

        viewModel.moveNext()

        assertEquals(2L, store.saved)
    }

    @Test
    fun jumpTo_movesCurrentIndexToMatchingPhoto() {
        val actions = FakePhotoActions()
        val viewModel = CardSwipeViewModel(actions, db.pileDao(), listOf(photo(1), photo(2), photo(3)))

        viewModel.jumpTo(3L)

        assertEquals(2, viewModel.state.value.currentIndex)
        assertEquals(3L, viewModel.state.value.currentPhoto?.mediaId)
    }

    @Test
    fun jumpTo_withUnknownMediaId_doesNothing() {
        val actions = FakePhotoActions()
        val viewModel = CardSwipeViewModel(actions, db.pileDao(), listOf(photo(1), photo(2)))

        viewModel.jumpTo(999L)

        assertEquals(0, viewModel.state.value.currentIndex)
    }

    @Test
    fun jumpToIndex_clampsWithinPhotosRange() {
        val actions = FakePhotoActions()
        val viewModel = CardSwipeViewModel(actions, db.pileDao(), listOf(photo(1), photo(2), photo(3)))

        viewModel.jumpToIndex(1)
        assertEquals(1, viewModel.state.value.currentIndex)

        viewModel.jumpToIndex(99)
        assertEquals(2, viewModel.state.value.currentIndex)

        viewModel.jumpToIndex(-5)
        assertEquals(0, viewModel.state.value.currentIndex)
    }

    @Test
    fun refresh_restoresAPhotoThatReappearedInTheProvidedSource() {
        val actions = FakePhotoActions()
        val viewModel = CardSwipeViewModel(
            actions,
            db.pileDao(),
            listOf(photo(1), photo(2)),
            refreshPhotosProvider = { listOf(photo(1), photo(2), photo(3)) },
        )

        viewModel.jumpTo(2L)
        viewModel.refresh()

        assertEquals(listOf(1L, 2L, 3L), viewModel.state.value.photos.map { it.mediaId })
        assertEquals(2L, viewModel.state.value.currentPhoto?.mediaId)
    }

    @Test
    fun refresh_clampsIndexWhenCurrentPhotoIsNoLongerInTheProvidedSource() {
        val actions = FakePhotoActions()
        val viewModel = CardSwipeViewModel(
            actions,
            db.pileDao(),
            listOf(photo(1), photo(2), photo(3)),
            refreshPhotosProvider = { listOf(photo(1), photo(2)) },
        )

        viewModel.jumpTo(3L)
        viewModel.refresh()

        assertEquals(listOf(1L, 2L), viewModel.state.value.photos.map { it.mediaId })
        assertEquals(1, viewModel.state.value.currentIndex)
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
    fun toggleFavorite_emitsFavoriteToggledEventWithNewState() = runTest(testDispatcher) {
        val actions = FakePhotoActions(favoriteSender = null)
        val viewModel = CardSwipeViewModel(actions, db.pileDao(), listOf(photo(1)))

        val emitted = mutableListOf<Boolean>()
        val job = launch {
            viewModel.favoriteToggledEvents.collect { emitted.add(it) }
        }
        advanceUntilIdle()

        viewModel.toggleFavorite { }
        advanceUntilIdle()
        viewModel.toggleFavorite { }
        advanceUntilIdle()

        assertEquals(listOf(true, false), emitted)
        job.cancel()
    }

    @Test
    fun toggleFavoriteFor_updatesNonCurrentPhotoWithoutChangingCurrentIndex() {
        val actions = FakePhotoActions(favoriteSender = null)
        val viewModel = CardSwipeViewModel(actions, db.pileDao(), listOf(photo(1), photo(2)))

        viewModel.toggleFavoriteFor(2L) { }

        assertEquals(0, viewModel.state.value.currentIndex)
        assertTrue(viewModel.state.value.photos.first { it.mediaId == 2L }.isFavorite)
        assertFalse(viewModel.state.value.currentPhoto!!.isFavorite)
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
        assertEquals(0, viewModel.state.value.currentIndex)
        assertEquals(2L, viewModel.state.value.currentPhoto?.mediaId)
    }

    @Test
    fun undoLastMove_movesPhotoBackAndReinsertsIt() = runTest(testDispatcher) {
        val actions = FakePhotoActions(moveSender = null)
        val pileId = db.pileDao().insert(
            PileEntity(name = "Trip", folderRelativePath = "Pictures/Piles/Trip", createdAt = 0L),
        )
        val original = photo(1).copy(relativePath = "DCIM/Camera/")
        val viewModel = CardSwipeViewModel(actions, db.pileDao(), listOf(original, photo(2)))
        viewModel.piles.launchIn(backgroundScope)
        advanceUntilIdle()
        viewModel.selectPile(pileId)

        viewModel.requestMoveToCurrentPile(onNeedsConfirmation = { }, onNoPileSelected = { })
        assertEquals(1, viewModel.state.value.photos.size)
        assertEquals("Trip", viewModel.state.value.lastMoved?.pileName)
        assertEquals("DCIM/Camera/", viewModel.state.value.lastMoved?.originalPath)

        viewModel.undoLastMove { }

        assertEquals(2, actions.moveCallCount)
        assertEquals(null, viewModel.state.value.lastMoved)
        assertEquals(2, viewModel.state.value.photos.size)
        assertEquals(1L, viewModel.state.value.currentPhoto?.mediaId)
    }

    @Test
    fun dismissUndo_clearsLastMovedWithoutMovingBack() = runTest(testDispatcher) {
        val actions = FakePhotoActions(moveSender = null)
        val pileId = db.pileDao().insert(
            PileEntity(name = "Trip", folderRelativePath = "Pictures/Piles/Trip", createdAt = 0L),
        )
        val viewModel = CardSwipeViewModel(actions, db.pileDao(), listOf(photo(1).copy(relativePath = "DCIM/Camera/")))
        viewModel.piles.launchIn(backgroundScope)
        advanceUntilIdle()
        viewModel.selectPile(pileId)
        viewModel.requestMoveToCurrentPile(onNeedsConfirmation = { }, onNoPileSelected = { })

        viewModel.dismissUndo()

        assertEquals(null, viewModel.state.value.lastMoved)
        assertEquals(1, actions.moveCallCount)
    }

    @Test
    fun requestTrash_removesPhotoFromListSoMovePreviousDoesNotRevealIt() {
        val fakeSender = Mockito.mock(IntentSender::class.java)
        val actions = FakePhotoActions(trashSender = fakeSender)
        val viewModel = CardSwipeViewModel(actions, db.pileDao(), listOf(photo(1), photo(2), photo(3)))

        viewModel.moveNext() // currentIndex = 1, photo(2) current
        viewModel.requestTrash { }
        viewModel.onConfirmationResult(confirmed = true)

        // photo(2) removed; photo(3) has shifted into index 1
        assertEquals(2, viewModel.state.value.photos.size)
        assertEquals(3L, viewModel.state.value.currentPhoto?.mediaId)

        viewModel.movePrevious()

        // Scrolling back must land on photo(1), never on the trashed photo(2)
        assertEquals(1L, viewModel.state.value.currentPhoto?.mediaId)
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
    fun requestMoveToCurrentPile_emitsPileMovedEventWhenAppliedDirectly() = runTest(testDispatcher) {
        val actions = FakePhotoActions(moveSender = null)
        val pileId = db.pileDao().insert(
            PileEntity(name = "Trip", folderRelativePath = "Pictures/Piles/Trip", createdAt = 0L),
        )
        val viewModel = CardSwipeViewModel(actions, db.pileDao(), listOf(photo(1)))
        viewModel.piles.launchIn(backgroundScope)
        advanceUntilIdle()
        viewModel.selectPile(pileId)

        val emitted = mutableListOf<PhotoMoved>()
        val job = launch {
            viewModel.pileMovedEvents.collect { emitted.add(it) }
        }
        advanceUntilIdle()

        viewModel.requestMoveToCurrentPile(onNeedsConfirmation = { }, onNoPileSelected = { })
        advanceUntilIdle()

        assertEquals(1, emitted.size)
        assertEquals(1, actions.moveCallCount)
        job.cancel()
    }

    @Test
    fun requestMoveToCurrentPile_emitsPileMovedEventOnlyAfterConfirmationResultIsTrue() = runTest(testDispatcher) {
        val moveSender = Mockito.mock(IntentSender::class.java)
        val actions = FakePhotoActions(moveSender = moveSender)
        val pileId = db.pileDao().insert(
            PileEntity(name = "Trip", folderRelativePath = "Pictures/Piles/Trip", createdAt = 0L),
        )
        val viewModel = CardSwipeViewModel(actions, db.pileDao(), listOf(photo(1)))
        viewModel.piles.launchIn(backgroundScope)
        advanceUntilIdle()
        viewModel.selectPile(pileId)

        val emitted = mutableListOf<PhotoMoved>()
        val job = launch {
            viewModel.pileMovedEvents.collect { emitted.add(it) }
        }
        advanceUntilIdle()

        viewModel.requestMoveToCurrentPile(onNeedsConfirmation = { }, onNoPileSelected = { })
        advanceUntilIdle()
        assertEquals(0, emitted.size)

        viewModel.onConfirmationResult(confirmed = true)
        advanceUntilIdle()
        assertEquals(1, emitted.size)
        job.cancel()
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
