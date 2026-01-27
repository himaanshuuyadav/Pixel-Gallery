package com.prantiux.pixelgallery.ui.screens

/**
 * Custom Video Player Screen - Material 3 Design
 * 
 * A modern, clean video player for single video playback with the following features:
 * 
 * FEATURES:
 * - Media3/ExoPlayer integration with custom Compose UI
 * - Play/Pause toggle (center overlay, auto-shows on tap)
 * - Seekbar with current/total time display
 * - Fullscreen toggle button
 * - Auto-hiding controls (3 second delay)
 * - Material 3 dynamic color support
 * - Gradient scrim overlays for readability
 * 
 * DESIGN PRINCIPLES:
 * - No Next/Previous buttons (single video player)
 * - Minimal, uncluttered interface
 * - Touch-friendly pill/rounded button shapes
 * - Smooth fade animations for controls
 * - Proper inset handling (status bar, navigation bar)
 * 
 * USAGE:
 * ```kotlin
 * VideoPlayerScreen(
 *     videoUri = Uri.parse("content://..."),
 *     onNavigateBack = { navController.popBackStack() }
 * )
 * ```
 * 
 * DEPENDENCIES:
 * - androidx.media3:media3-exoplayer
 * - androidx.media3:media3-ui
 */

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons

/**
 * Modern custom video player screen with Material 3 design
 * Single video playback with clean, minimal controls
 */
@Composable
fun VideoPlayerScreen(
    videoUri: Uri,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    settingsDataStore: com.prantiux.pixelgallery.data.SettingsDataStore
) {
    val context = LocalContext.current
    
    // Collect playback settings
    val autoPlayVideos by settingsDataStore.autoPlayVideosFlow.collectAsState(initial = true)
    val loopVideos by settingsDataStore.loopVideosFlow.collectAsState(initial = false)
    val keepScreenOn by settingsDataStore.keepScreenOnFlow.collectAsState(initial = true)
    val muteByDefault by settingsDataStore.muteByDefaultFlow.collectAsState(initial = false)
    
    // ExoPlayer instance
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
            playWhenReady = autoPlayVideos
            repeatMode = if (loopVideos) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            volume = if (muteByDefault) 0f else 1f
        }
    }
    
    // Player state
    var isPlaying by remember { mutableStateOf(exoPlayer.isPlaying) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isBuffering by remember { mutableStateOf(false) }
    
    // Controls visibility
    var showControls by remember { mutableStateOf(true) }
    var hideControlsJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    
    // Fullscreen state
    var isFullscreen by remember { mutableStateOf(false) }
    
    // Update player state continuously
    LaunchedEffect(exoPlayer) {
        while (isActive) {
            isPlaying = exoPlayer.isPlaying
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            isBuffering = exoPlayer.playbackState == Player.STATE_BUFFERING
            delay(100)
        }
    }
    
    // Auto-hide controls after 3 seconds
    LaunchedEffect(showControls, isPlaying) {
        hideControlsJob?.cancel()
        if (showControls && isPlaying) {
            hideControlsJob = kotlinx.coroutines.Job()
            delay(3000)
            showControls = false
        }
    }
    
    // Update loop mode when setting changes
    LaunchedEffect(loopVideos) {
        exoPlayer.repeatMode = if (loopVideos) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }
    
    // Update mute when setting changes
    LaunchedEffect(muteByDefault) {
        exoPlayer.volume = if (muteByDefault) 0f else 1f
    }
    
    // Player listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(listener)
        
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }
    
    // Keep screen on during playback
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(keepScreenOn, isPlaying) {
        val window = (view.context as? android.app.Activity)?.window
        if (window != null && keepScreenOn && isPlaying) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Video player surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // Disable default controls
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    showControls = !showControls
                }
        )
        
        // Loading indicator
        // Material 3 Expressive: Video buffering varies (SHORT to LONG operation, network-dependent)
        // Show indeterminate CircularProgressIndicator during buffering state
        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        // Controls overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            VideoControlsOverlay(
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                isFullscreen = isFullscreen,
                onPlayPauseClick = {
                    if (exoPlayer.isPlaying) {
                        exoPlayer.pause()
                    } else {
                        exoPlayer.play()
                    }
                },
                onSeek = { position ->
                    exoPlayer.seekTo(position)
                },
                onFullscreenToggle = {
                    isFullscreen = !isFullscreen
                    // TODO: Handle actual fullscreen logic
                },
                onBackClick = onNavigateBack
            )
        }
    }
}

/**
 * Video controls overlay with Material 3 styling
 */
@Composable
private fun VideoControlsOverlay(
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    isFullscreen: Boolean,
    onPlayPauseClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onFullscreenToggle: () -> Unit,
    onBackClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Top gradient scrim for back button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.7f),
                            Color.Transparent
                        )
                    )
                )
        ) {
            // Back button
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(8.dp)
                    .align(Alignment.TopStart)
            ) {
                FontIcon(
                    unicode = FontIcons.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    size = 28.sp
                )
            }
        }
        
        // Center play/pause button
        Surface(
            onClick = onPlayPauseClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
            modifier = Modifier
                .align(Alignment.Center)
                .size(72.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                FontIcon(
                    unicode = if (isPlaying) FontIcons.Pause else FontIcons.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    size = 40.sp,
                    filled = true
                )
            }
        }
        
        // Bottom controls with gradient scrim
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.8f)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .navigationBarsPadding()
            ) {
                // Seek bar
                Slider(
                    value = if (duration > 0) currentPosition.toFloat() else 0f,
                    onValueChange = { newPosition ->
                        onSeek(newPosition.toLong())
                    },
                    valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Time and fullscreen row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Time display
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTime(currentPosition),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "/",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            text = formatTime(duration),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                    
                    // Fullscreen button
                    Surface(
                        onClick = onFullscreenToggle,
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            FontIcon(
                                unicode = if (isFullscreen) FontIcons.FullscreenExit else FontIcons.Fullscreen,
                                contentDescription = if (isFullscreen) "Exit fullscreen" else "Fullscreen",
                                tint = Color.White,
                                size = 24.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Format milliseconds to MM:SS or HH:MM:SS
 */
private fun formatTime(timeMs: Long): String {
    val totalSeconds = (timeMs / 1000).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
