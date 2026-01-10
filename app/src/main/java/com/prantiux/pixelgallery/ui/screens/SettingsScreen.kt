package com.prantiux.pixelgallery.ui.screens

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
    onBackClick: () -> Unit = {}
) {
    // Settings states
    var gridSize by remember { mutableStateOf("Medium") }
    var showVideoDuration by remember { mutableStateOf(true) }
    var stickyDateHeaders by remember { mutableStateOf(true) }
    var theme by remember { mutableStateOf("System") }
    var dynamicColor by remember { mutableStateOf(true) }
    var amoledBlack by remember { mutableStateOf(false) }
    var swipeDownToClose by remember { mutableStateOf(true) }
    var doubleTapZoom by remember { mutableStateOf(true) }
    var edgeToEdge by remember { mutableStateOf(true) }
    var autoPlayVideos by remember { mutableStateOf(false) }
    var muteByDefault by remember { mutableStateOf(false) }
    var rememberPosition by remember { mutableStateOf(true) }
    var defaultSort by remember { mutableStateOf("Date") }
    var groupByMonth by remember { mutableStateOf(true) }
    var hideEmptyAlbums by remember { mutableStateOf(true) }
    var appLock by remember { mutableStateOf(false) }
    var hiddenAlbums by remember { mutableStateOf(false) }
    var enableTrash by remember { mutableStateOf(true) }
    var autoDeleteTrash by remember { mutableStateOf(true) }
    
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
            // View & Layout Section
            item {
                CategoryHeader("View & Layout")
            }
            item {
                SettingsGroup {
                    GroupedSettingItem(
                        title = "Grid size",
                        subtitle = gridSize,
                        iconUnicode = FontIcons.GridView,
                        position = SettingPosition.TOP,
                        onClick = { /* TODO: Show grid size dialog */ }
                    )
                    GroupedSettingToggle(
                        title = "Show video duration badge",
                        subtitle = "Display duration on video thumbnails",
                        iconUnicode = FontIcons.Timer,
                        checked = showVideoDuration,
                        onCheckedChange = { showVideoDuration = it },
                        position = SettingPosition.MIDDLE
                    )
                    GroupedSettingToggle(
                        title = "Sticky date headers",
                        subtitle = if (stickyDateHeaders) "On" else "Off",
                        iconUnicode = FontIcons.DateRange,
                        checked = stickyDateHeaders,
                        onCheckedChange = { stickyDateHeaders = it },
                        position = SettingPosition.BOTTOM
                    )
                }
            }
            
            // Appearance Section
            item {
                CategoryHeader("Appearance")
            }
            item {
                SettingsGroup {
                    GroupedSettingItem(
                        title = "Theme",
                        subtitle = theme,
                        iconUnicode = FontIcons.Palette,
                        position = SettingPosition.TOP,
                        onClick = { /* TODO: Show theme dialog */ }
                    )
                    GroupedSettingToggle(
                        title = "Dynamic color (Material You)",
                        subtitle = if (dynamicColor) "On" else "Off",
                        iconUnicode = FontIcons.ColorLens,
                        checked = dynamicColor,
                        onCheckedChange = { dynamicColor = it },
                        position = SettingPosition.MIDDLE
                    )
                    GroupedSettingToggle(
                        title = "AMOLED black mode",
                        subtitle = if (amoledBlack) "On" else "Off",
                        iconUnicode = FontIcons.Brightness2,
                        checked = amoledBlack,
                        onCheckedChange = { amoledBlack = it },
                        position = SettingPosition.BOTTOM
                    )
                }
            }
            
            // Navigation & Gestures Section
            item {
                CategoryHeader("Navigation & Gestures")
            }
            item {
                SettingsGroup {
                    GroupedSettingToggle(
                        title = "Swipe down to close media viewer",
                        subtitle = if (swipeDownToClose) "On" else "Off",
                        iconUnicode = FontIcons.SwipeDown,
                        checked = swipeDownToClose,
                        onCheckedChange = { swipeDownToClose = it },
                        position = SettingPosition.TOP
                    )
                    GroupedSettingToggle(
                        title = "Double-tap to zoom",
                        subtitle = if (doubleTapZoom) "On" else "Off",
                        iconUnicode = FontIcons.ZoomIn,
                        checked = doubleTapZoom,
                        onCheckedChange = { doubleTapZoom = it },
                        position = SettingPosition.MIDDLE
                    )
                    GroupedSettingToggle(
                        title = "Edge-to-edge mode",
                        subtitle = if (edgeToEdge) "On" else "Off",
                        iconUnicode = FontIcons.Fullscreen,
                        checked = edgeToEdge,
                        onCheckedChange = { edgeToEdge = it },
                        position = SettingPosition.BOTTOM
                    )
                }
            }
            
            // Video Player Section
            item {
                CategoryHeader("Video Player")
            }
            item {
                SettingsGroup {
                    GroupedSettingToggle(
                        title = "Auto-play videos",
                        subtitle = if (autoPlayVideos) "On" else "Off",
                        iconUnicode = FontIcons.PlayArrow,
                        checked = autoPlayVideos,
                        onCheckedChange = { autoPlayVideos = it },
                        position = SettingPosition.TOP
                    )
                    GroupedSettingToggle(
                        title = "Mute videos by default",
                        subtitle = if (muteByDefault) "On" else "Off",
                        iconUnicode = FontIcons.VolumeOff,
                        checked = muteByDefault,
                        onCheckedChange = { muteByDefault = it },
                        position = SettingPosition.MIDDLE
                    )
                    GroupedSettingToggle(
                        title = "Remember playback position",
                        subtitle = if (rememberPosition) "On" else "Off",
                        iconUnicode = FontIcons.History,
                        checked = rememberPosition,
                        onCheckedChange = { rememberPosition = it },
                        position = SettingPosition.BOTTOM
                    )
                }
            }
            
            // Sorting & Albums Section
            item {
                CategoryHeader("Sorting & Albums")
            }
            item {
                SettingsGroup {
                    GroupedSettingItem(
                        title = "Default sort order",
                        subtitle = defaultSort,
                        iconUnicode = FontIcons.Sort,
                        position = SettingPosition.TOP,
                        onClick = { /* TODO: Show sort dialog */ }
                    )
                    GroupedSettingToggle(
                        title = "Group media by month",
                        subtitle = if (groupByMonth) "On" else "Off",
                        iconUnicode = FontIcons.CalendarMonth,
                        checked = groupByMonth,
                        onCheckedChange = { groupByMonth = it },
                        position = SettingPosition.MIDDLE
                    )
                    GroupedSettingToggle(
                        title = "Hide empty albums",
                        subtitle = if (hideEmptyAlbums) "On" else "Off",
                        iconUnicode = FontIcons.FolderOff,
                        checked = hideEmptyAlbums,
                        onCheckedChange = { hideEmptyAlbums = it },
                        position = SettingPosition.BOTTOM
                    )
                }
            }
            
            // Privacy Section
            item {
                CategoryHeader("Privacy")
            }
            item {
                SettingsGroup {
                    GroupedSettingToggle(
                        title = "App lock",
                        subtitle = "Biometric / PIN",
                        iconUnicode = FontIcons.Lock,
                        checked = appLock,
                        onCheckedChange = { appLock = it },
                        position = SettingPosition.TOP
                    )
                    GroupedSettingItem(
                        title = "Hidden albums",
                        subtitle = "Manage hidden folders",
                        iconUnicode = FontIcons.VisibilityOff,
                        position = SettingPosition.BOTTOM,
                        onClick = { /* TODO */ }
                    )
                }
            }
            
            // Trash Section
            item {
                CategoryHeader("Trash")
            }
            item {
                SettingsGroup {
                    GroupedSettingToggle(
                        title = "Enable trash bin",
                        subtitle = if (enableTrash) "On" else "Off",
                        iconUnicode = FontIcons.Delete,
                        checked = enableTrash,
                        onCheckedChange = { enableTrash = it },
                        position = SettingPosition.TOP
                    )
                    GroupedSettingToggle(
                        title = "Auto-delete trash after 30 days",
                        subtitle = if (autoDeleteTrash) "On" else "Off",
                        iconUnicode = FontIcons.DeleteSweep,
                        checked = autoDeleteTrash,
                        onCheckedChange = { autoDeleteTrash = it },
                        position = SettingPosition.BOTTOM,
                        enabled = enableTrash
                    )
                }
            }
            
            // Storage Section
            item {
                CategoryHeader("Storage")
            }
            item {
                SettingsGroup {
                    GroupedSettingItem(
                        title = "Storage usage summary",
                        subtitle = "View storage breakdown",
                        iconUnicode = FontIcons.Storage,
                        position = SettingPosition.TOP,
                        onClick = { /* TODO */ }
                    )
                    GroupedSettingItem(
                        title = "Clear cache",
                        subtitle = "Remove temporary files",
                        iconUnicode = FontIcons.CleaningServices,
                        position = SettingPosition.BOTTOM,
                        onClick = { /* TODO */ }
                    )
                }
            }
            
            // About Section
            item {
                CategoryHeader("About")
            }
            item {
                SettingsGroup {
                    GroupedSettingItem(
                        title = "App version",
                        subtitle = "1.0.0",
                        iconUnicode = FontIcons.Info,
                        position = SettingPosition.TOP,
                        onClick = { /* TODO */ }
                    )
                    GroupedSettingItem(
                        title = "Send feedback",
                        subtitle = "Share your thoughts",
                        iconUnicode = FontIcons.Feedback,
                        position = SettingPosition.MIDDLE,
                        onClick = { /* TODO */ }
                    )
                    GroupedSettingItem(
                        title = "Privacy policy",
                        subtitle = "View our privacy policy",
                        iconUnicode = FontIcons.PrivacyTip,
                        position = SettingPosition.BOTTOM,
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
private fun CategoryHeader(title: String) {
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
private fun SettingsGroup(content: @Composable () -> Unit) {
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
private fun GroupedSettingItem(
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
private fun GroupedSettingToggle(
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
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}
