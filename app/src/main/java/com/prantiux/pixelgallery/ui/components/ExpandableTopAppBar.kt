@file:OptIn(ExperimentalMaterial3Api::class)

package com.prantiux.pixelgallery.ui.components

import android.app.Activity
import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons
import androidx.compose.ui.unit.TextUnit

private const val TAG = "ExpandableTopAppBar"

/**
 * Material 3 Large/Expandable Top App Bar for Photos tab
 * 
 * Features:
 * - Expands to show large title, subtitle, and settings icon
 * - Collapses smoothly when scrolling
 * - Follows Material 3 Expressive design guidelines
 * - No back arrow (root-level screen)
 */
@Composable
fun ExpandableTopAppBar(
    title: String,
    subtitle: String,
    scrollProgress: Float, // 0f = fully expanded, 1f = fully collapsed
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Material 3 Large Top App Bar dimensions
    // Collapsed height calculation:
    // - Status bar: 24-25dp
    // - Top padding: 14dp
    // - Title (26sp) + line height: 36dp
    // - Icon container (48dp) with padding: 54dp
    // - Proper vertical distribution: 14dp top + 54dp icon area = 68dp
    // - Bottom padding: 20dp
    // - Total: ~104dp (spacious for icon at collapsed state)
    val expandedHeight = 216.dp // Increased for immersive feel
    val collapsedHeight = 108.dp // Increased for proper icon spacing
    
    // Interpolate height smoothly
    val currentHeight = lerp(expandedHeight, collapsedHeight, scrollProgress)
    
    // Title size interpolation - more dramatic transition
    val expandedTitleSize = 45.sp // Larger for more impact
    val collapsedTitleSize = 26.sp // Increased from 22sp for better readability
    val currentTitleSize = lerp(expandedTitleSize, collapsedTitleSize, scrollProgress)
    
    // Title weight interpolation for smooth transition
    val titleFontWeight = if (scrollProgress < 0.5f) FontWeight.Bold else FontWeight.Medium
    
    // Subtitle alpha - fade out smoothly but completely
    val subtitleAlpha = (1f - scrollProgress * 1.5f).coerceIn(0f, 1f)
    
    // Title vertical alignment - stays visible in collapsed state with better spacing
    val titleBottomPadding = lerp(24.dp, 22.dp, scrollProgress)
    
    // Background color with proper opacity
    val backgroundColor = MaterialTheme.colorScheme.surface
    
    // Set status bar color
    SetStatusBarColor(backgroundColor)
    
    // Elevation only when fully collapsed
    val elevation = if (scrollProgress > 0.95f) 2.dp else 0.dp
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = backgroundColor,
        shadowElevation = elevation,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(currentHeight)
                .statusBarsPadding()
        ) {
            // Content container - always anchored to bottom of app bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = titleBottomPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // Title and subtitle column
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        fontSize = currentTitleSize,
                        fontWeight = titleFontWeight,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = currentTitleSize * 1.1f
                    )
                    
                    // Subtitle - only show when expanded
                    if (subtitleAlpha > 0.01f) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.alpha(subtitleAlpha)
                        )
                    }
                }
                
                // Settings icon button - always visible
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .size(48.dp)
                        .padding(bottom = 4.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            FontIcon(
                                unicode = FontIcons.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurface,
                                size = 22.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Calculate scroll progress for the expandable app bar
 * Returns 0f when fully expanded, 1f when fully collapsed
 * 
 * @param firstVisibleItemIndex The index of the first visible item in the list
 * @param firstVisibleItemScrollOffset The scroll offset of the first visible item
 * @param collapseThreshold The number of pixels to scroll before fully collapsing (default 150)
 */
fun calculateScrollProgress(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    collapseThreshold: Int = 150
): Float {
    return if (firstVisibleItemIndex > 0) {
        // If we've scrolled past the first item, fully collapse
        1f
    } else {
        // Smooth interpolation based on scroll offset
        (firstVisibleItemScrollOffset.toFloat() / collapseThreshold).coerceIn(0f, 1f)
    }
}

// Helper function to lerp between TextUnit values
private fun lerp(start: TextUnit, stop: TextUnit, fraction: Float): TextUnit {
    return (start.value + (stop.value - start.value) * fraction).sp
}
