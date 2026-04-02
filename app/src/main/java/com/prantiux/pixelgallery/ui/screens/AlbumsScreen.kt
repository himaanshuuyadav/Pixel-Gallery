package com.prantiux.pixelgallery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import com.prantiux.pixelgallery.ui.shapes.SmoothCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import coil.request.ImageRequest
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Restore
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.abs
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.prantiux.pixelgallery.data.AlbumRepository
import com.prantiux.pixelgallery.data.SettingsDataStore
import com.prantiux.pixelgallery.model.Album
import com.prantiux.pixelgallery.model.CategorizedAlbums
import com.prantiux.pixelgallery.ui.components.ConsistentHeader
import com.prantiux.pixelgallery.ui.components.ExpressiveLoadingIndicator
import com.prantiux.pixelgallery.ui.utils.calculateFloatingNavBarHeight
import com.prantiux.pixelgallery.viewmodel.MediaViewModel
import kotlinx.coroutines.launch
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons

private val AlbumCardSpacing = 18.dp
private val AlbumCardSwipeAnimationSpec = tween<Float>(200)

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
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsDataStore = remember { SettingsDataStore(context) }
    
    // UNFILTERED: Use allCategorizedAlbumsFlow (not affected by Photos View Settings filter)
    // Albums tab must show ALL albums regardless of Photos tab selection
    val allAlbums by viewModel.allAlbumsFlow.collectAsState()
    val favorites by viewModel.favoritesFlow.collectAsState()
    val trashedItems by viewModel.trashedItems.collectAsState()
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
    var albumActionsSheet by remember { mutableStateOf<Album?>(null) }
    var tabTransitionTargetIndex by remember { mutableStateOf<Int?>(null) }
    val dragOffset = remember { Animatable(0f) }
    var highlightCardWidth by remember { mutableStateOf(0f) }
    val view = LocalView.current
    val density = LocalDensity.current
    val cardSpacing = with(density) { AlbumCardSpacing.toPx() }

    // Ensure recycle-bin count is available when Albums tab opens.
    LaunchedEffect(context) {
        viewModel.loadTrashedItems(context)
    }

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
                            if (index == selectedMainAlbumIndex || index !in albums.mainAlbums.indices) {
                                return@AlbumsHeaderWithTabs
                            }

                            if (highlightCardWidth <= 0f) {
                                selectedMainAlbumIndex = index
                                return@AlbumsHeaderWithTabs
                            }

                            scope.launch {
                                dragOffset.stop()
                                tabTransitionTargetIndex = index

                                val direction = if (index > selectedMainAlbumIndex) 1 else -1
                                val targetOffset = if (direction > 0) {
                                    -(highlightCardWidth + cardSpacing)
                                } else {
                                    highlightCardWidth + cardSpacing
                                }

                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                dragOffset.animateTo(
                                    targetValue = targetOffset,
                                    animationSpec = AlbumCardSwipeAnimationSpec
                                )

                                selectedMainAlbumIndex = index
                                dragOffset.snapTo(0f)
                                tabTransitionTargetIndex = null
                            }
                        },
                        onViewAllClick = onNavigateToAllAlbums,
                        onTabLongPress = { album -> albumActionsSheet = album },
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
                            dragOffset = dragOffset,
                            transitionTargetIndex = tabTransitionTargetIndex,
                            onCardWidthMeasured = { width ->
                                highlightCardWidth = width
                            },
                            onTabChange = { newIndex ->
                                selectedMainAlbumIndex = newIndex
                            },
                            onViewAllClick = { 
                                onNavigateToAlbum(albums.mainAlbums[selectedMainAlbumIndex].id) 
                            },
                            onAlbumActionsClick = { album -> albumActionsSheet = album }
                        )
                    }
                }

                // Other Albums Section
                item {
                    if (albums.otherAlbums.isNotEmpty()) {
                        OtherAlbumsSection(
                            albums = albums.otherAlbums,
                            onViewAllClick = onNavigateToAllAlbums,
                            onAlbumClick = onNavigateToAlbum,
                            onAlbumLongPress = { album -> albumActionsSheet = album },
                            onAlbumMenuClick = { album -> albumActionsSheet = album }
                        )
                    }
                }

                // Special Action Buttons
                item {
                    SpecialActionButtons(
                        onStarredClick = onNavigateToFavorites,
                        onSecureClick = { /* TODO */ },
                        onRecycleBinClick = onNavigateToRecycleBin,
                        favoritesCount = favorites.size,
                        secureCount = 0,
                        recycleBinCount = trashedItems.size
                    )
                }
            }
            
            // Album Actions Bottom Sheet
            albumActionsSheet?.let { album ->
                AlbumActionsBottomSheet(
                    album = album,
                    onDismiss = { albumActionsSheet = null }
                )
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
    onTabLongPress: (Album) -> Unit = {},
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
            onTabLongPress = onTabLongPress,
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
    onTabLongPress: (Album) -> Unit = {},
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
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        items(albums.size) { index ->
            RectangularPillTab(
                label = albums[index].name,
                count = albums[index].itemCount,
                index = index,
                selectedIndex = selectedIndex,
                onClick = { onTabSelected(index) },
                onLongPress = { onTabLongPress(albums[index]) }
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
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null
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
            onLongClick = onLongPress,
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
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val animSpec = tween<Float>(durationMillis = 250, easing = FastOutSlowInEasing)

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .graphicsLayer {
                // Match tab feel: horizontal stretch is more noticeable than uniform scale.
                scaleX = scale.value
            }
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
                scope.launch {
                    scale.animateTo(1.15f, animationSpec = animSpec)
                    scale.animateTo(1f, animationSpec = animSpec)
                }
            }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            // Match tab idle size rhythm (same as AlbumTabAnimation content padding).
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
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
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val animSpec = tween<Float>(durationMillis = 250, easing = FastOutSlowInEasing)

    Surface(
        // Square base with heavy roundness to read as "pill-square" during stretch animation.
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = scale.value
            }
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
                scope.launch {
                    scale.animateTo(1.15f, animationSpec = animSpec)
                    scale.animateTo(1f, animationSpec = animSpec)
                }
            }
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
    dragOffset: Animatable<Float, AnimationVector1D>,
    transitionTargetIndex: Int? = null,
    onCardWidthMeasured: (Float) -> Unit,
    onTabChange: (Int) -> Unit,
    onViewAllClick: () -> Unit,
    onAlbumActionsClick: (Album) -> Unit = {}
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    
    var cardWidth by remember { mutableStateOf(0f) }
    val cardSpacing = with(density) { AlbumCardSpacing.toPx() }
    val swipeThreshold = 0.3f // 30% of card width to trigger tab change
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clipToBounds()
            .onGloballyPositioned { coordinates ->
                cardWidth = coordinates.size.width.toFloat()
                onCardWidthMeasured(cardWidth)
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
                                    animationSpec = AlbumCardSwipeAnimationSpec
                                )
                                onTabChange(targetIndex)
                                dragOffset.snapTo(0f)
                            } else {
                                // Snap back to center
                                dragOffset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = AlbumCardSwipeAnimationSpec
                                )
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            dragOffset.animateTo(0f, animationSpec = AlbumCardSwipeAnimationSpec)
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
        val resolvedTransitionTarget = transitionTargetIndex?.takeIf {
            it in albums.indices && it != currentTabIndex
        }
        val previousAlbum = when {
            resolvedTransitionTarget != null && resolvedTransitionTarget < currentTabIndex -> {
                albums[resolvedTransitionTarget]
            }
            currentTabIndex > 0 -> albums[currentTabIndex - 1]
            else -> null
        }
        val nextAlbum = when {
            resolvedTransitionTarget != null && resolvedTransitionTarget > currentTabIndex -> {
                albums[resolvedTransitionTarget]
            }
            currentTabIndex < albums.size - 1 -> albums[currentTabIndex + 1]
            else -> null
        }
        
        val offset = dragOffset.value
        val progress = if (cardWidth > 0) offset / cardWidth else 0f
        
        // Previous card (revealed when swiping right)
        previousAlbum?.let {
            AlbumPreviewCard(
                album = it,
                viewModel = viewModel,
                onViewAllClick = onViewAllClick,
                onAlbumActionsClick = onAlbumActionsClick,
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
                onAlbumActionsClick = onAlbumActionsClick,
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
            onAlbumActionsClick = onAlbumActionsClick,
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
    onAlbumActionsClick: (Album) -> Unit = {},
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
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { onAlbumActionsClick(album) }
                        )
                ) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = onViewAllClick,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("View All")
                    }
                    IconButton(onClick = { onAlbumActionsClick(album) }) {
                        FontIcon(
                            unicode = FontIcons.MoreVert,
                            contentDescription = "Album actions",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
    onViewAllClick: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onAlbumLongPress: ((Album) -> Unit)? = null,
    onAlbumMenuClick: ((Album) -> Unit)? = null
) {
    val carouselState = rememberCarouselState { albums.size }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "More Albums",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(onClick = onViewAllClick) {
                Text("View all")
            }
        }
        
        Spacer(modifier = Modifier.height(0.dp))

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
            val album = albums[index]
            val visibleFraction = if (carouselItemDrawInfo.maxSize > 0f) {
                (carouselItemDrawInfo.size / carouselItemDrawInfo.maxSize).coerceIn(0f, 1f)
            } else {
                0f
            }
            val isMostlyVisible = visibleFraction > 0.5f
            OtherAlbumPillButton(
                album = album,
                onClick = { onAlbumClick(album.id) },
                onLongPress = if (onAlbumLongPress != null) {{ onAlbumLongPress(album) }} else null,
                onMenuClick = if (onAlbumMenuClick != null) {{ onAlbumMenuClick(album) }} else null,
                showTitle = isMostlyVisible,
                modifier = Modifier
                    .width(160.dp)
                    .height(170.dp)
                    .maskClip(MaterialTheme.shapes.extraLarge)
            )
        }
    }
}

/**
 * Square album item for other albums with overlaid pill labels
 * Matches the visual style from the screenshot
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OtherAlbumPillButton(
    album: Album,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null,
    showTitle: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    Box(
        modifier = modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = if (onLongPress != null) {{
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onLongPress()
                }} else null
            )
    ) {
        // Square album cover image
        if (album.coverUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(album.coverUri)
                    .size(360, 510)
                    .crossfade(false)
                    .build(),
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

        if (onMenuClick != null) {
            Surface(
                onClick = onMenuClick,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    FontIcon(
                        unicode = FontIcons.MoreVert,
                        contentDescription = "Album actions",
                        size = 18.sp,
                        tint = Color.White
                    )
                }
            }
        }
        
        if (showTitle) {
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
}

/**
 * Special Action Buttons - grouped card style like settings
 */
@Composable
fun SpecialActionButtons(
    onStarredClick: () -> Unit,
    onSecureClick: () -> Unit,
    onRecycleBinClick: () -> Unit,
    favoritesCount: Int,
    secureCount: Int,
    recycleBinCount: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 16.dp) // Extra bottom padding for navigation bar
    ) {
        Text(
            text = "Utilities",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 0.dp, vertical = 8.dp)
        )

        // Favourites - Top card
        SpecialActionCard(
            iconUnicode = FontIcons.Star,
            label = "Favourites",
            description = "Your favourite photos and albums",
            onClick = onStarredClick,
            position = SpecialCardPosition.TOP,
            itemCount = favoritesCount
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Secure Folder - Bottom card
        SpecialActionCard(
            iconUnicode = FontIcons.Lock,
            label = "Secure Folder",
            description = "Protected content",
            onClick = onSecureClick,
            position = SpecialCardPosition.BOTTOM,
            itemCount = secureCount
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Recycle Bin - Separate single card
        SpecialActionCard(
            iconUnicode = FontIcons.Delete,
            label = "Recycle Bin",
            description = "Recently deleted",
            onClick = onRecycleBinClick,
            position = SpecialCardPosition.SINGLE,
            itemCount = recycleBinCount
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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SpecialActionCard(
    iconUnicode: String,
    label: String,
    description: String,
    onClick: () -> Unit,
    position: SpecialCardPosition,
    itemCount: Int
) {
    val countBadgeShape = when (position) {
        SpecialCardPosition.TOP -> MaterialShapes.Clover8Leaf.toShape()
        SpecialCardPosition.BOTTOM -> MaterialShapes.Pill.toShape()
        SpecialCardPosition.SINGLE -> MaterialShapes.Cookie6Sided.toShape()
    }

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

            Surface(
                modifier = Modifier.size(36.dp),
                shape = countBadgeShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = itemCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Reorder bottom sheet (using Modal BottomSheet with Reorderable library)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun ReorderBottomSheet(
    mainAlbums: List<Album>,
    otherAlbums: List<Album>,
    initialAlbumsOrder: String,
    onSave: (mode: String, mainAlbumIds: List<String>, otherAlbumIds: List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    // Helper function to calculate sorted albums based on order mode
    fun calculateSortedAlbums(albums: List<Album>, mode: String): Pair<MutableList<Album>, Int> {
        val all = albums.toMutableList()
        val mainSize = if (mainAlbums.isNotEmpty()) mainAlbums.size else 4
        
        return when (mode) {
            "Based on no. of images" -> {
                // Sort by number of images in descending order
                val sorted = all.sortedByDescending { it.itemCount }.toMutableList()
                Pair(sorted, minOf(mainSize, sorted.size))
            }
            "Recently updated" -> {
                // Sort by most recent media item date in descending order
                val sorted = all.sortedByDescending { album ->
                    album.topMediaItems.maxOfOrNull { it.dateAdded } ?: 0L
                }.toMutableList()
                Pair(sorted, minOf(mainSize, sorted.size))
            }
            else -> {
                // Custom mode - keep as is
                Pair(all, mainSize)
            }
        }
    }

    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var albumsOrder by remember(initialAlbumsOrder) { mutableStateOf(initialAlbumsOrder) }
    var previousAlbumsOrder by remember(initialAlbumsOrder) { mutableStateOf(initialAlbumsOrder) }
    var albumsOrderExpanded by remember { mutableStateOf(false) }
    var reorderHandleInUse by remember { mutableStateOf(false) }
    var isLoadingNewOrder by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var isDoneConfirming by remember { mutableStateOf(false) }
    var dragStartMs by remember { mutableStateOf<Long?>(null) }

    // Unified list architecture for seamless drag across categories.
    var allAlbumsList by remember(mainAlbums, otherAlbums) {
        mutableStateOf((mainAlbums + otherAlbums).toMutableList())
    }
    var mainCount by remember(mainAlbums) { mutableStateOf(mainAlbums.size) }

    val albumsOrderRotation by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (albumsOrderExpanded) 180f else 0f,
        animationSpec = androidx.compose.animation.core.tween(300)
    )
    val restoreInnerCorner by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isRestoring) 28.dp else 4.dp,
        animationSpec = androidx.compose.animation.core.tween(300),
        label = "RestoreSplitInnerCorner"
    )
    val restoreRotation by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isRestoring) 360f else 0f,
        animationSpec = androidx.compose.animation.core.tween(700),
        label = "RestoreSplitRotation"
    )
    val doneEndCorner by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isDoneConfirming) 28.dp else 4.dp,
        animationSpec = androidx.compose.animation.core.tween(300),
        label = "DoneEndCorner"
    )

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // Single LazyListState for the entire bottom sheet
    val listState = rememberLazyListState()
    
    val reorderableState = rememberReorderableLazyListState(
        onMove = { from, to ->
            if (albumsOrder != "Custom") return@rememberReorderableLazyListState

            val fromId = from.key as? String ?: return@rememberReorderableLazyListState
            val toId = to.key as? String ?: return@rememberReorderableLazyListState

            val fromIndex = allAlbumsList.indexOfFirst { it.id == fromId }
            val toIndex = allAlbumsList.indexOfFirst { it.id == toId }
            if (fromIndex == -1 || toIndex == -1) return@rememberReorderableLazyListState

            val fromInMain = fromIndex < mainCount
            val toInMain = toIndex < mainCount

            allAlbumsList = allAlbumsList.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            }

            when {
                fromInMain && !toInMain -> {
                    mainCount = (mainCount - 1).coerceAtLeast(0)
                }
                !fromInMain && toInMain -> {
                    mainCount = (mainCount + 1).coerceAtMost(allAlbumsList.size)
                }
            }
        },
        lazyListState = listState
    )
    val sheetGesturesEnabled = !reorderHandleInUse &&
        !reorderableState.isAnyItemDragging &&
        !isLoadingNewOrder &&
        listState.firstVisibleItemIndex == 0 &&
        listState.firstVisibleItemScrollOffset == 0

    LaunchedEffect(albumsOrder) {
        // Skip first emission — sheet just opened, no loader needed
        if (previousAlbumsOrder == albumsOrder) {
            return@LaunchedEffect
        }

        previousAlbumsOrder = albumsOrder

        if (albumsOrder == "Custom") {
            // Switching TO Custom — keep existing order as-is, no preview
            return@LaunchedEffect
        }

        // Switching FROM any mode TO Based on no. of images / Recently updated
        isLoadingNewOrder = true
        kotlinx.coroutines.delay(350)
        val (sortedAlbums, newMainCount) = calculateSortedAlbums(allAlbumsList, albumsOrder)
        allAlbumsList = sortedAlbums
        mainCount = newMainCount
        isLoadingNewOrder = false
    }
    
    ModalBottomSheet(
        onDismissRequest = {
            onDismiss()
        },
        sheetGesturesEnabled = sheetGesturesEnabled,
        sheetState = sheetState
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
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
                
                CompositionLocalProvider(LocalOverscrollFactory provides null) {
                LazyColumn(
                    state = listState,
                    userScrollEnabled = !(reorderHandleInUse || reorderableState.isAnyItemDragging || isLoadingNewOrder),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // Albums order setting with expandable options
                    item {
                        Surface(
                            onClick = {
                                albumsOrderExpanded = !albumsOrderExpanded
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
                                            albumsOrderExpanded = false
                                        },
                                        position = com.prantiux.pixelgallery.ui.screens.settings.SettingPosition.TOP
                                    )
                                    AlbumOrderOption(
                                        label = "Recently updated",
                                        iconUnicode = FontIcons.History,
                                        isSelected = albumsOrder == "Recently updated",
                                        onClick = {
                                            albumsOrder = "Recently updated"
                                            albumsOrderExpanded = false
                                        },
                                        position = com.prantiux.pixelgallery.ui.screens.settings.SettingPosition.MIDDLE
                                    )
                                    AlbumOrderOption(
                                        label = "Custom",
                                        iconUnicode = FontIcons.DragHandle,
                                        isSelected = albumsOrder == "Custom",
                                        onClick = {
                                            albumsOrder = "Custom"
                                            albumsOrderExpanded = false
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
                            enabled = true
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
                                handleModifier = Modifier.draggableHandle(
                                    onDragStarted = {
                                        if (albumsOrder != "Custom") {
                                            albumsOrder = "Custom"
                                            albumsOrderExpanded = false
                                        }
                                        dragStartMs = SystemClock.elapsedRealtime()
                                        reorderHandleInUse = true
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    },
                                    onDragStopped = {
                                        reorderHandleInUse = false
                                        dragStartMs = null
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                )
                            )
                        }
                    }
                }
                } // end CompositionLocalProvider

            }

            SplitButtonLayout(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                spacing = 2.dp,
                leadingButton = {
                    FilledTonalButton(
                        onClick = {
                            if (isDoneConfirming) return@FilledTonalButton
                            isDoneConfirming = true
                            onSave(
                                albumsOrder,
                                allAlbumsList.take(mainCount).map { it.id },
                                allAlbumsList.drop(mainCount).map { it.id }
                            )
                            scope.launch {
                                kotlinx.coroutines.delay(1000)
                                sheetState.hide()
                                onDismiss()
                            }
                        },
                        modifier = Modifier.height(56.dp),
                        shape = RoundedCornerShape(
                            topStart = 28.dp,
                            topEnd = doneEndCorner,
                            bottomEnd = doneEndCorner,
                            bottomStart = 28.dp
                        ),
                        contentPadding = PaddingValues(start = 24.dp, end = 24.dp)
                    ) {
                        if (isDoneConfirming) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Done")
                    }
                },
                trailingButton = {
                    FilledTonalIconButton(
                        onClick = {
                            if (isRestoring) return@FilledTonalIconButton
                            isRestoring = true
                            albumsOrderExpanded = false
                            isLoadingNewOrder = true
                            scope.launch {
                                kotlinx.coroutines.delay(350)
                                val restoredAlbums = (mainAlbums + otherAlbums).toMutableList()
                                val (sortedAlbums, newMainCount) = calculateSortedAlbums(restoredAlbums, "Based on no. of images")
                                allAlbumsList = sortedAlbums
                                mainCount = newMainCount
                                previousAlbumsOrder = "Based on no. of images"
                                albumsOrder = "Based on no. of images"
                                isLoadingNewOrder = false
                                isRestoring = false
                            }
                        },
                        modifier = Modifier.size(56.dp),
                        shape = RoundedCornerShape(
                            topStart = restoreInnerCorner,
                            topEnd = 28.dp,
                            bottomEnd = 28.dp,
                            bottomStart = restoreInnerCorner
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Restore,
                            contentDescription = "Restore",
                            modifier = Modifier
                                .size(26.dp)
                                .graphicsLayer { rotationZ = restoreRotation }
                        )
                    }
                }
            )
            
            // Loading overlay when switching album orders
            if (isLoadingNewOrder) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        ExpressiveLoadingIndicator(size = 56.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Sorting albums...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
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
