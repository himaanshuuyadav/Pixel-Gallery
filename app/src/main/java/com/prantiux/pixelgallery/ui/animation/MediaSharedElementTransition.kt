@file:OptIn(ExperimentalAnimationApi::class)

package com.prantiux.pixelgallery.ui.animation

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * PIXEL-STYLE SHARED ELEMENT TRANSITION
 * 
 * Implements a high-quality thumbnail → fullscreen animation that makes the SAME
 * media appear to physically grow and move into place.
 * 
 * This is NOT a screen transition. This is a coordinate-space transformation where:
 * - The media starts at thumbnail bounds
 * - The media ends at fullscreen bounds
 * - Position, size, corner radius, and background all animate smoothly
 * 
 * Motion Design Philosophy (Material Design 3 / Pixel Gallery):
 * - FastOutSlowIn easing for natural deceleration
 * - Slight scale overshoot (1.02) for tactile feel
 * - Synchronized transforms prevent jarring
 * - 300-350ms duration feels instant but not rushed
 */

/**
 * Represents the bounds of a thumbnail in window coordinates.
 * 
 * These coordinates are relative to the entire window (including status bar),
 * which is necessary for accurate positioning during the animation.
 * 
 * @param left X position of the left edge
 * @param top Y position of the top edge  
 * @param width Width of the thumbnail
 * @param height Height of the thumbnail
 */
data class SharedElementBounds(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
) {
    companion object {
        fun fromRect(rect: Rect): SharedElementBounds {
            return SharedElementBounds(
                left = rect.left,
                top = rect.top,
                width = rect.width,
                height = rect.height
            )
        }
    }
}

/**
 * Animation state for shared element transition.
 * 
 * Holds all animatable values that drive the transform from thumbnail to fullscreen.
 */
class SharedElementAnimationState {
    // Main animation progress: 0f = thumbnail state, 1f = fullscreen state
    val progress = Animatable(0f)
    
    // Scale overshoot for tactile feel (animates from 1f → 1.02f → 1f)
    val scale = Animatable(1f)
    
    // Background scrim alpha (0f = transparent, 1f = opaque black)
    val backgroundAlpha = Animatable(0f)
    
    // Corner radius in dp (thumbnail corner → 0dp)
    val cornerRadius = Animatable(0f)
    
    var isAnimating by mutableStateOf(false)
        private set
    
    /**
     * Start the opening animation (thumbnail → fullscreen).
     * 
     * Orchestrates multiple concurrent animations:
     * 1. Main progress (linear transform)
     * 2. Scale overshoot (spring for bounce)
     * 3. Background fade-in (synced with progress)
     * 4. Corner radius (sharp → rounded)
     */
    suspend fun animateOpen(
        thumbnailCornerRadius: Float,
        durationMillis: Int = OPEN_ANIMATION_DURATION_MS
    ) {
        isAnimating = true
        
        // Reset to initial state
        progress.snapTo(0f)
        scale.snapTo(1f)
        backgroundAlpha.snapTo(0f)
        cornerRadius.snapTo(thumbnailCornerRadius)
        
        // Launch all animations concurrently
        coroutineScope {
            // Main progress: smooth ease out
            launch {
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = durationMillis,
                        easing = FastOutSlowInEasing
                    )
                )
            }
            
            // Scale overshoot: creates tactile "settle" feeling
            // Briefly exceeds 1.0 before settling, like a spring
            launch {
                // Wait for 60% of animation before overshoot
                kotlinx.coroutines.delay((durationMillis * 0.6f).toLong())
                
                scale.animateTo(
                    targetValue = SCALE_OVERSHOOT_AMOUNT,
                    animationSpec = tween(
                        durationMillis = (durationMillis * 0.2f).toInt(),
                        easing = LinearOutSlowInEasing
                    )
                )
                
                // Settle back to 1.0
                scale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
            
            // Background scrim: fade in with slight delay for depth
            launch {
                kotlinx.coroutines.delay(50) // 50ms delay creates layering effect
                backgroundAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = durationMillis - 50,
                        easing = LinearEasing // Linear fade feels more natural for opacity
                    )
                )
            }
            
            // Corner radius: round → sharp
            launch {
                cornerRadius.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = durationMillis,
                        easing = FastOutSlowInEasing
                    )
                )
            }
        }
        
        isAnimating = false
    }
    
    /**
     * Start the closing animation (fullscreen → thumbnail).
     * 
     * Reverses the opening animation with slightly shorter duration
     * for snappy feel when dismissing.
     */
    suspend fun animateClose(
        thumbnailCornerRadius: Float,
        durationMillis: Int = CLOSE_ANIMATION_DURATION_MS
    ) {
        isAnimating = true
        
        coroutineScope {
            launch {
                progress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = durationMillis,
                        easing = FastOutSlowInEasing
                    )
                )
            }
            
            launch {
                scale.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = durationMillis,
                        easing = FastOutSlowInEasing
                    )
                )
            }
            
            launch {
                backgroundAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = durationMillis,
                        easing = LinearEasing
                    )
                )
            }
            
            launch {
                cornerRadius.animateTo(
                    targetValue = thumbnailCornerRadius,
                    animationSpec = tween(
                        durationMillis = durationMillis,
                        easing = FastOutSlowInEasing
                    )
                )
            }
        }
        
        isAnimating = false
    }
    
    /**
     * Instantly jump to fullscreen state without animation.
     * Used when no thumbnail bounds are available.
     */
    suspend fun snapToFullscreen() {
        progress.snapTo(1f)
        scale.snapTo(1f)
        backgroundAlpha.snapTo(1f)
        cornerRadius.snapTo(0f)
    }
}

/**
 * Transform data for positioning and scaling media during animation.
 * 
 * These values are calculated based on animation progress and applied
 * via graphicsLayer for efficient rendering.
 */
data class MediaTransform(
    val translationX: Float,
    val translationY: Float,
    val scaleX: Float,
    val scaleY: Float,
    val transformOrigin: TransformOrigin
)

/**
 * Calculate the transform needed to position media between thumbnail and fullscreen.
 * 
 * Math explanation:
 * - Start: Media is at thumbnail position/size
 * - End: Media is at fullscreen position/size (centered, aspect-fit)
 * - Progress: Interpolate linearly between start and end
 * 
 * The transform origin is set to top-left (0, 0) during animation so that
 * scaling and translating work correctly together.
 * 
 * @param thumbnailBounds Starting bounds (thumbnail position/size)
 * @param screenSize Fullscreen dimensions
 * @param progress Animation progress (0f to 1f)
 * @param scaleMultiplier Additional scale factor (for overshoot)
 * @return Transform values to apply via graphicsLayer
 */
fun calculateMediaTransform(
    thumbnailBounds: SharedElementBounds,
    screenSize: IntSize,
    progress: Float,
    scaleMultiplier: Float = 1f
): MediaTransform {
    // Calculate thumbnail scale relative to screen
    val startScaleX = thumbnailBounds.width / screenSize.width
    val startScaleY = thumbnailBounds.height / screenSize.height
    
    // Fullscreen is always scale 1.0 (but with scaleMultiplier for overshoot)
    val endScaleX = 1f * scaleMultiplier
    val endScaleY = 1f * scaleMultiplier
    
    // Interpolate scale
    val currentScaleX = androidx.compose.ui.util.lerp(startScaleX, endScaleX, progress)
    val currentScaleY = androidx.compose.ui.util.lerp(startScaleY, endScaleY, progress)
    
    // Calculate translation
    // At progress 0: media is at thumbnail.left, thumbnail.top
    // At progress 1: media is at 0, 0 (fullscreen, before scaling)
    val startTranslationX = thumbnailBounds.left
    val startTranslationY = thumbnailBounds.top
    val endTranslationX = 0f
    val endTranslationY = 0f
    
    val currentTranslationX = androidx.compose.ui.util.lerp(startTranslationX, endTranslationX, progress)
    val currentTranslationY = androidx.compose.ui.util.lerp(startTranslationY, endTranslationY, progress)
    
    return MediaTransform(
        translationX = currentTranslationX,
        translationY = currentTranslationY,
        scaleX = currentScaleX,
        scaleY = currentScaleY,
        transformOrigin = TransformOrigin(0f, 0f) // Top-left origin for consistent transforms
    )
}

/**
 * Composable entry point for shared element transition.
 * 
 * Usage in media overlay:
 * ```
 * val animationState = rememberSharedElementAnimation()
 * 
 * LaunchedEffect(Unit) {
 *     if (thumbnailBounds != null) {
 *         animationState.animateOpen(12f) // 12dp corner radius
 *     } else {
 *         animationState.snapToFullscreen()
 *     }
 * }
 * 
 * val transform = calculateMediaTransform(
 *     thumbnailBounds = thumbnailBounds,
 *     screenSize = IntSize(screenWidth, screenHeight),
 *     progress = animationState.progress.value,
 *     scaleMultiplier = animationState.scale.value
 * )
 * 
 * Box(
 *     modifier = Modifier
 *         .graphicsLayer {
 *             translationX = transform.translationX
 *             translationY = transform.translationY
 *             scaleX = transform.scaleX
 *             scaleY = transform.scaleY
 *             transformOrigin = transform.transformOrigin
 *             clip = true
 *             shape = RoundedCornerShape(animationState.cornerRadius.value.dp)
 *         }
 * )
 * ```
 */
@Composable
fun rememberSharedElementAnimation(): SharedElementAnimationState {
    return remember { SharedElementAnimationState() }
}

// ============================================================================
// ANIMATION CONSTANTS
// ============================================================================

/**
 * Opening animation duration in milliseconds.
 * 
 * 350ms is the sweet spot:
 * - Fast enough to feel instant
 * - Slow enough to perceive the motion
 * - Matches Material Design 3 emphasized timing
 */
private const val OPEN_ANIMATION_DURATION_MS = 350

/**
 * Closing animation duration in milliseconds.
 * 
 * Slightly faster than opening (300ms) creates a snappy dismiss feel,
 * which is common in mobile UX patterns.
 */
private const val CLOSE_ANIMATION_DURATION_MS = 300

/**
 * Scale overshoot amount.
 * 
 * The media briefly scales to 102% (1.02f) before settling at 100%.
 * This creates a subtle "bounce" that makes the animation feel tactile
 * and physical, similar to iOS and Pixel Gallery.
 */
private const val SCALE_OVERSHOOT_AMOUNT = 1.02f
