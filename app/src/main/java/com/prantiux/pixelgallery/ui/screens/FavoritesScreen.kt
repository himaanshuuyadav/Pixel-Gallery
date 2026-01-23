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
import com.prantiux.pixelgallery.ui.components.SubPageScaffoldGrid
import com.prantiux.pixelgallery.ui.components.MediaThumbnail
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons
import com.prantiux.pixelgallery.viewmodel.MediaViewModel

@Composable
fun FavoritesScreen(
    viewModel: MediaViewModel,
    onNavigateBack: () -> Unit,
    settingsDataStore: com.prantiux.pixelgallery.data.SettingsDataStore
) {
    val favoriteItems by viewModel.favoriteItems.collectAsState()
    val badgeType by settingsDataStore.badgeTypeFlow.collectAsState(initial = "Duration with icon")
    
    SubPageScaffoldGrid(
        title = "Favourites",
        subtitle = if (favoriteItems.isEmpty()) null else "${favoriteItems.size} ${if (favoriteItems.size == 1) "item" else "items"}",
        onNavigateBack = onNavigateBack,
        columns = 3,
        contentPadding = PaddingValues(start = 2.dp, end = 2.dp, top = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (favoriteItems.isEmpty()) {
            item {
                // Empty state - spans all columns
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
            }
        } else {
            items(favoriteItems) { item ->
                MediaThumbnail(
                    item = item,
                    isSelected = false,
                    isSelectionMode = false,
                    shape = RoundedCornerShape(4.dp),
                    badgeType = badgeType,
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
