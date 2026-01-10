package com.prantiux.pixelgallery.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

// DataStore instance
private val Context.recentSearchesDataStore: DataStore<Preferences> by preferencesDataStore(name = "recent_searches")

@Serializable
data class SearchHistoryEntry(
    val query: String,
    val timestamp: Long
)

class RecentSearchesDataStore(private val context: Context) {
    companion object {
        private val RECENT_SEARCHES_KEY = stringPreferencesKey("recent_searches_list")
        private const val MAX_RECENT_SEARCHES = 10
    }
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    /**
     * Get recent searches as Flow
     */
    val recentSearchesFlow: Flow<List<String>> = context.recentSearchesDataStore.data
        .map { preferences ->
            val jsonString = preferences[RECENT_SEARCHES_KEY] ?: "[]"
            try {
                val entries: List<SearchHistoryEntry> = json.decodeFromString(jsonString)
                // Return only query strings, sorted by most recent
                entries.sortedByDescending { it.timestamp }.map { it.query }
            } catch (e: Exception) {
                emptyList()
            }
        }
    
    /**
     * Add a search query to recent searches
     * - Avoids duplicates (moves to top if exists)
     * - Limits to MAX_RECENT_SEARCHES
     * - Only saves non-empty queries
     */
    suspend fun addRecentSearch(query: String) {
        if (query.isBlank()) return
        
        context.recentSearchesDataStore.edit { preferences ->
            val jsonString = preferences[RECENT_SEARCHES_KEY] ?: "[]"
            val existingEntries: MutableList<SearchHistoryEntry> = try {
                json.decodeFromString(jsonString)
            } catch (e: Exception) {
                mutableListOf()
            }
            
            // Remove duplicate if exists
            existingEntries.removeAll { it.query.equals(query, ignoreCase = true) }
            
            // Add new entry at the beginning
            existingEntries.add(0, SearchHistoryEntry(query, System.currentTimeMillis()))
            
            // Limit to MAX_RECENT_SEARCHES
            val trimmedEntries = existingEntries.take(MAX_RECENT_SEARCHES)
            
            // Save back to DataStore
            preferences[RECENT_SEARCHES_KEY] = json.encodeToString(trimmedEntries)
        }
    }
    
    /**
     * Clear all recent searches
     */
    suspend fun clearAllRecentSearches() {
        context.recentSearchesDataStore.edit { preferences ->
            preferences.remove(RECENT_SEARCHES_KEY)
        }
    }
    
    /**
     * Remove a specific search from history
     */
    suspend fun removeRecentSearch(query: String) {
        context.recentSearchesDataStore.edit { preferences ->
            val jsonString = preferences[RECENT_SEARCHES_KEY] ?: "[]"
            val existingEntries: MutableList<SearchHistoryEntry> = try {
                json.decodeFromString(jsonString)
            } catch (e: Exception) {
                mutableListOf()
            }
            
            // Remove matching entry
            existingEntries.removeAll { it.query.equals(query, ignoreCase = true) }
            
            // Save back to DataStore
            preferences[RECENT_SEARCHES_KEY] = json.encodeToString(existingEntries)
        }
    }
}
