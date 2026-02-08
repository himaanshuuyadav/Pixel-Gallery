@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.prantiux.pixelgallery.ui.components

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import com.prantiux.pixelgallery.model.MediaItem
import com.prantiux.pixelgallery.viewmodel.MediaViewModel

/**
 * CANONICAL SELECTION SYSTEM
 * 
 * This is the single source of truth for selection behavior across the app.
 * 
 * Selection Rules:
 * - Long press to enter selection mode
 * - Tap to toggle selection when in selection mode
 * - Tap to open when NOT in selection mode
 * - Haptic feedback on long press
 * - Back button exits selection mode
 * 
 * ❌ DO NOT implement custom selection logic
 * ✅ ALWAYS use these handlers for consistent behavior
 */

/**
 * Standard click handler for media items
 * 
 * Handles both selection mode and normal mode clicks
 * 
 * @param isSelectionMode Whether selection mode is active
 * @param item The media item being clicked
 * @param thumbnailBounds The thumbnail bounds for shared element animation
 * @param viewModel The MediaViewModel for state management
 * @param mediaType The type of media source ("photos", "album", "favorites", etc.)
 * @param albumId The album ID (for album-specific views)
 * @param index The index of the item in the list
 */
fun handleMediaItemClick(
    isSelectionMode: Boolean,
    item: MediaItem,
    thumbnailBounds: Rect?,
    viewModel: MediaViewModel,
    mediaType: String,
    albumId: String,
    index: Int
) {
    if (isSelectionMode) {
        // Selection mode: toggle selection
        viewModel.toggleSelection(item)
    } else {
        // Normal mode: open overlay with animation
        val bounds = thumbnailBounds?.let {
            MediaViewModel.ThumbnailBounds(
                startLeft = it.left,
                startTop = it.top,
                startWidth = it.width,
                startHeight = it.height
            )
        }
        viewModel.showMediaOverlay(
            mediaType = mediaType,
            albumId = albumId,
            selectedIndex = index,
            thumbnailBounds = bounds
        )
    }
}

/**
 * Standard long-press handler for media items
 * 
 * Enters selection mode with haptic feedback
 * 
 * @param isSelectionMode Whether selection mode is already active
 * @param item The media item being long-pressed
 * @param viewModel The MediaViewModel for state management
 * @param view The view for haptic feedback
 */
fun handleMediaItemLongPress(
    isSelectionMode: Boolean,
    item: MediaItem,
    viewModel: MediaViewModel,
    view: View
) {
    if (!isSelectionMode) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        viewModel.enterSelectionMode(item)
    }
}

/**
 * Selection State Helper
 * 
 * Checks if an item is selected
 */
fun isItemSelected(
    item: MediaItem,
    selectedItems: Set<MediaItem>
): Boolean {
    return selectedItems.contains(item)
}

/**
 * Selection Mode Configuration
 * 
 * Standard configuration for selection UI
 */
object SelectionConfig {
    /** Border width for selected items */
    const val SELECTION_BORDER_WIDTH_DP = 3
    
    /** Border alpha for selected items */
    const val SELECTION_BORDER_ALPHA = 0.3f
    
    /** Padding adjustment when item is selected */
    const val SELECTION_PADDING_DP = 6
    
    /** Checkmark size */
    const val CHECKMARK_SIZE_DP = 24
    
    /** Checkmark padding from corner */
    const val CHECKMARK_PADDING_DP = 8
    
    /** Favorite badge padding adjustment when selected */
    const val FAVORITE_BADGE_PADDING_DP = 6
}

/**
 * Grid Item Wrapper with Selection
 * 
 * Convenience composable that wraps MediaThumbnail with standard selection behavior
 * 
 * Usage Example:
 * ```
 * SelectableMediaItem(
 *     item = mediaItem,
 *     isSelectionMode = isSelectionMode,
 *     selectedItems = selectedItems,
 *     viewModel = viewModel,
 *     view = view,
 *     shape = gridShape,
 *     mediaType = "photos",
 *     albumId = "all",
 *     index = index
 * )
 * ```
 */
@Composable
fun SelectableMediaItem(
    item: MediaItem,
    isSelectionMode: Boolean,
    selectedItems: Set<MediaItem>,
    viewModel: MediaViewModel,
    view: View,
    shape: androidx.compose.ui.graphics.Shape,
    mediaType: String,
    albumId: String,
    index: Int,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    showFavorite: Boolean = true
) {
    val isSelected = isItemSelected(item, selectedItems)
    
    MediaThumbnail(
        item = item,
        isSelected = isSelected,
        isSelectionMode = isSelectionMode,
        shape = shape,
        onClick = { bounds ->
            handleMediaItemClick(
                isSelectionMode = isSelectionMode,
                item = item,
                thumbnailBounds = bounds,
                viewModel = viewModel,
                mediaType = mediaType,
                albumId = albumId,
                index = index
            )
        },
        onLongClick = {
            handleMediaItemLongPress(
                isSelectionMode = isSelectionMode,
                item = item,
                viewModel = viewModel,
                view = view
            )
        },
        modifier = modifier,
        showFavorite = showFavorite
    )
}
