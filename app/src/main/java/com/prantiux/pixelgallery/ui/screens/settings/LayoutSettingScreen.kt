package com.prantiux.pixelgallery.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prantiux.pixelgallery.ui.components.SubPageScaffold
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons
import com.prantiux.pixelgallery.viewmodel.MediaViewModel
import com.prantiux.pixelgallery.viewmodel.GridType
import com.prantiux.pixelgallery.data.SettingsDataStore
import com.prantiux.pixelgallery.ui.utils.bounceClick
import kotlinx.coroutines.launch

@Composable
fun LayoutSettingScreen(
    viewModel: MediaViewModel,
    settingsDataStore: SettingsDataStore,
    onBackClick: () -> Unit
) {
    val currentGridType by viewModel.gridType.collectAsState()
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

    var gridTypeExpanded by remember { mutableStateOf(false) }
    var gridSizeExpanded by remember { mutableStateOf(false) }

    val gridTypeRotation by animateFloatAsState(
        targetValue = if (gridTypeExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "gridTypeRotation"
    )

    val gridSizeRotation by animateFloatAsState(
        targetValue = if (gridSizeExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "gridSizeRotation"
    )

    val isDayMode = currentGridType.isDay
    val currentGridTypeName = if (isDayMode) "Day" else "Month"
    val currentGridSizeName = when (currentGridType) {
        GridType.DAY_3 -> "3"
        GridType.DAY_4 -> "4"
        GridType.MONTH_6 -> "6"
        GridType.MONTH_9 -> "9"
    }

    SubPageScaffold(
        title = "Layout",
        subtitle = "Choose how to group your photos",
        onNavigateBack = onBackClick
    ) {
        // Category header
        item {
            CategoryHeader("Grid")
        }
        
        // Grid Type setting with dropdown
        item {
            Surface(
                onClick = { gridTypeExpanded = !gridTypeExpanded },
                shape = if (gridTypeExpanded) RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp) else RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FontIcon(
                        unicode = FontIcons.Today,
                        contentDescription = null,
                        size = 24.sp,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Grid type",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = currentGridTypeName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FontIcon(
                        unicode = FontIcons.KeyboardArrowDown,
                        contentDescription = null,
                        size = 24.sp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.graphicsLayer { rotationZ = gridTypeRotation }
                    )
                }
            }
        }
        
        // Expandable Grid Type options with animation
        item {
            AnimatedVisibility(
                visible = gridTypeExpanded,
                enter = expandVertically(animationSpec = tween(300)) + fadeIn(),
                exit = shrinkVertically(animationSpec = tween(300)) + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    RadioExpandableOption(
                        label = "Day",
                        isSelected = isDayMode,
                        onClick = {
                            viewModel.setGridType(GridType.DAY_3)
                            gridTypeExpanded = false
                        },
                        position = SettingPosition.TOP
                    )
                    RadioExpandableOption(
                        label = "Month",
                        isSelected = !isDayMode,
                        onClick = {
                            viewModel.setGridType(GridType.MONTH_6)
                            gridTypeExpanded = false
                        },
                        position = SettingPosition.BOTTOM
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(2.dp))
        }

        // Grid Size setting with dropdown
        item {
            Surface(
                onClick = { gridSizeExpanded = !gridSizeExpanded },
                shape = if (gridSizeExpanded) RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 8.dp) else RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FontIcon(
                        unicode = FontIcons.GridView,
                        contentDescription = null,
                        size = 24.sp,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Grid size",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = currentGridSizeName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FontIcon(
                        unicode = FontIcons.KeyboardArrowDown,
                        contentDescription = null,
                        size = 24.sp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.graphicsLayer { rotationZ = gridSizeRotation }
                    )
                }
            }
        }
        
        // Expandable Grid Size options with animation
        item {
            AnimatedVisibility(
                visible = gridSizeExpanded,
                enter = expandVertically(animationSpec = tween(300)) + fadeIn(),
                exit = shrinkVertically(animationSpec = tween(300)) + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (isDayMode) {
                        RadioExpandableOption(
                            label = "3",
                            isSelected = currentGridType == GridType.DAY_3,
                            onClick = {
                                viewModel.setGridType(GridType.DAY_3)
                                gridSizeExpanded = false
                            },
                            position = SettingPosition.TOP
                        )
                        RadioExpandableOption(
                            label = "4",
                            isSelected = currentGridType == GridType.DAY_4,
                            onClick = {
                                viewModel.setGridType(GridType.DAY_4)
                                gridSizeExpanded = false
                            },
                            position = SettingPosition.BOTTOM
                        )
                    } else {
                        RadioExpandableOption(
                            label = "6",
                            isSelected = currentGridType == GridType.MONTH_6,
                            onClick = {
                                viewModel.setGridType(GridType.MONTH_6)
                                gridSizeExpanded = false
                            },
                            position = SettingPosition.TOP
                        )
                        RadioExpandableOption(
                            label = "9",
                            isSelected = currentGridType == GridType.MONTH_9,
                            onClick = {
                                viewModel.setGridType(GridType.MONTH_9)
                                gridSizeExpanded = false
                            },
                            position = SettingPosition.BOTTOM
                        )
                    }
                }
            }
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
private fun RadioExpandableOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    position: SettingPosition
) {
    val haptic = LocalHapticFeedback.current
    val shape = com.prantiux.pixelgallery.ui.theme.ExpressiveListShape(
        when (position) {
            SettingPosition.TOP -> com.prantiux.pixelgallery.ui.theme.ListItemPosition.MIDDLE
            SettingPosition.MIDDLE -> com.prantiux.pixelgallery.ui.theme.ListItemPosition.MIDDLE
            SettingPosition.BOTTOM -> com.prantiux.pixelgallery.ui.theme.ListItemPosition.BOTTOM
            SettingPosition.SINGLE -> com.prantiux.pixelgallery.ui.theme.ListItemPosition.SINGLE
        }
    )
    
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(40.dp)) // Indent for the icon
            
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            
            Box(
                modifier = Modifier
                    .size(20.dp)
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
    val haptic = LocalHapticFeedback.current
    // Apply rounded corners based on position
    val shape = com.prantiux.pixelgallery.ui.theme.ExpressiveListShape(
        when (position) {
            SettingPosition.TOP -> com.prantiux.pixelgallery.ui.theme.ListItemPosition.TOP
            SettingPosition.MIDDLE -> com.prantiux.pixelgallery.ui.theme.ListItemPosition.MIDDLE
            SettingPosition.BOTTOM -> com.prantiux.pixelgallery.ui.theme.ListItemPosition.BOTTOM
            SettingPosition.SINGLE -> com.prantiux.pixelgallery.ui.theme.ListItemPosition.SINGLE
        }
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .bounceClick(onClick = {
                if (!isSelected) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                onClick()
            })
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
