package com.imagemanager.app.data

import android.net.Uri
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

data class Photo(
    val mediaId: Long,
    val uri: Uri,
    val displayName: String,
    val dateModified: Long,
    val isFavorite: Boolean,
    val isVideo: Boolean = false,
    val relativePath: String? = null,
    /** Capture time in epoch millis; 0 when MediaStore has none. */
    val dateTaken: Long = 0L,
) {
    /** Capture time, falling back to file modification time (seconds -> millis). */
    val takenAtMillis: Long get() = if (dateTaken > 0) dateTaken else dateModified * 1000

    /** "yyyy-MM" in the device time zone, used to group photos by month. */
    val monthKey: String
        get() = YearMonth.from(Instant.ofEpochMilli(takenAtMillis).atZone(ZoneId.systemDefault())).toString()
}
