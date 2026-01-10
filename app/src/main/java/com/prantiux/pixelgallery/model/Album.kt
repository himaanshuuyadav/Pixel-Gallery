package com.prantiux.pixelgallery.model

import android.net.Uri

data class Album(
    val id: String,
    val name: String,
    val coverUri: Uri?,
    val itemCount: Int,
    val bucketDisplayName: String = name,
    val isMainAlbum: Boolean = false,
    val topMediaUris: List<Uri> = emptyList(), // Top 6 media URIs for grid display
    val topMediaItems: List<MediaItem> = emptyList() // Top 6 media items with full metadata
)

enum class AlbumCategory {
    CAMERA,
    SCREENSHOTS,
    WHATSAPP_IMAGES,
    WHATSAPP_VIDEO,
    OTHER
}

data class CategorizedAlbums(
    val mainAlbums: List<Album>,
    val otherAlbums: List<Album>
)
