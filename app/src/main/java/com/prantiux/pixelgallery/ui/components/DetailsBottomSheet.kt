@file:OptIn(ExperimentalMaterial3Api::class)

package com.prantiux.pixelgallery.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.prantiux.pixelgallery.model.MediaItem
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

/**
 * Details Bottom Sheet Component
 * 
 * Modern Material 3 bottom sheet displaying comprehensive media metadata.
 * Designed to be ~50-60% screen height with scrollable content.
 * 
 * Features:
 * - Clean card-based layout
 * - Conditional location section
 * - Dynamic theme colors (no hardcoded values)
 * - Smooth animations
 * - Lightweight static map preview
 */
@Composable
fun DetailsBottomSheet(
    mediaItem: MediaItem,
    onDismiss: () -> Unit,
    onEditMetadata: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false
    )
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shape = RoundedCornerShape(
            topStart = 28.dp,
            topEnd = 28.dp,
            bottomStart = 0.dp,
            bottomEnd = 0.dp
        ),
        dragHandle = {
            // Custom drag handle with theme-based color
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(48.dp)
                    .height(4.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        },
        modifier = modifier.heightIn(max = screenHeight * 0.6f) // Limit to 60% screen height
    ) {
        DetailsBottomSheetContent(
            mediaItem = mediaItem,
            onEditMetadata = onEditMetadata
        )
    }
}

/**
 * Details Bottom Sheet Content
 * 
 * Reusable content for displaying media metadata in a scrollable column.
 * Can be used inside any container (modal sheet or inline surface).
 */
@Composable
fun DetailsBottomSheetContent(
    mediaItem: MediaItem,
    onEditMetadata: () -> Unit
) {
    // Log GPS data for debugging
    if (com.prantiux.pixelgallery.BuildConfig.DEBUG) android.util.Log.d("DetailsBottomSheet", "=== GPS DATA DEBUG ===")
    if (com.prantiux.pixelgallery.BuildConfig.DEBUG) android.util.Log.d("DetailsBottomSheet", "Media: ${mediaItem.displayName}")
    if (com.prantiux.pixelgallery.BuildConfig.DEBUG) android.util.Log.d("DetailsBottomSheet", "Latitude: ${mediaItem.latitude}")
    if (com.prantiux.pixelgallery.BuildConfig.DEBUG) android.util.Log.d("DetailsBottomSheet", "Longitude: ${mediaItem.longitude}")
    if (com.prantiux.pixelgallery.BuildConfig.DEBUG) android.util.Log.d("DetailsBottomSheet", "Location: ${mediaItem.location}")
    if (com.prantiux.pixelgallery.BuildConfig.DEBUG) android.util.Log.d("DetailsBottomSheet", "Has GPS: ${mediaItem.latitude != null && mediaItem.longitude != null}")
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        // Scrollable content area
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)  // Takes remaining space, scrollable
                  .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
        // Header Row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp),  // Reduced padding
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Details",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                // Optional action icon (share)
                IconButton(onClick = onEditMetadata) {
                    FontIcon(
                        unicode = FontIcons.Edit,
                        contentDescription = "Edit metadata",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        size = 22.sp
                    )
                }
            }
        }
        
        val dateFormat = SimpleDateFormat("MMM dd, yyyy • h:mm a", Locale.getDefault())
        val formattedDate = dateFormat.format(Date(mediaItem.dateAdded * 1000))

        // Grouped metadata cards (Settings-style)
        item {
            DetailInfoCard(
                icon = FontIcons.Image,
                label = "Name",
                value = mediaItem.displayName,
                position = DetailCardPosition.TOP
            )
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
        }

        item {
            DetailInfoCard(
                icon = FontIcons.CalendarToday,
                label = "Date Taken",
                value = formattedDate,
                position = DetailCardPosition.MIDDLE
            )
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
        }

        item {
            SpecsInfoCard(
                mediaItem = mediaItem,
                position = DetailCardPosition.BOTTOM
            )
        }
        
        // Location Section (ONLY if GPS data exists)
        if (mediaItem.latitude != null && mediaItem.longitude != null) {
            item {
                LocationCard(
                    latitude = mediaItem.latitude,
                    longitude = mediaItem.longitude,
                    locationName = mediaItem.location ?: "${String.format("%.6f", mediaItem.latitude)}, ${String.format("%.6f", mediaItem.longitude)}"
                )
            }
        }
        
        // File Path Card
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            FilePathCard(filePath = mediaItem.path)
        }
        
        // Bottom padding for content
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
        
    }
}

private enum class DetailCardPosition {
    TOP, MIDDLE, BOTTOM, SINGLE
}

@Composable
private fun DetailInfoCard(
    icon: String,
    label: String,
    value: String,
    position: DetailCardPosition
) {
    Surface(
        shape = detailCardShape(position),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FontIcon(
                unicode = icon,
                contentDescription = null,
                size = 24.sp,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SpecsInfoCard(
    mediaItem: MediaItem,
    position: DetailCardPosition
) {
    val sizeKB = mediaItem.size / 1024
    val sizeFormatted = if (sizeKB > 1024) {
        String.format("%.1f MB", sizeKB / 1024.0)
    } else {
        "$sizeKB KB"
    }

    Surface(
        shape = detailCardShape(position),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FontIcon(
                unicode = FontIcons.Info,
                contentDescription = null,
                size = 24.sp,
                tint = MaterialTheme.colorScheme.primary
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Specs",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    SpecPill(text = sizeFormatted)
                    if (!mediaItem.isVideo && mediaItem.width > 0 && mediaItem.height > 0) {
                        val megapixels = (mediaItem.width * mediaItem.height) / 1_000_000.0
                        SpecPill(text = String.format("%.1f MP", megapixels))
                    }
                    if (mediaItem.width > 0 && mediaItem.height > 0) {
                        SpecPill(text = "${mediaItem.width} × ${mediaItem.height}")
                    }
                }
            }
        }
    }
}

private fun detailCardShape(position: DetailCardPosition): RoundedCornerShape {
    return when (position) {
        DetailCardPosition.TOP -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
        DetailCardPosition.MIDDLE -> RoundedCornerShape(12.dp)
        DetailCardPosition.BOTTOM -> RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        DetailCardPosition.SINGLE -> RoundedCornerShape(24.dp)
    }
}

/**
 * Spec Pill Component
 * Small pill-style tag for file specs
 */
@Composable
private fun SpecPill(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
        modifier = Modifier.height(28.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Location Card Component
 * Shows map preview with pin marker and location name
 */
@Composable
private fun LocationCard(
    latitude: Double,
    longitude: Double,
    locationName: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Static map preview (lightweight)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)  // Reduced from 140dp for compactness
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                // Static map from OpenStreetMap or Google Static Maps API
                // Using OpenStreetMap static tile as it's free and lightweight
                val zoom = 13
                val mapUrl = "https://tile.openstreetmap.org/$zoom/" +
                        "${lon2tile(longitude, zoom)}/" +
                        "${lat2tile(latitude, zoom)}.png"
                
                // Map image
                AsyncImage(
                    model = mapUrl,
                    contentDescription = "Location map",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                
                // Pin marker overlay
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp),
                        shadowElevation = 4.dp
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            FontIcon(
                                unicode = FontIcons.LocationOn,
                                contentDescription = "Location pin",
                                tint = MaterialTheme.colorScheme.onError,
                                size = 20.sp,
                                filled = true
                            )
                        }
                    }
                }
            }
            
            // Location name below map
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),  // Reduced from 16dp
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FontIcon(
                    unicode = FontIcons.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    size = 20.sp,
                    filled = true
                )
                
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = locationName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${String.format("%.6f", latitude)}, ${String.format("%.6f", longitude)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * File Path Card Component
 * Shows file location path
 */
@Composable
private fun FilePathCard(filePath: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = detailCardShape(DetailCardPosition.SINGLE),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Folder icon
            FontIcon(
                unicode = FontIcons.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                size = 24.sp
            )
            
            // Path text
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "File Location",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = filePath,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// Utility functions for map tile calculation
private fun lon2tile(lon: Double, zoom: Int): Int {
    return ((lon + 180.0) / 360.0 * (1 shl zoom)).toInt()
}

private fun lat2tile(lat: Double, zoom: Int): Int {
    return ((1.0 - kotlin.math.ln(
        kotlin.math.tan(lat * Math.PI / 180.0) + 
        1.0 / kotlin.math.cos(lat * Math.PI / 180.0)
    ) / Math.PI) / 2.0 * (1 shl zoom)).toInt()
}
