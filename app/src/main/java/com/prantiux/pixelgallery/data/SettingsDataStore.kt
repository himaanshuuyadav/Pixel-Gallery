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
        private val STICKY_DATE_HEADERS_KEY = booleanPreferencesKey("sticky_date_headers")
        private val HIDE_EMPTY_ALBUMS_KEY = booleanPreferencesKey("hide_empty_albums")
        private val DEFAULT_TAB_KEY = stringPreferencesKey("default_tab")
        private val LAST_USED_TAB_KEY = stringPreferencesKey("last_used_tab")
        private val THUMBNAIL_QUALITY_KEY = stringPreferencesKey("thumbnail_quality")
        private val CORNER_TYPE_KEY = stringPreferencesKey("corner_type")
        private val BADGE_TYPE_KEY = stringPreferencesKey("badge_type")
        private val APP_THEME_KEY = stringPreferencesKey("app_theme")
        private val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color")
        private val AMOLED_MODE_KEY = booleanPreferencesKey("amoled_mode")
        private val SHOW_BADGE_KEY = booleanPreferencesKey("show_badge")
        private val SHOW_COMPLETED_DURATION_KEY = booleanPreferencesKey("show_completed_duration")
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
    
    /**
     * Get sticky date headers state as Flow
     */
    val stickyDateHeadersFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[STICKY_DATE_HEADERS_KEY] ?: true
        }
    
    /**
     * Save sticky date headers preference
     */
    suspend fun saveStickyDateHeaders(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[STICKY_DATE_HEADERS_KEY] = enabled
        }
    }
    
    /**
     * Get hide empty albums state as Flow
     */
    val hideEmptyAlbumsFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[HIDE_EMPTY_ALBUMS_KEY] ?: false
        }
    
    /**
     * Save hide empty albums preference
     */
    suspend fun saveHideEmptyAlbums(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[HIDE_EMPTY_ALBUMS_KEY] = enabled
        }
    }
    
    /**
     * Get default tab as Flow
     */
    val defaultTabFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            preferences[DEFAULT_TAB_KEY] ?: "Gallery"
        }
    
    /**
     * Save default tab preference
     */
    suspend fun saveDefaultTab(tab: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[DEFAULT_TAB_KEY] = tab
        }
    }
    
    /**
     * Get last used tab as Flow
     */
    val lastUsedTabFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            preferences[LAST_USED_TAB_KEY] ?: "Gallery"
        }
    
    /**
     * Save last used tab preference
     */
    suspend fun saveLastUsedTab(tab: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[LAST_USED_TAB_KEY] = tab
        }
    }
    
    /**
     * Get thumbnail quality as Flow
     */
    val thumbnailQualityFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            preferences[THUMBNAIL_QUALITY_KEY] ?: "Standard"
        }
    
    /**
     * Save thumbnail quality preference
     */
    suspend fun saveThumbnailQuality(quality: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[THUMBNAIL_QUALITY_KEY] = quality
        }
    }
    
    /**
     * Get corner type as Flow
     */
    val cornerTypeFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            preferences[CORNER_TYPE_KEY] ?: "Rounded"
        }
    
    /**
     * Save corner type preference
     */
    suspend fun saveCornerType(cornerType: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[CORNER_TYPE_KEY] = cornerType
        }
    }
    
    /**
     * Get badge type as Flow
     */
    val badgeTypeFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            preferences[BADGE_TYPE_KEY] ?: "Duration with icon"
        }
    
    /**
     * Save badge type preference
     */
    suspend fun saveBadgeType(badgeType: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[BADGE_TYPE_KEY] = badgeType
        }
    }
    
    /**
     * Get app theme as Flow
     */
    val appThemeFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            preferences[APP_THEME_KEY] ?: "System Default"
        }
    
    /**
     * Save app theme preference
     */
    suspend fun saveAppTheme(theme: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[APP_THEME_KEY] = theme
        }
    }
    
    /**
     * Get dynamic color state as Flow
     */
    val dynamicColorFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[DYNAMIC_COLOR_KEY] ?: true
        }
    
    /**
     * Save dynamic color preference
     */
    suspend fun saveDynamicColor(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[DYNAMIC_COLOR_KEY] = enabled
        }
    }
    
    /**
     * Get AMOLED mode state as Flow
     */
    val amoledModeFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[AMOLED_MODE_KEY] ?: false
        }
    
    /**
     * Save AMOLED mode preference
     */
    suspend fun saveAmoledMode(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[AMOLED_MODE_KEY] = enabled
        }
    }
    
    /**
     * Get show badge state as Flow
     */
    val showBadgeFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[SHOW_BADGE_KEY] ?: true
        }
    
    /**
     * Save show badge preference
     */
    suspend fun saveShowBadge(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[SHOW_BADGE_KEY] = enabled
        }
    }
    
    /**
     * Get show completed duration state as Flow
     */
    val showCompletedDurationFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[SHOW_COMPLETED_DURATION_KEY] ?: false
        }
    
    /**
     * Save show completed duration preference
     */
    suspend fun saveShowCompletedDuration(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[SHOW_COMPLETED_DURATION_KEY] = enabled
        }
    }
}
