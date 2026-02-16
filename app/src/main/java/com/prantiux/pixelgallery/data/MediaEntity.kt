package com.prantiux.pixelgallery.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media")
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
