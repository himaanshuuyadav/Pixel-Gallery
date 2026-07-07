@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)

package com.prantiux.pixelgallery.ui.screens

import com.prantiux.pixelgallery.ui.utils.rememberZenithFlingBehavior
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.toShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.foundation.gestures.animateScrollBy
import kotlinx.coroutines.launch
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
import com.prantiux.pixelgallery.ui.utils.shimmerEffect
import com.prantiux.pixelgallery.search.SearchEngine
import com.prantiux.pixelgallery.smartalbum.SmartAlbumGenerator
import com.prantiux.pixelgallery.model.Album
import com.prantiux.pixelgallery.ui.components.SelectionTopBar
import com.prantiux.pixelgallery.ui.components.UnifiedScrollbar
import com.prantiux.pixelgallery.ui.components.PremiumEmptyState
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    
    // Derived values for backward compatibility in the rest of the file
    val searchResults = remember(searchState) {
        when (searchState) {
            is com.prantiux.pixelgallery.viewmodel.SearchState.Success -> (searchState as com.prantiux.pixelgallery.viewmodel.SearchState.Success).results
            else -> SearchEngine.SearchResult(emptyList(), emptyList(), searchQuery)
        }
    }
    
    val showLoadingIndicator = searchState is com.prantiux.pixelgallery.viewmodel.SearchState.Loading
    val isSearchEmpty = searchState is com.prantiux.pixelgallery.viewmodel.SearchState.Empty
    val badgeType by settingsDataStore.badgeTypeFlow.collectAsState(initial = "Duration with icon")
    val badgeEnabled by settingsDataStore.showBadgeFlow.collectAsState(initial = true)
    val thumbnailQuality by settingsDataStore.thumbnailQualityFlow.collectAsState(initial = "Standard")
    val cornerType by settingsDataStore.cornerTypeFlow.collectAsState(initial = "Rounded")
    
    // SearchBar active state
    // Multi-select state
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedItems by remember { mutableStateOf(emptySet<String>()) }
    
    // SearchBar active state
    val isSearchBarActive by viewModel.isSearchBarActive.collectAsState()
    
    // Search filter state
    var selectedFilter by remember { mutableStateOf("All") }
    val filterOptions = listOf("All", "Albums", "Images and Videos", "Images", "Videos")
    var albumActionsSheet by remember { mutableStateOf<Album?>(null) }
    
    // Get real recent searches from DataStore
    val recentSearches by viewModel.recentSearches.collectAsState()
    
    // Smart albums state
    var smartAlbums by remember { mutableStateOf<List<Album>>(emptyList()) }
    val smartAlbumThumbnailCache = viewModel.smartAlbumThumbnailCache
    
    // Load smart albums when sync finishes or on launch
    LaunchedEffect(isLoading) {
        if (!isLoading) {
            coroutineScope.launch {
                val albums = SmartAlbumGenerator.generateSmartAlbums(context)
                smartAlbums = albums
            }
        }
    }
    
    // Animated values for search bar shape
    val bottomCornerRadius by animateDpAsState(
        targetValue = if (isSearchBarActive || searchQuery.isNotBlank()) 8.dp else 24.dp,
        animationSpec = tween(300),
        label = "SearchBarBottomCornerRadius"
    )
    
    val topMlLabels by viewModel.topMlLabels.collectAsState()
    
    val quickFilters = remember(topMlLabels) { 
        val dates = SearchEngine.getDateShortcuts().take(3).map { it.label }
        val types = SearchEngine.getQuickFilters().map { it.label }
        val ml = topMlLabels.map { label -> label.replaceFirstChar { it.uppercase() } }
        (dates + types + ml).distinct()
    }

    LaunchedEffect(Unit) {
        viewModel.loadTopMlLabels()
    }

    val navBarHeight = calculateFloatingNavBarHeight()
    val showSearchCards = isSearchBarActive && searchQuery.isBlank()
    val showFilterCard = searchQuery.isNotBlank()
    var isSmartAlbumsExpanded by remember { mutableStateOf(false) }
    
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
                        onSearch = {
                            viewModel.setSearchBarActive(false)
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        },
                        expanded = isSearchBarActive,
                        onExpandedChange = { viewModel.setSearchBarActive(it) },
                        placeholder = { Text("Search your photos") },
                        leadingIcon = {
                            if (isSearchBarActive) {
                                IconButton(onClick = {
                                    // Clear search and close search bar
                                    if (searchQuery.isNotEmpty()) {
                                        viewModel.clearSearchQuery()
                                    }
                                    viewModel.setSearchBarActive(false)
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
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
                                IconButton(onClick = { viewModel.clearSearchQuery() }) {
                                    FontIcon(unicode = FontIcons.Clear, contentDescription = "Clear")
                                }
                            }
                        }
                    )
                },
                expanded = isSearchBarActive,
                onExpandedChange = { viewModel.setSearchBarActive(it) },
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
    flingBehavior = rememberZenithFlingBehavior(),
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
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
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
                                                viewModel.setSearchBarActive(false)
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
    flingBehavior = rememberZenithFlingBehavior(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp)
                        ) {
                            item {
                                Text(
                                    text = "Suggestions",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            item {
                                @OptIn(ExperimentalLayoutApi::class)
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    quickFilters.forEach { filterText ->
                                        // Simple pill shape like recent searches - text only, no icon, no delete
                                        Surface(
                                            modifier = Modifier
                                                .wrapContentWidth()
                                                .clip(RoundedCornerShape(20.dp))
                                                .clickable {
                                                    viewModel.setSearchQuery(filterText.lowercase())
                                                    viewModel.addRecentSearch(filterText)
                                                    viewModel.setSearchBarActive(false)
                                                    focusManager.clearFocus()
                                                    keyboardController?.hide()
                                                },
                                            shape = RoundedCornerShape(20.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                            tonalElevation = 1.dp
                                        ) {
                                            Text(
                                                text = filterText,
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
                    
                    val isLoadingSmartAlbums = isLoading

                    LazyColumn(
    flingBehavior = rememberZenithFlingBehavior(),
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
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    val dummyItems = List(4) { it }
                                    val chunked = dummyItems.chunked(3)
                                    val cardHeight = 240.dp
                                    
                                    chunked.forEachIndexed { index, chunk ->
                                        val isLeftLarge = index % 2 == 0
                                        if (chunk.size == 3) {
                                            Row(modifier = Modifier.fillMaxWidth().height(cardHeight), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                if (isLeftLarge) {
                                                    Box(modifier = Modifier.weight(2f).fillMaxHeight().clip(RoundedCornerShape(28.dp)).shimmerEffect())
                                                    Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                        Box(modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(28.dp)).shimmerEffect())
                                                        Box(modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(28.dp)).shimmerEffect())
                                                    }
                                                } else {
                                                    Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                        Box(modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(28.dp)).shimmerEffect())
                                                        Box(modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(28.dp)).shimmerEffect())
                                                    }
                                                    Box(modifier = Modifier.weight(2f).fillMaxHeight().clip(RoundedCornerShape(28.dp)).shimmerEffect())
                                                }
                                            }
                                        } else if (chunk.size == 2) {
                                            Row(modifier = Modifier.fillMaxWidth().height(160.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(28.dp)).shimmerEffect())
                                                Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(28.dp)).shimmerEffect())
                                            }
                                        } else if (chunk.size == 1) {
                                            Box(modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(28.dp)).shimmerEffect())
                                        }
                                    }
                                }
                            } else if (smartAlbums.isNotEmpty()) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    val chunked = smartAlbums.chunked(3)
                                    val cardHeight = 240.dp
                                    
                                    val RenderChunk: @Composable (Int, List<Album>) -> Unit = { chunkIndex, chunk ->
                                        val isLeftLarge = chunkIndex % 2 == 0
                                        val baseIndex = chunkIndex * 3
                                        
                                        if (chunk.size == 3) {
                                            Row(modifier = Modifier.fillMaxWidth().height(cardHeight), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                if (isLeftLarge) {
                                                    Box(modifier = Modifier.weight(2f).fillMaxHeight()) {
                                                        SmartAlbumHeroCardWrapped(chunk[0], baseIndex, smartAlbumThumbnailCache, navController, isSmallCard = false)
                                                    }
                                                    Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) { SmartAlbumHeroCardWrapped(chunk[1], baseIndex+1, smartAlbumThumbnailCache, navController, isSmallCard = true) }
                                                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) { SmartAlbumHeroCardWrapped(chunk[2], baseIndex+2, smartAlbumThumbnailCache, navController, isSmallCard = true) }
                                                    }
                                                } else {
                                                    Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) { SmartAlbumHeroCardWrapped(chunk[0], baseIndex, smartAlbumThumbnailCache, navController, isSmallCard = true) }
                                                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) { SmartAlbumHeroCardWrapped(chunk[1], baseIndex+1, smartAlbumThumbnailCache, navController, isSmallCard = true) }
                                                    }
                                                    Box(modifier = Modifier.weight(2f).fillMaxHeight()) {
                                                        SmartAlbumHeroCardWrapped(chunk[2], baseIndex+2, smartAlbumThumbnailCache, navController, isSmallCard = false)
                                                    }
                                                }
                                            }
                                        } else if (chunk.size == 2) {
                                            Row(modifier = Modifier.fillMaxWidth().height(160.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) { SmartAlbumHeroCardWrapped(chunk[0], baseIndex, smartAlbumThumbnailCache, navController, isSmallCard = false) }
                                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) { SmartAlbumHeroCardWrapped(chunk[1], baseIndex+1, smartAlbumThumbnailCache, navController, isSmallCard = false) }
                                            }
                                        } else if (chunk.size == 1) {
                                            Box(modifier = Modifier.fillMaxWidth().height(160.dp)) { SmartAlbumHeroCardWrapped(chunk[0], baseIndex, smartAlbumThumbnailCache, navController, isSmallCard = false) }
                                        }
                                    }
                                    
                                    // Visible chunks
                                    chunked.take(2).forEachIndexed { index, chunk ->
                                        RenderChunk(index, chunk)
                                    }
                                    
                                    // Hidden chunks with AnimatedVisibility
                                    if (chunked.size > 2) {
                                        val springSpec = androidx.compose.animation.core.spring<androidx.compose.ui.unit.IntSize>(
                                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                                            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                                        )
                                        AnimatedVisibility(
                                            visible = isSmartAlbumsExpanded,
                                            enter = expandVertically(animationSpec = springSpec) + fadeIn(),
                                            exit = shrinkVertically(animationSpec = springSpec) + fadeOut()
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                chunked.drop(2).forEachIndexed { index, chunk ->
                                                    RenderChunk(index + 2, chunk)
                                                }
                                            }
                                        }
                                    }
                                    
                                    if (smartAlbums.size > 6) {
                                        val rotation by androidx.compose.animation.core.animateFloatAsState(
                                            targetValue = if (isSmartAlbumsExpanded) 180f else 0f
                                        )
                                        
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    isSmartAlbumsExpanded = !isSmartAlbumsExpanded 
                                                    if (isSmartAlbumsExpanded) {
                                                        coroutineScope.launch {
                                                            kotlinx.coroutines.delay(100)
                                                            lazyListState.animateScrollBy(
                                                                value = 1500f,
                                                                animationSpec = androidx.compose.animation.core.spring(
                                                                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                                                                    stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                                                                )
                                                            )
                                                        }
                                                    }
                                                }
                                                .padding(vertical = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = if (isSmartAlbumsExpanded) "Show less" else "Show more",
                                                modifier = Modifier
                                                    .size(36.dp) // Make icon larger and bolder
                                                    .graphicsLayer { rotationZ = rotation },
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        
                    }
                }
                // Material 3 Expressive: Skeletal Shimmer Loader
                showLoadingIndicator && searchQuery.isNotBlank() -> {
                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                        contentPadding = PaddingValues(top = 16.dp, bottom = navBarHeight + 16.dp, start = 8.dp, end = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = false
                    ) {
                        items(24) {
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .shimmerEffect()
                            )
                        }
                    }
                }
                searchQuery.isNotBlank() && isSearchEmpty -> {
                    PremiumEmptyState(
                        icon = FontIcons.SearchOff,
                        title = "No results found",
                        subtitle = "Try a different search term or filter"
                    )
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
    flingBehavior = rememberZenithFlingBehavior(),
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
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
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
                                val carouselState = rememberCarouselState { searchResults.matchedAlbums.size }
                                HorizontalMultiBrowseCarousel(
                                    state = carouselState,
                                    preferredItemWidth = 160.dp,
                                    itemSpacing = 4.dp,
                                    flingBehavior = CarouselDefaults.multiBrowseFlingBehavior(state = carouselState),
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight()
                                ) { index ->
                                    val albumMatch = searchResults.matchedAlbums[index]
                                    val visibleFraction = if (carouselItemDrawInfo.maxSize > 0f) {
                                        (carouselItemDrawInfo.size / carouselItemDrawInfo.maxSize).coerceIn(0f, 1f)
                                    } else {
                                        0f
                                    }
                                    val isMostlyVisible = visibleFraction > 0.5f
                                    val bucketId = albumMatch.items.firstOrNull()?.bucketId

                                    OtherAlbumPillButton(
                                        album = Album(
                                            id = bucketId ?: albumMatch.albumName,
                                            name = albumMatch.albumName,
                                            coverUri = albumMatch.items.firstOrNull()?.uri,
                                            itemCount = albumMatch.items.size
                                        ),
                                        onClick = {
                                            if (bucketId != null) {
                                                // Save search to recent searches first
                                                viewModel.addRecentSearch(searchQuery)
                                                navController.navigate(com.prantiux.pixelgallery.navigation.Screen.AlbumDetail.createRoute(bucketId))
                                            }
                                        },
                                        onLongPress = null,
                                        onMenuClick = {
                                            albumActionsSheet = Album(
                                                id = bucketId ?: albumMatch.albumName,
                                                name = albumMatch.albumName,
                                                coverUri = albumMatch.items.firstOrNull()?.uri,
                                                itemCount = albumMatch.items.size
                                            )
                                        },
                                        showTitle = isMostlyVisible,
                                        modifier = Modifier
                                            .width(160.dp)
                                            .height(170.dp)
                                            .maskClip(MaterialTheme.shapes.extraLarge)
                                    )
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
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
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
                                    onClick = {
                                        // Save search to recent searches
                                        viewModel.addRecentSearch(searchQuery)
                                        
                                        viewModel.showMediaOverlay(
                                            mediaType = "search",
                                            albumId = "search_results",
                                            selectedIndex = index,
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
            albumActionsSheet?.let { album ->
                AlbumActionsBottomSheet(
                    album = album,
                    onDismiss = { albumActionsSheet = null }
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
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
fun SearchSuggestionItem(text: String, iconUnicode: String, onClick: () -> Unit, onDelete: (() -> Unit)) {
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

/**
 * Smart Album Vertical Card - Editorial-style vertical layout with pastel backgrounds and centered elements
 */
@Composable
fun SmartAlbumHeroCard(
    album: Album,
    dominantColor: Color,
    cachedThumbnailUri: android.net.Uri?,
    onThumbnailCached: (android.net.Uri) -> Unit,
    onClick: () -> Unit,
    albumIndex: Int = 0,
    isSmallCard: Boolean = false
) {
    val context = LocalContext.current
    val thumbnailUri = cachedThumbnailUri ?: album.coverUri

    // Use a different expressive shape for each card's item-count badge.
    val countBadgeShape = when (albumIndex % 4) {
        0 -> MaterialShapes.Clover8Leaf.toShape()
        1 -> MaterialShapes.Pill.toShape()
        2 -> MaterialShapes.Gem.toShape()
        else -> MaterialShapes.Cookie6Sided.toShape()
    }
    
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        onClick = onClick
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Full thumbnail area
            if (thumbnailUri != null) {
                AsyncImage(
                    model = thumbnailUri,
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        strokeWidth = 2.dp
                    )
                }
            }

            // Gradient Label Overlay
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.5f),
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = album.name,
                        style = if (isSmallCard) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = countBadgeShape,
                        color = Color.White.copy(alpha = 0.25f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = album.itemCount.toString(),
                                style = if (isSmallCard) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1
                            )
                        }
                    }
                }
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

@Composable
private fun SmartAlbumHeroCardWrapped(
    album: com.prantiux.pixelgallery.model.Album,
    index: Int,
    thumbnailCache: MutableMap<String, android.net.Uri?>,
    navController: androidx.navigation.NavController,
    isSmallCard: Boolean = false
) {
    val dominantColor = MaterialTheme.colorScheme.primaryContainer
    SmartAlbumHeroCard(
        album = album,
        dominantColor = dominantColor,
        cachedThumbnailUri = thumbnailCache[album.id],
        onThumbnailCached = { uri -> thumbnailCache[album.id] = uri },
        onClick = {
            navController.navigate(com.prantiux.pixelgallery.navigation.Screen.SmartAlbumView.createRoute(album.id))
        },
        albumIndex = index,
        isSmallCard = isSmallCard
    )
}
