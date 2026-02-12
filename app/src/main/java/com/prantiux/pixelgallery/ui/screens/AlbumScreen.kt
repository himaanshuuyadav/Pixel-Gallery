package com.prantiux.pixelgallery.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.prantiux.pixelgallery.model.MediaItem
import com.prantiux.pixelgallery.viewmodel.MediaViewModel
import com.prantiux.pixelgallery.ui.components.ConsistentHeader
import com.prantiux.pixelgallery.ui.components.SelectableMediaItem
import com.prantiux.pixelgallery.ui.utils.calculateFloatingNavBarHeight
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons
import com.prantiux.pixelgallery.ui.dialogs.CopyToAlbumDialog
import com.prantiux.pixelgallery.ui.dialogs.MoveToAlbumDialog
import com.prantiux.pixelgallery.smartalbum.SmartAlbumGenerator
import com.prantiux.pixelgallery.ui.shapes.SmoothCornerShape
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AlbumDetailScreen(
    viewModel: MediaViewModel,
    albumId: String,
    onNavigateBack: () -> Unit,
    onNavigateToViewer: (Int) -> Unit,
    settingsDataStore: com.prantiux.pixelgallery.data.SettingsDataStore
) {
    val images by viewModel.allImagesUnfiltered.collectAsState()
    val videos by viewModel.allVideosUnfiltered.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedItems by viewModel.selectedItems.collectAsState()
    val gridType by viewModel.gridType.collectAsState()
    val cornerType by settingsDataStore.cornerTypeFlow.collectAsState(initial = "Rounded")
    val view = LocalView.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    
    // Remember grid state for scrollbar
    val gridState = rememberLazyGridState()
    
    // Scrollbar state for overlay
    var scrollbarOverlayText by remember { mutableStateOf("") }
    var showScrollbarOverlay by remember { mutableStateOf(false) }
    
    // Add BackHandler to exit selection mode on back press
    BackHandler(enabled = isSelectionMode) {
        viewModel.exitSelectionMode()
    }
    
    // State for smart album media (populated asynchronously)
    var smartAlbumMedia by remember { mutableStateOf<List<MediaItem>?>(null) }
    
    // More menu state
    var showMoreMenu by remember { mutableStateOf(false) }
    
    // Check if this is a smart album and load media accordingly
    val isSmartAlbum = SmartAlbumGenerator.isSmartAlbum(albumId)
    
    // Load smart album media if needed
    LaunchedEffect(albumId, images, videos) {
        if (isSmartAlbum) {
            coroutineScope.launch {
                val allMedia = images + videos
                val smartMedia = SmartAlbumGenerator.getMediaForSmartAlbum(context, albumId, allMedia)
                smartAlbumMedia = smartMedia
            }
        }
    }
    
    // Combine images and videos for this album
    val albumMedia = remember(images, videos, albumId, smartAlbumMedia) {
        if (isSmartAlbum) {
            // Use smart album results
            smartAlbumMedia?.sortedByDescending { it.dateAdded } ?: emptyList()
        } else {
            // Regular album - filter by bucketId
            (images + videos)
                .filter { it.bucketId == albumId }
                .sortedByDescending { it.dateAdded }
        }
    }
    
    // Determine column count based on grid type
    val columnCount = when (gridType) {
        com.prantiux.pixelgallery.viewmodel.GridType.DAY -> 3
        com.prantiux.pixelgallery.viewmodel.GridType.MONTH -> 5
    }
    
    // Group by date or month based on grid type
    val groupedMedia: List<MediaGroup> = remember(albumMedia, gridType) {
        when (gridType) {
            com.prantiux.pixelgallery.viewmodel.GridType.DAY -> groupMediaByDate(albumMedia)
            com.prantiux.pixelgallery.viewmodel.GridType.MONTH -> groupMediaByMonth(albumMedia)
        }
    }
    
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
    
    val albumName = albumMedia.firstOrNull()?.bucketName ?: "Album"
    
    val navBarHeight = calculateFloatingNavBarHeight()
    
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = rememberTopAppBarState()
    )

    Column(modifier = Modifier.fillMaxSize()) {
        MediumTopAppBar(
            title = {
                Column {
                    Text(
                        text = albumName,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (albumMedia.isNotEmpty()) {
                        Text(
                            text = "${albumMedia.size} ${if (albumMedia.size == 1) "item" else "items"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    FontIcon(
                        unicode = FontIcons.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            scrollBehavior = scrollBehavior,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                scrolledContainerColor = MaterialTheme.colorScheme.surface
            )
        )
        
        Box(modifier = Modifier.fillMaxSize()) {
            if (albumMedia.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No media in this album",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                        )
                ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columnCount),
                    state = gridState,
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(
                        start = 2.dp,
                        end = 2.dp,
                        top = 16.dp,
                        bottom = navBarHeight + 2.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    groupedMedia.forEach { group ->
                        // Date Header - spans all columns with checkbox when in selection mode
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(columnCount) }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 8.dp, top = 32.dp, bottom = 8.dp, end = 8.dp),
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
                                                    if (allSelected) MaterialTheme.colorScheme.primary.copy(alpha = 1.0f) else androidx.compose.ui.graphics.Color.Transparent,
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
                            val globalIndex = albumMedia.indexOf(item)
                            val gridShape = com.prantiux.pixelgallery.ui.utils.getGridItemCornerShape(
                                index = index,
                                totalItems = group.items.size,
                                columns = columnCount,
                                cornerType = cornerType
                            )
                            
                            SelectableMediaItem(
                                item = item,
                                isSelectionMode = isSelectionMode,
                                selectedItems = selectedItems,
                                viewModel = viewModel,
                                view = view,
                                shape = gridShape,
                                mediaType = "album",
                                albumId = albumId,
                                index = globalIndex,
                                showFavorite = true
                            )
                        }
                    }
                }
            }
        }
        
        // Unified Scrollbar Component with date-based snapping
        com.prantiux.pixelgallery.ui.components.UnifiedScrollbar(
            modifier = Modifier.align(Alignment.TopEnd),
            gridState = gridState,
            mode = com.prantiux.pixelgallery.ui.components.ScrollbarMode.DATE_JUMPING,
            topPadding = 88.dp + 16.dp + 32.dp, // Align with first date header
            dateGroups = dateGroupsForScrollbar,
            coroutineScope = coroutineScope,
            isDarkTheme = isDarkTheme,
            onScrollbarVisibilityChanged = { /* No ViewModel state needed */ },
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
                isSelectionMode = true,
                items = listOf(
                    com.prantiux.pixelgallery.navigation.NavItem("copy", "Copy to", FontIcons.Copy),
                    com.prantiux.pixelgallery.navigation.NavItem("share", "Share", FontIcons.Share),
                    com.prantiux.pixelgallery.navigation.NavItem("delete", "Delete", FontIcons.Delete),
                    com.prantiux.pixelgallery.navigation.NavItem("more", "More", FontIcons.MoreVert)
                ),
                selectedRoute = "",
                onItemSelected = { item ->
                    when (item.route) {
                        "copy" -> { 
                            // Show copy to album dialog
                            viewModel.showCopyToAlbumDialog(selectedItems.toList())
                        }
                        "share" -> { viewModel.shareSelectedItems(context) }
                        "delete" -> {
                            viewModel.deleteSelectedItems(context) { success ->
                                if (success) viewModel.exitSelectionMode()
                            }
                        }
                        "more" -> { showMoreMenu = true }
                    }
                }
            )
        }
        
        // More menu dropdown for selection mode
        if (showMoreMenu && isSelectionMode) {
            val navBarInset = androidx.compose.foundation.layout.WindowInsets.navigationBars
                .asPaddingValues().calculateBottomPadding()
            
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = navBarInset + 80.dp, end = 16.dp)
            ) {
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false },
                    modifier = Modifier.widthIn(min = 220.dp),
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    shape = SmoothCornerShape(20.dp, 60)
                ) {
                    // Set as wallpaper
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Surface(
                                    shape = SmoothCornerShape(12.dp, 60),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        FontIcon(
                                            unicode = FontIcons.Image,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            size = 20.sp
                                        )
                                    }
                                }
                                Text(
                                    "Set as wallpaper",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        onClick = {
                            showMoreMenu = false
                            selectedItems.firstOrNull()?.let { item ->
                                if (!item.isVideo) {
                                    val wallpaperIntent = Intent(Intent.ACTION_ATTACH_DATA).apply {
                                        setDataAndType(item.uri, "image/*")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(wallpaperIntent, "Set as"))
                                } else {
                                    android.widget.Toast.makeText(context, "Cannot set video as wallpaper", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    
                    // Move to album
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Surface(
                                    shape = SmoothCornerShape(12.dp, 60),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        FontIcon(
                                            unicode = FontIcons.Move,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            size = 20.sp
                                        )
                                    }
                                }
                                Text(
                                    "Move to album",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        onClick = {
                            showMoreMenu = false
                            viewModel.showMoveToAlbumDialog(selectedItems.toList())
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    
                    // Hide from this label (only for smart albums)
                    if (isSmartAlbum) {
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Surface(
                                        shape = SmoothCornerShape(12.dp, 60),
                                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            FontIcon(
                                                unicode = FontIcons.VisibilityOff,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                size = 20.sp
                                            )
                                        }
                                    }
                                    Text(
                                        "Hide from this label",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            },
                            onClick = {
                                showMoreMenu = false
                                coroutineScope.launch {
                                    viewModel.hideFromSmartAlbum(context, albumId, selectedItems.toList())
                                    viewModel.exitSelectionMode()
                                    // Reload smart album media
                                    val allMedia = images + videos
                                    val smartMedia = SmartAlbumGenerator.getMediaForSmartAlbum(context, albumId, allMedia)
                                    smartAlbumMedia = smartMedia
                                    android.widget.Toast.makeText(
                                        context,
                                        "Hidden ${selectedItems.size} ${if (selectedItems.size == 1) "item" else "items"} from this label",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
    
    // Copy to Album Dialog
    val showCopyDialog by viewModel.showCopyToAlbumDialog.collectAsState()
    if (showCopyDialog) {
        CopyToAlbumDialog(
            viewModel = viewModel,
            albumRepository = com.prantiux.pixelgallery.data.AlbumRepository(context),
            onDismiss = { viewModel.hideCopyToAlbumDialog() }
        )
    }
    
    // Move to Album Dialog
    val showMoveDialog by viewModel.showMoveToAlbumDialog.collectAsState()
    if (showMoveDialog) {
        MoveToAlbumDialog(
            viewModel = viewModel,
            albumRepository = com.prantiux.pixelgallery.data.AlbumRepository(context),
            onDismiss = { viewModel.hideMoveToAlbumDialog() }
        )
    }
    }
}
