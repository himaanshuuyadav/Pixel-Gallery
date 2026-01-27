package com.prantiux.pixelgallery.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * DataStore for storing video playback positions
 * Used for resume playback feature
 */
private val Context.videoPositionDataStore: DataStore<Preferences> by preferencesDataStore(name = "video_positions")

class VideoPositionDataStore(private val context: Context) {
    
    /**
     * Get saved playback position for a video URI
     */
    fun getPositionFlow(videoUri: String): Flow<Long> = context.videoPositionDataStore.data
        .map { preferences ->
            val key = longPreferencesKey(videoUri)
            preferences[key] ?: 0L
        }
    
    /**
     * Save playback position for a video URI
     */
    suspend fun savePosition(videoUri: String, position: Long) {
        context.videoPositionDataStore.edit { preferences ->
            val key = longPreferencesKey(videoUri)
            preferences[key] = position
        }
    }
    
    /**
     * Clear saved position for a video (when video completes)
     */
    suspend fun clearPosition(videoUri: String) {
        context.videoPositionDataStore.edit { preferences ->
            val key = longPreferencesKey(videoUri)
            preferences.remove(key)
        }
    }
    
    /**
     * Get position synchronously using first() - use in suspend context
     */
    suspend fun getPosition(videoUri: String): Long {
        val key = longPreferencesKey(videoUri)
        val preferences = context.videoPositionDataStore.data
        return preferences.map { it[key] ?: 0L }.first()
    }
}
