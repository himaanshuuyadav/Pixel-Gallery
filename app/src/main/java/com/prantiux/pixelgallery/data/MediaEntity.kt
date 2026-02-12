package com.prantiux.pixelgallery.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.prantiux.pixelgallery.model.MediaItem

/**
 * Room entity for caching MediaStore results
 * 
 * Purpose:
 * - Store MediaItem metadata in local database
 * - Enable instant app startup (load from DB instead of MediaStore)
 * - Reduce cold start latency from 200ms-2s to 0-50ms
 * 
 * Why lightweight:
 * - No bitmap storage (only metadata)
 * - No duplicate data (MediaStore is source of truth)
 * - DB serves as fast-access cache layer
 */
@Entity(tableName = "media_cache")
data class MediaEntity(
    @PrimaryKey
    val id: Long,
    val uri: String,
    val displayName: String,
    val dateAdded: Long,
    val size: Long,
    val mimeType: String,
    val bucketId: String,
    val bucketName: String,
    val isVideo: Boolean,
    val duration: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val path: String = "",
    val dateExpires: Long = 0,
    val isFavorite: Boolean = false
) {
    /**
     * Convert database entity to domain model
     */
    fun toMediaItem(): MediaItem {
        return MediaItem(
            id = id,
            uri = android.net.Uri.parse(uri),
            displayName = displayName,
            dateAdded = dateAdded,
            size = size,
            mimeType = mimeType,
            bucketId = bucketId,
            bucketName = bucketName,
            isVideo = isVideo,
            duration = duration,
            width = width,
            height = height,
            path = path,
            dateExpires = dateExpires,
            isFavorite = isFavorite,
            latitude = null,  // GPS not cached (read on-demand)
            longitude = null
        )
    }
    
    companion object {
        /**
         * Convert domain model to database entity
         */
        fun fromMediaItem(item: MediaItem): MediaEntity {
            return MediaEntity(
                id = item.id,
                uri = item.uri.toString(),
                displayName = item.displayName,
                dateAdded = item.dateAdded,
                size = item.size,
                mimeType = item.mimeType,
                bucketId = item.bucketId,
                bucketName = item.bucketName,
                isVideo = item.isVideo,
                duration = item.duration,
                width = item.width,
                height = item.height,
                path = item.path,
                dateExpires = item.dateExpires,
                isFavorite = item.isFavorite
            )
        }
    }
}
