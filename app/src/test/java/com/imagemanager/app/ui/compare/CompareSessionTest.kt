package com.imagemanager.app.ui.compare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareSessionTest {

    @Test
    fun start_firstItemIsMain_restAreChallengers() {
        val s = CompareSession.start(listOf(1, 2, 3))
        assertEquals(1, s.main)
        assertEquals(2, s.challenger)
        assertEquals(listOf(2, 3), s.queue)
        assertEquals(2, s.total)
        assertEquals(1, s.position)
        assertFalse(s.isFinished)
        assertFalse(s.canUndo)
    }

    @Test(expected = IllegalArgumentException::class)
    fun start_withFewerThanTwoItems_isRejected() {
        CompareSession.start(listOf(1))
    }

    @Test
    fun delete_movesChallengerToDeleted_mainUnchanged() {
        val s = CompareSession.start(listOf(1, 2, 3)).delete()
        assertEquals(1, s.main)
        assertEquals(listOf(2), s.deleted)
        assertEquals(3, s.challenger)
        assertEquals(2, s.position)
    }

    @Test
    fun hold_movesChallengerToHeld_mainUnchanged() {
        val s = CompareSession.start(listOf(1, 2, 3)).hold()
        assertEquals(1, s.main)
        assertEquals(listOf(2), s.held)
        assertEquals(3, s.challenger)
    }

    @Test
    fun promote_challengerBecomesMain_oldMainIsHeld() {
        val s = CompareSession.start(listOf(1, 2, 3)).promote()
        assertEquals(2, s.main)
        assertEquals(listOf(1), s.held)
        assertEquals(emptyList<Int>(), s.deleted)
        assertEquals(3, s.challenger)
    }

    @Test
    fun finishes_whenQueueIsEmpty() {
        val s = CompareSession.start(listOf(1, 2, 3)).delete().promote()
        assertTrue(s.isFinished)
        assertNull(s.challenger)
        assertEquals(3, s.main)
        assertEquals(listOf(2), s.deleted)
        assertEquals(listOf(1), s.held)
    }

    @Test
    fun undo_restoresPreviousStateStepByStep() {
        val start = CompareSession.start(listOf(1, 2, 3, 4))
        val s = start.promote().delete().hold()
        assertTrue(s.isFinished)

        val u1 = s.undo()
        assertEquals(4, u1.challenger)
        assertEquals(listOf(3), u1.deleted)

        val u2 = u1.undo()
        assertEquals(3, u2.challenger)
        assertEquals(2, u2.main)

        val u3 = u2.undo()
        assertEquals(1, u3.main)
        assertEquals(2, u3.challenger)
        assertFalse(u3.canUndo)
    }

    @Test(expected = IllegalStateException::class)
    fun actionAfterFinish_isRejected() {
        CompareSession.start(listOf(1, 2)).delete().delete()
    }
}
