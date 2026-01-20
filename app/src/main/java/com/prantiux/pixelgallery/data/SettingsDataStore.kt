package com.prantiux.pixelgallery.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.prantiux.pixelgallery.viewmodel.GridType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// DataStore instance
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {
    companion object {
        private val GRID_TYPE_KEY = stringPreferencesKey("grid_type")
        private val SELECTED_ALBUMS_KEY = stringSetPreferencesKey("selected_albums")
        private val PINCH_GESTURE_ENABLED_KEY = booleanPreferencesKey("pinch_gesture_enabled")
    }
    
    /**
     * Get grid type as Flow
     */
    val gridTypeFlow: Flow<GridType> = context.settingsDataStore.data
        .map { preferences ->
            val gridTypeString = preferences[GRID_TYPE_KEY] ?: GridType.DAY.name
            try {
                GridType.valueOf(gridTypeString)
            } catch (e: Exception) {
                GridType.DAY
            }
        }
    
    /**
     * Save grid type preference
     */
    suspend fun saveGridType(gridType: GridType) {
        context.settingsDataStore.edit { preferences ->
            preferences[GRID_TYPE_KEY] = gridType.name
        }
    }
    
    /**
     * Get selected albums as Flow
     */
    val selectedAlbumsFlow: Flow<Set<String>> = context.settingsDataStore.data
        .map { preferences ->
            preferences[SELECTED_ALBUMS_KEY] ?: emptySet()
        }
    
    /**
     * Save selected albums preference
     */
    suspend fun saveSelectedAlbums(albumIds: Set<String>) {
        context.settingsDataStore.edit { preferences ->
            preferences[SELECTED_ALBUMS_KEY] = albumIds
        }
    }
    
    /**
     * Get pinch gesture enabled state as Flow
     */
    val pinchGestureEnabledFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[PINCH_GESTURE_ENABLED_KEY] ?: true
        }
    
    /**
     * Save pinch gesture enabled preference
     */
    suspend fun savePinchGestureEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[PINCH_GESTURE_ENABLED_KEY] = enabled
        }
    }
}
