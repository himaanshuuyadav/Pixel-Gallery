package com.prantiux.pixelgallery.ui.screens

import android.Manifest
import android.os.Build
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
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

data class MediaGroup(
    val date: String,
    val displayDate: String,
    val items: List<MediaItem>,
    val mostCommonLocation: String? = null
)

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen(
    viewModel: MediaViewModel,
    onNavigateToSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val images by viewModel.images.collectAsState()
    val videos by viewModel.videos.collectAsState()
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
        if (permissionsState.allPermissionsGranted) {
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
    LaunchedEffect(pinchGestureEnabled, isSelectionMode) {
        Log.d("PhotosGesture", "=== Gesture State ===")
        Log.d("PhotosGesture", "Pinch gesture enabled: $pinchGestureEnabled")
        Log.d("PhotosGesture", "Selection mode: $isSelectionMode")
        Log.d("PhotosGesture", "Grid type: $gridType")
        Log.d("PhotosGesture", "Gesture will work: ${pinchGestureEnabled && !isSelectionMode}")
    }
    
    // Transformable state for pinch gesture detection
    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        if (pinchGestureEnabled && !isSelectionMode) {
            // Track pan distance to distinguish pinch from scroll
            val panMagnitude = kotlin.math.sqrt(panChange.x * panChange.x + panChange.y * panChange.y)
            totalPanDistance += panMagnitude
            
            cumulativeScale *= zoomChange
            gestureInProgress = true
            
            Log.d("PhotosGesture", "Gesture: zoom=$zoomChange, pan=$panMagnitude, totalPan=$totalPanDistance, scale=$cumulativeScale")
        }
    }
    
    // Handle grid type change based on pinch gesture
    LaunchedEffect(gestureInProgress, cumulativeScale, totalPanDistance) {
        if (gestureInProgress && pinchGestureEnabled) {
            // Only trigger if pan distance is minimal (< 100px) - meaning it's a pinch, not a scroll
            val isPinchGesture = totalPanDistance < 100f
            
            Log.d("PhotosGesture", "Checking - scale: $cumulativeScale, pan: $totalPanDistance, isPinch: $isPinchGesture")
            
            if (isPinchGesture) {
                // Zoom out (scale < 0.8) on Day view -> Switch to Month view
                if (cumulativeScale < 0.8f && gridType == com.prantiux.pixelgallery.viewmodel.GridType.DAY) {
                    Log.d("PhotosGesture", "✓ Switching to MONTH view (zoomed out)")
                    viewModel.setGridType(com.prantiux.pixelgallery.viewmodel.GridType.MONTH)
                    cumulativeScale = 1f
                    gestureInProgress = false
                    totalPanDistance = 0f
                }
                // Zoom in (scale > 1.2) on Month view -> Switch to Day view
                else if (cumulativeScale > 1.2f && gridType == com.prantiux.pixelgallery.viewmodel.GridType.MONTH) {
                    Log.d("PhotosGesture", "✓ Switching to DAY view (zoomed in)")
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
            Log.d("PhotosGesture", "Touch released - scale: $cumulativeScale, pan: $totalPanDistance")
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
    
    LaunchedEffect(isLoading, images, videos) {
        Log.d("PhotosScreenLoad", "State change - isLoading: $isLoading, images: ${images.size}, videos: ${videos.size}, hasLoadedOnce: $hasLoadedOnce")
        
        if (images.isNotEmpty() || videos.isNotEmpty()) {
            hasLoadedOnce = true
            Log.d("PhotosScreenLoad", "hasLoadedOnce set to true")
        }
    }
    
    // Show loading when: loading AND (never loaded before OR lists are empty)
    val showLoading = isLoading && (!hasLoadedOnce || (images.isEmpty() && videos.isEmpty()))
    
    Log.d("PhotosScreenLoad", "Final decision - showLoading: $showLoading (isLoading: $isLoading, hasLoadedOnce: $hasLoadedOnce)")
    
    // Combine images and videos
    val allMedia = remember(images, videos) {
        (images + videos).sortedByDescending { it.dateAdded }
    }
    
    // Determine column count based on grid type
    val columnCount = when (gridType) {
        com.prantiux.pixelgallery.viewmodel.GridType.DAY -> 3
        com.prantiux.pixelgallery.viewmodel.GridType.MONTH -> 5
    }
    
    // Group by date or month based on grid type
    val groupedMedia = remember(allMedia, gridType) {
        when (gridType) {
            com.prantiux.pixelgallery.viewmodel.GridType.DAY -> groupMediaByDate(allMedia)
            com.prantiux.pixelgallery.viewmodel.GridType.MONTH -> groupMediaByMonth(allMedia)
        }
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
                Log.d("PhotosScreenLoad", "Showing loading indicator")
                com.prantiux.pixelgallery.ui.components.ExpressiveLoadingIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    size = 48.dp
                )
            } else if (allMedia.isEmpty()) {
                // Only show "no media" after loading is complete (when not loading)
                Log.d("PhotosScreenLoad", "Showing 'No media found' (loading complete, no items)")
                Text(
                    "No media found",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                Log.d("PhotosScreenLoad", "Showing media grid (${allMedia.size} items)")
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
                                        Log.d("PhotosScreen", "Selection mode - checkbox for ${group.displayDate}: allSelected=$allSelected")
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
                        
                        // Media items for this date
                        items(group.items.size) { index ->
                            val item = group.items[index]
                            val globalIndex = allMedia.indexOf(item)
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

fun groupMediaByDate(media: List<MediaItem>): List<MediaGroup> {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val calendar = Calendar.getInstance()
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    
    return media.groupBy { item ->
        calendar.timeInMillis = item.dateAdded * 1000
        dateFormat.format(calendar.time)
    }.map { (date, items) ->
        calendar.timeInMillis = items.first().dateAdded * 1000
        val itemYear = calendar.get(Calendar.YEAR)
        
        val displayDate = when {
            isToday(calendar) -> "Today"
            isYesterday(calendar) -> "Yesterday"
            itemYear == currentYear -> {
                // Same year: "12 Dec"
                SimpleDateFormat("d MMM", Locale.getDefault()).format(calendar.time)
            }
            else -> {
                // Different year: "28 Jan 2024"
                SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(calendar.time)
            }
        }
        
        // Find most common location for this date
        val mostCommonLocation = items
            .mapNotNull { it.location }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
        
        MediaGroup(date, displayDate, items, mostCommonLocation)
    }.sortedByDescending { it.date }
}

fun groupMediaByMonth(media: List<MediaItem>): List<MediaGroup> {
    val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    val calendar = Calendar.getInstance()
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    
    return media.groupBy { item ->
        calendar.timeInMillis = item.dateAdded * 1000
        monthFormat.format(calendar.time)
    }.map { (month, items) ->
        calendar.timeInMillis = items.first().dateAdded * 1000
        val itemYear = calendar.get(Calendar.YEAR)
        
        val displayDate = if (itemYear == currentYear) {
            // Current year: "January"
            SimpleDateFormat("MMMM", Locale.getDefault()).format(calendar.time)
        } else {
            // Different year: "December 2025"
            SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(calendar.time)
        }
        
        // Find most common location for this month
        val mostCommonLocation = items
            .mapNotNull { it.location }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
        
        MediaGroup(month, displayDate, items, mostCommonLocation)
    }.sortedByDescending { it.date }
}

fun isToday(calendar: Calendar): Boolean {
    val today = Calendar.getInstance()
    return calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            calendar.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
}

fun isYesterday(calendar: Calendar): Boolean {
    val yesterday = Calendar.getInstance()
    yesterday.add(Calendar.DAY_OF_YEAR, -1)
    return calendar.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
            calendar.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)
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
