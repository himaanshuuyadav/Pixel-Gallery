package com.prantiux.pixelgallery.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for storing ML-generated labels for media items
 * Labels are stored as comma-separated lowercase strings with confidence scores
 */
@Entity(tableName = "media_labels")
data class MediaLabelEntity(
    @PrimaryKey val mediaId: Long,
    val labels: String, // comma-separated lowercase labels (e.g., "dog,animal,pet")
    val labelsWithConfidence: String = "", // format: "dog:0.95,animal:0.88,pet:0.75"
    val processedTimestamp: Long = System.currentTimeMillis()
)
