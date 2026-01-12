package com.prantiux.pixelgallery.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey
    val mediaId: Long,
    val timestamp: Long = System.currentTimeMillis()
)
