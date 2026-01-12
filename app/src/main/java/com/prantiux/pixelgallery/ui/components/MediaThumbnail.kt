@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.prantiux.pixelgallery.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.prantiux.pixelgallery.model.MediaItem
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons

/**
 * CANONICAL MEDIA THUMBNAIL COMPONENT
 * 
 * This is the single source of truth for all media grid items across the app.
 * 
 * Features:
 * - Image loading with Coil
 * - Video duration pill (automatic)
 * - Favorite star badge
 * - Selection overlay with checkmark
 * - Thumbnail bounds tracking for animations
 * - Combined click handling (click + long press)
 * 
 * ❌ DO NOT create custom thumbnail implementations
 * ✅ ALWAYS use this component for any media grid
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaThumbnail(
    item: MediaItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    shape: Shape,
    onClick: (Rect?) -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    showFavorite: Boolean = true
) {
    var thumbnailBounds by remember { mutableStateOf<Rect?>(null) }
    val borderWidth = 16.dp
    val borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .then(
                if (isSelected) {
                    Modifier
                        .background(
                            color = borderColor,
                            shape = shape
                        )
                        .border(
                            width = borderWidth,
                            color = borderColor,
                            shape = shape
                        )
                } else Modifier
            )
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.displayName,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isSelected) {
                        Modifier.padding(borderWidth)
                    } else Modifier
                )
                .clip(shape)
                .onGloballyPositioned { coordinates ->
                    val position = coordinates.positionInWindow()
                    val size = coordinates.size
                    thumbnailBounds = Rect(
                        position.x,
                        position.y,
                        position.x + size.width,
                        position.y + size.height
                    )
                }
                .combinedClickable(
                    onClick = { onClick(thumbnailBounds) },
                    onLongClick = onLongClick
                ),
            contentScale = ContentScale.Crop
        )
        
        // Video duration pill (canonical implementation)
        if (item.isVideo) {
            VideoDurationPill(
                duration = item.duration,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(if (isSelected) borderWidth + 6.dp else 6.dp)
            )
        }
        
        // Favorite star badge (canonical implementation)
        if (item.isFavorite && showFavorite && !isSelectionMode) {
            FavoriteStarBadge(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(if (isSelected) borderWidth + 6.dp else 6.dp)
            )
        }
        
        // Selection indicator (canonical implementation)
        if (isSelectionMode) {
            SelectionCheckmark(
                isSelected = isSelected,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            )
        }
    }
}

/**
 * Video Duration Pill - Canonical Implementation
 * 
 * ❌ DO NOT create custom duration pills
 * ✅ ALWAYS use this component for video indicators
 */
@Composable
fun VideoDurationPill(
    duration: Long,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(50) // Pill shape
            )
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        FontIcon(
            unicode = FontIcons.PlayArrow,
            contentDescription = "Video",
            size = 14.sp,
            tint = Color.White
        )
        Text(
            text = formatDuration(duration),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

/**
 * Favorite Star Badge - Canonical Implementation
 * 
 * ❌ DO NOT create custom favorite badges
 * ✅ ALWAYS use this component for favorite indicators
 */
@Composable
fun FavoriteStarBadge(
    modifier: Modifier = Modifier
) {
    FontIcon(
        unicode = FontIcons.Star,
        contentDescription = "Favorited",
        size = 16.sp,
        tint = Color(0xFFFFD700), // Gold color
        modifier = modifier
    )
}

/**
 * Selection Checkmark - Canonical Implementation
 * 
 * ❌ DO NOT create custom selection indicators
 * ✅ ALWAYS use this component for selection UI
 */
@Composable
fun SelectionCheckmark(
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(24.dp)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.9f) 
                else Color.White.copy(alpha = 0.7f),
                CircleShape
            )
            .border(
                width = if (isSelected) 0.dp else 2.dp,
                color = if (isSelected) Color.Transparent else Color.Gray.copy(alpha = 0.5f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            FontIcon(
                unicode = FontIcons.Done,
                contentDescription = "Selected",
                size = 16.sp,
                tint = Color.White
            )
        }
    }
}

/**
 * Duration Formatter - Canonical Implementation
 * 
 * Formats milliseconds to HH:MM:SS or MM:SS
 */
fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs / 1000) % 60
    val minutes = (durationMs / (1000 * 60)) % 60
    val hours = durationMs / (1000 * 60 * 60)
    
    return when {
        hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, seconds)
        else -> String.format("%d:%02d", minutes, seconds)
    }
}
