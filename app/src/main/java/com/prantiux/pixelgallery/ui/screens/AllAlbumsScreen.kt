package com.prantiux.pixelgallery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prantiux.pixelgallery.ui.components.SubPageScaffoldGrid
import com.prantiux.pixelgallery.ui.utils.calculateFloatingNavBarHeight
import com.prantiux.pixelgallery.ui.utils.shimmerEffect
import androidx.compose.ui.graphics.Color

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
    // UNFILTERED: Use allCategorizedAlbumsFlow (not affected by Photos View Settings filter)
    // All Albums screen must show ALL albums regardless of Photos tab selection
    val categorizedAlbums by viewModel.allCategorizedAlbumsFlow.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val smartAlbumThumbnailCache = viewModel.smartAlbumThumbnailCache
    
    val allAlbums = remember(categorizedAlbums) {
        categorizedAlbums.mainAlbums + categorizedAlbums.otherAlbums
    }
    
    val navBarHeight = calculateFloatingNavBarHeight()

    SubPageScaffoldGrid(
        title = "All Albums",
        subtitle = if (allAlbums.isEmpty()) null else "${allAlbums.size} ${if (allAlbums.size == 1) "album" else "albums"}",
        onNavigateBack = onNavigateBack,
        columns = 2,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 44.dp, bottom = navBarHeight + 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isLoading) {
            items(12) {
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .background(Color.LightGray.copy(alpha = 0.2f), shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                        .shimmerEffect()
                )
            }
        } else if (allAlbums.isEmpty()) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                com.prantiux.pixelgallery.ui.components.PremiumEmptyState(
                    icon = com.prantiux.pixelgallery.ui.icons.FontIcons.Folder,
                    title = "No albums found",
                    subtitle = "Albums created by apps or folders will appear here.",
                    modifier = Modifier.padding(vertical = 64.dp)
                )
            }
        } else {
            items(allAlbums.size) { index ->
                val album = allAlbums[index]
                val dominantColor = MaterialTheme.colorScheme.primaryContainer

                SmartAlbumHeroCard(
                    album = album,
                    dominantColor = dominantColor,
                    cachedThumbnailUri = smartAlbumThumbnailCache[album.id] ?: album.coverUri,
                    onThumbnailCached = { uri ->
                        smartAlbumThumbnailCache[album.id] = uri
                    },
                    onClick = { onNavigateToAlbum(album.id) },
                    albumIndex = index
                )
            }
        }
    }
}
