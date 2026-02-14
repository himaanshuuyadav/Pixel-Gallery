package com.prantiux.pixelgallery.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import coil.compose.AsyncImage
import com.prantiux.pixelgallery.model.MediaItem
import com.prantiux.pixelgallery.viewmodel.MediaViewModel
import com.prantiux.pixelgallery.ui.components.MediaThumbnail
import com.prantiux.pixelgallery.ui.utils.calculateFloatingNavBarHeight
import com.prantiux.pixelgallery.search.SearchEngine
import com.prantiux.pixelgallery.smartalbum.SmartAlbumGenerator
import com.prantiux.pixelgallery.model.Album
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons
import kotlinx.coroutines.launch

data class AlbumInfo(val name: String, val count: Int, val thumbnailUri: android.net.Uri)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: MediaViewModel, 
    navController: androidx.navigation.NavController,
    settingsDataStore: com.prantiux.pixelgallery.data.SettingsDataStore
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val images by viewModel.images.collectAsState()
    val videos by viewModel.videos.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val badgeType by settingsDataStore.badgeTypeFlow.collectAsState(initial = "Duration with icon")
    val badgeEnabled by settingsDataStore.showBadgeFlow.collectAsState(initial = true)
    val thumbnailQuality by settingsDataStore.thumbnailQualityFlow.collectAsState(initial = "Standard")
    val cornerType by settingsDataStore.cornerTypeFlow.collectAsState(initial = "Rounded")
    
    // SearchBar active state
    var isSearchBarActive by remember { mutableStateOf(false) }
    
    // Search filter state
    var selectedFilter by remember { mutableStateOf("All") }
    val filterOptions = listOf("All", "Albums", "Images and Videos", "Images", "Videos")
    
    // Get real recent searches from DataStore
    val recentSearches by viewModel.recentSearches.collectAsState()
    
    // Smart albums state
    var smartAlbums by remember { mutableStateOf<List<Album>>(emptyList()) }
    val smartAlbumThumbnailCache = viewModel.smartAlbumThumbnailCache
    
    // Load smart albums on launch
    LaunchedEffect(images, videos) {
        if (images.isNotEmpty() || videos.isNotEmpty()) {
            coroutineScope.launch {
                val albums = SmartAlbumGenerator.generateSmartAlbums(context)
                smartAlbums = albums
            }
        }
    }
    
    // Material 3 Expressive: Adaptive loading with delay threshold
    // Show loader only if search takes longer than 100ms (prevents flicker on fast searches)
    var showLoadingIndicator by remember { mutableStateOf(false) }
    
    // Animated values for search bar shape
    val bottomCornerRadius by animateDpAsState(
        targetValue = if (isSearchBarActive) 8.dp else 24.dp,
        animationSpec = tween(300),
        label = "SearchBarBottomCornerRadius"
    )
    
    LaunchedEffect(isSearching) {
        if (isSearching) {
            // Show loader immediately for better feedback
            showLoadingIndicator = true
        } else {
            // Hide immediately when results arrive
            showLoadingIndicator = false
        }
    }

    // REFACTORED: Removed viewModel.refresh() call
    // Data is already loaded by PhotosScreen on app startup
    // SearchScreen now only observes StateFlows (images, videos)
    
    // Handle back gesture
    BackHandler(enabled = isSearchBarActive || searchQuery.isNotEmpty()) {
        if (searchQuery.isNotEmpty()) {
            viewModel.clearSearch()
            isSearchBarActive = false
            focusManager.clearFocus()
            keyboardController?.hide()
        } else if (isSearchBarActive) {
            isSearchBarActive = false
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    // Combine and sort by date - use remember only, derivedStateOf is for internal recomposition optimization
    val allMedia: List<MediaItem> = remember(images, videos) { 
        (images + videos).sortedByDescending { it.dateAdded }
    }
    
    // Get unique albums - cached calculation
    val albums: List<AlbumInfo> = remember(allMedia) {
        allMedia.groupBy { it.bucketName }
            .filter { it.key.isNotEmpty() }
            .map { (name, items) -> AlbumInfo(name, items.size, items.firstOrNull()?.uri ?: android.net.Uri.EMPTY) }
            .sortedByDescending { it.count }
    }
    
    // Quick filters from SearchEngine
    val quickFilters = remember { SearchEngine.getQuickFilters() }

    val navBarHeight = calculateFloatingNavBarHeight()
    val showSearchCards = isSearchBarActive && searchQuery.isBlank()
    val showFilterCard = searchQuery.isNotBlank()
    
    val headerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Gradient background layer - covers search bar area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            headerColor,
                            headerColor.copy(alpha = 0.3f),
                            headerColor.copy(alpha = 0.1f),
                            headerColor.copy(alpha = 0.05f)
                        )
                    )
                )
                .pointerInput(Unit) {
                    detectTapGestures { /* Consume tap to prevent color changes */ }
                }
        )
        
        Column(modifier = Modifier.fillMaxSize()) {
        // Search Bar Header with Animated Cards
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Material 3 DockedSearchBar with animated shape
            DockedSearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = searchQuery,
                        onQueryChange = { viewModel.searchMedia(it) },
                        onSearch = { query ->
                            if (query.isNotBlank()) {
                                viewModel.searchMedia(query)
                                viewModel.addRecentSearch(query.trim())
                                isSearchBarActive = false
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                        },
                        expanded = isSearchBarActive,
                        onExpandedChange = { isSearchBarActive = it },
                        placeholder = { Text("Search your photos") },
                        leadingIcon = {
                            if (isSearchBarActive) {
                                IconButton(onClick = {
                                    // Clear search and close search bar
                                    if (searchQuery.isNotEmpty()) {
                                        viewModel.clearSearch()
                                    }
                                    isSearchBarActive = false
                                }) {
                                    FontIcon(
                                        unicode = FontIcons.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                            } else {
                                FontIcon(
                                    unicode = FontIcons.Search,
                                    contentDescription = "Search"
                                )
                            }
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.clearSearch() }) {
                                    FontIcon(unicode = FontIcons.Clear, contentDescription = "Clear")
                                }
                            }
                        }
                    )
                },
                expanded = isSearchBarActive,
                onExpandedChange = { isSearchBarActive = it },
                shape = RoundedCornerShape(
                    topStart = 24.dp,
                    topEnd = 24.dp,
                    bottomStart = bottomCornerRadius,
                    bottomEnd = bottomCornerRadius
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 56.dp)
            ) {}
            
            AnimatedVisibility(
                visible = showSearchCards && recentSearches.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 2.dp),
                        shape = RoundedCornerShape(
                            topStart = 8.dp,
                            topEnd = 8.dp,
                            bottomStart = 8.dp,
                            bottomEnd = 8.dp
                        ),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp)
                        ) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Recent searches",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    TextButton(
                                        onClick = { viewModel.clearRecentSearches() },
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text(
                                            text = "Clear all",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                            item {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(recentSearches.take(10).size) { index ->
                                        val search = recentSearches.take(10)[index]
                                        RecentSearchPill(
                                            text = search,
                                            onClick = {
                                                viewModel.searchMedia(search)
                                                viewModel.addRecentSearch(search)
                                                isSearchBarActive = false
                                                focusManager.clearFocus()
                                                keyboardController?.hide()
                                            },
                                            onDelete = {
                                                viewModel.removeRecentSearch(search)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = showSearchCards,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(2.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(
                            topStart = 8.dp,
                            topEnd = 8.dp,
                            bottomStart = 24.dp,
                            bottomEnd = 24.dp
                        ),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp)
                        ) {
                            item {
                                Text(
                                    text = "Suggestions",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            item {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(quickFilters) { filter ->
                                        // Simple pill shape like recent searches - text only, no icon, no delete
                                        Surface(
                                            modifier = Modifier
                                                .wrapContentWidth()
                                                .clip(RoundedCornerShape(20.dp))
                                                .clickable {
                                                    viewModel.searchMedia(filter.label.lowercase())
                                                    viewModel.addRecentSearch(filter.label)
                                                    isSearchBarActive = false
                                                    focusManager.clearFocus()
                                                    keyboardController?.hide()
                                                },
                                            shape = RoundedCornerShape(20.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                            tonalElevation = 1.dp
                                        ) {
                                            Text(
                                                text = filter.label,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }            
            // Filter Card - Animated In when typing
            AnimatedVisibility(
                visible = showFilterCard,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(
                            topStart = 8.dp,
                            topEnd = 8.dp,
                            bottomStart = 24.dp,
                            bottomEnd = 24.dp
                        ),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp
                    ) {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            filterOptions.forEach { filter ->
                                val isSelected = selectedFilter == filter
                                Surface(
                                    modifier = Modifier
                                        .wrapContentWidth()
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable { selectedFilter = filter },
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isSelected) 
                                        MaterialTheme.colorScheme.primaryContainer 
                                    else 
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    tonalElevation = if (isSelected) 2.dp else 1.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isSelected) {
                                            FontIcon(
                                                unicode = "\ue86c",
                                                contentDescription = null,
                                                size = 16.sp,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                        Text(
                                            text = filter,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (isSelected) 
                                                MaterialTheme.colorScheme.onPrimaryContainer 
                                            else 
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Main content area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                searchQuery.isBlank() && !isSearchBarActive -> {
                    // Empty state with quick filters and date shortcuts only
                    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
                    
                    // Track scrolling to save search
                    LaunchedEffect(lazyListState.isScrollInProgress) {
                        if (lazyListState.isScrollInProgress && searchQuery.isNotBlank()) {
                            viewModel.addRecentSearch(searchQuery.trim())
                        }
                    }
                    
                    val smartAlbumRows = smartAlbums.chunked(2)
                    val isLoadingSmartAlbums = images.isNotEmpty() || videos.isNotEmpty() && smartAlbums.isEmpty()

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 16.dp, bottom = navBarHeight + 16.dp)
                    ) {
                        // Smart Albums section (2-column grid) - Always show heading
                        item {
                            SectionLabel("Suggested")
                        }
                        
                        // Show loading state or actual albums
                        if (isLoadingSmartAlbums) {
                            // Loading placeholders - 2 rows of 2 cards
                            items(2) { rowIndex ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    repeat(2) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(20.dp))
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(32.dp),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                                if (rowIndex == 0) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }
                        } else if (smartAlbums.isNotEmpty()) {
                            items(smartAlbumRows.size) { rowIndex ->
                                val row = smartAlbumRows[rowIndex]
                                Column {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        row.forEach { album ->
                                            Box(modifier = Modifier.weight(1f)) {
                                                SmartAlbumGridCard(
                                                    album = album,
                                                    allMediaItems = allMedia,
                                                    cachedThumbnailUri = smartAlbumThumbnailCache[album.id],
                                                    onThumbnailCached = { uri ->
                                                        smartAlbumThumbnailCache[album.id] = uri
                                                    },
                                                    onClick = {
                                                        navController.navigate(
                                                            com.prantiux.pixelgallery.navigation.Screen.SmartAlbumView.createRoute(album.id)
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                        if (row.size == 1) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                    if (rowIndex != smartAlbumRows.lastIndex) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                }
                            }
                        }
                        
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        
                    }
                }
                // Material 3 Expressive: Adaptive loading with 100ms delay
                // Shows LoadingIndicator only if search takes longer than 100ms
                showLoadingIndicator && searchQuery.isNotBlank() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            com.prantiux.pixelgallery.ui.components.ExpressiveLoadingIndicator(
                                size = 56.dp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Searching...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                searchQuery.isNotBlank() && !showLoadingIndicator && searchResults.matchedAlbums.isEmpty() && searchResults.matchedMedia.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            FontIcon(
                                unicode = FontIcons.SearchOff,
                                contentDescription = null,
                                size = 64.sp,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "No results found",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Try a different search term",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                else -> {
                    // Show search results with filter applied
                    val gridState = rememberLazyGridState()
                    val coroutineScope = rememberCoroutineScope()
                    val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
                    
                    // Apply filter logic
                    val showAlbums = when (selectedFilter) {
                        "All", "Albums" -> true
                        else -> false
                    }
                    
                    val filteredMedia = when (selectedFilter) {
                        "All", "Images and Videos" -> searchResults.matchedMedia
                        "Images" -> searchResults.matchedMedia.filter { !it.isVideo }
                        "Videos" -> searchResults.matchedMedia.filter { it.isVideo }
                        else -> emptyList()
                    }
                    
                    val categoryHeading = when (selectedFilter) {
                        "Images" -> "Images (${filteredMedia.size})"
                        "Videos" -> "Videos (${filteredMedia.size})"
                        else -> "Photos & Videos (${filteredMedia.size})"
                    }
                    
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 2.dp, end = 2.dp, top = 16.dp, bottom = navBarHeight + 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                        // Matched albums section
                        if (showAlbums && searchResults.matchedAlbums.isNotEmpty()) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                                Text(
                                    text = "Albums (${searchResults.matchedAlbums.size})",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                )
                            }
                            
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp)
                                ) {
                                    items(searchResults.matchedAlbums.size) { index ->
                                        val albumMatch = searchResults.matchedAlbums[index]
                                        AlbumPillItem(
                                            name = albumMatch.albumName,
                                            thumbnailUri = albumMatch.items.firstOrNull()?.uri ?: android.net.Uri.EMPTY,
                                            onClick = {
                                                // Navigate to album screen using bucketId from first item
                                                val bucketId = albumMatch.items.firstOrNull()?.bucketId
                                                if (bucketId != null) {
                                                    // Save search to recent searches first
                                                    viewModel.addRecentSearch(searchQuery)
                                                    navController.navigate(com.prantiux.pixelgallery.navigation.Screen.AlbumDetail.createRoute(bucketId))
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                            
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) { 
                                Spacer(modifier = Modifier.height(16.dp)) 
                            }
                        }
                        
                        // Matched media section
                        if (filteredMedia.isNotEmpty()) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                                Text(
                                    text = categoryHeading,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                )
                            }
                            
                            // Grid of media items
                            items(filteredMedia.size) { index ->
                                val item = filteredMedia[index]
                                MediaThumbnail(
                                    item = item,
                                    isSelected = false,
                                    isSelectionMode = false,
                                    shape = com.prantiux.pixelgallery.ui.utils.getGridItemCornerShape(
                                        index = index,
                                        totalItems = filteredMedia.size,
                                        columns = 3,
                                        cornerType = cornerType
                                    ),
                                    badgeType = badgeType,
                                    badgeEnabled = badgeEnabled,
                                    thumbnailQuality = thumbnailQuality,
                                    onClick = { bounds ->
                                        // Save search to recent searches
                                        viewModel.addRecentSearch(searchQuery)
                                        
                                        val thumbnailBounds = bounds?.let {
                                            com.prantiux.pixelgallery.ui.animation.SharedElementBounds(
                                                left = it.left,
                                                top = it.top,
                                                width = it.width,
                                                height = it.height
                                            )
                                        }
                                        viewModel.showMediaOverlay(
                                            mediaType = "search",
                                            albumId = "search_results",
                                            selectedIndex = index,
                                            searchQuery = searchQuery,
                                            thumbnailBounds = thumbnailBounds
                                        )
                                    },
                                    onLongClick = {},
                                    showFavorite = true
                                )
                            }
                        }
                    }
                        
                        // Unified scrollbar with smooth scrolling mode
                        com.prantiux.pixelgallery.ui.components.UnifiedScrollbar(
                            modifier = Modifier.align(Alignment.TopEnd),
                            gridState = gridState,
                            mode = com.prantiux.pixelgallery.ui.components.ScrollbarMode.SMOOTH_SCROLLING,
                            topPadding = 88.dp + 2.dp,
                            totalItems = (if (showAlbums) searchResults.matchedAlbums.size else 0) + filteredMedia.size + 3,
                            coroutineScope = coroutineScope,
                            isDarkTheme = isDarkTheme
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
fun RecentSearchPill(text: String, onClick: () -> Unit, onDelete: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.height(40.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Surface(
                onClick = onDelete,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                modifier = Modifier.size(28.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    FontIcon(
                        unicode = FontIcons.Close,
                        contentDescription = "Remove",
                        size = 16.sp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun SearchSuggestionItem(text: String, iconUnicode: String, onClick: () -> Unit, onDelete: (() -> Unit)? = null) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FontIcon(
                unicode = iconUnicode,
                contentDescription = null,
                size = 20.sp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            // Delete button for recent searches
            if (onDelete != null) {
                IconButton(
                    onClick = { onDelete() },
                    modifier = Modifier.size(36.dp)
                ) {
                    FontIcon(
                        unicode = FontIcons.Close,
                        contentDescription = "Remove",
                        size = 18.sp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickFilterChip(label: String, iconUnicode: String, onClick: () -> Unit) {
    FilterChip(
        selected = false,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            FontIcon(
                unicode = iconUnicode,
                contentDescription = null,
                size = 18.sp
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
fun DateShortcutItem(label: String, iconUnicode: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    FontIcon(
                        unicode = iconUnicode,
                        contentDescription = null,
                        size = 24.sp,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun AlbumQuickAccessItem(name: String, count: Int, thumbnailUri: android.net.Uri, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(56.dp)
            ) {
                AsyncImage(
                    model = thumbnailUri,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$count ${if (count == 1) "item" else "items"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AlbumPillItem(name: String, thumbnailUri: android.net.Uri, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(120.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        // Square album cover image
        AsyncImage(
            model = thumbnailUri,
            contentDescription = "$name album cover",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        // Overlaid pill label at bottom center
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp, start = 8.dp, end = 8.dp)
                .background(
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(50)
                )
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun AlbumResultItem(name: String, count: Int, thumbnailUri: android.net.Uri, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(64.dp)
            ) {
                AsyncImage(
                    model = thumbnailUri,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$count ${if (count == 1) "item" else "items"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FontIcon(
                unicode = FontIcons.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Smart Album grid card - rounded square with thumbnail background and text overlay
 */
@Composable
fun SmartAlbumGridCard(
    album: Album, 
    allMediaItems: List<MediaItem>,
    cachedThumbnailUri: android.net.Uri?,
    onThumbnailCached: (android.net.Uri?) -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val thumbnailUri = cachedThumbnailUri
    
    LaunchedEffect(album.id, allMediaItems, cachedThumbnailUri) {
        if (cachedThumbnailUri == null) {
            val media = SmartAlbumGenerator.getMediaForSmartAlbum(context, album.id, allMediaItems)
            onThumbnailCached(media.firstOrNull()?.uri)
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.BottomStart
    ) {
        // Thumbnail background
        if (thumbnailUri != null) {
            AsyncImage(
                model = thumbnailUri,
                contentDescription = album.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Gradient overlay for text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.5f)
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
            )
        } else {
            // Placeholder
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        
        // Text overlay at bottom
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = album.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            
            // Item count badge overlay
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
            ) {
                Text(
                    text = "${album.itemCount} ${if (album.itemCount == 1) "item" else "items"}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

