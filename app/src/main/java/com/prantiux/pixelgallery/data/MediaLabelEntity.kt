package com.prantiux.pixelgallery.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for storing ML-generated labels for media items
 * Labels are stored as comma-separated lowercase strings for efficient search
 */
@Entity(tableName = "media_labels")
data class MediaLabelEntity(
    @PrimaryKey val mediaId: Long,
    val labels: String, // comma-separated lowercase labels (e.g., "dog,animal,pet")
    val processedTimestamp: Long = System.currentTimeMillis()
)
