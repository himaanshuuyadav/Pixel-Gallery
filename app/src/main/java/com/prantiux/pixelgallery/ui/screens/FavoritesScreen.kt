package com.prantiux.pixelgallery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prantiux.pixelgallery.model.MediaItem
import com.prantiux.pixelgallery.ui.components.ConsistentHeader
import com.prantiux.pixelgallery.ui.components.MediaThumbnail
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons
import com.prantiux.pixelgallery.viewmodel.MediaViewModel

@Composable
fun FavoritesScreen(
    viewModel: MediaViewModel,
    onNavigateBack: () -> Unit
) {
    val favoriteItems by viewModel.favoriteItems.collectAsState()
    
    Scaffold(
        topBar = {
            ConsistentHeader(
                title = "Favourites",
                onNavigateBack = onNavigateBack
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (favoriteItems.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    FontIcon(
                        unicode = FontIcons.Star,
                        contentDescription = "No favorites",
                        size = 64.sp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No favourites yet",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap the star icon on any photo or video to add it here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                // Grid of favorite items
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(favoriteItems, key = { it.id }) { item ->
                        MediaThumbnail(
                            item = item,
                            isSelected = false,
                            isSelectionMode = false,
                            shape = RoundedCornerShape(4.dp),
                            onClick = { bounds ->
                                val index = favoriteItems.indexOf(item)
                                viewModel.showMediaOverlay(
                                    mediaType = "favorites",
                                    albumId = "",
                                    selectedIndex = index,
                                    thumbnailBounds = bounds?.let {
                                        MediaViewModel.ThumbnailBounds(
                                            startLeft = it.left,
                                            startTop = it.top,
                                            startWidth = it.width,
                                            startHeight = it.height
                                        )
                                    }
                                )
                            },
                            onLongClick = {},
                            showFavorite = true
                        )
                    }
                }
            }
        }
    }
}
