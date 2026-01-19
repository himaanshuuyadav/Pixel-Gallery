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
import com.prantiux.pixelgallery.ui.components.SubPageScaffold
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
    
    SubPageScaffold(
        title = "Favourites",
        subtitle = if (favoriteItems.isEmpty()) null else "${favoriteItems.size} ${if (favoriteItems.size == 1) "item" else "items"}",
        onNavigateBack = onNavigateBack
    ) {
        if (favoriteItems.isEmpty()) {
            item {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 64.dp, horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
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
            }
        } else {
            item {
                // Grid of favorite items inside LazyColumn
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    favoriteItems.chunked(3).forEach { rowItems ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            rowItems.forEach { item ->
                                Box(modifier = Modifier.weight(1f)) {
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
                            // Fill remaining space if row is not complete
                            repeat(3 - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}
