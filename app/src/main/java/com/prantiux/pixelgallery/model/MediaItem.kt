package com.prantiux.pixelgallery.model

import android.net.Uri

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateAdded: Long,
    val size: Long,
    val mimeType: String,
    val bucketId: String,
    val bucketName: String,
    val isVideo: Boolean = false,
    val duration: Long = 0L, // Video duration in milliseconds
    val width: Int = 0,
    val height: Int = 0,
    val path: String = "",
    val dateExpires: Long = 0, // For trash items - when they will be permanently deleted
    val isFavorite: Boolean = false // Track favorite status
)
