@file:OptIn(ExperimentalMaterial3Api::class)

package com.prantiux.pixelgallery.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive Shape System
 * 
 * Increased corner radii for expressive, friendly appearance
 * Follows Material You design guidelines for gallery apps
 */
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),    // Increased from 6dp
    small = RoundedCornerShape(16.dp),        // Increased from 12dp
    medium = RoundedCornerShape(24.dp),       // Increased from 20dp
    large = RoundedCornerShape(32.dp),        // Increased from 28dp
    extraLarge = RoundedCornerShape(40.dp)    // Increased from 36dp
)

enum class ListItemPosition {
    TOP, MIDDLE, BOTTOM, SINGLE
}

/**
 * Generates an expressive, context-aware shape for list items.
 * The outer corners are heavily rounded (32dp) while inner corners
 * touching adjacent items are sharp (4dp).
 */
fun ExpressiveListShape(position: ListItemPosition): RoundedCornerShape {
    return when (position) {
        ListItemPosition.TOP -> RoundedCornerShape(
            topStart = 32.dp, topEnd = 32.dp,
            bottomStart = 4.dp, bottomEnd = 4.dp
        )
        ListItemPosition.MIDDLE -> RoundedCornerShape(4.dp)
        ListItemPosition.BOTTOM -> RoundedCornerShape(
            topStart = 4.dp, topEnd = 4.dp,
            bottomStart = 32.dp, bottomEnd = 32.dp
        )
        ListItemPosition.SINGLE -> RoundedCornerShape(32.dp)
    }
}
