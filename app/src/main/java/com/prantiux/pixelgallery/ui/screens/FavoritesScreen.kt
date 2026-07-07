package com.prantiux.pixelgallery.ui.screens

import com.prantiux.pixelgallery.ui.utils.rememberZenithFlingBehavior
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
import com.prantiux.pixelgallery.ui.components.PremiumEmptyState
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons
import com.prantiux.pixelgallery.viewmodel.MediaViewModel
import com.prantiux.pixelgallery.ui.components.MediaThumbnail

@Composable
fun FavoritesScreen(
    viewModel: MediaViewModel,
    onNavigateBack: () -> Unit,
    settingsDataStore: com.prantiux.pixelgallery.data.SettingsDataStore
) {
    // ROOM-FIRST: Use Room-based favorites flow
    val favoriteItems by viewModel.favoritesFlow.collectAsState()
    
    // DETAILED LOGGING FOR DEBUGGING
    val collectionTime = try {
        java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS"))
    } catch (e: Exception) {
        "??:??:??.???"
    }
    
    if (com.prantiux.pixelgallery.BuildConfig.DEBUG) android.util.Log.d("SCREEN_TAB", "[$collectionTime] FavoritesScreen collected ${favoriteItems.size} favorites from favoritesFlow")
    if (favoriteItems.isNotEmpty()) {
        if (com.prantiux.pixelgallery.BuildConfig.DEBUG) android.util.Log.d("SCREEN_TAB", "  Items: ${favoriteItems.take(3).map { "${it.id}: ${it.displayName}" }.joinToString(", ")}${if (favoriteItems.size > 3) ", ..." else ""}")
    }
    
    val badgeType by settingsDataStore.badgeTypeFlow.collectAsState(initial = "Duration with icon")
    val badgeEnabled by settingsDataStore.showBadgeFlow.collectAsState(initial = true)
    val thumbnailQuality by settingsDataStore.thumbnailQualityFlow.collectAsState(initial = "Standard")
    val cornerType by settingsDataStore.cornerTypeFlow.collectAsState(initial = "Rounded")
    
    SubPageScaffoldGrid(
        title = "Favourites",
        subtitle = if (favoriteItems.isEmpty()) null else "${favoriteItems.size} ${if (favoriteItems.size == 1) "item" else "items"}",
        onNavigateBack = onNavigateBack,
        columns = 3,
        contentPadding = PaddingValues(start = 2.dp, end = 2.dp, top = 44.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (favoriteItems.isEmpty()) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                PremiumEmptyState(
                    icon = FontIcons.Star,
                    title = "No favourites yet",
                    subtitle = "Tap the star icon on any photo or video to add it here",
                    modifier = Modifier.padding(vertical = 64.dp)
                )
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
                    onClick = {
                        val index = favoriteItems.indexOf(item)
                        viewModel.showMediaOverlay(
                            mediaType = "favorites",
                            albumId = "",
                            selectedIndex = index
                        )
                    },
                    onLongClick = {}
                )
            }
        }
    }
}
