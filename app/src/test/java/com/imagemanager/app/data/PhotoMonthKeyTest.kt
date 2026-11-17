package com.imagemanager.app.data

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class PhotoMonthKeyTest {
    private fun millisOf(year: Int, month: Int, day: Int) =
        LocalDate.of(year, month, day).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun monthKey_usesDateTakenWhenPresent() {
        val photo = Photo(1, Uri.EMPTY, "a", dateModified = 0, isFavorite = false, dateTaken = millisOf(2024, 5, 15))
        assertEquals("2024-05", photo.monthKey)
    }

    @Test
    fun monthKey_fallsBackToDateModifiedSeconds() {
        val photo = Photo(1, Uri.EMPTY, "a", dateModified = millisOf(2023, 12, 15) / 1000, isFavorite = false)
        assertEquals("2023-12", photo.monthKey)
    }
}
