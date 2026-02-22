package com.prantiux.pixelgallery.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media",
    indices = [
        Index("dateAdded"),                           // Single index for date sorting
        Index("bucketId"),                            // Single index for album grouping
        Index("isVideo"),                             // Single index for image/video filtering
        Index(value = ["isVideo", "dateAdded"]),      // Composite for common filter+sort
        Index("displayName"),                         // Single index for name-based search
        Index("size")                                 // Single index for size-based filtering
    ]
)
data class MediaEntity(
    @PrimaryKey val id: Long,
    val uri: String,
    val displayName: String,
    val dateAdded: Long,
    val bucketId: String?,
    val bucketName: String?,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val size: Long,
    val duration: Long?,
    val isVideo: Boolean,
    val path: String = ""
)
