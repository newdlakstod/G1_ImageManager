package com.imagemanager.app.hashing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HashGroupingTest {

    @Test
    fun hammingDistance_identicalHashes_isZero() {
        assertEquals(0, HashGrouping.hammingDistance(0b1010L, 0b1010L))
    }

    @Test
    fun hammingDistance_countsDifferingBits() {
        assertEquals(2, HashGrouping.hammingDistance(0b1010L, 0b0000L))
    }

    @Test
    fun group_clustersHashesWithinThresholdInSameBucket() {
        val photos = listOf(
            HashedPhoto(mediaId = 1L, hash = 0x00000000000000F0L),
            HashedPhoto(mediaId = 2L, hash = 0x00000000000000F3L),
        )

        val groups = HashGrouping.group(photos)

        assertEquals(1, groups.size)
        assertEquals(setOf(1L, 2L), groups[0].toSet())
    }

    @Test
    fun group_doesNotClusterHashesBeyondThreshold() {
        val photos = listOf(
            HashedPhoto(mediaId = 1L, hash = 0x0000000000000000L),
            HashedPhoto(mediaId = 2L, hash = 0x0000000000000FFFL),
        )

        val groups = HashGrouping.group(photos, threshold = 10)

        assertTrue(groups.isEmpty())
    }

    @Test
    fun group_excludesSingletonsFromResult() {
        val photos = listOf(
            HashedPhoto(mediaId = 1L, hash = 0x0000000000000000L),
            HashedPhoto(mediaId = 2L, hash = 0x7F00000000000000L),
        )

        val groups = HashGrouping.group(photos)

        assertTrue(groups.isEmpty())
    }

    @Test
    fun group_transitivelyMergesAChainOfSimilarHashes() {
        val photos = listOf(
            HashedPhoto(mediaId = 1L, hash = 0x00000000000000F0L),
            HashedPhoto(mediaId = 2L, hash = 0x00000000000000F3L),
            HashedPhoto(mediaId = 3L, hash = 0x00000000000000FFL),
        )

        val groups = HashGrouping.group(photos, threshold = 4)

        assertEquals(1, groups.size)
        assertEquals(setOf(1L, 2L, 3L), groups[0].toSet())
    }
}
