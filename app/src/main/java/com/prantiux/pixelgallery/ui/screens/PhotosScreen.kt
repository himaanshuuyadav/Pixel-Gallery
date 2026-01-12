package com.prantiux.pixelgallery.ui.screens

import android.Manifest
import android.os.Build
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.min
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.prantiux.pixelgallery.R
import com.prantiux.pixelgallery.model.MediaItem
import com.prantiux.pixelgallery.viewmodel.MediaViewModel
import com.prantiux.pixelgallery.viewmodel.SortMode
import com.prantiux.pixelgallery.ui.components.ConsistentHeader
import com.prantiux.pixelgallery.ui.components.PermissionRequestScreen
import com.prantiux.pixelgallery.ui.components.SelectableMediaItem
import com.prantiux.pixelgallery.ui.utils.calculateFloatingNavBarHeight
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

data class MediaGroup(
    val date: String,
    val displayDate: String,
    val items: List<MediaItem>
)

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen(
    viewModel: MediaViewModel
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
            viewModel = viewModel
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
    viewModel: MediaViewModel
) {
    val context = LocalContext.current
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedItems by viewModel.selectedItems.collectAsState()
    val scrollbarVisible by viewModel.scrollbarVisible.collectAsState()
    val scrollbarMonth by viewModel.scrollbarMonth.collectAsState()
    val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val coroutineScope = rememberCoroutineScope()
    
    // Combine images and videos
    val allMedia = remember(images, videos) {
        (images + videos).sortedByDescending { it.dateAdded }
    }
    
    // Group by date
    val groupedMedia = remember(allMedia) {
        groupMediaByDate(allMedia)
    }
    
    // Remember scroll state to preserve position
    val gridState = rememberLazyGridState()
    
    // Calculate navbar height for proper content padding
    val navBarHeight = calculateFloatingNavBarHeight()
    
    // Track scrollbar drag with separate state for drag offset
    var isDraggingScrollbar by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(0f) }
    var lastSnappedDate by remember { mutableStateOf("") }
    
    // Get view for haptic feedback
    val view = LocalView.current
    
    // Don't auto-show scrollbar on scroll - only when dragging
    // Remove the auto-hide LaunchedEffect

    // Add BackHandler to exit selection mode on back press
    BackHandler(enabled = isSelectionMode) {
        viewModel.exitSelectionMode()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header - use same header component, just change title and add close button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                com.prantiux.pixelgallery.ui.components.MainTabHeader(
                    title = if (isSelectionMode) "${selectedItems.size} selected" else "Gallery",
                    actions = if (isSelectionMode) {
                        {
                            IconButton(onClick = { viewModel.exitSelectionMode() }) {
                                FontIcon(
                                    unicode = FontIcons.Close,
                                    contentDescription = "Close selection",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    } else null
                )
            }
            
            // Gallery content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (allMedia.isEmpty()) {
                    Text(
                        "No media found",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            bottom = navBarHeight + 2.dp,
                            start = 2.dp,
                            end = 2.dp,
                            top = 16.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        groupedMedia.forEach { group ->
                        // Date Header - spans all columns with checkbox when in selection mode
                        item(span = { GridItemSpan(3) }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 8.dp, top = 32.dp, bottom = 8.dp, end = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = group.displayDate,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                
                                if (isSelectionMode) {
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
                                                if (allSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
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
                                                tint = Color.White
                                            )
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
                            var thumbnailBounds by remember { mutableStateOf<Rect?>(null) }
                            val borderWidth = 16.dp
                            val gridShape = com.prantiux.pixelgallery.ui.utils.getGridItemCornerShape(
                                index = index,
                                totalItems = group.items.size,
                                columns = 3
                            )
                            // Much darker version of accent color for border
                            val borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .then(
                                        if (isSelected) {
                                            Modifier
                                                .background(
                                                    color = borderColor,
                                                    shape = gridShape
                                                )
                                                .border(
                                                    width = borderWidth,
                                                    color = borderColor,
                                                    shape = gridShape
                                                )
                                        } else Modifier
                                    )
                            ) {
                                AsyncImage(
                                    model = item.uri,
                                    contentDescription = item.displayName,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .then(
                                            if (isSelected) {
                                                Modifier.padding(borderWidth)
                                            } else Modifier
                                        )
                                        .clip(gridShape)
                                        .onGloballyPositioned { coordinates ->
                                            val position = coordinates.positionInWindow()
                                            val size = coordinates.size
                                            thumbnailBounds = Rect(
                                                position.x,
                                                position.y,
                                                position.x + size.width,
                                                position.y + size.height
                                            )
                                        }
                                        .combinedClickable(
                                            onClick = {
                                                if (isSelectionMode) {
                                                    viewModel.toggleSelection(item)
                                                } else {
                                                    thumbnailBounds?.let { bounds ->
                                                        viewModel.showMediaOverlay(
                                                            mediaType = "photos",
                                                            albumId = "all",
                                                            selectedIndex = globalIndex,
                                                            thumbnailBounds = MediaViewModel.ThumbnailBounds(
                                                                startLeft = bounds.left,
                                                                startTop = bounds.top,
                                                                startWidth = bounds.width,
                                                                startHeight = bounds.height
                                                            )
                                                        )
                                                    } ?: run {
                                                        // Fallback without bounds
                                                        viewModel.showMediaOverlay(
                                                            mediaType = "photos",
                                                            albumId = "all",
                                                            selectedIndex = globalIndex,
                                                            thumbnailBounds = null
                                                        )
                                                    }
                                                }
                                            },
                                            onLongClick = {
                                                if (!isSelectionMode) {
                                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                                    viewModel.enterSelectionMode(item)
                                                }
                                            }
                                        ),
                                    contentScale = ContentScale.Crop
                                )
                                
                                // Video indicator - pill-shaped with duration (always at bottom right)
                                if (item.isVideo) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(if (isSelected) borderWidth + 6.dp else 6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .background(
                                                    color = Color.Black.copy(alpha = 0.75f),
                                                    shape = RoundedCornerShape(50) // Pill shape
                                                )
                                                .padding(horizontal = 6.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            FontIcon(
                                                unicode = FontIcons.PlayArrow,
                                                contentDescription = "Video",
                                                size = 14.sp,
                                                tint = Color.White
                                            )
                                            Text(
                                                text = formatDuration(item.duration),
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            )
                                        }
                                    }
                                }
                                
                                // Favorite indicator (gold star at top-left)
                                if (item.isFavorite && !isSelectionMode) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(if (isSelected) borderWidth + 6.dp else 6.dp)
                                    ) {
                                        FontIcon(
                                            unicode = FontIcons.Star,
                                            contentDescription = "Favorited",
                                            size = 16.sp,
                                            tint = Color(0xFFFFD700)
                                        )
                                    }
                                }
                                
                                // Selection indicator with dark accent color (render last so it's on top)
                                if (isSelectionMode) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                            .size(24.dp)
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.7f),
                                                CircleShape
                                            )
                                            .border(
                                                width = if (isSelected) 0.dp else 2.dp,
                                                color = if (isSelected) Color.Transparent else Color.Gray.copy(alpha = 0.5f),
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            FontIcon(
                                                unicode = FontIcons.Done,
                                                contentDescription = "Selected",
                                                size = 16.sp,
                                                tint = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    }
                }
            }
        }
        
        // Scrollbar with month indicator overlay (only when dragging)
        if (scrollbarVisible && isDraggingScrollbar) {
            // Translucent scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isDarkTheme) 
                            Color.Black.copy(alpha = 0.3f)
                        else 
                            Color.White.copy(alpha = 0.3f) 
                    )
            )
            
            // Month/Year indicator in center
            if (scrollbarMonth.isNotEmpty()) {
                Text(
                    text = scrollbarMonth,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        
        // Scrollbar on right side - always have touch target, but visible only when scrolling/dragging
        LaunchedEffect(gridState.isScrollInProgress) {
            if (gridState.isScrollInProgress && !isDraggingScrollbar) {
                viewModel.setScrollbarVisible(true)
            } else if (!gridState.isScrollInProgress && !isDraggingScrollbar) {
                delay(1500) // Increased hiding delay
                viewModel.setScrollbarVisible(false)
            }
        }
        
        // Always render scrollbar container for touch input
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 88.dp + 32.dp)
                .fillMaxHeight()
                .width(40.dp)
                .padding(end = 8.dp)
        ) {
            // Calculate scrollbar position based on scroll state
            val firstVisibleItemIndex = gridState.firstVisibleItemIndex
            val totalItems = groupedMedia.sumOf { it.items.size + 1 }
            val scrollPercentage = if (totalItems > 0) {
                firstVisibleItemIndex.toFloat() / totalItems.toFloat()
            } else 0f
            
            // Scrollbar thumb that moves with scroll position
            androidx.compose.foundation.layout.BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
                // Increase scrollable area to 95% of available height when dragging
                val scrollableRatio = if (isDraggingScrollbar) 0.95f else 0.7f
                val maxOffsetPx = maxHeight * scrollableRatio
                val density = LocalDensity.current
                val scrollHeight = with(density) { maxHeight.toPx() * scrollableRatio }
                
                // Use drag offset when dragging, otherwise use scroll percentage
                val displayOffset = if (isDraggingScrollbar) {
                    dragOffset.coerceIn(0f, scrollHeight)
                } else {
                    with(density) { maxOffsetPx.toPx() } * scrollPercentage
                }
                
                Log.d("Scrollbar", "isDragging: $isDraggingScrollbar, scrollPercentage: $scrollPercentage, dragOffset: $dragOffset, displayOffset: $displayOffset, firstVisibleItem: $firstVisibleItemIndex")
                
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(y = with(density) { displayOffset.toDp() })
                        .width(if (isDraggingScrollbar) 24.dp else 12.dp)
                        .height(80.dp)
                        .alpha(if (scrollbarVisible || isDraggingScrollbar) 1f else 0f)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = if (isDraggingScrollbar) 0.9f else 0.7f),
                            RoundedCornerShape(if (isDraggingScrollbar) 12.dp else 8.dp)
                        )
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    Log.d("Scrollbar", "=== DRAG START ===")
                                    isDraggingScrollbar = true
                                    viewModel.setScrollbarVisible(true)
                                    viewModel.setScrollbarDragging(true)
                                    
                                    // Get current visual position of scrollbar
                                    val currentVisualOffset = with(density) { maxOffsetPx.toPx() } * scrollPercentage
                                    
                                    Log.d("Scrollbar", "Visual offset: $currentVisualOffset, saved dragOffset: $dragOffset")
                                    
                                    // If this is first drag (dragOffset == 0), initialize from current scroll position
                                    // Otherwise, keep the dragOffset from where user left it
                                    if (dragOffset == 0f) {
                                        dragOffset = currentVisualOffset
                                        Log.d("Scrollbar", "First drag - initialized dragOffset to: $dragOffset")
                                    } else {
                                        Log.d("Scrollbar", "Continuing from previous dragOffset: $dragOffset")
                                    }
                                    
                                    // Reset last snapped date
                                    lastSnappedDate = ""
                                },
                                onDragEnd = {
                                    Log.d("Scrollbar", "=== DRAG END ===")
                                    isDraggingScrollbar = false
                                    viewModel.setScrollbarVisible(false)
                                    viewModel.setScrollbarDragging(false)
                                    // DON'T reset dragOffset - keep it so next drag starts from here
                                    Log.d("Scrollbar", "Keeping dragOffset at: $dragOffset for next drag")
                                },
                                onDragCancel = {
                                    Log.d("Scrollbar", "=== DRAG CANCEL ===")
                                    isDraggingScrollbar = false
                                    viewModel.setScrollbarVisible(false)
                                    viewModel.setScrollbarDragging(false)
                                    // DON'T reset dragOffset
                                }
                            ) { change, dragAmount ->
                                change.consume()
                                // Update drag offset
                                val oldDragOffset = dragOffset
                                dragOffset = (dragOffset + dragAmount.y).coerceIn(0f, scrollHeight)
                                val newPercentage = dragOffset / scrollHeight
                                
                                Log.d("Scrollbar", "Drag: amount=${dragAmount.y}, oldOffset=$oldDragOffset, newOffset=$dragOffset, percentage=$newPercentage")
                                
                                // Find the date group to snap to based on percentage
                                var targetGroupIndex = -1
                                var targetDate = ""
                                
                                // If at the very end (>99%), go to last group
                                if (newPercentage >= 0.99f) {
                                    targetGroupIndex = groupedMedia.lastIndex
                                    targetDate = groupedMedia.last().date
                                } else {
                                    // Map percentage directly to group index for smooth distribution
                                    val groupIndex = (newPercentage * groupedMedia.size).toInt().coerceIn(0, groupedMedia.lastIndex)
                                    targetGroupIndex = groupIndex
                                    targetDate = groupedMedia[groupIndex].date
                                }
                                
                                Log.d("Scrollbar", "Calculated target group: $targetGroupIndex of ${groupedMedia.size}, date: $targetDate")
                                
                                // Only scroll and provide haptic if we moved to a different date
                                if (targetGroupIndex >= 0 && targetDate != lastSnappedDate) {
                                    lastSnappedDate = targetDate
                                    
                                    Log.d("Scrollbar", "NEW DATE: $targetDate (group $targetGroupIndex)")
                                    
                                    // Gentle haptic feedback
                                    view.performHapticFeedback(
                                        HapticFeedbackConstants.CLOCK_TICK,
                                        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                                    )
                                    
                                    // Calculate the exact index of the date header
                                    var headerIndex = 0
                                    for (i in 0 until targetGroupIndex) {
                                        headerIndex += groupedMedia[i].items.size + 1
                                    }
                                    
                                    Log.d("Scrollbar", "Scrolling to header index: $headerIndex")
                                    
                                    // Update month display
                                    val group = groupedMedia[targetGroupIndex]
                                    val calendar = Calendar.getInstance()
                                    calendar.timeInMillis = group.items.first().dateAdded * 1000
                                    val monthYear = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(calendar.time)
                                    viewModel.setScrollbarMonth(monthYear)
                                    
                                    // Scroll to the date header
                                    coroutineScope.launch {
                                        gridState.scrollToItem(headerIndex)
                                    }
                                }
                            }
                        }
                )
            }
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
        MediaGroup(date, displayDate, items)
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
