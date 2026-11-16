package com.imagemanager.app.data.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromMediaIds(ids: List<Long>): String = ids.joinToString(",")

    @TypeConverter
    fun toMediaIds(csv: String): List<Long> =
        if (csv.isBlank()) emptyList() else csv.split(",").map { it.toLong() }
}
