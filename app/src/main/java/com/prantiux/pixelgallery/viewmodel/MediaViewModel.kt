package com.prantiux.pixelgallery.viewmodel

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.prantiux.pixelgallery.data.AlbumRepository
import com.prantiux.pixelgallery.data.AppDatabase
import com.prantiux.pixelgallery.data.FavoriteEntity
import com.prantiux.pixelgallery.data.MediaContentObserver
import com.prantiux.pixelgallery.data.MediaEntity
import com.prantiux.pixelgallery.data.MediaRepository
import com.prantiux.pixelgallery.data.RecentSearchesDataStore
import com.prantiux.pixelgallery.data.SettingsDataStore
import com.prantiux.pixelgallery.data.toMediaItem
import com.prantiux.pixelgallery.data.toMediaItems
import com.prantiux.pixelgallery.BuildConfig
import com.prantiux.pixelgallery.ml.ImageLabelWorker
import com.prantiux.pixelgallery.model.Album
import com.prantiux.pixelgallery.model.CategorizedAlbums
import com.prantiux.pixelgallery.model.MediaGroup
import com.prantiux.pixelgallery.model.MediaItem
import com.prantiux.pixelgallery.search.SearchEngine
import com.prantiux.pixelgallery.search.SearchResultFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.SharingStarted
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class SortMode {
    DATE_DESC, DATE_ASC, NAME_ASC, NAME_DESC, SIZE_DESC, SIZE_ASC
}

enum class GridType {
    DAY, MONTH
}

/**
 * Holds computed media state after heavy processing
 * Used to defer StateFlow updates until Main thread
 */
private data class ComputedMediaState(
    val images: List<MediaItem>,
    val videos: List<MediaItem>,
    val combined: List<MediaItem>,
    val albums: List<Album>,
    val groupedMedia: List<MediaGroup>,
    val groupingDurationMs: Long
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MediaViewModel : ViewModel() {
    init {
        Log.d("VM_INIT", "ViewModel constructor: Starting property initialization")
    }
    
    private lateinit var repository: MediaRepository
    private lateinit var albumRepository: AlbumRepository
    private lateinit var recentSearchesDataStore: RecentSearchesDataStore
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var database: AppDatabase
    private var mediaContentObserver: MediaContentObserver? = null

    // ═════════════════════════════════════════════════════════════════════════════════
    // DEPRECATED StateFlows: MediaStore-first architecture (DO NOT USE)
    // ═════════════════════════════════════════════════════════════════════════════════
    // Use Room-first flows instead (mediaFlow, imagesFlow, videosFlow, etc.)
    // These are kept for backward compatibility during migration but will be removed.
    // ═════════════════════════════════════════════════════════════════════════════════
    
    @Deprecated("Use imagesFlow instead - Room-first architecture", ReplaceWith("imagesFlow"))
    private val _images = MutableStateFlow<List<MediaItem>>(emptyList())
    @Deprecated("Use imagesFlow instead - Room-first architecture", ReplaceWith("imagesFlow"))
    val images: StateFlow<List<MediaItem>> = _images.asStateFlow()

    @Deprecated("Use videosFlow instead - Room-first architecture", ReplaceWith("videosFlow"))
    private val _videos = MutableStateFlow<List<MediaItem>>(emptyList())
    @Deprecated("Use videosFlow instead - Room-first architecture", ReplaceWith("videosFlow"))
    val videos: StateFlow<List<MediaItem>> = _videos.asStateFlow()
    
    @Deprecated("Use mediaFlow instead - Room-first architecture", ReplaceWith("mediaFlow"))
    private val _sortedMedia = MutableStateFlow<List<MediaItem>>(emptyList())
    @Deprecated("Use mediaFlow instead - Room-first architecture", ReplaceWith("mediaFlow"))
    val sortedMedia: StateFlow<List<MediaItem>> = _sortedMedia.asStateFlow()

    @Deprecated("Use groupedMediaFlow instead - Room-first architecture", ReplaceWith("groupedMediaFlow"))
    private val _groupedMedia = MutableStateFlow<List<MediaGroup>>(emptyList())
    @Deprecated("Use groupedMediaFlow instead - Room-first architecture", ReplaceWith("groupedMediaFlow"))
    val groupedMedia: StateFlow<List<MediaGroup>> = _groupedMedia.asStateFlow()
    
    @Deprecated("Use imagesFlow instead - Room-first architecture", ReplaceWith("imagesFlow"))
    private val _allImagesUnfiltered = MutableStateFlow<List<MediaItem>>(emptyList())
    @Deprecated("Use imagesFlow instead - Room-first architecture", ReplaceWith("imagesFlow"))
    val allImagesUnfiltered: StateFlow<List<MediaItem>> = _allImagesUnfiltered.asStateFlow()
    
    @Deprecated("Use videosFlow instead - Room-first architecture", ReplaceWith("videosFlow"))
    private val _allVideosUnfiltered = MutableStateFlow<List<MediaItem>>(emptyList())
    @Deprecated("Use videosFlow instead - Room-first architecture", ReplaceWith("videosFlow"))
    val allVideosUnfiltered: StateFlow<List<MediaItem>> = _allVideosUnfiltered.asStateFlow()
    
    // DEPRECATED: _favoriteIds removed - was redundant manual cache causing sync issues
    // Room's getAllFavoriteIdsFlow() is now the single source of truth
    
    @Deprecated("Use favoritesFlow instead - Room-first architecture", ReplaceWith("favoritesFlow"))
    private val _favoriteItems = MutableStateFlow<List<MediaItem>>(emptyList())
    @Deprecated("Use favoritesFlow instead - Room-first architecture", ReplaceWith("favoritesFlow"))
    val favoriteItems: StateFlow<List<MediaItem>> = _favoriteItems.asStateFlow()

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _smartAlbumThumbnailCache = mutableStateMapOf<String, android.net.Uri?>()
    val smartAlbumThumbnailCache: SnapshotStateMap<String, android.net.Uri?>
        get() = _smartAlbumThumbnailCache

    @Deprecated("Use categorizedAlbumsFlow instead - Room-first architecture", ReplaceWith("categorizedAlbumsFlow"))
    private val _categorizedAlbums = MutableStateFlow<CategorizedAlbums>(CategorizedAlbums(emptyList(), emptyList()))
    @Deprecated("Use categorizedAlbumsFlow instead - Room-first architecture", ReplaceWith("categorizedAlbumsFlow"))
    val categorizedAlbums: StateFlow<CategorizedAlbums> = _categorizedAlbums.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.DATE_DESC)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    private val _selectedItems = MutableStateFlow<Set<MediaItem>>(emptySet())
    val selectedItems: StateFlow<Set<MediaItem>> = _selectedItems.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()
    
    private val _scrollbarVisible = MutableStateFlow(false)
    val scrollbarVisible: StateFlow<Boolean> = _scrollbarVisible.asStateFlow()
    
    private val _scrollbarMonth = MutableStateFlow("")
    val scrollbarMonth: StateFlow<String> = _scrollbarMonth.asStateFlow()
    
    private val _isScrollbarDragging = MutableStateFlow(false)
    val isScrollbarDragging: StateFlow<Boolean> = _isScrollbarDragging.asStateFlow()
    
    private val _trashedItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val trashedItems: StateFlow<List<MediaItem>> = _trashedItems.asStateFlow()
    
    private val _isLoadingTrash = MutableStateFlow(false)
    val isLoadingTrash: StateFlow<Boolean> = _isLoadingTrash.asStateFlow()
    
    // Trash selection mode
    private val _isTrashSelectionMode = MutableStateFlow(false)
    val isTrashSelectionMode: StateFlow<Boolean> = _isTrashSelectionMode.asStateFlow()
    
    private val _selectedTrashItems = MutableStateFlow<Set<MediaItem>>(emptySet())
    val selectedTrashItems: StateFlow<Set<MediaItem>> = _selectedTrashItems.asStateFlow()
    
    // Trash request launcher for Android 11+
    private var trashRequestLauncher: ((android.app.PendingIntent) -> Unit)? = null
    private var restoreRequestLauncher: ((android.app.PendingIntent) -> Unit)? = null
    private var permanentDeleteRequestLauncher: ((android.app.PendingIntent) -> Unit)? = null
    
    // ML labeling progress state
    private val _labelingProgress = MutableStateFlow<Pair<Int, Int>?>(null) // (processed, total)
    val labelingProgress: StateFlow<Pair<Int, Int>?> = _labelingProgress.asStateFlow()
    
    private val _isLabelingInProgress = MutableStateFlow(false)
    val isLabelingInProgress: StateFlow<Boolean> = _isLabelingInProgress.asStateFlow()
    
    // Grid type state
    private val _gridType = MutableStateFlow(GridType.DAY)
    val gridType: StateFlow<GridType> = _gridType.asStateFlow()
    
    // Selected albums for gallery view
    private val _selectedAlbums = MutableStateFlow<Set<String>>(emptySet())
    val selectedAlbums: StateFlow<Set<String>> = _selectedAlbums.asStateFlow()
    
    // Pinch gesture enabled state
    private val _pinchGestureEnabled = MutableStateFlow(false)
    val pinchGestureEnabled: StateFlow<Boolean> = _pinchGestureEnabled.asStateFlow()
    
    // Copy to album dialog state
    private val _showCopyToAlbumDialog = MutableStateFlow(false)
    val showCopyToAlbumDialog: StateFlow<Boolean> = _showCopyToAlbumDialog.asStateFlow()
    
    private val _itemsToCopy = MutableStateFlow<List<MediaItem>>(emptyList())
    val itemsToCopy: StateFlow<List<MediaItem>> = _itemsToCopy.asStateFlow()
    
    // Move to album dialog state
    private val _showMoveToAlbumDialog = MutableStateFlow(false)
    val showMoveToAlbumDialog: StateFlow<Boolean> = _showMoveToAlbumDialog.asStateFlow()
    
    private val _itemsToMove = MutableStateFlow<List<MediaItem>>(emptyList())
    val itemsToMove: StateFlow<List<MediaItem>> = _itemsToMove.asStateFlow()
    
    private val _copySuccessMessage = MutableStateFlow<String?>(null)
    val copySuccessMessage: StateFlow<String?> = _copySuccessMessage.asStateFlow()
    
    private val _moveSuccessMessage = MutableStateFlow<String?>(null)
    val moveSuccessMessage: StateFlow<String?> = _moveSuccessMessage.asStateFlow()
    
    private val _deleteSuccessMessage = MutableStateFlow<String?>(null)
    val deleteSuccessMessage: StateFlow<String?> = _deleteSuccessMessage.asStateFlow()
    
    private val _restoreSuccessMessage = MutableStateFlow<String?>(null)
    val restoreSuccessMessage: StateFlow<String?> = _restoreSuccessMessage.asStateFlow()
    
    // Store URIs being processed
    private var pendingRestoreUris: List<android.net.Uri> = emptyList()
    private var pendingDeleteUris: List<android.net.Uri> = emptyList()
    
    fun setTrashRequestLauncher(launcher: (android.app.PendingIntent) -> Unit) {
        trashRequestLauncher = launcher
    }
    
    fun setRestoreRequestLauncher(launcher: (android.app.PendingIntent) -> Unit) {
        restoreRequestLauncher = launcher
    }
    
    fun setPermanentDeleteRequestLauncher(launcher: (android.app.PendingIntent) -> Unit) {
        permanentDeleteRequestLauncher = launcher
    }
    
    fun setScrollbarVisible(visible: Boolean) {
        _scrollbarVisible.value = visible
    }
    
    fun setScrollbarMonth(month: String) {
        _scrollbarMonth.value = month
    }
    
    fun setScrollbarDragging(dragging: Boolean) {
        _isScrollbarDragging.value = dragging
    }

    // Search state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @Deprecated("Use searchMediaFlow(query) instead - Room-first architecture", ReplaceWith("searchMediaFlow(searchQuery.value)"))
    private val _searchResults = MutableStateFlow<SearchEngine.SearchResult>(
        SearchEngine.SearchResult(emptyList(), emptyList(), "")
    )
    @Deprecated("Use searchMediaFlow(query) instead - Room-first architecture", ReplaceWith("searchMediaFlow(searchQuery.value)"))
    val searchResults: StateFlow<SearchEngine.SearchResult> = _searchResults.asStateFlow()

    private var searchJob: Job? = null
    
    // Cached all media for search (queried once)
    private val _allMediaCached = MutableStateFlow<List<MediaItem>>(emptyList())
    
    // Recent searches from DataStore
    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    // Media overlay state
    data class MediaOverlayState(
        val isVisible: Boolean = false,
        val mediaType: String = "photos",
        val albumId: String = "all",
        val selectedIndex: Int = 0,
        val searchQuery: String? = null,
        val thumbnailBounds: com.prantiux.pixelgallery.ui.animation.SharedElementBounds? = null
    )

    private val _overlayState = MutableStateFlow(MediaOverlayState())
    val overlayState: StateFlow<MediaOverlayState> = _overlayState.asStateFlow()

    // ═════════════════════════════════════════════════════════════════════════════════
    // ROOM AS SINGLE SOURCE OF TRUTH (Phase 3 - ROOM FIRST CLEAN CUT)
    // All media data flows from Room; MediaStore is for syncing only
    // ═════════════════════════════════════════════════════════════════════════════════
    
    // Database ready state - triggers reactive reconnection of flows
    private val _databaseReady = MutableStateFlow(false)
    
    // ─────────────────────────────────────────────────────────────────────────────────
    // PRIMARY FLOW: Media from Room (sorted by user preference)
    // ─────────────────────────────────────────────────────────────────────────────────
    private val mediaEntitiesFlow: StateFlow<List<MediaEntity>> = _databaseReady
        .flatMapLatest { ready ->
            if (!ready) {
                Log.d("VM_FLOW", "Database not ready yet")
                flowOf(emptyList())
            } else {
                Log.d("VM_FLOW", "Database ready — connecting DAO")
                _sortMode.flatMapLatest { sortMode ->
                    when (sortMode) {
                        SortMode.DATE_DESC -> database.mediaDao().getMediaByDateDesc()
                        SortMode.DATE_ASC -> database.mediaDao().getMediaByDateAsc()
                        SortMode.NAME_ASC -> database.mediaDao().getMediaByNameAsc()
                        SortMode.NAME_DESC -> database.mediaDao().getMediaByNameDesc()
                        SortMode.SIZE_DESC -> database.mediaDao().getMediaBySizeDesc()
                        SortMode.SIZE_ASC -> database.mediaDao().getMediaBySizeAsc()
                    }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    /**
     * PRIMARY UI FLOW: All media as MediaItem (Room-first - fully reactive)
     *  
     * UI screens should observe this instead of sortedMedia.
     * Automatically updates when:
     * - Sort mode changes
     * - MediaStore sync adds/removes media
     * - Favorite status changes
     * - Database is updated
     */
    val mediaFlow: StateFlow<List<MediaItem>> = _databaseReady
        .flatMapLatest { ready ->
            if (!ready) {
                flowOf(emptyList())
            } else {
                mediaEntitiesFlow.combine(database.favoriteDao().getAllFavoriteIdsFlow()) { entities, favIds ->
                    android.util.Log.d("ROOM_FLOW", "Room emitted ${entities.size} items")
                    entities.toMediaItems(favIds.toSet())
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    /**
     * ALBUMS FLOW: Album list from Room (Room-first)
     * 
     * Groups media by bucketId directly from Room.
     * UI should observe this instead of categorizedAlbums.
     */
    /**
     * ALBUMS FLOW: Album list from Room (Room-first - derived from mediaFlow)
     * 
     * Groups media by bucketId directly from mediaFlow.
     * UI should observe this instead of categorizedAlbums.
     */
    val albumsFlow: StateFlow<List<Album>> = mediaFlow
        .map { items ->
            if (items.isEmpty()) {
                emptyList()
            } else {
                android.util.Log.d("ROOM_FLOW", "Albums: Generated ${items.groupBy { it.bucketId }.size} albums")
                items
                    .groupBy { it.bucketId }
                    .map { (bucketId, groupItems) ->
                        Album(
                            id = bucketId,
                            name = groupItems.first().bucketName,
                            coverUri = groupItems.firstOrNull()?.uri,
                            itemCount = groupItems.size,
                            bucketDisplayName = groupItems.first().bucketName,
                            topMediaUris = groupItems.take(6).map { it.uri },
                            topMediaItems = groupItems.take(6)
                        )
                    }
                    .sortedByDescending { it.itemCount }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    /**
     * CATEGORIZED ALBUMS FLOW: Albums categorized into main/other (Room-first)
     * 
     * Derived from albumsFlow, splits albums into:
     * - mainAlbums: First 4 albums (top albums by item count)
     * - otherAlbums: Remaining albums
     */
    val categorizedAlbumsFlow: StateFlow<CategorizedAlbums> = albumsFlow
        .map { albums ->
            android.util.Log.d("ROOM_FLOW", "Categorized Albums: ${albums.size} total (${albums.take(4).size} main, ${albums.drop(4).size} other)")
            CategorizedAlbums(
                mainAlbums = albums.take(4),
                otherAlbums = albums.drop(4)
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CategorizedAlbums(emptyList(), emptyList())
        )
    
    /**
     * SEARCH FLOW: Search results from Room (Room-first)
     * 
     * Searches directly in Room database.
     * Updates automatically as search query changes.
     */
    fun searchMediaFlow(query: String): kotlinx.coroutines.flow.Flow<List<MediaItem>> {
        android.util.Log.d("SEARCH_ROOM", "═══════════════════════════════════════════════════════════")
        android.util.Log.d("SEARCH_ROOM", "🔍 searchMediaFlow() called with query='$query'")
        
        if (query.isBlank()) {
            android.util.Log.d("SEARCH_ROOM", "  → Empty query, returning emptyList")
            return kotlinx.coroutines.flow.flowOf(emptyList())
        }
        
        val normalizedQuery = query.lowercase(java.util.Locale.getDefault()).trim()
        android.util.Log.d("SEARCH_ROOM", "  Normalized: '$normalizedQuery'")
        
        // DETECT FILTERS using SearchEngine's logic
        val dateFilter = detectSearchDateFilter(normalizedQuery)
        val typeFilter = detectSearchMediaTypeFilter(normalizedQuery)
        val sizeFilter = detectSearchSizeFilter(normalizedQuery)
        val cleanQuery = removeKeywords(normalizedQuery)
        
        android.util.Log.d("SEARCH_ROOM", "  Detected filters:")
        android.util.Log.d("SEARCH_ROOM", "    - Date: ${dateFilter?.toString() ?: "none"}")
        android.util.Log.d("SEARCH_ROOM", "    - Type: ${typeFilter?.toString() ?: "none"}")
        android.util.Log.d("SEARCH_ROOM", "    - Size: ${sizeFilter?.toString() ?: "none"}")
        android.util.Log.d("SEARCH_ROOM", "    - Clean query: '$cleanQuery'")
        
        // SELECT APPROPRIATE DAO QUERY based on detected filters
        val daoQuery: kotlinx.coroutines.flow.Flow<List<MediaEntity>> = when {
            // ML LABEL SEARCH (if query matches a label-like pattern)
            cleanQuery.isNotEmpty() && typeFilter == null && dateFilter == null && sizeFilter == null -> {
                android.util.Log.d("SEARCH_ROOM", "  → Using searchByLabel() for potential ML search")
                database.mediaDao().searchByLabel(cleanQuery)
            }
            
            // SCREENSHOT (special case)
            normalizedQuery.contains("screenshot") -> {
                android.util.Log.d("SEARCH_ROOM", "  → Using searchByScreenshots()")
                database.mediaDao().searchByScreenshots(cleanQuery)
            }
            
            // CAMERA (special case)
            normalizedQuery.contains("camera") || normalizedQuery.contains("dcim") -> {
                android.util.Log.d("SEARCH_ROOM", "  → Using searchByCamera()")
                database.mediaDao().searchByCamera(cleanQuery)
            }
            
            // GIF (special case)
            normalizedQuery.contains("gif") -> {
                android.util.Log.d("SEARCH_ROOM", "  → Using searchByGif()")
                database.mediaDao().searchByGif(cleanQuery)
            }
            
            // ALL THREE FILTERS (Type + Size + Date)
            typeFilter != null && sizeFilter != null && dateFilter != null -> {
                android.util.Log.d("SEARCH_ROOM", "  → Using searchByTypeAndSizeAndDate() combo")
                val (isVideo, typeName) = parseMediaTypeFilter(typeFilter)
                val (minSize, maxSize, sizeName) = parseSizeFilter(sizeFilter)
                val (startMs, endMs, dateName) = parseDateFilter(dateFilter)
                
                android.util.Log.d("SEARCH_ROOM", "    Type: $typeName, Size: $sizeName, Date: $dateName")
                database.mediaDao().searchByTypeAndSizeAndDate(
                    query = cleanQuery,
                    isVideo = isVideo,
                    minSize = minSize,
                    maxSize = maxSize,
                    startMs = startMs,
                    endMs = endMs
                )
            }
            
            // TYPE + DATE
            typeFilter != null && dateFilter != null && sizeFilter == null -> {
                android.util.Log.d("SEARCH_ROOM", "  → Using searchByTypeAndDate() combo")
                val (isVideo, typeName) = parseMediaTypeFilter(typeFilter)
                val (startMs, endMs, dateName) = parseDateFilter(dateFilter)
                
                android.util.Log.d("SEARCH_ROOM", "    Type: $typeName, Date: $dateName")
                database.mediaDao().searchByTypeAndDate(
                    query = cleanQuery,
                    isVideo = isVideo,
                    startMs = startMs,
                    endMs = endMs
                )
            }
            
            // TYPE + SIZE
            typeFilter != null && sizeFilter != null && dateFilter == null -> {
                android.util.Log.d("SEARCH_ROOM", "  → Using searchByTypeAndSize() combo")
                val (isVideo, typeName) = parseMediaTypeFilter(typeFilter)
                val (minSize, maxSize, sizeName) = parseSizeFilter(sizeFilter)
                
                android.util.Log.d("SEARCH_ROOM", "    Type: $typeName, Size: $sizeName")
                database.mediaDao().searchByTypeAndSize(
                    query = cleanQuery,
                    isVideo = isVideo,
                    minSize = minSize,
                    maxSize = maxSize
                )
            }
            
            // SIZE + DATE
            sizeFilter != null && dateFilter != null && typeFilter == null -> {
                android.util.Log.d("SEARCH_ROOM", "  → Using searchBySizeAndDate() combo")
                val (minSize, maxSize, sizeName) = parseSizeFilter(sizeFilter)
                val (startMs, endMs, dateName) = parseDateFilter(dateFilter)
                
                android.util.Log.d("SEARCH_ROOM", "    Size: $sizeName, Date: $dateName")
                database.mediaDao().searchBySizeAndDate(
                    query = cleanQuery,
                    minSize = minSize,
                    maxSize = maxSize,
                    startMs = startMs,
                    endMs = endMs
                )
            }
            
            // ONLY TYPE
            typeFilter != null && dateFilter == null && sizeFilter == null -> {
                android.util.Log.d("SEARCH_ROOM", "  → Using searchByMediaType()")
                val (isVideo, typeName) = parseMediaTypeFilter(typeFilter)
                android.util.Log.d("SEARCH_ROOM", "    Type: $typeName")
                database.mediaDao().searchByMediaType(query = cleanQuery, isVideo = isVideo)
            }
            
            // ONLY SIZE
            sizeFilter != null && typeFilter == null && dateFilter == null -> {
                android.util.Log.d("SEARCH_ROOM", "  → Using searchBySize()")
                val (minSize, maxSize, sizeName) = parseSizeFilter(sizeFilter)
                android.util.Log.d("SEARCH_ROOM", "    Size: $sizeName")
                database.mediaDao().searchBySize(query = cleanQuery, minSize = minSize, maxSize = maxSize)
            }
            
            // ONLY DATE
            dateFilter != null && typeFilter == null && sizeFilter == null -> {
                android.util.Log.d("SEARCH_ROOM", "  → Using searchByDateRange()")
                val (startMs, endMs, dateName) = parseDateFilter(dateFilter)
                android.util.Log.d("SEARCH_ROOM", "    Date: $dateName")
                database.mediaDao().searchByDateRange(query = cleanQuery, startMs = startMs, endMs = endMs)
            }
            
            // DEFAULT: Just name search
            else -> {
                android.util.Log.d("SEARCH_ROOM", "  → Using basic searchMedia()")
                database.mediaDao().searchMedia(cleanQuery)
            }
        }
        
        // COMBINE with favorite IDs and map to MediaItems
        return kotlinx.coroutines.flow.combine(
            daoQuery,
            database.favoriteDao().getAllFavoriteIdsFlow()
        ) { entities, favIds ->
            val resultTime = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS"))
            android.util.Log.d("SEARCH_ROOM", "  [$resultTime] Room query returned ${entities.size} items")
            
            val mapped = entities.toMediaItems(favIds.toSet())
            android.util.Log.d("SEARCH_ROOM", "  ✓ Mapped to MediaItems: ${mapped.size} items with isFavorite set")
            
            mapped
        }
    }
    
    // ─────────────────────────────────────────────────────────────────────
    // HELPER FUNCTIONS FOR SEARCH FILTER DETECTION & PARSING
    // ─────────────────────────────────────────────────────────────────────
    
    private fun detectSearchDateFilter(query: String): SearchEngine.DateFilter? {
        return when {
            query.contains("today") -> SearchEngine.DateFilter.Today
            query.contains("yesterday") -> SearchEngine.DateFilter.Yesterday
            query.contains("this week") -> SearchEngine.DateFilter.ThisWeek
            query.contains("last week") -> SearchEngine.DateFilter.LastWeek
            query.contains("this month") -> SearchEngine.DateFilter.ThisMonth
            query.contains("last month") -> SearchEngine.DateFilter.LastMonth
            query.matches(Regex(".*\\b(202[0-9]|201[0-9])\\b.*")) -> {
                val year = Regex("\\b(202[0-9]|201[0-9])\\b").find(query)?.value?.toInt()
                if (year != null) SearchEngine.DateFilter.Year(year) else null
            }
            query.contains("january") || query.contains("jan") -> SearchEngine.DateFilter.Month(java.util.Calendar.JANUARY)
            query.contains("february") || query.contains("feb") -> SearchEngine.DateFilter.Month(java.util.Calendar.FEBRUARY)
            query.contains("march") || query.contains("mar") -> SearchEngine.DateFilter.Month(java.util.Calendar.MARCH)
            query.contains("april") || query.contains("apr") -> SearchEngine.DateFilter.Month(java.util.Calendar.APRIL)
            query.contains("may") -> SearchEngine.DateFilter.Month(java.util.Calendar.MAY)
            query.contains("june") || query.contains("jun") -> SearchEngine.DateFilter.Month(java.util.Calendar.JUNE)
            query.contains("july") || query.contains("jul") -> SearchEngine.DateFilter.Month(java.util.Calendar.JULY)
            query.contains("august") || query.contains("aug") -> SearchEngine.DateFilter.Month(java.util.Calendar.AUGUST)
            query.contains("september") || query.contains("sep") -> SearchEngine.DateFilter.Month(java.util.Calendar.SEPTEMBER)
            query.contains("october") || query.contains("oct") -> SearchEngine.DateFilter.Month(java.util.Calendar.OCTOBER)
            query.contains("november") || query.contains("nov") -> SearchEngine.DateFilter.Month(java.util.Calendar.NOVEMBER)
            query.contains("december") || query.contains("dec") -> SearchEngine.DateFilter.Month(java.util.Calendar.DECEMBER)
            else -> null
        }
    }
    
    private fun detectSearchMediaTypeFilter(query: String): SearchEngine.MediaTypeFilter? {
        return when {
            query.contains("video") -> SearchEngine.MediaTypeFilter.Videos
            query.contains("photo") || query.contains("image") -> SearchEngine.MediaTypeFilter.Photos
            query.contains("gif") -> SearchEngine.MediaTypeFilter.Gifs
            query.contains("screenshot") -> SearchEngine.MediaTypeFilter.Screenshots
            query.contains("camera") -> SearchEngine.MediaTypeFilter.Camera
            else -> null
        }
    }
    
    private fun detectSearchSizeFilter(query: String): SearchEngine.SizeFilter? {
        return when {
            query.contains("small") -> SearchEngine.SizeFilter.Small
            query.contains("medium") -> SearchEngine.SizeFilter.Medium
            query.contains("large") -> SearchEngine.SizeFilter.Large
            else -> null
        }
    }
    
    private fun removeKeywords(query: String): String {
        var cleaned = query
        
        val dateKeywords = listOf(
            "today", "yesterday", "this week", "last week", "this month", "last month",
            "january", "february", "march", "april", "may", "june",
            "july", "august", "september", "october", "november", "december",
            "jan", "feb", "mar", "apr", "jun", "jul", "aug", "sep", "oct", "nov", "dec"
        )
        dateKeywords.forEach { cleaned = cleaned.replace(it, "") }
        
        cleaned = cleaned.replace(Regex("\\b(202[0-9]|201[0-9])\\b"), "")
        
        val typeKeywords = listOf("photo", "photos", "image", "images", "video", "videos", "gif", "gifs", "screenshot", "camera", "dcim")
        typeKeywords.forEach { cleaned = cleaned.replace(it, "") }
        
        val sizeKeywords = listOf("small", "medium", "large")
        sizeKeywords.forEach { cleaned = cleaned.replace(it, "") }
        
        val commonWords = listOf("from", "in", "on", "the", "a", "an")
        commonWords.forEach { cleaned = cleaned.replace(Regex("\\b$it\\b"), "") }
        
        return cleaned.trim().replace(Regex("\\s+"), " ")
    }
    
    private fun parseMediaTypeFilter(filter: SearchEngine.MediaTypeFilter): Pair<Boolean, String> {
        return when (filter) {
            SearchEngine.MediaTypeFilter.Photos -> Pair(false, "Photos")
            SearchEngine.MediaTypeFilter.Videos -> Pair(true, "Videos")
            SearchEngine.MediaTypeFilter.Gifs -> Pair(false, "GIFs")
            SearchEngine.MediaTypeFilter.Screenshots -> Pair(false, "Screenshots")
            SearchEngine.MediaTypeFilter.Camera -> Pair(false, "Camera Roll")
        }
    }
    
    private fun parseSizeFilter(filter: SearchEngine.SizeFilter): Triple<Long, Long, String> {
        val fiveMB = 5 * 1024 * 1024L
        val hundredMB = 100 * 1024 * 1024L
        
        return when (filter) {
            SearchEngine.SizeFilter.Small -> Triple(0L, fiveMB, "Small (<5MB)")
            SearchEngine.SizeFilter.Medium -> Triple(fiveMB, hundredMB, "Medium (5-100MB)")
            SearchEngine.SizeFilter.Large -> Triple(hundredMB, Long.MAX_VALUE, "Large (>100MB)")
        }
    }
    
    private fun parseDateFilter(filter: SearchEngine.DateFilter): Triple<Long, Long, String> {
        val cal = java.util.Calendar.getInstance()
        val now = System.currentTimeMillis()
        
        return when (filter) {
            SearchEngine.DateFilter.Today -> {
                cal.apply {
                    timeInMillis = now
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                }
                val startMs = cal.timeInMillis
                val endMs = startMs + (24 * 60 * 60 * 1000L)
                Triple(startMs, endMs, "Today")
            }
            SearchEngine.DateFilter.Yesterday -> {
                cal.apply {
                    timeInMillis = now
                    add(java.util.Calendar.DAY_OF_YEAR, -1)
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                }
                val startMs = cal.timeInMillis
                val endMs = startMs + (24 * 60 * 60 * 1000L)
                Triple(startMs, endMs, "Yesterday")
            }
            SearchEngine.DateFilter.ThisWeek -> {
                cal.apply {
                    timeInMillis = now
                    set(java.util.Calendar.DAY_OF_WEEK, firstDayOfWeek)
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                }
                val startMs = cal.timeInMillis
                val endMs = now
                Triple(startMs, endMs, "This Week")
            }
            SearchEngine.DateFilter.LastWeek -> {
                cal.apply {
                    timeInMillis = now
                    add(java.util.Calendar.WEEK_OF_YEAR, -1)
                    set(java.util.Calendar.DAY_OF_WEEK, firstDayOfWeek)
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                }
                val startMs = cal.timeInMillis
                val endMs = startMs + (7 * 24 * 60 * 60 * 1000L)
                Triple(startMs, endMs, "Last Week")
            }
            SearchEngine.DateFilter.ThisMonth -> {
                cal.apply {
                    timeInMillis = now
                    set(java.util.Calendar.DAY_OF_MONTH, 1)
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                }
                val startMs = cal.timeInMillis
                val endMs = now
                Triple(startMs, endMs, "This Month")
            }
            SearchEngine.DateFilter.LastMonth -> {
                cal.apply {
                    timeInMillis = now
                    add(java.util.Calendar.MONTH, -1)
                    set(java.util.Calendar.DAY_OF_MONTH, 1)
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                }
                val startMs = cal.timeInMillis
                cal.add(java.util.Calendar.MONTH, 1)
                cal.add(java.util.Calendar.MILLISECOND, -1)
                val endMs = cal.timeInMillis
                Triple(startMs, endMs, "Last Month")
            }
            is SearchEngine.DateFilter.Year -> {
                cal.apply {
                    timeInMillis = now
                    set(java.util.Calendar.YEAR, filter.year)
                    set(java.util.Calendar.DAY_OF_YEAR, 1)
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                }
                val startMs = cal.timeInMillis
                cal.add(java.util.Calendar.YEAR, 1)
                cal.add(java.util.Calendar.MILLISECOND, -1)
                val endMs = cal.timeInMillis
                Triple(startMs, endMs, "${filter.year}")
            }
            is SearchEngine.DateFilter.Month -> {
                cal.apply {
                    timeInMillis = now
                    set(java.util.Calendar.MONTH, filter.month)
                    set(java.util.Calendar.DAY_OF_MONTH, 1)
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                }
                val startMs = cal.timeInMillis
                cal.add(java.util.Calendar.MONTH, 1)
                cal.add(java.util.Calendar.MILLISECOND, -1)
                val endMs = cal.timeInMillis
                val monthName = java.text.SimpleDateFormat("MMMM", java.util.Locale.getDefault()).format(cal.time)
                Triple(startMs, endMs, monthName)
            }
        }
    }
    
    /**
     * ALBUM MEDIA FLOW: Media from specific album (Room-first, pure DAO)
     * 
     * Returns only media from the specified bucketId.
     * Updates automatically when album media changes.
     * 
     * Special case: "all" returns all media via mediaFlow.
     */
    fun albumMediaFlow(bucketId: String): kotlinx.coroutines.flow.Flow<List<MediaItem>> {
        android.util.Log.d("ALBUM_FLOW_DEBUG", "albumMediaFlow called for id=$bucketId")
        
        // Special case: "all" means all media, not a specific bucket
        if (bucketId == "all") {
            android.util.Log.d("ALBUM_FLOW", "Using mediaFlow for 'all'")
            return mediaFlow
        }
        
        return kotlinx.coroutines.flow.combine(
            database.mediaDao().getMediaByBucket(bucketId),
            database.favoriteDao().getAllFavoriteIdsFlow()
        ) { entities, favIds ->
            android.util.Log.d("ALBUM_FLOW", "Album '$bucketId' emitted ${entities.size} items")
            entities.toMediaItems(favIds.toSet())
        }
    }
    
    /**
     * FAVORITES FLOW: Favorite media from Room (Room-first)
     * 
     * Waits for database readiness, then filters all media by favorite IDs.
     * Avoids problematic INNER JOIN - uses simple List filtering instead.
     * Updates automatically when favorites change (fully reactive).
     */
    val favoritesFlow: StateFlow<List<MediaItem>> = _databaseReady
        .flatMapLatest { ready ->
            if (!ready) {
                flowOf(emptyList())
            } else {
                database.mediaDao().getAllMedia()
                    .combine(database.favoriteDao().getAllFavoriteIdsFlow()) { allMedia, favIds ->
                        val flowEmitTime = try {
                            java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS"))
                        } catch (e: Exception) {
                            "??:??:??.???"
                        }
                        
                        // LOG SOURCE DETAILS
                        android.util.Log.d("FAVORITES_FLOW", "[$flowEmitTime] combine emitted:")
                        android.util.Log.d("FAVORITES_FLOW", "  - All media: ${allMedia.size} items")
                        android.util.Log.d("FAVORITES_FLOW", "  - Favorite IDs: ${favIds.size} IDs = [${favIds.take(5).joinToString(",")}${if(favIds.size > 5) "..." else ""}]")
                        
                        // FILTER: Get media items where ID is in favorite IDs
                        val favIdSet = favIds.toSet()
                        val filtered = allMedia.filter { it.id in favIdSet }
                        
                        android.util.Log.d("FAVORITES_FLOW", "  - Filtered result: ${filtered.size} items")
                        
                        // MAP: MediaEntity → MediaItem (set isFavorite=true for all filtered items)
                        val mapped = filtered.toMediaItems(favIdSet)
                        
                        android.util.Log.d("FAVORITES_FLOW", "  - Mapped result: ${mapped.size} items (all with isFavorite=true)")
                        
                        mapped
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    /**
     * IMAGES ONLY FLOW: Images from Room (Room-first)
     */
    /**
     * IMAGES ONLY FLOW: Images from Room (Room-first - fully reactive)
     */
    val imagesFlow: StateFlow<List<MediaItem>> = _databaseReady
        .flatMapLatest { ready ->
            if (!ready) {
                flowOf(emptyList())
            } else {
                database.mediaDao().getAllImages()
                    .combine(database.favoriteDao().getAllFavoriteIdsFlow()) { entities, favIds ->
                        entities.toMediaItems(favIds.toSet())
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    /**
     * VIDEOS ONLY FLOW: Videos from Room (Room-first - fully reactive)
     */
    val videosFlow: StateFlow<List<MediaItem>> = _databaseReady
        .flatMapLatest { ready ->
            if (!ready) {
                flowOf(emptyList())
            } else {
                database.mediaDao().getAllVideos()
                    .combine(database.favoriteDao().getAllFavoriteIdsFlow()) { entities, favIds ->
                        entities.toMediaItems(favIds.toSet())
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    /**
     * GROUPED MEDIA FLOW: Media grouped by date/month (Room-first)
     * 
     * Dynamically groups media based on current grid type (DAY or MONTH).
     * Updates automatically when mediaFlow or gridType changes.
     */
    val groupedMediaFlow: StateFlow<List<MediaGroup>> = mediaFlow
        .combine(_gridType) { media, gridType ->
            android.util.Log.d("ROOM_FLOW", "Grouped Media: ${media.size} items grouped by $gridType")
            groupMediaForGrid(media, gridType)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    // ─────────────────────────────────────────────────────────────────────────────────
    // LOADER STATE: Controlled by first Room emission
    // ─────────────────────────────────────────────────────────────────────────────────
    init {
        Log.d("VM_INIT", "init block: Properties initialized, starting mediaFlow collection")
        viewModelScope.launch {
            mediaFlow.collect { media ->
                Log.d("VM_INIT", "init block: mediaFlow collected ${media.size} items")
                if (media.isNotEmpty() && _isLoading.value) {
                    android.util.Log.d("PERF", "First Room emission received (${media.size} items)")
                    _isLoading.value = false
                }
            }
        }
    }
    
    // Background sync job for periodic updates (non-blocking)
    private var backgroundSyncJob: Job? = null

    fun initialize(context: Context) {
        if (::database.isInitialized) return
        
        Log.d("VM_INITIALIZE", "initialize(): Called, about to initialize database and repositories")
        repository = MediaRepository(context)
        albumRepository = AlbumRepository(context)
        recentSearchesDataStore = RecentSearchesDataStore(context)
        settingsDataStore = SettingsDataStore(context)
        database = AppDatabase.getDatabase(context)
        
        // Trigger reactive reconnection of flows
        _databaseReady.value = true
        Log.d("VM_INITIALIZE", "Database initialized")
        Log.d("VM_INITIALIZE", "initialize(): Database initialized, database.isInitialized=${::database.isInitialized}")
        
        // FAVORITES INITIALIZATION: Reactive Flow collection
        // Continuously observes getAllFavoriteIdsFlow() - not a one-time blocking load
        android.util.Log.d("FAVORITES_INIT", "initialize(): Setting up reactive favorite IDs observation...")
        viewModelScope.launch {
            try {
                database.favoriteDao().getAllFavoriteIdsFlow().collect { ids ->
                    val initTime = try { 
                        java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")) 
                    } catch (e: Exception) { 
                        "??:??:??.???" 
                    }
                    android.util.Log.d("FAVORITES_INIT", "[$initTime] getAllFavoriteIdsFlow emitted: ${ids.size} favorite IDs")
                    if (ids.isNotEmpty()) {
                        android.util.Log.d("FAVORITES_INIT", "  IDs: [${ids.take(5).joinToString(",")}${if(ids.size > 5) ",..." else ""}]")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("FAVORITES_INIT", "ERROR: Failed to observe favorite IDs", e)
                e.printStackTrace()
            }
        }
        
        // Observe WorkManager for ML labeling progress
        observeLabelingProgress(context)
        
        // Load recent searches from DataStore (collect in viewModelScope - will cancel when VM is cleared)
        viewModelScope.launch {
            try {
                recentSearchesDataStore.recentSearchesFlow.collect { searches ->
                    _recentSearches.value = searches
                }
            } catch (e: Exception) {
                // Ignore cancellation exceptions
            }
        }
        
        // Load grid type from DataStore
        viewModelScope.launch {
            try {
                settingsDataStore.gridTypeFlow.collect { type ->
                    _gridType.value = type
                }
            } catch (e: Exception) {
                // Ignore cancellation exceptions
            }
        }
        
        // Load selected albums from DataStore
        viewModelScope.launch {
            try {
                settingsDataStore.selectedAlbumsFlow.collect { albums ->
                    android.util.Log.d("HIDDEN_DEBUG", "Selected albums count=${albums.size}, ids=${albums.joinToString()}")
                    _selectedAlbums.value = albums
                    // Room mediaFlow automatically handles filtering via SQL (no applySorting() needed)
                }
            } catch (e: Exception) {
                // Ignore cancellation exceptions
            }
        }
        
        // Load pinch gesture setting from DataStore
        viewModelScope.launch {
            try {
                settingsDataStore.pinchGestureEnabledFlow.collect { enabled ->
                    _pinchGestureEnabled.value = enabled
                }
            } catch (e: Exception) {
                // Ignore cancellation exceptions
            }
        }
        
        // Start observing MediaStore for automatic refresh on media changes
        startObserving(context)
    }

    fun isMediaEmpty(): Boolean {
        return mediaFlow.value.isEmpty()
    }

    fun refresh(context: Context) {
        Log.d("SYNC_ENGINE", "refresh() START: Checking repository initialization")
        if (!::repository.isInitialized) {
            initialize(context)
        }
        
        viewModelScope.launch {
            // Defensive guard: Prevent overlapping loads
            if (_isLoading.value) {
                if (BuildConfig.DEBUG) {
                    android.util.Log.d("SYNC_ENGINE", "Sync skipped (already syncing)")
                }
                return@launch
            }
            
            _isLoading.value = true
            
            val loadStart = SystemClock.elapsedRealtime()
            if (BuildConfig.DEBUG) {
                android.util.Log.d("SYNC_ENGINE", "MediaStore sync START")
            }
            Log.d("SYNC_ENGINE", "refresh(): Starting MediaStore → Room sync")
            
            try {
                // ═════════════════════════════════════════════════════════════
                // ROOM-FIRST ARCHITECTURE: SYNC-ONLY OPERATION
                // ═════════════════════════════════════════════════════════════
                // MediaStore → Room sync only
                // UI updates automatically via Room flows (mediaFlow, albumsFlow, etc.)
                // NO direct UI StateFlow updates
                // ═════════════════════════════════════════════════════════════
                
                // STEP 1: Query MediaStore for latest media (sync source)
                val images = repository.loadImages()
                val videos = repository.loadVideos()
                val combined = (images + videos)
                
                if (BuildConfig.DEBUG) {
                    val queryDuration = SystemClock.elapsedRealtime() - loadStart
                    android.util.Log.d("SYNC_ENGINE", "MediaStore query completed: ${queryDuration}ms (${combined.size} items)")
                }
                
                // STEP 2: Sync to Room database (single source of truth)
                withContext(Dispatchers.IO) {
                    try {
                        val upsertStart = SystemClock.elapsedRealtime()
                        
                        // Convert MediaItem → MediaEntity
                        val mediaEntities = combined.map { item ->
                            MediaEntity(
                                id = item.id,
                                uri = item.uri.toString(),
                                displayName = item.displayName,
                                dateAdded = item.dateAdded,
                                bucketId = item.bucketId,
                                bucketName = item.bucketName,
                                mimeType = item.mimeType,
                                width = item.width,
                                height = item.height,
                                size = item.size,
                                duration = if (item.isVideo) item.duration else null,
                                isVideo = item.isVideo,
                                path = item.path
                            )
                        }
                        
                        // UPSERT: Insert or replace if exists (Room handles deduplication)
                        Log.d("SYNC_ENGINE", "refresh(): Upserting ${mediaEntities.size} entities to Room")
                        database.mediaDao().upsertAll(mediaEntities)
                        Log.d("SYNC_ENGINE", "Upserted ${mediaEntities.size} items into Room")
                        
                        if (BuildConfig.DEBUG) {
                            val upsertDuration = SystemClock.elapsedRealtime() - upsertStart
                            android.util.Log.d("SYNC_ENGINE", "Room sync completed: ${upsertDuration}ms (${mediaEntities.size} items synced)")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("SYNC_ENGINE", "Error syncing to Room", e)
                    }
                }
                
                // STEP 3: Room flows auto-emit → UI updates automatically
                // - mediaFlow emits sorted media
                // - albumsFlow emits grouped albums
                // - imagesFlow/videosFlow emit filtered media
                // - No manual StateFlow updates needed!
                
                if (BuildConfig.DEBUG) {
                    android.util.Log.d("SYNC_ENGINE", "Sync complete. Room flows will update UI automatically.")
                }
                
                // Schedule deferred ML labeling if needed
                scheduleDeferredLabelingIfNeeded(context)
                
            } catch (e: Exception) {
                android.util.Log.e("SYNC_ENGINE", "Error in refresh()", e)
            } finally {
                // Note: loader is now controlled by Room flow emissions (see init block)
                // Set to false here as fallback in case of errors
                _isLoading.value = false
            }
        }
    }

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
        // mediaFlow automatically updates via flatMapLatest
        // No applySorting() call needed!
    }

    // ═════════════════════════════════════════════════════════════════════
    // ═════════════════════════════════════════════════════════════════════
    // DEPRECATED: applySorting() removed
    // Room now handles sorting via SQL ORDER BY (mediaFlow flatMapLatest)
    // ═════════════════════════════════════════════════════════════════════

    fun delete(item: MediaItem, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.delete(item)
            onComplete(success)
        }
    }

    fun getDeleteRequest(items: List<MediaItem>) = repository.createDeleteRequest(items)
    
    // Media overlay functions
    fun showMediaOverlay(
        mediaType: String,
        albumId: String,
        selectedIndex: Int,
        searchQuery: String? = null,
        thumbnailBounds: com.prantiux.pixelgallery.ui.animation.SharedElementBounds? = null
    ) {
        _overlayState.value = MediaOverlayState(
            isVisible = true,
            mediaType = mediaType,
            albumId = albumId,
            selectedIndex = selectedIndex,
            searchQuery = searchQuery,
            thumbnailBounds = thumbnailBounds
        )
    }

    fun hideMediaOverlay() {
        _overlayState.value = _overlayState.value.copy(isVisible = false)
    }

    fun updateOverlayIndex(index: Int) {
        _overlayState.value = _overlayState.value.copy(selectedIndex = index)
    }
    
    // Selection mode functions
    fun enterSelectionMode(item: MediaItem) {
        _isSelectionMode.value = true
        _selectedItems.value = setOf(item)
    }
    
    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedItems.value = emptySet()
    }
    
    fun toggleSelection(item: MediaItem) {
        _selectedItems.value = if (_selectedItems.value.contains(item)) {
            _selectedItems.value - item
        } else {
            _selectedItems.value + item
        }
        // Exit selection mode if no items selected
        if (_selectedItems.value.isEmpty()) {
            _isSelectionMode.value = false
        }
    }
    
    fun selectAll(items: List<MediaItem>) {
        _selectedItems.value = items.toSet()
    }
    
    fun selectAllInGroup(items: List<MediaItem>) {
        val currentSelection = _selectedItems.value.toMutableSet()
        currentSelection.addAll(items)
        _selectedItems.value = currentSelection
    }
    
    fun deselectAllInGroup(items: List<MediaItem>) {
        _selectedItems.value = _selectedItems.value - items.toSet()
        if (_selectedItems.value.isEmpty()) {
            _isSelectionMode.value = false
        }
    }
    
    fun deleteSelectedItems(context: Context, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // For Android 11+, launch the trash request
                    val pendingIntent = getTrashRequest()
                    if (pendingIntent != null && trashRequestLauncher != null) {
                        trashRequestLauncher?.invoke(pendingIntent)
                        // Don't call onComplete here - wait for user confirmation
                    } else {
                        android.util.Log.e("MediaViewModel", "Trash request or launcher is null")
                        onComplete(false)
                    }
                } else {
                    // For older versions, use direct delete
                    val uris = _selectedItems.value.map { it.uri }
                    val success = repository.deleteMediaItems(context, uris)
                    if (success) {
                        exitSelectionMode()
                        refresh(context)
                    }
                    onComplete(success)
                }
            } catch (e: Exception) {
                android.util.Log.e("MediaViewModel", "Error in deleteSelectedItems", e)
                onComplete(false)
            }
        }
    }
    
    // Called when user confirms the trash request
    fun onDeleteConfirmed(context: Context) {
        viewModelScope.launch {
            val itemCount = _selectedItems.value.size
            val itemType = if (_selectedItems.value.all { it.isVideo }) {
                if (itemCount == 1) "video" else "videos"
            } else if (_selectedItems.value.all { !it.isVideo }) {
                if (itemCount == 1) "photo" else "photos"
            } else {
                if (itemCount == 1) "item" else "items"
            }
            
            _deleteSuccessMessage.value = "$itemCount $itemType moved to trash"
            
            exitSelectionMode()
            refresh(context)
            
            // Auto-dismiss after 2 seconds
            kotlinx.coroutines.delay(2000)
            _deleteSuccessMessage.value = null
        }
    }
    
    // Called when user cancels the trash request
    fun onDeleteCancelled() {
        // Just close the dialog, keep selection mode active
    }
    
    // Get trash request for Android 11+ (returns PendingIntent to launch)
    fun getTrashRequest(): android.app.PendingIntent? {
        val uris = _selectedItems.value.map { it.uri }
        return repository.createTrashRequest(uris)
    }
    
    fun shareSelectedItems(context: Context) {
        val uris = _selectedItems.value.map { it.uri }
        repository.shareMediaItems(context, uris)
    }
    
    /**
     * Hide selected items from a smart album by removing the associated labels
     */
    suspend fun hideFromSmartAlbum(context: Context, smartAlbumId: String, items: List<MediaItem>) {
        try {
            val albumType = com.prantiux.pixelgallery.smartalbum.SmartAlbumGenerator.SmartAlbumType.fromId(smartAlbumId)
            if (albumType == null) {
                android.util.Log.e("MediaViewModel", "Invalid smart album ID: $smartAlbumId")
                return
            }
            
            val labelDao = database.mediaLabelDao()
            val labelsToRemove = albumType.labels.map { it.lowercase() }.toSet()
            
            items.forEach { mediaItem ->
                // Get existing label entity
                val existingEntity = labelDao.getLabelsForMedia(mediaItem.id)
                if (existingEntity != null) {
                    // Parse existing labels
                    val parsedLabels = com.prantiux.pixelgallery.search.SearchResultFilter
                        .parseLabelsWithConfidence(existingEntity.labelsWithConfidence)
                    
                    // Filter out labels associated with this smart album
                    val filteredLabels = parsedLabels.filter { it.label !in labelsToRemove }
                    
                    if (filteredLabels.isEmpty()) {
                        // Remove the entire entity if no labels remain
                        labelDao.delete(existingEntity)
                    } else {
                        // Rebuild the labelsWithConfidence string
                        val newLabelsString = filteredLabels.joinToString(",") { 
                            "${it.label}:${String.format("%.2f", it.confidence)}" 
                        }
                        
                        // Update the entity
                        val updatedEntity = existingEntity.copy(
                            labels = filteredLabels.joinToString(",") { it.label },
                            labelsWithConfidence = newLabelsString
                        )
                        labelDao.update(updatedEntity)
                    }
                }
            }
            
            android.util.Log.d("MediaViewModel", "Hidden ${items.size} items from smart album: ${albumType.displayName}")
        } catch (e: Exception) {
            android.util.Log.e("MediaViewModel", "Error hiding items from smart album", e)
        }
    }
    
    // Search functions
    fun searchMedia(query: String) {
        // Cancel previous search job first
        searchJob?.cancel()
        
        // Guard: handle empty/blank query safely - do this BEFORE setting _searchQuery
        if (query.isBlank()) {
            _searchQuery.value = ""
            _searchResults.value = SearchEngine.SearchResult(emptyList(), emptyList(), "")
            _isSearching.value = false
            return
        }
        
        // Now safe to set query
        _searchQuery.value = query
        _isSearching.value = true
        
        // Material 3 Expressive: 300ms debounce prevents unnecessary searches during typing.
        // Search execution is instant (<100ms) on cached data, so no visible loader needed.
        // The isSearching state is kept for potential future network-based search.
        searchJob = viewModelScope.launch {
            delay(300)
            
            // Use cached media list (no MediaStore query)
            val cachedMedia = _allMediaCached.value
            
            // Guard: ensure media is loaded
            if (cachedMedia.isEmpty()) {
                _searchResults.value = SearchEngine.SearchResult(emptyList(), emptyList(), query)
                _isSearching.value = false
                return@launch
            }
            
            // Perform traditional search on cached data (filename, date, location)
            val traditionalResults = SearchEngine.search(query, cachedMedia)
            
            // Perform ML-based label search with confidence filtering (if database initialized)
            val mlResults = if (::database.isInitialized) {
                try {
                    val labelDao = database.mediaLabelDao()
                    val labelMatches = labelDao.searchByLabel(query.lowercase())
                    
                    // Apply post-processing filter and ranking
                    SearchResultFilter.filterAndRankMedia(
                        query = query,
                        matchedLabels = labelMatches,
                        mediaItems = cachedMedia,
                        hardFilter = true // Remove low-confidence matches
                    )
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
            
            // Combine results (traditional + ML-based)
            // Remove duplicates by ID, prioritize traditional matches
            val traditionalMedia = traditionalResults.matchedMedia
            val combinedMedia = (traditionalMedia + mlResults)
                .distinctBy { item -> item.id }
            
            _searchResults.value = SearchEngine.SearchResult(
                matchedAlbums = traditionalResults.matchedAlbums,
                matchedMedia = combinedMedia,
                query = query
            )
            _isSearching.value = false
        }
    }
    
    /**
     * Add search query to recent searches (called when user submits/selects search)
     */
    fun addRecentSearch(query: String) {
        viewModelScope.launch {
            if (query.isNotBlank() && ::recentSearchesDataStore.isInitialized) {
                try {
                    recentSearchesDataStore.addRecentSearch(query)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    
    /**
     * Remove a specific search from recent searches
     */
    fun removeRecentSearch(query: String) {
        viewModelScope.launch {
            if (::recentSearchesDataStore.isInitialized) {
                try {
                    recentSearchesDataStore.removeRecentSearch(query)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    
    /**
     * Clear all recent searches
     */
    fun clearRecentSearches() {
        viewModelScope.launch {
            if (::recentSearchesDataStore.isInitialized) {
                try {
                    recentSearchesDataStore.clearAllRecentSearches()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    
    /**
     * Clear all recent searches
     */
    fun clearAllRecentSearches() {
        viewModelScope.launch {
            if (::recentSearchesDataStore.isInitialized) {
                try {
                    recentSearchesDataStore.clearAllRecentSearches()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    
    fun clearSearch() {
        // Cancel any ongoing search
        searchJob?.cancel()
        searchJob = null
        
        // Reset state directly without triggering searchMedia
        _searchQuery.value = ""
        _searchResults.value = SearchEngine.SearchResult(emptyList(), emptyList(), "")
        _isSearching.value = false
    }
    
    fun getAllMedia(): List<MediaItem> {
        // Room is now the single source of truth - return current mediaFlow value
        return mediaFlow.value
    }
    
    // Recycle Bin functions
    fun loadTrashedItems(context: Context) {
        viewModelScope.launch {
            // Material 3 Expressive: Loading trashed items takes 200ms-1s (SHORT operation)
            // Show indeterminate loading indicator for proper user feedback
            _isLoadingTrash.value = true
            _trashedItems.value = repository.loadTrashedItems(context)
            _isLoadingTrash.value = false
        }
    }
    
    // Restore single item from trash (from MediaOverlay)
    fun restoreFromTrash(context: Context, item: MediaItem) {
        restoreTrashedItems(context, listOf(item))
    }
    
    // Permanently delete single item from trash (from MediaOverlay)
    fun permanentlyDelete(context: Context, item: MediaItem) {
        permanentlyDeleteTrashedItems(context, listOf(item))
    }
    
    // Restore multiple items from trash
    private fun restoreTrashedItems(context: Context, items: List<MediaItem>) {
        val uris = items.map { it.uri }
        pendingRestoreUris = uris
        
        val pendingIntent = repository.createRestoreRequest(uris)
        if (pendingIntent != null) {
            restoreRequestLauncher?.invoke(pendingIntent)
        } else {
            android.util.Log.e("MediaViewModel", "Failed to create restore request")
        }
    }
    
    // Permanently delete multiple items from trash
    private fun permanentlyDeleteTrashedItems(context: Context, items: List<MediaItem>) {
        val uris = items.map { it.uri }
        pendingDeleteUris = uris
        
        val pendingIntent = repository.createPermanentDeleteRequest(uris)
        if (pendingIntent != null) {
            permanentDeleteRequestLauncher?.invoke(pendingIntent)
        } else {
            android.util.Log.e("MediaViewModel", "Failed to create delete request")
        }
    }
    
    // Called when user confirms restore in system dialog
    fun onRestoreConfirmed(context: Context) {
        viewModelScope.launch {
            val itemCount = pendingRestoreUris.size
            val success = repository.performRestore(context, pendingRestoreUris)
            if (success) {
                android.util.Log.d("MediaViewModel", "Successfully restored $itemCount items")
                
                val itemType = if (itemCount == 1) "item" else "items"
                _restoreSuccessMessage.value = "$itemCount $itemType restored"
                
                // Auto-dismiss after 2 seconds
                viewModelScope.launch {
                    kotlinx.coroutines.delay(2000)
                    _restoreSuccessMessage.value = null
                }
            }
            pendingRestoreUris = emptyList()
            exitTrashSelectionMode()
            loadTrashedItems(context)
            refresh(context)
        }
    }
    
    // Called when user cancels restore
    fun onRestoreCancelled() {
        pendingRestoreUris = emptyList()
    }
    
    // Called when user confirms permanent delete in system dialog
    fun onPermanentDeleteConfirmed(context: Context) {
        viewModelScope.launch {
            val itemCount = pendingDeleteUris.size
            val success = repository.performPermanentDelete(context, pendingDeleteUris)
            if (success) {
                android.util.Log.d("MediaViewModel", "Successfully deleted $itemCount items")
                
                val itemType = if (itemCount == 1) "item" else "items"
                _deleteSuccessMessage.value = "$itemCount $itemType permanently deleted"
                
                // Auto-dismiss after 2 seconds
                viewModelScope.launch {
                    kotlinx.coroutines.delay(2000)
                    _deleteSuccessMessage.value = null
                }
            }
            pendingDeleteUris = emptyList()
            exitTrashSelectionMode()
            loadTrashedItems(context)
        }
    }
    
    // Called when user cancels permanent delete
    fun onPermanentDeleteCancelled() {
        pendingDeleteUris = emptyList()
    }
    
    // Trash selection mode
    fun enterTrashSelectionMode(item: MediaItem) {
        _isTrashSelectionMode.value = true
        _selectedTrashItems.value = setOf(item)
    }
    
    fun exitTrashSelectionMode() {
        _isTrashSelectionMode.value = false
        _selectedTrashItems.value = emptySet()
    }
    
    fun toggleTrashSelection(item: MediaItem) {
        _selectedTrashItems.value = if (_selectedTrashItems.value.contains(item)) {
            _selectedTrashItems.value - item
        } else {
            _selectedTrashItems.value + item
        }
    }
    
    fun selectTrashGroup(items: List<MediaItem>) {
        _selectedTrashItems.value = _selectedTrashItems.value + items
    }
    
    fun deselectTrashGroup(items: List<MediaItem>) {
        _selectedTrashItems.value = _selectedTrashItems.value - items.toSet()
    }
    
    // Restore selected items from trash (from RecycleBinScreen)
    fun restoreSelectedTrashItems(context: Context) {
        val items = _selectedTrashItems.value.toList()
        if (items.isNotEmpty()) {
            restoreTrashedItems(context, items)
        }
    }
    
    // Delete selected items from trash (from RecycleBinScreen)
    fun deleteSelectedTrashItems(context: Context) {
        val items = _selectedTrashItems.value.toList()
        if (items.isNotEmpty()) {
            permanentlyDeleteTrashedItems(context, items)
        }
    }
    
    // Trash overlay functions
    fun showTrashMediaOverlay(
        selectedIndex: Int,
        thumbnailBounds: com.prantiux.pixelgallery.ui.animation.SharedElementBounds? = null
    ) {
        _overlayState.value = MediaOverlayState(
            isVisible = true,
            mediaType = "trash",
            albumId = "",
            selectedIndex = selectedIndex,
            searchQuery = null,
            thumbnailBounds = thumbnailBounds
        )
    }
    
    // Favorites functions - ROOM-FIRST: Only write to database, let Flow streams re-emit
    fun toggleFavorite(mediaId: Long, newState: Boolean) {
        val toggleStart = System.currentTimeMillis()
        val toggleTime = try { 
            java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")) 
        } catch (e: Exception) { 
            "??:??:??.???" 
        }
        
        android.util.Log.d("FAVORITES_TOGGLE", "[$toggleTime] toggleFavorite called: mediaId=$mediaId, newState=$newState")
        
        viewModelScope.launch {
            try {
                if (newState) {
                    // ADD TO FAVORITES
                    android.util.Log.d("FAVORITES_TOGGLE", "  → Inserting into favorites table...")
                    database.favoriteDao().addFavorite(FavoriteEntity(mediaId))
                    val addDuration = System.currentTimeMillis() - toggleStart
                    
                    android.util.Log.d("FAVORITES_TOGGLE", "✓ Added favorite id=$mediaId to DB in ${addDuration}ms")
                    android.util.Log.d("FAVORITES_TOGGLE", "  → Room will emit new favorite IDs from getAllFavoriteIdsFlow()")
                    android.util.Log.d("FAVORITES_TOGGLE", "  → favoritesFlow will recompute and re-emit")
                } else {
                    // REMOVE FROM FAVORITES
                    android.util.Log.d("FAVORITES_TOGGLE", "  → Deleting from favorites table...")
                    database.favoriteDao().removeFavorite(mediaId)
                    val removeDuration = System.currentTimeMillis() - toggleStart
                    
                    android.util.Log.d("FAVORITES_TOGGLE", "✓ Removed favorite id=$mediaId from DB in ${removeDuration}ms")
                    android.util.Log.d("FAVORITES_TOGGLE", "  → Room will emit new favorite IDs from getAllFavoriteIdsFlow()")
                    android.util.Log.d("FAVORITES_TOGGLE", "  → favoritesFlow will recompute and re-emit")
                }
            } catch (e: Exception) {
                val errorDuration = System.currentTimeMillis() - toggleStart
                android.util.Log.e("FAVORITES_TOGGLE", "✗ DATABASE ERROR after ${errorDuration}ms: ${e.message}", e)
                e.printStackTrace()
            }
        }
    }
    
    // ML Labeling functions
    private fun observeLabelingProgress(context: Context) {
        val workManager = WorkManager.getInstance(context)
        viewModelScope.launch {
            workManager.getWorkInfosByTagFlow("image_labeling").collect { workInfoList ->
                val runningWork = workInfoList.firstOrNull { 
                    it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED 
                }
                
                _isLabelingInProgress.value = runningWork != null
                
                // Get progress from running work first, then completed work
                val workToCheck = runningWork ?: workInfoList.firstOrNull { 
                    it.state == WorkInfo.State.SUCCEEDED 
                }
                
                if (workToCheck != null) {
                    // Try to get from progress (for running work)
                    var progress = workToCheck.progress.getInt(ImageLabelWorker.KEY_PROGRESS, 0)
                    var total = workToCheck.progress.getInt(ImageLabelWorker.KEY_TOTAL, 0)
                    
                    // If not in progress, try output data (for completed work)
                    if (total == 0) {
                        progress = workToCheck.outputData.getInt(ImageLabelWorker.KEY_PROGRESS, 0)
                        total = workToCheck.outputData.getInt(ImageLabelWorker.KEY_TOTAL, 0)
                    }
                    
                    if (total > 0) {
                        _labelingProgress.value = Pair(progress, total)
                    }
                }
            }
        }
    }
    
    fun updateLabelingProgress(processed: Int, total: Int) {
        _labelingProgress.value = Pair(processed, total)
    }
    
    fun setLabelingInProgress(inProgress: Boolean) {
        _isLabelingInProgress.value = inProgress
    }
    
    fun getLabelingProgressPercent(): Float {
        val progress = _labelingProgress.value ?: return 0f
        if (progress.second == 0) return 0f
        return progress.first.toFloat() / progress.second.toFloat()
    }
    
    // Copy to album functions
    fun showCopyToAlbumDialog(items: List<MediaItem>) {
        _itemsToCopy.value = items
        _showCopyToAlbumDialog.value = true
    }
    
    fun hideCopyToAlbumDialog() {
        _showCopyToAlbumDialog.value = false
        _itemsToCopy.value = emptyList()
    }
    
    suspend fun copyToAlbum(context: Context, targetAlbum: Album): Boolean {
        return if (::repository.isInitialized) {
            val items = _itemsToCopy.value
            if (items.isNotEmpty()) {
                android.util.Log.d("MediaViewModel", "Copying ${items.size} items to ${targetAlbum.name}")
                val success = repository.copyMediaToAlbum(items, targetAlbum)
                
                android.util.Log.d("MediaViewModel", "Copy operation result: $success")
                
                if (success) {
                    // Show success message
                    val itemType = if (items.size == 1) {
                        if (items.first().isVideo) "video" else "image"
                    } else {
                        "items"
                    }
                    _copySuccessMessage.value = "${items.size} $itemType copied to ${targetAlbum.name}"
                    
                    // Exit selection mode immediately
                    exitSelectionMode()
                    
                    // Clear copy state and hide dialog
                    hideCopyToAlbumDialog()
                    
                    // Force refresh with delay for MediaStore indexing
                    viewModelScope.launch {
                        android.util.Log.d("MediaViewModel", "Waiting 800ms before refresh...")
                        kotlinx.coroutines.delay(800)
                        android.util.Log.d("MediaViewModel", "Refreshing after copy...")
                        // Refresh media lists to show new items
                        refresh(context)
                        android.util.Log.d("MediaViewModel", "Refresh complete")
                    }
                    
                    // Auto-dismiss success message after 2 seconds
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(2000)
                        _copySuccessMessage.value = null
                    }
                } else {
                    android.util.Log.e("MediaViewModel", "Copy operation failed")
                    // Still clear state even on failure to prevent stuck dialog
                    hideCopyToAlbumDialog()
                }
                success
            } else {
                android.util.Log.w("MediaViewModel", "No items to copy")
                false
            }
        } else {
            android.util.Log.e("MediaViewModel", "Repository not initialized")
            false
        }
    }
    
    // Move to album functions
    fun showMoveToAlbumDialog(items: List<MediaItem>) {
        _itemsToMove.value = items
        _showMoveToAlbumDialog.value = true
    }
    
    fun hideMoveToAlbumDialog() {
        _showMoveToAlbumDialog.value = false
        _itemsToMove.value = emptyList()
    }
    
    suspend fun moveToAlbum(context: Context, targetAlbum: Album): Boolean {
        return if (::repository.isInitialized) {
            val items = _itemsToMove.value
            if (items.isNotEmpty()) {
                android.util.Log.d("MediaViewModel", "Moving ${items.size} items to ${targetAlbum.name}")
                val result = repository.moveMediaToAlbum(items, targetAlbum)
                
                android.util.Log.d("MediaViewModel", "Move operation result: ${result.success}, message: ${result.message}")
                
                if (result.success) {
                    android.util.Log.d("MediaViewModel", "Move completed successfully")
                    
                    // Show success message
                    val itemType = if (items.size == 1) {
                        if (items.first().isVideo) "video" else "image"
                    } else {
                        "items"
                    }
                    _moveSuccessMessage.value = "${items.size} $itemType moved to ${targetAlbum.name}"
                    
                    // Exit selection mode immediately
                    exitSelectionMode()
                    
                    // Clear move state and hide dialog
                    hideMoveToAlbumDialog()
                    
                    // Force immediate refresh with longer delay for MediaStore to fully update
                    viewModelScope.launch {
                        android.util.Log.d("MediaViewModel", "Waiting 600ms before refresh...")
                        kotlinx.coroutines.delay(600)
                        android.util.Log.d("MediaViewModel", "Refreshing after move...")
                        refresh(context)
                        android.util.Log.d("MediaViewModel", "Refresh complete")
                    }
                    
                    // Auto-dismiss success message after 2 seconds
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(2000)
                        _moveSuccessMessage.value = null
                    }
                    
                    return true
                } else {
                    android.util.Log.e("MediaViewModel", "Move operation failed")
                    hideMoveToAlbumDialog()
                    return false
                }
            } else {
                android.util.Log.w("MediaViewModel", "No items to move")
                return false
            }
        } else {
            android.util.Log.e("MediaViewModel", "Repository not initialized")
            return false
        }
    }
    
    // Grid type functions
    fun setGridType(type: GridType) {
        _gridType.value = type
        viewModelScope.launch {
            val start = SystemClock.elapsedRealtime()
            val grouped = withContext(Dispatchers.Default) {
                groupMediaForGrid(_sortedMedia.value, type)
            }
            _groupedMedia.value = grouped
            if (BuildConfig.DEBUG) {
                android.util.Log.d("Perf", "Grouping took ${SystemClock.elapsedRealtime() - start} ms")
            }
        }
        // Save to DataStore
        viewModelScope.launch {
            if (::settingsDataStore.isInitialized) {
                try {
                    settingsDataStore.saveGridType(type)
                } catch (e: Exception) {
                    // Ignore errors
                }
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════
    // REFACTORED METHODS: Generate albums from cached media
    // ═══════════════════════════════════════════════════════════════════
    
    /**
     * Generate albums from cached media list (no MediaStore query)
     * 
     * This method groups media by bucketId and creates Album objects.
     * Used by refresh() to populate _albums StateFlow from cached data.
     * 
     * @param media Combined list of images and videos from cache
     * @return List of albums sorted by item count (descending)
     */
    private fun generateAlbumsFromCache(media: List<MediaItem>): List<Album> {
        if (media.isEmpty()) return emptyList()
        
        return media
            .groupBy { it.bucketId }
            .map { (bucketId, items) ->
                Album(
                    id = bucketId,
                    name = items.first().bucketName,
                    coverUri = items.firstOrNull()?.uri,
                    itemCount = items.size,
                    bucketDisplayName = items.first().bucketName,
                    topMediaUris = items.take(6).map { it.uri },
                    topMediaItems = items.take(6)
                )
            }
            .sortedByDescending { it.itemCount }
    }
    
    /**
     * Categorize albums into main (top 4) and other sections
     * 
     * @param albums List of albums generated from cache
     * @return CategorizedAlbums with mainAlbums and otherAlbums
     */
    private fun categorizeAlbumsFromCache(albums: List<Album>): CategorizedAlbums {
        // Top 4 albums go to main section
        val mainAlbums = albums.take(4)
        
        // Remaining albums go to other section
        val otherAlbums = albums.drop(4)
        
        return CategorizedAlbums(
            mainAlbums = mainAlbums,
            otherAlbums = otherAlbums
        )
    }
    
    /**
     * Start background sync to detect MediaStore changes
     * 
     * This is non-blocking and runs periodically to check for new media.
     * Updates StateFlows only if changes are detected.
     * 
     * TODO: Implement incremental updates instead of full refresh
     */
    private fun startBackgroundSync(context: Context) {
        // Cancel previous sync job if running
        backgroundSyncJob?.cancel()
        
        // Optional: Start periodic background sync
        // For now, we rely on user manually refreshing or observing ContentObserver
        // Future enhancement: Implement ContentObserver for automatic updates
        
        // backgroundSyncJob = viewModelScope.launch {
        //     while (isActive) {
        //         delay(30_000) // Check every 30 seconds
        //         // Compare with MediaStore and update if changed
        //     }
        // }
    }

    /**
     * Start observing MediaStore for changes
     * 
     * When photos/videos are added by other apps (e.g., camera),
     * this automatically triggers a refresh of the gallery.
     * 
     * @param context Context for ContentObserver registration
     */
    fun startObserving(context: Context) {
        if (mediaContentObserver == null) {
            mediaContentObserver = MediaContentObserver(context) {
                // ROOM-FIRST: ContentObserver triggers MediaStore → Room sync
                // Room flows auto-emit → UI updates automatically
                android.util.Log.d("SYNC_ENGINE", "MediaStore change detected, triggering sync")
                refresh(context)
                // Schedule deferred ML labeling after refresh completes
                scheduleDeferredLabelingIfNeeded(context)
            }
            mediaContentObserver?.register(viewModelScope)
        }
    }

    /**
     * Schedule deferred ML labeling only if unlabeled images exist
     * 
     * Called after refresh() completes to ensure allImages is populated.
     * Checks if any images need labeling before scheduling worker.
     * 
     * @param context Context for WorkManager scheduling
     */
    fun scheduleDeferredLabelingIfNeeded(context: Context) {
        viewModelScope.launch {
            try {
                // Query DB to check if any media is unlabeled
                val processedIds = database.mediaLabelDao().getAllProcessedIds().toSet()
                val unlabeledCount = mediaFlow.value.count { it.id !in processedIds }
                
                if (unlabeledCount > 0) {
                    android.util.Log.d("MediaViewModel", "ML: Deferred labeling scheduled ($unlabeledCount unlabeled)")
                    com.prantiux.pixelgallery.ml.ImageLabelScheduler.scheduleDeferredLabeling(context)
                } else {
                    android.util.Log.d("MediaViewModel", "ML: No labeling needed (all media labeled)")
                }
            } catch (e: Exception) {
                android.util.Log.e("MediaViewModel", "Error checking labeling status", e)
            }
        }
    }
    
    /**
     * Stop observing MediaStore changes
     * 
     * Called when ViewModel is cleared to prevent memory leaks.
     */
    fun stopObserving() {
        mediaContentObserver?.unregister()
        mediaContentObserver = null
    }

    /**
     * Clean up resources when ViewModel is destroyed
     * 
     * Unregisters the MediaStore ContentObserver to prevent memory leaks.
     */
    override fun onCleared() {
        stopObserving()
        super.onCleared()
    }

    private fun groupMediaForGrid(media: List<MediaItem>, gridType: GridType): List<MediaGroup> {
    return when (gridType) {
        GridType.DAY -> groupMediaByDate(media)
        GridType.MONTH -> groupMediaByMonth(media)
    }
}

private fun groupMediaByDate(media: List<MediaItem>): List<MediaGroup> {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val calendar = Calendar.getInstance()
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)

    return media.groupBy { item ->
        calendar.timeInMillis = item.dateAdded * 1000
        dateFormat.format(calendar.time)
    }.map { (date, items) ->
        calendar.timeInMillis = items.first().dateAdded * 1000
        val itemYear = calendar.get(Calendar.YEAR)

        val displayDate = when {
            isToday(calendar) -> "Today"
            isYesterday(calendar) -> "Yesterday"
            itemYear == currentYear -> {
                // Same year: "12 Dec"
                SimpleDateFormat("d MMM", Locale.getDefault()).format(calendar.time)
            }
            else -> {
                // Different year: "28 Jan 2024"
                SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(calendar.time)
            }
        }

        // Find most common location for this date
        val mostCommonLocation = items
            .mapNotNull { it.location }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

        MediaGroup(date, displayDate, items, mostCommonLocation)
    }.sortedByDescending { it.date }
}

private fun groupMediaByMonth(media: List<MediaItem>): List<MediaGroup> {
    val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    val calendar = Calendar.getInstance()
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)

    return media.groupBy { item ->
        calendar.timeInMillis = item.dateAdded * 1000
        monthFormat.format(calendar.time)
    }.map { (month, items) ->
        calendar.timeInMillis = items.first().dateAdded * 1000
        val itemYear = calendar.get(Calendar.YEAR)

        val displayDate = if (itemYear == currentYear) {
            // Current year: "January"
            SimpleDateFormat("MMMM", Locale.getDefault()).format(calendar.time)
        } else {
            // Different year: "December 2025"
            SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(calendar.time)
        }

        // Find most common location for this month
        val mostCommonLocation = items
            .mapNotNull { it.location }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

        MediaGroup(month, displayDate, items, mostCommonLocation)
    }.sortedByDescending { it.date }
}

private fun isToday(calendar: Calendar): Boolean {
    val today = Calendar.getInstance()
    return calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            calendar.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
}

private fun isYesterday(calendar: Calendar): Boolean {
    val yesterday = Calendar.getInstance()
    yesterday.add(Calendar.DAY_OF_YEAR, -1)
    return calendar.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
            calendar.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)
}
}
