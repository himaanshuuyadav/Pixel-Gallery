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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
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
import com.prantiux.pixelgallery.model.MediaItem
import com.prantiux.pixelgallery.viewmodel.MediaViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons

/**
 * ============================================================================
 * SLOT-BASED ARCHITECTURE - PRODUCTION-GRADE GALLERY IMPLEMENTATION
 * ============================================================================
 * 
 * This MediaOverlay uses a professional slot-based rendering system that
 * eliminates image blinking during swipes - matching Google Photos/Samsung Gallery.
 * 
 * KEY CONCEPTS:
 * 
 * 1. THREE PERSISTENT SLOTS:
 *    - Composables are NEVER destroyed during swipes
 *    - Keyed by position (PREV, CENTER, NEXT) - not by media ID
 *    - Only the MediaItem data inside slots changes
 * 
 * 2. SLOT ROTATION (Not Composable Recreation):
 *    User swipes left:
 *      Before: [PREV: img9] [CENTER: img10] [NEXT: img11]
 *      After:  [PREV: img10] [CENTER: img11] [NEXT: img12]
 *    
 *    CRITICAL: The PREV, CENTER, NEXT composables stay alive!
 *    Only their mediaItem property changes.
 * 
 * 3. WHY THIS ELIMINATES BLINKING:
 *    - AsyncImage painters remain cached in memory
 *    - No composable destruction = no black placeholder state
 *    - Coil's image cache is preserved across rotations
 * 
 * 4. WHAT WAS REMOVED:
 *    - displayIndex variable (dead code - never used)
 *    - favoriteStates local cache (redundant - already in MediaItem)
 *    - Index-driven rendering (prev/current/next recalculated each frame)
 *    - Duplicate transform logic (video player had same code as images)
 * 
 * 5. PERFORMANCE IMPROVEMENTS:
 *    - derivedStateOf prevents unnecessary recompositions
 *    - Stable keys (by SlotPosition) optimize Compose diff algorithm
 *    - Single source of truth for transforms (MediaSlot composable)
 * 
 * DEBUGGING:
 * - Search logs for "SlotManager" to see slot rotation events
 * - Each swipe logs: threshold check, animation, rotation, index update
 * 
 * @author Refactored to production-grade architecture (Jan 2026)
 */

// ============= SLOT-BASED ARCHITECTURE =============
// Prevents image blinking by using stable composable slots

/**
 * Slot positions for the three-image carousel.
 * These are STABLE - composables keyed by position never get destroyed.
 */
private enum class SlotPosition {
    PREV,    // Left slot  (offset: -screenWidth)
    CENTER,  // Center slot (offset: 0)
    NEXT     // Right slot (offset: +screenWidth)
}

/**
 * Represents a single image slot with its media data and position.
 * Only the mediaItem changes during rotation - slot itself is stable.
 */
private data class ImageSlot(
    val position: SlotPosition,
    val mediaItem: MediaItem?,
    val baseOffsetX: Float  // Fixed offset for this slot position
)

/**
 * Manages the three-slot carousel and handles slot rotation.
 * KEY INSIGHT: Slots never change, only their content rotates.
 */
private class SlotManager(
    private val mediaItems: List<MediaItem>,
    initialIndex: Int,
    private val screenWidth: Float
) {
    var centerIndex by mutableIntStateOf(initialIndex)
    
    /**
     * Returns the three slots with current media items.
     * Called on every recomposition but slots themselves are stable.
     */
    fun getSlots(): List<ImageSlot> {
        Log.d("SlotManager", "getSlots() - centerIndex=$centerIndex, total=${mediaItems.size}")
        return listOf(
            ImageSlot(
                position = SlotPosition.PREV,
                mediaItem = mediaItems.getOrNull(centerIndex - 1),
                baseOffsetX = -screenWidth
            ),
            ImageSlot(
                position = SlotPosition.CENTER,
                mediaItem = mediaItems.getOrNull(centerIndex),
                baseOffsetX = 0f
            ),
            ImageSlot(
                position = SlotPosition.NEXT,
                mediaItem = mediaItems.getOrNull(centerIndex + 1),
                baseOffsetX = screenWidth
            )
        )
    }
    
    /**
     * Rotate slots forward (user swiped left, next image becomes center).
     * This ONLY changes data, not composables.
     */
    fun rotateForward(): Boolean {
        return if (centerIndex < mediaItems.size - 1) {
            centerIndex++
            Log.d("SlotManager", "rotateForward() - new centerIndex=$centerIndex")
            true
        } else {
            Log.d("SlotManager", "rotateForward() - BLOCKED at end")
            false
        }
    }
    
    /**
     * Rotate slots backward (user swiped right, prev image becomes center).
     */
    fun rotateBackward(): Boolean {
        return if (centerIndex > 0) {
            centerIndex--
            Log.d("SlotManager", "rotateBackward() - new centerIndex=$centerIndex")
            true
        } else {
            Log.d("SlotManager", "rotateBackward() - BLOCKED at start")
            false
        }
    }
    
    fun getCurrentItem(): MediaItem? = mediaItems.getOrNull(centerIndex)
}

// Gesture direction locking
private enum class GestureMode {
    NONE,
    HORIZONTAL_SWIPE,
    VERTICAL_UP,
    VERTICAL_DOWN,
    ZOOM
}

private data class Transforms(
    val translationX: Float,
    val translationY: Float,
    val scaleX: Float,
    val scaleY: Float
)

@Composable
fun MediaOverlay(
    viewModel: MediaViewModel,
    overlayState: MediaViewModel.MediaOverlayState,
    mediaItems: List<MediaItem>,
    onDismiss: () -> Unit
) {
    if (!overlayState.isVisible) return

    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val view = LocalView.current
    val context = LocalContext.current
    
    // Set solid black colors for system bars during media overlay using modern API
    SideEffect {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            // Use modern WindowInsetsController API
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.setSystemBarsAppearance(
                    0,  // Dark content (light icons/text)
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or 
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
            }
            // Set scrim colors to transparent black for edge-to-edge
            window.statusBarColor = android.graphics.Color.BLACK
            window.navigationBarColor = android.graphics.Color.BLACK
        }
    }
    
    // Check if this is trash mode
    val isTrashMode = overlayState.mediaType == "trash"
    
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }

    // ============= SLOT MANAGER - CORE OF BLINK-FREE ARCHITECTURE =============
    val slotManager = remember(mediaItems) {
        SlotManager(
            mediaItems = mediaItems,
            initialIndex = overlayState.selectedIndex,
            screenWidth = screenWidth
        )
    }
    
    // Derive slots from manager - this updates when centerIndex changes
    val slots by remember {
        derivedStateOf { slotManager.getSlots() }
    }
    
    // Current item from slot manager (not from index calculation)
    val currentItem by remember {
        derivedStateOf { slotManager.getCurrentItem() }
    }

    // Animation progress
    var animationProgress by remember { mutableFloatStateOf(0f) }
    var isClosing by remember { mutableStateOf(false) }
    
    // Zoom state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    
    // Video player state
    var exoPlayer: ExoPlayer? by remember { mutableStateOf(null) }
    var isPlaying by remember { mutableStateOf(false) }
    
    // Delete confirmation dialog
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Gesture state - PERSISTENT ANIMATABLE OFFSETS
    var gestureMode by remember { mutableStateOf(GestureMode.NONE) }
    val horizontalOffset = remember { Animatable(0f) }
    val verticalOffset = remember { Animatable(0f) }
    var detailsPanelProgress by remember { mutableFloatStateOf(0f) }
    
    // Track if closing from downward swipe (to prevent opening animation reversal)
    var closingFromSwipe by remember { mutableStateOf(false) }
    
    // UI visibility
    var showControls by remember { mutableStateOf(false) }
    var showDetailsPanel by remember { mutableStateOf(false) }
    
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
        val player = ExoPlayer.Builder(context).build()
        exoPlayer = player
        onDispose {
            player.release()
            exoPlayer = null
        }
    }
    
    // Update video playback when current item changes
    LaunchedEffect(currentItem) {
        exoPlayer?.let { player ->
            val item = currentItem  // Local copy for smart cast
            if (item?.isVideo == true) {
                val mediaItem = ExoMediaItem.fromUri(item.uri)
                player.setMediaItem(mediaItem)
                player.prepare()
                player.playWhenReady = true
                isPlaying = true
                Log.d("SlotManager", "Video playback started for: ${item.displayName}")
            } else {
                player.pause()
                player.clearMediaItems()
                isPlaying = false
            }
        }
    }
    
    // Close progress for downward swipe (0f = not dragging, 1f = at threshold)
    val closeProgress = if (gestureMode == GestureMode.VERTICAL_DOWN) {
        (abs(verticalOffset.value) / 150f).coerceIn(0f, 1f)
    } else {
        0f
    }

    // Unified closing animation - returns image to thumbnail position
    val closeOverlayWithAnimation: () -> Unit = {
        scope.launch {
            closingFromSwipe = true  // Use gesture-driven transforms during close
            isClosing = true
            showControls = false
            
            // Animate to thumbnail position
            launch {
                animate(
                    initialValue = animationProgress,
                    targetValue = 0f,
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) { value, _ ->
                    animationProgress = value
                }
            }
            
            // Also animate verticalOffset if there's any drag offset
            if (abs(verticalOffset.value) > 0f) {
                launch {
                    verticalOffset.animateTo(
                        targetValue = overlayState.thumbnailBounds?.startTop ?: 0f,
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    )
                }
            }
            
            // Wait for animation then dismiss
            kotlinx.coroutines.delay(300)
            onDismiss()
        }
    }

    // Back handler - intercept system back gesture
    BackHandler(enabled = overlayState.isVisible && !isClosing) {
        closeOverlayWithAnimation()
    }

    // Opening animation
    LaunchedEffect(overlayState.isVisible) {
        if (overlayState.isVisible && !isClosing) {
            // Reset slot manager to initial index
            slotManager.centerIndex = overlayState.selectedIndex
            animationProgress = 0f
            scale = 1f
            offsetX = 0f
            offsetY = 0f
            gestureMode = GestureMode.NONE
            closingFromSwipe = false
            horizontalOffset.snapTo(0f)
            verticalOffset.snapTo(0f)
            detailsPanelProgress = 0f
            showDetailsPanel = false
            
            Log.d("SlotManager", "Opening overlay at index ${overlayState.selectedIndex}")
            
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
            ) { value, _ ->
                animationProgress = value
            }
            showControls = true
        }
    }

    // Current media item for action handlers
    val centerItem = currentItem
    
    // Action handlers (defined after centerItem and closeOverlayWithAnimation)
    val shareItem: () -> Unit = {
        centerItem?.let { item ->
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = item.mimeType
                putExtra(Intent.EXTRA_STREAM, item.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share via"))
        }
    }
    
    val deleteItem: () -> Unit = {
        centerItem?.let { item ->
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
                        val nextIndex = if (slotManager.centerIndex < mediaItems.size - 1) {
                            slotManager.centerIndex // Stay at same index, next item shifts into position
                        } else {
                            (slotManager.centerIndex - 1).coerceAtLeast(0) // Go to previous if was last
                        }
                        
                        // If no more items, close overlay
                        if (mediaItems.size <= 1) {
                            closeOverlayWithAnimation()
                        } else {
                            // Update slot manager index
                            slotManager.centerIndex = nextIndex
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
            centerItem?.let { item ->
                // Trigger system restore dialog directly
                viewModel.restoreFromTrash(context, item)
                closeOverlayWithAnimation()
            }
        }
    }
    
    val editItem: () -> Unit = {
        centerItem?.let { item ->
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
        centerItem?.let { item ->
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
        centerItem?.let { item ->
            // Toggle the favorite state directly (no local cache needed)
            val newState = !item.isFavorite
            
            // Persist to database via ViewModel
            viewModel.toggleFavorite(item.id, newState)
            
            // Show pill message
            favoriteMessage = if (newState) "Added to favourites" else "Removed from favourites"
            showFavoritePill = true
        }
    }
    
    // Double-tap zoom handler (images only)
    val handleDoubleTap: () -> Unit = {
        if (centerItem?.isVideo != true && animationProgress >= 1f && !isClosing) {
            scope.launch {
                val targetScale = if (scale > 1f) 1f else 2f
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

    // Calculate transforms based on thumbnail bounds
    val thumbnailBounds = overlayState.thumbnailBounds
    val (openingTranslationX, openingTranslationY, openingScaleX, openingScaleY) = remember(animationProgress, thumbnailBounds) {
        if (thumbnailBounds == null) {
            Transforms(0f, 0f, 1f, 1f)
        } else {
            val startLeft = thumbnailBounds.startLeft
            val startTop = thumbnailBounds.startTop
            val startWidth = thumbnailBounds.startWidth
            val startHeight = thumbnailBounds.startHeight

            val finalLeft = 0f
            val finalTop = 0f
            val finalWidth = screenWidth
            val finalHeight = screenHeight

            val startScaleX = startWidth / finalWidth
            val startScaleY = startHeight / finalHeight

            val progress = if (isClosing) 1f - animationProgress else animationProgress

            val tx = startLeft + (finalLeft - startLeft) * progress
            val ty = startTop + (finalTop - startTop) * progress
            val sx = startScaleX + (1f - startScaleX) * progress
            val sy = startScaleY + (1f - startScaleY) * progress

            Transforms(tx, ty, sx, sy)
        }
    }

    // Background scrim alpha
    val backgroundAlpha = when {
        isClosing -> animationProgress * 0.5f
        animationProgress < 0.7f -> 0f
        gestureMode == GestureMode.VERTICAL_DOWN -> {
            // During downward swipe, fade background based on drag distance
            val baseAlpha = if (animationProgress >= 0.7f) (animationProgress - 0.7f) / 0.3f else 0f
            baseAlpha * (1f - closeProgress)
        }
        else -> {
            (animationProgress - 0.7f) / 0.3f
        }
    }

    // Controls visibility based on gesture
    val controlsVisible = showControls && 
        !isClosing && 
        animationProgress >= 0.8f && 
        gestureMode != GestureMode.VERTICAL_UP &&
        !showDetailsPanel &&
        detailsPanelProgress < 0.05f &&  // Hide controls when details start emerging
        closeProgress == 0f  // Hide controls when downward swipe starts

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(scale) {
                // Centralized gesture coordinator - DO NOT consume until direction locked
                awaitEachGesture {
                    // 1️⃣ LOG POINTER ENTRY
                    Log.d("GESTURE_DEBUG", "========== Gesture START ==========")
                    
                    val down = awaitFirstDown(requireUnconsumed = false)
                    
                    // LOG FIRST DOWN
                    Log.d("GESTURE_DEBUG", "First DOWN - scale=$scale, animationProgress=$animationProgress")
                    
                    // 1️⃣ INVARIANT: Only process when animation complete AND scale = 1f
                    if (animationProgress < 1f) {
                        Log.d("GESTURE_DEBUG", "BLOCKED - Animation not complete (progress=$animationProgress)")
                        return@awaitEachGesture
                    }
                    
                    // 2️⃣ LOG SCALE GATING
                    Log.d("GESTURE_DEBUG", "Scale check - currentScale=$scale (swipeAllowed=${scale == 1f})")
                    if (scale != 1f) {
                        Log.d("GESTURE_DEBUG", "BLOCKED - Scale is not 1f (scale=$scale)")
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
                                    accumulatedDy > 0 -> GestureMode.VERTICAL_DOWN
                                    accumulatedDy < 0 -> GestureMode.VERTICAL_UP
                                    else -> GestureMode.NONE
                                }
                                gestureMode = currentGestureMode
                                
                                // 4️⃣ LOG DIRECTION LOCK DECISION
                                Log.d("GESTURE_DEBUG", "DIRECTION LOCKED → $currentGestureMode")
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
                                    if (slotManager.centerIndex == 0 && horizontalOffset.value + dx > 0f) {
                                        // At first image, resist right swipe
                                        adjustedDx *= 0.15f
                                    } else if (slotManager.centerIndex == mediaItems.size - 1 && horizontalOffset.value + dx < 0f) {
                                        // At last image, resist left swipe
                                        adjustedDx *= 0.15f
                                    }
                                    
                                    // Calculate velocity for fast swipe detection
                                    val currentTime = System.currentTimeMillis()
                                    val deltaTime = (currentTime - lastMoveTime).coerceAtLeast(1)
                                    velocityX = (adjustedDx / deltaTime) * 1000f  // pixels per second
                                    lastMoveTime = currentTime
                                    
                                    horizontalOffset.snapTo(horizontalOffset.value + adjustedDx)
                                }
                            }
                            
                            GestureMode.VERTICAL_UP -> {
                                // REMOVED: change.consume()
                                scope.launch {
                                    verticalOffset.snapTo(verticalOffset.value + dy)
                                    Log.d("GESTURE_DEBUG", "OFFSET UPDATE - vertical=${verticalOffset.value}")
                                }
                                // Calculate progress based on drag distance (threshold = 50% screen height)
                                val progress = (abs(verticalOffset.value) / (screenHeight * 0.5f)).coerceIn(0f, 1f)
                                detailsPanelProgress = progress
                                
                                // Immediately fade out controls as user starts dragging
                                if (progress > 0.05f) {
                                    showControls = false
                                }
                            }
                            
                            GestureMode.VERTICAL_DOWN -> {
                                // REMOVED: change.consume()
                                scope.launch {
                                    verticalOffset.snapTo(verticalOffset.value + dy)
                                    Log.d("GESTURE_DEBUG", "OFFSET UPDATE - vertical=${verticalOffset.value}")
                                }
                                
                                // Calculate vertical velocity for intent detection
                                val currentTime = System.currentTimeMillis()
                                val deltaTime = (currentTime - lastMoveTime).coerceAtLeast(1)
                                velocityY = (dy / deltaTime) * 1000f  // pixels per second
                                lastMoveTime = currentTime
                                
                                // If details panel is active, close it first (don't close image)
                                if (detailsPanelProgress > 0f) {
                                    // Reverse animate details panel based on drag
                                    val progress = (abs(verticalOffset.value) / (screenHeight * 0.5f)).coerceIn(0f, 1f)
                                    detailsPanelProgress = (1f - progress).coerceIn(0f, 1f)
                                    
                                    // Fade controls back in as details close
                                    if (detailsPanelProgress < 0.3f) {
                                        showControls = true
                                    }
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
                                    // Determine swipe direction
                                    val swipingLeft = horizontalOffset.value < 0
                                    
                                    Log.d("SlotManager", "Swipe threshold passed - offset=${horizontalOffset.value}, velocity=$velocityX, direction=${if (swipingLeft) "LEFT" else "RIGHT"}")
                                    
                                    // Animate to completion
                                    val targetOffset = if (swipingLeft) -screenWidth else screenWidth
                                    
                                    horizontalOffset.animateTo(
                                        targetValue = targetOffset,
                                        animationSpec = tween(
                                            durationMillis = 250,
                                            easing = FastOutSlowInEasing
                                        )
                                    )
                                    
                                    Log.d("SlotManager", "Animation completed - rotating slots")
                                    
                                    // CRITICAL: Rotate slot data (not composables)
                                    val rotated = if (swipingLeft) {
                                        slotManager.rotateForward()
                                    } else {
                                        slotManager.rotateBackward()
                                    }
                                    
                                    if (rotated) {
                                        // Update ViewModel index for external state
                                        viewModel.updateOverlayIndex(slotManager.centerIndex)
                                        
                                        // Reset offset to 0 WITHOUT animation
                                        // This makes the new center slot appear at screen center
                                        horizontalOffset.snapTo(0f)
                                        
                                        Log.d("SlotManager", "Slot rotation complete - new center=${slotManager.centerIndex}")
                                    } else {
                                        // Hit boundary, snap back
                                        Log.d("SlotManager", "Boundary hit - snapping back")
                                        horizontalOffset.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        )
                                    }
                                } else {
                                    Log.d("SlotManager", "Snap back - offset (${horizontalOffset.value}) below threshold ($distanceThreshold)")
                                    // Snap back to center
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
                            Log.d("GESTURE_DEBUG", "THRESHOLD CHECK - detailsProgress=$detailsPanelProgress, threshold=0.5f")
                            
                            scope.launch {
                                if (detailsPanelProgress >= 0.5f) {
                                    // Complete gesture - lock details panel at 100%
                                    showDetailsPanel = true
                                    Log.d("GESTURE_DEBUG", "GESTURE RESULT → LOCK_DETAILS")
                                    animate(
                                        initialValue = detailsPanelProgress,
                                        targetValue = 1f,
                                        animationSpec = spring(
                                            stiffness = Spring.StiffnessMediumLow,
                                            dampingRatio = Spring.DampingRatioNoBouncy
                                        )
                                    ) { value, _ ->
                                        detailsPanelProgress = value
                                    }
                                } else {
                                    // Snap back - collapse details
                                    Log.d("GESTURE_DEBUG", "GESTURE RESULT → COLLAPSE_DETAILS")
                                    animate(
                                        initialValue = detailsPanelProgress,
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            stiffness = Spring.StiffnessMediumLow,
                                            dampingRatio = Spring.DampingRatioNoBouncy
                                        )
                                    ) { value, _ ->
                                        detailsPanelProgress = value
                                    }
                                    showDetailsPanel = false
                                    showControls = true
                                }
                                verticalOffset.snapTo(0f)
                            }
                        }
                        
                        GestureMode.VERTICAL_DOWN -> {
                            // If details panel is active, close it first
                            if (detailsPanelProgress > 0f) {
                                Log.d("GESTURE_DEBUG", "THRESHOLD CHECK - detailsProgress=$detailsPanelProgress, velocityY=$velocityY")
                                
                                scope.launch {
                                    // Close if EITHER: dragged > 50% OR fast downward swipe
                                    val shouldCloseDetails = detailsPanelProgress < 0.5f || velocityY > 1200f
                                    
                                    if (shouldCloseDetails) {
                                        // Complete close - collapse details
                                        Log.d("GESTURE_DEBUG", "GESTURE RESULT → CLOSE_DETAILS (progress=$detailsPanelProgress, velocity=$velocityY)")
                                        animate(
                                            initialValue = detailsPanelProgress,
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                stiffness = Spring.StiffnessMediumLow,
                                                dampingRatio = Spring.DampingRatioNoBouncy
                                            )
                                        ) { value, _ ->
                                            detailsPanelProgress = value
                                        }
                                        showDetailsPanel = false
                                        showControls = true
                                    } else {
                                        // Snap back to open
                                        Log.d("GESTURE_DEBUG", "GESTURE RESULT → SNAP_BACK_TO_OPEN_DETAILS")
                                        animate(
                                            initialValue = detailsPanelProgress,
                                            targetValue = 1f,
                                            animationSpec = spring(
                                                stiffness = Spring.StiffnessMediumLow,
                                                dampingRatio = Spring.DampingRatioNoBouncy
                                            )
                                        ) { value, _ ->
                                            detailsPanelProgress = value
                                        }
                                        showDetailsPanel = true
                                    }
                                    verticalOffset.snapTo(0f)
                                }
                            } else {
                                // Details panel is closed, proceed with image close
                                val threshold = 150f
                                
                                Log.d("GESTURE_DEBUG", "THRESHOLD CHECK - offset=${verticalOffset.value}, threshold=$threshold")
                                
                                scope.launch {
                                    if (abs(verticalOffset.value) > threshold) {
                                        // Complete close gesture - animate to thumbnail
                                        Log.d("GESTURE_DEBUG", "GESTURE RESULT → CLOSE_OVERLAY (return to thumbnail)")
                                        closingFromSwipe = true
                                        isClosing = true
                                        showControls = false
                                        
                                        // Animate image back to thumbnail position
                                        launch {
                                            animate(
                                                initialValue = animationProgress,
                                                targetValue = 0f,
                                                animationSpec = tween(300, easing = FastOutSlowInEasing)
                                            ) { value, _ ->
                                                animationProgress = value
                                            }
                                        }
                                        
                                        // Animate verticalOffset to thumbnail top position
                                        launch {
                                            verticalOffset.animateTo(
                                                targetValue = thumbnailBounds?.startTop ?: 0f,
                                                animationSpec = tween(300, easing = FastOutSlowInEasing)
                                            )
                                        }
                                        
                                        // Wait for animation then dismiss
                                        kotlinx.coroutines.delay(300)
                                        onDismiss()
                                    } else {
                                        // Snap back
                                        Log.d("GESTURE_DEBUG", "GESTURE RESULT → SNAP_BACK (close cancelled)")
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

        // ============= SLOT-BASED RENDERING - NO COMPOSABLE RECREATION =============
        // Media images rendered using stable slots
        slots.forEach { slot ->
            // KEY: Stable composable identity by position
            // Only slot.mediaItem changes during rotation, not the composable itself
            key(slot.position) {
                MediaSlot(
                    mediaItem = slot.mediaItem,
                    baseOffsetX = slot.baseOffsetX,
                    gestureOffsetX = horizontalOffset.value,
                    detailsPanelProgress = detailsPanelProgress,
                    animationProgress = animationProgress,
                    isClosing = isClosing,
                    closingFromSwipe = closingFromSwipe,
                    openingTranslationX = openingTranslationX,
                    openingTranslationY = openingTranslationY,
                    openingScaleX = openingScaleX,
                    openingScaleY = openingScaleY,
                    verticalOffset = verticalOffset.value,
                    closeProgress = closeProgress,
                    gestureMode = gestureMode,
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    thumbnailBounds = overlayState.thumbnailBounds,
                    scale = scale,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    onTap = {
                        // Single tap to toggle UI or close details
                        if (animationProgress >= 1f && !isClosing) {
                            if (showDetailsPanel || detailsPanelProgress > 0.1f) {
                                // Close details panel
                                showDetailsPanel = false
                                scope.launch {
                                    animate(
                                        initialValue = detailsPanelProgress,
                                        targetValue = 0f,
                                        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                                    ) { value, _ ->
                                        detailsPanelProgress = value
                                    }
                                    verticalOffset.snapTo(0f)
                                    showControls = true
                                }
                            } else {
                                showControls = !showControls
                            }
                        }
                    },
                    onDoubleTap = handleDoubleTap,
                    onZoomGesture = { centroid, panChange, zoomChange ->
                        if (animationProgress >= 1f && !isClosing) {
                            val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                            scale = newScale
                            
                            if (scale > 1f) {
                                gestureMode = GestureMode.ZOOM
                                offsetX += panChange.x
                                offsetY += panChange.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                                gestureMode = GestureMode.NONE
                            }
                        }
                    }
                )
            }
        }
        
        // Track fullscreen state across all components
        var isAnyVideoFullscreen by remember { mutableStateOf(false) }
        
        // Video player overlay (when video is playing)
        exoPlayer?.let { player ->
            if (centerItem?.isVideo == true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                        // Apply same transformations as image
                        if (animationProgress < 1f && !isClosing) {
                            this.translationX = openingTranslationX
                            this.translationY = openingTranslationY
                            this.scaleX = openingScaleX
                            this.scaleY = openingScaleY
                            transformOrigin = TransformOrigin(0f, 0f)
                        } else if (closingFromSwipe && isClosing) {
                            transformOrigin = TransformOrigin(0f, 0f)
                            val thumbnailBounds = overlayState.thumbnailBounds
                            if (thumbnailBounds != null) {
                                val targetX = thumbnailBounds.startLeft
                                val targetY = thumbnailBounds.startTop
                                val targetScaleX = thumbnailBounds.startWidth / screenWidth
                                val targetScaleY = thumbnailBounds.startHeight / screenHeight
                                val closeTransitionProgress = 1f - animationProgress
                                val startX = horizontalOffset.value
                                val startY = verticalOffset.value
                                val startScale = 1f - closeProgress * 0.15f
                                this.translationX = startX + (targetX - startX) * closeTransitionProgress
                                this.translationY = startY + (targetY - startY) * closeTransitionProgress
                                this.scaleX = startScale + (targetScaleX - startScale) * closeTransitionProgress
                                this.scaleY = startScale + (targetScaleY - startScale) * closeTransitionProgress
                            }
                        } else {
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                            val detailsActivationThreshold = 0.05f
                            if (detailsPanelProgress > detailsActivationThreshold) {
                                val effectiveProgress = ((detailsPanelProgress - detailsActivationThreshold) / 
                                    (1f - detailsActivationThreshold)).coerceIn(0f, 1f)
                                val imageMaxOffset = screenHeight * 0.25f
                                this.translationY = -imageMaxOffset * effectiveProgress
                                transformOrigin = TransformOrigin(0.5f, 0f)
                            } else {
                                this.translationX = horizontalOffset.value
                                this.translationY = verticalOffset.value
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
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),  // Respect status bar inset
                color = Color.Black
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
                            text = centerItem?.let {
                                val dateFormat = java.text.SimpleDateFormat("MMM d, yyyy • h:mm a", java.util.Locale.getDefault())
                                dateFormat.format(java.util.Date(it.dateAdded * 1000))
                            } ?: "",
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    
                    // Right side: Three-dot menu (hidden in trash mode)
                    if (!isTrashMode) {
                        var menuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                FontIcon(
                                    unicode = FontIcons.MoreVert,
                                    contentDescription = "More options",
                                    tint = Color.White
                                )
                            }
                            // Dropdown attached to top bar, flush to right edge, top-left and bottom-left rounded
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                modifier = Modifier
                                    .widthIn(min = 220.dp)
                                    .background(
                                        Color.Black,
                                        shape = RoundedCornerShape(
                                            topStart = 12.dp,
                                            topEnd = 0.dp,
                                            bottomEnd = 0.dp,
                                            bottomStart = 12.dp
                                        )
                                    ),
                                offset = DpOffset(x = 8.dp, y = 0.dp)
                            ) {
                                // 1. Set as wallpaper
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            FontIcon(
                                                unicode = FontIcons.Image,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.9f),
                                                size = 24.sp
                                            )
                                            Text("Set as wallpaper", color = Color.White)
                                        }
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        setAsWallpaper()
                                    }
                                )
                                // 2. Copy to album
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            FontIcon(
                                                unicode = FontIcons.Copy,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.9f),
                                                size = 24.sp
                                            )
                                            Text("Copy to album", color = Color.White)
                                        }
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        // TODO: Copy to album
                                    }
                                )
                                // 3. 3. Move to album
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            FontIcon(
                                                unicode = FontIcons.Move,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.9f),
                                                size = 24.sp
                                            )
                                            Text("Move to album", color = Color.White)
                                        }
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        // TODO: Move to album
                                    }
                                )
                                // 4. Details
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            FontIcon(
                                                unicode = FontIcons.Info,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.9f),
                                                size = 24.sp
                                            )
                                            Text("Details", color = Color.White)
                                        }
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        showDetailsPanel = true
                                        scope.launch {
                                            detailsPanelProgress = 1f
                                        }
                                    }
                                )
                            }
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
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),  // Respect navigation bar inset
                color = Color.Black,
                shape = androidx.compose.ui.graphics.RectangleShape  // Edge-to-edge, no rounded corners
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
                        // Normal mode: Star → Edit → Share → Delete
                        // Favorite (toggles between filled and unfilled star)
                        val isFavorited = centerItem?.isFavorite ?: false
                        IconButton(onClick = toggleFavorite) {
                            FontIcon(
                                unicode = if (isFavorited) FontIcons.Star else FontIcons.StarOutline,
                                contentDescription = if (isFavorited) "Remove from favorites" else "Add to favorites",
                                tint = if (isFavorited) Color(0xFFFFD700) else Color.White
                            )
                        }
                        
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

        // Details panel - Material 3 Expressive, bottom half
        if (detailsPanelProgress > 0f) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f * detailsPanelProgress)
                    .graphicsLayer {
                        // Slide up from bottom, driven by progress
                        val slideOffset = (1f - detailsPanelProgress) * size.height
                        translationY = slideOffset
                        alpha = detailsPanelProgress.coerceIn(0f, 1f)
                    },
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.extraLarge.copy(
                    bottomStart = CornerSize(0.dp),
                    bottomEnd = CornerSize(0.dp)
                ),
                tonalElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    // Drag handle
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(4.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                shape = MaterialTheme.shapes.extraLarge
                            )
                            .align(Alignment.CenterHorizontally)
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Details title
                    Text(
                        text = "Details",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.graphicsLayer {
                            alpha = ((detailsPanelProgress - 0.3f) / 0.7f).coerceIn(0f, 1f)
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    centerItem?.let { item ->
                        // Progressive fade-in for metadata sections
                        val textAlpha = ((detailsPanelProgress - 0.4f) / 0.6f).coerceIn(0f, 1f)
                        
                        Column(
                            modifier = Modifier.graphicsLayer { alpha = textAlpha },
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // File name
                            DetailSection(
                                label = "Name",
                                value = item.displayName
                            )
                            
                            // Date and time
                            DetailSection(
                                label = "Date",
                                value = java.text.SimpleDateFormat(
                                    "MMMM dd, yyyy • h:mm a",
                                    java.util.Locale.getDefault()
                                ).format(java.util.Date(item.dateAdded * 1000))
                            )
                            
                            // GPS Location (if available)
                            if (item.latitude != null && item.longitude != null) {
                                DetailSection(
                                    label = "GPS Location",
                                    value = "${item.latitude}, ${item.longitude}"
                                )
                            } else if (item.location != null) {
                                DetailSection(
                                    label = "Location",
                                    value = item.location
                                )
                            }
                            
                            // File Location
                            if (item.path.contains("/")) {
                                val locationPath = item.path.substringBeforeLast("/")
                                DetailSection(
                                    label = "File Location",
                                    value = locationPath
                                )
                            }
                            
                            // Size
                            val sizeKB = item.size / 1024
                            val sizeFormatted = if (sizeKB > 1024) {
                                String.format("%.2f MB", sizeKB / 1024.0)
                            } else {
                                "$sizeKB KB"
                            }
                            DetailSection(
                                label = "Size",
                                value = sizeFormatted
                            )
                            
                            // Full path
                            DetailSection(
                                label = "Path",
                                value = item.path
                            )
                        }
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
                        centerItem?.let { item ->
                            try {
                                val deleted = context.contentResolver.delete(item.uri, null, null)
                                if (deleted > 0) {
                                    Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                                    closeOverlayWithAnimation()
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

// ============= MEDIA SLOT COMPOSABLE =============
/**
 * A single stable image slot that never gets destroyed during swipes.
 * Only the mediaItem data changes during rotation.
 * 
 * @param mediaItem The media to display (null if slot is empty)
 * @param baseOffsetX The fixed offset for this slot (-screenWidth, 0, or +screenWidth)
 * @param gestureOffsetX The dynamic gesture offset applied to all slots
 */
@Composable
private fun MediaSlot(
    mediaItem: MediaItem?,
    baseOffsetX: Float,
    gestureOffsetX: Float,
    detailsPanelProgress: Float,
    animationProgress: Float,
    isClosing: Boolean,
    closingFromSwipe: Boolean,
    openingTranslationX: Float,
    openingTranslationY: Float,
    openingScaleX: Float,
    openingScaleY: Float,
    verticalOffset: Float,
    closeProgress: Float,
    gestureMode: GestureMode,
    screenWidth: Float,
    screenHeight: Float,
    thumbnailBounds: MediaViewModel.ThumbnailBounds?,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onZoomGesture: (Offset, Offset, Float) -> Unit
) {
    // If slot is empty (at boundaries), don't render anything
    if (mediaItem == null) return
    
    Log.d("SlotManager", "Rendering slot at baseOffset=$baseOffsetX for media: ${mediaItem.displayName}")
    
    AsyncImage(
        model = mediaItem.uri,
        contentDescription = mediaItem.displayName,
        contentScale = if (detailsPanelProgress > 0.05f) ContentScale.Crop else ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onDoubleTap() },
                    onTap = { onTap() }
                )
            }
            .graphicsLayer {
                // Opening animation only
                if (animationProgress < 1f && !isClosing) {
                    // Opening animation - thumbnail to fullscreen
                    this.translationX = openingTranslationX
                    this.translationY = openingTranslationY
                    this.scaleX = openingScaleX
                    this.scaleY = openingScaleY
                    transformOrigin = TransformOrigin(0f, 0f)
                } else if (isClosing && !closingFromSwipe) {
                    // Back button close - use opening animation in reverse
                    this.translationX = openingTranslationX
                    this.translationY = openingTranslationY
                    this.scaleX = openingScaleX
                    this.scaleY = openingScaleY
                    transformOrigin = TransformOrigin(0f, 0f)
                } else if (closingFromSwipe && isClosing) {
                    // Swipe close - animate from current state to thumbnail
                    transformOrigin = TransformOrigin(0f, 0f)
                    
                    if (thumbnailBounds != null) {
                        val targetX = thumbnailBounds.startLeft
                        val targetY = thumbnailBounds.startTop
                        val targetScaleX = thumbnailBounds.startWidth / screenWidth
                        val targetScaleY = thumbnailBounds.startHeight / screenHeight
                        
                        val closeTransitionProgress = 1f - animationProgress
                        
                        val startX = gestureOffsetX
                        val startY = verticalOffset
                        val startScale = 1f - closeProgress * 0.15f
                        
                        this.translationX = startX + (targetX - startX) * closeTransitionProgress
                        this.translationY = startY + (targetY - startY) * closeTransitionProgress
                        this.scaleX = startScale + (targetScaleX - startScale) * closeTransitionProgress
                        this.scaleY = startScale + (targetScaleY - startScale) * closeTransitionProgress
                    } else {
                        this.translationX = gestureOffsetX
                        this.translationY = verticalOffset
                        this.scaleX = 1f - closeProgress * 0.15f
                        this.scaleY = 1f - closeProgress * 0.15f
                    }
                } else {
                    // Gesture-driven state (normal interaction)
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                    
                    // Details panel transition
                    val detailsActivationThreshold = 0.05f
                    if (detailsPanelProgress > detailsActivationThreshold) {
                        val effectiveProgress = ((detailsPanelProgress - detailsActivationThreshold) / 
                            (1f - detailsActivationThreshold)).coerceIn(0f, 1f)
                        
                        val imageMaxOffset = screenHeight * 0.25f
                        this.translationY = -imageMaxOffset * effectiveProgress
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    } else {
                        // BASE POSITION: Slot's fixed offset + gesture offset
                        this.translationX = baseOffsetX + gestureOffsetX
                        
                        // Subtle scale during horizontal swipe
                        if (gestureMode == GestureMode.HORIZONTAL_SWIPE) {
                            val swipeProgress = (abs(gestureOffsetX) / screenWidth).coerceIn(0f, 1f)
                            val scaleAmount = swipeProgress * 0.05f
                            this.scaleX = 1f - scaleAmount
                            this.scaleY = 1f - scaleAmount
                        }
                        
                        // Vertical offset
                        this.translationY = verticalOffset
                        
                        // Vertical close scale
                        if (gestureMode == GestureMode.VERTICAL_DOWN) {
                            val scaleAmount = 0.15f * closeProgress
                            this.scaleX = 1f - scaleAmount
                            this.scaleY = 1f - scaleAmount
                        }
                        
                        // Zoom transforms
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
                // Pinch zoom (disabled for videos)
                if (mediaItem.isVideo != true) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        onZoomGesture(centroid, pan, zoom)
                    }
                }
            }
    )
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
