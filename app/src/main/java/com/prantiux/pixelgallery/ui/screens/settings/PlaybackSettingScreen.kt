package com.prantiux.pixelgallery.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prantiux.pixelgallery.ui.components.SubPageScaffold
import com.prantiux.pixelgallery.ui.icons.FontIcons
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSettingScreen(
    settingsDataStore: com.prantiux.pixelgallery.data.SettingsDataStore,
    onBackClick: () -> Unit = {}
) {
    var autoPlayVideos by remember { mutableStateOf(true) }
    var resumePlayback by remember { mutableStateOf(true) }
    var loopVideos by remember { mutableStateOf(false) }
    var keepScreenOn by remember { mutableStateOf(true) }
    var muteByDefault by remember { mutableStateOf(false) }
    var showControlsOnTap by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    
    // Load playback settings
    LaunchedEffect(Unit) {
        scope.launch {
            settingsDataStore.autoPlayVideosFlow.collect { enabled ->
                autoPlayVideos = enabled
            }
        }
    }
    
    LaunchedEffect(Unit) {
        scope.launch {
            settingsDataStore.resumePlaybackFlow.collect { enabled ->
                resumePlayback = enabled
            }
        }
    }
    
    LaunchedEffect(Unit) {
        scope.launch {
            settingsDataStore.loopVideosFlow.collect { enabled ->
                loopVideos = enabled
            }
        }
    }
    
    LaunchedEffect(Unit) {
        scope.launch {
            settingsDataStore.keepScreenOnFlow.collect { enabled ->
                keepScreenOn = enabled
            }
        }
    }
    
    LaunchedEffect(Unit) {
        scope.launch {
            settingsDataStore.muteByDefaultFlow.collect { enabled ->
                muteByDefault = enabled
            }
        }
    }
    
    LaunchedEffect(Unit) {
        scope.launch {
            settingsDataStore.showControlsOnTapFlow.collect { enabled ->
                showControlsOnTap = enabled
            }
        }
    }
    
    SubPageScaffold(
        title = "Playback",
        subtitle = "Video playback preferences",
        onNavigateBack = onBackClick
    ) {
        // Add consistent spacing
        item {
            Spacer(modifier = Modifier.height(28.dp))
        }
        
        // Behavior category
        item {
            CategoryHeader("Behavior")
        }
        
        // Auto-play videos
        item {
            GroupedSettingToggle(
                title = "Auto-play videos",
                subtitle = "Start playing videos automatically",
                iconUnicode = FontIcons.PlayArrow,
                checked = autoPlayVideos,
                onCheckedChange = { 
                    autoPlayVideos = it
                    scope.launch {
                        settingsDataStore.saveAutoPlayVideos(it)
                    }
                },
                position = SettingPosition.TOP
            )
        }
        
        // Resume playback
        item {
            GroupedSettingToggle(
                title = "Resume playback",
                subtitle = "Continue from last position",
                iconUnicode = FontIcons.History,
                checked = resumePlayback,
                onCheckedChange = { 
                    resumePlayback = it
                    scope.launch {
                        settingsDataStore.saveResumePlayback(it)
                    }
                },
                position = SettingPosition.MIDDLE
            )
        }
        
        // Loop videos
        item {
            GroupedSettingToggle(
                title = "Loop videos",
                subtitle = "Replay videos automatically",
                iconUnicode = FontIcons.Refresh,
                checked = loopVideos,
                onCheckedChange = { 
                    loopVideos = it
                    scope.launch {
                        settingsDataStore.saveLoopVideos(it)
                    }
                },
                position = SettingPosition.MIDDLE
            )
        }
        
        // Keep screen on
        item {
            GroupedSettingToggle(
                title = "Keep screen on",
                subtitle = "Prevent screen timeout during playback",
                iconUnicode = FontIcons.LightMode,
                checked = keepScreenOn,
                onCheckedChange = { 
                    keepScreenOn = it
                    scope.launch {
                        settingsDataStore.saveKeepScreenOn(it)
                    }
                },
                position = SettingPosition.BOTTOM
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // Audio category
        item {
            CategoryHeader("Audio")
        }
        
        // Mute by default
        item {
            GroupedSettingToggle(
                title = "Mute by default",
                subtitle = "Start videos with audio muted",
                iconUnicode = FontIcons.VolumeOff,
                checked = muteByDefault,
                onCheckedChange = { 
                    muteByDefault = it
                    scope.launch {
                        settingsDataStore.saveMuteByDefault(it)
                    }
                },
                position = SettingPosition.SINGLE
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // Controls category
        item {
            CategoryHeader("Controls")
        }
        
        // Show controls on tap
        item {
            GroupedSettingToggle(
                title = "Show controls on tap",
                subtitle = "Display playback controls when tapped",
                iconUnicode = FontIcons.Settings,
                checked = showControlsOnTap,
                onCheckedChange = { 
                    showControlsOnTap = it
                    scope.launch {
                        settingsDataStore.saveShowControlsOnTap(it)
                    }
                },
                position = SettingPosition.SINGLE
            )
        }
    }
}
