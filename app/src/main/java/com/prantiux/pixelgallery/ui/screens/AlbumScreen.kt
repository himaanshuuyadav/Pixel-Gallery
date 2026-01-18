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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AlbumDetailScreen(
    viewModel: MediaViewModel,
    albumId: String,
    onNavigateBack: () -> Unit,
    onNavigateToViewer: (Int) -> Unit
) {
    val images by viewModel.images.collectAsState()
    val videos by viewModel.videos.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedItems by viewModel.selectedItems.collectAsState()
    val view = LocalView.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    
    // Remember grid state for scrollbar
    val gridState = rememberLazyGridState()
    
    // Add BackHandler to exit selection mode on back press
    BackHandler(enabled = isSelectionMode) {
        viewModel.exitSelectionMode()
    }
    
    // Combine images and videos for this album
    val albumMedia = (images + videos)
        .filter { it.bucketId == albumId }
        .sortedByDescending { it.dateAdded }
    
    val albumName = albumMedia.firstOrNull()?.bucketName ?: "Album"
    
    val navBarHeight = calculateFloatingNavBarHeight()

    Column(modifier = Modifier.fillMaxSize()) {
        ConsistentHeader(
            title = albumName,
            onNavigateBack = onNavigateBack
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
                    // Calculate columns based on screen width (Adaptive uses 120.dp minSize)
                    val columns = 3 // Default to 3 columns for consistent rounded corners
                
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    contentPadding = PaddingValues(
                        start = 2.dp,
                        end = 2.dp,
                        top = 2.dp,
                        bottom = navBarHeight + 2.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(albumMedia) { index, item ->
                        val gridShape = com.prantiux.pixelgallery.ui.utils.getGridItemCornerShape(
                            index = index,
                            totalItems = albumMedia.size,
                            columns = columns
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
                            index = index,
                            showFavorite = true
                        )
                    }
                }
            }
        }
        
        // Unified Scrollbar Component with smooth scrolling (no jumping)
        com.prantiux.pixelgallery.ui.components.UnifiedScrollbar(
            modifier = Modifier.align(Alignment.TopEnd),
            gridState = gridState,
            mode = com.prantiux.pixelgallery.ui.components.ScrollbarMode.SMOOTH_SCROLLING,
            topPadding = 88.dp + 16.dp,
            totalItems = albumMedia.size,
            coroutineScope = coroutineScope,
            isDarkTheme = isDarkTheme
        )
        
        // Selection Top Bar - overlay above navigation bar
        com.prantiux.pixelgallery.ui.components.SelectionTopBar(
            isVisible = isSelectionMode,
            selectedCount = selectedItems.size,
            onCancelSelection = { viewModel.exitSelectionMode() },
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
                        "copy" -> { /* TODO: Copy to album functionality */ }
                        "share" -> { viewModel.shareSelectedItems(context) }
                        "delete" -> {
                            viewModel.deleteSelectedItems(context) { success ->
                                if (success) viewModel.exitSelectionMode()
                            }
                        }
                        "more" -> { /* TODO: More options */ }
                    }
                }
            )
        }
    }
    }
}
