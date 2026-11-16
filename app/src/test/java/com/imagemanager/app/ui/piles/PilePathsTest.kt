package com.imagemanager.app.ui.piles

import org.junit.Assert.assertEquals
import org.junit.Test

class PilePathsTest {

    @Test
    fun relativePathFor_wrapsNameInPicturesPilesFolder() {
        assertEquals("Pictures/Piles/Trip/", PilePaths.relativePathFor("Trip"))
    }

    @Test
    fun relativePathFor_trimsWhitespace() {
        assertEquals("Pictures/Piles/Trip/", PilePaths.relativePathFor("  Trip  "))
    }
}
