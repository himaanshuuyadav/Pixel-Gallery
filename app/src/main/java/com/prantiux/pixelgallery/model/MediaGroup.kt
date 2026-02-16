package com.prantiux.pixelgallery.model

data class MediaGroup(
    val date: String,
    val displayDate: String,
    val items: List<MediaItem>,
    val mostCommonLocation: String? = null
)
