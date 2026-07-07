package com.prantiux.pixelgallery.ui.utils

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Calculates the local index and total items for a media item within its date group,
 * given its global index in the flattened list (Headers + Media).
 * 
 * @param globalIndex The index of the item in the flattened list
 * @param dateGroups The list of DateGroupInfo
 * @param contentOffsetIndex The number of non-grid items at the start (e.g. tab header)
 * @return A Pair of (indexInGroup, totalItemsInGroup), or null if it's a header
 */
fun getLocalPositionInDateGroup(
    globalIndex: Int,
    dateGroups: List<com.prantiux.pixelgallery.ui.components.DateGroupInfo>,
    contentOffsetIndex: Int = 1
): Pair<Int, Int>? {
    var currentIndex = contentOffsetIndex
    for (group in dateGroups) {
        if (globalIndex == currentIndex) return null // Header
        
        val groupStart = currentIndex + 1
        val groupEnd = currentIndex + group.itemCount
        
        if (globalIndex in groupStart..groupEnd) {
            return Pair(globalIndex - groupStart, group.itemCount)
        }
        
        currentIndex += group.itemCount + 1
    }
    return null
}

/**
 * Returns the (accentRadius, defaultRadius) pair based on the grid type.
 * DAY_3: 8/4, DAY_4: 6/3, MONTH_6: 4/2, MONTH_9: 2/1
 */
fun cornerRadiiForGridType(gridType: com.prantiux.pixelgallery.viewmodel.GridType): Pair<Dp, Dp> {
    return when (gridType) {
        com.prantiux.pixelgallery.viewmodel.GridType.DAY_3 -> Pair(8.dp, 4.dp)
        com.prantiux.pixelgallery.viewmodel.GridType.DAY_4 -> Pair(6.dp, 3.dp)
        com.prantiux.pixelgallery.viewmodel.GridType.MONTH_6 -> Pair(4.dp, 2.dp)
        com.prantiux.pixelgallery.viewmodel.GridType.MONTH_9 -> Pair(2.dp, 1.dp)
    }
}

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
