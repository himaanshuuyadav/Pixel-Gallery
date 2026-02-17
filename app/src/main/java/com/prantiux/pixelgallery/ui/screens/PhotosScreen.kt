package com.prantiux.pixelgallery.ui.screens

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen(
    viewModel: MediaViewModel,
    onNavigateToSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    // ROOM-FIRST: Observe Room flows instead of MediaStore-derived StateFlows
    val images by viewModel.imagesFlow.collectAsState()
    val videos by viewModel.videosFlow.collectAsState()
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

    LaunchedEffect(Unit) {
        val start = SystemClock.elapsedRealtime()
        withFrameNanos { }
        if (BuildConfig.DEBUG) {
            Log.d("Perf", "First frame rendered in ${SystemClock.elapsedRealtime() - start} ms")
        }
        // ROOM-FIRST: No manual refresh needed - Room flows are reactive
        // ContentObserver will trigger MediaStore sync automatically when media changes
        if (permissionsState.allPermissionsGranted && viewModel.isMediaEmpty()) {
            viewModel.refresh(context)
        }
    }

    if (permissionsState.allPermissionsGranted) {
        PhotosContent(
            images = images,
            videos = videos,
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
    images: List<MediaItem>,
    videos: List<MediaItem>,
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
    
    // Pinch gesture state
    var cumulativeScale by remember { mutableStateOf(1f) }
    var gestureInProgress by remember { mutableStateOf(false) }
    var totalPanDistance by remember { mutableStateOf(0f) }
    
    // Log initial gesture state
    // Removed verbose gesture logs
    
    // Transformable state for pinch gesture detection
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
    var hasLoadedOnce by remember { mutableStateOf(images.isNotEmpty() || videos.isNotEmpty()) }
    var previousIsLoading by remember { mutableStateOf(isLoading) }
    
    LaunchedEffect(isLoading) {
        // Only set hasLoadedOnce when isLoading transitions from true -> false
        // AND we have media items
        // AND hasLoadedOnce is still false
        if (previousIsLoading && !isLoading && !hasLoadedOnce) {
            val totalMediaCount = images.size + videos.size
            if (totalMediaCount > 0) {
                hasLoadedOnce = true
            }
        }
        
        // Update previous state for next comparison
        previousIsLoading = isLoading
    }
    
    // Show loading when: loading AND (never loaded before OR lists are empty)
    val showLoading = isLoading && (!hasLoadedOnce || (images.isEmpty() && videos.isEmpty()))
    
    // ROOM-FIRST: Use Room flows (single source of truth, computed once on IO/Default thread)
    // Use filteredMediaFlow to apply hidden album filtering
    val sortedMediaList by viewModel.filteredMediaFlow.collectAsState()
    val groupedMedia by viewModel.groupedMediaFlow.collectAsState()
    
    // CALLING TAB LOG
    android.util.Log.d("SCREEN_TAB", "PhotosScreen collected ${sortedMediaList.size} items from filtered media")
    
    // Create index map for O(1) lookups instead of O(n) indexOf()
    val indexMap = remember(sortedMediaList) {
        sortedMediaList.mapIndexed { i, item -> item.id to i }.toMap()
    }
    
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
    
    // Calculate navbar height for proper content padding
    val navBarHeight = calculateFloatingNavBarHeight()
    
    // Get view for haptic feedback
    val view = LocalView.current
    
    // Prepare date group info for scrollbar
    val dateGroupsForScrollbar = remember(groupedMedia) {
        groupedMedia.map { group ->
            com.prantiux.pixelgallery.ui.components.DateGroupInfo(
                date = group.date,
                displayDate = group.displayDate,
                itemCount = group.items.size
            )
        }
    }
    
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
            scrollProgress = scrollProgress.value,
            onSettingsClick = onNavigateToSettings,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Content area with grid
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color = MaterialTheme.colorScheme.surface)
        ) {
            // Material 3 Expressive: Show LoadingIndicator ONLY on FIRST load after permission grant
            // Never show on subsequent navigations to prevent UI jank
            // Show loading FIRST, only show "no media" after loading completes
            if (showLoading) {
                com.prantiux.pixelgallery.ui.components.ExpressiveLoadingIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    size = 48.dp
                )
            } else if (sortedMediaList.isEmpty()) {
                // Only show "no media" after loading is complete (when not loading)
                Text(
                    "No media found",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                LazyVerticalGrid(
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
                        
                        groupedMedia.forEach { group ->
                        // Date Header - spans all columns with checkbox when in selection mode
                        item(span = { GridItemSpan(columnCount) }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 8.dp, top = 28.dp, bottom = 8.dp, end = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Date text
                                Text(
                                    text = group.displayDate,
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
                                        val allSelected = group.items.all { selectedItems.contains(it) }
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .border(
                                                    width = 2.dp,
                                                    color = if (allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    shape = CircleShape
                                                )
                                                .background(
                                                    if (allSelected) MaterialTheme.colorScheme.primary.copy(alpha = 1.0f)  else Color.Transparent,
                                                    CircleShape
                                                )
                                                .clickable {
                                                    if (allSelected) {
                                                        viewModel.deselectAllInGroup(group.items)
                                                    } else {
                                                        viewModel.selectAllInGroup(group.items)
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (allSelected) {
                                                FontIcon(
                                                    unicode = FontIcons.Done,
                                                    contentDescription = "Selected",
                                                    size = 16.sp,
                                                    tint = MaterialTheme.colorScheme.onPrimary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Media items for this date - with stable keys for optimal recomposition
                        items(
                            count = group.items.size,
                            key = { index -> group.items[index].id }  // CRITICAL: Stable key prevents unnecessary recomposition
                        ) { index ->
                            val item = group.items[index]
                            val globalIndex = indexMap[item.id] ?: 0  // O(1) lookup instead of O(n) indexOf
                            val isSelected = selectedItems.contains(item)
                            val gridShape = com.prantiux.pixelgallery.ui.utils.getGridItemCornerShape(
                                index = index,
                                totalItems = group.items.size,
                                columns = columnCount,
                                cornerType = cornerType
                            )
                            
                            MediaThumbnail(
                                item = item,
                                isSelected = isSelected,
                                isSelectionMode = isSelectionMode,
                                shape = gridShape,
                                badgeType = badgeType,
                                badgeEnabled = badgeEnabled,
                                thumbnailQuality = thumbnailQuality,
                                onClick = { bounds ->
                                    if (isSelectionMode) {
                                        viewModel.toggleSelection(item)
                                    } else {
                                        val thumbnailBounds = bounds?.let {
                                            com.prantiux.pixelgallery.ui.animation.SharedElementBounds(
                                                left = it.left,
                                                top = it.top,
                                                width = it.width,
                                                height = it.height
                                            )
                                        }
                                        viewModel.showMediaOverlay(
                                            mediaType = "photos",
                                            albumId = "all",
                                            selectedIndex = globalIndex,
                                            thumbnailBounds = thumbnailBounds
                                        )
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        viewModel.enterSelectionMode(item)
                                    }
                                },
                                showFavorite = true
                            )
                        }
                    }
                }
            }
            
            // Unified Scrollbar Component - overlaid on grid
            com.prantiux.pixelgallery.ui.components.UnifiedScrollbar(
                modifier = Modifier.align(Alignment.TopEnd),
                gridState = gridState,
                mode = com.prantiux.pixelgallery.ui.components.ScrollbarMode.DATE_JUMPING,
                topPadding = 0.dp, // No offset needed since app bar is above
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
