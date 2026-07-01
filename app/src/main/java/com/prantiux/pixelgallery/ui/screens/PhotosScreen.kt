package com.prantiux.pixelgallery.ui.screens

import com.prantiux.pixelgallery.ui.utils.rememberZenithFlingBehavior
import android.Manifest
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.prantiux.pixelgallery.R
import com.prantiux.pixelgallery.BuildConfig
import com.prantiux.pixelgallery.model.MediaItem
import com.prantiux.pixelgallery.viewmodel.MediaViewModel
import com.prantiux.pixelgallery.viewmodel.SortMode
import com.prantiux.pixelgallery.ui.components.ConsistentHeader
import com.prantiux.pixelgallery.ui.components.MediaThumbnail
import com.prantiux.pixelgallery.ui.components.PermissionRequestScreen
import com.prantiux.pixelgallery.ui.components.SelectableMediaItem
import com.prantiux.pixelgallery.ui.utils.calculateFloatingNavBarHeight
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.prantiux.pixelgallery.model.MediaGridItem

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen(
    viewModel: MediaViewModel,
    onNavigateToSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    // ROOM-FIRST: Observe pagedMediaFlow
    val pagedMedia = viewModel.pagedMediaFlow.collectAsLazyPagingItems()
    val isLoading by viewModel.isLoading.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        )
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val permissionsState = rememberMultiplePermissionsState(permissions)

    LaunchedEffect(permissionsState.allPermissionsGranted) {
        viewModel.setMediaPermissionsGranted(permissionsState.allPermissionsGranted)
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        // ROOM-FIRST: No manual refresh needed - Room flows are reactive
        // ContentObserver will trigger MediaStore sync automatically when media changes
    }

    if (permissionsState.allPermissionsGranted) {
        PhotosContent(
            pagedMedia = pagedMedia,
            isLoading = isLoading,
            sortMode = sortMode,
            onSortModeChanged = { viewModel.setSortMode(it) },
            viewModel = viewModel,
            onNavigateToSettings = onNavigateToSettings
        )
    } else {
        PermissionRequestScreen(
            onRequestPermission = { permissionsState.launchMultiplePermissionRequest() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PhotosContent(
    pagedMedia: androidx.paging.compose.LazyPagingItems<MediaGridItem>,
    isLoading: Boolean,
    sortMode: SortMode,
    onSortModeChanged: (SortMode) -> Unit,
    viewModel: MediaViewModel,
    onNavigateToSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val settingsDataStore = remember { com.prantiux.pixelgallery.data.SettingsDataStore(context) }
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedItems by viewModel.selectedItems.collectAsState()
    val scrollbarVisible by viewModel.scrollbarVisible.collectAsState()
    val scrollbarMonth by viewModel.scrollbarMonth.collectAsState()
    val gridType by viewModel.gridType.collectAsState()
    val pinchGestureEnabled by viewModel.pinchGestureEnabled.collectAsState()
    val cornerType by settingsDataStore.cornerTypeFlow.collectAsState(initial = "Rounded")
    val badgeType by settingsDataStore.badgeTypeFlow.collectAsState(initial = "Duration with icon")
    val badgeEnabled by settingsDataStore.showBadgeFlow.collectAsState(initial = true)
    val thumbnailQuality by settingsDataStore.thumbnailQualityFlow.collectAsState(initial = "Standard")
    val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    var contentTopPx by remember { mutableStateOf<Float?>(null) }
    var firstHeaderTopPx by remember { mutableStateOf<Float?>(null) }
    
    // Pinch gesture state
    var cumulativeScale by remember { mutableStateOf(1f) }
    var gestureInProgress by remember { mutableStateOf(false) }
    var totalPanDistance by remember { mutableStateOf(0f) }
    
    // Log initial gesture state
    // Removed verbose gesture logs
    
    // Transformable state for pinch gesture detection
    @Suppress("DEPRECATION")
    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        if (pinchGestureEnabled && !isSelectionMode) {
            // Track pan distance to distinguish pinch from scroll
            val panMagnitude = kotlin.math.sqrt(panChange.x * panChange.x + panChange.y * panChange.y)
            totalPanDistance += panMagnitude
            
            cumulativeScale *= zoomChange
            gestureInProgress = true
        }
    }
    
    // Handle grid type change based on pinch gesture
    LaunchedEffect(gestureInProgress, cumulativeScale, totalPanDistance) {
        if (gestureInProgress && pinchGestureEnabled) {
            // Only trigger if pan distance is minimal (< 100px) - meaning it's a pinch, not a scroll
            val isPinchGesture = totalPanDistance < 100f
            
            if (isPinchGesture) {
                // Zoom out (scale < 0.8) on Day view -> Switch to Month view
                if (cumulativeScale < 0.8f && gridType == com.prantiux.pixelgallery.viewmodel.GridType.DAY) {
                    viewModel.setGridType(com.prantiux.pixelgallery.viewmodel.GridType.MONTH)
                    cumulativeScale = 1f
                    gestureInProgress = false
                    totalPanDistance = 0f
                }
                // Zoom in (scale > 1.2) on Month view -> Switch to Day view
                else if (cumulativeScale > 1.2f && gridType == com.prantiux.pixelgallery.viewmodel.GridType.MONTH) {
                    viewModel.setGridType(com.prantiux.pixelgallery.viewmodel.GridType.DAY)
                    cumulativeScale = 1f
                    gestureInProgress = false
                    totalPanDistance = 0f
                }
            }
        }
    }
    
    // Reset scale when touch is released
    LaunchedEffect(transformableState.isTransformInProgress) {
        if (!transformableState.isTransformInProgress && gestureInProgress) {
            cumulativeScale = 1f
            gestureInProgress = false
            totalPanDistance = 0f
        }
    }
    

    
    // Scrollbar state for overlay
    var scrollbarOverlayText by remember { mutableStateOf("") }
    var showScrollbarOverlay by remember { mutableStateOf(false) }
    
    // Track if initial load is complete - only show loader on FIRST load after permission grant
    // Initialize based on whether we already have data
    var hasLoadedOnce by remember { mutableStateOf(pagedMedia.itemCount > 0) }
    var previousIsLoading by remember { mutableStateOf(isLoading) }
    
    LaunchedEffect(isLoading) {
        // Only set hasLoadedOnce when isLoading transitions from true -> false
        // AND we have media items
        // AND hasLoadedOnce is still false
        if (previousIsLoading && !isLoading && !hasLoadedOnce) {
            if (pagedMedia.itemCount > 0) {
                hasLoadedOnce = true
            }
        }
        
        // Update previous state for next comparison
        previousIsLoading = isLoading
    }
    
    // Show loading when: loading AND (never loaded before OR list is empty)
    val showLoading = isLoading && pagedMedia.itemCount == 0
    
    // Create index map for O(1) lookups instead of O(n) indexOf()
    // Note: With paging, a simple map is not sufficient without full list observation,
    // which is not how Paging3 works. The indexMap is omitted here as it's not feasible.

    // Determine column count based on grid type
    val columnCount = when (gridType) {
        com.prantiux.pixelgallery.viewmodel.GridType.DAY -> 3
        com.prantiux.pixelgallery.viewmodel.GridType.MONTH -> 5
    }
    
    // Remember scroll state to preserve position
    val gridState = rememberLazyGridState()

    
    // Calculate scroll progress for expandable app bar
    val scrollProgress = remember {
        derivedStateOf {
            com.prantiux.pixelgallery.ui.components.calculateScrollProgress(
                firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = gridState.firstVisibleItemScrollOffset,
                collapseThreshold = 150
            )
        }
    }
    
    // Snap animation for header - same behavior as SubPageScaffold
    val snappedScrollProgress = remember { Animatable(0f) }
    
    // Track scrolling state to detect when scroll stops
    LaunchedEffect(gridState.isScrollInProgress, scrollProgress.value) {
        if (gridState.isScrollInProgress) {
            // While scrolling, snap immediately to follow scroll position
            snappedScrollProgress.snapTo(scrollProgress.value)
        } else {
            // When scroll stops, animate to nearest state (expanded or collapsed)
            val targetProgress = if (scrollProgress.value < 0.5f) 0f else 1f
            snappedScrollProgress.animateTo(
                targetValue = targetProgress,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }
    
    // Calculate navbar height for proper content padding
    val navBarHeight = calculateFloatingNavBarHeight()
    
    // Get view for haptic feedback
    val view = LocalView.current
    
    // Prepare date group info for scrollbar
    val dateGroupsForScrollbar by viewModel.photosDateGroups.collectAsState()
    
    // Don't auto-show scrollbar on scroll - only when dragging
    // Remove the auto-hide LaunchedEffect

    // Add BackHandler to exit selection mode on back press
    BackHandler(enabled = isSelectionMode) {
        viewModel.exitSelectionMode()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Expandable Top App Bar - sticky header
        com.prantiux.pixelgallery.ui.components.ExpandableTopAppBar(
            title = "Photos",
            scrollProgress = snappedScrollProgress.value,
            onSettingsClick = onNavigateToSettings,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Content area with grid
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color = MaterialTheme.colorScheme.surface)
                .onGloballyPositioned { coords ->
                    if (contentTopPx == null) {
                        contentTopPx = coords.positionInRoot().y
                    }
                }
        ) {
            // Material 3 Expressive: Show LoadingIndicator ONLY on FIRST load after permission grant
            // Never show on subsequent navigations to prevent UI jank
            // Show loading FIRST, only show "no media" after loading completes
            if (showLoading) {
                com.prantiux.pixelgallery.ui.components.EchoLoadingIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (pagedMedia.itemCount == 0) {
                // Only show "no media" after loading is complete (when not loading)
                Text(
                    "No media found",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                LazyVerticalGrid(
    flingBehavior = rememberZenithFlingBehavior(),
                        columns = GridCells.Fixed(columnCount),
                        state = gridState,
                        modifier = Modifier
                            .fillMaxSize()
                            .transformable(
                                state = transformableState,
                                enabled = pinchGestureEnabled && !isSelectionMode
                            ),
                        contentPadding = PaddingValues(
                            bottom = navBarHeight + 2.dp,
                            start = 2.dp,
                            end = 2.dp,
                            top = 0.dp // No top padding - app bar is above grid now
                        ),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        
                        items(
                            count = pagedMedia.itemCount,
                            key = pagedMedia.itemKey { item ->
                                when (item) {
                                    is MediaGridItem.Header -> "header_${item.dateGroupKey}"
                                    is MediaGridItem.Media -> item.mediaItem.id
                                }
                            },
                            span = { index ->
                                val item = pagedMedia[index]
                                if (item is MediaGridItem.Header) {
                                    GridItemSpan(columnCount)
                                } else {
                                    GridItemSpan(1)
                                }
                            }
                        ) { index ->
                            val item = pagedMedia[index]
                            
                            if (item != null) {
                                when (item) {
                                    is MediaGridItem.Header -> {
                                        val isDay = gridType == com.prantiux.pixelgallery.viewmodel.GridType.DAY
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 8.dp, top = 28.dp, bottom = 8.dp, end = 8.dp)
                                                .then(
                                                    if (index == 0) {
                                                        Modifier.onGloballyPositioned { coords ->
                                                            if (firstHeaderTopPx == null) {
                                                                firstHeaderTopPx = coords.positionInRoot().y
                                                            }
                                                        }
                                                    } else {
                                                        Modifier
                                                    }
                                                ),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Date text
                                            Text(
                                                text = item.displayDate,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            
                                            // Always reserve space for checkbox to prevent layout shift
                                            Box(
                                                modifier = Modifier.size(24.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSelectionMode) {
                                                    // Show checkbox in selection mode
                                                    Box(
                                                        modifier = Modifier
                                                            .size(24.dp)
                                                            .clip(CircleShape)
                                                            .border(
                                                                width = 2.dp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                shape = CircleShape
                                                            )
                                                            .background(Color.Transparent, CircleShape)
                                                            .clickable {
                                                                viewModel.selectAllInDateGroup(item.dateGroupKey, isDay)
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        // Render empty checkbox that can be clicked to select all
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    is MediaGridItem.Media -> {
                                        val mediaItem = item.mediaItem
                                        val isSelected = selectedItems.contains(mediaItem.id)
                                        val gridShape = com.prantiux.pixelgallery.ui.utils.getGridItemCornerShape(
                                            index = index, // Approximate corner shape for now
                                            totalItems = pagedMedia.itemCount,
                                            columns = columnCount,
                                            cornerType = cornerType
                                        )
                            
                                        MediaThumbnail(
                                            item = mediaItem,
                                            isSelected = isSelected,
                                            isSelectionMode = isSelectionMode,
                                            shape = gridShape,
                                            badgeType = badgeType,
                                            badgeEnabled = badgeEnabled,
                                            thumbnailQuality = thumbnailQuality,
                                            onClick = {
                                                if (isSelectionMode) {
                                                    viewModel.toggleSelection(mediaItem.id)
                                                } else {
                                                    viewModel.showMediaOverlayWithItem(
                                                        mediaType = "photos",
                                                        albumId = "all",
                                                        item = mediaItem
                                                    )
                                                }
                                            },
                                            onLongClick = {
                                                if (!isSelectionMode) {
                                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                                    viewModel.enterSelectionMode(mediaItem.id)
                                                }
                                            },
                                            showFavorite = true
                                        )
                                    }
                                }
                            }
                        }
                }
            }
            
            // Unified Scrollbar Component - overlaid on grid
            com.prantiux.pixelgallery.ui.components.UnifiedScrollbar(
                modifier = Modifier.align(Alignment.TopEnd),
                gridState = gridState,
                mode = com.prantiux.pixelgallery.ui.components.ScrollbarMode.DATE_JUMPING,
                topPadding = if (contentTopPx != null && firstHeaderTopPx != null) {
                    with(density) { (firstHeaderTopPx!! - contentTopPx!!).coerceAtLeast(0f).toDp() }
                } else {
                    0.dp
                },
                dateGroups = dateGroupsForScrollbar,
                coroutineScope = coroutineScope,
                isDarkTheme = isDarkTheme,
                onScrollbarVisibilityChanged = { visible ->
                    viewModel.setScrollbarVisible(visible)
                },
                onOverlayTextChanged = { text ->
                    scrollbarOverlayText = text
                    showScrollbarOverlay = text.isNotEmpty()
                }
            )
            
            // Selection Top Bar - overlay above navigation bar
            val copySuccessMessage by viewModel.copySuccessMessage.collectAsState()
            val moveSuccessMessage by viewModel.moveSuccessMessage.collectAsState()
            val deleteSuccessMessage by viewModel.deleteSuccessMessage.collectAsState()
            com.prantiux.pixelgallery.ui.components.SelectionTopBar(
                isVisible = isSelectionMode || copySuccessMessage != null || moveSuccessMessage != null || deleteSuccessMessage != null,
                selectedCount = selectedItems.size,
                onCancelSelection = { viewModel.exitSelectionMode() },
                successMessage = copySuccessMessage ?: moveSuccessMessage ?: deleteSuccessMessage,
                view = view,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = navBarHeight) // No gap - connects directly to nav bar
            )
        }
    }
}


@Composable
fun SortMenuItem(text: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
        onClick = onClick
    )
}

// Helper function to format video duration
private fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs / 1000) % 60
    val minutes = (durationMs / (1000 * 60)) % 60
    val hours = durationMs / (1000 * 60 * 60)
    
    return when {
        hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, seconds)
        else -> String.format("%d:%02d", minutes, seconds)
    }
}
