package com.prantiux.pixelgallery.viewmodel

import android.content.Context
import android.os.Build
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
import com.prantiux.pixelgallery.data.MediaRepository
import com.prantiux.pixelgallery.data.RecentSearchesDataStore
import com.prantiux.pixelgallery.data.SettingsDataStore
import com.prantiux.pixelgallery.ml.ImageLabelWorker
import com.prantiux.pixelgallery.model.Album
import com.prantiux.pixelgallery.model.CategorizedAlbums
import com.prantiux.pixelgallery.model.MediaItem
import com.prantiux.pixelgallery.search.SearchEngine
import com.prantiux.pixelgallery.search.SearchResultFilter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SortMode {
    DATE_DESC, DATE_ASC, NAME_ASC, NAME_DESC, SIZE_DESC, SIZE_ASC
}

enum class GridType {
    DAY, MONTH
}

class MediaViewModel : ViewModel() {
    private lateinit var repository: MediaRepository
    private lateinit var albumRepository: AlbumRepository
    private lateinit var recentSearchesDataStore: RecentSearchesDataStore
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var database: AppDatabase
    private var mediaContentObserver: MediaContentObserver? = null

    private val _images = MutableStateFlow<List<MediaItem>>(emptyList())
    val images: StateFlow<List<MediaItem>> = _images.asStateFlow()

    private val _videos = MutableStateFlow<List<MediaItem>>(emptyList())
    val videos: StateFlow<List<MediaItem>> = _videos.asStateFlow()
    
    // Unfiltered media for album detail views (not affected by gallery view settings)
    private val _allImagesUnfiltered = MutableStateFlow<List<MediaItem>>(emptyList())
    val allImagesUnfiltered: StateFlow<List<MediaItem>> = _allImagesUnfiltered.asStateFlow()
    
    private val _allVideosUnfiltered = MutableStateFlow<List<MediaItem>>(emptyList())
    val allVideosUnfiltered: StateFlow<List<MediaItem>> = _allVideosUnfiltered.asStateFlow()
    
    private val _favoriteIds = MutableStateFlow<Set<Long>>(emptySet())
    val favoriteIds: StateFlow<Set<Long>> = _favoriteIds.asStateFlow()
    
    private val _favoriteItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val favoriteItems: StateFlow<List<MediaItem>> = _favoriteItems.asStateFlow()

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _smartAlbumThumbnailCache = mutableStateMapOf<String, android.net.Uri?>()
    val smartAlbumThumbnailCache: SnapshotStateMap<String, android.net.Uri?>
        get() = _smartAlbumThumbnailCache

    // REFACTORED: Now non-nullable and always derived from cached media
    private val _categorizedAlbums = MutableStateFlow<CategorizedAlbums>(CategorizedAlbums(emptyList(), emptyList()))
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

    private val _searchResults = MutableStateFlow<SearchEngine.SearchResult>(
        SearchEngine.SearchResult(emptyList(), emptyList(), "")
    )
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

    // REFACTORED: Single source of truth for all media
    // All tabs derive their data from these cached lists
    private var allImages = listOf<MediaItem>()
    private var allVideos = listOf<MediaItem>()
    
    // Background sync job for periodic updates (non-blocking)
    private var backgroundSyncJob: Job? = null

    fun initialize(context: Context) {
        repository = MediaRepository(context)
        albumRepository = AlbumRepository(context)
        recentSearchesDataStore = RecentSearchesDataStore(context)
        settingsDataStore = SettingsDataStore(context)
        database = AppDatabase.getDatabase(context)
        
        // Load favorite IDs from database
        viewModelScope.launch {
            try {
                val ids = database.favoriteDao().getAllFavoriteIds()
                _favoriteIds.value = ids.toSet()
            } catch (e: Exception) {
                // Ignore errors
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
                    _selectedAlbums.value = albums
                    // Re-apply sorting to filter images whenever selection changes
                    if (::repository.isInitialized && allImages.isNotEmpty()) {
                        applySorting()
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
    }

    /**
     * REFACTORED: Single MediaStore query that populates ALL data structures
     * 
     * Flow:
     * 1. Query MediaStore ONCE (images + videos)
     * 2. Store raw results in allImages/allVideos (private cache)
     * 3. Populate all StateFlows:
     *    - _images (filtered/sorted for Photos tab)
     *    - _videos (filtered/sorted for Photos tab)
     *    - _allImagesUnfiltered (for album detail views)
     *    - _allVideosUnfiltered (for album detail views)
     *    - _allMediaCached (for search)
     *    - _albums (derived from cache)
     *    - _categorizedAlbums (derived from cache)
     * 4. All tabs now read from StateFlows - NO additional MediaStore queries
     * 5. Optional: Start background sync for future updates
     */
    fun isMediaEmpty(): Boolean {
        return _allImagesUnfiltered.value.isEmpty() && _allVideosUnfiltered.value.isEmpty()
    }

    fun refresh(context: Context) {
        if (!::repository.isInitialized) {
            initialize(context)
        }
        
        viewModelScope.launch {
            // Defensive guard: Prevent overlapping loads
            if (_isLoading.value) {
                android.util.Log.d("MediaLoad", "Load skipped (already loading)")
                return@launch
            }
            
            _isLoading.value = true
            android.util.Log.d("MediaLoad", "Media load START")
            
            try {
                // ═══════════════════════════════════════════════════════════
                // STEP 1: Query MediaStore ONCE
                // ═══════════════════════════════════════════════════════════
                allImages = repository.loadImages()
                allVideos = repository.loadVideos()
                
                val totalItems = allImages.size + allVideos.size
                android.util.Log.d("MediaLoad", "Media load END ($totalItems items)")
                
                // ═══════════════════════════════════════════════════════════
                // STEP 2: Populate all StateFlows from cached data
                // ═══════════════════════════════════════════════════════════
                
                // Update unfiltered StateFlows for album detail views
                _allImagesUnfiltered.value = allImages
                _allVideosUnfiltered.value = allVideos
                
                // Cache combined media for search (sorted once)
                _allMediaCached.value = (allImages + allVideos).sortedByDescending { it.dateAdded }
                
                // Derive albums from cached media (no additional MediaStore query)
                val generatedAlbums = generateAlbumsFromCache(allImages + allVideos)
                _albums.value = generatedAlbums
                
                // Categorize albums from cached data
                val categorized = categorizeAlbumsFromCache(generatedAlbums)
                _categorizedAlbums.value = categorized
                
                // Apply current sort/filter to populate Photos tab data
                applySorting()
                
                // ═══════════════════════════════════════════════════════════
                // STEP 3: Start background sync (optional, non-blocking)
                // ═══════════════════════════════════════════════════════════
                startBackgroundSync(context)
                
            } catch (e: Exception) {
                android.util.Log.e("MediaLoad", "Error loading media", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
        applySorting()
    }

    private fun applySorting() {
        val favoriteIdSet = _favoriteIds.value
        val selectedAlbumIds = _selectedAlbums.value
        
        // Filter images by selected albums - if selection was explicitly made and is empty, show nothing
        val filteredImages = if (selectedAlbumIds.isEmpty()) {
            // Show nothing when explicitly unselected all albums
            emptyList()
        } else {
            allImages.filter { selectedAlbumIds.contains(it.bucketId) }
        }
        
        // Filter videos by selected albums - if selection was explicitly made and is empty, show nothing
        val filteredVideos = if (selectedAlbumIds.isEmpty()) {
            // Show nothing when explicitly unselected all albums
            emptyList()
        } else {
            allVideos.filter { selectedAlbumIds.contains(it.bucketId) }
        }
        
        _images.value = when (_sortMode.value) {
            SortMode.DATE_DESC -> filteredImages.sortedByDescending { it.dateAdded }
            SortMode.DATE_ASC -> filteredImages.sortedBy { it.dateAdded }
            SortMode.NAME_ASC -> filteredImages.sortedBy { it.displayName.lowercase() }
            SortMode.NAME_DESC -> filteredImages.sortedByDescending { it.displayName.lowercase() }
            SortMode.SIZE_DESC -> filteredImages.sortedByDescending { it.size }
            SortMode.SIZE_ASC -> filteredImages.sortedBy { it.size }
        }.map { it.copy(isFavorite = favoriteIdSet.contains(it.id)) }

        _videos.value = when (_sortMode.value) {
            SortMode.DATE_DESC -> filteredVideos.sortedByDescending { it.dateAdded }
            SortMode.DATE_ASC -> filteredVideos.sortedBy { it.dateAdded }
            SortMode.NAME_ASC -> filteredVideos.sortedBy { it.displayName.lowercase() }
            SortMode.NAME_DESC -> filteredVideos.sortedByDescending { it.displayName.lowercase() }
            SortMode.SIZE_DESC -> filteredVideos.sortedByDescending { it.size }
            SortMode.SIZE_ASC -> filteredVideos.sortedBy { it.size }
        }.map { it.copy(isFavorite = favoriteIdSet.contains(it.id)) }
        
        // Update favorite items list with only items from selected albums
        updateFavoritesList(selectedAlbumIds)
    }
    
    private fun updateFavoritesList(selectedAlbumIds: Set<String>) {
        val favoriteIdSet = _favoriteIds.value
        
        // Filter favorites by selected albums
        val filteredMedia = if (selectedAlbumIds.isEmpty()) {
            allImages + allVideos
        } else {
            (allImages + allVideos).filter { selectedAlbumIds.contains(it.bucketId) }
        }
        
        _favoriteItems.value = filteredMedia
            .map { it.copy(isFavorite = favoriteIdSet.contains(it.id)) }
            .filter { it.isFavorite }
            .sortedByDescending { it.dateAdded }
    }

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
        return allImages + allVideos
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
    
    // Favorites functions
    fun toggleFavorite(mediaId: Long, newState: Boolean) {
        viewModelScope.launch {
            try {
                if (newState) {
                    database.favoriteDao().addFavorite(FavoriteEntity(mediaId))
                    _favoriteIds.value = _favoriteIds.value + mediaId
                } else {
                    database.favoriteDao().removeFavorite(mediaId)
                    _favoriteIds.value = _favoriteIds.value - mediaId
                }
                // Reapply sorting to update isFavorite flags
                applySorting()
            } catch (e: Exception) {
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
                // When media changes, refresh the gallery
                refresh(context)
            }
            mediaContentObserver?.register(viewModelScope)
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
}
