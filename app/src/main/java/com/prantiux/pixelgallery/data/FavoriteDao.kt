package com.prantiux.pixelgallery.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY timestamp DESC")
    fun getAllFavorites(): Flow<List<FavoriteEntity>>
    
    // ═══════════════════════════════════════════════════════════════════════════
    // REACTIVE FAVORITE IDS FLOW (Room-first)
    // Replaces suspend getAllFavoriteIds() for real-time favorite updates
    // ═══════════════════════════════════════════════════════════════════════════
    @Query("SELECT mediaId FROM favorites")
    fun getAllFavoriteIdsFlow(): Flow<List<Long>>
    
    @Query("SELECT mediaId FROM favorites")
    suspend fun getAllFavoriteIds(): List<Long>
    
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE mediaId = :mediaId)")
    suspend fun isFavorite(mediaId: Long): Boolean
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)
    
    @Query("DELETE FROM favorites WHERE mediaId = :mediaId")
    suspend fun removeFavorite(mediaId: Long)

    @Query("DELETE FROM favorites WHERE mediaId IN (:mediaIds)")
    suspend fun removeFavorites(mediaIds: List<Long>)
    
    @Query("DELETE FROM favorites")
    suspend fun clearAllFavorites()
}
