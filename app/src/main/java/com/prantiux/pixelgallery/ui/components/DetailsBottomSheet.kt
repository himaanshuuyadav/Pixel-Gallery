@file:OptIn(ExperimentalMaterial3Api::class)

package com.prantiux.pixelgallery.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
    android.util.Log.d("DetailsBottomSheet", "=== GPS DATA DEBUG ===")
    android.util.Log.d("DetailsBottomSheet", "Media: ${mediaItem.displayName}")
    android.util.Log.d("DetailsBottomSheet", "Latitude: ${mediaItem.latitude}")
    android.util.Log.d("DetailsBottomSheet", "Longitude: ${mediaItem.longitude}")
    android.util.Log.d("DetailsBottomSheet", "Location: ${mediaItem.location}")
    android.util.Log.d("DetailsBottomSheet", "Has GPS: ${mediaItem.latitude != null && mediaItem.longitude != null}")
    
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                IconButton(onClick = { /* Share action */ }) {
                    FontIcon(
                        unicode = FontIcons.Share,
                        contentDescription = "Share",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        size = 22.sp
                    )
                }
            }
        }
        
        // Metadata Card (Main Info)
        item {
            MetadataCard(mediaItem = mediaItem)
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
            FilePathCard(filePath = mediaItem.path)
        }
        
        // Bottom padding for content
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
        
        // Sticky button at bottom - always visible above system nav
        Button(
            onClick = onEditMetadata,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 16.dp)
                .height(48.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 2.dp,
                pressedElevation = 4.dp
            )
        ) {
            Text(
                text = "Edit Metadata",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Metadata Card Component
 * Displays file name, date, and specs in a clean card layout
 */
@Composable
private fun MetadataCard(mediaItem: MediaItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Name Row
            MetadataRow(
                icon = FontIcons.Image,
                label = "Name",
                value = mediaItem.displayName,
                showDivider = true
            )
            
            // Date Taken Row
            val dateFormat = SimpleDateFormat("MMM dd, yyyy • h:mm a", Locale.getDefault())
            val formattedDate = dateFormat.format(Date(mediaItem.dateAdded * 1000))
            
            MetadataRow(
                icon = FontIcons.CalendarToday,
                label = "Date Taken",
                value = formattedDate,
                showDivider = true
            )
            
            // Specs Row (pills)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FontIcon(
                    unicode = FontIcons.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    size = 20.sp
                )
                
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Specs",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    
                    // Pills container
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // File size pill
                        val sizeKB = mediaItem.size / 1024
                        val sizeFormatted = if (sizeKB > 1024) {
                            String.format("%.1f MB", sizeKB / 1024.0)
                        } else {
                            "$sizeKB KB"
                        }
                        SpecPill(text = sizeFormatted)
                        
                        // Megapixels pill (for images)
                        if (!mediaItem.isVideo && mediaItem.width > 0 && mediaItem.height > 0) {
                            val megapixels = (mediaItem.width * mediaItem.height) / 1_000_000.0
                            SpecPill(text = String.format("%.1f MP", megapixels))
                        }
                        
                        // Resolution pill
                        if (mediaItem.width > 0 && mediaItem.height > 0) {
                            SpecPill(text = "${mediaItem.width} × ${mediaItem.height}")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Metadata Row Component
 * Single row with icon, label, and value
 */
@Composable
private fun MetadataRow(
    icon: String,
    label: String,
    value: String,
    showDivider: Boolean = false
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Leading icon
            FontIcon(
                unicode = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                size = 20.sp
            )
            
            // Label and value stacked
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
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
        
        // Optional divider
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 32.dp, top = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Folder icon
            FontIcon(
                unicode = FontIcons.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                size = 20.sp
            )
            
            // Path text
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "File Location",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = filePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
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
