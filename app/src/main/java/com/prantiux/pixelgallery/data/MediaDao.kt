package com.prantiux.pixelgallery.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * Data Access Object for MediaStore cache
 * 
 * Operations:
 * - Fast read on startup (getAllMedia)
 * - Background sync (upsertMedia, deleteByIds)
 * - Diff-based updates (efficient sync)
 */
@Dao
interface MediaDao {
    
    /**
     * Get all cached media for instant startup
     * 
     * Performance: 0-50ms for 10,000 items
     * Order: Descending by dateAdded (newest first)
     */
    @Query("SELECT * FROM media_cache ORDER BY dateAdded DESC")
    suspend fun getAllMedia(): List<MediaEntity>
    
    /**
     * Get all media IDs for diff comparison
     * 
     * Used to detect deleted items during background sync
     */
    @Query("SELECT id FROM media_cache")
    suspend fun getAllMediaIds(): List<Long>
    
    /**
     * Insert or update media items
     * 
     * OnConflictStrategy.REPLACE: Update if exists, insert if new
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMedia(media: List<MediaEntity>)
    
    /**
     * Delete media items by IDs
     * 
     * Used to remove items deleted from MediaStore
     */
    @Query("DELETE FROM media_cache WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
    
    /**
     * Clear entire cache
     * 
     * Used for full refresh (rare)
     */
    @Query("DELETE FROM media_cache")
    suspend fun clearAll()
    
    /**
     * Get count of cached items
     * 
     * Used to check if database is empty on first launch
     */
    @Query("SELECT COUNT(*) FROM media_cache")
    suspend fun getCount(): Int
    
    /**
     * Atomic clear and insert operation
     * 
     * Ensures consistency during full refresh
     */
    @Transaction
    suspend fun replaceAll(media: List<MediaEntity>) {
        clearAll()
        upsertMedia(media)
    }
    
    /**
     * Get cached images only
     */
    @Query("SELECT * FROM media_cache WHERE isVideo = 0 ORDER BY dateAdded DESC")
    suspend fun getAllImages(): List<MediaEntity>
    
    /**
     * Get all cached videos only
     */
    @Query("SELECT * FROM media_cache WHERE isVideo = 1 ORDER BY dateAdded DESC")
    suspend fun getAllVideos(): List<MediaEntity>
    
    /**
     * Get new media items added since lastSyncTimestamp (incremental sync)
     * 
     * Used to efficiently sync only new items from MediaStore without full rescan
     */
    @Query("SELECT * FROM media_cache WHERE dateAdded > :lastSyncTimestamp ORDER BY dateAdded DESC")
    suspend fun getNewMediaSince(lastSyncTimestamp: Long): List<MediaEntity>
    
    /**
     * PagingSource for all images (descending by date)
     * 
     * Used with Paging 3 for memory-efficient scrolling of large galleries
     * Loads data in 60-item pages with 20-item prefetch distance
     */
    @Query("SELECT * FROM media_cache WHERE isVideo = 0 ORDER BY dateAdded DESC")
    fun pagingSourceImages(): PagingSource<Int, MediaEntity>
    
    /**
     * PagingSource for all videos (descending by date)
     * 
     * Used with Paging 3 for memory-efficient scrolling of large video lists
     */
    @Query("SELECT * FROM media_cache WHERE isVideo = 1 ORDER BY dateAdded DESC")
    fun pagingSourceVideos(): PagingSource<Int, MediaEntity>
    
    /**
     * PagingSource for all media items regardless of type (descending by date)
     * 
     * Used with Paging 3 for mixed grid view
     */
    @Query("SELECT * FROM media_cache ORDER BY dateAdded DESC")
    fun pagingSourceAllMedia(): PagingSource<Int, MediaEntity>
}
