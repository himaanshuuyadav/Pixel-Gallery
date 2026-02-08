@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)

package com.prantiux.pixelgallery.ui.overlay

import android.app.Activity
import android.app.WallpaperManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.WindowInsetsController
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.*
import com.prantiux.pixelgallery.ui.shapes.SmoothCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
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
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Size
import com.prantiux.pixelgallery.model.MediaItem
import com.prantiux.pixelgallery.viewmodel.MediaViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons
import com.prantiux.pixelgallery.ui.components.DetailsBottomSheetContent
import com.prantiux.pixelgallery.ui.animation.SharedElementBounds
import com.prantiux.pixelgallery.ui.animation.rememberSharedElementAnimation
import com.prantiux.pixelgallery.ui.animation.calculateMediaTransform
import androidx.compose.ui.unit.IntSize


// Gesture direction locking
private enum class GestureMode {
    NONE,
    HORIZONTAL_SWIPE,
    VERTICAL_UP,
    VERTICAL_DOWN,
    ZOOM
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
    val showControlsOnTap by settingsDataStore.showControlsOnTapFlow.collectAsState(initial = true)
    
    // Set solid black colors for system bars during media overlay using modern API
    SideEffect {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            // Use modern WindowInsetsController API for appearance
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.setSystemBarsAppearance(
                    0,  // Dark content (light icons/text)
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or 
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
            }
            // Set colors - suppress deprecation warning as these APIs are still the standard way
            // Note: Deprecated in Java but still needed for all API levels
            @Suppress("DEPRECATION")
            window.statusBarColor = android.graphics.Color.BLACK
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.BLACK
        }
    }
    
    // Check if this is trash mode
    val isTrashMode = overlayState.mediaType == "trash"

    // Current index state
    var currentIndex by remember { mutableIntStateOf(overlayState.selectedIndex) }
    
    // Track the index being displayed during transition (prevents blink)
    var displayIndex by remember { mutableIntStateOf(overlayState.selectedIndex) }
    
    // Zoom state
    var scale by remember { mutableFloatStateOf(1f) }
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
    
    // Shared element animation state - MUST be here before LaunchedEffects use it
    val sharedElementAnimation = rememberSharedElementAnimation()
    
    // Convert ViewModel bounds to SharedElementBounds - MUST be here before LaunchedEffects use it
    val thumbnailBounds = overlayState.thumbnailBounds?.let {
        SharedElementBounds(
            left = it.startLeft,
            top = it.startTop,
            width = it.startWidth,
            height = it.startHeight
        )
    }
    
    // UI visibility
    var showControls by remember { mutableStateOf(false) }
    var showDetailsPanel by remember { mutableStateOf(false) }
    
    // Favorite states - track which items are favorited
    val favoriteStates = remember { mutableStateMapOf<Long, Boolean>().apply {
        mediaItems.forEach { item -> put(item.id, item.isFavorite) }
    } }
    
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
    LaunchedEffect(currentIndex, mediaItems, autoPlayVideos, resumePlayback, sharedElementAnimation.isAnimating) {
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
                
                // IMPORTANT: Wait for animation to complete before starting playback
                // This prevents video from playing during the opening animation
                if (sharedElementAnimation.isAnimating) {
                    // Pause during animation
                    player.playWhenReady = false
                    isPlaying = false
                } else {
                    // Use autoPlayVideos setting after animation completes
                    player.playWhenReady = autoPlayVideos
                    isPlaying = autoPlayVideos
                }
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
    val closeProgress = if (gestureMode == GestureMode.VERTICAL_DOWN) {
        (abs(verticalOffset.value) / 150f).coerceIn(0f, 1f)
    } else {
        0f
    }



    // Back handler - intercept system back gesture
    BackHandler(enabled = overlayState.isVisible) {
        onDismiss()
    }

    // Initialize state and trigger opening animation
    LaunchedEffect(overlayState.isVisible) {
        if (overlayState.isVisible) {
            currentIndex = overlayState.selectedIndex
            displayIndex = overlayState.selectedIndex
            scale = 1f
            offsetX = 0f
            offsetY = 0f
            gestureMode = GestureMode.NONE
            horizontalOffset.snapTo(0f)
            verticalOffset.snapTo(0f)
            detailsPanelProgress.snapTo(0f)
            showDetailsPanel = false
            
            // Trigger shared element animation
            if (thumbnailBounds != null) {
                // Animate from thumbnail to fullscreen (12dp corner radius for thumbnails)
                sharedElementAnimation.animateOpen(thumbnailCornerRadius = 12f * density.density)
                // Show controls after animation completes
                showControls = true
            } else {
                // No thumbnail bounds - snap directly to fullscreen
                sharedElementAnimation.snapToFullscreen()
                showControls = true
            }
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
                        // After successful deletion, slide to next item
                        val nextIndex = if (currentIndex < mediaItems.size - 1) {
                            currentIndex // Stay at same index, next item shifts into position
                        } else {
                            (currentIndex - 1).coerceAtLeast(0) // Go to previous if was last
                        }
                        
                        // If no more items, close overlay
                        if (mediaItems.size <= 1) {
                            onDismiss()
                        } else {
                            // Slide to next item without closing
                            displayIndex = nextIndex
                            viewModel.exitSelectionMode()
                        }
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
                val targetScale = if (scale > 1f) {
                    1f  // Zoom out to fit
                } else {
                    when (doubleTapZoomLevel) {
                        "2x" -> 2f
                        "3x" -> 3f
                        "4x" -> 4f
                        else -> 2f
                    }
                }
                val targetOffsetX = if (targetScale == 1f) 0f else offsetX
                val targetOffsetY = if (targetScale == 1f) 0f else offsetY
                
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

    // Calculate screen dimensions for gesture calculations
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }

    // Background scrim alpha - combines animation and gesture states
    val backgroundAlpha = when {
        gestureMode == GestureMode.VERTICAL_DOWN -> {
            // During swipe down, fade out based on drag distance
            sharedElementAnimation.backgroundAlpha.value * (1f - closeProgress)
        }
        else -> {
            // Normal state: use animation alpha
            sharedElementAnimation.backgroundAlpha.value
        }
    }

    // Controls visibility based on gesture
    val controlsVisible = showControls && 
        gestureMode != GestureMode.VERTICAL_UP &&
        detailsPanelProgress.value < 0.01f &&
        closeProgress == 0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(scale) {
                // Centralized gesture coordinator - DO NOT consume until direction locked
                awaitEachGesture {
                    
                    val down = awaitFirstDown(requireUnconsumed = false)
                    
                    // Only process when scale = 1f
                    if (scale != 1f) {
                        return@awaitEachGesture
                    }

                    var currentGestureMode = GestureMode.NONE
                    var accumulatedDx = 0f
                    var accumulatedDy = 0f
                    var lastMoveTime = System.currentTimeMillis()
                    var velocityX = 0f
                    var velocityY = 0f

                    while (true) {
                        val event = awaitPointerEvent()
                        
                        // Check if finger released
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            // 7️⃣ LOG POINTER RELEASE
                            Log.d("GESTURE_DEBUG", "UP - finalOffsetX=${horizontalOffset.value}, finalOffsetY=${verticalOffset.value}")
                            
                            // ✅ Consume on pointer UP
                            change.consume()
                            break
                        }

                        // Detect multi-touch for zoom
                        if (event.changes.size > 1) {
                            currentGestureMode = GestureMode.ZOOM
                            Log.d("GESTURE_DEBUG", "Multi-touch detected - switching to ZOOM mode")
                            // Let zoom handler consume
                            break
                        }

                        val dx = change.positionChangeIgnoreConsumed().x
                        val dy = change.positionChangeIgnoreConsumed().y
                        
                        accumulatedDx += dx
                        accumulatedDy += dy

                        // 3️⃣ LOG RAW MOVEMENT
                        Log.d("GESTURE_DEBUG", "MOVE - dx=$dx dy=$dy accumulatedDx=$accumulatedDx accumulatedDy=$accumulatedDy")

                        // 🔒 Direction lock - NO CONSUMPTION YET
                        if (currentGestureMode == GestureMode.NONE) {
                            val threshold = 10f
                            if (abs(accumulatedDx) > threshold || abs(accumulatedDy) > threshold) {
                                currentGestureMode = when {
                                    abs(accumulatedDx) > abs(accumulatedDy) -> GestureMode.HORIZONTAL_SWIPE
                                    accumulatedDy > 0 && (swipeDownToClose || detailsPanelProgress.value > 0f) -> GestureMode.VERTICAL_DOWN
                                    accumulatedDy < 0 && swipeUpToDetails -> GestureMode.VERTICAL_UP
                                    else -> GestureMode.NONE
                                }
                                gestureMode = currentGestureMode
                                
                                // 4️⃣ LOG DIRECTION LOCK DECISION
                                Log.d("GESTURE_DEBUG", "DIRECTION LOCKED → $currentGestureMode (swipeUp=$swipeUpToDetails, swipeDown=$swipeDownToClose)")
                            }
                        }

                        // 3️⃣ Accumulate drag into PERSISTENT state
                        // ✅ DO NOT CONSUME during MOVE - let deltas continue flowing
                        when (currentGestureMode) {
                            GestureMode.HORIZONTAL_SWIPE -> {
                                // REMOVED: change.consume() - consumption kills dx/dy deltas
                                scope.launch {
                                    // Apply resistance at edges
                                    var adjustedDx = dx
                                    if (currentIndex == 0 && horizontalOffset.value + dx > 0f) {
                                        // At first image, resist right swipe
                                        adjustedDx *= 0.15f
                                    } else if (currentIndex == mediaItems.size - 1 && horizontalOffset.value + dx < 0f) {
                                        // At last image, resist left swipe
                                        adjustedDx *= 0.15f
                                    }
                                    
                                    // Calculate velocity for fast swipe detection
                                    val currentTime = System.currentTimeMillis()
                                    val deltaTime = (currentTime - lastMoveTime).coerceAtLeast(1)
                                    velocityX = (adjustedDx / deltaTime) * 1000f  // pixels per second
                                    lastMoveTime = currentTime
                                    
                                    horizontalOffset.snapTo(horizontalOffset.value + adjustedDx)
                                    // 5️⃣ LOG OFFSET STATE UPDATE
                                    Log.d("GESTURE_DEBUG", "OFFSET UPDATE - horizontal=${horizontalOffset.value}, velocity=$velocityX")
                                }
                            }
                            
                            GestureMode.VERTICAL_UP -> {
                                // Only process if swipe up to details is enabled
                                if (swipeUpToDetails) {
                                    scope.launch {
                                        verticalOffset.snapTo(verticalOffset.value + dy)
                                        Log.d("GESTURE_DEBUG", "OFFSET UPDATE - vertical=${verticalOffset.value}")
                                        
                                        // Calculate progress based on drag distance (threshold = 50% screen height)
                                        val progress = (abs(verticalOffset.value) / (screenHeight * 0.5f)).coerceIn(0f, 1f)
                                        detailsPanelProgress.snapTo(progress)
                                        
                                        // Hide controls immediately when gesture starts
                                        if (progress > 0.01f) {
                                            showControls = false
                                        }
                                    }
                                }
                            }
                            
                            GestureMode.VERTICAL_DOWN -> {
                                // Only track if enabled (either for closing overlay or closing details panel)
                                if (swipeDownToClose || detailsPanelProgress.value > 0f) {
                                    scope.launch {
                                        verticalOffset.snapTo(verticalOffset.value + dy)
                                        Log.d("GESTURE_DEBUG", "OFFSET UPDATE - vertical=${verticalOffset.value}")
                                    }
                                    
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

                    // 5️⃣ Release logic - decide complete or snap back
                    when (currentGestureMode) {
                        GestureMode.HORIZONTAL_SWIPE -> {
                            // 4️⃣ Threshold uses distance OR velocity
                            val distanceThreshold = screenWidth * 0.25f
                            val velocityThreshold = 1500f  // pixels per second
                            
                            // 8️⃣ LOG THRESHOLD CHECK
                            Log.d("GESTURE_DEBUG", "THRESHOLD CHECK - offset=${horizontalOffset.value}, velocity=$velocityX, distanceThreshold=$distanceThreshold")
                            
                            scope.launch {
                                // Fast swipe or sufficient distance
                                if (abs(horizontalOffset.value) > distanceThreshold || abs(velocityX) > velocityThreshold) {
                                    // Determine next index
                                    val newIndex = if (horizontalOffset.value < 0 && currentIndex < mediaItems.size - 1) {
                                        currentIndex + 1
                                    } else if (horizontalOffset.value > 0 && currentIndex > 0) {
                                        currentIndex - 1
                                    } else {
                                        currentIndex  // Stay at current if at boundary
                                    }
                                    
                                    Log.d("MediaOverlay_Swipe", "Swipe threshold passed - currentIndex=$currentIndex, newIndex=$newIndex, offset=${horizontalOffset.value}")
                                    
                                    // Complete gesture - animate slide to completion
                                    val targetOffset = if (horizontalOffset.value < 0) -screenWidth else screenWidth
                                    
                                    // Animate slide to next/prev
                                    horizontalOffset.animateTo(
                                        targetValue = targetOffset,
                                        animationSpec = tween(
                                            durationMillis = 250,
                                            easing = FastOutSlowInEasing
                                        )
                                    )
                                    
                                    Log.d("MediaOverlay_Swipe", "Animation complete - offset reached $targetOffset")
                                    
                                    // Update currentIndex AFTER animation completes
                                    if (newIndex != currentIndex) {
                                        currentIndex = newIndex
                                        viewModel.updateOverlayIndex(currentIndex)
                                        Log.d("MediaOverlay_Swipe", "Updated currentIndex to $currentIndex (new image now centered)")
                                    }
                                    
                                    // Reset offset to center (this makes the new currentIndex appear at center)
                                    horizontalOffset.snapTo(0f)
                                    Log.d("MediaOverlay_Swipe", "Reset offset to 0 - swipe complete")
                                } else {
                                    Log.d("MediaOverlay_Swipe", "Snap back - offset (${horizontalOffset.value}) below threshold ($distanceThreshold)")
                                    // Snap back to center with simple tween
                                    horizontalOffset.animateTo(
                                        targetValue = 0f,
                                        animationSpec = tween(
                                            durationMillis = 200,
                                            easing = FastOutSlowInEasing
                                        )
                                    )
                                }
                            }
                        }
                        
                        GestureMode.VERTICAL_UP -> {
                            // Only process if swipe up to details is enabled
                            if (swipeUpToDetails) {
                                Log.d("GESTURE_DEBUG", "THRESHOLD CHECK - detailsProgress=${detailsPanelProgress.value}, threshold=0.3f")
                                
                                scope.launch {
                                    if (detailsPanelProgress.value >= 0.3f) {
                                        // Complete gesture - lock details panel at 100%
                                        showDetailsPanel = true
                                        Log.d("GESTURE_DEBUG", "GESTURE RESULT → LOCK_DETAILS")
                                        launch {
                                            detailsPanelProgress.animateTo(
                                                targetValue = 1f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMedium
                                                )
                                            )
                                        }
                                    } else {
                                        // Snap back - collapse details
                                        Log.d("GESTURE_DEBUG", "GESTURE RESULT → COLLAPSE_DETAILS")
                                        launch {
                                            detailsPanelProgress.animateTo(
                                                targetValue = 0f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMedium
                                                )
                                            )
                                        }
                                        showDetailsPanel = false
                                        showControls = true
                                    }
                                    verticalOffset.snapTo(0f)
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
                                Log.d("GESTURE_DEBUG", "THRESHOLD CHECK - detailsProgress=${detailsPanelProgress.value}, velocityY=$velocityY")
                                
                                scope.launch {
                                    // Close if dragged down significantly OR fast downward swipe
                                    val shouldCloseDetails = detailsPanelProgress.value < 0.7f || velocityY > 1200f
                                    
                                    if (shouldCloseDetails) {
                                        // Complete close - collapse details
                                        Log.d("GESTURE_DEBUG", "GESTURE RESULT → CLOSE_DETAILS")
                                        launch {
                                            detailsPanelProgress.animateTo(
                                                targetValue = 0f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMedium
                                                )
                                            )
                                        }
                                        showDetailsPanel = false
                                        showControls = true
                                    } else {
                                        // Snap back to open
                                        Log.d("GESTURE_DEBUG", "GESTURE RESULT → SNAP_BACK_TO_OPEN_DETAILS")
                                        launch {
                                            detailsPanelProgress.animateTo(
                                                targetValue = 1f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMedium
                                                )
                                            )
                                        }
                                        showDetailsPanel = true
                                    }
                                    verticalOffset.snapTo(0f)
                                }
                            } else if (swipeDownToClose) {
                                // Details panel is closed, proceed with image close only if enabled
                                val threshold = 150f
                                
                                
                                scope.launch {
                                    if (abs(verticalOffset.value) > threshold) {
                                        // Complete close gesture
                                        showControls = false
                                        onDismiss()
                                    } else {
                                        // Snap back
                                        Log.d("GESTURE_DEBUG", "GESTURE RESULT → SNAP_BACK")
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
                            Log.d("GESTURE_DEBUG", "No gesture detected - mode was NONE")
                        }
                    }

                    // Reset gesture mode
                    Log.d("GESTURE_DEBUG", "========== Gesture END - Resetting mode ==========")
                    gestureMode = GestureMode.NONE
                }
            }
    ) {
        // Background scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = backgroundAlpha))
        )

        // Media image
        currentItem?.let { item ->
            // Render previous, current, and next images based on currentIndex
            val prevItem = mediaItems.getOrNull(currentIndex - 1)
            val nextItem = mediaItems.getOrNull(currentIndex + 1)
            
            Log.d("MediaOverlay_Render", "Rendering: currentIndex=$currentIndex, prev=${currentIndex-1}, next=${currentIndex+1}, offset=${horizontalOffset.value}")
            
            // Preload adjacent images into memory to prevent blink on swipe
            LaunchedEffect(currentIndex) {
                // Preload next and previous images
                prevItem?.let { prev ->
                    val imageLoader = coil.Coil.imageLoader(context)
                    val request = ImageRequest.Builder(context)
                        .data(prev.uri)
                        .size(Size.ORIGINAL)  // Load full size
                        .memoryCacheKey(prev.uri.toString())
                        .diskCacheKey(prev.uri.toString())
                        .build()
                    imageLoader.enqueue(request)
                }
                
                nextItem?.let { next ->
                    val imageLoader = coil.Coil.imageLoader(context)
                    val request = ImageRequest.Builder(context)
                        .data(next.uri)
                        .size(Size.ORIGINAL)  // Load full size
                        .memoryCacheKey(next.uri.toString())
                        .diskCacheKey(next.uri.toString())
                        .build()
                    imageLoader.enqueue(request)
                }
            }
            
            // Render previous, current, and next images for smooth horizontal swipe
            Box(modifier = Modifier.fillMaxSize()) {
                // Previous image - use stable key to prevent rebinding
                prevItem?.let { prev ->
                    key(prev.id) {  // Stable key prevents recomposition
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(prev.uri)
                                .size(Size.ORIGINAL)
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
                key(item.id) {  // Stable key prevents unnecessary recomposition
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(item.uri)
                            .size(Size.ORIGINAL)
                            .memoryCacheKey(item.uri.toString())
                            .diskCacheKey(item.uri.toString())
                            .crossfade(false)  // Disable crossfade to prevent blink
                            .build(),
                        contentDescription = item.displayName,
                        contentScale = if (detailsPanelProgress.value > 0.01f) ContentScale.Crop else ContentScale.Fit,
                        loading = {
                            // Show black background during load (matches overlay)
                            Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                // Double-tap detection for images (before single tap)
                                detectTapGestures(
                                    onDoubleTap = { handleDoubleTap() },
                                    onTap = {
                                        // Single tap to toggle UI
                                        showControls = !showControls
                                    }
                                )
                            }
                            .graphicsLayer {
                                // SHARED ELEMENT ANIMATION: Priority #1
                                // During opening animation, apply thumbnail-to-fullscreen transform
                                if (sharedElementAnimation.isAnimating && thumbnailBounds != null) {
                                    val transform = calculateMediaTransform(
                                        thumbnailBounds = thumbnailBounds,
                                        screenSize = IntSize(screenWidth.toInt(), screenHeight.toInt()),
                                        progress = sharedElementAnimation.progress.value,
                                        scaleMultiplier = sharedElementAnimation.scale.value
                                    )
                                    
                                    this.translationX = transform.translationX
                                    this.translationY = transform.translationY
                                    this.scaleX = transform.scaleX
                                    this.scaleY = transform.scaleY
                                    this.transformOrigin = transform.transformOrigin
                                    
                                    // Round corners during animation
                                    this.clip = true
                                    this.shape = RoundedCornerShape(sharedElementAnimation.cornerRadius.value.dp)
                                } else {
                                    // GESTURE-DRIVEN STATE: After animation completes
                                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                                    this.clip = false
                                    
                                    // Details panel active - move image to upper half
                                    if (detailsPanelProgress.value > 0.01f) {
                                        // Image slides up to fit in upper half
                                        val imageOffset = screenHeight * 0.25f * detailsPanelProgress.value
                                        this.translationY = -imageOffset
                                        transformOrigin = TransformOrigin(0.5f, 0f)
                                    } else {
                                        // Horizontal swipe offset from Animatable
                                        this.translationX = horizontalOffset.value
                                        
                                        // Subtle scale during horizontal swipe (for depth)
                                        if (gestureMode == GestureMode.HORIZONTAL_SWIPE) {
                                            val swipeProgress = (abs(horizontalOffset.value) / screenWidth).coerceIn(0f, 1f)
                                            val scaleAmount = swipeProgress * 0.05f
                                            this.scaleX = 1f - scaleAmount
                                            this.scaleY = 1f - scaleAmount
                                        }
                                        
                                        // Vertical offset from Animatable
                                        this.translationY = verticalOffset.value
                                        
                                        // Vertical close - scale down effect (1f → 0.85f)
                                        if (gestureMode == GestureMode.VERTICAL_DOWN) {
                                            val scaleAmount = 0.15f * closeProgress
                                            this.scaleX = 1f - scaleAmount
                                            this.scaleY = 1f - scaleAmount
                                        }
                                        
                                        // Zoom transforms (only when zoomed)
                                        if (scale > 1f) {
                                            this.scaleX *= scale
                                            this.scaleY *= scale
                                            this.translationX += offsetX
                                            this.translationY += offsetY
                                        }
                                    }
                                }
                            }
                        .pointerInput(Unit) {
                            // Pinch zoom (highest priority) - disabled for videos
                            if (item.isVideo != true) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                                    scale = newScale
                                        
                                        if (scale > 1f) {
                                            gestureMode = GestureMode.ZOOM
                                            offsetX += pan.x
                                            offsetY += pan.y
                                        } else {
                                        offsetX = 0f
                                        offsetY = 0f
                                        gestureMode = GestureMode.NONE
                                    }
                                }
                            }
                        }
                    )
                }
                
                // Next image - use stable key to prevent rebinding
                nextItem?.let { next ->
                    key(next.id) {  // Stable key prevents recomposition
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(next.uri)
                                .size(Size.ORIGINAL)
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
        
        // Track fullscreen state across all components
        var isAnyVideoFullscreen by remember { mutableStateOf(false) }
        
        // Video player overlay (when video is playing)
        exoPlayer?.let { player ->
            if (currentItem?.isVideo == true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // SHARED ELEMENT ANIMATION: Apply to video player too
                            if (sharedElementAnimation.isAnimating && thumbnailBounds != null) {
                                val transform = calculateMediaTransform(
                                    thumbnailBounds = thumbnailBounds,
                                    screenSize = IntSize(screenWidth.toInt(), screenHeight.toInt()),
                                    progress = sharedElementAnimation.progress.value,
                                    scaleMultiplier = sharedElementAnimation.scale.value
                                )
                                
                                this.translationX = transform.translationX
                                this.translationY = transform.translationY
                                this.scaleX = transform.scaleX
                                this.scaleY = transform.scaleY
                                this.transformOrigin = transform.transformOrigin
                                
                                // Round corners during animation
                                this.clip = true
                                this.shape = RoundedCornerShape(sharedElementAnimation.cornerRadius.value.dp)
                            } else {
                                // GESTURE-DRIVEN STATE: After animation completes
                                transformOrigin = TransformOrigin(0.5f, 0.5f)
                                this.clip = false
                                
                                // Details panel active - move video to upper half
                                if (detailsPanelProgress.value > 0.01f) {
                                    val imageOffset = screenHeight * 0.25f * detailsPanelProgress.value
                                    this.translationY = -imageOffset
                                    transformOrigin = TransformOrigin(0.5f, 0f)
                                } else {
                                    // Horizontal swipe offset
                                    this.translationX = horizontalOffset.value
                                    
                                    // Vertical offset
                                    this.translationY = verticalOffset.value
                                    
                                    // Vertical close - scale down effect
                                    if (gestureMode == GestureMode.VERTICAL_DOWN) {
                                        val scaleAmount = 0.15f * closeProgress
                                        this.scaleX = 1f - scaleAmount
                                        this.scaleY = 1f - scaleAmount
                                    }
                                }
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
                var isVideoFullscreen by remember { mutableStateOf(false) }
                
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
                        }
                    )
                }
            }
        }
}
        // Top header (animated) - hide during fullscreen
        AnimatedVisibility(
            visible = controlsVisible && !isAnyVideoFullscreen,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    // Fade out and slide up during downward swipe
                    alpha = 1f - closeProgress
                    translationY = -closeProgress * 100f
                }
        ) {
            // Background extends behind status bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
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
                        // Location (if available)
                        // TODO: Add location from media metadata when available
                        // Text(
                        //     text = "Location Name",
                        //     color = Color.White.copy(alpha = 0.7f),
                        //     style = MaterialTheme.typography.bodySmall
                        // )
                        
                        // Date + Time
                        Text(
                            text = currentItem?.let {
                                val dateFormat = java.text.SimpleDateFormat("MMM d, yyyy • h:mm a", java.util.Locale.getDefault())
                                dateFormat.format(java.util.Date(it.dateAdded * 1000))
                            } ?: "",
                            color = Color.White.copy(alpha = 0.9f),
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
                                tint = if (isFavorited) Color(0xFFFFD700) else Color.White
                            )
                        }
                    }
                }
            }
        }

        // Bottom controls (animated)
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer {
                    // Fade out and slide down during downward swipe
                    alpha = 1f - closeProgress
                    translationY = closeProgress * 100f
                }
        ) {
            // Background extends behind navigation bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .navigationBarsPadding()  // Content respects navigation bar inset
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isTrashMode) {
                        // Trash mode: Only Restore and Delete
                        IconButton(onClick = restoreItem) {
                            FontIcon(
                                unicode = FontIcons.Refresh,
                                contentDescription = "Restore",
                                tint = Color.White
                            )
                        }
                        
                        IconButton(onClick = deleteItem) {
                            FontIcon(
                                unicode = FontIcons.Delete,
                                contentDescription = "Delete permanently",
                                tint = Color.White
                            )
                        }
                    } else {
                        // Normal mode: Edit → Share → Delete → Hamburger Menu
                        
                        // Edit
                        IconButton(onClick = editItem) {
                            FontIcon(
                                unicode = FontIcons.Edit,
                                contentDescription = "Edit",
                                tint = Color.White
                            )
                        }
                        
                        // Share
                        IconButton(onClick = shareItem) {
                            FontIcon(
                                unicode = FontIcons.Share,
                                contentDescription = "Share",
                                tint = Color.White
                            )
                        }
                        
                        // Delete
                        IconButton(onClick = deleteItem) {
                            FontIcon(
                                unicode = FontIcons.Delete,
                                contentDescription = "Delete",
                                tint = Color.White
                            )
                        }
                        
                        // Hamburger Menu (more options)
                        var menuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                FontIcon(
                                    unicode = FontIcons.Menu,
                                    contentDescription = "More options",
                                    tint = Color.White
                                )
                            }
                            // Dropdown menu
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                modifier = Modifier.widthIn(min = 220.dp),
                                offset = DpOffset(x = (-8).dp, y = (-8).dp),
                                shape = SmoothCornerShape(20.dp, 60),
                                containerColor = Color.Black.copy(alpha = 0.95f)
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
                                                shape = SmoothCornerShape(12.dp, 60),
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
                                                        tint = Color.White,
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
                                                shape = SmoothCornerShape(12.dp, 60),
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
                                                        tint = Color.White,
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
                                                shape = SmoothCornerShape(12.dp, 60),
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
                                                        tint = Color.White,
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
                                                shape = SmoothCornerShape(12.dp, 60),
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
                                                        tint = Color.White,
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
                                        showDetailsPanel = true
                                        showControls = false
                                        scope.launch {
                                            detailsPanelProgress.animateTo(
                                                targetValue = 1f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMedium
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
        
        // Gesture-driven Details Panel - slides up from bottom
        if (detailsPanelProgress.value > 0f) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight((0.6f * detailsPanelProgress.value).coerceAtMost(0.6f))  // Max 60% height
                    .graphicsLayer {
                        // No gap at bottom - directly attached
                        val slideOffset = (1f - detailsPanelProgress.value) * size.height
                        translationY = slideOffset
                        alpha = detailsPanelProgress.value.coerceIn(0f, 1f)
                    }
                    .pointerInput(Unit) {
                        // Consume all touch events inside the panel to prevent parent gesture handling
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                // Consume all pointer events to prevent overlay gestures
                                event.changes.forEach { it.consume() }
                            }
                        }
                    },
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(
                    topStart = 28.dp,
                    topEnd = 28.dp,
                    bottomStart = 0.dp,
                    bottomEnd = 0.dp
                ),
                tonalElevation = 3.dp
            ) {
                currentItem?.let { item ->
                    DetailsBottomSheetContent(
                        mediaItem = item,
                        onEditMetadata = {
                            scope.launch {
                                detailsPanelProgress.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                                showDetailsPanel = false
                                showControls = true
                            }
                            editItem()
                        }
                    )
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
                        mediaItems.getOrNull(currentIndex)?.let { item ->
                            try {
                                val deleted = context.contentResolver.delete(item.uri, null, null)
                                if (deleted > 0) {
                                    Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
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
                TextButton(onClick = { showDeleteDialog = false }) {
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
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onFullscreenChange: (Boolean) -> Unit = {}
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
                .padding(bottom = bottomBarHeight) // Attached to bottom bar, no spacing
                .pointerInput(isFullscreen) {
                    // Block ALL gestures in this area (video controls zone)
                    // In fullscreen, also block to prevent any interaction
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            // Consume all pointer events to prevent ANY gestures from passing through
                            event.changes.forEach { change ->
                                change.consume()
                            }
                        }
                    }
                }
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
