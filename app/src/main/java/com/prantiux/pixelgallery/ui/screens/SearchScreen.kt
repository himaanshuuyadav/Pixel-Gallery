package com.prantiux.pixelgallery.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.prantiux.pixelgallery.model.MediaItem
import com.prantiux.pixelgallery.viewmodel.MediaViewModel
import com.prantiux.pixelgallery.ui.components.ConsistentHeader
import com.prantiux.pixelgallery.ui.components.MediaThumbnail
import com.prantiux.pixelgallery.ui.utils.calculateFloatingNavBarHeight
import com.prantiux.pixelgallery.search.SearchEngine
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons

data class AlbumInfo(val name: String, val count: Int, val thumbnailUri: android.net.Uri)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(viewModel: MediaViewModel, navController: androidx.navigation.NavController) {
    val context = LocalContext.current
    val images by viewModel.images.collectAsState()
    val videos by viewModel.videos.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    
    // Real recent searches from DataStore
    val recentSearches by viewModel.recentSearches.collectAsState()
    
    // ML Labeling progress state
    val isLabelingInProgress by viewModel.isLabelingInProgress.collectAsState()
    val labelingProgress by viewModel.labelingProgress.collectAsState()
    val isCharging = remember { 
        mutableStateOf(com.prantiux.pixelgallery.ml.ImageLabelScheduler.isCharging(context))
    }
    
    // Track next batch status
    var nextBatchStatus by remember { mutableStateOf("Checking...") }
    
    // Update charging state and calculate next batch timing
    LaunchedEffect(Unit) {
        while (true) {
            isCharging.value = com.prantiux.pixelgallery.ml.ImageLabelScheduler.isCharging(context)
            
            // Simple next batch calculation based on current state
            nextBatchStatus = when {
                isLabelingInProgress -> "Running now"
                labelingProgress?.let { (p, t) -> p < t } == true -> "Retry in ~10s"
                else -> "Complete"
            }
            
            kotlinx.coroutines.delay(5000) // Check every 5 seconds
        }
    }
    
    // Material 3 Expressive: Adaptive loading with delay threshold
    // Show loader only if search takes longer than 100ms (prevents flicker on fast searches)
    var showLoadingIndicator by remember { mutableStateOf(false) }
    
    LaunchedEffect(isSearching) {
        if (isSearching) {
            // Show loader immediately for better feedback
            showLoadingIndicator = true
        } else {
            // Hide immediately when results arrive
            showLoadingIndicator = false
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refresh(context)
    }
    
    // Handle back gesture when search query is active
    BackHandler(enabled = searchQuery.isNotEmpty()) {
        viewModel.clearSearch()
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
    val dateShortcuts = remember { SearchEngine.getDateShortcuts() }

    Column(modifier = Modifier.fillMaxSize()) {
        com.prantiux.pixelgallery.ui.components.MainTabHeader(title = "Search")
        
        val navBarHeight = calculateFloatingNavBarHeight()
        
        TextField(
            value = searchQuery,
            onValueChange = { viewModel.searchMedia(it) },
            placeholder = { Text("Try 'camera photos' or 'videos today'") },
            leadingIcon = { FontIcon(unicode = FontIcons.Search, contentDescription = "Search") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.clearSearch() }) {
                        FontIcon(unicode = FontIcons.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
        ) {
            when {
                searchQuery.isBlank() -> {
                    // Empty state with suggestions
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 16.dp, bottom = navBarHeight + 16.dp)
                    ) {
                        // ML LABELING DEBUG PANEL - Completely Redesigned
                        item {
                            val progress = labelingProgress?.let { (processed, total) ->
                                if (total > 0) processed.toFloat() / total.toFloat() else 0f
                            } ?: 0f
                            
                            val isComplete = labelingProgress?.let { (processed, total) ->
                                processed == total && total > 0
                            } ?: false
                            
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                color = if (isComplete) 
                                    MaterialTheme.colorScheme.primaryContainer 
                                else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(16.dp),
                                shadowElevation = 4.dp
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp)
                                ) {
                                    // Header with icon and title
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (isComplete) "✅" else "🤖",
                                                style = MaterialTheme.typography.headlineSmall
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = "ML Image Labeling",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        // Status Badge
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = when {
                                                isComplete -> MaterialTheme.colorScheme.primary
                                                isLabelingInProgress -> MaterialTheme.colorScheme.tertiary
                                                else -> MaterialTheme.colorScheme.surfaceContainer
                                            }
                                        ) {
                                            Text(
                                                text = when {
                                                    isComplete -> "DONE"
                                                    isLabelingInProgress -> "RUNNING"
                                                    else -> "IDLE"
                                                },
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = when {
                                                    isComplete -> MaterialTheme.colorScheme.onPrimary
                                                    isLabelingInProgress -> MaterialTheme.colorScheme.onTertiary
                                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    // Large Progress Display
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Bottom,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column {
                                            Text(
                                                text = "Images Labeled",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = labelingProgress?.let { (processed, total) ->
                                                    "$processed / $total"
                                                } ?: "0 / 0",
                                                style = MaterialTheme.typography.headlineMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        // Large Percentage
                                        Text(
                                            text = "${(progress * 100).toInt()}%",
                                            style = MaterialTheme.typography.displaySmall,
                                            fontWeight = FontWeight.Black,
                                            color = if (isComplete) 
                                                MaterialTheme.colorScheme.primary 
                                            else MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    // Progress Bar with better visibility
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = if (isComplete) 
                                            MaterialTheme.colorScheme.primary 
                                        else MaterialTheme.colorScheme.tertiary,
                                        trackColor = MaterialTheme.colorScheme.surfaceContainer
                                    )
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    HorizontalDivider()
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    // Detailed Status Grid
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        // Charging Status
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "⚡",
                                                    style = MaterialTheme.typography.titleMedium
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Device Charging",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Text(
                                                text = if (isCharging.value) "Yes" else "No",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isCharging.value) 
                                                    MaterialTheme.colorScheme.tertiary
                                                else MaterialTheme.colorScheme.error
                                            )
                                        }
                                        
                                        // ML Engine Status
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "🧠",
                                                    style = MaterialTheme.typography.titleMedium
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "ML Engine",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Text(
                                                text = if (isLabelingInProgress) "Active" else "Standby",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isLabelingInProgress) 
                                                    MaterialTheme.colorScheme.tertiary
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        
                                        // Completion Status
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = if (isComplete) "✅" else "📊",
                                                    style = MaterialTheme.typography.titleMedium
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Status",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Text(
                                                text = when {
                                                    isComplete -> "Labeling Complete!"
                                                    isLabelingInProgress -> "Processing..."
                                                    labelingProgress?.first ?: 0 > 0 -> "In Progress"
                                                    else -> "Not Started"
                                                },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = when {
                                                    isComplete -> MaterialTheme.colorScheme.primary
                                                    isLabelingInProgress -> MaterialTheme.colorScheme.tertiary
                                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                        
                                        // Next Batch Schedule (only show if not complete)
                                        if (!isComplete) {
                                            Row(
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = "⏰",
                                                        style = MaterialTheme.typography.titleMedium
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "Next Batch",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                                Text(
                                                    text = nextBatchStatus,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isLabelingInProgress) 
                                                        MaterialTheme.colorScheme.tertiary
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                    
                                    // Info message
                                    if (!isComplete) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f)
                                        ) {
                                            Text(
                                                text = "💡 ML runs in background without affecting app performance",
                                                modifier = Modifier.padding(12.dp),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Recent Searches
                        if (recentSearches.isNotEmpty()) {
                            item {
                                SectionLabel("Recent searches")
                            }
                            items(recentSearches.take(5)) { search ->
                                SearchSuggestionItem(
                                    text = search,
                                    iconUnicode = FontIcons.History,
                                    onClick = { 
                                        viewModel.searchMedia(search)
                                        viewModel.addRecentSearch(search)
                                    },
                                    onDelete = {
                                        viewModel.removeRecentSearch(search)
                                    }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }
                        
                        // Quick filters
                        item {
                            SectionLabel("Quick filters")
                        }
                        item {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp)
                            ) {
                                items(quickFilters) { filter ->
                                    val iconUnicode = when (filter.label) {
                                        "Photos" -> FontIcons.Image
                                        "Videos" -> FontIcons.VideoLibrary
                                        "Screenshots" -> FontIcons.Screenshot
                                        "Camera" -> FontIcons.CameraAlt
                                        "Large Files" -> FontIcons.Storage
                                        else -> FontIcons.Image
                                    }
                                    QuickFilterChip(
                                        label = filter.label,
                                        iconUnicode = iconUnicode,
                                        onClick = { viewModel.searchMedia(filter.label.lowercase()) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                        
                        // Date shortcuts
                        item {
                            SectionLabel("Quick access")
                        }
                        item {
                            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                dateShortcuts.forEach { shortcut ->
                                    val iconUnicode = when (shortcut.label) {
                                        "Today" -> FontIcons.Today
                                        "Yesterday" -> FontIcons.CalendarToday
                                        "This Week" -> FontIcons.DateRange
                                        "This Month" -> FontIcons.CalendarMonth
                                        else -> FontIcons.CalendarToday
                                    }
                                    DateShortcutItem(shortcut.label, iconUnicode) {
                                        viewModel.searchMedia(shortcut.label.lowercase())
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
                // Material 3 Expressive: Adaptive loading with 100ms delay
                // Shows LoadingIndicator only if search takes longer than 100ms
                showLoadingIndicator && searchQuery.isNotBlank() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
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
                searchQuery.isNotBlank() && !showLoadingIndicator && searchResults.matchedAlbums.isEmpty() && searchResults.matchedMedia.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
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
                else -> {
                    // Show search results with priority: Albums first, then media
                    val gridState = rememberLazyGridState()
                    val coroutineScope = rememberCoroutineScope()
                    val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
                    
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
                        if (searchResults.matchedAlbums.isNotEmpty()) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                                Text(
                                    text = "Albums (${searchResults.matchedAlbums.size})",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                )
                            }
                            
                            items(searchResults.matchedAlbums.size, span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) { index ->
                                val albumMatch = searchResults.matchedAlbums[index]
                                AlbumResultItem(
                                    name = albumMatch.albumName,
                                    count = albumMatch.items.size,
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
                            
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }
                        
                        // Matched media section
                        if (searchResults.matchedMedia.isNotEmpty()) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                                Text(
                                    text = "Photos & Videos (${searchResults.matchedMedia.size})",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                )
                            }
                            
                            // Grid of media items
                            val matchedMedia = searchResults.matchedMedia
                            
                            items(matchedMedia.size) { index ->
                                val item = matchedMedia[index]
                                MediaThumbnail(
                                    item = item,
                                    isSelected = false,
                                    isSelectionMode = false,
                                    shape = com.prantiux.pixelgallery.ui.utils.getGridItemCornerShape(
                                        index = index,
                                        totalItems = matchedMedia.size,
                                        columns = 3
                                    ),
                                    onClick = { bounds ->
                                        // Save search to recent searches
                                        viewModel.addRecentSearch(searchQuery)
                                        
                                        viewModel.showMediaOverlay(
                                            mediaType = "search",
                                            albumId = "search_results",
                                            selectedIndex = index,
                                            thumbnailBounds = bounds?.let {
                                                MediaViewModel.ThumbnailBounds(
                                                    startLeft = it.left,
                                                    startTop = it.top,
                                                    startWidth = it.width,
                                                    startHeight = it.height
                                                )
                                            },
                                            searchQuery = searchQuery
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
                            totalItems = searchResults.matchedAlbums.size + searchResults.matchedMedia.size + 3,
                            coroutineScope = coroutineScope,
                            isDarkTheme = isDarkTheme
                        )
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

