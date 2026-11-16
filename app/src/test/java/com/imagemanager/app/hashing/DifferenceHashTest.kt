package com.imagemanager.app.hashing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DifferenceHashTest {

    @Test
    fun compute_rejectsBufferWithWrongSize() {
        assertThrows(IllegalArgumentException::class.java) {
            DifferenceHash.compute(IntArray(10), width = 9, height = 8)
        }
    }

    @Test
    fun compute_identicalInputsProduceIdenticalHashes() {
        val luminance = IntArray(72) { it * 3 }

        val hashA = DifferenceHash.compute(luminance, width = 9, height = 8)
        val hashB = DifferenceHash.compute(luminance, width = 9, height = 8)

        assertEquals(hashA, hashB)
    }

    @Test
    fun compute_setsBitWhenLeftPixelDarkerThanRightPixel() {
        val luminance = IntArray(72) { 255 }
        luminance[0] = 0

        val hash = DifferenceHash.compute(luminance, width = 9, height = 8)

        assertEquals(1L, hash and 1L)
    }

    @Test
    fun compute_flatImageProducesAllZeroBits() {
        val luminance = IntArray(72) { 128 }

        val hash = DifferenceHash.compute(luminance, width = 9, height = 8)

        assertEquals(0L, hash)
    }

    @Test
    fun compute_differentImagesProduceDifferentHashes() {
        val flat = IntArray(72) { 128 }
        val gradient = IntArray(72) { index -> (index % 9) * 25 }

        val hashA = DifferenceHash.compute(flat, width = 9, height = 8)
        val hashB = DifferenceHash.compute(gradient, width = 9, height = 8)

        assertNotEquals(hashA, hashB)
    }
}
