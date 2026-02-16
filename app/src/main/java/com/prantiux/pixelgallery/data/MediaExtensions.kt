package com.prantiux.pixelgallery.data

import android.net.Uri
import com.prantiux.pixelgallery.model.MediaItem

/**
 * Extension functions for converting between MediaEntity (Room) and MediaItem (UI)
 * 
 * This is the bridge between Room database and UI layer in Room-first architecture.
 */

/**
 * Convert MediaEntity (Room) to MediaItem (UI model)
 * 
 * @param isFavorite Whether this media is favorited (from favorites table)
 */
fun MediaEntity.toMediaItem(isFavorite: Boolean = false): MediaItem {
    return MediaItem(
        id = id,
        uri = Uri.parse(uri),
        displayName = displayName,
        dateAdded = dateAdded,
        size = size,
        mimeType = mimeType,
        bucketId = bucketId ?: "unknown",
        bucketName = bucketName ?: "Unknown",
        isVideo = isVideo,
        duration = duration ?: 0L,
        width = width,
        height = height,
        path = path,
        dateExpires = 0L, // Not used for regular media
        isFavorite = isFavorite,
        latitude = null, // Not stored in MediaEntity currently
        longitude = null,
        location = null
    )
}

/**
 * Convert list of MediaEntity to list of MediaItem
 */
fun List<MediaEntity>.toMediaItems(favoriteIds: Set<Long> = emptySet()): List<MediaItem> {
    return map { entity ->
        entity.toMediaItem(isFavorite = entity.id in favoriteIds)
    }
}
