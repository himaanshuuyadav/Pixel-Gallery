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
        private val SWIPE_DOWN_TO_CLOSE_KEY = booleanPreferencesKey("swipe_down_to_close")
        private val SWIPE_UP_TO_DETAILS_KEY = booleanPreferencesKey("swipe_up_to_details")
        private val DOUBLE_TAP_TO_ZOOM_KEY = booleanPreferencesKey("double_tap_to_zoom")
        private val DOUBLE_TAP_ZOOM_LEVEL_KEY = stringPreferencesKey("double_tap_zoom_level")
        // Playback settings
        private val AUTO_PLAY_VIDEOS_KEY = booleanPreferencesKey("auto_play_videos")
        private val RESUME_PLAYBACK_KEY = booleanPreferencesKey("resume_playback")
        private val LOOP_VIDEOS_KEY = booleanPreferencesKey("loop_videos")
        private val KEEP_SCREEN_ON_KEY = booleanPreferencesKey("keep_screen_on")
        private val MUTE_BY_DEFAULT_KEY = booleanPreferencesKey("mute_by_default")
        private val SHOW_CONTROLS_ON_TAP_KEY = booleanPreferencesKey("show_controls_on_tap")
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
    
    /**
     * Get swipe down to close state as Flow
     */
    val swipeDownToCloseFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[SWIPE_DOWN_TO_CLOSE_KEY] ?: true
        }
    
    /**
     * Save swipe down to close preference
     */
    suspend fun saveSwipeDownToClose(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[SWIPE_DOWN_TO_CLOSE_KEY] = enabled
        }
    }
    
    /**
     * Get swipe up to details state as Flow
     */
    val swipeUpToDetailsFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[SWIPE_UP_TO_DETAILS_KEY] ?: true
        }
    
    /**
     * Save swipe up to details preference
     */
    suspend fun saveSwipeUpToDetails(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[SWIPE_UP_TO_DETAILS_KEY] = enabled
        }
    }
    
    /**
     * Get double tap to zoom state as Flow
     */
    val doubleTapToZoomFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[DOUBLE_TAP_TO_ZOOM_KEY] ?: true
        }
    
    /**
     * Save double tap to zoom preference
     */
    suspend fun saveDoubleTapToZoom(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[DOUBLE_TAP_TO_ZOOM_KEY] = enabled
        }
    }
    
    /**
     * Get double tap zoom level as Flow
     */
    val doubleTapZoomLevelFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            preferences[DOUBLE_TAP_ZOOM_LEVEL_KEY] ?: "Fit"
        }
    
    /**
     * Save double tap zoom level preference
     */
    suspend fun saveDoubleTapZoomLevel(level: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[DOUBLE_TAP_ZOOM_LEVEL_KEY] = level
        }
    }
    
    // Playback settings
    
    /**
     * Get auto-play videos state as Flow
     */
    val autoPlayVideosFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[AUTO_PLAY_VIDEOS_KEY] ?: true
        }
    
    /**
     * Save auto-play videos preference
     */
    suspend fun saveAutoPlayVideos(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[AUTO_PLAY_VIDEOS_KEY] = enabled
        }
    }
    
    /**
     * Get resume playback state as Flow
     */
    val resumePlaybackFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[RESUME_PLAYBACK_KEY] ?: true
        }
    
    /**
     * Save resume playback preference
     */
    suspend fun saveResumePlayback(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[RESUME_PLAYBACK_KEY] = enabled
        }
    }
    
    /**
     * Get loop videos state as Flow
     */
    val loopVideosFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[LOOP_VIDEOS_KEY] ?: false
        }
    
    /**
     * Save loop videos preference
     */
    suspend fun saveLoopVideos(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[LOOP_VIDEOS_KEY] = enabled
        }
    }
    
    /**
     * Get keep screen on state as Flow
     */
    val keepScreenOnFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[KEEP_SCREEN_ON_KEY] ?: true
        }
    
    /**
     * Save keep screen on preference
     */
    suspend fun saveKeepScreenOn(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEEP_SCREEN_ON_KEY] = enabled
        }
    }
    
    /**
     * Get mute by default state as Flow
     */
    val muteByDefaultFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[MUTE_BY_DEFAULT_KEY] ?: false
        }
    
    /**
     * Save mute by default preference
     */
    suspend fun saveMuteByDefault(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[MUTE_BY_DEFAULT_KEY] = enabled
        }
    }
    
    /**
     * Get show controls on tap state as Flow
     */
    val showControlsOnTapFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[SHOW_CONTROLS_ON_TAP_KEY] ?: true
        }
    
    /**
     * Save show controls on tap preference
     */
    suspend fun saveShowControlsOnTap(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[SHOW_CONTROLS_ON_TAP_KEY] = enabled
        }
    }
}
