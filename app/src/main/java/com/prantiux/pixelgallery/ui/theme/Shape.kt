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
    extraSmall = RoundedCornerShape(6.dp),    // Increased from 4dp
    small = RoundedCornerShape(12.dp),        // Increased from 8dp
    medium = RoundedCornerShape(20.dp),       // Increased from 16dp
    large = RoundedCornerShape(28.dp),        // Increased from 24dp
    extraLarge = RoundedCornerShape(36.dp)    // Increased from 32dp
)
