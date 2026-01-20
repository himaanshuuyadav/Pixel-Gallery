package com.prantiux.pixelgallery.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prantiux.pixelgallery.ui.components.SubPageScaffold
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons
import com.prantiux.pixelgallery.viewmodel.MediaViewModel
import com.prantiux.pixelgallery.viewmodel.GridType
import com.prantiux.pixelgallery.data.SettingsDataStore
import kotlinx.coroutines.launch

@Composable
fun LayoutSettingScreen(
    viewModel: MediaViewModel,
    settingsDataStore: SettingsDataStore,
    onBackClick: () -> Unit
) {
    val currentGridType by viewModel.gridType.collectAsState()
    var selectedGridType by remember { mutableStateOf(currentGridType) }
    var gestureChangeEnabled by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    // Load gesture setting
    LaunchedEffect(Unit) {
        scope.launch {
            settingsDataStore.pinchGestureEnabledFlow.collect { enabled ->
                gestureChangeEnabled = enabled
            }
        }
    }
    
    // Save setting on back
    BackHandler {
        viewModel.setGridType(selectedGridType)
        onBackClick()
    }
    
    SubPageScaffold(
        title = "Layout",
        subtitle = "Choose how to group your photos",
        onNavigateBack = {
            viewModel.setGridType(selectedGridType)
            onBackClick()
        }
    ) {
        // Category header
        item {
            CategoryHeader("Grid type")
        }
        
        // Day option
        item {
            GridTypeOption(
                title = "Day",
                subtitle = "Group photos by day",
                iconUnicode = FontIcons.Today,
                isSelected = selectedGridType == GridType.DAY,
                position = SettingPosition.TOP,
                onClick = { selectedGridType = GridType.DAY }
            )
        }
        
        // Month option
        item {
            GridTypeOption(
                title = "Month",
                subtitle = "Group photos by month",
                iconUnicode = FontIcons.CalendarMonth,
                isSelected = selectedGridType == GridType.MONTH,
                position = SettingPosition.BOTTOM,
                onClick = { selectedGridType = GridType.MONTH }
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // Gesture toggle - separated with all rounded corners
        item {
            GroupedSettingToggle(
                title = "Gestures over grid",
                subtitle = "Pinch to zoom and switch layout",
                iconUnicode = FontIcons.ZoomIn,
                checked = gestureChangeEnabled,
                onCheckedChange = { 
                    gestureChangeEnabled = it
                    scope.launch {
                        settingsDataStore.savePinchGestureEnabled(it)
                    }
                },
                position = SettingPosition.SINGLE
            )
        }
    }
}

@Composable
private fun GridTypeOption(
    title: String,
    subtitle: String,
    iconUnicode: String,
    isSelected: Boolean,
    position: SettingPosition,
    onClick: () -> Unit
) {
    val shape = when (position) {
        SettingPosition.TOP -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
        SettingPosition.MIDDLE -> RoundedCornerShape(12.dp)
        SettingPosition.BOTTOM -> RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        SettingPosition.SINGLE -> RoundedCornerShape(24.dp)
    }
    
    Surface(
        onClick = onClick,
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
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Radio button
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .border(
                        width = 2.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary 
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        shape = CircleShape
                    )
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary 
                        else MaterialTheme.colorScheme.surface,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(MaterialTheme.colorScheme.onPrimary, CircleShape)
                    )
                }
            }
        }
    }
}
