package com.imagemanager.app.data

import android.content.Context

class SharedPrefsLastPositionStore(context: Context) : LastPositionStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun save(mediaId: Long) {
        prefs.edit().putLong(KEY_LAST_MEDIA_ID, mediaId).apply()
    }

    override fun load(): Long? {
        val value = prefs.getLong(KEY_LAST_MEDIA_ID, NO_VALUE)
        return if (value == NO_VALUE) null else value
    }

    private companion object {
        const val PREFS_NAME = "image_manager_prefs"
        const val KEY_LAST_MEDIA_ID = "last_media_id"
        const val NO_VALUE = -1L
    }
}
