package com.prantiux.pixelgallery.data

import android.content.Context
import android.net.Uri
import com.prantiux.pixelgallery.model.Album
import com.prantiux.pixelgallery.model.CategorizedAlbums
import com.prantiux.pixelgallery.model.MediaItem
import com.prantiux.pixelgallery.smartalbum.SmartAlbumGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.prantiux.pixelgallery.data.AppDatabase

class AlbumRepository(private val context: Context) {

    /**
     * Fetch all albums from Room using a native SQL query.
     * This avoids any in-memory grouping on the massive media list.
     */
    suspend fun loadAllAlbumsFromRoom(): List<Album> = withContext(Dispatchers.IO) {
        val database = AppDatabase.getDatabase(context)
        val albums = mutableListOf<Album>()
        
        // 1. Get unique buckets and their counts
        val buckets = database.mediaDao().getAllBuckets()
        
        // 2. Fetch top 6 covers for each bucket
        buckets.forEach { bucket ->
            val topMedia = database.mediaDao().getTopMediaForBucket(bucket.bucketId, 6)
            if (topMedia.isNotEmpty()) {
                albums.add(
                    Album(
                        id = bucket.bucketId,
                        name = bucket.bucketName ?: "Unknown",
                        coverUri = android.net.Uri.parse(topMedia.first().uri),
                        itemCount = bucket.count,
                        bucketDisplayName = bucket.bucketName ?: "Unknown",
                        topMediaUris = topMedia.map { android.net.Uri.parse(it.uri) },
                        topMediaItems = topMedia.map { it.toMediaItem() }
                    )
                )
            }
        }
        
        albums.sortedByDescending { it.itemCount }
    }

    /**
     * Load ML-based smart albums (used by Search screen only)
     */
    suspend fun loadSmartAlbums(): List<Album> = withContext(Dispatchers.IO) {
        try {
            SmartAlbumGenerator.generateSmartAlbums(context)
        } catch (e: Exception) {
            android.util.Log.e("AlbumRepository", "Error loading smart albums", e)
            emptyList()
        }
    }
}
