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
        dateExpires = 0, // This is overridden for trashed items when needed
        isFavorite = isFavorite,
        latitude = null, // Not stored in MediaEntity currently
        longitude = null, // Not stored in MediaEntity currently
        location = null, // Resolved asynchronously
        dateGroupDay = dateGroupDay,
        dateGroupMonth = dateGroupMonth
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

/**
 * Convert MediaItem (UI model) to MediaEntity (Room)
 */
fun MediaItem.toMediaEntity(): MediaEntity {
    return MediaEntity(
        id = this.id,
        uri = this.uri.toString(),
        displayName = this.displayName,
        dateAdded = this.dateAdded,
        bucketId = this.bucketId,
        bucketName = this.bucketName,
        mimeType = this.mimeType,
        width = this.width,
        height = this.height,
        size = this.size,
        duration = this.duration,
        isVideo = this.isVideo,
        path = this.path,
        dateGroupDay = this.dateGroupDay,
        dateGroupMonth = this.dateGroupMonth
    )
}
