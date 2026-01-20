package com.prantiux.pixelgallery.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prantiux.pixelgallery.ui.components.ConsistentHeader
import com.prantiux.pixelgallery.ui.utils.calculateFloatingNavBarHeight
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: com.prantiux.pixelgallery.viewmodel.MediaViewModel,
    onNavigateToGridType: () -> Unit = {},
    onNavigateToGalleryView: () -> Unit = {},
    onNavigateToTheme: () -> Unit = {},
    onBackClick: () -> Unit = {}
) {
    // Get current grid type
    val currentGridType by viewModel.gridType.collectAsState()
    val gridTypeText = when (currentGridType) {
        com.prantiux.pixelgallery.viewmodel.GridType.DAY -> "Day"
        com.prantiux.pixelgallery.viewmodel.GridType.MONTH -> "Month"
    }
    
    val navBarHeight = calculateFloatingNavBarHeight()
    
    Column(modifier = Modifier.fillMaxSize()) {
        com.prantiux.pixelgallery.ui.components.MainTabHeader(
            title = "Settings"
        )
        
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ),
            contentPadding = PaddingValues(top = 16.dp, bottom = navBarHeight + 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Appearance Section
            item {
                CategoryHeader("Appearance")
            }
            item {
                SettingsGroup {
                    GroupedSettingItem(
                        title = "Layout",
                        subtitle = gridTypeText,
                        iconUnicode = FontIcons.GridView,
                        position = SettingPosition.TOP,
                        onClick = onNavigateToGridType
                    )
                    GroupedSettingItem(
                        title = "Gallery view",
                        subtitle = "Choose folders to show",
                        iconUnicode = FontIcons.Folder,
                        position = SettingPosition.MIDDLE,
                        onClick = onNavigateToGalleryView
                    )
                    GroupedSettingItem(
                        title = "Theme",
                        subtitle = "System default",
                        iconUnicode = FontIcons.Palette,
                        position = SettingPosition.MIDDLE,
                        onClick = onNavigateToTheme
                    )
                    GroupedSettingItem(
                        title = "Previews",
                        subtitle = "Thumbnail and media settings",
                        iconUnicode = FontIcons.Image,
                        position = SettingPosition.BOTTOM,
                        onClick = { /* TODO */ }
                    )
                }
            }
            
            // Interaction Section
            item {
                CategoryHeader("Interaction")
            }
            item {
                SettingsGroup {
                    GroupedSettingItem(
                        title = "Gestures",
                        subtitle = "Swipe and tap controls",
                        iconUnicode = FontIcons.SwipeDown,
                        position = SettingPosition.TOP,
                        onClick = { /* TODO */ }
                    )
                    GroupedSettingItem(
                        title = "Playback",
                        subtitle = "Video and audio settings",
                        iconUnicode = FontIcons.PlayArrow,
                        position = SettingPosition.MIDDLE,
                        onClick = { /* TODO */ }
                    )
                    GroupedSettingItem(
                        title = "Viewing",
                        subtitle = "Display and zoom preferences",
                        iconUnicode = FontIcons.ZoomIn,
                        position = SettingPosition.BOTTOM,
                        onClick = { /* TODO */ }
                    )
                }
            }
            
            // Storage and Privacy Section
            item {
                CategoryHeader("Storage and Privacy")
            }
            item {
                SettingsGroup {
                    GroupedSettingItem(
                        title = "Storage",
                        subtitle = "Manage space and cache",
                        iconUnicode = FontIcons.Storage,
                        position = SettingPosition.TOP,
                        onClick = { /* TODO */ }
                    )
                    GroupedSettingItem(
                        title = "Hidden",
                        subtitle = "Hidden albums and items",
                        iconUnicode = FontIcons.VisibilityOff,
                        position = SettingPosition.MIDDLE,
                        onClick = { /* TODO */ }
                    )
                    GroupedSettingItem(
                        title = "Performance",
                        subtitle = "Optimize app performance",
                        iconUnicode = FontIcons.Settings,
                        position = SettingPosition.BOTTOM,
                        onClick = { /* TODO */ }
                    )
                }
            }
            
            // Support Section
            item {
                CategoryHeader("Support")
            }
            item {
                SettingsGroup {
                    GroupedSettingItem(
                        title = "About and help",
                        subtitle = "App info and support",
                        iconUnicode = FontIcons.Info,
                        position = SettingPosition.SINGLE,
                        onClick = { /* TODO */ }
                    )
                }
            }
        }
    }
}

enum class SettingPosition {
    TOP, MIDDLE, BOTTOM, SINGLE
}

@Composable
internal fun CategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 12.dp)
    )
}

@Composable
internal fun SettingsGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        content()
    }
}

@Composable
internal fun GroupedSettingItem(
    title: String,
    subtitle: String,
    iconUnicode: String,
    position: SettingPosition,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val shape = when (position) {
        SettingPosition.TOP -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
        SettingPosition.MIDDLE -> RoundedCornerShape(12.dp)
        SettingPosition.BOTTOM -> RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        SettingPosition.SINGLE -> RoundedCornerShape(24.dp)
    }
    
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FontIcon(
                unicode = iconUnicode,
                contentDescription = null,
                size = 24.sp,
                tint = if (enabled) MaterialTheme.colorScheme.primary 
                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface 
                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant 
                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
            }
            FontIcon(
                unicode = FontIcons.KeyboardArrowRight,
                contentDescription = null,
                size = 24.sp,
                tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant 
                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            )
        }
    }
}

@Composable
internal fun GroupedSettingToggle(
    title: String,
    subtitle: String,
    iconUnicode: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    position: SettingPosition,
    enabled: Boolean = true
) {
    val shape = when (position) {
        SettingPosition.TOP -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
        SettingPosition.MIDDLE -> RoundedCornerShape(8.dp)
        SettingPosition.BOTTOM -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        SettingPosition.SINGLE -> RoundedCornerShape(24.dp)
    }
    
    Surface(
        onClick = { if (enabled) onCheckedChange(!checked) },
        enabled = enabled,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FontIcon(
                unicode = iconUnicode,
                contentDescription = null,
                size = 24.sp,
                tint = if (enabled) MaterialTheme.colorScheme.primary 
                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface 
                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant 
                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}
