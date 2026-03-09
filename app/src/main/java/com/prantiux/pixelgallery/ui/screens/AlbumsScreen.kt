package com.prantiux.pixelgallery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import com.prantiux.pixelgallery.ui.shapes.SmoothCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import android.view.HapticFeedbackConstants
import android.os.SystemClock
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.abs
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
import com.prantiux.pixelgallery.BuildConfig
import com.prantiux.pixelgallery.data.AlbumRepository
import com.prantiux.pixelgallery.data.SettingsDataStore
import com.prantiux.pixelgallery.model.Album
import com.prantiux.pixelgallery.model.CategorizedAlbums
import com.prantiux.pixelgallery.ui.components.ConsistentHeader
import com.prantiux.pixelgallery.ui.utils.calculateFloatingNavBarHeight
import com.prantiux.pixelgallery.viewmodel.MediaViewModel
import kotlinx.coroutines.launch
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons

/**
 * Modern Albums Screen with rectangular pill tabs
 * Features:
 * - Header with statusBarsPadding
 * - Rectangular pill tabs for main albums (music app style)
 * - Other albums section with same pill design
 * - Wide special action buttons at bottom
 */
@Composable
fun AlbumsScreen(
    onNavigateToAlbum: (String) -> Unit,
    onNavigateToAllAlbums: () -> Unit,
    onNavigateToRecycleBin: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    viewModel: MediaViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsDataStore = remember { SettingsDataStore(context) }
    
    // UNFILTERED: Use allCategorizedAlbumsFlow (not affected by Photos View Settings filter)
    // Albums tab must show ALL albums regardless of Photos tab selection
    val allAlbums by viewModel.allAlbumsFlow.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val initialSetupInProgress by viewModel.initialSetupInProgress.collectAsState()
    val albumOrderMode by settingsDataStore.albumOrderModeFlow.collectAsState(initial = "Based on no. of images")
    val mainAlbumOrder by settingsDataStore.mainAlbumOrderFlow.collectAsState(initial = emptyList())
    val otherAlbumOrder by settingsDataStore.otherAlbumOrderFlow.collectAsState(initial = emptyList())
    var localAlbumOrderMode by remember { mutableStateOf<String?>(null) }
    var localMainAlbumOrder by remember { mutableStateOf<List<String>?>(null) }
    var localOtherAlbumOrder by remember { mutableStateOf<List<String>?>(null) }

    val effectiveAlbumOrderMode = localAlbumOrderMode ?: albumOrderMode
    val effectiveMainAlbumOrder = localMainAlbumOrder ?: mainAlbumOrder
    val effectiveOtherAlbumOrder = localOtherAlbumOrder ?: otherAlbumOrder
    
    var selectedMainAlbumIndex by remember { mutableStateOf(0) }
    var showReorderBottomSheet by remember { mutableStateOf(false) }

    // No LaunchedEffect needed - data is already loaded by ViewModel
    // Albums are derived from cached media in viewModel.refresh()

    Box(modifier = modifier.fillMaxSize()) {
        if (initialSetupInProgress) {
            com.prantiux.pixelgallery.ui.components.EchoLoadingIndicator(
                modifier = Modifier.align(Alignment.Center),
                label = "Setting up gallery..."
            )
        } else
        // Only show "no albums" if loading is complete AND still no albums
        // This prevents showing "no albums" briefly during initial load
        if (!isLoading && allAlbums.isEmpty()) {
            Text(
                text = "No albums found",
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        } else if (!isLoading) {
            val albums = remember(allAlbums, effectiveAlbumOrderMode, effectiveMainAlbumOrder, effectiveOtherAlbumOrder) {
                buildDisplayAlbums(
                    allAlbums = allAlbums,
                    mode = effectiveAlbumOrderMode,
                    mainOrderIds = effectiveMainAlbumOrder,
                    otherOrderIds = effectiveOtherAlbumOrder
                )
            }
            val navBarHeight = calculateFloatingNavBarHeight()
            val headerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            val backgroundColor = MaterialTheme.colorScheme.surface
            
            // Gradient background layer - extends behind entire content area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                headerColor,
                                headerColor,
                                headerColor.copy(alpha = 0.15f),
                                backgroundColor
                            )
                        )
                    )
            )
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = PaddingValues(top = 0.dp, bottom = navBarHeight)
            ) {
                // Extended Header with Tabs - unified background
                item {
                    AlbumsHeaderWithTabs(
                        albums = albums.mainAlbums,
                        selectedIndex = selectedMainAlbumIndex,
                        onTabSelected = { index ->
                            selectedMainAlbumIndex = index
                        },
                        onViewAllClick = onNavigateToAllAlbums,
                        onEditClick = { showReorderBottomSheet = true },
                        onSettingsClick = onNavigateToSettings
                    )
                }

                // Highlighted section for selected album
                item {
                    if (selectedMainAlbumIndex < albums.mainAlbums.size) {
                        HighlightAlbumSection(
                            albums = albums.mainAlbums,
                            viewModel = viewModel,
                            currentTabIndex = selectedMainAlbumIndex,
                            onTabChange = { newIndex ->
                                selectedMainAlbumIndex = newIndex
                            },
                            onViewAllClick = { 
                                onNavigateToAlbum(albums.mainAlbums[selectedMainAlbumIndex].id) 
                            }
                        )
                    }
                }

                // Other Albums Section
                item {
                    if (albums.otherAlbums.isNotEmpty()) {
                        OtherAlbumsSection(
                            albums = albums.otherAlbums,
                            onAlbumClick = onNavigateToAlbum
                        )
                    }
                }

                // Special Action Buttons
                item {
                    SpecialActionButtons(
                        onStarredClick = onNavigateToFavorites,
                        onSecureClick = { /* TODO */ },
                        onRecycleBinClick = onNavigateToRecycleBin
                    )
                }
            }
            
            // Reorder Bottom Sheet
            if (showReorderBottomSheet) {
                ReorderBottomSheet(
                    mainAlbums = albums.mainAlbums,
                    otherAlbums = albums.otherAlbums,
                    initialAlbumsOrder = effectiveAlbumOrderMode,
                    onSave = { mode, mainOrderIds, otherOrderIds ->
                        localAlbumOrderMode = mode
                        localMainAlbumOrder = mainOrderIds
                        localOtherAlbumOrder = otherOrderIds
                        scope.launch {
                            settingsDataStore.saveAlbumOrderMode(mode)
                            if (mode == "Custom") {
                                settingsDataStore.saveAlbumOrders(mainOrderIds, otherOrderIds)
                            }
                        }
                    },
                    onDismiss = { showReorderBottomSheet = false }
                )
            }
        }
    }
}

/**
 * Combined header with title and album tabs
 * - Font size matched with Photos collapsed heading (26sp)
 * - Background handled by parent composable
 */
@Composable
fun AlbumsHeaderWithTabs(
    albums: List<Album>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    onViewAllClick: () -> Unit,
    onEditClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    val headerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    
    // Set status bar to match header background
    com.prantiux.pixelgallery.ui.components.SetStatusBarColor(headerColor)
    
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Title with Settings icon - same style as Photos screen
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .statusBarsPadding(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Albums",
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            // Settings icon button - same as Photos screen
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.size(48.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        FontIcon(
                            unicode = FontIcons.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface,
                            size = 22.sp
                        )
                    }
                }
            }
        }
        
        // Tabs - directly below without spacer
        MainAlbumTabs(
            albums = albums,
            selectedIndex = selectedIndex,
            onTabSelected = onTabSelected,
            onViewAllClick = onViewAllClick,
            onEditClick = onEditClick
        )
    }
}

/**
 * Header with "Albums" title - respects system status bar
 */
@Composable
fun AlbumsHeader() {
    val headerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    
    // Set status bar to match header background
    com.prantiux.pixelgallery.ui.components.SetStatusBarColor(headerColor)
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = headerColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Text(
                text = "Albums",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )
        }
    }
}

/**
 * Main Album Tabs - rectangular pills with rounded corners (music app style)
 * Acts as real tabs with one active at a time, plus View All button
 */
@Composable
fun MainAlbumTabs(
    albums: List<Album>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    onViewAllClick: () -> Unit,
    onEditClick: () -> Unit = {}
) {
    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    
    // Auto-scroll to selected tab
    LaunchedEffect(selectedIndex) {
        scope.launch {
            lazyListState.animateScrollToItem(selectedIndex)
        }
    }
    
    LazyRow(
        state = lazyListState,
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(bottom = 16.dp)
    ) {
        items(albums.size) { index ->
            RectangularPillTab(
                label = albums[index].name,
                count = albums[index].itemCount,
                index = index,
                selectedIndex = selectedIndex,
                onClick = { onTabSelected(index) }
            )
        }
        
        // View All button
        item {
            ViewAllPillButton(
                onClick = onViewAllClick
            )
        }
        
        // Edit button (icon only)
        item {
            EditPillButton(
                onClick = onEditClick
            )
        }
    }
}

/**
 * Rectangular pill tab with indicator - inspired by music app tabs with Pixel Play animations
 */
@Composable
fun RectangularPillTab(
    label: String,
    count: Int,
    index: Int,
    selectedIndex: Int,
    onClick: () -> Unit
) {
    val isSelected = index == selectedIndex
    
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        com.prantiux.pixelgallery.ui.components.AlbumTabAnimation(
            index = index,
            selectedIndex = selectedIndex,
            onClick = onClick,
            selectedColor = MaterialTheme.colorScheme.primaryContainer,
            unselectedColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = contentColor
            )
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        
        // Animated indicator line
        val indicatorWidth by animateDpAsState(
            targetValue = if (isSelected) 40.dp else 0.dp,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            label = "Indicator Width"
        )
        
        Box(
            modifier = Modifier
                .width(indicatorWidth)
                .height(3.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(2.dp)
                )
        )
    }
}

/**
 * View All pill button - matches tab pill styling
 */
@Composable
fun ViewAllPillButton(
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "View All",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * Edit pill button - icon only, same style as View All
 */
@Composable
fun EditPillButton(
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            FontIcon(
                unicode = FontIcons.Edit,
                contentDescription = "Edit albums layout",
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * Highlighted section showing the selected main album with 6-photo grid
 * Supports horizontal swipe gestures to switch between tabs with smooth animation
 */
@Composable
fun HighlightAlbumSection(
    albums: List<Album>,
    viewModel: com.prantiux.pixelgallery.viewmodel.MediaViewModel,
    currentTabIndex: Int,
    onTabChange: (Int) -> Unit,
    onViewAllClick: () -> Unit
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    
    val dragOffset = remember { Animatable(0f) }
    var cardWidth by remember { mutableStateOf(0f) }
    val cardSpacing = with(density) { 18.dp.toPx() } // 18dp spacing between cards
    val swipeThreshold = 0.3f // 30% of card width to trigger tab change
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .onGloballyPositioned { coordinates ->
                cardWidth = coordinates.size.width.toFloat()
            }
            .pointerInput(currentTabIndex) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        // Cancel any ongoing animation
                        scope.launch { dragOffset.stop() }
                    },
                    onDragEnd = {
                        scope.launch {
                            val progress = dragOffset.value / cardWidth
                            val threshold = swipeThreshold
                            
                            // Determine target index based on drag direction and threshold
                            val targetIndex = when {
                                progress < -threshold && currentTabIndex < albums.size - 1 -> {
                                    // Swiped left enough -> next tab
                                    currentTabIndex + 1
                                }
                                progress > threshold && currentTabIndex > 0 -> {
                                    // Swiped right enough -> previous tab
                                    currentTabIndex - 1
                                }
                                else -> currentTabIndex // Stay on current
                            }
                            
                            if (targetIndex != currentTabIndex) {
                                // Complete the swipe transition
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                val targetOffset = if (targetIndex > currentTabIndex) -(cardWidth + cardSpacing) else (cardWidth + cardSpacing)
                                dragOffset.animateTo(
                                    targetValue = targetOffset,
                                    animationSpec = tween(200)
                                )
                                onTabChange(targetIndex)
                                dragOffset.snapTo(0f)
                            } else {
                                // Snap back to center
                                dragOffset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(200)
                                )
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            dragOffset.animateTo(0f, animationSpec = tween(200))
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        scope.launch {
                            val newOffset = dragOffset.value + dragAmount
                            
                            // Apply resistance at boundaries
                            val constrainedOffset = when {
                                currentTabIndex == 0 && newOffset > 0 -> {
                                    // At first tab, resist right swipe
                                    newOffset * 0.3f
                                }
                                currentTabIndex == albums.size - 1 && newOffset < 0 -> {
                                    // At last tab, resist left swipe
                                    newOffset * 0.3f
                                }
                                else -> newOffset
                            }
                            
                            dragOffset.snapTo(constrainedOffset)
                        }
                        change.consume()
                    }
                )
            }
    ) {
        // Render cards in layers: previous (if exists), current, next (if exists)
        val currentAlbum = albums[currentTabIndex]
        val previousAlbum = if (currentTabIndex > 0) albums[currentTabIndex - 1] else null
        val nextAlbum = if (currentTabIndex < albums.size - 1) albums[currentTabIndex + 1] else null
        
        val offset = dragOffset.value
        val progress = if (cardWidth > 0) offset / cardWidth else 0f
        
        // Previous card (revealed when swiping right)
        previousAlbum?.let {
            AlbumPreviewCard(
                album = it,
                viewModel = viewModel,
                onViewAllClick = onViewAllClick,
                modifier = Modifier
                    .zIndex(0f)
                    .graphicsLayer {
                        translationX = -(cardWidth + cardSpacing) + offset
                        alpha = if (offset > 0) 1f else 0f
                    }
            )
            // Translucent overlay on previous card
            if (offset > 0) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .zIndex(0.5f)
                        .graphicsLayer {
                            translationX = -(cardWidth + cardSpacing) + offset
                        }
                        .background(Color.Black.copy(alpha = (1f - progress).coerceIn(0f, 0.4f)))
                )
            }
        }
        
        // Next card (revealed when swiping left)
        nextAlbum?.let {
            AlbumPreviewCard(
                album = it,
                viewModel = viewModel,
                onViewAllClick = onViewAllClick,
                modifier = Modifier
                    .zIndex(0f)
                    .graphicsLayer {
                        translationX = (cardWidth + cardSpacing) + offset
                        alpha = if (offset < 0) 1f else 0f
                    }
            )
            // Translucent overlay on next card
            if (offset < 0) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .zIndex(0.5f)
                        .graphicsLayer {
                            translationX = (cardWidth + cardSpacing) + offset
                        }
                        .background(Color.Black.copy(alpha = (1f + progress).coerceIn(0f, 0.4f)))
                )
            }
        }
        
        // Current card (moves with finger)
        AlbumPreviewCard(
            album = currentAlbum,
            viewModel = viewModel,
            onViewAllClick = onViewAllClick,
            modifier = Modifier
                .zIndex(1f)
                .graphicsLayer {
                    translationX = offset
                }
        )
    }
}

/**
 * Individual album preview card with 6-photo grid
 */
@Composable
fun AlbumPreviewCard(
    album: Album,
    viewModel: com.prantiux.pixelgallery.viewmodel.MediaViewModel,
    onViewAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = SmoothCornerShape(20.dp, 60),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            // 6-photo grid (2 rows x 3 columns) with clickable items
            if (album.topMediaItems.isNotEmpty()) {
                val itemsToShow = album.topMediaItems.take(6)
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // First row (3 items)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        itemsToShow.take(3).forEachIndexed { index, mediaItem ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                            ) {
                                AsyncImage(
                                    model = mediaItem.uri,
                                    contentDescription = mediaItem.displayName,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(com.prantiux.pixelgallery.ui.utils.getAlbumPreviewCornerShape(index))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable {
                                            viewModel.showMediaOverlay(
                                                mediaType = "album",
                                                albumId = album.id,
                                                selectedIndex = index
                                            )
                                        },
                                    contentScale = ContentScale.Crop
                                )
                                
                                // Video indicator
                                if (mediaItem.isVideo) {
                                    FontIcon(
                                        unicode = FontIcons.PlayArrow,
                                        contentDescription = "Video",
                                        size = 32.sp,
                                        tint = Color.White,
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .background(
                                                color = Color.Black.copy(alpha = 0.6f),
                                                shape = CircleShape
                                            )
                                            .padding(8.dp)
                                    )
                                }
                            }
                        }
                        // Fill empty slots if less than 3 items
                        repeat(3 - minOf(3, itemsToShow.size)) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(com.prantiux.pixelgallery.ui.utils.getAlbumPreviewCornerShape(itemsToShow.size))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(2.dp))
                    
                    // Second row (3 items)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        itemsToShow.drop(3).take(3).forEachIndexed { relIndex, mediaItem ->
                            val index = relIndex + 3
                            val isSixthImage = index == 5
                            val remainingCount = album.itemCount - 5
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                            ) {
                                AsyncImage(
                                    model = mediaItem.uri,
                                    contentDescription = mediaItem.displayName,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(com.prantiux.pixelgallery.ui.utils.getAlbumPreviewCornerShape(index))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable {
                                            // If 6th image and more items exist, open album view
                                            if (isSixthImage && remainingCount > 0) {
                                                onViewAllClick()
                                            } else {
                                                viewModel.showMediaOverlay(
                                                    mediaType = "album",
                                                    albumId = album.id,
                                                    selectedIndex = index
                                                )
                                            }
                                        },
                                    contentScale = ContentScale.Crop
                                )
                                
                                // Dark overlay with "+X more" text on 6th image
                                if (isSixthImage && remainingCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                                com.prantiux.pixelgallery.ui.utils.getAlbumPreviewCornerShape(index)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "+$remainingCount",
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                                
                                // Video indicator (only show if not 6th image with overlay)
                                if (mediaItem.isVideo && !(isSixthImage && remainingCount > 0)) {
                                    FontIcon(
                                        unicode = FontIcons.PlayArrow,
                                        contentDescription = "Video",
                                        size = 32.sp,
                                        tint = Color.White,
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .background(
                                                color = Color.Black.copy(alpha = 0.6f),
                                                shape = CircleShape
                                            )
                                            .padding(8.dp)
                                    )
                                }
                            }
                        }
                        // Fill empty slots if less than 6 items total
                        repeat(3 - minOf(3, maxOf(0, itemsToShow.size - 3))) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(com.prantiux.pixelgallery.ui.utils.getAlbumPreviewCornerShape(itemsToShow.size + it))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                        }
                    }
                }
            } else {
                // Fallback if no media items available
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No media available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Album info with View All button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = album.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${album.itemCount} items",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(
                    onClick = onViewAllClick,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("View All")
                }
            }
        }
    }
}

/**
 * Other Albums Section - horizontally scrollable pills
 */
@Composable
fun OtherAlbumsSection(
    albums: List<Album>,
    onAlbumClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Text(
            text = "Other Albums",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 0.dp)
        )
        
        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(albums.size) { index ->
                OtherAlbumPillButton(
                    album = albums[index],
                    onClick = { onAlbumClick(albums[index].id) }
                )
            }
        }
    }
}

/**
 * Square album item for other albums with overlaid pill labels
 * Matches the visual style from the screenshot
 */
@Composable
fun OtherAlbumPillButton(
    album: Album,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(120.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        // Square album cover image
        if (album.coverUri != null) {
            AsyncImage(
                model = album.coverUri,
                contentDescription = "${album.name} cover",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            // Fallback background if no cover image
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
        
        // Overlaid pill label at bottom center
        Text(
            text = album.name,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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

/**
 * Special Action Buttons - grouped card style like settings
 */
@Composable
fun SpecialActionButtons(
    onStarredClick: () -> Unit,
    onSecureClick: () -> Unit,
    onRecycleBinClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .padding(bottom = 16.dp) // Extra bottom padding for navigation bar
    ) {
        Text(
            text = "Special Folders",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Favourites - Top card
        SpecialActionCard(
            iconUnicode = FontIcons.Star,
            label = "Favourites",
            description = "Your favorite photos",
            onClick = onStarredClick,
            position = SpecialCardPosition.TOP
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Secure Folder - Bottom card
        SpecialActionCard(
            iconUnicode = FontIcons.Lock,
            label = "Secure Folder",
            description = "Protected content",
            onClick = onSecureClick,
            position = SpecialCardPosition.BOTTOM
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Recycle Bin - Separate single card
        SpecialActionCard(
            iconUnicode = FontIcons.Delete,
            label = "Recycle Bin",
            description = "Recently deleted",
            onClick = onRecycleBinClick,
            position = SpecialCardPosition.SINGLE
        )
    }
}

// Position enum for special folder cards
enum class SpecialCardPosition {
    TOP, BOTTOM, SINGLE
}

/**
 * Special action card - styled like Grid Type options in Layout Settings
 */
@Composable
fun SpecialActionCard(
    iconUnicode: String,
    label: String,
    description: String,
    onClick: () -> Unit,
    position: SpecialCardPosition
) {
    val cardShape = when (position) {
        SpecialCardPosition.TOP -> SmoothCornerShape(
            cornerRadiusTL = 24.dp,
            cornerRadiusTR = 24.dp,
            cornerRadiusBR = 8.dp, 
            cornerRadiusBL = 8.dp,
            smoothnessAsPercent = 60
        )
        SpecialCardPosition.BOTTOM -> SmoothCornerShape(
            cornerRadiusTL = 8.dp,
            cornerRadiusTR = 8.dp,
            cornerRadiusBR = 24.dp,
            cornerRadiusBL = 24.dp,
            smoothnessAsPercent = 60
        )
        SpecialCardPosition.SINGLE -> SmoothCornerShape(24.dp, 60)
    }
    
    Surface(
        onClick = onClick,
        shape = cardShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FontIcon(
                unicode = iconUnicode,
                contentDescription = label,
                size = 24.sp,
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Reorder bottom sheet (using Modal BottomSheet with Reorderable library)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReorderBottomSheet(
    mainAlbums: List<Album>,
    otherAlbums: List<Album>,
    initialAlbumsOrder: String,
    onSave: (mode: String, mainAlbumIds: List<String>, otherAlbumIds: List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val logTag = "AlbumsReorder"
    val sessionId = remember { "S${SystemClock.elapsedRealtime().toString().takeLast(6)}" }
    val openedAtMs = remember { SystemClock.elapsedRealtime() }

    fun log(event: String, message: String) {
        if (BuildConfig.DEBUG) {
            val elapsed = SystemClock.elapsedRealtime() - openedAtMs
            android.util.Log.d(logTag, "[$sessionId][$elapsed ms][$event] $message")
        }
    }

    val view = LocalView.current
    var albumsOrder by remember(initialAlbumsOrder) { mutableStateOf(initialAlbumsOrder) }
    var albumsOrderExpanded by remember { mutableStateOf(false) }
    var reorderHandleInUse by remember { mutableStateOf(false) }
    var moveCount by remember { mutableStateOf(0) }
    var crossMoveCount by remember { mutableStateOf(0) }
    var dragStartMs by remember { mutableStateOf<Long?>(null) }
    var dragStopCount by remember { mutableStateOf(0) }
    var lastMoveAtMs by remember { mutableStateOf<Long?>(null) }
    var lastScrollLogAtMs by remember { mutableStateOf(0L) }

    // Unified list architecture for seamless drag across categories.
    var allAlbumsList by remember(mainAlbums, otherAlbums) {
        mutableStateOf((mainAlbums + otherAlbums).toMutableList())
    }
    var mainCount by remember(mainAlbums) { mutableStateOf(mainAlbums.size) }

    val mainAlbumsList = remember(allAlbumsList, mainCount) {
        allAlbumsList.take(mainCount)
    }
    val otherAlbumsList = remember(allAlbumsList, mainCount) {
        allAlbumsList.drop(mainCount)
    }

    val albumsOrderRotation by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (albumsOrderExpanded) 180f else 0f,
        animationSpec = androidx.compose.animation.core.tween(300)
    )
    
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // Single LazyListState for the entire bottom sheet
    val listState = rememberLazyListState()
    
    val reorderableState = rememberReorderableLazyListState(
        onMove = { from, to ->
            if (albumsOrder != "Custom") return@rememberReorderableLazyListState

            moveCount += 1
            val now = SystemClock.elapsedRealtime()
            val deltaFromLastMove = lastMoveAtMs?.let { now - it } ?: -1L
            lastMoveAtMs = now

            val fromId = from.key as? String ?: return@rememberReorderableLazyListState
            val toId = to.key as? String ?: return@rememberReorderableLazyListState

            val fromIndex = allAlbumsList.indexOfFirst { it.id == fromId }
            val toIndex = allAlbumsList.indexOfFirst { it.id == toId }
            if (fromIndex == -1 || toIndex == -1) return@rememberReorderableLazyListState

            log(
                event = "MOVE",
                message = "#${moveCount} from=$fromId(list=$fromIndex/raw=${from.index}) to=$toId(list=$toIndex/raw=${to.index}), deltaFromLastMove=${deltaFromLastMove}ms"
            )

            val fromInMain = fromIndex < mainCount
            val toInMain = toIndex < mainCount

            allAlbumsList = allAlbumsList.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            }

            when {
                fromInMain && !toInMain -> {
                    mainCount = (mainCount - 1).coerceAtLeast(0)
                    crossMoveCount += 1
                    log(event = "MOVE_CROSS_MAIN_TO_OTHER", message = "mainCount=$mainCount crossMoves=$crossMoveCount")
                }
                !fromInMain && toInMain -> {
                    mainCount = (mainCount + 1).coerceAtMost(allAlbumsList.size)
                    crossMoveCount += 1
                    log(event = "MOVE_CROSS_OTHER_TO_MAIN", message = "mainCount=$mainCount crossMoves=$crossMoveCount")
                }
                fromInMain && toInMain -> {
                    log(event = "MOVE_MAIN", message = "reordered in main")
                }
                else -> {
                    log(event = "MOVE_OTHER", message = "reordered in other")
                }
            }
        },
        lazyListState = listState
    )

    LaunchedEffect(Unit) {
        log(
            event = "OPEN",
            message = "main=${mainAlbumsList.size}, other=${otherAlbumsList.size}, mode=$albumsOrder"
        )
    }

    LaunchedEffect(albumsOrder) {
        log(
            event = "MODE",
            message = "albumsOrder changed to '$albumsOrder'"
        )
    }

    LaunchedEffect(reorderHandleInUse, reorderableState.isAnyItemDragging) {
        log(
            event = "DRAG_STATE",
            message = "handleInUse=$reorderHandleInUse, isAnyItemDragging=${reorderableState.isAnyItemDragging}"
        )
    }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging && reorderHandleInUse) {
            reorderHandleInUse = false
            log(event = "DRAG_RECOVERY", message = "Forced handleInUse=false after drag end")
        }
    }

    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastScrollLogAtMs >= 350L) {
            lastScrollLogAtMs = now
            log(
                event = "SCROLL",
                message = "index=${listState.firstVisibleItemIndex}, offset=${listState.firstVisibleItemScrollOffset}"
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            log(
                event = "CLOSE",
                message = "moves=$moveCount, crossMoves=$crossMoveCount, dragStops=$dragStopCount, finalMain=${mainAlbumsList.size}, finalOther=${otherAlbumsList.size}"
            )
        }
    }
    
    ModalBottomSheet(
        onDismissRequest = {
            log(
                event = "DISMISS",
                message = "onDismissRequest called (back/outside/swipe)"
            )
            onDismiss()
        },
        sheetState = sheetState
    ) {
        log(
            event = "COMPOSE",
            message = "sheet content composed main=${mainAlbumsList.size}, other=${otherAlbumsList.size}, mode=$albumsOrder"
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Reorder your albums",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                userScrollEnabled = !(reorderHandleInUse || reorderableState.isAnyItemDragging),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Albums order setting with expandable options
                item {
                    Surface(
                        onClick = {
                            albumsOrderExpanded = !albumsOrderExpanded
                            log(event = "ORDER_SECTION", message = "expanded=$albumsOrderExpanded")
                        },
                        shape = if (albumsOrderExpanded) 
                            RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp) 
                            else RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FontIcon(
                                unicode = FontIcons.SwapVert,
                                contentDescription = null,
                                size = 24.sp,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Albums order",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = albumsOrder,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FontIcon(
                                unicode = FontIcons.KeyboardArrowDown,
                                contentDescription = null,
                                size = 24.sp,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.graphicsLayer { rotationZ = albumsOrderRotation }
                            )
                        }
                    }
                }
                
                // Expandable options (inspired by badge type)
                item {
                    Column {
                        Spacer(modifier = Modifier.height(2.dp))
                        androidx.compose.animation.AnimatedVisibility(
                            visible = albumsOrderExpanded,
                            enter = androidx.compose.animation.expandVertically(animationSpec = androidx.compose.animation.core.tween(300)) + androidx.compose.animation.fadeIn(),
                            exit = androidx.compose.animation.shrinkVertically(animationSpec = androidx.compose.animation.core.tween(300)) + androidx.compose.animation.fadeOut()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                AlbumOrderOption(
                                    label = "Based on no. of images",
                                    iconUnicode = FontIcons.Numbers,
                                    isSelected = albumsOrder == "Based on no. of images",
                                    onClick = {
                                        albumsOrder = "Based on no. of images"
                                        log(event = "ORDER_OPTION", message = "selected='Based on no. of images'")
                                    },
                                    position = com.prantiux.pixelgallery.ui.screens.settings.SettingPosition.TOP
                                )
                                AlbumOrderOption(
                                    label = "Recently updated",
                                    iconUnicode = FontIcons.History,
                                    isSelected = albumsOrder == "Recently updated",
                                    onClick = {
                                        albumsOrder = "Recently updated"
                                        log(event = "ORDER_OPTION", message = "selected='Recently updated'")
                                    },
                                    position = com.prantiux.pixelgallery.ui.screens.settings.SettingPosition.MIDDLE
                                )
                                AlbumOrderOption(
                                    label = "Custom",
                                    iconUnicode = FontIcons.DragHandle,
                                    isSelected = albumsOrder == "Custom",
                                    onClick = {
                                        albumsOrder = "Custom"
                                        log(event = "ORDER_OPTION", message = "selected='Custom'")
                                    },
                                    position = com.prantiux.pixelgallery.ui.screens.settings.SettingPosition.BOTTOM
                                )
                            }
                        }
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
                
                if (allAlbumsList.isNotEmpty()) {
                    item {
                        Text(
                            text = "Main albums",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                        )
                    }
                }

                items(allAlbumsList.size, key = { allAlbumsList[it].id }) { index ->
                    val album = allAlbumsList[index]
                    if (index == mainCount && mainCount in 1 until allAlbumsList.size) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Other albums",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                        )
                    }

                    ReorderableItem(
                        state = reorderableState,
                        key = album.id,
                        enabled = albumsOrder == "Custom"
                    ) { isDragging ->
                        DraggableAlbumItem(
                            albumName = album.name,
                            position = when {
                                allAlbumsList.size == 1 -> AlbumItemPosition.SINGLE
                                index == 0 -> AlbumItemPosition.TOP
                                index == allAlbumsList.size - 1 -> AlbumItemPosition.BOTTOM
                                else -> AlbumItemPosition.MIDDLE
                            },
                            isDragging = isDragging,
                            handleModifier = if (albumsOrder == "Custom") {
                                Modifier.draggableHandle(
                                    onDragStarted = {
                                        dragStartMs = SystemClock.elapsedRealtime()
                                        reorderHandleInUse = true
                                        val section = if (index < mainCount) "main" else "other"
                                        log(event = "DRAG_START", message = "section=$section album=${album.name} idx=$index")
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    },
                                    onDragStopped = {
                                        reorderHandleInUse = false
                                        dragStopCount += 1
                                        val duration = dragStartMs?.let { SystemClock.elapsedRealtime() - it }
                                        val section = if (index < mainCount) "main" else "other"
                                        log(
                                            event = "DRAG_STOP",
                                            message = "section=$section album=${album.name} idx=$index duration=${duration ?: -1}ms stopCount=$dragStopCount"
                                        )
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                )
                            } else {
                                Modifier
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        log(event = "RESTORE", message = "Restore pressed")
                        allAlbumsList = (mainAlbums + otherAlbums).toMutableList()
                        mainCount = mainAlbums.size
                        albumsOrder = "Based on no. of images"
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Restore")
                }
                Button(
                    onClick = {
                        log(
                            event = "DONE",
                            message = "Done pressed mode=$albumsOrder mainCount=$mainCount total=${allAlbumsList.size}"
                        )
                        onSave(
                            albumsOrder,
                            allAlbumsList.take(mainCount).map { it.id },
                            allAlbumsList.drop(mainCount).map { it.id }
                        )
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Done")
                }
            }
        }
    }
}

enum class AlbumItemPosition {
    TOP, MIDDLE, BOTTOM, SINGLE
}

/**
 * Album order option - inspired by badge type expandable option
 */
@Composable
private fun AlbumOrderOption(
    label: String,
    iconUnicode: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    position: com.prantiux.pixelgallery.ui.screens.settings.SettingPosition
) {
    val shape = when (position) {
        com.prantiux.pixelgallery.ui.screens.settings.SettingPosition.TOP -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
        com.prantiux.pixelgallery.ui.screens.settings.SettingPosition.MIDDLE -> RoundedCornerShape(8.dp)
        com.prantiux.pixelgallery.ui.screens.settings.SettingPosition.BOTTOM -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        else -> RoundedCornerShape(0.dp)
    }
    
    Surface(
        onClick = onClick,
        shape = shape,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FontIcon(
                unicode = iconUnicode,
                contentDescription = null,
                size = 24.sp,
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            
            // Radio button
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .border(
                        width = 2.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary 
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        shape = CircleShape
                    )
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary 
                        else MaterialTheme.colorScheme.surface,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(MaterialTheme.colorScheme.onPrimary, CircleShape)
                    )
                }
            }
        }
    }
}

/**
 * Draggable album item with drag handle
 */
@Composable
private fun DraggableAlbumItem(
    albumName: String,
    position: AlbumItemPosition,
    isDragging: Boolean,
    handleModifier: Modifier = Modifier
) {
    val shape = when (position) {
        AlbumItemPosition.TOP -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
        AlbumItemPosition.MIDDLE -> RoundedCornerShape(4.dp)
        AlbumItemPosition.BOTTOM -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
        AlbumItemPosition.SINGLE -> RoundedCornerShape(16.dp)
    }
    
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shadowElevation = if (isDragging) 4.dp else 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.DragIndicator,
                contentDescription = "Drag to reorder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = handleModifier
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Text(
                text = albumName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun buildDisplayAlbums(
    allAlbums: List<Album>,
    mode: String,
    mainOrderIds: List<String>,
    otherOrderIds: List<String>
): CategorizedAlbums {
    // Handle "Recently updated" mode
    if (mode == "Recently updated") {
        val sortedAlbums = allAlbums.sortedByDescending { album ->
            album.topMediaItems.maxOfOrNull { it.dateAdded } ?: 0L
        }
        return CategorizedAlbums(
            mainAlbums = sortedAlbums.take(4),
            otherAlbums = sortedAlbums.drop(4)
        )
    }
    
    if (mode != "Custom") {
        return CategorizedAlbums(
            mainAlbums = allAlbums.take(4),
            otherAlbums = allAlbums.drop(4)
        )
    }

    if (mainOrderIds.isEmpty() && otherOrderIds.isEmpty()) {
        return CategorizedAlbums(
            mainAlbums = allAlbums.take(4),
            otherAlbums = allAlbums.drop(4)
        )
    }

    val byId = allAlbums.associateBy { it.id }

    val mainAlbums = mainOrderIds
        .mapNotNull { byId[it] }
        .distinctBy { it.id }

    val mainIdSet = mainAlbums.map { it.id }.toSet()
    val otherAlbums = otherOrderIds
        .mapNotNull { byId[it] }
        .filterNot { it.id in mainIdSet }
        .distinctBy { it.id }

    val usedIds = (mainAlbums + otherAlbums).map { it.id }.toSet()
    val remaining = allAlbums.filterNot { it.id in usedIds }

    return CategorizedAlbums(
        mainAlbums = mainAlbums,
        otherAlbums = otherAlbums + remaining
    )
}
