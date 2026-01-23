package com.prantiux.pixelgallery.ui.utils

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Calculate corner radius for grid items based on position
 * Provides Material 3 expressive rounded corners for grid layouts
 * 
 * @param index Item index in the grid
 * @param totalItems Total number of items in the grid
 * @param columns Number of columns in the grid (default: 3)
 * @param defaultRadius Default radius for most corners (default: 4.dp)
 * @param accentRadius Accent radius for corner items (default: 8.dp)
 * @param cornerType Corner type setting ("Rounded" or "Sharp")
 * @return RoundedCornerShape with appropriate corner radii
 */
fun getGridItemCornerShape(
    index: Int,
    totalItems: Int,
    columns: Int = 3,
    defaultRadius: Dp = 4.dp,
    accentRadius: Dp = 8.dp,
    cornerType: String = "Rounded"
): RoundedCornerShape {
    // If Sharp corners selected, return sharp corners for all items
    if (cornerType == "Sharp") {
        return RoundedCornerShape(0.dp)
    }
    
    val row = index / columns
    val col = index % columns
    val totalRows = (totalItems + columns - 1) / columns
    val lastRowItemCount = if (totalItems % columns == 0) columns else totalItems % columns
    
    val isFirstRow = row == 0
    val isLastRow = row == totalRows - 1
    val isFirstColumn = col == 0
    val isLastColumn = col == columns - 1
    val isLastItemInRow = isLastRow && col == lastRowItemCount - 1
    
    // Single item case
    if (totalItems == 1) {
        return RoundedCornerShape(accentRadius)
    }
    
    // Single row case
    if (totalRows == 1) {
        return when {
            isFirstColumn && lastRowItemCount == 1 -> RoundedCornerShape(accentRadius) // Only one item
            isFirstColumn -> RoundedCornerShape(
                topStart = accentRadius,
                topEnd = defaultRadius,
                bottomStart = accentRadius,
                bottomEnd = defaultRadius
            )
            isLastItemInRow -> RoundedCornerShape(
                topStart = defaultRadius,
                topEnd = accentRadius,
                bottomStart = defaultRadius,
                bottomEnd = accentRadius
            )
            else -> RoundedCornerShape(defaultRadius)
        }
    }
    
    // Last row with single item
    if (isLastRow && lastRowItemCount == 1) {
        return RoundedCornerShape(
            topStart = defaultRadius,
            topEnd = defaultRadius,
            bottomStart = accentRadius,
            bottomEnd = accentRadius
        )
    }
    
    // First row
    if (isFirstRow) {
        return when {
            isFirstColumn -> RoundedCornerShape(
                topStart = accentRadius,
                topEnd = defaultRadius,
                bottomStart = defaultRadius,
                bottomEnd = defaultRadius
            )
            isLastColumn -> RoundedCornerShape(
                topStart = defaultRadius,
                topEnd = accentRadius,
                bottomStart = defaultRadius,
                bottomEnd = defaultRadius
            )
            else -> RoundedCornerShape(defaultRadius)
        }
    }
    
    // Last row
    if (isLastRow) {
        return when {
            isFirstColumn -> RoundedCornerShape(
                topStart = defaultRadius,
                topEnd = defaultRadius,
                bottomStart = accentRadius,
                bottomEnd = defaultRadius
            )
            isLastItemInRow -> RoundedCornerShape(
                topStart = defaultRadius,
                topEnd = defaultRadius,
                bottomStart = defaultRadius,
                bottomEnd = accentRadius
            )
            else -> RoundedCornerShape(defaultRadius)
        }
    }
    
    // Middle rows
    return RoundedCornerShape(defaultRadius)
}

/**
 * Simpler version for album preview grids (2x3 fixed layout)
 */
fun getAlbumPreviewCornerShape(
    index: Int,
    defaultRadius: Dp = 4.dp,
    accentRadius: Dp = 8.dp
): RoundedCornerShape {
    return when (index) {
        0 -> RoundedCornerShape(topStart = accentRadius, topEnd = defaultRadius, bottomStart = defaultRadius, bottomEnd = defaultRadius)
        2 -> RoundedCornerShape(topStart = defaultRadius, topEnd = accentRadius, bottomStart = defaultRadius, bottomEnd = defaultRadius)
        3 -> RoundedCornerShape(topStart = defaultRadius, topEnd = defaultRadius, bottomStart = accentRadius, bottomEnd = defaultRadius)
        5 -> RoundedCornerShape(topStart = defaultRadius, topEnd = defaultRadius, bottomStart = defaultRadius, bottomEnd = accentRadius)
        else -> RoundedCornerShape(defaultRadius)
    }
}
