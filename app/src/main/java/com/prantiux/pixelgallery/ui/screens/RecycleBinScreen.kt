package com.prantiux.pixelgallery.ui.screens

import android.Manifest
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Rect
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.prantiux.pixelgallery.model.MediaItem
import com.prantiux.pixelgallery.viewmodel.MediaViewModel
import com.prantiux.pixelgallery.ui.components.ConsistentHeader
import com.prantiux.pixelgallery.ui.components.PermissionRequestScreen
import com.prantiux.pixelgallery.ui.components.MediaThumbnail
import com.prantiux.pixelgallery.ui.utils.calculateFloatingNavBarHeight
import kotlinx.coroutines.launch
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons
import com.prantiux.pixelgallery.navigation.NavItem

data class TrashedGroup(
    val daysLeftRange: String,
    val items: List<MediaItem>,
    val maxDaysLeft: Int
)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RecycleBinScreen(
    viewModel: MediaViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val trashedItems by viewModel.trashedItems.collectAsState()
    val isLoading by viewModel.isLoadingTrash.collectAsState()
    val isSelectionMode by viewModel.isTrashSelectionMode.collectAsState()
    val selectedItems by viewModel.selectedTrashItems.collectAsState()

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
            viewModel.loadTrashedItems(context)
        }
    }
    
    // Back handler for selection mode
    BackHandler(enabled = isSelectionMode) {
        viewModel.exitTrashSelectionMode()
    }

    if (permissionsState.allPermissionsGranted) {
        RecycleBinContent(
            trashedItems = trashedItems,
            isLoading = isLoading,
            isSelectionMode = isSelectionMode,
            selectedItems = selectedItems,
            onNavigateBack = onNavigateBack,
            viewModel = viewModel
        )
    } else {
        PermissionRequestScreen(
            onRequestPermission = { permissionsState.launchMultiplePermissionRequest() }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecycleBinContent(
    trashedItems: List<MediaItem>,
    isLoading: Boolean,
    isSelectionMode: Boolean,
    selectedItems: Set<MediaItem>,
    onNavigateBack: () -> Unit,
    viewModel: MediaViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val navBarHeight = calculateFloatingNavBarHeight()
    val view = LocalView.current
    
    // Group items by days left
    val groupedItems = remember(trashedItems) {
        groupByDaysLeft(trashedItems)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Keep header unchanged - always show ConsistentHeader
            ConsistentHeader(
                title = "Recycle Bin",
                onNavigateBack = onNavigateBack
            )
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (trashedItems.isEmpty()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        FontIcon(
                            unicode = FontIcons.Delete,
                            contentDescription = null,
                            size = 64.sp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Recycle Bin is Empty",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Deleted items will appear here",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
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
                        groupedItems.forEach { group ->
                            // Section header with checkbox for selecting all in category
                            item(span = { GridItemSpan(3) }) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 8.dp, top = 32.dp, bottom = 8.dp, end = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = group.daysLeftRange,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    
                                    // Always reserve space for checkbox to prevent layout shift
                                    Box(
                                        modifier = Modifier.size(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // Show checkbox only in selection mode
                                        if (isSelectionMode) {
                                            val allSelected = group.items.all { selectedItems.contains(it) }
                                            android.util.Log.d("RecycleBinScreen", "Selection mode - checkbox for ${group.daysLeftRange}: allSelected=$allSelected")
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .border(
                                                        width = 2.dp,
                                                        color = if (allSelected) Color.Transparent else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        shape = CircleShape
                                                    )
                                                    .background(
                                                        if (allSelected) MaterialTheme.colorScheme.primary.copy(alpha = 1.0f) else Color.Transparent,
                                                        CircleShape
                                                    )
                                                    .clickable {
                                                        if (allSelected) {
                                                            viewModel.deselectTrashGroup(group.items)
                                                        } else {
                                                            viewModel.selectTrashGroup(group.items)
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
                            
                            // Media items
                            items(group.items.size) { index ->
                                val item = group.items[index]
                                val isSelected = selectedItems.contains(item)
                                val gridShape = com.prantiux.pixelgallery.ui.utils.getGridItemCornerShape(
                                    index = index,
                                    totalItems = group.items.size,
                                    columns = 3
                                )
                                
                                MediaThumbnail(
                                    item = item,
                                    isSelected = isSelected,
                                    isSelectionMode = isSelectionMode,
                                    shape = gridShape,
                                    onClick = { bounds ->
                                        if (isSelectionMode) {
                                            viewModel.toggleTrashSelection(item)
                                        } else {
                                            viewModel.showTrashMediaOverlay(
                                                selectedIndex = trashedItems.indexOf(item),
                                                thumbnailBounds = bounds?.let {
                                                    MediaViewModel.ThumbnailBounds(
                                                        startLeft = it.left,
                                                        startTop = it.top,
                                                        startWidth = it.width,
                                                        startHeight = it.height
                                                    )
                                                }
                                            )
                                        }
                                    },
                                    onLongClick = {
                                        if (!isSelectionMode) {
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            viewModel.enterTrashSelectionMode(item)
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
        
        // Floating navbar for selection mode
        if (isSelectionMode) {
            val navBarInset = androidx.compose.foundation.layout.WindowInsets.navigationBars
                .asPaddingValues().calculateBottomPadding()
            val bottomPadding = if (navBarInset > 0.dp) 8.dp else 24.dp
            
            com.prantiux.pixelgallery.navigation.PixelStyleFloatingNavBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = bottomPadding),
                items = listOf(
                    NavItem("restore", "Restore", FontIcons.Refresh),
                    NavItem("delete", "Delete", FontIcons.Delete)
                ),
                selectedRoute = "", // No selection highlighting in action mode
                onItemSelected = { item ->
                    when (item.route) {
                        "restore" -> { viewModel.restoreSelectedTrashItems(context) }
                        "delete" -> { viewModel.deleteSelectedTrashItems(context) }
                    }
                }
            )
        }
        
        // Trash media overlay - use existing MediaOverlay with unified overlayState
        val overlayState by viewModel.overlayState.collectAsState()
        
        if (overlayState.isVisible && overlayState.mediaType == "trash" && trashedItems.isNotEmpty()) {
            com.prantiux.pixelgallery.ui.overlay.MediaOverlay(
                viewModel = viewModel,
                overlayState = overlayState,
                mediaItems = trashedItems,
                onDismiss = { viewModel.hideMediaOverlay() }
            )
        }
    }
}

// Group items by days left until expiry
private fun groupByDaysLeft(items: List<MediaItem>): List<TrashedGroup> {
    // Get midnight of current day (start of today)
    val calendar = java.util.Calendar.getInstance()
    calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
    calendar.set(java.util.Calendar.MINUTE, 0)
    calendar.set(java.util.Calendar.SECOND, 0)
    calendar.set(java.util.Calendar.MILLISECOND, 0)
    val todayMidnight = calendar.timeInMillis / 1000
    
    val groups = mutableMapOf<Int, MutableList<MediaItem>>()
    
    items.forEach { item ->
        // Use DATE_EXPIRES directly from MediaItem (already set by MediaStore)
        val expiryTime = item.dateExpires
        
        // Get midnight of expiry day
        calendar.timeInMillis = expiryTime * 1000
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val expiryMidnight = calendar.timeInMillis / 1000
        
        // Calculate days between midnights
        val secondsBetween = expiryMidnight - todayMidnight
        val daysLeft = (secondsBetween / (24 * 60 * 60)).toInt().coerceAtLeast(0)
        
        groups.getOrPut(daysLeft) { mutableListOf() }.add(item)
    }
    
    // Convert to TrashedGroup and sort by days left (descending - most time left first)
    return groups.entries
        .sortedByDescending { it.key }
        .map { (days, items) ->
            val rangeText = when {
                days > 1 -> "$days days left"
                days == 1 -> "1 day left"
                else -> "0 days left"
            }
            TrashedGroup(
                daysLeftRange = rangeText,
                items = items.sortedByDescending { it.dateAdded }, // Most recently trashed first
                maxDaysLeft = days
            )
        }
}

