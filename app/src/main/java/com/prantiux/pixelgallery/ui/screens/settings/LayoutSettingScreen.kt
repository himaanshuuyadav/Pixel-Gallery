package com.prantiux.pixelgallery.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
    var stickyDateHeaders by remember { mutableStateOf(true) }
    var hideEmptyAlbums by remember { mutableStateOf(false) }
    var defaultTab by remember { mutableStateOf("Last used") }
    var showDefaultTabDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    // Load gesture setting
    LaunchedEffect(Unit) {
        scope.launch {
            settingsDataStore.pinchGestureEnabledFlow.collect { enabled ->
                gestureChangeEnabled = enabled
            }
        }
    }
    
    // Load behavior settings
    LaunchedEffect(Unit) {
        scope.launch {
            settingsDataStore.stickyDateHeadersFlow.collect { enabled ->
                stickyDateHeaders = enabled
            }
        }
    }
    
    LaunchedEffect(Unit) {
        scope.launch {
            settingsDataStore.hideEmptyAlbumsFlow.collect { enabled ->
                hideEmptyAlbums = enabled
            }
        }
    }
    
    LaunchedEffect(Unit) {
        scope.launch {
            settingsDataStore.defaultTabFlow.collect { tab ->
                defaultTab = tab
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
        
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // Behavior category header
        item {
            CategoryHeader("Behavior")
        }
        
        // Sticky date headers
        item {
            GroupedSettingToggle(
                title = "Sticky date headers",
                subtitle = "Keep date headers visible when scrolling",
                iconUnicode = FontIcons.PushPin,
                checked = stickyDateHeaders,
                onCheckedChange = { 
                    stickyDateHeaders = it
                    scope.launch {
                        settingsDataStore.saveStickyDateHeaders(it)
                    }
                },
                position = SettingPosition.TOP
            )
        }
        
        // Hide empty albums
        item {
            GroupedSettingToggle(
                title = "Hide empty albums",
                subtitle = "Don't show albums with no items",
                iconUnicode = FontIcons.VisibilityOff,
                checked = hideEmptyAlbums,
                onCheckedChange = { 
                    hideEmptyAlbums = it
                    scope.launch {
                        settingsDataStore.saveHideEmptyAlbums(it)
                    }
                },
                position = SettingPosition.MIDDLE
            )
        }
        
        // Default tab
        item {
            GroupedSettingItem(
                title = "Default tab",
                subtitle = defaultTab,
                iconUnicode = FontIcons.Tab,
                position = SettingPosition.BOTTOM,
                onClick = { showDefaultTabDialog = true }
            )
        }
    }
    
    // Default tab selection dialog
    if (showDefaultTabDialog) {
        DefaultTabSelectionDialog(
            currentTab = defaultTab,
            onTabSelected = { 
                defaultTab = it
                scope.launch {
                    settingsDataStore.saveDefaultTab(it)
                }
                showDefaultTabDialog = false
            },
            onDismiss = { showDefaultTabDialog = false }
        )
    }
}

@Composable
private fun DefaultTabSelectionDialog(
    currentTab: String,
    onTabSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon at top
                FontIcon(
                    unicode = FontIcons.Tab,
                    contentDescription = null,
                    size = 40.sp,
                    tint = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Title
                Text(
                    text = "Default tab",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Grouped tab options with card-style layout
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Column {
                        TabOption(
                            iconUnicode = FontIcons.Home,
                            label = "Gallery",
                            isSelected = currentTab == "Gallery",
                            onClick = { onTabSelected("Gallery") },
                            position = SettingPosition.TOP
                        )
                        
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        
                        TabOption(
                            iconUnicode = FontIcons.Person,
                            label = "Albums",
                            isSelected = currentTab == "Albums",
                            onClick = { onTabSelected("Albums") },
                            position = SettingPosition.MIDDLE
                        )
                        
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        
                        TabOption(
                            iconUnicode = FontIcons.Search,
                            label = "Search",
                            isSelected = currentTab == "Search",
                            onClick = { onTabSelected("Search") },
                            position = SettingPosition.MIDDLE
                        )
                        
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        
                        TabOption(
                            iconUnicode = FontIcons.History,
                            label = "Last used",
                            isSelected = currentTab == "Last used",
                            onClick = { onTabSelected("Last used") },
                            position = SettingPosition.BOTTOM
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Cancel button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun TabOption(
    iconUnicode: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    position: SettingPosition
) {
    // Apply rounded corners based on position
    val shape = when (position) {
        SettingPosition.TOP -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        SettingPosition.MIDDLE -> RoundedCornerShape(0.dp)
        SettingPosition.BOTTOM -> RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
        SettingPosition.SINGLE -> RoundedCornerShape(16.dp)
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surfaceContainerHighest
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FontIcon(
            unicode = iconUnicode,
            contentDescription = null,
            size = 24.sp,
            tint = if (isSelected) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        
        if (isSelected) {
            FontIcon(
                unicode = FontIcons.Check,
                contentDescription = null,
                size = 24.sp,
                tint = MaterialTheme.colorScheme.primary
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
