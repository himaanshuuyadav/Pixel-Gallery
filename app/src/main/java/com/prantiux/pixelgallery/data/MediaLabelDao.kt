package com.prantiux.pixelgallery.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaLabelDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MediaLabelEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<MediaLabelEntity>)
    
    @Update
    suspend fun update(entity: MediaLabelEntity)
    
    @Delete
    suspend fun delete(entity: MediaLabelEntity)
    
    /**
     * Search for media by label using LIKE query
     * Query is case-insensitive and matches partial labels
     */
    @Query("SELECT * FROM media_labels WHERE labels LIKE '%' || :query || '%'")
    suspend fun searchByLabel(query: String): List<MediaLabelEntity>
    
    /**
     * Get all media IDs that have been processed
     * Used to skip already-labeled images
     */
    @Query("SELECT mediaId FROM media_labels")
    suspend fun getAllProcessedIds(): List<Long>
    
    /**
     * Get labels for specific media item
     */
    @Query("SELECT * FROM media_labels WHERE mediaId = :mediaId")
    suspend fun getLabelsForMedia(mediaId: Long): MediaLabelEntity?
    
    /**
     * Get count of processed images
     */
    @Query("SELECT COUNT(*) FROM media_labels")
    suspend fun getProcessedCount(): Int
    
    /**
     * Delete labels for specific media (e.g., when media is deleted)
     */
    @Query("DELETE FROM media_labels WHERE mediaId = :mediaId")
    suspend fun deleteLabelsForMedia(mediaId: Long)

    @Query("DELETE FROM media_labels WHERE mediaId IN (:mediaIds)")
    suspend fun deleteLabelsForMediaIds(mediaIds: List<Long>)
    
    /**
     * Clear all labels (for debugging)
     */
    @Query("DELETE FROM media_labels")
    suspend fun clearAllLabels()
    
    /**
     * Observe labeling progress (count of processed images)
     */
    @Query("SELECT COUNT(*) FROM media_labels")
    fun observeProcessedCount(): Flow<Int>
    
    /**
     * Get all labeled media for smart album generation
     */
    @Query("SELECT * FROM media_labels")
    suspend fun getAllLabels(): List<MediaLabelEntity>
}
