package com.prantiux.pixelgallery.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.prantiux.pixelgallery.viewmodel.GridType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// DataStore instance
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {
    companion object {
        private val GRID_TYPE_KEY = stringPreferencesKey("grid_type")
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
}
