package com.prantiux.pixelgallery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
    // ROOM-FIRST: Use Room-based favorites flow
    val favoriteItems by viewModel.favoritesFlow.collectAsState()
    
    // CALLING TAB LOG
    android.util.Log.d("SCREEN_TAB", "FavoritesScreen collected ${favoriteItems.size} favorites")
    
    val badgeType by settingsDataStore.badgeTypeFlow.collectAsState(initial = "Duration with icon")
    val badgeEnabled by settingsDataStore.showBadgeFlow.collectAsState(initial = true)
    val thumbnailQuality by settingsDataStore.thumbnailQualityFlow.collectAsState(initial = "Standard")
    val cornerType by settingsDataStore.cornerTypeFlow.collectAsState(initial = "Rounded")
    
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
            itemsIndexed(favoriteItems) { index, item ->
                val gridShape = com.prantiux.pixelgallery.ui.utils.getGridItemCornerShape(
                    index = index,
                    totalItems = favoriteItems.size,
                    columns = 3,
                    cornerType = cornerType
                )
                MediaThumbnail(
                    item = item,
                    isSelected = false,
                    isSelectionMode = false,
                    shape = gridShape,
                    badgeType = badgeType,
                    badgeEnabled = badgeEnabled,
                    thumbnailQuality = thumbnailQuality,
                    onClick = { bounds ->
                        val index = favoriteItems.indexOf(item)
                        val thumbnailBounds = bounds?.let {
                            com.prantiux.pixelgallery.ui.animation.SharedElementBounds(
                                left = it.left,
                                top = it.top,
                                width = it.width,
                                height = it.height
                            )
                        }
                        viewModel.showMediaOverlay(
                            mediaType = "favorites",
                            albumId = "",
                            selectedIndex = index,
                            thumbnailBounds = thumbnailBounds
                        )
                    },
                    onLongClick = {}
                )
            }
        }
    }
}
