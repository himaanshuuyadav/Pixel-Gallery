package com.prantiux.pixelgallery.ui.screens

import com.prantiux.pixelgallery.ui.utils.rememberZenithFlingBehavior
import android.Manifest
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.BackHandler

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
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.prantiux.pixelgallery.ui.icons.FontIcons
import com.prantiux.pixelgallery.viewmodel.MediaViewModel
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import com.prantiux.pixelgallery.ui.components.MediaThumbnail
import com.prantiux.pixelgallery.viewmodel.SortMode
import com.prantiux.pixelgallery.ui.components.SelectionTopBar
import com.prantiux.pixelgallery.ui.components.UnifiedScrollbar
import com.prantiux.pixelgallery.ui.components.PremiumEmptyState
import com.prantiux.pixelgallery.ui.components.ConsistentHeader
import com.prantiux.pixelgallery.ui.components.PermissionRequestScreen
import com.prantiux.pixelgallery.ui.components.SelectableMediaItem
import com.prantiux.pixelgallery.ui.utils.calculateFloatingNavBarHeight
import com.prantiux.pixelgallery.ui.icons.FontIcon
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
    val fullySelectedDateGroups by viewModel.fullySelectedDateGroups.collectAsState()
    val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val coroutineScope = rememberCoroutineScope()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
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
            val isPinchGesture = totalPanDistance < 100f
            
            if (isPinchGesture) {
                // Zoom out (pinch in) -> go to a smaller/more-column grid
                if (cumulativeScale < 0.8f) {
                    val next = when (gridType) {
                        com.prantiux.pixelgallery.viewmodel.GridType.DAY_3 -> com.prantiux.pixelgallery.viewmodel.GridType.DAY_4
                        com.prantiux.pixelgallery.viewmodel.GridType.DAY_4 -> com.prantiux.pixelgallery.viewmodel.GridType.MONTH_6
                        com.prantiux.pixelgallery.viewmodel.GridType.MONTH_6 -> com.prantiux.pixelgallery.viewmodel.GridType.MONTH_9
                        com.prantiux.pixelgallery.viewmodel.GridType.MONTH_9 -> null
                    }
                    if (next != null) {
                        viewModel.setGridType(next)
                        cumulativeScale = 1f
                        gestureInProgress = false
                        totalPanDistance = 0f
                    }
                }
                // Zoom in (spread) -> go to a larger/fewer-column grid
                else if (cumulativeScale > 1.2f) {
                    val prev = when (gridType) {
                        com.prantiux.pixelgallery.viewmodel.GridType.MONTH_9 -> com.prantiux.pixelgallery.viewmodel.GridType.MONTH_6
                        com.prantiux.pixelgallery.viewmodel.GridType.MONTH_6 -> com.prantiux.pixelgallery.viewmodel.GridType.DAY_4
                        com.prantiux.pixelgallery.viewmodel.GridType.DAY_4 -> com.prantiux.pixelgallery.viewmodel.GridType.DAY_3
                        com.prantiux.pixelgallery.viewmodel.GridType.DAY_3 -> null
                    }
                    if (prev != null) {
                        viewModel.setGridType(prev)
                        cumulativeScale = 1f
                        gestureInProgress = false
                        totalPanDistance = 0f
                    }
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
        com.prantiux.pixelgallery.viewmodel.GridType.DAY_3 -> 3
        com.prantiux.pixelgallery.viewmodel.GridType.DAY_4 -> 4
        com.prantiux.pixelgallery.viewmodel.GridType.MONTH_6 -> 6
        com.prantiux.pixelgallery.viewmodel.GridType.MONTH_9 -> 9
    }
    
    // Remember scroll state to preserve position
    val gridState = rememberLazyGridState()
    
    // Track last selected item during drag-to-select
    var lastSelectedKey by remember { mutableStateOf<Any?>(null) }
    
    // Calculate Sticky Header State
    val topInsetPx = with(density) { WindowInsets.statusBars.getTop(density).toFloat() }.toInt()
    // Make the header stick 32dp higher so it is closer to the status bar (matching top padding)
    val stickyThresholdPx = topInsetPx - with(density) { 32.dp.toPx() }.toInt()
    
    val stickyHeaderInfo by remember(gridState, pagedMedia.itemCount, topInsetPx, stickyThresholdPx, columnCount) {
        derivedStateOf {
            val visibleItems = gridState.layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@derivedStateOf null
            
            // The item just below the threshold
            val topItem = visibleItems.firstOrNull { it.offset.y + it.size.height >= stickyThresholdPx } ?: visibleItems.first()
            if (topItem.index == 0) return@derivedStateOf null // PhotosTabHeader is visible, no sticky needed
            
            var activeHeader: MediaGridItem.Header? = null
            var activeHeaderIndex = -1
            var i = topItem.index
            while (i > 0) {
                val pagedIndex = i - 1
                val item = if (pagedIndex < pagedMedia.itemCount) pagedMedia.peek(pagedIndex) else null
                if (item is MediaGridItem.Header) {
                    val visibleHeader = visibleItems.find { it.index == i }
                    if (visibleHeader != null && visibleHeader.offset.y > stickyThresholdPx) {
                        // This header hasn't reached the threshold yet. Keep scanning backwards.
                    } else {
                        activeHeader = item
                        activeHeaderIndex = i
                        break
                    }
                }
                i--
            }
            
            if (activeHeader != null && activeHeaderIndex != -1) {
                // Check if this date has <= columnCount items (1 row)
                var isSingleRow = false
                val startCheckIndex = activeHeaderIndex + 1
                for (j in 0..columnCount) {
                    val checkIndex = startCheckIndex + j
                    if (checkIndex - 1 < pagedMedia.itemCount) {
                        val checkItem = pagedMedia.peek(checkIndex - 1)
                        if (checkItem is MediaGridItem.Header) {
                            isSingleRow = true
                            break
                        }
                    } else {
                        isSingleRow = true
                        break
                    }
                }
                
                if (isSingleRow) return@derivedStateOf null
            }
            
            var pushUpOffset = 0
            if (activeHeader != null) {
                val nextVisibleHeader = visibleItems.firstOrNull { 
                    it.index > 0 && it.offset.y > stickyThresholdPx && 
                    (if (it.index - 1 < pagedMedia.itemCount) pagedMedia.peek(it.index - 1) else null) is MediaGridItem.Header
                }
                if (nextVisibleHeader != null) {
                    val headerHeight = nextVisibleHeader.size.height
                    // Add roughly 75% of a grid item's height (e.g. 75dp) to the threshold.
                    // This makes the sticky header start pushing up when it covers 25% of the last row.
                    val extraThreshold = (75f * density.density).toInt()
                    val threshold = stickyThresholdPx + headerHeight + extraThreshold
                    if (nextVisibleHeader.offset.y <= threshold) {
                        pushUpOffset = nextVisibleHeader.offset.y - threshold
                    }
                }
            }
            
            if (activeHeader == null) null else Pair(activeHeader, pushUpOffset)
        }
    }
    
    val activeStickyHeaderKey by remember {
        derivedStateOf { stickyHeaderInfo?.first?.dateGroupKey }
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
                PremiumEmptyState(
                    icon = FontIcons.Image,
                    title = "No media found",
                    subtitle = "Your photos and videos\nwill appear here.",
                    modifier = Modifier.align(Alignment.Center)
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
                            )
                            .let {
                                val currentIsSelectionMode by rememberUpdatedState(isSelectionMode)
                                val currentSelectedItems by rememberUpdatedState(selectedItems)
                                val autoScrollSpeed = remember { mutableFloatStateOf(0f) }
                                LaunchedEffect(Unit) {
                                    while (isActive) {
                                        if (autoScrollSpeed.floatValue != 0f) {
                                            gridState.scrollBy(autoScrollSpeed.floatValue)
                                        }
                                        delay(10)
                                    }
                                }
                                it.pointerInput(Unit) {
                                    var initialDragIndex: Int? = null
                                    var currentDragIndex: Int? = null
                                    var dragSelectState = true
                                    var initialSelectedIds = setOf<Long>()
                                    
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { offset ->
                                            val item = gridState.layoutInfo.visibleItemsInfo.firstOrNull { 
                                                offset.y >= it.offset.y && offset.y <= it.offset.y + it.size.height &&
                                                offset.x >= it.offset.x && offset.x <= it.offset.x + it.size.width
                                            }
                                            if (item != null) {
                                                val key = item.key
                                                if (key is Long) {
                                                    initialSelectedIds = currentSelectedItems.toSet()
                                                    if (!currentIsSelectionMode) {
                                                        viewModel.enterSelectionMode(key)
                                                        dragSelectState = true
                                                        initialSelectedIds = setOf(key)
                                                    } else {
                                                        val isSelected = initialSelectedIds.contains(key)
                                                        dragSelectState = !isSelected
                                                        val newSelection = initialSelectedIds.toMutableSet()
                                                        if (dragSelectState) newSelection.add(key) else newSelection.remove(key)
                                                        viewModel.setSelectedItems(newSelection)
                                                    }
                                                    initialDragIndex = item.index
                                                    currentDragIndex = item.index
                                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                }
                                            }
                                        },
                                        onDrag = { change, _ ->
                                            val raw = change.position
                                            if (initialDragIndex != null) {
                                                val distB = gridState.layoutInfo.viewportSize.height - raw.y
                                                val distT = raw.y
                                                autoScrollSpeed.floatValue = when {
                                                    distB < 150f -> 150f - distB
                                                    distT < 150f -> -(150f - distT)
                                                    else -> 0f
                                                }

                                                val item = gridState.layoutInfo.visibleItemsInfo.firstOrNull { 
                                                    raw.y >= it.offset.y && raw.y <= it.offset.y + it.size.height &&
                                                    raw.x >= it.offset.x && raw.x <= it.offset.x + it.size.width
                                                }
                                                if (item != null) {
                                                    val newIdx = item.index
                                                    if (newIdx != currentDragIndex) {
                                                        val start = initialDragIndex!!
                                                        val activeRange = if (newIdx >= start) start..newIdx else newIdx..start
                                                        
                                                        val activeIds = activeRange.mapNotNull { i -> 
                                                            if (i >= 0 && i < pagedMedia.itemCount) {
                                                                val itItem = pagedMedia.peek(i)
                                                                if (itItem is com.prantiux.pixelgallery.model.MediaGridItem.Media) itItem.mediaItem.id else null
                                                            } else null
                                                        }.toSet()
                                                        
                                                        val newSelection = initialSelectedIds.toMutableSet()
                                                        if (dragSelectState) {
                                                            newSelection.addAll(activeIds)
                                                        } else {
                                                            newSelection.removeAll(activeIds)
                                                        }
                                                        
                                                        viewModel.setSelectedItems(newSelection)
                                                        currentDragIndex = newIdx
                                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                                    }
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            initialDragIndex = null
                                            currentDragIndex = null
                                            autoScrollSpeed.floatValue = 0f
                                        },
                                        onDragCancel = {
                                            initialDragIndex = null
                                            currentDragIndex = null
                                            autoScrollSpeed.floatValue = 0f
                                        }
                                    )
                                }
                            },
                        contentPadding = PaddingValues(
                            bottom = navBarHeight + 2.dp,
                            start = 2.dp,
                            end = 2.dp,
                            top = 0.dp // No top padding - app bar is above grid now
                        ),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            com.prantiux.pixelgallery.ui.components.PhotosTabHeader(
                                title = "Photos",
                                onSettingsClick = onNavigateToSettings,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

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
                                        val isDay = gridType.isDay
                                        val isCurrentlySticky = stickyHeaderInfo?.first?.dateGroupKey == item.dateGroupKey
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 8.dp, top = 32.dp, bottom = 8.dp, end = 8.dp)
                                                .alpha(if (isCurrentlySticky) 0f else 1f)
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
                                                    val isFullySelected = fullySelectedDateGroups.contains(item.dateGroupKey)
                                                    com.prantiux.pixelgallery.ui.components.SelectionCheckmark(
                                                        isSelected = isFullySelected,
                                                        modifier = Modifier
                                                            .clickable {
                                                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                                viewModel.selectAllInDateGroup(item.dateGroupKey, isDay)
                                                            }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    is MediaGridItem.Media -> {
                                        val mediaItem = item.mediaItem
                                        val isSelected = selectedItems.contains(mediaItem.id)
                                        val localPos = com.prantiux.pixelgallery.ui.utils.getLocalPositionInDateGroup(
                                            globalIndex = index,
                                            dateGroups = dateGroupsForScrollbar,
                                            contentOffsetIndex = 0
                                        )
                                        val (accentR, defaultR) = com.prantiux.pixelgallery.ui.utils.cornerRadiiForGridType(gridType)
                                        val gridShape = if (localPos != null) {
                                            com.prantiux.pixelgallery.ui.utils.getGridItemCornerShape(
                                                index = localPos.first,
                                                totalItems = localPos.second,
                                                columns = columnCount,
                                                accentRadius = accentR,
                                                defaultRadius = defaultR,
                                                cornerType = cornerType
                                            )
                                        } else {
                                            com.prantiux.pixelgallery.ui.utils.getGridItemCornerShape(
                                                index = 0,
                                                totalItems = 1,
                                                columns = columnCount,
                                                accentRadius = accentR,
                                                defaultRadius = defaultR,
                                                cornerType = cornerType
                                            )
                                        }
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
                                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                                    viewModel.toggleSelection(mediaItem.id)
                                                } else {
                                                    viewModel.showMediaOverlayWithItem(
                                                        mediaType = "photos",
                                                        albumId = "all",
                                                        item = mediaItem
                                                    )
                                                }
                                            },
                                            onLongClick = null,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                }
            }
            
            // Top Status Bar Gradient (drawn under the sticky header)
            com.prantiux.pixelgallery.ui.components.TopStatusBarGradient(
                modifier = Modifier.align(Alignment.TopCenter)
            )
            
            // Sticky Header Overlay
            val currentStickyHeaderInfo = stickyHeaderInfo
            if (currentStickyHeaderInfo != null) {
                val (header, pushUpOffset) = currentStickyHeaderInfo
                val totalOffsetY = stickyThresholdPx + pushUpOffset
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { androidx.compose.ui.unit.IntOffset(0, totalOffsetY) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 10.dp, top = 32.dp, bottom = 8.dp, end = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = header.displayDate,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Box(
                            modifier = Modifier.size(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelectionMode) {
                                val isFullySelected = fullySelectedDateGroups.contains(header.dateGroupKey)
                                com.prantiux.pixelgallery.ui.components.SelectionCheckmark(
                                    isSelected = isFullySelected,
                                    modifier = Modifier
                                        .clickable {
                                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                            val isDay = gridType.isDay
                                            viewModel.selectAllInDateGroup(header.dateGroupKey, isDay)
                                        }
                                )
                            }
                        }
                    }
                }
            }
            
            // Unified Scrollbar Component - overlaid on grid
            com.prantiux.pixelgallery.ui.components.UnifiedScrollbar(
                modifier = Modifier.align(Alignment.TopEnd),
                gridState = gridState,
                targetScrollOffset = -stickyThresholdPx,
                contentOffsetIndex = 1,
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
