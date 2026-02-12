package com.prantiux.pixelgallery.ui.screens.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prantiux.pixelgallery.ui.components.SubPageScaffold
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GesturesSettingScreen(
    settingsDataStore: com.prantiux.pixelgallery.data.SettingsDataStore,
    onBackClick: () -> Unit = {}
) {
    var swipeDownToClose by remember { mutableStateOf(true) }
    var swipeUpToDetails by remember { mutableStateOf(true) }
    var doubleTapToZoom by remember { mutableStateOf(true) }
    var doubleTapZoomLevel by remember { mutableStateOf("2x") }
    var zoomLevelExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    
    // Load gesture settings
    LaunchedEffect(Unit) {
        scope.launch {
            settingsDataStore.swipeDownToCloseFlow.collect { enabled ->
                swipeDownToClose = enabled
            }
        }
    }
    
    LaunchedEffect(Unit) {
        scope.launch {
            settingsDataStore.swipeUpToDetailsFlow.collect { enabled ->
                swipeUpToDetails = enabled
            }
        }
    }
    
    LaunchedEffect(Unit) {
        scope.launch {
            settingsDataStore.doubleTapToZoomFlow.collect { enabled ->
                doubleTapToZoom = enabled
            }
        }
    }
    
    LaunchedEffect(Unit) {
        scope.launch {
            settingsDataStore.doubleTapZoomLevelFlow.collect { level ->
                doubleTapZoomLevel = level
            }
        }
    }
    
    // Animate zoom level expansion
    val zoomLevelRotation by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (zoomLevelExpanded) 180f else 0f,
        animationSpec = androidx.compose.animation.core.tween(300)
    )
    
    SubPageScaffold(
        title = "Gestures",
        subtitle = "Customize swipe and tap interactions",
        onNavigateBack = onBackClick
    ) {
        // Add consistent spacing
        item {
            Spacer(modifier = Modifier.height(28.dp))
        }
        
        // Navigation category header
        item {
            CategoryHeader("Navigation")
        }
        
        // Swipe down to close
        item {
            GroupedSettingToggle(
                title = "Swipe down to close",
                subtitle = "Close media viewer with downward swipe",
                iconUnicode = FontIcons.SwipeDown,
                checked = swipeDownToClose,
                onCheckedChange = { 
                    swipeDownToClose = it
                    scope.launch {
                        settingsDataStore.saveSwipeDownToClose(it)
                    }
                },
                position = SettingPosition.TOP
            )
        }
        
        // Swipe up to details
        item {
            GroupedSettingToggle(
                title = "Swipe up to details",
                subtitle = "Show image details with upward swipe",
                iconUnicode = FontIcons.Info,
                checked = swipeUpToDetails,
                onCheckedChange = { 
                    swipeUpToDetails = it
                    scope.launch {
                        settingsDataStore.saveSwipeUpToDetails(it)
                    }
                },
                position = SettingPosition.BOTTOM
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // Zoom category header
        item {
            CategoryHeader("Zoom")
        }
        
        // Double-tap to zoom
        item {
            GroupedSettingToggle(
                title = "Double-tap to zoom",
                subtitle = "Enable double-tap gesture for zooming",
                iconUnicode = FontIcons.ZoomIn,
                checked = doubleTapToZoom,
                onCheckedChange = { 
                    doubleTapToZoom = it
                    scope.launch {
                        settingsDataStore.saveDoubleTapToZoom(it)
                    }
                },
                position = SettingPosition.TOP
            )
        }
        
        // Double-tap zoom level with dropdown
        item {
            Surface(
                onClick = { if (doubleTapToZoom) zoomLevelExpanded = !zoomLevelExpanded },
                shape = if (zoomLevelExpanded) 
                    RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 8.dp) 
                    else RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
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
                        unicode = FontIcons.Image,
                        contentDescription = null,
                        size = 24.sp,
                        tint = if (doubleTapToZoom) MaterialTheme.colorScheme.primary 
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Double-tap zoom level",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (doubleTapToZoom) MaterialTheme.colorScheme.onSurface
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                        Text(
                            text = doubleTapZoomLevel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (doubleTapToZoom) MaterialTheme.colorScheme.onSurfaceVariant
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        )
                    }
                    FontIcon(
                        unicode = FontIcons.KeyboardArrowDown,
                        contentDescription = null,
                        size = 24.sp,
                        tint = if (doubleTapToZoom) MaterialTheme.colorScheme.onSurfaceVariant
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                        modifier = Modifier.graphicsLayer { rotationZ = zoomLevelRotation }
                    )
                }
            }
        }
        
        // Expandable zoom level options with slider
        item {
            AnimatedVisibility(
                visible = zoomLevelExpanded && doubleTapToZoom,
                enter = expandVertically(animationSpec = tween(300)) + fadeIn(),
                exit = shrinkVertically(animationSpec = tween(300)) + fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        // Slider for zoom level
                        Text(
                            text = "Zoom level: $doubleTapZoomLevel",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        var sliderPosition by remember { 
                            mutableFloatStateOf(when (doubleTapZoomLevel) {
                                "2x" -> 0f
                                "3x" -> 1f
                                "4x" -> 2f
                                else -> 0f
                            })
                        }
                        var lastHapticStep by remember { mutableIntStateOf(-1) }
                        
                        Slider(
                            value = sliderPosition,
                            onValueChange = { 
                                val step = when {
                                    it < 0.5f -> 0
                                    it < 1.5f -> 1
                                    else -> 2
                                }
                                if (step != lastHapticStep) {
                                    lastHapticStep = step
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                sliderPosition = it
                                doubleTapZoomLevel = when {
                                    it < 0.5f -> "2x"
                                    it < 1.5f -> "3x"
                                    else -> "4x"
                                }
                            },
                            onValueChangeFinished = {
                                scope.launch {
                                    settingsDataStore.saveDoubleTapZoomLevel(doubleTapZoomLevel)
                                }
                            },
                            valueRange = 0f..2f,
                            steps = 1,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Labels under slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "2x",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "3x",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "4x",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
