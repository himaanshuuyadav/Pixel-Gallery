package com.prantiux.pixelgallery.viewmodel

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prantiux.pixelgallery.data.AlbumRepository
import com.prantiux.pixelgallery.data.AppDatabase
import com.prantiux.pixelgallery.data.FavoriteEntity
import com.prantiux.pixelgallery.data.MediaRepository
import com.prantiux.pixelgallery.data.RecentSearchesDataStore
import com.prantiux.pixelgallery.model.Album
import com.prantiux.pixelgallery.model.CategorizedAlbums
import com.prantiux.pixelgallery.model.MediaItem
import com.prantiux.pixelgallery.search.SearchEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SortMode {
    DATE_DESC, DATE_ASC, NAME_ASC, NAME_DESC, SIZE_DESC, SIZE_ASC
}

class MediaViewModel : ViewModel() {
    private lateinit var repository: MediaRepository
    private lateinit var albumRepository: AlbumRepository
    private lateinit var recentSearchesDataStore: RecentSearchesDataStore
    private lateinit var database: AppDatabase

    private val _images = MutableStateFlow<List<MediaItem>>(emptyList())
    val images: StateFlow<List<MediaItem>> = _images.asStateFlow()

    private val _videos = MutableStateFlow<List<MediaItem>>(emptyList())
    val videos: StateFlow<List<MediaItem>> = _videos.asStateFlow()
    
    private val _favoriteIds = MutableStateFlow<Set<Long>>(emptySet())
    val favoriteIds: StateFlow<Set<Long>> = _favoriteIds.asStateFlow()
    
    private val _favoriteItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val favoriteItems: StateFlow<List<MediaItem>> = _favoriteItems.asStateFlow()

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _categorizedAlbums = MutableStateFlow<CategorizedAlbums?>(null)
    val categorizedAlbums: StateFlow<CategorizedAlbums?> = _categorizedAlbums.asStateFlow()

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
        val thumbnailBounds: ThumbnailBounds? = null,
        val searchQuery: String? = null
    )

    data class ThumbnailBounds(
        val startLeft: Float,
        val startTop: Float,
        val startWidth: Float,
        val startHeight: Float
    )

    private val _overlayState = MutableStateFlow(MediaOverlayState())
    val overlayState: StateFlow<MediaOverlayState> = _overlayState.asStateFlow()

    private var allImages = listOf<MediaItem>()
    private var allVideos = listOf<MediaItem>()

    fun initialize(context: Context) {
        repository = MediaRepository(context)
        albumRepository = AlbumRepository(context)
        recentSearchesDataStore = RecentSearchesDataStore(context)
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
    }

    fun refresh(context: Context) {
        if (!::repository.isInitialized) {
            initialize(context)
        }
        
        viewModelScope.launch {
            // Material 3 Expressive: MediaStore queries take 200ms-2s (SHORT operation)
            // Show indeterminate loading indicator for proper user feedback
            _isLoading.value = true
            try {
                // Load all media
                allImages = repository.loadImages()
                allVideos = repository.loadVideos()
                val loadedAlbums = repository.loadAlbums()
                
                // Load categorized albums
                val categorized = albumRepository.loadCategorizedAlbums()

                // Apply current sort
                applySorting()
                _albums.value = loadedAlbums
                _categorizedAlbums.value = categorized
                
                // Cache combined media for search (ONCE)
                _allMediaCached.value = (allImages + allVideos).sortedByDescending { it.dateAdded }
            } catch (e: Exception) {
                e.printStackTrace()
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
        
        _images.value = when (_sortMode.value) {
            SortMode.DATE_DESC -> allImages.sortedByDescending { it.dateAdded }
            SortMode.DATE_ASC -> allImages.sortedBy { it.dateAdded }
            SortMode.NAME_ASC -> allImages.sortedBy { it.displayName.lowercase() }
            SortMode.NAME_DESC -> allImages.sortedByDescending { it.displayName.lowercase() }
            SortMode.SIZE_DESC -> allImages.sortedByDescending { it.size }
            SortMode.SIZE_ASC -> allImages.sortedBy { it.size }
        }.map { it.copy(isFavorite = favoriteIdSet.contains(it.id)) }

        _videos.value = when (_sortMode.value) {
            SortMode.DATE_DESC -> allVideos.sortedByDescending { it.dateAdded }
            SortMode.DATE_ASC -> allVideos.sortedBy { it.dateAdded }
            SortMode.NAME_ASC -> allVideos.sortedBy { it.displayName.lowercase() }
            SortMode.NAME_DESC -> allVideos.sortedByDescending { it.displayName.lowercase() }
            SortMode.SIZE_DESC -> allVideos.sortedByDescending { it.size }
            SortMode.SIZE_ASC -> allVideos.sortedBy { it.size }
        }.map { it.copy(isFavorite = favoriteIdSet.contains(it.id)) }
        
        // Update favorite items list
        val allMedia = (allImages + allVideos).map { 
            it.copy(isFavorite = favoriteIdSet.contains(it.id)) 
        }
        _favoriteItems.value = allMedia.filter { it.isFavorite }.sortedByDescending { it.dateAdded }
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
        thumbnailBounds: ThumbnailBounds?,
        searchQuery: String? = null
    ) {
        _overlayState.value = MediaOverlayState(
            isVisible = true,
            mediaType = mediaType,
            albumId = albumId,
            selectedIndex = selectedIndex,
            thumbnailBounds = thumbnailBounds,
            searchQuery = searchQuery
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
            exitSelectionMode()
            refresh(context)
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
                return@launch
            }
            
            // Perform search on cached data
            val results = SearchEngine.search(query, cachedMedia)
            _searchResults.value = results
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
            val success = repository.performRestore(context, pendingRestoreUris)
            if (success) {
                android.util.Log.d("MediaViewModel", "Successfully restored ${pendingRestoreUris.size} items")
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
            val success = repository.performPermanentDelete(context, pendingDeleteUris)
            if (success) {
                android.util.Log.d("MediaViewModel", "Successfully deleted ${pendingDeleteUris.size} items")
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
    fun showTrashMediaOverlay(selectedIndex: Int, thumbnailBounds: ThumbnailBounds? = null) {
        _overlayState.value = MediaOverlayState(
            isVisible = true,
            mediaType = "trash",
            albumId = "",
            selectedIndex = selectedIndex,
            thumbnailBounds = thumbnailBounds,
            searchQuery = null
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
}
