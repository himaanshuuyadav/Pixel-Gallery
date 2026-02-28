@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)

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
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
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
import kotlin.math.absoluteValue

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
    // ROOM-FIRST: Use Room flows for base media lists
    val images by viewModel.imagesFlow.collectAsState()
    val videos by viewModel.videosFlow.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    // ROOM-FIRST: Use Room-based search flow instead of in-memory search
    val searchResultsRaw by viewModel.searchMediaFlow(searchQuery).collectAsState(initial = emptyList())
    val isSearching by viewModel.isSearching.collectAsState()
    
    // CALLING TAB LOG
    android.util.Log.d("SCREEN_TAB", "SearchScreen collected ${searchResultsRaw.size} results for query='$searchQuery'")
    
    // ROOM-FIRST: Compute search result structure from raw media list
    val searchResults = remember(searchResultsRaw) {
        val matchedAlbums = searchResultsRaw
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
        
        SearchEngine.SearchResult(
            matchedAlbums = matchedAlbums,
            matchedMedia = searchResultsRaw,
            query = searchQuery
        )
    }
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
    val smartAlbumDominantColors = viewModel.smartAlbumDominantColors
    
    // Load smart albums on launch
    LaunchedEffect(images, videos) {
        if (images.isNotEmpty() || videos.isNotEmpty()) {
            coroutineScope.launch {
                val albums = SmartAlbumGenerator.generateSmartAlbums(context)
                smartAlbums = albums

                viewModel.preloadSmartAlbumColors(
                    context = context,
                    albums = albums,
                    allMediaItems = images + videos
                )
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
                        onQueryChange = { viewModel.setSearchQuery(it) },
                        onSearch = { query ->
                            if (query.isNotBlank()) {
                                viewModel.setSearchQuery(query)
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
                                                viewModel.setSearchQuery(search)
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
                                                    viewModel.setSearchQuery(filter.label.lowercase())
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
                    
                    val isLoadingSmartAlbums = (images.isNotEmpty() || videos.isNotEmpty()) && smartAlbums.isEmpty()

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 16.dp, bottom = navBarHeight + 16.dp)
                    ) {
                        // Smart Albums section - Horizontal Hero Layout
                        item {
                            SectionLabel("Suggested")
                        }
                        
                        // Show loading state or actual albums in centered carousel
                        item {
                            if (isLoadingSmartAlbums) {
                                // Loading placeholders - horizontal scroll
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp)
                                ) {
                                    items(4) {
                                        Box(
                                            modifier = Modifier
                                                .width(280.dp)
                                                .height(210.dp)
                                                .clip(RoundedCornerShape(28.dp))
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
                            } else if (smartAlbums.isNotEmpty()) {
                                // Smart Albums Hero Cards in centered carousel with HorizontalPager
                                val pagerState = rememberPagerState(
                                    pageCount = { smartAlbums.size }
                                )
                                val configuration = LocalConfiguration.current
                                val screenWidth = configuration.screenWidthDp.dp
                                val cardWidth = screenWidth * 0.75f
                                
                                HorizontalPager(
                                    state = pagerState,
                                    contentPadding = PaddingValues(
                                        horizontal = (screenWidth - cardWidth) / 2
                                    ),
                                    pageSpacing = 16.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) { page ->
                                    val album = smartAlbums[page]
                                    val dominantColor = smartAlbumDominantColors[album.id] 
                                        ?: MaterialTheme.colorScheme.primaryContainer
                                    
                                    val pageOffset = (
                                        (pagerState.currentPage - page) +
                                        pagerState.currentPageOffsetFraction
                                    ).absoluteValue
                                    
                                    val scale = lerp(
                                        start = 0.92f,
                                        stop = 1f,
                                        fraction = 1f - pageOffset.coerceIn(0f, 1f)
                                    )
                                    
                                    Box(
                                        modifier = Modifier
                                            .width(cardWidth)
                                            .graphicsLayer {
                                                scaleX = scale
                                                scaleY = scale
                                            }
                                    ) {
                                        SmartAlbumHeroCard(
                                            album = album,
                                            dominantColor = dominantColor,
                                            allMediaItems = allMedia,
                                            cachedThumbnailUri = smartAlbumThumbnailCache[album.id],
                                            onThumbnailCached = { uri ->
                                                smartAlbumThumbnailCache[album.id] = uri
                                            },
                                            onClick = {
                                                navController.navigate(
                                                    com.prantiux.pixelgallery.navigation.Screen.SmartAlbumView.createRoute(album.id)
                                                )
                                            },
                                            albumIndex = page
                                        )
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
                        com.prantiux.pixelgallery.ui.components.EchoLoadingIndicator(
                            modifier = Modifier.padding(32.dp)
                        )
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
                    val density = LocalDensity.current
                    var contentTopPx by remember { mutableStateOf<Float?>(null) }
                    var albumsHeaderTopPx by remember { mutableStateOf<Float?>(null) }
                    var mediaHeaderTopPx by remember { mutableStateOf<Float?>(null) }
                    
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
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { coords ->
                                if (contentTopPx == null) {
                                    contentTopPx = coords.positionInRoot().y
                                }
                            }
                    ) {
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
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                        .then(
                                            if (albumsHeaderTopPx == null) {
                                                Modifier.onGloballyPositioned { coords ->
                                                    albumsHeaderTopPx = coords.positionInRoot().y
                                                }
                                            } else {
                                                Modifier
                                            }
                                        )
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
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                        .then(
                                            if (mediaHeaderTopPx == null) {
                                                Modifier.onGloballyPositioned { coords ->
                                                    mediaHeaderTopPx = coords.positionInRoot().y
                                                }
                                            } else {
                                                Modifier
                                            }
                                        )
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
                            topPadding = if (contentTopPx != null && (albumsHeaderTopPx != null || mediaHeaderTopPx != null)) {
                                val headerTopPx = if (showAlbums && searchResults.matchedAlbums.isNotEmpty() && albumsHeaderTopPx != null) {
                                    albumsHeaderTopPx!!
                                } else {
                                    mediaHeaderTopPx ?: 0f
                                }
                                with(density) { (headerTopPx - contentTopPx!!).coerceAtLeast(0f).toDp() }
                            } else {
                                0.dp
                            },
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
 * Smart Album Vertical Card - Editorial-style vertical layout with pastel backgrounds and centered elements
 */
@Composable
fun SmartAlbumHeroCard(
    album: Album,
    dominantColor: Color,
    allMediaItems: List<MediaItem>,
    cachedThumbnailUri: android.net.Uri?,
    onThumbnailCached: (android.net.Uri?) -> Unit,
    onClick: () -> Unit,
    albumIndex: Int = 0
) {
    val context = LocalContext.current
    val thumbnailUri = cachedThumbnailUri
    
    // Load thumbnail if not cached
    LaunchedEffect(album.id, allMediaItems, cachedThumbnailUri) {
        if (cachedThumbnailUri == null) {
            val media = SmartAlbumGenerator.getMediaForSmartAlbum(context, album.id, allMediaItems)
            onThumbnailCached(media.firstOrNull()?.uri)
        }
    }
    
    // Select shape based on album index
    val thumbnailShape = when (albumIndex % 4) {
        0 -> MaterialShapes.Square.toShape()
        1 -> MaterialShapes.Clover8Leaf.toShape()
        2 -> MaterialShapes.Arch.toShape()
        else -> MaterialShapes.Cookie4Sided.toShape()
    }
    
    // Background color with slight transparency
    val backgroundColor = remember(dominantColor) {
        dominantColor.copy(alpha = 0.92f)
    }
    
    // Determine text color based on background luminance (perceived brightness)
    val textColor = remember(backgroundColor) {
        val perceivedBrightness = computePerceivedBrightness(backgroundColor)
        if (perceivedBrightness > 127) Color.Black.copy(alpha = 0.87f) else Color.White
    }
    
    // Pill background with adaptive transparency
    val pillBackgroundColor = remember(textColor) {
        val brightness = computePerceivedBrightness(textColor)
        if (brightness > 127) {
            Color.Black.copy(alpha = 0.12f)
        } else {
            Color.White.copy(alpha = 0.25f)
        }
    }
    
    Surface(
        modifier = Modifier
            .width(240.dp)
            .height(340.dp),
        shape = RoundedCornerShape(28.dp),
        color = backgroundColor,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center
        ) {
            // Thumbnail with Material 3 Expressive shape masking
            if (thumbnailUri != null) {
                AsyncImage(
                    model = thumbnailUri,
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(160.dp)
                        .clip(thumbnailShape)
                        .align(Alignment.CenterHorizontally)
                )
            } else {
                // Loading placeholder
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(thumbnailShape)
                        .background(textColor.copy(alpha = 0.1f))
                        .align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = textColor.copy(alpha = 0.5f),
                        strokeWidth = 2.dp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Title left-aligned
            Text(
                text = album.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Start
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Count pill left-aligned
            Surface(
                shape = RoundedCornerShape(50),
                color = pillBackgroundColor
            ) {
                Text(
                    text = "${album.itemCount} ${if (album.itemCount == 1) "item" else "items"}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/**
 * Compute perceived brightness of a color using standard luminance formula
 * Range: 0-255 where >127 is considered "light"
 */
private fun computePerceivedBrightness(color: Color): Int {
    val r = color.red * 255
    val g = color.green * 255
    val b = color.blue * 255
    // WCAG relative luminance formula
    return (0.299 * r + 0.587 * g + 0.114 * b).toInt()
}

