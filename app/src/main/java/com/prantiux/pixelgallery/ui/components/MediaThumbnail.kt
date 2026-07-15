@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.prantiux.pixelgallery.ui.components

import androidx.compose.animation.core.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.prantiux.pixelgallery.model.MediaItem
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons
import com.prantiux.pixelgallery.ui.animation.bounceScale

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
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    showFavorite: Boolean = true,
    badgeType: String = "Duration with icon",
    badgeEnabled: Boolean = true,
    thumbnailQuality: String = "Standard"
) {
    // Outer BoxWithConstraints so we can scale the border to thumbnail size
    BoxWithConstraints(
        modifier = modifier
            .bounceScale()
            .aspectRatio(1f)
    ) {
        // Derive border width from thumbnail size:
        // 3-col (≥80dp) → 16dp | 4-col (≥65dp) → 11dp | 6-col (≥52dp) → 8dp | 9-col (<52dp) → 5dp
        val targetBorderWidth = when {
            !isSelected -> 0.dp
            maxWidth >= 80.dp -> 16.dp
            maxWidth >= 65.dp -> 11.dp
            maxWidth >= 52.dp -> 8.dp
            else -> 5.dp
        }
        // Corner radius for the inner image also scales with size
        val innerCornerRadius = when {
            maxWidth >= 80.dp -> 24.dp
            maxWidth >= 65.dp -> 16.dp
            maxWidth >= 52.dp -> 12.dp
            else -> 8.dp
        }

        // Different animation for selection vs deselection for better visibility
        val borderWidthRaw by animateDpAsState(
            targetValue = targetBorderWidth,
            animationSpec = if (isSelected) {
                spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            } else {
                tween(
                    durationMillis = 350,
                    easing = FastOutSlowInEasing
                )
            },
            label = "borderWidth"
        )

        // CRITICAL: Clamp to 0dp minimum to prevent negative border width
        val borderWidth = borderWidthRaw.coerceAtLeast(0.dp)

        val borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)

        // Use scaled corner radius for inner shape when selected
        val innerShape = if (isSelected) RoundedCornerShape(innerCornerRadius) else shape

        // Staggered load animation
        var isLoaded by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            isLoaded = true
        }

        val entryAlpha by animateFloatAsState(
            targetValue = if (isLoaded) 1f else 0f,
            animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing),
            label = "entryAlpha"
        )

        val entryOffsetY by animateDpAsState(
            targetValue = if (isLoaded) 0.dp else 16.dp,
            animationSpec = spring(
                dampingRatio = 0.8f,
                stiffness = Spring.StiffnessLow
            ),
            label = "entryOffsetY"
        )

        // Capture maxWidth before entering the inner Box (BoxWithConstraintsScope only, not BoxScope)
        val thumbnailWidth = maxWidth
        val isSmall = thumbnailWidth < 80.dp
        // Precompute checkmark size/padding here while still in BoxWithConstraintsScope
        val checkSize = when {
            thumbnailWidth >= 80.dp -> 24.dp
            thumbnailWidth >= 65.dp -> 20.dp
            thumbnailWidth >= 52.dp -> 17.dp
            else -> 14.dp
        }
        val checkPadding = when {
            thumbnailWidth >= 80.dp -> 8.dp
            thumbnailWidth >= 65.dp -> 5.dp
            thumbnailWidth >= 52.dp -> 4.dp
            else -> 3.dp
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = entryAlpha
                    translationY = entryOffsetY.toPx()
                }
                .then(
                    if (borderWidth > 0.dp) {
                        Modifier.background(color = borderColor, shape = shape)
                    } else Modifier
                )
        ) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val density = androidx.compose.ui.platform.LocalDensity.current.density
        
        // Calculate target thumbnail size based on quality setting
        val targetSize = remember(thumbnailQuality, density) {
            when (thumbnailQuality) {
                "High" -> coil.size.Size.ORIGINAL  // Full resolution, highest quality
                "Standard" -> coil.size.Size(512, 512)  // 512x512 balanced quality
                "Automatic" -> {
                    // Auto mode: choose based on device capabilities
                    val activityManager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                    val memoryClass = activityManager.memoryClass
                    
                    // High-end: 512MB+ RAM and high density (xxhdpi+)
                    // Mid-range: Use standard 512x512
                    // Low-end: Use smaller 384x384
                    when {
                        memoryClass >= 512 && density >= 3.0f -> coil.size.Size(768, 768)
                        memoryClass >= 256 -> coil.size.Size(512, 512)
                        else -> coil.size.Size(384, 384)
                    }
                }
                else -> coil.size.Size(512, 512)  // Default to Standard
            }
        }
        
        val viewConfiguration = androidx.compose.ui.platform.LocalViewConfiguration.current
        var downTime by remember { mutableLongStateOf(0L) }
        
        val clickModifier = Modifier
            .fillMaxSize()
            .padding(borderWidth)
            .then(if (isSelectionMode && !isSelected) Modifier.alpha(0.6f) else Modifier)
            .clip(innerShape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    downTime = System.currentTimeMillis()
                }
            }
            .combinedClickable(
                onClick = {
                    if (System.currentTimeMillis() - downTime < viewConfiguration.longPressTimeoutMillis + 100L) {
                        onClick()
                    }
                },
                onLongClick = onLongClick
            )

        val thumbnailData = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            com.prantiux.pixelgallery.image.MediaThumbnailRequest(item.uri, item.isVideo)
        } else {
            item.uri
        }

        val imageRequest = remember(thumbnailData, targetSize, context) {
            coil.request.ImageRequest.Builder(context)
                .data(thumbnailData)
                .size(targetSize)
                .crossfade(false)
                .build()
        }

        val imageModifier = clickModifier

        AsyncImage(
            model = imageRequest,
            contentDescription = item.displayName,
            modifier = imageModifier,
            contentScale = ContentScale.Crop
        )
        
        val overlayPadding = when {
            thumbnailWidth >= 80.dp -> 6.dp
            thumbnailWidth >= 65.dp -> 5.dp
            thumbnailWidth >= 52.dp -> 4.dp
            else -> 3.dp
        }

        // Video duration pill (canonical implementation)
        if (item.isVideo && badgeEnabled) {
            VideoDurationPill(
                duration = item.duration,
                badgeType = badgeType,
                thumbnailWidth = thumbnailWidth,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(if (isSelected) borderWidth + overlayPadding else overlayPadding)
            )
        }
        
        // Favorite star badge (canonical implementation)
        if (item.isFavorite && showFavorite && !isSelectionMode) {
            FavoriteStarBadge(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(if (isSelected) borderWidth + overlayPadding else overlayPadding)
            )
        }
        
        // Selection indicator (canonical implementation)
        if (isSelectionMode) {
            SelectionCheckmark(
                isSelected = isSelected,
                size = checkSize,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(checkPadding)
            )
        }
    } // close inner Box
    } // close outer BoxWithConstraints
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
    modifier: Modifier = Modifier,
    badgeType: String = "Duration with icon",
    thumbnailWidth: androidx.compose.ui.unit.Dp = 100.dp
) {
    val paddingHorizontal = when {
        thumbnailWidth >= 100.dp -> 6.dp
        thumbnailWidth >= 80.dp -> 5.dp
        thumbnailWidth >= 65.dp -> 4.dp
        thumbnailWidth >= 52.dp -> 3.dp
        else -> 2.dp
    }
    val paddingVertical = when {
        thumbnailWidth >= 100.dp -> 3.dp
        thumbnailWidth >= 80.dp -> 2.5.dp
        thumbnailWidth >= 65.dp -> 2.dp
        else -> 1.5.dp
    }
    val iconSize = when {
        thumbnailWidth >= 100.dp -> 13.sp
        thumbnailWidth >= 80.dp -> 11.sp
        thumbnailWidth >= 65.dp -> 9.sp
        thumbnailWidth >= 52.dp -> 8.sp
        else -> 7.sp
    }
    val fontSize = when {
        thumbnailWidth >= 100.dp -> 11.sp
        thumbnailWidth >= 80.dp -> 9.sp
        thumbnailWidth >= 65.dp -> 8.sp
        thumbnailWidth >= 52.dp -> 7.sp
        else -> 6.sp
    }
    val spacing = 1.dp

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing)
    ) {
        when (badgeType) {
            "Duration with icon" -> {
                val pillHeight = when {
                    thumbnailWidth >= 100.dp -> 20.dp
                    thumbnailWidth >= 80.dp -> 17.dp
                    thumbnailWidth >= 65.dp -> 15.dp
                    thumbnailWidth >= 52.dp -> 13.dp
                    else -> 11.dp
                }
                val iconPillWidth = when {
                    thumbnailWidth >= 100.dp -> 24.dp
                    thumbnailWidth >= 80.dp -> 20.dp
                    thumbnailWidth >= 65.dp -> 17.dp
                    thumbnailWidth >= 52.dp -> 15.dp
                    else -> 13.dp
                }
                
                // Left half: Icon
                Box(
                    modifier = Modifier
                        .height(pillHeight)
                        .width(iconPillWidth)
                        .background(
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(
                                topStartPercent = 50, 
                                bottomStartPercent = 50, 
                                topEndPercent = 15, 
                                bottomEndPercent = 15
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    FontIcon(
                        unicode = FontIcons.PlayArrow,
                        contentDescription = "Video",
                        size = iconSize,
                        tint = Color.White,
                        filled = true
                    )
                }
                
                // Right half: Duration
                Box(
                    modifier = Modifier
                        .height(pillHeight)
                        .background(
                            color = Color.Black.copy(alpha = 0.65f), // Match icon transparency
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(
                                topStartPercent = 15, 
                                bottomStartPercent = 15, 
                                topEndPercent = 50, 
                                bottomEndPercent = 50
                            )
                        )
                        .padding(horizontal = paddingHorizontal + 1.dp), 
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = formatDuration(duration),
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = fontSize,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
            "Icon only" -> {
                Box(
                    modifier = Modifier
                        .background(
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
                        )
                        .padding(horizontal = paddingHorizontal, vertical = paddingVertical),
                    contentAlignment = Alignment.Center
                ) {
                    FontIcon(
                        unicode = FontIcons.PlayArrow,
                        contentDescription = "Video",
                        size = iconSize,
                        tint = Color.White,
                        filled = true
                    )
                }
            }
            "Duration only" -> {
                Box(
                    modifier = Modifier
                        .background(
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
                        )
                        .padding(horizontal = paddingHorizontal, vertical = paddingVertical),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = formatDuration(duration),
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = fontSize,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }
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
        filled = true,
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
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 24.dp
) {
    val iconSize = (size.value * 0.65f).sp
    Box(
        modifier = modifier
            .size(size)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 1.0f) 
                else Color.Transparent,
                CircleShape
            )
            .border(
                width = if (isSelected) 0.dp else (size.value * 0.085f).dp,
                color = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.onSurfaceVariant,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            FontIcon(
                unicode = FontIcons.Done,
                contentDescription = "Selected",
                size = iconSize,
                tint = MaterialTheme.colorScheme.onPrimary
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
