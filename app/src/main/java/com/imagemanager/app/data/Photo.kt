package com.imagemanager.app.data

import android.net.Uri

data class Photo(
    val mediaId: Long,
    val uri: Uri,
    val displayName: String,
    val dateModified: Long,
    val isFavorite: Boolean,
    val isVideo: Boolean = false,
    val relativePath: String? = null,
)
