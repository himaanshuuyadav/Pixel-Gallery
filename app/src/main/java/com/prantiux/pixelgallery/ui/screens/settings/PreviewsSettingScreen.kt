package com.prantiux.pixelgallery.ui.screens.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.prantiux.pixelgallery.ui.components.SubPageScaffold
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewsSettingScreen(
    settingsDataStore: com.prantiux.pixelgallery.data.SettingsDataStore,
    onBackClick: () -> Unit = {}
) {
    var thumbnailQuality by remember { mutableStateOf("Standard") }
    var cornerType by remember { mutableStateOf("Rounded") }
    var badgeType by remember { mutableStateOf("Duration with icon") }
    var showBadge by remember { mutableStateOf(true) }
    var showCompletedDuration by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showCornerTypeDialog by remember { mutableStateOf(false) }
    var badgeTypeExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    // Load preview settings
    LaunchedEffect(Unit) {
        scope.launch {
            settingsDataStore.thumbnailQualityFlow.collect { quality ->
                thumbnailQuality = quality
            }
        }
    }
    
    LaunchedEffect(Unit) {
        scope.launch {
            settingsDataStore.cornerTypeFlow.collect { corner ->
                cornerType = corner
            }
        }
    }
    
    LaunchedEffect(Unit) {
        scope.launch {
            settingsDataStore.badgeTypeFlow.collect { badge ->
                badgeType = badge
            }
        }
    }
    
    LaunchedEffect(Unit) {
        scope.launch {
            settingsDataStore.showBadgeFlow.collect { show ->
                showBadge = show
            }
        }
    }
    
    LaunchedEffect(Unit) {
        scope.launch {
            settingsDataStore.showCompletedDurationFlow.collect { show ->
                showCompletedDuration = show
            }
        }
    }
    
    // Animate badge type expansion
    val badgeTypeRotation by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (badgeTypeExpanded) 180f else 0f,
        animationSpec = androidx.compose.animation.core.tween(300)
    )
    
    SubPageScaffold(
        title = "Previews",
        subtitle = "Customize thumbnail appearance",
        onNavigateBack = onBackClick
    ) {
        // Add consistent spacing
        item {
            Spacer(modifier = Modifier.height(28.dp))
        }
        
        // Thumbnail tweaks category header
        item {
            CategoryHeader("Thumbnail tweaks")
        }
        
        // Quality setting
        item {
            GroupedSettingItem(
                title = "Quality",
                subtitle = thumbnailQuality,
                iconUnicode = FontIcons.Image,
                position = SettingPosition.TOP,
                onClick = { showQualityDialog = true }
            )
        }
        
        // Corner type setting
        item {
            GroupedSettingItem(
                title = "Corner type",
                subtitle = cornerType,
                iconUnicode = FontIcons.GridView,
                position = SettingPosition.BOTTOM,
                onClick = { showCornerTypeDialog = true }
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // Video thumbnails category header
        item {
            CategoryHeader("Video thumbnails")
        }
        
        // Badge toggle
        item {
            GroupedSettingToggle(
                title = "Badge",
                subtitle = "Show badge on video thumbnails",
                iconUnicode = FontIcons.VideoLibrary,
                checked = showBadge,
                onCheckedChange = { 
                    showBadge = it
                    scope.launch {
                        settingsDataStore.saveShowBadge(it)
                    }
                },
                position = SettingPosition.TOP
            )
        }
        
        // Badge type setting with dropdown
        item {
            Surface(
                onClick = { if (showBadge) badgeTypeExpanded = !badgeTypeExpanded },
                shape = if (badgeTypeExpanded) RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 8.dp) else RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
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
                        unicode = FontIcons.Edit,
                        contentDescription = null,
                        size = 24.sp,
                        tint = if (showBadge) MaterialTheme.colorScheme.primary 
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Badge type",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (showBadge) MaterialTheme.colorScheme.onSurface
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                        Text(
                            text = badgeType,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (showBadge) MaterialTheme.colorScheme.onSurfaceVariant
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        )
                    }
                    FontIcon(
                        unicode = FontIcons.KeyboardArrowDown,
                        contentDescription = null,
                        size = 24.sp,
                        tint = if (showBadge) MaterialTheme.colorScheme.onSurfaceVariant
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                        modifier = Modifier.graphicsLayer { rotationZ = badgeTypeRotation }
                    )
                }
            }
        }
        
        // Expandable badge type options with animation
        item {
            AnimatedVisibility(
                visible = badgeTypeExpanded && showBadge,
                enter = expandVertically(animationSpec = tween(300)) + fadeIn(),
                exit = shrinkVertically(animationSpec = tween(300)) + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BadgeTypeExpandableOption(
                        label = "Duration with icon",
                        badgePreviewType = "Duration with icon",
                        isSelected = badgeType == "Duration with icon",
                        onClick = {
                            badgeType = "Duration with icon"
                            scope.launch {
                                settingsDataStore.saveBadgeType("Duration with icon")
                            }
                        },
                        position = SettingPosition.TOP
                    )
                    BadgeTypeExpandableOption(
                        label = "Duration only",
                        badgePreviewType = "Duration only",
                        isSelected = badgeType == "Duration only",
                        onClick = {
                            badgeType = "Duration only"
                            scope.launch {
                                settingsDataStore.saveBadgeType("Duration only")
                            }
                        },
                        position = SettingPosition.MIDDLE
                    )
                    BadgeTypeExpandableOption(
                        label = "Icon only",
                        badgePreviewType = "Icon only",
                        isSelected = badgeType == "Icon only",
                        onClick = {
                            badgeType = "Icon only"
                            scope.launch {
                                settingsDataStore.saveBadgeType("Icon only")
                            }
                        },
                        position = SettingPosition.BOTTOM
                    )
                }
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // Duration toggle - ungrouped
        item {
            GroupedSettingToggle(
                title = "Duration",
                subtitle = "Show completed video duration",
                iconUnicode = FontIcons.Timer,
                checked = showCompletedDuration,
                onCheckedChange = { 
                    showCompletedDuration = it
                    scope.launch {
                        settingsDataStore.saveShowCompletedDuration(it)
                    }
                },
                position = SettingPosition.SINGLE
            )
        }
    }
    
    // Quality selection dialog
    if (showQualityDialog) {
        QualitySelectionDialog(
            currentQuality = thumbnailQuality,
            onQualitySelected = { 
                thumbnailQuality = it
                scope.launch {
                    settingsDataStore.saveThumbnailQuality(it)
                }
                showQualityDialog = false
            },
            onDismiss = { showQualityDialog = false }
        )
    }
    
    // Corner type selection dialog
    if (showCornerTypeDialog) {
        CornerTypeSelectionDialog(
            currentCornerType = cornerType,
            onCornerTypeSelected = { 
                cornerType = it
                scope.launch {
                    settingsDataStore.saveCornerType(it)
                }
                showCornerTypeDialog = false
            },
            onDismiss = { showCornerTypeDialog = false }
        )
    }
}

@Composable
private fun QualitySelectionDialog(
    currentQuality: String,
    onQualitySelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
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
                    unicode = FontIcons.Image,
                    contentDescription = null,
                    size = 40.sp,
                    tint = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Title
                Text(
                    text = "Thumbnail quality",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Quality options
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Column {
                        QualityOption(
                            label = "Standard",
                            isSelected = currentQuality == "Standard",
                            onClick = { onQualitySelected("Standard") },
                            position = SettingPosition.TOP
                        )
                        
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        
                        QualityOption(
                            label = "High",
                            isSelected = currentQuality == "High",
                            onClick = { onQualitySelected("High") },
                            position = SettingPosition.MIDDLE
                        )
                        
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        
                        QualityOption(
                            label = "Automatic",
                            isSelected = currentQuality == "Automatic",
                            onClick = { onQualitySelected("Automatic") },
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
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun CornerTypeSelectionDialog(
    currentCornerType: String,
    onCornerTypeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
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
                    unicode = FontIcons.GridView,
                    contentDescription = null,
                    size = 40.sp,
                    tint = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Title
                Text(
                    text = "Corner type",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Corner type options
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Column {
                        CornerTypeOption(
                            label = "Rounded",
                            isSelected = currentCornerType == "Rounded",
                            onClick = { onCornerTypeSelected("Rounded") },
                            position = SettingPosition.TOP
                        )
                        
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        
                        CornerTypeOption(
                            label = "Sharp",
                            isSelected = currentCornerType == "Sharp",
                            onClick = { onCornerTypeSelected("Sharp") },
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
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun BadgeTypeExpandableOption(
    label: String,
    badgePreviewType: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    position: SettingPosition
) {
    val haptic = LocalHapticFeedback.current
    val shape = when (position) {
        SettingPosition.TOP -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
        SettingPosition.MIDDLE -> RoundedCornerShape(8.dp)
        SettingPosition.BOTTOM -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        else -> RoundedCornerShape(0.dp)
    }

    val handleSelect = {
        if (!isSelected) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        onClick()
    }
    
    Surface(
        onClick = handleSelect,
        shape = shape,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Badge preview using actual pill shape
            Box(
                modifier = Modifier
                    .size(width = 80.dp, height = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                BadgePreviewExpanded(badgeType = badgePreviewType)
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            
            // Custom radio button matching grid type style
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

@Composable
private fun BadgePreviewExpanded(badgeType: String) {
    // Use exact same pill shape as actual video thumbnails
    Row(
        modifier = Modifier
            .background(
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(50) // Pill shape - same as VideoDurationPill
            )
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = when (badgeType) {
            "Duration with icon" -> Arrangement.spacedBy(3.dp)
            "Icon only" -> Arrangement.Center
            "Duration only" -> Arrangement.Center
            else -> Arrangement.spacedBy(3.dp)
        }
    ) {
        when (badgeType) {
            "Duration with icon" -> {
                FontIcon(
                    unicode = FontIcons.PlayArrow,
                    contentDescription = null,
                    size = 14.sp,
                    tint = Color.White
                )
                Text(
                    text = "4:30",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
            "Icon only" -> {
                FontIcon(
                    unicode = FontIcons.PlayArrow,
                    contentDescription = null,
                    size = 14.sp,
                    tint = Color.White
                )
            }
            "Duration only" -> {
                Text(
                    text = "4:30",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}

@Composable
private fun QualityOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    position: SettingPosition
) {
    val haptic = LocalHapticFeedback.current
    val shape = when (position) {
        SettingPosition.TOP -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        SettingPosition.BOTTOM -> RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
        else -> RoundedCornerShape(0.dp)
    }

    val handleSelect = {
        if (!isSelected) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        onClick()
    }
    
    Surface(
        onClick = handleSelect,
        shape = shape,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = handleSelect
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun CornerTypeOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    position: SettingPosition
) {
    val haptic = LocalHapticFeedback.current
    val shape = when (position) {
        SettingPosition.TOP -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        SettingPosition.BOTTOM -> RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
        else -> RoundedCornerShape(0.dp)
    }

    val handleSelect = {
        if (!isSelected) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        onClick()
    }
    
    Surface(
        onClick = handleSelect,
        shape = shape,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = handleSelect
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
