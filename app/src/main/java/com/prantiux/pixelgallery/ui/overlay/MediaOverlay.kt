@file:OptIn(ExperimentalAnimationApi::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)

package com.prantiux.pixelgallery.ui.overlay

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.ripple
import androidx.compose.material3.toShape
import com.prantiux.pixelgallery.ui.shapes.SmoothCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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
    if (!overlayState.isVisible) return

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
    
    // Zoom state
    var scale by remember { mutableFloatStateOf(OverlayConstants.MIN_ZOOM_SCALE) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    
    // Video player state
    var exoPlayer: ExoPlayer? by remember { mutableStateOf(null) }
    var isPlaying by remember { mutableStateOf(false) }
    
    // Keep screen on for video playback
    DisposableEffect(keepScreenOn, isPlaying, currentIndex, mediaItems) {
        val window = (view.context as? Activity)?.window
        val currentItem = mediaItems.getOrNull(currentIndex)
        if (window != null && keepScreenOn && currentItem?.isVideo == true && isPlaying) {
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
    val horizontalOffset = remember { Animatable(0f) }
    val verticalOffset = remember { Animatable(0f) }
    val detailsPanelProgress = remember { Animatable(0f) }
    var isNavigating by remember { mutableStateOf(false) }

    // UI visibility
    var showControls by remember { mutableStateOf(false) }
    var showBars by remember { mutableStateOf(true) }
    var isVideoFullscreen by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val isDetailsOpen = detailsPanelProgress.value > 0.5f

    val windowInsetsController = remember(view) {
        WindowInsetsControllerCompat(
            (view.context as Activity).window,
            view
        )
    }

    LaunchedEffect(showBars, isDetailsOpen) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        if (showBars || isDetailsOpen) {
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
    
    // ExoPlayer lifecycle management
    DisposableEffect(context) {
        val player = ExoPlayer.Builder(context).build().apply {
            // Set repeat mode based on loop setting
            repeatMode = if (loopVideos) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            // Set volume based on mute setting
            volume = if (muteByDefault) 0f else 1f
        }
        exoPlayer = player
        onDispose {
            player.release()
            exoPlayer = null
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
    LaunchedEffect(currentIndex, mediaItems, autoPlayVideos, resumePlayback) {
        exoPlayer?.let { player ->
            // Save position of previous video before switching
            val previousIndex = currentIndex - 1
            if (previousIndex >= 0 && previousIndex < mediaItems.size) {
                val previousItem = mediaItems.getOrNull(previousIndex)
                if (previousItem?.isVideo == true && resumePlayback) {
                    val position = player.currentPosition
                    val duration = player.duration
                    // Only save if video has meaningful progress (>2 sec) and isn't near the end
                    if (position > 2000 && duration > 0 && position < duration - 3000) {
                        videoPositionDataStore.savePosition(previousItem.uri.toString(), position)
                    } else if (position >= duration - 3000 && duration > 0) {
                        // Video completed, clear saved position
                        videoPositionDataStore.clearPosition(previousItem.uri.toString())
                    }
                }
            }
            
            val currentItem = mediaItems.getOrNull(currentIndex)
            if (currentItem?.isVideo == true) {
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
    LaunchedEffect(currentIndex, mediaItems, resumePlayback) {
        while (isActive && resumePlayback) {
            val currentItem = mediaItems.getOrNull(currentIndex)
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

    // When sheet is closed, back gesture closes overlay
    BackHandler(enabled = overlayState.isVisible && !isSheetOpen) {
        onDismiss()
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
            currentIndex = overlayState.selectedIndex
            scale = OverlayConstants.MIN_ZOOM_SCALE
            offsetX = 0f
            offsetY = 0f
            gestureMode = GestureMode.NONE
            horizontalOffset.snapTo(0f)
            verticalOffset.snapTo(0f)
            detailsPanelProgress.snapTo(0f)
            showControls = true
            requestImageForIndex(currentIndex)
            preloadNeighbors(currentIndex)
        }
    }

    // Current media item (use currentIndex for rendering to prevent blink)
    val currentItem = mediaItems.getOrNull(currentIndex)
    
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
                viewModel.enterSelectionMode(item)
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
            view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
            
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
    val handleDoubleTap: () -> Unit = {
        if (currentItem?.isVideo != true && doubleTapToZoom) {
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

    // Background scrim alpha - based on gesture state only
    val backgroundAlpha = when {
        gestureMode == GestureMode.VERTICAL_DOWN -> {
            // During swipe down, fade out based on drag distance
            1f - closeProgress
        }
        else -> 1f
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


    val controlsVisible = showControls && 
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(scale) {
                // Centralized gesture coordinator - DO NOT consume until direction locked
                awaitEachGesture {
                    
                    val down = awaitFirstDown(requireUnconsumed = false)

                    // Ignore global overlay gestures when drag starts inside protected UI zones.
                    val protectBars = latestControlsVisible && !latestIsAnyVideoFullscreen
                    val protectVideoControls = latestIsCurrentItemVideo && latestControlsVisible && !latestIsAnyVideoFullscreen
                    val inProtectedZone =
                        (protectBars &&
                            (latestTopBarBounds?.contains(down.position) == true ||
                                latestBottomBarBounds?.contains(down.position) == true)) ||
                            (protectVideoControls && latestVideoControlsBounds?.contains(down.position) == true)

                    if (inProtectedZone) {
                        waitForUpOrCancellation()
                        return@awaitEachGesture
                    }

                    var currentGestureMode = GestureMode.NONE
                    var accumulatedDx = 0f
                    var accumulatedDy = 0f
                    var lastMoveTime = System.currentTimeMillis()
                    var velocityX = 0f
                    var velocityY = 0f
                    var upPosition = down.position
                    var isMultiTouch = false

                    while (true) {
                        val event = awaitPointerEvent()
                        
                        // Check if finger released
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            upPosition = change.position
                            break
                        }

                        // Detect multi-touch for zoom
                        if (event.changes.size > 1) {
                            currentGestureMode = GestureMode.ZOOM
                            isMultiTouch = true
                            // Let zoom handler consume
                            break
                        }

                        val dx = change.positionChangeIgnoreConsumed().x
                        val dy = change.positionChangeIgnoreConsumed().y
                        
                        accumulatedDx += dx
                        accumulatedDy += dy

                        // When zoomed, do not run swipe direction lock; only allow tap resolution on release.
                        if (scale != OverlayConstants.MIN_ZOOM_SCALE) {
                            continue
                        }

                        // 🔒 Direction lock - NO CONSUMPTION YET
                        if (currentGestureMode == GestureMode.NONE) {
                            val threshold = OverlayConstants.TAP_SLOP_PX
                            if (abs(accumulatedDx) > threshold || abs(accumulatedDy) > threshold) {
                                currentGestureMode = when {
                                    abs(accumulatedDx) > abs(accumulatedDy) && detailsPanelProgress.value <= 0f -> GestureMode.HORIZONTAL_SWIPE
                                    accumulatedDy > 0 && (swipeDownToClose || detailsPanelProgress.value > 0f) -> GestureMode.VERTICAL_DOWN
                                    accumulatedDy < 0 && swipeUpToDetails && detailsPanelProgress.value <= 0f -> GestureMode.VERTICAL_UP
                                    else -> GestureMode.NONE
                                }
                                gestureMode = currentGestureMode
                                if (currentGestureMode == GestureMode.HORIZONTAL_SWIPE && horizontalOffset.isRunning) {
                                    scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                                        horizontalOffset.stop()
                                    }
                                }
                            }
                        }

                        // 3️⃣ Accumulate drag into PERSISTENT state
                        // ✅ DO NOT CONSUME during MOVE - let deltas continue flowing
                        when (currentGestureMode) {
                            GestureMode.HORIZONTAL_SWIPE -> {
                                if (isNavigating) {
                                    continue
                                }
                                // REMOVED: change.consume() - consumption kills dx/dy deltas
                                // Apply resistance at edges
                                var adjustedDx = dx
                                if (currentIndex == 0 && horizontalOffset.value + dx > 0f) {
                                    // At first image, resist right swipe
                                    adjustedDx *= 0.15f
                                } else if (currentIndex == mediaItems.size - 1 && horizontalOffset.value + dx < 0f) {
                                    // At last image, resist left swipe
                                    adjustedDx *= 0.15f
                                }

                                val currentOffset = horizontalOffset.value
                                val progress = (abs(currentOffset) / pageWidthPx).coerceIn(0f, 1f)
                                val friction = 1f - (progress * progress)
                                adjustedDx *= friction
                                
                                // Calculate velocity for fast swipe detection
                                val currentTime = System.currentTimeMillis()
                                val deltaTime = (currentTime - lastMoveTime).coerceAtLeast(1)
                                velocityX = (adjustedDx / deltaTime) * 1000f  // pixels per second
                                lastMoveTime = currentTime
                                
                                horizontalMoveDeltas.trySend(adjustedDx)
                            }
                            
                            GestureMode.VERTICAL_UP -> {
                                // Only process if swipe up to details is enabled
                                if (swipeUpToDetails) {
                                    verticalUpMoveDeltas.trySend(dy)
                                }
                            }
                            
                            GestureMode.VERTICAL_DOWN -> {
                                // Only track if enabled (either for closing overlay or closing details panel)
                                if (swipeDownToClose || detailsPanelProgress.value > 0f) {
                                    verticalDownMoveDeltas.trySend(dy)
                                    
                                    // Calculate vertical velocity for intent detection
                                    val currentTime = System.currentTimeMillis()
                                    val deltaTime = (currentTime - lastMoveTime).coerceAtLeast(1)
                                    velocityY = (dy / deltaTime) * 1000f  // pixels per second
                                    lastMoveTime = currentTime
                                }
                            }
                            
                            else -> {
                                // Don't consume if no mode locked yet
                            }
                        }
                    }

                    val isTapGesture = !isMultiTouch && isTap(down.position, upPosition)
                    if (isTapGesture) {
                        val touchPosition = down.position
                        val inBlockedZone =
                            (latestTopBarBounds?.contains(touchPosition) == true) ||
                                (latestBottomBarBounds?.contains(touchPosition) == true) ||
                                (latestVideoControlsBounds?.contains(touchPosition) == true)

                        if (!inBlockedZone) {
                            val now = System.currentTimeMillis()
                            val isDoubleTap =
                                (now - lastTapTimeMs) <= 300L &&
                                    (touchPosition - lastTapPosition).getDistance() < 48f

                            if (isDoubleTap) {
                                handleDoubleTap()
                                lastTapTimeMs = 0L
                            } else {
                                if (!isDetailsOpen) {
                                    showBars = !showBars
                                    showControls = showBars
                                }
                                lastTapTimeMs = now
                                lastTapPosition = touchPosition
                            }
                        }

                        gestureMode = GestureMode.NONE
                        return@awaitEachGesture
                    }

                    // 5️⃣ Release logic - decide complete or snap back
                    when (currentGestureMode) {
                        GestureMode.HORIZONTAL_SWIPE -> {
                            // 4️⃣ Threshold uses distance OR velocity
                            val velocityThreshold = 1500f  // pixels per second

                            scope.launch {
                                if (isNavigating) {
                                    return@launch
                                }

                                // Fast swipe or sufficient distance
                                val shouldNavigate = abs(horizontalOffset.value) > navigationThreshold || abs(velocityX) > velocityThreshold
                                if (shouldNavigate) {
                                    val currentOffset = horizontalOffset.value
                                    // Determine next index
                                    val newIndex = if (currentOffset < 0 && currentIndex < mediaItems.size - 1) {
                                        currentIndex + 1
                                    } else if (currentOffset > 0 && currentIndex > 0) {
                                        currentIndex - 1
                                    } else {
                                        currentIndex  // Stay at current if at boundary
                                    }

                                    if (newIndex != currentIndex) {
                                        if (newIndex > currentIndex) {
                                            navigateNextPage(velocityX)
                                        } else {
                                            navigatePreviousPage(velocityX)
                                        }
                                    } else {
                                        // Cancel swipe at boundary - use spring
                                        horizontalOffset.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = 0.95f,
                                                stiffness = 900f
                                            )
                                        )
                                    }
                                } else {
                                    // Cancel swipe (insufficient distance/velocity) - use spring
                                    horizontalOffset.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = 0.95f,
                                            stiffness = 900f
                                        )
                                    )
                                }
                            }
                        }

                        GestureMode.VERTICAL_UP -> {
                            // Only process if swipe up to details is enabled
                            if (swipeUpToDetails) {
                                
                                scope.launch {
                                    val existingTarget = if (detailsPanelProgress.value >= 0.3f) 1f else 0f
                                    val target = if (velocityY < -800f) 1f else if (velocityY > 800f) 0f else existingTarget

                                    verticalOffset.snapTo(0f)
                                    detailsPanelProgress.animateTo(
                                        targetValue = target,
                                        animationSpec = spring(
                                            dampingRatio = if (target >= 0.5f) 0.8f else 1.0f,
                                            stiffness = if (target >= 0.5f) 380f else 300f
                                        )
                                    )
                                    if (target <= 0.5f) {
                                        showControls = true
                                    }
                                }
                            } else {
                                // If disabled, just snap back
                                scope.launch {
                                    verticalOffset.snapTo(0f)
                                }
                            }
                        }
                        
                        GestureMode.VERTICAL_DOWN -> {
                            // If details panel is active, close it first
                            if (detailsPanelProgress.value > 0f) {
                                
                                scope.launch {
                                    // Close if dragged down significantly OR fast downward swipe
                                    val shouldCloseDetails = detailsPanelProgress.value < 0.7f || velocityY > 1200f
                                    detailsPanelProgress.animateTo(
                                        targetValue = if (shouldCloseDetails) 0f else 1f,
                                        animationSpec = spring(
                                            dampingRatio = if (shouldCloseDetails) 1.0f else 0.85f,
                                            stiffness = if (shouldCloseDetails) 300f else 400f
                                        )
                                    )
                                    if (shouldCloseDetails) {
                                        showControls = true
                                    }
                                    verticalOffset.snapTo(0f)
                                }
                            } else if (swipeDownToClose) {
                                // Details panel is closed, proceed with image close only if enabled
                                val threshold = OverlayConstants.VERTICAL_CLOSE_THRESHOLD_PX
                                
                                
                                scope.launch {
                                    if (abs(verticalOffset.value) > threshold) {
                                        // Complete close gesture
                                        showControls = false
                                        onDismiss()
                                    } else {
                                        // Snap back
                                        verticalOffset.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        )
                                    }
                                }
                            } else {
                                // If swipe down to close is disabled, just snap back
                                scope.launch {
                                    verticalOffset.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            }
                        }
                        
                        else -> {
                        }
                    }

                    if (isMultiTouch) {
                        gestureMode = GestureMode.NONE
                    }

                    // Reset gesture mode
                    gestureMode = GestureMode.NONE
                }
            }
    ) {
        // Background scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrimColor.copy(alpha = backgroundAlpha))
        )

        // Media image
        currentItem?.let { item ->
            // Render previous, current, and next images based on currentIndex
            val prevItem = mediaItems.getOrNull(currentIndex - 1)?.takeIf { !it.isVideo }
            val nextItem = mediaItems.getOrNull(currentIndex + 1)?.takeIf { !it.isVideo }

            // Render previous, current, and next images for smooth horizontal swipe
            Box(modifier = Modifier.fillMaxSize()) {
                // Previous image - use stable key to prevent rebinding
                prevItem?.let { prev ->
                    key(prev.id) {  // Stable key prevents recomposition
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(prev.uri)
                                .size(targetDecodeSize)
                                .memoryCacheKey(prev.uri.toString())
                                .diskCacheKey(prev.uri.toString())
                                .crossfade(false)  // No crossfade to prevent blink
                                .build(),
                            contentDescription = prev.displayName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(end = 4.dp)  // Small gap between images
                                .graphicsLayer {
                                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                                    this.translationX = horizontalOffset.value - screenWidth
                                },
                            loading = {
                                // Show black background during load (matches overlay)
                                Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                            }
                        )
                    }
                }
                
                // Current image - use stable key to prevent rebinding on index change
                if (!item.isVideo) {
                    key(item.id) {
                        var imageIntrinsicSize by remember {
                            mutableStateOf(androidx.compose.ui.geometry.Size.Zero)
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(item.uri)
                                    .size(targetDecodeSize)
                                    .memoryCacheKey(item.uri.toString())
                                    .diskCacheKey(item.uri.toString())
                                    .crossfade(false)
                                    .build(),
                                contentDescription = item.displayName,
                                contentScale = ContentScale.Fit,
                                onSuccess = { state ->
                                    val painter = state.painter
                                    imageIntrinsicSize = painter.intrinsicSize
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

                                        if (detailsProgress > 0.01f) {
                                            val imageOffset = screenHeight * 0.25f * detailsProgress
                                            this.translationY = -imageOffset
                                            this.scaleX = detailsScale
                                            this.scaleY = detailsScale
                                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                                        } else {
                                            this.translationX = horizontalOffset.value
                                            if (gestureMode == GestureMode.HORIZONTAL_SWIPE) {
                                                val swipeProgress = (abs(horizontalOffset.value) / screenWidth).coerceIn(0f, 1f)
                                                val scaleAmount = swipeProgress * 0.05f
                                                this.scaleX = 1f - scaleAmount
                                                this.scaleY = 1f - scaleAmount
                                            }
                                            this.translationY = verticalOffset.value
                                            if (gestureMode == GestureMode.VERTICAL_DOWN) {
                                                val scaleAmount = 0.15f * closeProgress
                                                this.scaleX = 1f - scaleAmount
                                                this.scaleY = 1f - scaleAmount
                                            }
                                            if (scale > OverlayConstants.MIN_ZOOM_SCALE) {
                                                this.scaleX *= scale
                                                this.scaleY *= scale
                                                this.translationX += offsetX
                                                this.translationY += offsetY
                                            }
                                        }
                                    }
                                    .pointerInput(Unit) {
                                        if (!item.isVideo) {
                                            detectTransformGestures { _, pan, zoom, _ ->
                                                val newScale = calculateScale(scale, zoom)
                                                val rawImageWidth = if (imageIntrinsicSize.width > 0f) imageIntrinsicSize.width else screenWidth
                                                val rawImageHeight = if (imageIntrinsicSize.height > 0f) imageIntrinsicSize.height else screenHeight
                                                val fitScale = kotlin.math.min(screenWidth / rawImageWidth, screenHeight / rawImageHeight)
                                                val fittedImageWidth = rawImageWidth * fitScale
                                                val fittedImageHeight = rawImageHeight * fitScale
                                                if (newScale > OverlayConstants.MIN_ZOOM_SCALE) {
                                                    gestureMode = GestureMode.ZOOM
                                                    val adjustedPanX = pan.x * newScale
                                                    val adjustedPanY = pan.y * newScale
                                                    val proposedOffsetX = offsetX + adjustedPanX
                                                    val proposedOffsetY = offsetY + adjustedPanY
                                                    val (clampedOffsetX, clampedOffsetY) = clampOffset(
                                                        offsetX = proposedOffsetX,
                                                        offsetY = proposedOffsetY,
                                                        scale = newScale,
                                                        containerWidth = screenWidth,
                                                        containerHeight = screenHeight,
                                                        imageWidth = fittedImageWidth,
                                                        imageHeight = fittedImageHeight
                                                    )
                                                    scale = newScale
                                                    offsetX = clampedOffsetX
                                                    offsetY = clampedOffsetY
                                                } else {
                                                    scale = OverlayConstants.MIN_ZOOM_SCALE
                                                    offsetX = 0f
                                                    offsetY = 0f
                                                    gestureMode = GestureMode.NONE
                                                }
                                            }
                                        }
                                    }
                            )
                        }
                    }
                }
                
                // Next image - use stable key to prevent rebinding
                nextItem?.let { next ->
                    key(next.id) {  // Stable key prevents recomposition
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(next.uri)
                                .size(targetDecodeSize)
                                .memoryCacheKey(next.uri.toString())
                                .diskCacheKey(next.uri.toString())
                                .crossfade(false)  // No crossfade to prevent blink
                                .build(),
                            contentDescription = next.displayName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 4.dp)  // Small gap between images
                                .graphicsLayer {
                                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                                    this.translationX = horizontalOffset.value + screenWidth
                                },
                            loading = {
                                // Show black background during load (matches overlay)
                                Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                            }
                        )
                    }
                }
            }
        }
        
        // Video player overlay (when video is playing)
        exoPlayer?.let { player ->
            if (currentItem?.isVideo == true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                            val detailsProgress = detailsPanelProgress.value.coerceIn(0f, 1f)
                            val imageOffset = screenHeight * 0.25f * detailsProgress
                            this.translationY = (verticalOffset.value * (1f - detailsProgress)) - imageOffset
                            this.translationX = horizontalOffset.value * (1f - detailsProgress)
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                            if (gestureMode == GestureMode.VERTICAL_DOWN && detailsProgress == 0f) {
                                val scaleAmount = 0.15f * closeProgress
                                this.scaleX = 1f - scaleAmount
                                this.scaleY = 1f - scaleAmount
                            }
                        }
                ) {
                // Video player surface only (no default controls)
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = false // Disable default controls
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                
                // Custom video controls overlay
                AnimatedVisibility(
                    visible = controlsVisible && !isVideoFullscreen,
                    enter = fadeIn(animationSpec = tween(300)),
                    exit = fadeOut(animationSpec = tween(300))
                ) {
                    CustomVideoControls(
                        exoPlayer = player,
                        isPlaying = isPlaying,
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
                        }
                    )
                }
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
            visible = showBars && !isAnyVideoFullscreen && !isDetailsOpen,
            enter = fadeIn(
                animationSpec = tween(
                    durationMillis = 150,
                    delayMillis = 0,
                    easing = FastOutSlowInEasing
                )
            ) + slideInVertically(
                animationSpec = tween(
                    durationMillis = 180,
                    delayMillis = 0,
                    easing = FastOutSlowInEasing
                )
            ) { -it },
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
                        .background(
                            brush = if (isZoomed) {
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to barColor.copy(alpha = 0.3f),
                                        0.75f to barColor.copy(alpha = 0.3f),
                                        0.78f to barColor.copy(alpha = 0.28f),
                                        0.86f to barColor.copy(alpha = 0.16f),
                                        0.93f to barColor.copy(alpha = 0.05f),
                                        0.98f to barColor.copy(alpha = 0.01f),
                                        1.0f to Color.Transparent
                                    )
                                )
                            } else {
                                Brush.verticalGradient(
                                    colors = listOf(barColor, barColor)
                                )
                            }
                        )
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
                                    unicode = if (isFavorited) FontIcons.Star else FontIcons.StarOutline,
                                    contentDescription = if (isFavorited) "Remove from favorites" else "Add to favorites",
                                    tint = if (isFavorited) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

            }
        }

        // Bottom controls (animated)
        AnimatedVisibility(
            visible = showBars && !isDetailsOpen,
            enter = fadeIn(
                animationSpec = tween(
                    durationMillis = 150,
                    delayMillis = 40,
                    easing = FastOutSlowInEasing
                )
            ) + slideInVertically(
                animationSpec = tween(
                    durationMillis = 180,
                    delayMillis = 40,
                    easing = FastOutSlowInEasing
                )
            ) { it },
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
                        .background(
                            brush = if (isZoomed) {
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.Transparent,
                                        0.02f to barColor.copy(alpha = 0.01f),
                                        0.07f to barColor.copy(alpha = 0.05f),
                                        0.14f to barColor.copy(alpha = 0.16f),
                                        0.22f to barColor.copy(alpha = 0.28f),
                                        0.25f to barColor.copy(alpha = 0.3f),
                                        1.0f to barColor.copy(alpha = 0.3f)
                                    )
                                )
                            } else {
                                Brush.verticalGradient(
                                    colors = listOf(barColor, barColor)
                                )
                            }
                        )
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
                                            viewModel.showCopyToAlbumDialog(listOfNotNull(currentItem))
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
                                            viewModel.showMoveToAlbumDialog(listOfNotNull(currentItem))
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
        
        if (detailsPanelProgress.value > 0f) {
            val progress = detailsPanelProgress.value.coerceIn(0f, 1f)
            val leadingAlpha = (progress * 1.5f).coerceIn(0f, 1f)

            // Scrim — matches ModalBottomSheet scrim behavior
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f * progress))
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
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
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            }
                        }
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
                        view?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
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
                    view?.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    showDeleteDialog = false 
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}
}

/**
 * Custom video controls overlay with Material 3 design
 */
@Composable
private fun CustomVideoControls(
    exoPlayer: ExoPlayer,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onFullscreenChange: (Boolean) -> Unit = {},
    onControlsBoundsChanged: (Rect) -> Unit = {}
) {
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var videoEnded by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(exoPlayer.volume == 0f) }
    var isFullscreen by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val activity = context as? Activity
    
    // Notify parent about fullscreen changes
    LaunchedEffect(isFullscreen) {
        onFullscreenChange(isFullscreen)
    }
    
    // Calculate bottom bar height: IconButton (48dp) + vertical padding (12dp * 2) + navigation bars
    val density = LocalDensity.current
    val navigationBarsPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBarHeight = 48.dp + 24.dp + navigationBarsPadding // Total height of bottom action bar
    
    // Update position continuously
    LaunchedEffect(Unit) {
        while (isActive) {
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            videoEnded = exoPlayer.playbackState == Player.STATE_ENDED
            delay(100)
        }
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
                    onControlsBoundsChanged(coordinates.boundsInRoot())
                }
                .padding(bottom = bottomBarHeight) // Attached to bottom bar, no spacing
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent, // Top: transparent (merges with video)
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f) // Bottom: theme-based color with opacity
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Seek bar
                Slider(
                    value = if (duration > 0) currentPosition.toFloat() else 0f,
                    onValueChange = { newPosition ->
                        exoPlayer.seekTo(newPosition.toLong())
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
                
                // Play/Pause button and Time display - button left, time center
                Box(modifier = Modifier.fillMaxWidth()) {
                    // Play/Pause/Restart button (left-aligned)
                    val playShape = if (playButtonShape > 0.5f) CircleShape else androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    
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
                        shape = playShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        modifier = Modifier
                            .size(36.dp)
                            .align(Alignment.CenterStart)
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
                                tint = Color.White,
                                size = 20.sp,
                                filled = true
                            )
                        }
                    }
                    
                    // Time display (center-aligned)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Text(
                            text = formatVideoTime(currentPosition),
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
                            text = formatVideoTime(duration),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                    
                    // Right-aligned controls: Mute/Unmute and Fullscreen
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        // Mute/Unmute button with animated rounded square background
                        val muteShape = if (muteButtonShape > 0.5f) CircleShape else androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        
                        Surface(
                            onClick = {
                                isMuted = !isMuted
                                exoPlayer.volume = if (isMuted) 0f else 1f
                            },
                            shape = muteShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                FontIcon(
                                    unicode = if (isMuted) FontIcons.VolumeOff else FontIcons.VolumeUp,
                                    contentDescription = if (isMuted) "Unmute" else "Mute",
                                    tint = Color.White,
                                    size = 20.sp
                                )
                            }
                        }
                        
                        // Fullscreen button
                        Surface(
                            onClick = {
                                activity?.let { act ->
                                    val window = act.window
                                    val insetsController = WindowInsetsControllerCompat(window, window.decorView)
                                    
                                    isFullscreen = !isFullscreen
                                    if (isFullscreen) {
                                        // Enter fullscreen landscape mode
                                        act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                        
                                        // Hide system bars with modern API
                                        insetsController.hide(WindowInsetsCompat.Type.systemBars())
                                        insetsController.systemBarsBehavior = 
                                            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                                    } else {
                                        // Exit fullscreen - restore portrait
                                        act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                        
                                        // Show system bars
                                        insetsController.show(WindowInsetsCompat.Type.systemBars())
                                    }
                                }
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                FontIcon(
                                    unicode = if (isFullscreen) FontIcons.FullscreenExit else FontIcons.Fullscreen,
                                    contentDescription = if (isFullscreen) "Exit Fullscreen" else "Fullscreen",
                                    tint = Color.White,
                                    size = 20.sp
                                )
                            }
                        }
                    }
                }
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
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
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
