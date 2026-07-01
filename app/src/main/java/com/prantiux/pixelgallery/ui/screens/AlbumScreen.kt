@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.prantiux.pixelgallery.ui.screens

import com.prantiux.pixelgallery.ui.utils.rememberZenithFlingBehavior
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.prantiux.pixelgallery.model.MediaGroup
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
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.prantiux.pixelgallery.model.MediaGridItem
import com.prantiux.pixelgallery.ui.utils.shimmerEffect

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AlbumDetailScreen(
    viewModel: MediaViewModel,
    albumId: String,
    onNavigateBack: () -> Unit,
    onNavigateToViewer: (Int) -> Unit,
    settingsDataStore: com.prantiux.pixelgallery.data.SettingsDataStore
) {
    // Get context
    val context = LocalContext.current
    
    // ROOM-FIRST: Album media from Paged Flow
    val pagedAlbumMediaFlow = remember(albumId) { viewModel.pagedAlbumMediaFlow(context, albumId) }
    val pagedAlbumMedia = pagedAlbumMediaFlow.collectAsLazyPagingItems()
    
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedItems by viewModel.selectedItems.collectAsState()
    val gridType by viewModel.gridType.collectAsState()
    val cornerType by settingsDataStore.cornerTypeFlow.collectAsState(initial = "Rounded")
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val density = LocalDensity.current
    var contentTopPx by remember { mutableStateOf<Float?>(null) }
    var firstHeaderTopPx by remember { mutableStateOf<Float?>(null) }
    
    // Remember grid state for scrollbar
    val gridState = rememberLazyGridState()

    // Shared element scroll guard
    val isGridScrolling by remember { derivedStateOf { gridState.isScrollInProgress } }
    var canAnimate by remember { mutableStateOf(true) }
    LaunchedEffect(isGridScrolling) {
        if (isGridScrolling) {
            canAnimate = false
        } else {
            kotlinx.coroutines.delay(300)
            canAnimate = true
        }
    }
    

    // Scrollbar state for overlay
    var scrollbarOverlayText by remember { mutableStateOf("") }
    var showScrollbarOverlay by remember { mutableStateOf(false) }
    
    // Add BackHandler to exit selection mode on back press
    BackHandler(enabled = isSelectionMode) {
        viewModel.exitSelectionMode()
    }
    
    // More menu state
    var showMoreMenu by remember { mutableStateOf(false) }
    
    // Determine column count based on grid type
    val columnCount = when (gridType) {
        com.prantiux.pixelgallery.viewmodel.GridType.DAY -> 3
        com.prantiux.pixelgallery.viewmodel.GridType.MONTH -> 5
    }
    
    // Prepare date group info for scrollbar
    val dateGroupsFlow = remember(albumId) { viewModel.getAlbumDateGroups(albumId) }
    val dateGroupsForScrollbar by dateGroupsFlow.collectAsState(initial = emptyList())
    
    val isSmartAlbum = SmartAlbumGenerator.isSmartAlbum(albumId)
    val album = remember(albumId, viewModel.allAlbumsFlow.value) {
        viewModel.allAlbumsFlow.value.find { it.id == albumId }
    }
    val albumName = remember(albumId, album) {
        if (isSmartAlbum) {
            SmartAlbumGenerator.SmartAlbumType.fromId(albumId)?.displayName ?: "Smart Album"
        } else {
            album?.name ?: "Album"
        }
    }
    val albumItemCount = album?.itemCount ?: 0
    
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
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = com.prantiux.pixelgallery.ui.theme.zenithHeadingFont
                            ),
                            fontWeight = FontWeight.ExtraBold
                        )
                    if (albumItemCount > 0) {
                        Text(
                            text = "$albumItemCount ${if (albumItemCount == 1) "item" else "items"}",
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
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    if (contentTopPx == null) {
                        contentTopPx = coords.positionInRoot().y
                    }
                }
        ) {
            if (pagedAlbumMedia.itemCount == 0) {
                if (!pagedAlbumMedia.loadState.append.endOfPaginationReached) {
                    // Show expressive skeleton loader
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                            )
                    ) {
                        LazyVerticalGrid(
    flingBehavior = rememberZenithFlingBehavior(),
                            columns = GridCells.Fixed(columnCount),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 2.dp,
                                end = 2.dp,
                                top = 16.dp,
                                bottom = navBarHeight + 2.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(30) {
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .background(Color.LightGray.copy(alpha = 0.2f))
                                        .shimmerEffect()
                                )
                            }
                        }
                    }
                } else {
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
    flingBehavior = rememberZenithFlingBehavior(),
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
                    items(
                        count = pagedAlbumMedia.itemCount,
                        key = pagedAlbumMedia.itemKey { 
                            when (it) {
                                is MediaGridItem.Media -> it.mediaItem.id
                                is MediaGridItem.Header -> "header_${it.dateGroupKey}"
                            }
                        },
                        span = { index ->
                            val item = pagedAlbumMedia[index]
                            if (item is MediaGridItem.Header) {
                                androidx.compose.foundation.lazy.grid.GridItemSpan(columnCount)
                            } else {
                                androidx.compose.foundation.lazy.grid.GridItemSpan(1)
                            }
                        }
                    ) { index ->
                        val item = pagedAlbumMedia[index]
                        
                        if (item != null) {
                            when (item) {
                                is MediaGridItem.Header -> {
                                    val isDay = gridType == com.prantiux.pixelgallery.viewmodel.GridType.DAY
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, top = 32.dp, bottom = 8.dp, end = 8.dp)
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
                                        Text(
                                            text = item.displayDate,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        
                                        Box(
                                            modifier = Modifier.size(24.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelectionMode) {
                                                // We don't have allSelected logic here easily in paging. 
                                                // If we need selection by day, we should query DB like in PhotosScreen
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
                                                    // Empty checkbox that selects all when clicked
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
                                        totalItems = pagedAlbumMedia.itemCount,
                                        columns = columnCount,
                                        cornerType = cornerType
                                    )
                                    
                                    com.prantiux.pixelgallery.ui.components.MediaThumbnail(
                                        item = mediaItem,
                                        isSelected = isSelected,
                                        isSelectionMode = isSelectionMode,
                                        shape = gridShape,
                                        onClick = {
                                            if (isSelectionMode) {
                                                viewModel.toggleSelection(mediaItem.id)
                                            } else {
                                                viewModel.showMediaOverlayWithItem(
                                                    mediaType = "album",
                                                    albumId = albumId,
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
        }
        
        // Unified Scrollbar Component with date-based snapping
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
                                    shape = MaterialShapes.Cookie7Sided.toShape(),
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
                            selectedItems.firstOrNull()?.let { itemId ->
                                viewModel.setAsWallpaper(context, itemId)
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
                                    shape = MaterialShapes.Cookie7Sided.toShape(),
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
                                        shape = MaterialShapes.Cookie7Sided.toShape(),
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
                                    pagedAlbumMedia.refresh()
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
