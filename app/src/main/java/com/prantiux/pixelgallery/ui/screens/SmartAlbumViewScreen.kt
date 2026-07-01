package com.prantiux.pixelgallery.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prantiux.pixelgallery.model.MediaItem
import com.prantiux.pixelgallery.viewmodel.MediaViewModel
import com.prantiux.pixelgallery.ui.components.SubPageScaffoldGrid
import com.prantiux.pixelgallery.ui.components.SelectableMediaItem
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons
import com.prantiux.pixelgallery.ui.dialogs.CopyToAlbumDialog
import com.prantiux.pixelgallery.ui.dialogs.MoveToAlbumDialog
import com.prantiux.pixelgallery.smartalbum.SmartAlbumGenerator
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SmartAlbumViewScreen(
    viewModel: MediaViewModel,
    albumId: String,
    onNavigateBack: () -> Unit,
    onNavigateToViewer: (Int) -> Unit,
    settingsDataStore: com.prantiux.pixelgallery.data.SettingsDataStore
) {
    // ROOM-FIRST: Use unfiltered media flow for smart albums (independent of Photos View filters)
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedItems by viewModel.selectedItems.collectAsState()
    val cornerType by settingsDataStore.cornerTypeFlow.collectAsState(initial = "Rounded")
    val view = LocalView.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Add BackHandler to exit selection mode on back press
    BackHandler(enabled = isSelectionMode) {
        viewModel.exitSelectionMode()
    }
    
    // ROOM-FIRST: Album media from Paged Flow
    val pagedAlbumMediaFlow = remember(albumId) { viewModel.pagedAlbumMediaFlow(context, albumId) }
    val pagedAlbumMedia = pagedAlbumMediaFlow.collectAsLazyPagingItems()
    
    // Get smart album name
    val albumType = SmartAlbumGenerator.SmartAlbumType.fromId(albumId)
    val albumName = albumType?.displayName ?: "Smart Album"

    Box(modifier = Modifier.fillMaxSize()) {
        SubPageScaffoldGrid(
            title = albumName,
            subtitle = if (pagedAlbumMedia.itemCount == 0) null else "${pagedAlbumMedia.itemCount} items",
            onNavigateBack = onNavigateBack,
            columns = 3,
            contentPadding = PaddingValues(start = 2.dp, end = 2.dp, top = 44.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (pagedAlbumMedia.itemCount == 0 && pagedAlbumMedia.loadState.refresh !is androidx.paging.LoadState.Loading) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 64.dp, horizontal = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            FontIcon(
                                unicode = FontIcons.Image,
                                contentDescription = "No items",
                                size = 64.sp,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No items in this album",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(
                    count = pagedAlbumMedia.itemCount,
                    key = pagedAlbumMedia.itemKey { item ->
                        when (item) {
                            is com.prantiux.pixelgallery.model.MediaGridItem.Header -> "header_${item.dateGroupKey}"
                            is com.prantiux.pixelgallery.model.MediaGridItem.Media -> item.mediaItem.id
                            else -> "unknown"
                        }
                    },
                    span = { index ->
                        val item = pagedAlbumMedia.peek(index)
                        if (item is com.prantiux.pixelgallery.model.MediaGridItem.Header) {
                            androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan)
                        } else {
                            androidx.compose.foundation.lazy.grid.GridItemSpan(1)
                        }
                    }
                ) { index ->
                    val item = pagedAlbumMedia[index]
                    when (item) {
                        is com.prantiux.pixelgallery.model.MediaGridItem.Header -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 8.dp, top = 32.dp, bottom = 8.dp, end = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.displayDate,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        is com.prantiux.pixelgallery.model.MediaGridItem.Media -> {
                            val mediaItem = item.mediaItem
                            val gridShape = com.prantiux.pixelgallery.ui.utils.getGridItemCornerShape(
                                index = index,
                                totalItems = pagedAlbumMedia.itemCount,
                                columns = 3,
                                cornerType = cornerType
                            )
                            
                            SelectableMediaItem(
                                item = mediaItem,
                                isSelectionMode = isSelectionMode,
                                selectedItems = selectedItems,
                                viewModel = viewModel,
                                view = view,
                                shape = gridShape,
                                mediaType = "smartalbum",
                                albumId = albumId,
                                index = index,
                                showFavorite = true)
                        }
                        null -> {
                            Box(modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .background(MaterialTheme.colorScheme.surfaceVariant))
                        }
                    }
                }
            }
        }
        
        // More menu state (needs to be inside the Box scope where it's used, or re-declared)
        var showMoreMenu by remember { mutableStateOf(false) }
        
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
                        shape = com.prantiux.pixelgallery.ui.shapes.SmoothCornerShape(20.dp, 60)
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
                                        shape = com.prantiux.pixelgallery.ui.shapes.SmoothCornerShape(12.dp, 60),
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
                        
                        // Hide from this label
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
                                        shape = com.prantiux.pixelgallery.ui.shapes.SmoothCornerShape(12.dp, 60),
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
                                    // Paging 3 will automatically refresh since the underlying flow updates
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
