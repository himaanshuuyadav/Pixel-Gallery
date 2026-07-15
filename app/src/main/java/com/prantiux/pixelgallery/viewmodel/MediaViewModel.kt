package com.prantiux.pixelgallery.viewmodel

import android.content.Context
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
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
import com.prantiux.pixelgallery.data.toMediaEntity
import com.prantiux.pixelgallery.BuildConfig
import com.prantiux.pixelgallery.ml.ImageLabelWorker
import com.prantiux.pixelgallery.model.Album
import com.prantiux.pixelgallery.model.CategorizedAlbums
import com.prantiux.pixelgallery.model.MediaGroup
import com.prantiux.pixelgallery.model.MediaItem
import com.prantiux.pixelgallery.search.SearchEngine
import com.prantiux.pixelgallery.search.SearchResultFilter
import com.prantiux.pixelgallery.smartalbum.SmartAlbumGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.text.SimpleDateFormat
import java.util.Calendar
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.prantiux.pixelgallery.model.MediaGridItem
import java.util.Locale
import kotlin.math.roundToInt

enum class SortMode {
    DATE_DESC, DATE_ASC, NAME_ASC, NAME_DESC, SIZE_DESC, SIZE_ASC
}

enum class GridType {
    DAY_3, DAY_4, MONTH_6, MONTH_9;
    
    /** True when this is a day-level grouping */
    val isDay: Boolean get() = this == DAY_3 || this == DAY_4
}

sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Success(val results: SearchEngine.SearchResult) : SearchState
    data object Empty : SearchState
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
    private companion object {
        private const val GALLERY_PERF_TAG = "GalleryPerf"
        private const val PERF_LOG_THRESHOLD = 1000
        private val INCREMENTAL_MEDIA_BATCHES = listOf(50, 200, 500, 1000)
        private const val FINAL_STAGE_SPLIT_START = 1000
        private const val FINAL_STAGE_STEP_SIZE = 100
        private const val LARGE_BATCH_EMIT_DELAY_MS = 12L
    }

    private lateinit var repository: MediaRepository
    private lateinit var albumRepository: AlbumRepository
    private lateinit var recentSearchesDataStore: RecentSearchesDataStore
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var database: AppDatabase
    private var mediaContentObserver: MediaContentObserver? = null
    private var isSyncing = false
    private var pendingRefresh = false
    private var initialSyncCompletedCached: Boolean? = null
    private val refreshStateLock = Any()

    // Active Album management for smart albums only
    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _smartAlbumThumbnailCache = mutableStateMapOf<String, android.net.Uri?>()
    val smartAlbumThumbnailCache: SnapshotStateMap<String, android.net.Uri?>
        get() = _smartAlbumThumbnailCache
    


    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _mediaPermissionsGranted = MutableStateFlow(false)
    val mediaPermissionsGranted: StateFlow<Boolean> = _mediaPermissionsGranted.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.DATE_DESC)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    private val _selectedItems = MutableStateFlow<Set<Long>>(emptySet())
    val selectedItems: StateFlow<Set<Long>> = _selectedItems.asStateFlow()
    
    private val _fullySelectedDateGroups = MutableStateFlow<Set<String>>(emptySet())
    val fullySelectedDateGroups: StateFlow<Set<String>> = _fullySelectedDateGroups.asStateFlow()

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
    
    private val _selectedTrashItems = MutableStateFlow<Set<Long>>(emptySet())
    val selectedTrashItems: StateFlow<Set<Long>> = _selectedTrashItems.asStateFlow()
    
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
    private val _gridType = MutableStateFlow(GridType.DAY_3)
    val gridType: StateFlow<GridType> = _gridType.asStateFlow()
    
    // Selected albums for gallery view
    private val _selectedAlbums = MutableStateFlow<Set<String>?>(null)
    val selectedAlbums: StateFlow<Set<String>?> = _selectedAlbums.asStateFlow()
    
    // Pinch gesture enabled state
    private val _pinchGestureEnabled = MutableStateFlow(false)
    val pinchGestureEnabled: StateFlow<Boolean> = _pinchGestureEnabled.asStateFlow()
    
    // Copy to album dialog state
    private val _showCopyToAlbumDialog = MutableStateFlow(false)
    val showCopyToAlbumDialog: StateFlow<Boolean> = _showCopyToAlbumDialog.asStateFlow()
    
    private val _itemsToCopy = MutableStateFlow<List<Long>>(emptyList())
    val itemsToCopy: StateFlow<List<Long>> = _itemsToCopy.asStateFlow()
    
    // Move to album dialog state
    private val _showMoveToAlbumDialog = MutableStateFlow(false)
    val showMoveToAlbumDialog: StateFlow<Boolean> = _showMoveToAlbumDialog.asStateFlow()
    
    private val _itemsToMove = MutableStateFlow<List<Long>>(emptyList())
    val itemsToMove: StateFlow<List<Long>> = _itemsToMove.asStateFlow()
    
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
    private var pendingDeleteIds: List<Long> = emptyList()
    private var suppressObserverUntilMs: Long = 0L
    
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
    
    private val _isSearchBarActive = MutableStateFlow(false)
    val isSearchBarActive: StateFlow<Boolean> = _isSearchBarActive.asStateFlow()

    private val _topMlLabels = MutableStateFlow<List<String>>(emptyList())
    val topMlLabels: StateFlow<List<String>> = _topMlLabels.asStateFlow()

    fun loadTopMlLabels() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allLabels = database.mediaLabelDao().getAllLabels()
                val labelCounts = mutableMapOf<String, Int>()
                allLabels.forEach { entity ->
                    entity.labels.split(",").forEach { label ->
                        val cleanLabel = label.trim().lowercase()
                        if (cleanLabel.isNotEmpty()) {
                            labelCounts[cleanLabel] = (labelCounts[cleanLabel] ?: 0) + 1
                        }
                    }
                }
                val top = labelCounts.entries
                    .sortedByDescending { it.value }
                    .take(3)
                    .map { it.key }
                _topMlLabels.value = top
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setSearchBarActive(active: Boolean) {
        _isSearchBarActive.value = active
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val searchState: StateFlow<SearchState> = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) {
                flowOf<SearchState>(SearchState.Idle)
            } else {
                flow<SearchState> {
                    emit(SearchState.Loading)
                    searchMediaFlow(query).collect { rawMedia ->
                        if (rawMedia.isEmpty()) {
                            emit(SearchState.Empty)
                        } else {
                            val matchedAlbums = rawMedia
                                .groupBy { it.bucketName }
                                .filter { it.key.isNotEmpty() }
                                .map { (albumName, items) ->
                                    SearchEngine.AlbumMatch(
                                        albumName = albumName,
                                        items = items,
                                        matchPriority = 1
                                    )
                                }
                                .sortedByDescending { it.items.size }
                                
                            emit(SearchState.Success(SearchEngine.SearchResult(
                                matchedAlbums = matchedAlbums,
                                matchedMedia = rawMedia,
                                query = query
                            )))
                        }
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchState.Idle)
    
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
        val selectedItemId: Long? = null
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
                flowOf(emptyList())
            } else {
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
            started = SharingStarted.Lazily,
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
     * - Selected albums changes (Photos View filter)
     */
    val mediaFlow: StateFlow<List<MediaItem>> = _databaseReady
        .flatMapLatest { ready ->
            if (!ready) {
                flowOf(emptyList())
            } else {
                mediaEntitiesFlow
                    .combine(database.favoriteDao().getAllFavoriteIdsFlow()) { entities, favIds ->
                        withContext(Dispatchers.Default) {
                            traceGalleryPerf("mediaFlow.mapEntities") {
                                logPerfIfLarge("mediaFlow.toMediaItems", entities.size)
                                entities.toMediaItems(favIds.toSet()).toList()
                            }
                        }
                    }
                    .combine(_selectedAlbums) { mediaItems, selectedAlbums ->
                        withContext(Dispatchers.Default) {
                            if (BuildConfig.DEBUG) {
                                if (com.prantiux.pixelgallery.BuildConfig.DEBUG) android.util.Log.d("PHOTOS_FILTER", "mediaFlow: Media count before filter: ${mediaItems.size}")
                                if (com.prantiux.pixelgallery.BuildConfig.DEBUG) android.util.Log.d("PHOTOS_FILTER", "mediaFlow: Selected albums: ${selectedAlbums?.size ?: "null"} album(s)")
                            }

                            logPerfIfLarge("mediaFlow.albumFilter", mediaItems.size)

                            val filtered = if (selectedAlbums == null) {
                                mediaItems
                            } else if (selectedAlbums.isEmpty()) {
                                if (BuildConfig.DEBUG) {
                                    if (com.prantiux.pixelgallery.BuildConfig.DEBUG) android.util.Log.d("PHOTOS_FILTER", "mediaFlow: No albums selected → returning empty list")
                                }
                                emptyList()
                            } else {
                                mediaItems.filter { it.bucketId in selectedAlbums }
                            }

                            if (BuildConfig.DEBUG) {
                                if (com.prantiux.pixelgallery.BuildConfig.DEBUG) android.util.Log.d("PHOTOS_FILTER", "mediaFlow: Media count after filter: ${filtered.size}")
                            }

                            filtered.toList()
                        }
                    }
            }
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .conflate()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyList()
        )

    /**
     * PAGED MEDIA FLOW: Primary UI Flow for Paging 3 (Infinite Scroll)
     */
    private data class PagedMediaState(
        val ready: Boolean,
        val selected: Set<String>?,
        val sortMode: SortMode,
        val gridType: GridType,
        val favSet: Set<Long>
    )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val pagedMediaFlow: kotlinx.coroutines.flow.Flow<PagingData<MediaGridItem>> = _databaseReady
        .flatMapLatest { ready ->
            if (!ready) {
                flowOf(PagingData.empty())
            } else {
                kotlinx.coroutines.flow.combine(
                    _selectedAlbums,
                    _sortMode,
                    _gridType,
                    database.favoriteDao().getAllFavoriteIdsFlow()
                ) { selected, sortMode, gridType, favIds ->
                    PagedMediaState(true, selected, sortMode, gridType, favIds.toSet())
                }.flatMapLatest { state ->
                    Pager(
                        config = PagingConfig(
                            pageSize = 100, 
                            initialLoadSize = 100,
                            enablePlaceholders = true, 
                            prefetchDistance = 200
                        ),
                        pagingSourceFactory = {
                            if (state.selected == null || state.selected.isEmpty()) {
                                when (state.sortMode) {
                                    SortMode.DATE_DESC -> database.mediaDao().getPagedMediaByDateDesc()
                                    SortMode.DATE_ASC -> database.mediaDao().getPagedMediaByDateAsc()
                                    SortMode.NAME_ASC -> database.mediaDao().getPagedMediaByNameAsc()
                                    SortMode.NAME_DESC -> database.mediaDao().getPagedMediaByNameDesc()
                                    SortMode.SIZE_DESC -> database.mediaDao().getPagedMediaBySizeDesc()
                                    SortMode.SIZE_ASC -> database.mediaDao().getPagedMediaBySizeAsc()
                                }
                            } else {
                                val selectedList = state.selected.toList()
                                when (state.sortMode) {
                                    SortMode.DATE_DESC -> database.mediaDao().getPagedMediaByBucketIdsDateDesc(selectedList)
                                    SortMode.DATE_ASC -> database.mediaDao().getPagedMediaByBucketIdsDateAsc(selectedList)
                                    SortMode.NAME_ASC -> database.mediaDao().getPagedMediaByBucketIdsNameAsc(selectedList)
                                    SortMode.NAME_DESC -> database.mediaDao().getPagedMediaByBucketIdsNameDesc(selectedList)
                                    SortMode.SIZE_DESC -> database.mediaDao().getPagedMediaByBucketIdsSizeDesc(selectedList)
                                    SortMode.SIZE_ASC -> database.mediaDao().getPagedMediaByBucketIdsSizeAsc(selectedList)
                                }
                            }
                        }
                    ).flow
                    .map { pagingData ->
                        pagingData.map { entity ->
                            MediaGridItem.Media(entity.toMediaItem(isFavorite = entity.id in state.favSet))
                        }
                        .insertSeparators { before: MediaGridItem.Media?, after: MediaGridItem.Media? ->
                            if (after == null) return@insertSeparators null
                            
                            val beforeGroup = before?.let { if (state.gridType.isDay) it.mediaItem.dateGroupDay else it.mediaItem.dateGroupMonth }
                            val afterGroup = if (state.gridType.isDay) after.mediaItem.dateGroupDay else after.mediaItem.dateGroupMonth
                            
                            if (beforeGroup != afterGroup) {
                                val displayDate = formatDisplayDate(after.mediaItem.dateAdded * 1000L, state.gridType.isDay)
                                MediaGridItem.Header(displayDate = displayDate, dateGroupKey = afterGroup)
                            } else {
                                null
                            }
                        }
                    }
                }
            }
        }.cachedIn(viewModelScope)

    /**
     * ALL ALBUMS FLOW: Complete album list from Room (UNFILTERED)
     * 
     * Shows ALL albums regardless of Photos View album selection.
     * Used by Settings screen to display full album list with checkboxes.
     * Does NOT apply _selectedAlbums filter.
     */
    val allAlbumsFlow: StateFlow<List<Album>> = _databaseReady
        .flatMapLatest { ready ->
            if (!ready) {
                flowOf(emptyList())
            } else {
                database.mediaDao().getAllBucketsFlow()
                    .mapLatest { buckets ->
                        withContext(Dispatchers.IO) {
                            traceGalleryPerf("allAlbumsFlow.fromRoom") {
                                buckets.mapNotNull { bucket ->
                                    val topMedia = database.mediaDao().getTopMediaForBucket(bucket.bucketId, 6)
                                    if (topMedia.isNotEmpty()) {
                                        Album(
                                            id = bucket.bucketId,
                                            name = bucket.bucketName ?: "Unknown",
                                            coverUri = android.net.Uri.parse(topMedia.first().uri),
                                            itemCount = bucket.count,
                                            bucketDisplayName = bucket.bucketName ?: "Unknown",
                                            topMediaUris = topMedia.map { android.net.Uri.parse(it.uri) },
                                            topMediaItems = topMedia.map { it.toMediaItem() }
                                        )
                                    } else null
                                }
                            }
                        }
                    }
            }
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .conflate()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyList()
        )
    
    /**
     * ALBUMS FLOW: Album list from Room (Room-first)
     * 
     * Groups media by bucketId directly from Room.
     * UI should observe this instead of categorizedAlbums.
     */
    /**
     * ALBUMS FLOW: Filtered album list
     * 
     * Derives from allAlbumsFlow and filters by selectedAlbums.
     * This avoids heavy redundant .groupBy() operations caused by the incremental mediaFlow.
     */
    val albumsFlow: StateFlow<List<Album>> = allAlbumsFlow
        .combine(_selectedAlbums) { albums, selected ->
            if (selected == null) {
                albums
            } else if (selected.isEmpty()) {
                emptyList()
            } else {
                albums.filter { it.id in selected }
            }
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
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
            CategorizedAlbums(
                mainAlbums = albums.take(4),
                otherAlbums = albums.drop(4)
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CategorizedAlbums(emptyList(), emptyList())
        )
    
    /**
     * ALL CATEGORIZED ALBUMS FLOW: Complete album list categorized (UNFILTERED)
     * 
     * Derived from allAlbumsFlow (not filtered by _selectedAlbums).
     * Used by Settings screen to show ALL albums with checkboxes.
     * - mainAlbums: First 4 albums (top albums by item count)
     * - otherAlbums: Remaining albums
     */
    val allCategorizedAlbumsFlow: StateFlow<CategorizedAlbums> = allAlbumsFlow
        .map { albums ->
            CategorizedAlbums(
                mainAlbums = albums.take(4),
                otherAlbums = albums.drop(4)
            )
        }
        .distinctUntilChanged()
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
        if (query.isBlank()) {
            return kotlinx.coroutines.flow.flowOf(emptyList())
        }
        
        val normalizedQuery = query.lowercase(java.util.Locale.getDefault()).trim()
        
        // DETECT FILTERS using SearchEngine's logic
        val dateFilter = detectSearchDateFilter(normalizedQuery)
        val typeFilter = detectSearchMediaTypeFilter(normalizedQuery)
        val sizeFilter = detectSearchSizeFilter(normalizedQuery)
        val cleanQuery = removeKeywords(normalizedQuery)
        
        // SELECT APPROPRIATE DAO QUERY based on detected filters
        val daoQuery: kotlinx.coroutines.flow.Flow<List<MediaEntity>> = when {
            // SCREENSHOT (special case)
            normalizedQuery.contains("screenshot") || normalizedQuery.contains("screenshots") -> {
                if (cleanQuery.isBlank()) {
                    database.mediaDao().searchScreenshotsOnly()
                } else {
                    database.mediaDao().searchByScreenshots(cleanQuery)
                }
            }
            
            // CAMERA (special case)
            normalizedQuery.contains("camera") || normalizedQuery.contains("dcim") -> {
                if (cleanQuery.isBlank()) {
                    database.mediaDao().searchByCameraOnly()
                } else {
                    database.mediaDao().searchByCamera(cleanQuery)
                }
            }
            
            // GIF (special case)
            normalizedQuery.contains("gif") || normalizedQuery.contains("animation") || normalizedQuery.contains("animated") -> {
                if (cleanQuery.isBlank()) {
                    database.mediaDao().searchByGifOnly()
                } else {
                    database.mediaDao().searchByGif(cleanQuery)
                }
            }
            
            // ML LABEL SEARCH (if query looks like a label-only search)
            cleanQuery.isNotEmpty() && typeFilter == null && dateFilter == null && sizeFilter == null -> {
                database.mediaDao().searchByLabel(cleanQuery)
            }
            
            // ALL THREE FILTERS (Type + Size + Date)
            typeFilter != null && sizeFilter != null && dateFilter != null -> {
                val (isVideo, typeName) = parseMediaTypeFilter(typeFilter)
                val (minSize, maxSize, sizeName) = parseSizeFilter(sizeFilter)
                val (startMs, endMs, dateName) = parseDateFilter(dateFilter)
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
                val (isVideo, typeName) = parseMediaTypeFilter(typeFilter)
                val (startMs, endMs, dateName) = parseDateFilter(dateFilter)
                database.mediaDao().searchByTypeAndDate(
                    query = cleanQuery,
                    isVideo = isVideo,
                    startMs = startMs,
                    endMs = endMs
                )
            }
            
            // TYPE + SIZE
            typeFilter != null && sizeFilter != null && dateFilter == null -> {
                val (isVideo, typeName) = parseMediaTypeFilter(typeFilter)
                val (minSize, maxSize, sizeName) = parseSizeFilter(sizeFilter)
                database.mediaDao().searchByTypeAndSize(
                    query = cleanQuery,
                    isVideo = isVideo,
                    minSize = minSize,
                    maxSize = maxSize
                )
            }
            
            // SIZE + DATE
            sizeFilter != null && dateFilter != null && typeFilter == null -> {
                val (minSize, maxSize, sizeName) = parseSizeFilter(sizeFilter)
                val (startMs, endMs, dateName) = parseDateFilter(dateFilter)
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
                val (isVideo, typeName) = parseMediaTypeFilter(typeFilter)
                database.mediaDao().searchByMediaType(query = cleanQuery, isVideo = isVideo)
            }
            
            // ONLY SIZE
            sizeFilter != null && typeFilter == null && dateFilter == null -> {
                val (minSize, maxSize, sizeName) = parseSizeFilter(sizeFilter)
                database.mediaDao().searchBySize(query = cleanQuery, minSize = minSize, maxSize = maxSize)
            }
            
            // ONLY DATE
            dateFilter != null && typeFilter == null && sizeFilter == null -> {
                val (startMs, endMs, dateName) = parseDateFilter(dateFilter)
                database.mediaDao().searchByDateRange(query = cleanQuery, startMs = startMs, endMs = endMs)
            }
            
            // DEFAULT: Just name search
            else -> {
                database.mediaDao().searchMedia(cleanQuery)
            }
        }
        
        // COMBINE with favorite IDs and map to MediaItems
        return kotlinx.coroutines.flow.combine(
            daoQuery,
            database.favoriteDao().getAllFavoriteIdsFlow()
        ) { entities, favIds ->
            val mapped = withContext(Dispatchers.Default) {
                logPerfIfLarge("searchMediaFlow.toMediaItems", entities.size)
                entities.toMediaItems(favIds.toSet())
            }
            
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
            query.contains("video") || query.contains("mp4") || query.contains("movie") || query.contains("recording") -> SearchEngine.MediaTypeFilter.Videos
            query.contains("photo") || query.contains("image") || query.contains("picture") || query.contains("pic") -> SearchEngine.MediaTypeFilter.Photos
            query.contains("gif") || query.contains("animation") || query.contains("animated") -> SearchEngine.MediaTypeFilter.Gifs
            query.contains("screenshot") || query.contains("ss") || query.contains("capture") -> SearchEngine.MediaTypeFilter.Screenshots
            query.contains("camera") || query.contains("dcim") || query.contains("shot") -> SearchEngine.MediaTypeFilter.Camera
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
        
        val typeKeywords = listOf(
            "photos", "photo", "images", "image", "pictures", "picture", "pics", "pic",
            "videos", "video", "movies", "movie", "recordings", "recording", "mp4",
            "animations", "animation", "animated", "gifs", "gif",
            "screenshots", "screenshot", "screen capture", "ss",
            "camera", "dcim", "shots", "shot", "captured"
        ).sortedByDescending { it.length }
        typeKeywords.forEach { cleaned = cleaned.replace(it, "") }
        
        val sizeKeywords = listOf("small", "medium", "large").sortedByDescending { it.length }
        sizeKeywords.forEach { cleaned = cleaned.replace(it, "") }
        
        val commonWords = listOf("from", "in", "on", "the", "a", "an", "file", "files")
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
        val toSeconds: (Long) -> Long = { ms -> ms / 1000L }
        
        return when (filter) {
            SearchEngine.DateFilter.Today -> {
                cal.apply {
                    timeInMillis = now
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                }
                val startMs = cal.timeInMillis
                val endMs = startMs + (24 * 60 * 60 * 1000L) - 1
                Triple(toSeconds(startMs), toSeconds(endMs), "Today")
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
                val endMs = startMs + (24 * 60 * 60 * 1000L) - 1
                Triple(toSeconds(startMs), toSeconds(endMs), "Yesterday")
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
                Triple(toSeconds(startMs), toSeconds(endMs), "This Week")
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
                val endMs = startMs + (7 * 24 * 60 * 60 * 1000L) - 1
                Triple(toSeconds(startMs), toSeconds(endMs), "Last Week")
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
                Triple(toSeconds(startMs), toSeconds(endMs), "This Month")
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
                Triple(toSeconds(startMs), toSeconds(endMs), "Last Month")
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
                Triple(toSeconds(startMs), toSeconds(endMs), "${filter.year}")
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
                Triple(toSeconds(startMs), toSeconds(endMs), monthName)
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
        // Special case: "all" means all media, not a specific bucket
        if (bucketId == "all") {
            return mediaFlow
        }
        
        return kotlinx.coroutines.flow.combine(
            database.mediaDao().getMediaByBucket(bucketId),
            database.favoriteDao().getAllFavoriteIdsFlow()
        ) { entities, favIds ->
            withContext(Dispatchers.Default) {
                logPerfIfLarge("albumMediaFlow.toMediaItems", entities.size)
                entities
                    .toMediaItems(favIds.toSet())
                    .sortedByDescending { it.dateAdded }
                    .toList()
            }
        }
            .flowOn(Dispatchers.Default)
            .conflate()
    }

    /**
     * PAGED ALBUM MEDIA FLOW: Media from specific album for Paging 3
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun pagedAlbumMediaFlow(context: Context, bucketId: String): kotlinx.coroutines.flow.Flow<PagingData<MediaGridItem>> {
        if (bucketId == "all") {
            return pagedMediaFlow
        }
        
        if (SmartAlbumGenerator.isSmartAlbum(bucketId)) {
            return _gridType.flatMapLatest { type ->
                flow {
                    val smartMedia = withContext(Dispatchers.IO) {
                        try {
                            SmartAlbumGenerator.getMediaForSmartAlbum(context, bucketId)
                                .sortedByDescending { it.dateAdded }
                        } catch (e: Exception) {
                            emptyList()
                        }
                    }
                    
                    val gridItems = smartMedia.map { MediaGridItem.Media(it) }
                    
                    emit(PagingData.from(gridItems))
                }
            }
        }
        
        return kotlinx.coroutines.flow.combine(
            _databaseReady,
            _gridType,
            database.favoriteDao().getAllFavoriteIdsFlow()
        ) { ready, type, favIds ->
            Triple(ready, type, favIds.toSet())
        }.flatMapLatest { (ready, type, favSet) ->
            if (!ready) {
                flowOf(PagingData.empty())
            } else {
                Pager(
                    config = PagingConfig(pageSize = 100, enablePlaceholders = true, prefetchDistance = 200),
                    pagingSourceFactory = { database.mediaDao().getPagedMediaByBucketIdsDateDesc(listOf(bucketId)) }
                ).flow
                .map { pagingData ->
                    pagingData.map { entity ->
                        MediaGridItem.Media(entity.toMediaItem(isFavorite = entity.id in favSet))
                    }
                    .insertSeparators { before: MediaGridItem.Media?, after: MediaGridItem.Media? ->
                        if (after == null) return@insertSeparators null
                        
                        val beforeGroup = before?.let { if (type.isDay) it.mediaItem.dateGroupDay else it.mediaItem.dateGroupMonth }
                        val afterGroup = if (type.isDay) after.mediaItem.dateGroupDay else after.mediaItem.dateGroupMonth
                        
                        if (beforeGroup != afterGroup) {
                            val displayDate = formatDisplayDate(after.mediaItem.dateAdded * 1000L, type.isDay)
                            MediaGridItem.Header(displayDate = displayDate, dateGroupKey = afterGroup)
                        } else {
                            null
                        }
                    }
                }
            }
        }.cachedIn(viewModelScope)
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
                        // FILTER + MAP on background thread for large lists
                        val mapped = withContext(Dispatchers.Default) {
                            logPerfIfLarge("favoritesFlow.filterMap", allMedia.size)
                            val favIdSet = favIds.toSet()
                            val filtered = allMedia.filter { it.id in favIdSet }

                            // MAP: MediaEntity → MediaItem (set isFavorite=true for all filtered items)
                            filtered.toMediaItems(favIdSet).toList()
                        }
                        
                        mapped
                    }
            }
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyList()
        )

    // DELETED: groupedMediaFlow to prevent memory spikes. We use pagedMediaFlow with insertSeparators instead.
    // ─────────────────────────────────────────────────────────────────────────────────
    // LOADER STATE: Controlled by first Room emission
    // ─────────────────────────────────────────────────────────────────────────────────
    // LOADER STATE: Handled reactively by Paging and refresh()
    
    // Background sync job for periodic updates (non-blocking)
    private var backgroundSyncJob: Job? = null

    private fun logPerfIfLarge(stage: String, size: Int) {
        if (size >= PERF_LOG_THRESHOLD) {
            if (com.prantiux.pixelgallery.BuildConfig.DEBUG) android.util.Log.d(GALLERY_PERF_TAG, "$stage size=$size")
        }
    }

    private fun <T> traceGalleryPerf(section: String, block: () -> T): T {
        return block()
    }

    private fun buildIncrementalBatchSizes(totalSize: Int): List<Int> {
        if (totalSize <= 0) return emptyList()

        val seedBatches = INCREMENTAL_MEDIA_BATCHES
            .filter { it in 1..totalSize }
            .distinct()
            .sorted()

        val baseLast = seedBatches.lastOrNull() ?: 0
        val finalStageBatches = if (totalSize > FINAL_STAGE_SPLIT_START && baseLast >= FINAL_STAGE_SPLIT_START) {
            generateSequence(baseLast + FINAL_STAGE_STEP_SIZE) { previous ->
                previous + FINAL_STAGE_STEP_SIZE
            }
                .takeWhile { it < totalSize }
                .toList()
        } else {
            emptyList()
        }

        return (seedBatches + finalStageBatches + totalSize)
            .filter { it in 1..totalSize }
            .distinct()
            .sorted()
    }

    private fun emitIncrementalMediaBatches(fullList: List<MediaItem>) = flow {
        // Obsolete function. Return the full list immediately.
        emit(fullList)
    }

    fun initialize(context: Context) {
        if (::database.isInitialized) return
        
        repository = MediaRepository(context)
        albumRepository = AlbumRepository(context)
        recentSearchesDataStore = RecentSearchesDataStore(context)
        settingsDataStore = SettingsDataStore(context)
        database = AppDatabase.getDatabase(context)
        
        // Trigger reactive reconnection of flows
        _databaseReady.value = true
        
        // FAVORITES INITIALIZATION: Reactive Flow collection
        // Continuously observes getAllFavoriteIdsFlow()
        viewModelScope.launch {
            try {
                database.favoriteDao().getAllFavoriteIdsFlow().collect { ids ->
                    // Reactive collection - no logging needed for normal operation
                }
            } catch (e: Exception) {
                Log.e("FAVORITES", "Failed to observe favorite IDs", e)
            }
        }
        
        // Observe WorkManager for ML labeling progress
        observeLabelingProgress(context)
        
        // Update fully selected date groups whenever selection changes
        viewModelScope.launch {
            _selectedItems.collect { selectedIds ->
                if (selectedIds.isEmpty()) {
                    _fullySelectedDateGroups.value = emptySet()
                    return@collect
                }
                
                launch(Dispatchers.IO) {
                    val entities = database.mediaDao().getMediaByIds(selectedIds.toList())
                    val dayGroups = entities.groupBy { it.dateGroupDay }
                    val monthGroups = entities.groupBy { it.dateGroupMonth }
                    
                    val fullySelected = mutableSetOf<String>()
                    
                    for ((dayKey, items) in dayGroups) {
                        if (dayKey.isEmpty()) continue
                        val totalInDb = database.mediaDao().getMediaByDateGroupDay(dayKey).size
                        if (items.size == totalInDb) fullySelected.add(dayKey)
                    }
                    
                    for ((monthKey, items) in monthGroups) {
                        if (monthKey.isEmpty()) continue
                        val totalInDb = database.mediaDao().getMediaByDateGroupMonth(monthKey).size
                        if (items.size == totalInDb) fullySelected.add(monthKey)
                    }
                    
                    _fullySelectedDateGroups.value = fullySelected
                }
            }
        }
        
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
        
        // Load selected albums from DataStore with first-run default selection
        viewModelScope.launch {
            try {
                settingsDataStore.selectedAlbumsFlow.collect { albums ->
                    if (albums.isEmpty()) {
                        // First run: No albums in DataStore
                        // Detect all distinct bucketIds from Room and set as defaults
                        if (BuildConfig.DEBUG) {
                            if (com.prantiux.pixelgallery.BuildConfig.DEBUG) android.util.Log.d("PHOTOS_FILTER", "First run detected: No albums in DataStore")
                        }
                        
                        withContext(Dispatchers.IO) {
                            try {
                                val mediaEntities = database.mediaDao().getAllMedia().first()
                                val distinctBucketIds = mediaEntities
                                    .mapNotNull { it.bucketId }
                                    .distinct()
                                    .toSet()
                                
                                if (distinctBucketIds.isNotEmpty()) {
                                    if (BuildConfig.DEBUG) {
                                        if (com.prantiux.pixelgallery.BuildConfig.DEBUG) android.util.Log.d("PHOTOS_FILTER", "Applying default selection: ${distinctBucketIds.size} albums")
                                    }
                                    // Save to DataStore
                                    settingsDataStore.saveSelectedAlbums(distinctBucketIds)
                                    // Update ViewModel state
                                    _selectedAlbums.value = distinctBucketIds
                                } else {
                                    if (BuildConfig.DEBUG) {
                                        if (com.prantiux.pixelgallery.BuildConfig.DEBUG) android.util.Log.d("PHOTOS_FILTER", "No albums found in Room database")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("PHOTOS_FILTER", "Error applying default album selection", e)
                            }
                        }
                    } else {
                        // Not first run: User has previously selected albums
                        if (BuildConfig.DEBUG) {
                            if (com.prantiux.pixelgallery.BuildConfig.DEBUG) android.util.Log.d("PHOTOS_FILTER", "Selected albums changed: ${albums.size} album(s)")
                        }
                        _selectedAlbums.value = albums
                    }
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

        // Background sync on startup (no loader, keep UI responsive)
        // Delayed to prevent fighting with the initial UI rendering for CPU/IO
        viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(2000)
            refresh(context, showLoader = false)
        }
    }

    fun setMediaPermissionsGranted(granted: Boolean) {
        if (_mediaPermissionsGranted.value != granted) {
            _mediaPermissionsGranted.value = granted
            if (com.prantiux.pixelgallery.BuildConfig.DEBUG) android.util.Log.d("INIT_SETUP", "Media permissions granted = $granted")
        }
    }

    fun isMediaEmpty(): Boolean {
        return mediaFlow.value.isEmpty()
    }

    fun refresh(context: Context, showLoader: Boolean = false) {
        if (com.prantiux.pixelgallery.BuildConfig.DEBUG) android.util.Log.d("SYNC_ENGINE", "refresh() START: Checking repository initialization")
        if (!::repository.isInitialized) {
            initialize(context)
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            // Defensive guard: Prevent overlapping loads
            synchronized(refreshStateLock) {
                if (isSyncing) {
                    pendingRefresh = true
                    if (BuildConfig.DEBUG) {
                        if (com.prantiux.pixelgallery.BuildConfig.DEBUG) android.util.Log.d("SYNC_ENGINE", "Sync skipped (already syncing)")
                    }
                    return@launch
                }
                isSyncing = true
            }

            if (BuildConfig.DEBUG) {
                if (com.prantiux.pixelgallery.BuildConfig.DEBUG) android.util.Log.d(
                    "SYNC_ENGINE",
                    "[REFRESH-START] permissionsGranted=${_mediaPermissionsGranted.value}"
                )
            }
            var roomWriteCompleted = false
            val streamedIds = LinkedHashSet<Long>()
            var streamedItemCount = 0
            if (showLoader) {
                _isLoading.value = true
            }
            
            val loadStart = SystemClock.elapsedRealtime()
            if (BuildConfig.DEBUG) {
                if (com.prantiux.pixelgallery.BuildConfig.DEBUG) android.util.Log.d("SYNC_ENGINE", "MediaStore sync START")
            }
            if (com.prantiux.pixelgallery.BuildConfig.DEBUG) android.util.Log.d("SYNC_ENGINE", "refresh(): Starting MediaStore → Room sync")
            
            try {
                // ═════════════════════════════════════════════════════════════
                // ROOM-FIRST ARCHITECTURE: SYNC-ONLY OPERATION
                // ═════════════════════════════════════════════════════════════
                // MediaStore → Room sync only
                // UI updates automatically via Room flows (mediaFlow, albumsFlow, etc.)
                // NO direct UI StateFlow updates
                // ═════════════════════════════════════════════════════════════
                
                    val zoneId = java.time.ZoneId.systemDefault()
                    val dayFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd", java.util.Locale.getDefault())
                    val monthFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM", java.util.Locale.getDefault())
                    
                    val allMediaEntities = mutableListOf<MediaEntity>()

                    // STEP 1: Stream MediaStore rows and accumulate into a single batch
                    // Inserting all at once prevents multiple Paging 3 invalidations
                    repository.streamAllMediaBatches().collect { mediaBatch ->
                        streamedItemCount += mediaBatch.size
                        
                        val mediaEntities = mediaBatch.map { item ->
                            val instant = java.time.Instant.ofEpochSecond(item.dateAdded)
                            val zonedDateTime = instant.atZone(zoneId)
                            val dayStr = dayFormatter.format(zonedDateTime)
                            val monthStr = monthFormatter.format(zonedDateTime)
                            
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
                                path = item.path,
                                dateGroupDay = dayStr,
                                dateGroupMonth = monthStr
                            )
                        }

                        streamedIds.addAll(mediaEntities.map { it.id })
                        allMediaEntities.addAll(mediaEntities)
                    }

                    // Insert all media into Room in a single transaction
                    if (allMediaEntities.isNotEmpty()) {
                        withContext(Dispatchers.IO) {
                            try {
                                database.mediaDao().upsertAll(allMediaEntities)
                            } catch (e: Exception) {
                                android.util.Log.e("SYNC_ENGINE", "Error syncing all media to Room", e)
                            }
                        }
                    }

                withContext(Dispatchers.IO) {
                    try {
                        val existingIds = database.mediaDao().getAllIds()
                        val missingIds = existingIds.filterNot { it in streamedIds }
                        if (missingIds.isNotEmpty()) {
                            if (com.prantiux.pixelgallery.BuildConfig.DEBUG) android.util.Log.d("SYNC_ENGINE", "Deleting ${missingIds.size} stale items from Room")
                            database.mediaDao().deleteByIds(missingIds)
                        }

                        roomWriteCompleted = true
                    } catch (e: Exception) {
                        android.util.Log.e("SYNC_ENGINE", "Error finalizing Room sync", e)
                    }
                }

                if (BuildConfig.DEBUG) {
                    val queryDuration = SystemClock.elapsedRealtime() - loadStart
                    if (com.prantiux.pixelgallery.BuildConfig.DEBUG) android.util.Log.d("SYNC_ENGINE", "MediaStore streaming query completed: ${queryDuration}ms (${streamedItemCount} items)")
                }

                if (roomWriteCompleted) {
                    val permissionsGranted = _mediaPermissionsGranted.value
                    if (com.prantiux.pixelgallery.BuildConfig.DEBUG) android.util.Log.d(
                        "SYNC_ENGINE",
                        "[ROOM-WRITE-COMPLETE] items=$streamedItemCount, permissionsGranted=$permissionsGranted"
                    )
                }
                
                // STEP 3: Room flows auto-emit → UI updates automatically
                // - mediaFlow emits sorted media
                // - albumsFlow emits grouped albums
                // - imagesFlow/videosFlow emit filtered media
                // - No manual StateFlow updates needed!
                
                if (BuildConfig.DEBUG) {
                    if (com.prantiux.pixelgallery.BuildConfig.DEBUG) android.util.Log.d("SYNC_ENGINE", "Sync complete. Room flows will update UI automatically.")
                }
                
                // Schedule deferred ML labeling if needed
                scheduleDeferredLabelingIfNeeded(context)
                
            } catch (e: Exception) {
                android.util.Log.e("SYNC_ENGINE", "Error in refresh()", e)
            } finally {
                // Note: loader is now controlled by Room flow emissions (see init block)
                // Set to false here as fallback in case of errors
                _isLoading.value = false
                synchronized(refreshStateLock) {
                    isSyncing = false
                }
                if (com.prantiux.pixelgallery.BuildConfig.DEBUG) android.util.Log.d(
                    "SYNC_ENGINE",
                    "[REFRESH-END] isSyncing=false, isLoading=${_isLoading.value}"
                )

                val triggerPendingRefresh = synchronized(refreshStateLock) {
                    if (pendingRefresh) {
                        pendingRefresh = false
                        true
                    } else {
                        false
                    }
                }

                if (triggerPendingRefresh) {
                    refresh(context, showLoader = false)
                }
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

    fun delete(context: Context, item: MediaItem, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.delete(item)
            if (success) {
                refresh(context, showLoader = false)
            }
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
        selectedItemId: Long? = null
    ) {
        _overlayState.value = MediaOverlayState(
            isVisible = true,
            mediaType = mediaType,
            albumId = albumId,
            selectedIndex = selectedIndex,
            searchQuery = searchQuery,
            selectedItemId = selectedItemId
        )
    }

    fun showMediaOverlayWithItem(
        mediaType: String,
        albumId: String,
        item: MediaItem,
        searchQuery: String? = null
    ) {
        // For album/smartalbum types, the overlay resolves the correct item by selectedItemId
        // within the album-specific list. Using a global mediaFlow index causes wrong items to open.
        // For photos/search/favorites, use the correct list index.
        val index = when (mediaType) {
            "album", "smartalbum" -> {
                // Use 0 as placeholder; overlay will resolve correct position by selectedItemId
                0
            }
            "favorites" -> favoritesFlow.value.indexOf(item).takeIf { it >= 0 } ?: 0
            else -> mediaFlow.value.indexOf(item).takeIf { it >= 0 } ?: 0
        }
        showMediaOverlay(mediaType, albumId, index, searchQuery, item.id)
    }

    fun hideMediaOverlay() {
        _overlayState.value = _overlayState.value.copy(isVisible = false)
    }

    fun updateOverlayIndex(index: Int) {
        _overlayState.value = _overlayState.value.copy(selectedIndex = index)
    }
    
    // Selection mode functions
    fun enterSelectionMode(itemId: Long) {
        _isSelectionMode.value = true
        _selectedItems.value = setOf(itemId)
    }
    
    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedItems.value = emptySet()
    }
    
    fun toggleSelection(itemId: Long) {
        _selectedItems.value = if (_selectedItems.value.contains(itemId)) {
            _selectedItems.value - itemId
        } else {
            _selectedItems.value + itemId
        }
        // Exit selection mode if no items selected
        if (_selectedItems.value.isEmpty()) {
            _isSelectionMode.value = false
        }
    }
    
    fun selectItem(itemId: Long) {
        _selectedItems.value = _selectedItems.value + itemId
    }
    
    fun deselectItem(itemId: Long) {
        _selectedItems.value = _selectedItems.value - itemId
        if (_selectedItems.value.isEmpty()) {
            _isSelectionMode.value = false
        }
    }
    
    fun setSelectedItems(itemIds: Set<Long>) {
        _selectedItems.value = itemIds
        if (_selectedItems.value.isEmpty()) {
            _isSelectionMode.value = false
        }
    }
    
    fun selectAll(itemIds: List<Long>) {
        _selectedItems.value = itemIds.toSet()
    }
    
    fun selectAllInGroup(itemIds: List<Long>) {
        val currentSelection = _selectedItems.value.toMutableSet()
        currentSelection.addAll(itemIds)
        _selectedItems.value = currentSelection
    }
    
    fun deselectAllInGroup(itemIds: List<Long>) {
        _selectedItems.value = _selectedItems.value - itemIds.toSet()
        if (_selectedItems.value.isEmpty()) {
            _isSelectionMode.value = false
        }
    }

    fun selectAllInDateGroup(dateGroupKey: String, isDay: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val items = if (isDay) {
                database.mediaDao().getMediaByDateGroupDay(dateGroupKey)
            } else {
                database.mediaDao().getMediaByDateGroupMonth(dateGroupKey)
            }
            val ids = items.map { it.id }.toSet()
            
            withContext(Dispatchers.Main) {
                val currentSelection = _selectedItems.value
                val isFullySelected = currentSelection.containsAll(ids)
                
                if (isFullySelected) {
                    // Deselect
                    _selectedItems.value = currentSelection - ids
                    if (_selectedItems.value.isEmpty()) {
                        _isSelectionMode.value = false
                    }
                } else {
                    // Select
                    _selectedItems.value = currentSelection + ids
                    _isSelectionMode.value = true
                }
            }
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
                    val selectedIds = _selectedItems.value.toList()
                    val uris = database.mediaDao().getMediaByIds(selectedIds).map { android.net.Uri.parse(it.uri) }
                    val success = repository.deleteMediaItems(context, uris)
                    if (success) {
                        removeMediaFromRoom(selectedIds)
                        markLocalMutation()
                        exitSelectionMode()
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
            val deletedIds = _selectedItems.value.toList()
            val itemCount = deletedIds.size
            
            // Fast check for item type if needed, but for delete message just use item count
            val itemType = if (itemCount == 1) "item" else "items"
            
            _deleteSuccessMessage.value = "$itemCount $itemType moved to trash"
            
            removeMediaFromRoom(deletedIds)
            markLocalMutation()
            exitSelectionMode()
            
            // Auto-dismiss after 2 seconds
            kotlinx.coroutines.delay(2000)
            _deleteSuccessMessage.value = null
        }
    }
    
    // Called when user cancels the trash request
    fun onDeleteCancelled() {
        exitSelectionMode()
    }
    
    // Get trash request for Android 11+ (returns PendingIntent to launch)
    suspend fun getTrashRequest(): android.app.PendingIntent? {
        val selectedIds = _selectedItems.value.toList()
        val uris = database.mediaDao().getMediaByIds(selectedIds).map { android.net.Uri.parse(it.uri) }
        return repository.createTrashRequest(uris)
    }
    
    fun shareSelectedItems(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val selectedIds = _selectedItems.value.toList()
            val uris = database.mediaDao().getMediaByIds(selectedIds).map { android.net.Uri.parse(it.uri) }
            withContext(Dispatchers.Main) {
                repository.shareMediaItems(context, uris)
            }
        }
    }
    
    fun setAsWallpaper(context: Context, itemId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val items = database.mediaDao().getMediaByIds(listOf(itemId))
            val item = items.firstOrNull()
            
            withContext(Dispatchers.Main) {
                if (item != null) {
                    if (!item.isVideo) {
                        val uri = android.net.Uri.parse(item.uri)
                        val wallpaperIntent = android.content.Intent(android.content.Intent.ACTION_ATTACH_DATA).apply {
                            setDataAndType(uri, "image/*")
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(wallpaperIntent, "Set as"))
                    } else {
                        android.widget.Toast.makeText(context, "Cannot set video as wallpaper", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    
    /**
     * Hide selected items from a smart album by removing the associated labels
     */
    suspend fun hideFromSmartAlbum(context: Context, smartAlbumId: String, items: List<Long>) {
        try {
            val albumType = com.prantiux.pixelgallery.smartalbum.SmartAlbumGenerator.fromId(smartAlbumId)
            if (albumType == null) {
                android.util.Log.e("MediaViewModel", "Invalid smart album ID: $smartAlbumId")
                return
            }
            
            val labelDao = database.mediaLabelDao()
            val labelsToRemove = albumType.positiveLabels.map { it.lowercase() }.toSet()
            
            items.forEach { mediaItemId ->
                // Get existing label entity
                val existingEntity = labelDao.getLabelsForMedia(mediaItemId)
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
            
            if (com.prantiux.pixelgallery.BuildConfig.DEBUG) android.util.Log.d("MediaViewModel", "Hidden ${items.size} items from smart album: ${albumType.displayName}")
        } catch (e: Exception) {
            android.util.Log.e("MediaViewModel", "Error hiding items from smart album", e)
        }
    }
    
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
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
    
    fun clearSearchQuery() {
        _searchQuery.value = ""
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
    


    /**
     * Recreates the smart formatting from MediaGrouping.kt
     */
    private fun formatDisplayDate(dateAddedMs: Long, isDay: Boolean): String {
        val calendar = java.util.Calendar.getInstance()
        val currentYear = calendar.get(java.util.Calendar.YEAR)
        calendar.timeInMillis = dateAddedMs
        val itemYear = calendar.get(java.util.Calendar.YEAR)

        if (isDay) {
            val today = java.util.Calendar.getInstance()
            val yesterday = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }

            val isToday = itemYear == today.get(java.util.Calendar.YEAR) && calendar.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR)
            val isYesterday = itemYear == yesterday.get(java.util.Calendar.YEAR) && calendar.get(java.util.Calendar.DAY_OF_YEAR) == yesterday.get(java.util.Calendar.DAY_OF_YEAR)

            return when {
                isToday -> "Today"
                isYesterday -> "Yesterday"
                itemYear == currentYear -> java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(calendar.time)
                else -> java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault()).format(calendar.time)
            }
        } else {
            return if (itemYear == currentYear) {
                java.text.SimpleDateFormat("MMMM", java.util.Locale.getDefault()).format(calendar.time)
            } else {
                java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(calendar.time)
            }
        }
    }

    /**
     * Scrollbar fast query helpers
     */
    val photosDateGroups: StateFlow<List<com.prantiux.pixelgallery.ui.components.DateGroupInfo>> = kotlinx.coroutines.flow.combine(mediaFlow, _gridType) { items, type ->
        val groups = mutableListOf<com.prantiux.pixelgallery.ui.components.DateGroupInfo>()
        var currentGroupKey = ""
        var currentCount = 0
        var groupDateAdded = 0L

        for (item in items) {
            val key = if (type.isDay) item.dateGroupDay else item.dateGroupMonth
            if (key != currentGroupKey) {
                if (currentGroupKey.isNotEmpty()) {
                    groups.add(
                        com.prantiux.pixelgallery.ui.components.DateGroupInfo(
                            date = currentGroupKey,
                            displayDate = formatDisplayDate(groupDateAdded * 1000L, type.isDay),
                            itemCount = currentCount
                        )
                    )
                }
                currentGroupKey = key
                currentCount = 1
                groupDateAdded = item.dateAdded
            } else {
                currentCount++
            }
        }
        if (currentGroupKey.isNotEmpty()) {
            groups.add(
                com.prantiux.pixelgallery.ui.components.DateGroupInfo(
                    date = currentGroupKey,
                    displayDate = formatDisplayDate(groupDateAdded * 1000L, type.isDay),
                    itemCount = currentCount
                )
            )
        }
        groups
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Lazily, emptyList())

    fun getAlbumDateGroups(bucketId: String): kotlinx.coroutines.flow.Flow<List<com.prantiux.pixelgallery.ui.components.DateGroupInfo>> {
        return kotlinx.coroutines.flow.combine(albumMediaFlow(bucketId), _gridType) { items, type ->
            val groups = mutableListOf<com.prantiux.pixelgallery.ui.components.DateGroupInfo>()
            var currentGroupKey = ""
            var currentCount = 0
            var groupDateAdded = 0L

            for (item in items) {
            val key = if (type.isDay) item.dateGroupDay else item.dateGroupMonth
                if (key != currentGroupKey) {
                    if (currentGroupKey.isNotEmpty()) {
                        groups.add(
                            com.prantiux.pixelgallery.ui.components.DateGroupInfo(
                                date = currentGroupKey,
                                displayDate = formatDisplayDate(groupDateAdded * 1000L, type.isDay),
                                itemCount = currentCount
                            )
                        )
                    }
                    currentGroupKey = key
                    currentCount = 1
                    groupDateAdded = item.dateAdded
                } else {
                    currentCount++
                }
            }
            if (currentGroupKey.isNotEmpty()) {
                groups.add(
                    com.prantiux.pixelgallery.ui.components.DateGroupInfo(
                        date = currentGroupKey,
                        displayDate = formatDisplayDate(groupDateAdded * 1000L, type.isDay),
                        itemCount = currentCount
                    )
                )
            }
            groups
        }
    }
    
    // Permanently delete multiple items from trash
    private fun permanentlyDeleteTrashedItems(context: Context, items: List<MediaItem>) {
        val uris = items.map { it.uri }
        pendingDeleteUris = uris
        pendingDeleteIds = items.map { it.id }
        
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
                if (com.prantiux.pixelgallery.BuildConfig.DEBUG) android.util.Log.d("MediaViewModel", "Successfully restored $itemCount items")
                
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
            refresh(context, showLoader = false)
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
                if (com.prantiux.pixelgallery.BuildConfig.DEBUG) android.util.Log.d("MediaViewModel", "Successfully deleted $itemCount items")
                
                val itemType = if (itemCount == 1) "item" else "items"
                _deleteSuccessMessage.value = "$itemCount $itemType permanently deleted"
                
                // Auto-dismiss after 2 seconds
                viewModelScope.launch {
                    kotlinx.coroutines.delay(2000)
                    _deleteSuccessMessage.value = null
                }
            }
            if (pendingDeleteIds.isNotEmpty()) {
                removeMediaFromRoom(pendingDeleteIds)
                markLocalMutation()
            }
            pendingDeleteUris = emptyList()
            pendingDeleteIds = emptyList()
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
        _selectedTrashItems.value = setOf(item.id)
    }
    
    fun exitTrashSelectionMode() {
        _isTrashSelectionMode.value = false
        _selectedTrashItems.value = emptySet()
    }
    
    fun toggleTrashSelection(item: MediaItem) {
        _selectedTrashItems.value = if (_selectedTrashItems.value.contains(item.id)) {
            _selectedTrashItems.value - item.id
        } else {
            _selectedTrashItems.value + item.id
        }
    }
    
    fun selectTrashGroup(items: List<MediaItem>) {
        _selectedTrashItems.value = _selectedTrashItems.value + items.map { it.id }
    }
    
    fun setSelectedTrashItems(itemIds: Set<Long>) {
        _selectedTrashItems.value = itemIds
        if (itemIds.isEmpty()) {
            _isTrashSelectionMode.value = false
        }
    }
    
    fun deselectTrashGroup(items: List<MediaItem>) {
        _selectedTrashItems.value = _selectedTrashItems.value - items.map { it.id }.toSet()
    }
    
    // Restore selected items from trash (from RecycleBinScreen)
    fun restoreSelectedTrashItems(context: Context) {
        viewModelScope.launch {
            val itemIds = _selectedTrashItems.value.toSet()
            if (itemIds.isNotEmpty()) {
                val items = _trashedItems.value.filter { itemIds.contains(it.id) }
                restoreTrashedItems(context, items)
            }
        }
    }
    
    // Delete selected items from trash (from RecycleBinScreen)
    fun deleteSelectedTrashItems(context: Context) {
        viewModelScope.launch {
            val itemIds = _selectedTrashItems.value.toSet()
            if (itemIds.isNotEmpty()) {
                val items = _trashedItems.value.filter { itemIds.contains(it.id) }
                permanentlyDeleteTrashedItems(context, items)
            }
        }
    }
    
    // Trash overlay functions
    fun showTrashMediaOverlay(
        selectedIndex: Int
    ) {
        _overlayState.value = MediaOverlayState(
            isVisible = true,
            mediaType = "trash",
            albumId = "",
            selectedIndex = selectedIndex,
            searchQuery = null
        )
    }
    
    // Favorites functions - ROOM-FIRST: Only write to database, let Flow streams re-emit
    fun toggleFavorite(mediaId: Long, newState: Boolean) {
        viewModelScope.launch {
            try {
                if (newState) {
                    database.favoriteDao().addFavorite(FavoriteEntity(mediaId))
                } else {
                    database.favoriteDao().removeFavorite(mediaId)
                }
            } catch (e: Exception) {
                android.util.Log.e("FAVORITES_TOGGLE", "DATABASE ERROR: ${e.message}", e)
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
    fun showCopyToAlbumDialog(itemIds: List<Long>) {
        _itemsToCopy.value = itemIds
        _showCopyToAlbumDialog.value = true
    }
    
    fun hideCopyToAlbumDialog() {
        _showCopyToAlbumDialog.value = false
        _itemsToCopy.value = emptyList()
    }
    
    suspend fun copyToAlbum(context: Context, targetAlbum: Album): Boolean {
        return if (::repository.isInitialized) {
            val itemIds = _itemsToCopy.value
            if (itemIds.isNotEmpty()) {
                if (com.prantiux.pixelgallery.BuildConfig.DEBUG) android.util.Log.d("MediaViewModel", "Copying ${itemIds.size} items to ${targetAlbum.name}")
                val favIds = database.favoriteDao().getAllFavoriteIds().toSet()
                val items = database.mediaDao().getMediaByIds(itemIds).toMediaItems(favIds)
                val success = repository.copyMediaToAlbum(items, targetAlbum)
                
                if (com.prantiux.pixelgallery.BuildConfig.DEBUG) android.util.Log.d("MediaViewModel", "Copy operation result: $success")
                
                if (success) {
                    // Show success message
                    val itemType = if (items.size == 1) {
                        if (items.first().isVideo) "video" else "image"
                    } else {
                        "items"
                    }
                    _copySuccessMessage.value = "Copied ${items.size} $itemType to ${targetAlbum.name}"
                    
                    // Mark as mutated and dismiss selection
                    markLocalMutation()
                    exitSelectionMode()
                    
                    // Trigger a refresh after a small delay to let MediaStore update
                    kotlinx.coroutines.delay(500)
                    refresh(context, showLoader = false)
                    
                    // Auto-dismiss after 2 seconds
                    kotlinx.coroutines.delay(2000)
                    _copySuccessMessage.value = null
                }
                success
            } else {
                false
            }
        } else {
            android.util.Log.e("MediaViewModel", "Repository not initialized")
            false
        }
    }
    
    // Move to album functions
    fun showMoveToAlbumDialog(itemIds: List<Long>) {
        _itemsToMove.value = itemIds
        _showMoveToAlbumDialog.value = true
    }
    
    fun hideMoveToAlbumDialog() {
        _showMoveToAlbumDialog.value = false
        _itemsToMove.value = emptyList()
    }
    
    suspend fun moveToAlbum(context: Context, targetAlbum: Album): Boolean {
        return if (::repository.isInitialized) {
            val itemIds = _itemsToMove.value
            if (itemIds.isNotEmpty()) {
                if (com.prantiux.pixelgallery.BuildConfig.DEBUG) android.util.Log.d("MediaViewModel", "Moving ${itemIds.size} items to ${targetAlbum.name}")
                val favIds = database.favoriteDao().getAllFavoriteIds().toSet()
                val items = database.mediaDao().getMediaByIds(itemIds).toMediaItems(favIds)
                val result = repository.moveMediaToAlbum(items, targetAlbum)
                
                if (com.prantiux.pixelgallery.BuildConfig.DEBUG) android.util.Log.d("MediaViewModel", "Move operation result: ${result.success}, message: ${result.message}")
                
                if (result.success) {
                    if (com.prantiux.pixelgallery.BuildConfig.DEBUG) android.util.Log.d("MediaViewModel", "Move completed successfully")
                    
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
                    
                    // Remove from room cache immediately for responsive UI
                    removeMediaFromRoom(itemIds)
                    markLocalMutation()
                    
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
            mediaContentObserver = MediaContentObserver(context) { urisToProcess ->
                // ROOM-FIRST: ContentObserver triggers MediaStore → Room sync
                // Room flows auto-emit → UI updates automatically
                val now = SystemClock.elapsedRealtime()
                if (now < suppressObserverUntilMs) {
                    if (com.prantiux.pixelgallery.BuildConfig.DEBUG) android.util.Log.d("SYNC_ENGINE", "MediaStore change detected, sync suppressed")
                    return@MediaContentObserver
                }
                
                // Always trigger a full refresh. This is fast and guarantees we pick up items
                // that have their IS_PENDING flag cleared, bypassing incremental sync issues.
                if (com.prantiux.pixelgallery.BuildConfig.DEBUG) android.util.Log.d("SYNC_ENGINE", "MediaStore change detected, triggering full sync")
                refresh(context, showLoader = false)
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
                    if (com.prantiux.pixelgallery.BuildConfig.DEBUG) android.util.Log.d("MediaViewModel", "ML: Deferred labeling scheduled ($unlabeledCount unlabeled)")
                    com.prantiux.pixelgallery.ml.ImageLabelScheduler.scheduleDeferredLabeling(context)
                } else {
                    if (com.prantiux.pixelgallery.BuildConfig.DEBUG) android.util.Log.d("MediaViewModel", "ML: No labeling needed (all media labeled)")
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

    private fun markLocalMutation() {
        suppressObserverUntilMs = SystemClock.elapsedRealtime() + 1500L
    }

    private suspend fun removeMediaFromRoom(ids: List<Long>) {
        if (ids.isEmpty()) return
        withContext(Dispatchers.IO) {
            database.mediaDao().deleteByIds(ids)
            database.favoriteDao().removeFavorites(ids)
            database.mediaLabelDao().deleteLabelsForMediaIds(ids)
        }
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


}
