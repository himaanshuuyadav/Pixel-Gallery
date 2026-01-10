package com.prantiux.pixelgallery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.prantiux.pixelgallery.model.MediaItem
import com.prantiux.pixelgallery.viewmodel.MediaViewModel
import com.prantiux.pixelgallery.ui.components.ConsistentHeader
import com.prantiux.pixelgallery.ui.utils.calculateFloatingNavBarHeight
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    viewModel: MediaViewModel,
    albumId: String,
    onNavigateBack: () -> Unit,
    onNavigateToViewer: (Int) -> Unit
) {
    val images by viewModel.images.collectAsState()
    val videos by viewModel.videos.collectAsState()
    
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
                        var thumbnailBounds by remember { mutableStateOf<Rect?>(null) }
                        
                        Box(modifier = Modifier.aspectRatio(1f)) {
                            AsyncImage(
                                model = item.uri,
                                contentDescription = item.displayName,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(com.prantiux.pixelgallery.ui.utils.getGridItemCornerShape(
                                        index = index,
                                        totalItems = albumMedia.size,
                                        columns = columns
                                    ))
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
                                    .clickable {
                                        thumbnailBounds?.let { bounds ->
                                            viewModel.showMediaOverlay(
                                                mediaType = "album",
                                                albumId = albumId,
                                                selectedIndex = index,
                                                thumbnailBounds = MediaViewModel.ThumbnailBounds(
                                                    startLeft = bounds.left,
                                                    startTop = bounds.top,
                                                    startWidth = bounds.width,
                                                    startHeight = bounds.height
                                                )
                                            )
                                        } ?: run {
                                            viewModel.showMediaOverlay(
                                                mediaType = "album",
                                                albumId = albumId,
                                                selectedIndex = index,
                                                thumbnailBounds = null
                                            )
                                        }
                                    },
                                contentScale = ContentScale.Crop
                            )
                            // Video indicator - small play icon with duration in top right
                            if (item.isVideo) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .background(
                                                color = Color.Black.copy(alpha = 0.7f),
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 4.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        FontIcon(
                                            unicode = FontIcons.PlayArrow,
                                            contentDescription = "Video",
                                            size = 12.sp,
                                            tint = Color.White
                                        )
                                        Text(
                                            text = formatVideoDuration(item.duration),
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp
                                            )
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
}

private fun formatVideoDuration(durationMs: Long): String {
    val seconds = (durationMs / 1000) % 60
    val minutes = (durationMs / (1000 * 60)) % 60
    val hours = durationMs / (1000 * 60 * 60)
    
    return when {
        hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, seconds)
        else -> String.format("%d:%02d", minutes, seconds)
    }
}
