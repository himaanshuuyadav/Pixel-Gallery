package com.prantiux.pixelgallery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.prantiux.pixelgallery.data.AlbumRepository
import com.prantiux.pixelgallery.model.Album
import com.prantiux.pixelgallery.ui.components.SubPageScaffoldGrid
import com.prantiux.pixelgallery.ui.utils.calculateFloatingNavBarHeight
import kotlinx.coroutines.launch

/**
 * Screen showing all albums in a grid layout
 */
@Composable
fun AllAlbumsScreen(
    onNavigateToAlbum: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: com.prantiux.pixelgallery.viewmodel.MediaViewModel,
    modifier: Modifier = Modifier
) {
    // REFACTORED: Use ViewModel's cached albums instead of querying MediaStore
    // ROOM-FIRST: Use Room-derived categorized albums flow
    val categorizedAlbums by viewModel.categorizedAlbumsFlow.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    val allAlbums = remember(categorizedAlbums) {
        categorizedAlbums.mainAlbums + categorizedAlbums.otherAlbums
    }
    
    val navBarHeight = calculateFloatingNavBarHeight()

    SubPageScaffoldGrid(
        title = "All Albums",
        subtitle = if (allAlbums.isEmpty()) null else "${allAlbums.size} ${if (allAlbums.size == 1) "album" else "albums"}",
        onNavigateBack = onNavigateBack,
        columns = 2,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = navBarHeight + 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isLoading) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else if (allAlbums.isEmpty()) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No albums found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else {
            items(allAlbums.size) { index ->
                AllAlbumsGridItem(
                    album = allAlbums[index],
                    onClick = { onNavigateToAlbum(allAlbums[index].id) }
                )
            }
        }
    }
}

/**
 * Grid item for displaying an album - matches OtherAlbumPillButton style
 */
@Composable
fun AllAlbumsGridItem(
    album: Album,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        // Square album cover image
        if (album.coverUri != null) {
            AsyncImage(
                model = album.coverUri,
                contentDescription = "${album.name} cover",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            // Fallback background if no cover image
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
        
        // Overlaid pill label at bottom center
        Text(
            text = album.name,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp, start = 8.dp, end = 8.dp)
                .background(
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(50)
                )
                .padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}
