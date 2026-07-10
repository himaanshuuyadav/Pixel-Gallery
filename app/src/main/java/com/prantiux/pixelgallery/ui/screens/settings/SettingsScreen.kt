package com.prantiux.pixelgallery.ui.screens.settings

import com.prantiux.pixelgallery.ui.utils.rememberZenithFlingBehavior
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prantiux.pixelgallery.ui.components.ConsistentHeader
import com.prantiux.pixelgallery.ui.components.SubPageScaffold
import com.prantiux.pixelgallery.ui.utils.calculateFloatingNavBarHeight
import com.prantiux.pixelgallery.ui.utils.bounceClick
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: com.prantiux.pixelgallery.viewmodel.MediaViewModel,
    onNavigateToGridType: () -> Unit = {},
    onNavigateToGalleryView: () -> Unit = {},
    onNavigateToTheme: () -> Unit = {},
    onNavigateToPreviews: () -> Unit = {},
    onNavigateToGestures: () -> Unit = {},
    onNavigateToPlayback: () -> Unit = {},
    onNavigateToDebug: () -> Unit = {},
    onBackClick: () -> Unit = {}
) {
    // Get current grid type
    val currentGridType by viewModel.gridType.collectAsState()
    val gridTypeText = when (currentGridType) {
        com.prantiux.pixelgallery.viewmodel.GridType.DAY_3 -> "Day · 3"
        com.prantiux.pixelgallery.viewmodel.GridType.DAY_4 -> "Day · 4"
        com.prantiux.pixelgallery.viewmodel.GridType.MONTH_6 -> "Month · 6"
        com.prantiux.pixelgallery.viewmodel.GridType.MONTH_9 -> "Month · 9"
    }
    
    SubPageScaffold(
        title = "Settings",
        subtitle = "Customize your gallery experience",
        onNavigateBack = onBackClick
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
                    title = "Photos view",
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
                    onClick = onNavigateToPreviews
                )
            }
        }
        
        // Interaction Section
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
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
                    onClick = onNavigateToGestures
                )
                GroupedSettingItem(
                    title = "Playback",
                    subtitle = "Video and audio settings",
                    iconUnicode = FontIcons.PlayArrow,
                    position = SettingPosition.MIDDLE,
                    onClick = onNavigateToPlayback
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
            Spacer(modifier = Modifier.height(8.dp))
        }
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
            Spacer(modifier = Modifier.height(8.dp))
        }
        item {
            CategoryHeader("Support")
        }
        item {
            SettingsGroup {
                GroupedSettingItem(
                    title = "About and help",
                    subtitle = "App info and support",
                    iconUnicode = FontIcons.Info,
                    position = SettingPosition.TOP,
                    onClick = { /* TODO */ }
                )
                GroupedSettingItem(
                    title = "Debug",
                    subtitle = "Developer tools",
                    iconUnicode = FontIcons.Settings,
                    position = SettingPosition.BOTTOM,
                    onClick = onNavigateToDebug
                )
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
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 12.dp)
    )
}

@Composable
internal fun SettingsGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        content()
    }
}

@Composable
internal fun GroupedSettingItem(
    title: String,
    subtitle: String = "",
    iconUnicode: String,
    position: SettingPosition,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val shape = com.prantiux.pixelgallery.ui.theme.ExpressiveListShape(
        when (position) {
            SettingPosition.TOP -> com.prantiux.pixelgallery.ui.theme.ListItemPosition.TOP
            SettingPosition.MIDDLE -> com.prantiux.pixelgallery.ui.theme.ListItemPosition.MIDDLE
            SettingPosition.BOTTOM -> com.prantiux.pixelgallery.ui.theme.ListItemPosition.BOTTOM
            SettingPosition.SINGLE -> com.prantiux.pixelgallery.ui.theme.ListItemPosition.SINGLE
        }
    )
    
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        enabled = enabled,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .bounceClick()
            .fillMaxWidth()
    ) {
        val isPressed by interactionSource.collectIsPressedAsState()
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FontIcon(
                unicode = iconUnicode,
                contentDescription = null,
                size = 24.sp,
                tint = if (enabled) MaterialTheme.colorScheme.primary 
                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                filled = isPressed
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface 
                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    )
                }
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
    val haptic = LocalHapticFeedback.current
    val shape = com.prantiux.pixelgallery.ui.theme.ExpressiveListShape(
        when (position) {
            SettingPosition.TOP -> com.prantiux.pixelgallery.ui.theme.ListItemPosition.TOP
            SettingPosition.MIDDLE -> com.prantiux.pixelgallery.ui.theme.ListItemPosition.MIDDLE
            SettingPosition.BOTTOM -> com.prantiux.pixelgallery.ui.theme.ListItemPosition.BOTTOM
            SettingPosition.SINGLE -> com.prantiux.pixelgallery.ui.theme.ListItemPosition.SINGLE
        }
    )

    val handleCheckedChange: (Boolean) -> Unit = { newValue ->
        if (enabled && newValue != checked) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        if (enabled) {
            onCheckedChange(newValue)
        }
    }
    
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

    Surface(
        onClick = { handleCheckedChange(!checked) },
        interactionSource = interactionSource,
        enabled = enabled,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .bounceClick()
            .fillMaxWidth()
    ) {
        val isPressed by interactionSource.collectIsPressedAsState()
        
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
                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                filled = checked || isPressed
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
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
                onCheckedChange = { handleCheckedChange(it) },
                enabled = enabled
            )
        }
    }
}
