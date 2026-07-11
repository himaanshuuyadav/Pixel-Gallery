@file:OptIn(ExperimentalAnimationApi::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)

package com.prantiux.pixelgallery.ui.overlay

import com.prantiux.pixelgallery.ui.components.util.swipe
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.graphics.shapes.Morph
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.material3.ripple
import androidx.compose.material3.toShape
import com.prantiux.pixelgallery.ui.shapes.SmoothCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.prantiux.pixelgallery.model.MediaItem
import com.prantiux.pixelgallery.viewmodel.MediaViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons
import com.prantiux.pixelgallery.ui.components.DetailsBottomSheetContent


// Gesture direction locking
private enum class GestureMode {
    NONE,
    HORIZONTAL_SWIPE,
    VERTICAL_UP,
    VERTICAL_DOWN,
    ZOOM
}

enum class SeekIndicatorState {
    LEFT, RIGHT
}

class MorphPolygonShape(
    private val morph: Morph,
    private val progress: Float,
    private val rotationDegrees: Float = 0f
) : Shape {
    private val matrix = android.graphics.Matrix()

    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val composePath = androidx.compose.ui.graphics.Path()
        morph.toPath(progress, composePath)
        val androidPath = composePath.asAndroidPath()

        val bounds = android.graphics.RectF()
        androidPath.computeBounds(bounds, true)

        matrix.reset()
        if (rotationDegrees != 0f) {
            matrix.postRotate(rotationDegrees, bounds.centerX(), bounds.centerY())
            androidPath.transform(matrix)
            androidPath.computeBounds(bounds, true)
            matrix.reset()
        }

        // Translate to origin (0,0)
        matrix.postTranslate(-bounds.left, -bounds.top)
        
        // Scale to fit Box size uniformly
        val scaleX = size.width / bounds.width()
        val scaleY = size.height / bounds.height()
        val scale = minOf(scaleX, scaleY)
        matrix.postScale(scale, scale)
        
        // Center perfectly within the Box
        val scaledWidth = bounds.width() * scale
        val scaledHeight = bounds.height() * scale
        matrix.postTranslate((size.width - scaledWidth) / 2f, (size.height - scaledHeight) / 2f)
        
        androidPath.transform(matrix)

        return Outline.Generic(androidPath.asComposePath())
    }
}

object OverlayConstants {
    const val MIN_ZOOM_SCALE = 1f
    const val MAX_ZOOM_SCALE = 5f
    const val TAP_SLOP_PX = 10f
    const val VERTICAL_CLOSE_THRESHOLD_PX = 150f
}

private fun clampOffset(
    offsetX: Float,
    offsetY: Float,
    scale: Float,
    containerWidth: Float,
    containerHeight: Float,
    imageWidth: Float,
    imageHeight: Float
): Pair<Float, Float> {
    val scaledWidth = imageWidth * scale
    val scaledHeight = imageHeight * scale

    val maxOffsetX = kotlin.math.max(0f, (scaledWidth - containerWidth) / 2f)
    val maxOffsetY = kotlin.math.max(0f, (scaledHeight - containerHeight) / 2f)

    val clampedX = offsetX.coerceIn(-maxOffsetX, maxOffsetX)
    val clampedY = offsetY.coerceIn(-maxOffsetY, maxOffsetY)
    return clampedX to clampedY
}

private fun calculateScale(current: Float, zoom: Float): Float {
    return (current * zoom).coerceIn(OverlayConstants.MIN_ZOOM_SCALE, OverlayConstants.MAX_ZOOM_SCALE)
}

private fun isTap(down: Offset, up: Offset): Boolean {
    return (up - down).getDistance() < OverlayConstants.TAP_SLOP_PX
}

@Composable
fun MediaOverlay(
    viewModel: MediaViewModel,
    overlayState: MediaViewModel.MediaOverlayState,
    mediaItems: List<MediaItem>,
    settingsDataStore: com.prantiux.pixelgallery.data.SettingsDataStore,
    videoPositionDataStore: com.prantiux.pixelgallery.data.VideoPositionDataStore,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val view = LocalView.current
    val context = LocalContext.current
    val pageWidthPx = remember(configuration.screenWidthDp, density.density) {
        if (configuration.screenWidthDp > 0) {
            configuration.screenWidthDp * density.density
        } else {
            1080f
        }
    }
    val maxHorizontalOffset = remember(pageWidthPx) { pageWidthPx }
    val navigationThreshold = remember(pageWidthPx) { pageWidthPx * 0.5f }
    
    // Collect gesture settings
    val swipeDownToClose by settingsDataStore.swipeDownToCloseFlow.collectAsState(initial = true)
    val swipeUpToDetails by settingsDataStore.swipeUpToDetailsFlow.collectAsState(initial = true)
    val doubleTapToZoom by settingsDataStore.doubleTapToZoomFlow.collectAsState(initial = true)
    val doubleTapZoomLevel by settingsDataStore.doubleTapZoomLevelFlow.collectAsState(initial = "Fit")
    
    // Collect playback settings
    val autoPlayVideos by settingsDataStore.autoPlayVideosFlow.collectAsState(initial = true)
    val resumePlayback by settingsDataStore.resumePlaybackFlow.collectAsState(initial = true)
    val loopVideos by settingsDataStore.loopVideosFlow.collectAsState(initial = false)
    val keepScreenOn by settingsDataStore.keepScreenOnFlow.collectAsState(initial = true)
    val muteByDefault by settingsDataStore.muteByDefaultFlow.collectAsState(initial = false)
    
    // Check if this is trash mode
    val isTrashMode = overlayState.mediaType == "trash"
    val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val barColor = if (isDarkTheme) Color.Black else Color.White

    // Current index state
    var currentIndex by remember { mutableIntStateOf(overlayState.selectedIndex) }
    var hasResolvedInitialIndex by remember { mutableStateOf(false) }
    
    // Keep track of the currently viewed ID to maintain position when list updates
    var currentlyViewedId by remember { mutableStateOf<Long?>(null) }
    
    LaunchedEffect(currentIndex, hasResolvedInitialIndex) {
        if (hasResolvedInitialIndex && currentIndex >= 0 && currentIndex < mediaItems.size) {
            currentlyViewedId = mediaItems[currentIndex].id
        }
    }
    
    // When mediaItems updates (e.g. after frame export), adjust currentIndex to keep looking at the same item
    LaunchedEffect(mediaItems) {
        if (hasResolvedInitialIndex && currentlyViewedId != null) {
            val newIdx = mediaItems.indexOfFirst { it.id == currentlyViewedId }
            if (newIdx >= 0 && newIdx != currentIndex) {
                currentIndex = newIdx
            }
        }
    }
    // Determine the true active index synchronously for rendering
    val activeIndex = remember(currentIndex, mediaItems, overlayState.selectedItemId, hasResolvedInitialIndex, currentlyViewedId) {
        if (!hasResolvedInitialIndex && mediaItems.isNotEmpty() && overlayState.selectedItemId != null) {
            val idx = mediaItems.indexOfFirst { it.id == overlayState.selectedItemId }
            if (idx >= 0) idx else currentIndex
        } else if (hasResolvedInitialIndex && currentlyViewedId != null) {
            val idx = mediaItems.indexOfFirst { it.id == currentlyViewedId }
            if (idx >= 0) idx else currentIndex
        } else {
            currentIndex
        }
    }

    // Zoom state
    var scale by remember { mutableFloatStateOf(OverlayConstants.MIN_ZOOM_SCALE) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    
    // Video player state
    var exoPlayer: ExoPlayer? by remember { mutableStateOf(null) }
    var isPlaying by remember { mutableStateOf(false) }
    
    val haptic = LocalHapticFeedback.current

    DisposableEffect(keepScreenOn, isPlaying, currentlyViewedId) {
        val window = (view.context as? Activity)?.window
        val isVideo = mediaItems.find { it.id == currentlyViewedId }?.isVideo == true
        if (window != null && keepScreenOn && isVideo && isPlaying) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    
    // Delete confirmation dialog
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Gesture state - PERSISTENT ANIMATABLE OFFSETS
    var gestureMode by remember { mutableStateOf(GestureMode.NONE) }
    val horizontalOffset = remember { androidx.compose.animation.core.Animatable(0f) }
    val verticalOffset = remember { androidx.compose.animation.core.Animatable(0f) }
    val detailsPanelProgress = remember { androidx.compose.animation.core.Animatable(0f) }
    val imageZIndex = remember { Animatable(1f) }  // For smooth z-order transition during bar entrance
    var isNavigating by remember { mutableStateOf(false) }

    // UI visibility
    var showControls by remember { mutableStateOf(false) }
    var showBars by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Autohide logic removed as requested by user
    var barsRevealUnlocked by remember { mutableStateOf(false) }
    var isDismissing by remember { mutableStateOf(false) }
    var openingProgress by remember { mutableStateOf(0f) }
    var predictiveBackProgress by remember { mutableFloatStateOf(0f) }

    val animatedOpeningProgress by animateFloatAsState(
        targetValue = openingProgress,
        animationSpec = tween(
            durationMillis = 0,
            delayMillis = 0,
            easing = FastOutSlowInEasing
        ),
        label = "openingProgress"
    )
    var isVideoFullscreen by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val isDetailsOpen = detailsPanelProgress.value > 0.5f

    val windowInsetsController = remember(view) {
        WindowInsetsControllerCompat(
            (view.context as Activity).window,
            view
        )
    }

    SideEffect {
        val window = (view.context as? Activity)?.window
            ?: return@SideEffect
        if (isDismissing) {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            windowInsetsController.isAppearanceLightStatusBars = !isDarkTheme
            windowInsetsController.isAppearanceLightNavigationBars = !isDarkTheme
            @Suppress("DEPRECATION")
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        } else if (showBars || isDetailsOpen) {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            windowInsetsController.isAppearanceLightStatusBars = !isDarkTheme
            windowInsetsController.isAppearanceLightNavigationBars = !isDarkTheme
            @Suppress("DEPRECATION")
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        } else {
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            windowInsetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Calculate screen dimensions
    // IMPORTANT: These values should stay in the same coordinate space as gesture math
    val screenWidth = pageWidthPx
    val screenHeight = remember(configuration.screenHeightDp, density.density) {
        with(density) { configuration.screenHeightDp.dp.toPx() }
    }

    val targetDecodeSize = remember(configuration.screenWidthDp, configuration.screenHeightDp, density.density) {
        val fallbackScreenWidthPx = 1080
        val fallbackScreenHeightPx = 1920

        val screenWidthPx = if (configuration.screenWidthDp > 0) {
            (configuration.screenWidthDp * density.density).roundToInt()
        } else {
            fallbackScreenWidthPx
        }

        val screenHeightPx = if (configuration.screenHeightDp > 0) {
            (configuration.screenHeightDp * density.density).roundToInt()
        } else {
            fallbackScreenHeightPx
        }

        val clampedScreenWidthPx = screenWidthPx.coerceIn(720, 3000)
        val clampedScreenHeightPx = screenHeightPx.coerceIn(1280, 5000)

        val targetWidth = (clampedScreenWidthPx * 2).coerceAtMost(4096)
        val targetHeight = (clampedScreenHeightPx * 2).coerceAtMost(4096)

        Size(targetWidth, targetHeight)
    }

    val horizontalMoveDeltas = remember { Channel<Float>(capacity = Channel.UNLIMITED) }
    val verticalUpMoveDeltas = remember { Channel<Float>(capacity = Channel.UNLIMITED) }
    val verticalDownMoveDeltas = remember { Channel<Float>(capacity = Channel.UNLIMITED) }

    LaunchedEffect(Unit) {
        for (dx in horizontalMoveDeltas) {
            if (isNavigating) {
                continue
            }
            val clampedOffset = (horizontalOffset.value + dx).coerceIn(-maxHorizontalOffset, maxHorizontalOffset)
            horizontalOffset.snapTo(clampedOffset)
        }
    }

    LaunchedEffect(Unit) {
        for (dy in verticalUpMoveDeltas) {
            verticalOffset.snapTo(verticalOffset.value + dy)

            val rawProgress = abs(verticalOffset.value) / (screenHeight * 0.5f)
            val clampedRaw = rawProgress.coerceIn(0f, 1f)

            // Ease-in curve: slow start, increasing resistance toward end
            val easedProgress = Math.pow(clampedRaw.toDouble(), 1.6).toFloat()
                .coerceIn(0f, 1f)

            detailsPanelProgress.snapTo(easedProgress)

            showControls = detailsPanelProgress.value < 0.1f
        }
    }

    LaunchedEffect(Unit) {
        for (dy in verticalDownMoveDeltas) {
            if (detailsPanelProgress.value > 0f) {
                // Mirror open tracking in reverse: downward drag reduces panel progress in real-time.
                val deltaProgress = dy / (screenHeight * 0.5f)
                val newProgress = (detailsPanelProgress.value - deltaProgress).coerceIn(0f, 1f)
                detailsPanelProgress.snapTo(newProgress)
                verticalOffset.snapTo(0f)
                showControls = detailsPanelProgress.value < 0.1f
            } else if (swipeDownToClose) {
                // Preserve overlay swipe-down-to-close tracking when details are not open.
                verticalOffset.snapTo(verticalOffset.value + dy)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            horizontalMoveDeltas.close()
            verticalUpMoveDeltas.close()
            verticalDownMoveDeltas.close()
        }
    }
    
    // Favorite states - track which items are favorited
    val favoriteStates = remember { mutableStateMapOf<Long, Boolean>().apply {
        mediaItems.forEach { item -> 
            put(item.id, item.isFavorite)
        }
    } }

    LaunchedEffect(mediaItems) {
        mediaItems.forEach { item ->
            favoriteStates.getOrPut(item.id) { item.isFavorite }
        }
    }
    
    // Favorite message pill state
    var showFavoritePill by remember { mutableStateOf(false) }
    var favoriteMessage by remember { mutableStateOf("") }
    
    // Auto-hide favorite message after 2 seconds
    LaunchedEffect(showFavoritePill) {
        if (showFavoritePill) {
            kotlinx.coroutines.delay(2000)
            showFavoritePill = false
        }
    }
    
    var videoIntrinsicSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    var isHoldingFor2x by remember { mutableStateOf(false) }
    var holdJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var seekIndicatorState by remember { mutableStateOf<SeekIndicatorState?>(null) }
    var seekAmount by remember { mutableIntStateOf(0) }
    var seekJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    
    var firstFrameRenderedId by remember { mutableStateOf<Long?>(null) }
    
    // ExoPlayer lifecycle management
    DisposableEffect(context) {
        val player = ExoPlayer.Builder(context).build().apply {
            setSeekParameters(SeekParameters.EXACT)
            // Set repeat mode based on loop setting
            repeatMode = if (loopVideos) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            // Set volume based on mute setting
            volume = if (muteByDefault) 0f else 1f
            
            addListener(object : androidx.media3.common.Player.Listener {
                override fun onRenderedFirstFrame() {
                    firstFrameRenderedId = currentlyViewedId
                }
                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                    videoIntrinsicSize = androidx.compose.ui.geometry.Size(
                        videoSize.width.toFloat(), 
                        videoSize.height.toFloat()
                    )
                }
            })
        }
        exoPlayer = player
        onDispose {
            player.release()
            exoPlayer = null
        }
    }
    
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                exoPlayer?.pause()
                isPlaying = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Update loop mode when setting changes
    LaunchedEffect(loopVideos) {
        exoPlayer?.repeatMode = if (loopVideos) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }
    
    // Update mute when setting changes
    LaunchedEffect(muteByDefault) {
        exoPlayer?.volume = if (muteByDefault) 0f else 1f
    }
    
    // Update video playback when current item changes
    LaunchedEffect(currentlyViewedId, autoPlayVideos, resumePlayback) {
        exoPlayer?.let { player ->
            // Save position of previous video before switching using ExoPlayer's current media
            val currentUri = player.currentMediaItem?.localConfiguration?.uri
            if (currentUri != null && resumePlayback) {
                val position = player.currentPosition
                val duration = player.duration
                if (position > 2000 && duration > 0 && position < duration - 3000) {
                    videoPositionDataStore.savePosition(currentUri.toString(), position)
                } else if (position >= duration - 3000 && duration > 0) {
                    videoPositionDataStore.clearPosition(currentUri.toString())
                }
            }
            
            val currentItem = mediaItems.find { it.id == currentlyViewedId }
            if (currentItem?.isVideo == true) {
                firstFrameRenderedId = null
                val mediaItem = ExoMediaItem.fromUri(currentItem.uri)
                player.setMediaItem(mediaItem)
                player.prepare()
                
                // Restore saved position if resume playback is enabled
                if (resumePlayback) {
                    val savedPosition = videoPositionDataStore.getPosition(currentItem.uri.toString())
                    if (savedPosition > 0) {
                        player.seekTo(savedPosition)
                    }
                }
                
                // Auto-play based on setting
                player.playWhenReady = autoPlayVideos
                isPlaying = autoPlayVideos
            } else {
                player.pause()
                player.clearMediaItems()
                isPlaying = false
            }
        }
    }
    
    // Save video position periodically while playing
    LaunchedEffect(currentlyViewedId, resumePlayback) {
        while (isActive && resumePlayback) {
            val currentItem = mediaItems.find { it.id == currentlyViewedId }
            if (currentItem?.isVideo == true && isPlaying) {
                exoPlayer?.let { player ->
                    val position = player.currentPosition
                    val duration = player.duration
                    // Save position every 3 seconds if video is playing
                    if (position > 2000 && duration > 0 && position < duration - 3000) {
                        videoPositionDataStore.savePosition(currentItem.uri.toString(), position)
                    } else if (position >= duration - 3000 && duration > 0) {
                        // Video near end, clear saved position
                        videoPositionDataStore.clearPosition(currentItem.uri.toString())
                    }
                }
            }
            kotlinx.coroutines.delay(3000)
        }
    }
    
    // Close progress for downward swipe (0f = not dragging, 1f = at threshold)
    val closeProgress by remember {
        derivedStateOf {
            if (gestureMode == GestureMode.VERTICAL_DOWN) {
                (abs(verticalOffset.value) / OverlayConstants.VERTICAL_CLOSE_THRESHOLD_PX).coerceIn(0f, 1f)
            } else {
                0f
            }
        }
    }

    val isSheetOpen by remember { derivedStateOf { detailsPanelProgress.value > 0.01f } }

    // When sheet is open, back gesture closes sheet only
    BackHandler(enabled = overlayState.isVisible && isSheetOpen) {
        scope.launch {
            detailsPanelProgress.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 1.0f,
                    stiffness = 300f
                )
            )
            showControls = true
        }
    }

    // When sheet is closed, back gesture closes overlay with predictive back
    PredictiveBackHandler(enabled = overlayState.isVisible && !isSheetOpen) { progressFlow ->
        try {
            progressFlow.collect { backEvent ->
                predictiveBackProgress = backEvent.progress
            }
            // Swipe committed
            if (scale > OverlayConstants.MIN_ZOOM_SCALE) {
                scale = OverlayConstants.MIN_ZOOM_SCALE
                offsetX = 0f
                offsetY = 0f
                predictiveBackProgress = 0f
            } else {
                isDismissing = true
                onDismiss()
                // Do not reset predictiveBackProgress here so the image doesn't jump back to full size before AnimatedVisibility exit transition
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Swipe cancelled
            predictiveBackProgress = 0f
        }
    }

    val requestImageForIndex: (Int) -> Unit = { index ->
        mediaItems.getOrNull(index)?.takeIf { !it.isVideo }?.let { media ->
            val imageLoader = coil.Coil.imageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(media.uri)
                .size(targetDecodeSize)
                .memoryCacheKey(media.uri.toString())
                .diskCacheKey(media.uri.toString())
                .build()
            imageLoader.enqueue(request)
        }
    }

    val preloadNeighbors: (Int) -> Unit = preload@{ index ->
        if (mediaItems.getOrNull(index)?.isVideo == true) {
            return@preload
        }
        val prevIndex = index - 1
        val nextIndex = index + 1

        val imageLoader = coil.Coil.imageLoader(context)
        mediaItems.getOrNull(prevIndex)?.takeIf { !it.isVideo }?.let { prev ->
            val request = ImageRequest.Builder(context)
                .data(prev.uri)
                .size(targetDecodeSize)
                .memoryCacheKey(prev.uri.toString())
                .diskCacheKey(prev.uri.toString())
                .build()
            imageLoader.enqueue(request)
        }

        mediaItems.getOrNull(nextIndex)?.takeIf { !it.isVideo }?.let { next ->
            val request = ImageRequest.Builder(context)
                .data(next.uri)
                .size(targetDecodeSize)
                .memoryCacheKey(next.uri.toString())
                .diskCacheKey(next.uri.toString())
                .build()
            imageLoader.enqueue(request)
        }
    }

    // Initialize state when overlay opens
    LaunchedEffect(overlayState.isVisible) {
        if (overlayState.isVisible) {
            hasResolvedInitialIndex = false
            isDismissing = false
            barsRevealUnlocked = false
            showControls = false
            imageZIndex.snapTo(1f)  // Image on top during opening animation
            openingProgress = 0f
            scale = OverlayConstants.MIN_ZOOM_SCALE
            offsetX = 0f
            offsetY = 0f
            gestureMode = GestureMode.NONE
            horizontalOffset.snapTo(0f)
            verticalOffset.snapTo(0f)
            detailsPanelProgress.snapTo(0f)
            requestImageForIndex(currentIndex)
            preloadNeighbors(currentIndex)
            // Scrim fades in starting immediately, peaks around when shared element lands
            openingProgress = 1f
            barsRevealUnlocked = true
            showControls = true
            imageZIndex.snapTo(0f)
        }
    }
    
    // Asynchronously resolve initial index when mediaItems loads
    LaunchedEffect(mediaItems, overlayState.selectedItemId, hasResolvedInitialIndex, overlayState.isVisible) {
        if (overlayState.isVisible && !hasResolvedInitialIndex && mediaItems.isNotEmpty()) {
            val targetId = overlayState.selectedItemId
            if (targetId != null) {
                val idx = mediaItems.indexOfFirst { it.id == targetId }
                if (idx >= 0) {
                    currentIndex = idx
                    hasResolvedInitialIndex = true
                } else {
                    currentIndex = overlayState.selectedIndex
                    hasResolvedInitialIndex = true
                }
            } else {
                currentIndex = overlayState.selectedIndex
                hasResolvedInitialIndex = true
            }
        }
    }
    


    // Current media item (use activeIndex for rendering to prevent blink)
    val currentItem = mediaItems.getOrNull(activeIndex)
    
    // Action handlers (defined after currentItem)
    val shareItem: () -> Unit = {
        mediaItems.getOrNull(currentIndex)?.let { item ->
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = item.mimeType
                putExtra(Intent.EXTRA_STREAM, item.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share via"))
        }
    }
    
    val deleteItem: () -> Unit = {
        mediaItems.getOrNull(currentIndex)?.let { item ->
            if (isTrashMode) {
                // In trash mode, trigger system delete dialog directly without closing overlay
                viewModel.permanentlyDelete(context, item)
                // Don't close overlay - will slide to next item after deletion
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // For Android 11+, trigger system confirmation without closing overlay
                // User will see the confirmation dialog over the media overlay
                viewModel.enterSelectionMode(item.id)
                viewModel.deleteSelectedItems(context) { success ->
                    if (success) {
                        // If no more items, close overlay
                        if (mediaItems.size <= 1) {
                            onDismiss()
                        } else {
                            // Slide to next item without closing
                            viewModel.exitSelectionMode()
                        }
                    } else {
                        viewModel.exitSelectionMode()
                    }
                }
            } else {
                // For older versions, show confirmation dialog first
                showDeleteDialog = true
            }
        }
    }
    
    // Restore from trash action (only for trash mode)
    val restoreItem: () -> Unit = {
        if (isTrashMode) {
            mediaItems.getOrNull(currentIndex)?.let { item ->
                // Trigger system restore dialog directly
                viewModel.restoreFromTrash(context, item)
                onDismiss()
            }
        }
    }
    
    val editItem: () -> Unit = {
        mediaItems.getOrNull(currentIndex)?.let { item ->
            val editIntent = Intent(Intent.ACTION_EDIT).apply {
                setDataAndType(item.uri, item.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                context.startActivity(Intent.createChooser(editIntent, "Edit with"))
            } catch (e: Exception) {
                Toast.makeText(context, "No editor app found", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    val setAsWallpaper: () -> Unit = {
        mediaItems.getOrNull(currentIndex)?.let { item ->
            if (item.isVideo) {
                Toast.makeText(context, "Cannot set video as wallpaper", Toast.LENGTH_SHORT).show()
                return@let
            }
            try {
                val wallpaperIntent = Intent(Intent.ACTION_ATTACH_DATA).apply {
                    setDataAndType(item.uri, "image/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(wallpaperIntent, "Set as"))
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    val toggleFavorite: () -> Unit = {
        mediaItems.getOrNull(currentIndex)?.let { item ->
            // Haptic feedback
            view?.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
            
            // Toggle the favorite state
            val currentState = favoriteStates[item.id] ?: item.isFavorite
            val newState = !currentState
            favoriteStates[item.id] = newState
            
            // Persist to database via ViewModel
            viewModel.toggleFavorite(item.id, newState)
            
            // Show pill message
            favoriteMessage = if (newState) "Added to favourites" else "Removed from favourites"
            showFavoritePill = true
        }
    }
    
    // Double-tap zoom handler (images only)
    val handleDoubleTap: (Float) -> Unit = { touchX ->
        if (currentItem?.isVideo == true) {
            val screenW = screenWidth
            if (touchX < screenW * 0.3f) {
                // Seek back 10s
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                seekJob?.cancel()
                if (seekIndicatorState != SeekIndicatorState.LEFT) seekAmount = 0
                seekIndicatorState = SeekIndicatorState.LEFT
                seekAmount -= 10
                exoPlayer?.let { player ->
                    val target = (player.currentPosition - 10000L).coerceAtLeast(0L)
                    player.seekTo(target)
                }
                seekJob = scope.launch {
                    delay(600)
                    seekIndicatorState = null
                    seekAmount = 0
                }
            } else if (touchX > screenW * 0.7f) {
                // Seek forward 10s
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                seekJob?.cancel()
                if (seekIndicatorState != SeekIndicatorState.RIGHT) seekAmount = 0
                seekIndicatorState = SeekIndicatorState.RIGHT
                seekAmount += 10
                exoPlayer?.let { player ->
                    val target = (player.currentPosition + 10000L).coerceAtMost(player.duration)
                    player.seekTo(target)
                }
                seekJob = scope.launch {
                    delay(600)
                    seekIndicatorState = null
                    seekAmount = 0
                }
            } else {
                // Zoom to Fit for Video
                scope.launch {
                    if (scale == 1f) {
                        val rawWidth = if (videoIntrinsicSize.width > 0f) videoIntrinsicSize.width else screenWidth
                        val rawHeight = if (videoIntrinsicSize.height > 0f) videoIntrinsicSize.height else screenHeight
                        val fitScale = kotlin.math.min(screenWidth / rawWidth, screenHeight / rawHeight)
                        val fillScale = kotlin.math.max(screenWidth / rawWidth, screenHeight / rawHeight)
                        val targetScale = (fillScale / fitScale).coerceAtLeast(1f)
                        animate(
                            initialValue = scale,
                            targetValue = targetScale,
                            animationSpec = tween(200, easing = FastOutSlowInEasing)
                        ) { value, _ ->
                            scale = value
                            offsetX = 0f
                            offsetY = 0f
                        }
                    } else {
                        animate(
                            initialValue = scale,
                            targetValue = 1f,
                            animationSpec = tween(200, easing = FastOutSlowInEasing)
                        ) { value, _ ->
                            scale = value
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
            }
        } else if (doubleTapToZoom) {
            scope.launch {
                // Calculate target scale based on zoom level setting
                val targetScale = if (scale > OverlayConstants.MIN_ZOOM_SCALE) {
                    OverlayConstants.MIN_ZOOM_SCALE  // Zoom out to fit
                } else {
                    when (doubleTapZoomLevel) {
                        "2x" -> 2f
                        "3x" -> 3f
                        "4x" -> 4f
                        else -> 2f
                    }
                }
                val targetOffsetX = if (targetScale == OverlayConstants.MIN_ZOOM_SCALE) 0f else offsetX
                val targetOffsetY = if (targetScale == OverlayConstants.MIN_ZOOM_SCALE) 0f else offsetY
                
                animate(
                    initialValue = scale,
                    targetValue = targetScale,
                    animationSpec = tween(200, easing = FastOutSlowInEasing)
                ) { value, _ ->
                    scale = value
                    offsetX = targetOffsetX * (value - 1f)
                    offsetY = targetOffsetY * (value - 1f)
                }
            }
        }
    }

    val resetZoomStateForPageChange: (Int) -> Unit = { _ ->
        scale = OverlayConstants.MIN_ZOOM_SCALE
        offsetX = 0f
        offsetY = 0f
    }

    val settleDurationForVelocity: (Float) -> Int = { velocity ->
        when {
            abs(velocity) > 8000f -> 60
            abs(velocity) > 4000f -> 100
            else -> 160
        }
    }

    val navigateNextPage: (Float) -> Unit = navigateNext@{ velocityX ->
        if (isNavigating || currentIndex >= mediaItems.size - 1) {
            return@navigateNext
        }
        isNavigating = true
        scope.launch {
            while (true) {
                horizontalMoveDeltas.tryReceive().getOrNull() ?: break
            }
            horizontalOffset.stop()

            val oldIndex = currentIndex
            val targetIndex = oldIndex + 1
            requestImageForIndex(targetIndex)

            val safeVelocity = velocityX.coerceIn(-15000f, 15000f)
            val duration = settleDurationForVelocity(safeVelocity)
            horizontalOffset.animateTo(
                targetValue = -pageWidthPx,
                animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing),
                initialVelocity = safeVelocity
            )

            currentIndex = targetIndex
            resetZoomStateForPageChange(currentIndex)
            viewModel.updateOverlayIndex(currentIndex)
            preloadNeighbors(currentIndex)
            horizontalOffset.snapTo(0f)
            isNavigating = false
        }
    }

    val navigatePreviousPage: (Float) -> Unit = navigatePrev@{ velocityX ->
        if (isNavigating || currentIndex <= 0) {
            return@navigatePrev
        }
        isNavigating = true
        scope.launch {
            while (true) {
                horizontalMoveDeltas.tryReceive().getOrNull() ?: break
            }
            horizontalOffset.stop()

            val oldIndex = currentIndex
            val targetIndex = oldIndex - 1
            requestImageForIndex(targetIndex)

            val safeVelocity = velocityX.coerceIn(-15000f, 15000f)
            val duration = settleDurationForVelocity(safeVelocity)
            horizontalOffset.animateTo(
                targetValue = pageWidthPx,
                animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing),
                initialVelocity = safeVelocity
            )

            currentIndex = targetIndex
            resetZoomStateForPageChange(currentIndex)
            viewModel.updateOverlayIndex(currentIndex)
            preloadNeighbors(currentIndex)
            horizontalOffset.snapTo(0f)
            isNavigating = false
        }
    }

    // Thumbnail bounds are captured in window coordinates, so we use them directly
    // without inset adjustment (they're already positioned relative to the window)

    // Read verticalOffset.value unconditionally so snapshot subscription
    // is always active — this ensures recomposition fires every frame
    // during the gesture regardless of current gestureMode
    val currentVerticalOffset = verticalOffset.value
    val rawCloseProgress = (abs(currentVerticalOffset) /
        OverlayConstants.VERTICAL_CLOSE_THRESHOLD_PX).coerceIn(0f, 1f)

    // Background scrim alpha - based on gesture state only
    val backgroundAlpha = when {
        isDismissing -> 0f
        predictiveBackProgress > 0f -> 1f - predictiveBackProgress
        gestureMode == GestureMode.VERTICAL_DOWN ->
            1f - rawCloseProgress
        else -> animatedOpeningProgress
    }

    // In light theme, scrim transitions between surface color (controls visible)
    // and black (controls hidden) — matching Google Photos cinema mode behaviour.
    // In dark theme, scrim is always black.
    val scrimColor by animateColorAsState(
        targetValue = when {
            isDarkTheme -> Color.Black
            showBars -> Color.White
            else -> Color.Black
        },
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "scrimColor"
    )

    // Controls visibility based on gesture
    // Hide bars when zoomed in
    val isZoomed = scale > OverlayConstants.MIN_ZOOM_SCALE
    LaunchedEffect(isZoomed) {
        if (isZoomed) {
            showBars = false
            showControls = false
        }
    }
    
    LaunchedEffect(isHoldingFor2x) {
        if (isHoldingFor2x) {
            showBars = false
            showControls = false
        }
    }


    val controlsVisible = barsRevealUnlocked && showControls && 
        showBars &&
        gestureMode != GestureMode.VERTICAL_UP &&
        !isDetailsOpen &&
        closeProgress == 0f

    // Protected UI zones where parent overlay gestures should not start.
    var topBarBounds by remember { mutableStateOf<Rect?>(null) }
    var bottomBarBounds by remember { mutableStateOf<Rect?>(null) }
    var videoControlsBounds by remember { mutableStateOf<Rect?>(null) }
    var isAnyVideoFullscreen by remember { mutableStateOf(false) }
    var lastTapTimeMs by remember { mutableLongStateOf(0L) }
    var lastTapPosition by remember { mutableStateOf(Offset.Zero) }

    val latestTopBarBounds by rememberUpdatedState(topBarBounds)
    val latestBottomBarBounds by rememberUpdatedState(bottomBarBounds)
    val latestVideoControlsBounds by rememberUpdatedState(videoControlsBounds)
    val latestControlsVisible by rememberUpdatedState(controlsVisible)
    val latestIsAnyVideoFullscreen by rememberUpdatedState(isAnyVideoFullscreen)
    val latestIsCurrentItemVideo by rememberUpdatedState(currentItem?.isVideo == true)

    val morphProgress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(seekIndicatorState) {
        if (seekIndicatorState != null) {
            morphProgress.snapTo(0f)
            kotlinx.coroutines.delay(200) // Wait a bit for the enter animation so user can see the shape start as a square
            morphProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
            )
        }
    }
    
    val baseMorph = remember { Morph(MaterialShapes.Square, MaterialShapes.Arrow) }
    // Assuming Arrow points UP, rotate -90 for left and 90 for right.
    // If it points right natively, we will need to change this to 180 and 0.
    val leftMorphShape = remember(morphProgress.value) { 
        MorphPolygonShape(baseMorph, morphProgress.value, rotationDegrees = -90f) 
    }
    val rightMorphShape = remember(morphProgress.value) { 
        MorphPolygonShape(baseMorph, morphProgress.value, rotationDegrees = 90f) 
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Still capture tap for UI controls
                detectTapGestures(
                    onTap = { tapOffset ->
                        val inBlockedZone =
                            (latestTopBarBounds?.contains(tapOffset) == true) ||
                                (latestBottomBarBounds?.contains(tapOffset) == true) ||
                                (latestVideoControlsBounds?.contains(tapOffset) == true)

                        if (!inBlockedZone) {
                            if (!isDetailsOpen) {
                                showBars = !showBars
                                if (showBars) {
                                    lastInteractionTime = System.currentTimeMillis()
                                }
                                showControls = showBars
                            }
                        }
                    }
                )
            }
    ) {
        val pagerState = androidx.compose.foundation.pager.rememberPagerState(
            initialPage = activeIndex,
            pageCount = { mediaItems.size }
        )

        // Keep currentIndex synchronized with pagerState
        LaunchedEffect(pagerState.currentPage) {
            currentIndex = pagerState.currentPage
            currentlyViewedId = mediaItems.getOrNull(pagerState.currentPage)?.id
            
            // Sync with MediaViewModel
            
        }

        // Background scrim
        val scrimAlpha = if (isDismissing) 0f else backgroundAlpha
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrimColor.copy(alpha = scrimAlpha))
        )

        var isZoomed by remember { mutableStateOf(false) }

        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(imageZIndex.value)
                .swipe(
                    enabled = !isZoomed,
                    onDragStart = {
                        scope.launch {
                            detailsPanelProgress.stop()
                            verticalOffset.stop()
                        }
                    },
                    onDrag = { dragAmount ->
                        scope.launch {
                            val dy = dragAmount.y
                            val dx = dragAmount.x
                            if (detailsPanelProgress.value > 0f) {
                                val deltaProgress = dy / (screenHeight * 0.5f)
                                val newProgress = detailsPanelProgress.value - deltaProgress
                                if (newProgress < 0f) {
                                    val overscroll = -newProgress
                                    val resistance = 1f / (1f + overscroll * 5f)
                                    detailsPanelProgress.snapTo((detailsPanelProgress.value - deltaProgress * resistance).coerceAtLeast(-0.2f))
                                } else if (newProgress > 1f) {
                                    val overscroll = newProgress - 1f
                                    val resistance = 1f / (1f + overscroll * 5f)
                                    detailsPanelProgress.snapTo((detailsPanelProgress.value - deltaProgress * resistance).coerceAtMost(1.2f))
                                } else {
                                    detailsPanelProgress.snapTo(newProgress)
                                }
                                showControls = detailsPanelProgress.value < 0.1f
                            } else if (dy < 0 && swipeUpToDetails) {
                                val deltaProgress = dy / (screenHeight * 0.5f)
                                detailsPanelProgress.snapTo((detailsPanelProgress.value - deltaProgress).coerceIn(0f, 1f))
                                showControls = detailsPanelProgress.value < 0.1f
                            } else {
                                if (dy > 0 || verticalOffset.value > 0f || dy < 0) {
                                    val newOffset = verticalOffset.value + dy
                                    if (newOffset < 0f) {
                                        val overscroll = -newOffset
                                        val resistance = 1f / (1f + overscroll / 400f)
                                        verticalOffset.snapTo(verticalOffset.value + dy * resistance)
                                    } else {
                                        verticalOffset.snapTo(newOffset)
                                        predictiveBackProgress = (verticalOffset.value / 200f).coerceIn(0f, 1f)
                                    }
                                }
                            }
                        }
                    },
                    onDragEnd = { velocity ->
                        scope.launch {
                            val vy = velocity.y
                            if (detailsPanelProgress.value > 0f) {
                                val shouldOpen = if (vy < -800f) true 
                                                 else if (vy > 800f) false
                                                 else detailsPanelProgress.value > 0.5f
                                
                                detailsPanelProgress.animateTo(
                                    targetValue = if (shouldOpen) 1f else 0f,
                                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 380f)
                                )
                                showControls = !shouldOpen
                            } else if (verticalOffset.value > 0f || verticalOffset.value < 0f) {
                                if (vy > 800f || verticalOffset.value > 200f) {
                                    isDismissing = true
                                    onDismiss()
                                } else {
                                    verticalOffset.animateTo(0f, androidx.compose.animation.core.spring())
                                    predictiveBackProgress = 0f
                                }
                            }
                        }
                    }
                ),
            pageSpacing = 16.dp,
            beyondViewportPageCount = 1
        ) { page ->
            val pageItem = mediaItems.getOrNull(page) ?: return@HorizontalPager
            
            if (pageItem.isVideo) {
                val videoZoomState = rememberZoomState()
                
                LaunchedEffect(videoZoomState.scale) {
                    if (page == pagerState.currentPage) {
                        isZoomed = videoZoomState.scale > 1.0f
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                            val detailsProgress = detailsPanelProgress.value.coerceIn(0f, 1f)
                            
                            val rawImageWidth = exoPlayer?.videoFormat?.width?.toFloat() ?: screenWidth
                            val rawImageHeight = exoPlayer?.videoFormat?.height?.toFloat() ?: screenHeight
                            val fitScale = kotlin.math.min(screenWidth / rawImageWidth, screenHeight / rawImageHeight)
                            val fillScale = kotlin.math.max(screenWidth / rawImageWidth, screenHeight / rawImageHeight)
                            val detailsZoomTarget = (fillScale / fitScale).coerceAtLeast(1f)
                            val detailsScale = 1f + (detailsZoomTarget - 1f) * detailsProgress

                            if (page == pagerState.currentPage && detailsProgress > 0.01f) {
                                val imageOffset = screenHeight * 0.25f * detailsProgress
                                this.translationY = -imageOffset
                                this.scaleX = detailsScale
                                this.scaleY = detailsScale
                            } else {
                                this.translationY = verticalOffset.value
                                // Pinch to dismiss scaling removed
                                if (predictiveBackProgress > 0f) {
                                    val predScale = 1f - (predictiveBackProgress * 0.1f)
                                    this.scaleX *= predScale
                                    this.scaleY *= predScale
                                    this.shape = androidx.compose.foundation.shape.RoundedCornerShape((predictiveBackProgress * 32f).dp)
                                    this.clip = true
                                }
                            }
                        }
                        .zoomable(videoZoomState, onTap = { 
                            if (!isDetailsOpen) {
                                showBars = !showBars
                                if (showBars) {
                                    lastInteractionTime = System.currentTimeMillis()
                                }
                                showControls = showBars
                            }
                        })
                ) {
                    // Always keep the thumbnail in the tree to prevent flashes, just hide it after the first frame renders
                    val showThumbnail = page != pagerState.currentPage || firstFrameRenderedId != pageItem.id
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(pageItem.uri)
                            .size(targetDecodeSize)
                            .memoryCacheKey(pageItem.uri.toString())
                            .diskCacheKey(pageItem.uri.toString())
                            .build(),
                        contentDescription = pageItem.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            alpha = if (showThumbnail) 1f else 0f
                        }
                    )

                    if (page == pagerState.currentPage) {
                        exoPlayer?.let { player ->
                            AndroidView(
                                factory = { ctx ->
                                    val view = android.view.LayoutInflater.from(ctx).inflate(com.prantiux.pixelgallery.R.layout.video_player_view, null) as androidx.media3.ui.PlayerView
                                    view.player = player
                                    view.setKeepContentOnPlayerReset(false)
                                    view.setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                                    view
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                            
                            // Left Seek Zone
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(0.3f)
                                    .align(Alignment.CenterStart)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onDoubleTap = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                seekJob?.cancel()
                                                if (seekIndicatorState != SeekIndicatorState.LEFT) seekAmount = 0
                                                seekIndicatorState = SeekIndicatorState.LEFT
                                                seekAmount -= 10
                                                val target = (player.currentPosition - 10000L).coerceAtLeast(0L)
                                                player.seekTo(target)
                                                seekJob = scope.launch {
                                                    kotlinx.coroutines.delay(600)
                                                    seekIndicatorState = null
                                                    seekAmount = 0
                                                }
                                            },
                                            onTap = {
                                                if (!isDetailsOpen) {
                                                    showBars = !showBars
                                                    if (showBars) {
                                                        lastInteractionTime = System.currentTimeMillis()
                                                    }
                                                    showControls = showBars
                                                }
                                            }
                                        )
                                    }
                            )
                            
                            // Right Seek Zone
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(0.3f)
                                    .align(Alignment.CenterEnd)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onDoubleTap = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                seekJob?.cancel()
                                                if (seekIndicatorState != SeekIndicatorState.RIGHT) seekAmount = 0
                                                seekIndicatorState = SeekIndicatorState.RIGHT
                                                seekAmount += 10
                                                val target = (player.currentPosition + 10000L).coerceAtMost(player.duration)
                                                player.seekTo(target)
                                                seekJob = scope.launch {
                                                    kotlinx.coroutines.delay(600)
                                                    seekIndicatorState = null
                                                    seekAmount = 0
                                                }
                                            },
                                            onTap = {
                                                if (!isDetailsOpen) {
                                                    showBars = !showBars
                                                    if (showBars) {
                                                        lastInteractionTime = System.currentTimeMillis()
                                                    }
                                                    showControls = showBars
                                                }
                                            }
                                        )
                                    }
                            )
                            
                            // Center area for 2x speed (long press)
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(0.4f)
                                    .align(Alignment.Center)
                                    .pointerInput(Unit) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            if (player.isPlaying) {
                                                var localHoldingFor2x = false
                                                val holdJobLocal = scope.launch {
                                                    kotlinx.coroutines.delay(500)
                                                    localHoldingFor2x = true
                                                    isHoldingFor2x = true
                                                    player.setPlaybackSpeed(2f)
                                                }
                                                var up = false
                                                while (!up) {
                                                    val event = awaitPointerEvent()
                                                    if (event.changes.any { it.positionChange().getDistance() > 10f }) {
                                                        holdJobLocal.cancel()
                                                        if (localHoldingFor2x) {
                                                            player.setPlaybackSpeed(1f)
                                                            isHoldingFor2x = false
                                                        }
                                                    }
                                                    if (event.changes.none { it.pressed }) {
                                                        up = true
                                                    }
                                                }
                                                holdJobLocal.cancel()
                                                if (localHoldingFor2x) {
                                                    player.setPlaybackSpeed(1f)
                                                    isHoldingFor2x = false
                                                }
                                            }
                                        }
                                    }
                            )

                            // Seek Ripples
                            AnimatedVisibility(
                                visible = seekIndicatorState == SeekIndicatorState.LEFT,
                                enter = slideInHorizontally(animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f)) { -it } + fadeIn(),
                                exit = slideOutHorizontally() { -it } + fadeOut(),
                                modifier = Modifier.align(Alignment.CenterStart).padding(start = 32.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(140.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                            shape = leftMorphShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Rounded.FastRewind,
                                            contentDescription = "Rewind",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = "${abs(seekAmount)}s",
                                            color = MaterialTheme.colorScheme.onSurface,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                }
                            }

                            AnimatedVisibility(
                                visible = seekIndicatorState == SeekIndicatorState.RIGHT,
                                enter = slideInHorizontally(animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f)) { it } + fadeIn(),
                                exit = slideOutHorizontally() { it } + fadeOut(),
                                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 32.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(140.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                            shape = rightMorphShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Rounded.FastForward,
                                            contentDescription = "Forward",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = "${abs(seekAmount)}s",
                                            color = MaterialTheme.colorScheme.onSurface,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                }
                            }

                            // 2x Speed Pill
                            AnimatedVisibility(
                                visible = isHoldingFor2x,
                                enter = slideInVertically(animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f)) { -it } + fadeIn(),
                                exit = slideOutVertically() { -it } + fadeOut(),
                                modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp)
                            ) {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.6f),
                                    shape = androidx.compose.foundation.shape.CircleShape
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = "2x Speed",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Icon(
                                            imageVector = Icons.Rounded.FastForward,
                                            contentDescription = "Fast Forward",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Image Page
                val zoomState = rememberZoomState()
                var imageIntrinsicSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
                
                // Disable pager scrolling when zoomed in
                LaunchedEffect(zoomState.scale) {
                    if (page == pagerState.currentPage) {
                        isZoomed = zoomState.scale > 1.0f
                    }
                }
                
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(pageItem.uri)
                        .size(targetDecodeSize)
                        .memoryCacheKey(pageItem.uri.toString())
                        .diskCacheKey(pageItem.uri.toString())
                        .build(),
                    contentDescription = pageItem.displayName,
                    contentScale = ContentScale.Fit,
                    onSuccess = { state ->
                        imageIntrinsicSize = state.painter.intrinsicSize
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                            val detailsProgress = detailsPanelProgress.value.coerceIn(0f, 1f)
                            
                            val rawImageWidth = if (imageIntrinsicSize.width > 0f) imageIntrinsicSize.width else screenWidth
                            val rawImageHeight = if (imageIntrinsicSize.height > 0f) imageIntrinsicSize.height else screenHeight
                            val fitScale = kotlin.math.min(screenWidth / rawImageWidth, screenHeight / rawImageHeight)
                            val fillScale = kotlin.math.max(screenWidth / rawImageWidth, screenHeight / rawImageHeight)
                            val detailsZoomTarget = (fillScale / fitScale).coerceAtLeast(1f)
                            val detailsScale = 1f + (detailsZoomTarget - 1f) * detailsProgress

                            if (page == pagerState.currentPage && detailsProgress > 0.01f) {
                                val imageOffset = screenHeight * 0.25f * detailsProgress
                                this.translationY = -imageOffset
                                this.scaleX = detailsScale
                                this.scaleY = detailsScale
                            } else {
                                this.translationY = verticalOffset.value
                                // Pinch to dismiss scaling removed
                                if (predictiveBackProgress > 0f) {
                                    val predScale = 1f - (predictiveBackProgress * 0.1f)
                                    this.scaleX *= predScale
                                    this.scaleY *= predScale
                                    this.shape = androidx.compose.foundation.shape.RoundedCornerShape((predictiveBackProgress * 32f).dp)
                                    this.clip = true
                                }
                            }
                        }
                        .zoomable(zoomState, onTap = { 
                            if (!isDetailsOpen) {
                                showBars = !showBars
                                if (showBars) {
                                    lastInteractionTime = System.currentTimeMillis()
                                }
                                showControls = showBars
                            }
                        })
                )
            }
        }
        val detailsTopBarLayerVisible = isDetailsOpen && !isAnyVideoFullscreen

        if (detailsTopBarLayerVisible) {
            val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(topInset + 20.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to barColor,
                                0.45f to barColor,
                                0.65f to barColor.copy(alpha = 0.85f),
                                0.8f to barColor.copy(alpha = 0.5f),
                                0.92f to barColor.copy(alpha = 0.15f),
                                1.0f to Color.Transparent
                            )
                        )
                    )
            )
        }

        // Top header (animated) - hide during fullscreen
        AnimatedVisibility(
            visible = barsRevealUnlocked && showControls && showBars && !isAnyVideoFullscreen &&
                !isDetailsOpen && !isDismissing &&
                gestureMode != GestureMode.VERTICAL_DOWN &&
                currentVerticalOffset <= 0f && predictiveBackProgress == 0f,
            enter = fadeIn(
                animationSpec = tween(
                    durationMillis = 200,
                    delayMillis = 0,
                    easing = FastOutSlowInEasing
                )
            ),
            exit = fadeOut(
                animationSpec = tween(
                    durationMillis = 120,
                    delayMillis = 30,
                    easing = FastOutLinearInEasing
                )
            ) + slideOutVertically(
                animationSpec = tween(
                    durationMillis = 140,
                    delayMillis = 30,
                    easing = FastOutLinearInEasing
                )
            ) { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    // Fade out and slide up during downward swipe
                    alpha = 1f - closeProgress
                    translationY = -closeProgress * 100f
                }
        ) {
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Background extends behind status bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            if (isZoomed) {
                                val extraHeight = 48.dp.toPx()
                                val totalHeight = size.height + extraHeight
                                val brush = Brush.verticalGradient(
                                    colors = listOf(
                                        barColor,
                                        barColor.copy(alpha = barColor.alpha * 0.7f),
                                        barColor.copy(alpha = barColor.alpha * 0.3f),
                                        barColor.copy(alpha = barColor.alpha * 0.1f),
                                        Color.Transparent
                                    ),
                                    startY = 0f,
                                    endY = totalHeight
                                )
                                drawRect(
                                    brush = brush,
                                    size = androidx.compose.ui.geometry.Size(size.width, totalHeight)
                                )
                            } else {
                                drawRect(color = barColor)
                            }
                        }
                        .onGloballyPositioned { coordinates ->
                            topBarBounds = coordinates.boundsInRoot()
                        }
                        .statusBarsPadding()  // Content respects status bar inset
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left side: Location + Date/Time stacked
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            // Date + Time
                            Text(
                                text = remember(currentItem?.dateAdded) {
                                    currentItem?.let {
                                        java.text.SimpleDateFormat(
                                            "MMM d, yyyy • h:mm a",
                                            java.util.Locale.getDefault()
                                        ).format(java.util.Date(it.dateAdded * 1000))
                                    } ?: ""
                                },
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        // Right side: Favorite button (replaces three-dot menu)
                        if (!isTrashMode) {
                            val isFavorited = currentItem?.let { favoriteStates[it.id] ?: it.isFavorite } ?: false

                            IconButton(onClick = toggleFavorite) {
                                FontIcon(
                                    unicode = FontIcons.Star,
                                    contentDescription = if (isFavorited) "Remove from favorites" else "Add to favorites",
                                    tint = if (isFavorited) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurface,
                                    filled = isFavorited
                                )
                            }
                        }
                    }
                }

            }
        }

        // Bottom controls (animated)
        AnimatedVisibility(
            visible = barsRevealUnlocked && showControls && showBars && !isDetailsOpen &&
                !isDismissing && gestureMode != GestureMode.VERTICAL_DOWN &&
                currentVerticalOffset <= 0f && predictiveBackProgress == 0f,
            enter = fadeIn(
                animationSpec = tween(
                    durationMillis = 200,
                    delayMillis = 0,
                    easing = FastOutSlowInEasing
                )
            ),
            exit = fadeOut(
                animationSpec = tween(
                    durationMillis = 120,
                    delayMillis = 0,
                    easing = FastOutLinearInEasing
                )
            ) + slideOutVertically(
                animationSpec = tween(
                    durationMillis = 140,
                    delayMillis = 0,
                    easing = FastOutLinearInEasing
                )
            ) { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer {
                    // Fade out and slide down during downward swipe
                    alpha = 1f - closeProgress
                    translationY = closeProgress * 100f
                }
        ) {
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Background extends behind navigation bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            if (latestIsCurrentItemVideo) {
                                val vcHeight = latestVideoControlsBounds?.height ?: 0f
                                if (isZoomed) {
                                    val totalHeight = size.height + vcHeight
                                    val smoothGradient = listOf(
                                        Color.Transparent,
                                        barColor.copy(alpha = barColor.alpha * 0.1f),
                                        barColor.copy(alpha = barColor.alpha * 0.3f),
                                        barColor.copy(alpha = barColor.alpha * 0.7f),
                                        barColor
                                    )
                                    val brush = Brush.verticalGradient(
                                        colors = smoothGradient,
                                        startY = -vcHeight,
                                        endY = size.height
                                    )
                                    drawRect(
                                        brush = brush,
                                        topLeft = androidx.compose.ui.geometry.Offset(0f, -vcHeight),
                                        size = androidx.compose.ui.geometry.Size(size.width, totalHeight)
                                    )
                                } else {
                                    if (vcHeight > 0f) {
                                        val smoothGradient = listOf(
                                            Color.Transparent,
                                            barColor.copy(alpha = barColor.alpha * 0.1f),
                                            barColor.copy(alpha = barColor.alpha * 0.3f),
                                            barColor.copy(alpha = barColor.alpha * 0.7f),
                                            barColor
                                        )
                                        val brush = Brush.verticalGradient(
                                            colors = smoothGradient,
                                            startY = -vcHeight,
                                            endY = 0f
                                        )
                                        drawRect(
                                            brush = brush,
                                            topLeft = androidx.compose.ui.geometry.Offset(0f, -vcHeight),
                                            size = androidx.compose.ui.geometry.Size(size.width, vcHeight)
                                        )
                                    }
                                    drawRect(color = barColor)
                                }
                            } else {
                                if (isZoomed) {
                                    val extraHeight = 48.dp.toPx()
                                    val totalHeight = size.height + extraHeight
                                    val brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            barColor.copy(alpha = barColor.alpha * 0.1f),
                                            barColor.copy(alpha = barColor.alpha * 0.3f),
                                            barColor.copy(alpha = barColor.alpha * 0.7f),
                                            barColor
                                        ),
                                        startY = -extraHeight,
                                        endY = size.height
                                    )
                                    drawRect(
                                        brush = brush,
                                        topLeft = androidx.compose.ui.geometry.Offset(0f, -extraHeight),
                                        size = androidx.compose.ui.geometry.Size(size.width, totalHeight)
                                    )
                                } else {
                                    drawRect(color = barColor)
                                }
                            }
                        }
                        .onGloballyPositioned { coordinates ->
                            bottomBarBounds = coordinates.boundsInRoot()
                        }
                        .navigationBarsPadding()  // Content respects navigation bar inset
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isTrashMode) {
                            // Trash mode: Only Restore and Delete
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(bounded = false, radius = 32.dp)
                                    ) { restoreItem() }
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                FontIcon(
                                    unicode = FontIcons.Refresh,
                                    contentDescription = "Restore",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Restore",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(bounded = false, radius = 32.dp)
                                    ) { deleteItem() }
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                FontIcon(
                                    unicode = FontIcons.Delete,
                                    contentDescription = "Delete permanently",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Delete",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        } else {
                            // Normal mode: Edit -> Share -> Delete -> Hamburger Menu

                            // Edit
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(bounded = false, radius = 32.dp)
                                    ) { editItem() }
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                FontIcon(
                                    unicode = FontIcons.Edit,
                                    contentDescription = "Edit",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Edit",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Share
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(bounded = false, radius = 32.dp)
                                    ) { shareItem() }
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                FontIcon(
                                    unicode = FontIcons.Share,
                                    contentDescription = "Share",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Share",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Delete
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(bounded = false, radius = 32.dp)
                                    ) { deleteItem() }
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                FontIcon(
                                    unicode = FontIcons.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Delete",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Hamburger Menu (more options)
                            Box {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = ripple(bounded = false, radius = 32.dp)
                                        ) { menuExpanded = true }
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    FontIcon(
                                        unicode = FontIcons.Menu,
                                        contentDescription = "More options",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "More",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                    // Dropdown menu
                                    DropdownMenu(
                                        expanded = menuExpanded,
                                        onDismissRequest = { menuExpanded = false },
                                        modifier = Modifier.widthIn(min = 220.dp),
                                        offset = DpOffset(x = (-8).dp, y = (-8).dp),
                                        shape = SmoothCornerShape(20.dp, 60),
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) {
                                    // 1. Set as wallpaper
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                Surface(
                                                    shape = MaterialShapes.Cookie7Sided.toShape(),
                                                    color = Color.White.copy(alpha = 0.15f),
                                                    modifier = Modifier.size(40.dp)
                                                ) {
                                                    Box(
                                                        contentAlignment = Alignment.Center,
                                                        modifier = Modifier.fillMaxSize()
                                                    ) {
                                                        FontIcon(
                                                            unicode = FontIcons.Image,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onSurface,
                                                            size = 20.sp
                                                        )
                                                    }
                                                }
                                                Text(
                                                    "Set as wallpaper",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                                    color = Color.White
                                                )
                                            }
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            setAsWallpaper()
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                    // 2. Copy to album
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                Surface(
                                                    shape = MaterialShapes.Cookie7Sided.toShape(),
                                                    color = Color.White.copy(alpha = 0.15f),
                                                    modifier = Modifier.size(40.dp)
                                                ) {
                                                    Box(
                                                        contentAlignment = Alignment.Center,
                                                        modifier = Modifier.fillMaxSize()
                                                    ) {
                                                        FontIcon(
                                                            unicode = FontIcons.Copy,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onSurface,
                                                            size = 20.sp
                                                        )
                                                    }
                                                }
                                                Text(
                                                    "Copy to album",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                                    color = Color.White
                                                )
                                            }
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            // Show copy to album dialog with current item
                                            viewModel.showCopyToAlbumDialog(listOfNotNull(currentItem?.id))
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                    // 3. Move to album
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                Surface(
                                                    shape = MaterialShapes.Cookie7Sided.toShape(),
                                                    color = Color.White.copy(alpha = 0.15f),
                                                    modifier = Modifier.size(40.dp)
                                                ) {
                                                    Box(
                                                        contentAlignment = Alignment.Center,
                                                        modifier = Modifier.fillMaxSize()
                                                    ) {
                                                        FontIcon(
                                                            unicode = FontIcons.Move,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onSurface,
                                                            size = 20.sp
                                                        )
                                                    }
                                                }
                                                Text(
                                                    "Move to album",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                                    color = Color.White
                                                )
                                            }
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            // Show move to album dialog with current item
                                            viewModel.showMoveToAlbumDialog(listOfNotNull(currentItem?.id))
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                    // 4. Details
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                Surface(
                                                    shape = MaterialShapes.Cookie7Sided.toShape(),
                                                    color = Color.White.copy(alpha = 0.15f),
                                                    modifier = Modifier.size(40.dp)
                                                ) {
                                                    Box(
                                                        contentAlignment = Alignment.Center,
                                                        modifier = Modifier.fillMaxSize()
                                                    ) {
                                                        FontIcon(
                                                            unicode = FontIcons.Info,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onSurface,
                                                            size = 20.sp
                                                        )
                                                    }
                                                }
                                                Text(
                                                    "Details",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                                    color = Color.White
                                                )
                                            }
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            showControls = false
                                            scope.launch {
                                                detailsPanelProgress.animateTo(
                                                    targetValue = 1f,
                                                    animationSpec = spring(
                                                        dampingRatio = 0.8f,
                                                        stiffness = 380f
                                                    )
                                                )
                                            }
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                    }
                            }
                        }
                    }
                }
            }
        }

        // Favorite message pill - appears above bottom bar with animation
        AnimatedVisibility(
            visible = showFavoritePill,
            enter = fadeIn(animationSpec = tween(200)) + slideInVertically(
                initialOffsetY = { it / 2 },
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ),
            exit = fadeOut(animationSpec = tween(200)) + slideOutVertically(
                targetOffsetY = { it / 2 },
                animationSpec = tween(200, easing = FastOutLinearInEasing)
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp) // Space above bottom bar
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 6.dp,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FontIcon(
                        unicode = FontIcons.Star,
                        contentDescription = null,
                        size = 18.sp,
                        tint = Color(0xFFFFD700)
                    )
                    Text(
                        text = favoriteMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        
        // Custom video controls overlay (Placed here to draw natively on top of the bottom bar)
        exoPlayer?.let { player ->
            if (currentItem?.isVideo == true) {
                AnimatedVisibility(
                    visible = controlsVisible && !isVideoFullscreen,
                    enter = fadeIn(animationSpec = tween(300)),
                    exit = fadeOut(animationSpec = tween(300)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    val bottomBarHeightDp = with(LocalDensity.current) {
                        bottomBarBounds?.height?.toDp() ?: 0.dp
                    }
                    CustomVideoControls(
                        exoPlayer = player,
                        mediaItem = currentItem,
                        isPlaying = isPlaying,
                        bottomPadding = bottomBarHeightDp,
                        onPlayPauseClick = {
                            if (player.isPlaying) {
                                player.pause()
                                isPlaying = false
                            } else {
                                player.play()
                                isPlaying = true
                            }
                        },
                        onFullscreenChange = { isFullscreen ->
                            isVideoFullscreen = isFullscreen
                            isAnyVideoFullscreen = isFullscreen
                        },
                        onControlsBoundsChanged = { bounds ->
                            videoControlsBounds = bounds
                        },
                        onInteraction = {
                            lastInteractionTime = System.currentTimeMillis()
                        }
                    )
                }
            }
        }

        if (detailsPanelProgress.value > 0f) {
            val progress = detailsPanelProgress.value.coerceIn(0f, 1f)
            val leadingAlpha = (progress * 1.5f).coerceIn(0f, 1f)

            // Scrim — matches ModalBottomSheet scrim behavior
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f * progress))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                scope.launch {
                                    detailsPanelProgress.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = 300f))
                                    showControls = true
                                }
                            }
                        )
                    }
                    .swipe(
                        enabled = true,
                        onDragStart = {
                            scope.launch { detailsPanelProgress.stop(); verticalOffset.stop() }
                        },
                        onDrag = { dragAmount ->
                            scope.launch {
                                val dy = dragAmount.y
                                if (detailsPanelProgress.value > 0f) {
                                    val deltaProgress = dy / (screenHeight * 0.5f)
                                    val newProgress = detailsPanelProgress.value - deltaProgress
                                    if (newProgress < 0f) {
                                        val overscroll = -newProgress
                                        val resistance = 1f / (1f + overscroll * 5f)
                                        detailsPanelProgress.snapTo((detailsPanelProgress.value - deltaProgress * resistance).coerceAtLeast(-0.2f))
                                    } else if (newProgress > 1f) {
                                        val overscroll = newProgress - 1f
                                        val resistance = 1f / (1f + overscroll * 5f)
                                        detailsPanelProgress.snapTo((detailsPanelProgress.value - deltaProgress * resistance).coerceAtMost(1.2f))
                                    } else {
                                        detailsPanelProgress.snapTo(newProgress)
                                    }
                                    showControls = detailsPanelProgress.value < 0.1f
                                }
                            }
                        },
                        onDragEnd = { velocity ->
                            scope.launch {
                                val vy = velocity.y
                                if (detailsPanelProgress.value > 0f) {
                                    val shouldOpen = if (vy < -800f) true 
                                                     else if (vy > 800f) false
                                                     else detailsPanelProgress.value > 0.5f
                                    detailsPanelProgress.animateTo(if (shouldOpen) 1f else 0f, androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 380f))
                                    showControls = !shouldOpen
                                }
                            }
                        }
                    )
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.55f)
                    .graphicsLayer {
                        val panelHeight = size.height
                        translationY = (1f - progress) * panelHeight
                        alpha = leadingAlpha
                    }
                    .nestedScroll(remember {
                        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                                if (available.y > 0 && detailsPanelProgress.value > 0f && detailsPanelProgress.value < 1f) {
                                    scope.launch {
                                        val deltaProgress = available.y / (screenHeight * 0.5f)
                                        detailsPanelProgress.snapTo((detailsPanelProgress.value - deltaProgress).coerceIn(0f, 1f))
                                        showControls = detailsPanelProgress.value < 0.1f
                                    }
                                    return androidx.compose.ui.geometry.Offset(0f, available.y)
                                }
                                return androidx.compose.ui.geometry.Offset.Zero
                            }
                            
                            override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                                if (available.y > 0 && detailsPanelProgress.value > 0f) {
                                    scope.launch {
                                        val deltaProgress = available.y / (screenHeight * 0.5f)
                                        detailsPanelProgress.snapTo((detailsPanelProgress.value - deltaProgress).coerceIn(0f, 1f))
                                        showControls = detailsPanelProgress.value < 0.1f
                                    }
                                    return androidx.compose.ui.geometry.Offset(0f, available.y)
                                }
                                return androidx.compose.ui.geometry.Offset.Zero
                            }

                            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                                if (detailsPanelProgress.value < 1f) {
                                    if (detailsPanelProgress.value > 0f) {
                                        val shouldOpen = if (available.y < -800f) true 
                                                         else if (available.y > 800f) false
                                                         else detailsPanelProgress.value > 0.5f
                                        detailsPanelProgress.animateTo(if (shouldOpen) 1f else 0f, androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 380f))
                                        showControls = !shouldOpen
                                    }
                                    return available
                                }
                                return androidx.compose.ui.unit.Velocity.Zero
                            }
                            
                            override suspend fun onPostFling(consumed: androidx.compose.ui.unit.Velocity, available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                                if (available.y > 0f && detailsPanelProgress.value > 0f) {
                                    val shouldOpen = if (available.y < -800f) true 
                                                     else if (available.y > 800f) false
                                                     else detailsPanelProgress.value > 0.5f
                                    detailsPanelProgress.animateTo(if (shouldOpen) 1f else 0f, androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 380f))
                                    showControls = !shouldOpen
                                    return available
                                }
                                return androidx.compose.ui.unit.Velocity.Zero
                            }
                        }
                    })
                    .pointerInput(Unit) {
                        detectTapGestures { } // Consume taps so they don't fall through
                    },
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(
                    topStart = 28.dp,
                    topEnd = 28.dp,
                    bottomStart = 0.dp,
                    bottomEnd = 0.dp
                ),
                tonalElevation = 1.dp,
                shadowElevation = 0.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Drag handle pill — exact Material 3 spec
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 22.dp, bottom = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(32.dp)
                                .height(4.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                        .copy(alpha = 0.4f),
                                    shape = CircleShape
                                )
                        )
                    }

                    currentItem?.let { item ->
                        DetailsBottomSheetContent(
                            mediaItem = item,
                            onEditMetadata = {
                                scope.launch {
                                    detailsPanelProgress.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = 1.0f,
                                            stiffness = 300f
                                        )
                                    )
                                    showControls = true
                                }
                                editItem()
                            }
                        )
                    }
                }
            }
        }

    }
    
    // Delete confirmation dialog (only for older Android versions, not for trash)
    if (showDeleteDialog && Build.VERSION.SDK_INT < Build.VERSION_CODES.R && !isTrashMode) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete item?") },
            text = { Text("This item will be permanently deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        view?.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
                        mediaItems.getOrNull(currentIndex)?.let { item ->
                            try {
                                val deleted = context.contentResolver.delete(item.uri, null, null)
                                if (deleted > 0) {
                                    Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                                    viewModel.refresh(context, showLoader = false)
                                    onDismiss()
                                } else {
                                    Toast.makeText(context, "Failed to delete", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    view?.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
                    showDeleteDialog = false 
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Custom video controls overlay with Material 3 design
 */
@Composable
private fun CustomVideoControls(
    exoPlayer: ExoPlayer,
    mediaItem: com.prantiux.pixelgallery.model.MediaItem?,
    isPlaying: Boolean,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onPlayPauseClick: () -> Unit,
    onFullscreenChange: (Boolean) -> Unit,
    onControlsBoundsChanged: (androidx.compose.ui.geometry.Rect) -> Unit,
    onInteraction: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var isMuted by remember { mutableStateOf(exoPlayer.volume == 0f) }
    var isFullscreen by remember { mutableStateOf(false) }
    
    // Track duration and ended state
    var duration by remember(mediaItem?.id) { mutableLongStateOf(exoPlayer.duration.coerceAtLeast(0L)) }
    var videoEnded by remember(mediaItem?.id) { mutableStateOf(exoPlayer.playbackState == androidx.media3.common.Player.STATE_ENDED) }
    
    // Shared scrub and position state between slider and time text
    val scrubPosition = remember(mediaItem?.id) { mutableStateOf<Long?>(null) }
    val currentPositionState = remember(mediaItem?.id) { mutableLongStateOf(exoPlayer.currentPosition.coerceAtLeast(0L)) }
    
    // Continually check for end state and duration changes
    LaunchedEffect(Unit) {
        while (isActive) {
            duration = exoPlayer.duration.coerceAtLeast(0L)
            videoEnded = exoPlayer.playbackState == androidx.media3.common.Player.STATE_ENDED
            kotlinx.coroutines.delay(200L)
        }
    }
    
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()
    var isExportingFrame by remember { mutableStateOf(false) }
    
    // Notify parent about fullscreen changes
    LaunchedEffect(isFullscreen) {
        onFullscreenChange(isFullscreen)
    }
    
    // Animated button shape for play/pause with spring animation
    val playButtonShape by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "playButtonShape"
    )
    
    // Animated button shape for mute with spring animation
    val muteButtonShape by animateFloatAsState(
        targetValue = if (isMuted) 0f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "muteButtonShape"
    )
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Video progress bar and time above bottom bar with gradient background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .onGloballyPositioned { coordinates ->
                    onControlsBoundsChanged(coordinates.boundsInWindow())
                }
                .padding(bottom = bottomPadding) // Attached to bottom bar, no spacing
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent()
                            onInteraction()
                        }
                    }
                }
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Play/Pause button and Time display - button left, time center
                Box(modifier = Modifier.fillMaxWidth()) {
                    // Play/Pause/Restart button (left-aligned as a pill with bounce effect)
                    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = if (isPressed) 0.85f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "playScale"
                    )
                    
                    Surface(
                        onClick = {
                            if (videoEnded) {
                                // Restart video
                                exoPlayer.seekTo(0)
                                exoPlayer.play()
                                videoEnded = false
                            } else {
                                onPlayPauseClick()
                            }
                        },
                        interactionSource = interactionSource,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        modifier = Modifier
                            .height(36.dp)
                            .width(64.dp)
                            .align(Alignment.CenterStart)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            FontIcon(
                                unicode = when {
                                    videoEnded -> FontIcons.Refresh
                                    isPlaying -> FontIcons.Pause
                                    else -> FontIcons.PlayArrow
                                },
                                contentDescription = when {
                                    videoEnded -> "Restart"
                                    isPlaying -> "Pause"
                                    else -> "Play"
                                },
                                tint = MaterialTheme.colorScheme.onSurface,
                                size = 20.sp,
                                filled = true // Play, Pause and Restart look best filled
                            )
                        }
                    }
                    
                    // Time display (center-aligned)
                    Row(
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        VideoTime(
                            duration = duration, 
                            scrubPosition = scrubPosition, 
                            currentPositionState = currentPositionState
                        )
                    }
                    
                    // Right-aligned controls: Mute/Unmute and Fullscreen
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        // Mute/Unmute button (left half of split pill)
                        val muteInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        val isMutePressed by muteInteraction.collectIsPressedAsState()
                        val muteScale by animateFloatAsState(
                            targetValue = if (isMutePressed) 0.85f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "muteScale"
                        )
                        
                        Surface(
                            onClick = {
                                isMuted = !isMuted
                                exoPlayer.volume = if (isMuted) 0f else 1f
                            },
                            interactionSource = muteInteraction,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp, topEnd = 4.dp, bottomEnd = 4.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                            modifier = Modifier
                                .size(width = 44.dp, height = 36.dp)
                                .graphicsLayer {
                                    scaleX = muteScale
                                    scaleY = muteScale
                                }
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                FontIcon(
                                    unicode = if (isMuted) FontIcons.VolumeOff else FontIcons.VolumeUp,
                                    contentDescription = if (isMuted) "Unmute" else "Mute",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    size = 20.sp
                                )
                            }
                        }
                        
                        // Frame Export button (right half of split pill)
                        val exportInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        val isExportPressed by exportInteraction.collectIsPressedAsState()
                        val exportScale by animateFloatAsState(
                            targetValue = if (isExportPressed) 0.85f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "exportScale"
                        )
                        
                        Surface(
                            onClick = {
                                if (isExportingFrame) return@Surface
                                isExportingFrame = true
                                if (mediaItem != null) {
                                    val position = exoPlayer.currentPosition
                                    coroutineScope.launch {
                                        extractAndSaveFrame(context, mediaItem.uri, position, mediaItem.dateAdded * 1000L) { success ->
                                            isExportingFrame = false
                                            Toast.makeText(context, if (success) "Frame saved to gallery!" else "Failed to save frame", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else {
                                    isExportingFrame = false
                                }
                            },
                            interactionSource = exportInteraction,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                            modifier = Modifier
                                .size(width = 44.dp, height = 36.dp)
                                .graphicsLayer {
                                    scaleX = exportScale
                                    scaleY = exportScale
                                }
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isExportingFrame) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    FontIcon(
                                        unicode = FontIcons.FrameExport,
                                        contentDescription = "Export Frame",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        size = 18.sp,
                                        filled = isExportPressed
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Seek bar
                VideoSlider(
                    exoPlayer = exoPlayer,
                    duration = duration,
                    haptic = haptic,
                    scrubPosition = scrubPosition,
                    currentPositionState = currentPositionState,
                    isPlaying = isPlaying
                )
            }
        }
    }
}

/**
 * Mute/unmute button for video - to be added in bottom bar center
 */
@Composable
fun MuteUnmuteButton(
    exoPlayer: ExoPlayer,
    modifier: Modifier = Modifier
) {
    var isMuted by remember { mutableStateOf(exoPlayer.volume == 0f) }
    
    IconButton(
        onClick = {
            if (isMuted) {
                exoPlayer.volume = 1f
                isMuted = false
            } else {
                exoPlayer.volume = 0f
                isMuted = true
            }
        },
        modifier = modifier
    ) {
        FontIcon(
            unicode = if (isMuted) FontIcons.VolumeOff else FontIcons.VolumeUp,
            contentDescription = if (isMuted) "Unmute" else "Mute",
            tint = Color.White
        )
    }
}

/**
 * Format milliseconds to MM:SS or HH:MM:SS
 */
private fun formatVideoTime(timeMs: Long): String {
    val totalSeconds = (timeMs / 1000).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    
    return if (hours > 0) {
        String.format(java.util.Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
       return String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoSlider(
    exoPlayer: androidx.media3.exoplayer.ExoPlayer,
    duration: Long,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    scrubPosition: androidx.compose.runtime.MutableState<Long?>,
    currentPositionState: androidx.compose.runtime.MutableLongState,
    isPlaying: Boolean
) {
    var isScrubbing by remember { mutableStateOf(false) }
    var lastSeekTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isPlaying, isScrubbing) {
        while (isPlaying && !isScrubbing) {
            currentPositionState.longValue = exoPlayer.currentPosition
            kotlinx.coroutines.delay(16L) // ~60fps updates for smoother slider
        }
    }

    LaunchedEffect(isScrubbing, isPlaying) {
        while (isActive && !isScrubbing && !isPlaying) {
            currentPositionState.longValue = exoPlayer.currentPosition
            kotlinx.coroutines.delay(100L)
        }
    }

    Slider(
        value = currentPositionState.longValue.toFloat(),
        onValueChange = { newValue ->
            if (!isScrubbing) {
                exoPlayer.setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
            }
            isScrubbing = true
            currentPositionState.longValue = newValue.toLong()
            scrubPosition.value = currentPositionState.longValue
            
            // Throttle seeks during scrub to avoid decoder lag
            val now = System.currentTimeMillis()
            if (now - lastSeekTime > 40) {
                lastSeekTime = now
                exoPlayer.seekTo(currentPositionState.longValue)
            }
        },
        onValueChangeFinished = {
            isScrubbing = false
            scrubPosition.value = null
            exoPlayer.setSeekParameters(androidx.media3.exoplayer.SeekParameters.EXACT)
            exoPlayer.seekTo(currentPositionState.longValue)
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
        },
        valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.onSurface,
            activeTrackColor = MaterialTheme.colorScheme.onSurface,
            inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        ),
        modifier = Modifier.fillMaxWidth().scale(scaleX = 1f, scaleY = 0.7f)
    )
}

@Composable
private fun VideoTime(
    duration: Long,
    scrubPosition: androidx.compose.runtime.MutableState<Long?>,
    currentPositionState: androidx.compose.runtime.State<Long>
) {
    val displayPosition = scrubPosition.value ?: currentPositionState.value

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formatVideoTime(displayPosition),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "/",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = formatVideoTime(duration),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
        )
    }
}

@Composable
fun DetailSection(label: String, value: String) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Extracts a frame from a video at a specific time and saves it to the MediaStore.
 */
private suspend fun extractAndSaveFrame(context: Context, uri: android.net.Uri, positionMs: Long, mediaDateAddedMs: Long, onResult: (Boolean) -> Unit) {
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            // Use OPTION_CLOSEST for exact frame extraction instead of closest keyframe
            val bitmap = retriever.getFrameAtTime(positionMs * 1000, android.media.MediaMetadataRetriever.OPTION_CLOSEST)
            if (bitmap != null) {
                // Use exact timestamp from source video
                val exactFrameTimeMs = mediaDateAddedMs
                val exactFrameTimeSeconds = exactFrameTimeMs / 1000
                
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "Frame_${System.currentTimeMillis()}.jpg")
                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(android.provider.MediaStore.Images.Media.DATE_ADDED, exactFrameTimeSeconds)
                    put(android.provider.MediaStore.Images.Media.DATE_MODIFIED, exactFrameTimeSeconds)
                    put(android.provider.MediaStore.Images.Media.DATE_TAKEN, exactFrameTimeMs)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/PixelGallery")
                        put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }

                val imageUri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (imageUri != null) {
                    context.contentResolver.openOutputStream(imageUri)?.use { out ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, out)
                    }

                    // Write EXIF Date so MediaStore picks it up on Android Q+
                    try {
                        context.contentResolver.openFileDescriptor(imageUri, "rw")?.use { pfd ->
                            val exif = androidx.exifinterface.media.ExifInterface(pfd.fileDescriptor)
                            val date = java.util.Date(exactFrameTimeMs)
                            
                            val sdf = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US)
                            val dateString = sdf.format(date)
                            exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL, dateString)
                            exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME, dateString)
                            exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_DIGITIZED, dateString)
                            
                            val offsetSdf = java.text.SimpleDateFormat("XXX", java.util.Locale.US)
                            val offsetString = offsetSdf.format(date)
                            exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_OFFSET_TIME, offsetString)
                            exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_OFFSET_TIME_ORIGINAL, offsetString)
                            exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_OFFSET_TIME_DIGITIZED, offsetString)
                            
                            exif.saveAttributes()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        values.clear()
                        values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                        values.put(android.provider.MediaStore.Images.Media.DATE_TAKEN, exactFrameTimeMs)
                        values.put(android.provider.MediaStore.Images.Media.DATE_ADDED, exactFrameTimeSeconds)
                        values.put(android.provider.MediaStore.Images.Media.DATE_MODIFIED, exactFrameTimeSeconds)
                        context.contentResolver.update(imageUri, values, null, null)
                    }
                    
                    // Request media scan
                    android.media.MediaScannerConnection.scanFile(context, arrayOf(imageUri.toString()), arrayOf("image/jpeg"), null)

                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onResult(true)
                    }
                } else {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onResult(false)
                    }
                }
            } else {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(false)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onResult(false)
            }
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {}
        }
    }
}
