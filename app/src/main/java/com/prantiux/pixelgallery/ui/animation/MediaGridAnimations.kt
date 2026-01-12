@file:OptIn(ExperimentalAnimationApi::class)

package com.prantiux.pixelgallery.ui.animation

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import com.prantiux.pixelgallery.viewmodel.MediaViewModel

/**
 * CANONICAL MEDIA OVERLAY ANIMATION SYSTEM (Material 3 Expressive)
 * 
 * This defines the SINGLE source of truth for media open/close animations.
 * 
 * Animation Specifications (Expressive Motion):
 * - Duration: 400ms (open), 350ms (close) - Emphasized timing
 * - Easing: EaseInOutCubic (Expressive standard)
 * - Spring: Medium damping for natural feel
 * - Transform: Scale + Position + Fade
 * - Origin: Thumbnail bounds → Full screen
 * 
 * ❌ DO NOT create custom overlay animations
 * ✅ ALWAYS use this system for media transitions
 */

/**
 * Standard animation duration for media overlay opening (Expressive timing)
 */
const val MEDIA_OPEN_DURATION_MS = 400

/**
 * Standard animation duration for media overlay closing (Expressive timing)
 */
const val MEDIA_CLOSE_DURATION_MS = 350

/**
 * Creates the canonical animation spec for media overlay opening
 * Uses Expressive easing for primary actions
 */
fun mediaOpenAnimationSpec(): AnimationSpec<Float> = tween(
    durationMillis = MEDIA_OPEN_DURATION_MS,
    easing = EaseInOutCubic
)

/**
 * Creates the canonical animation spec for media overlay closing
 * Uses Expressive easing for exit transitions
 */
fun mediaCloseAnimationSpec(): AnimationSpec<Float> = tween(
    durationMillis = MEDIA_CLOSE_DURATION_MS,
    easing = EaseInOutCubic
)

/**
 * Creates spring-based animation spec for emphasized motion
 * Used for selection mode transitions and interactive elements
 */
fun mediaSpringAnimationSpec(): AnimationSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMedium
)

/**
 * Thumbnail Bounds Data Class
 * 
 * Represents the position and size of a thumbnail for animation purposes
 */
data class ThumbnailBounds(
    val startLeft: Float,
    val startTop: Float,
    val startWidth: Float,
    val startHeight: Float
)

/**
 * Converts geometry Rect to ThumbnailBounds
 */
fun Rect.toThumbnailBounds(): MediaViewModel.ThumbnailBounds {
    return MediaViewModel.ThumbnailBounds(
        startLeft = left,
        startTop = top,
        startWidth = width,
        startHeight = height
    )
}

/**
 * Remember animatable state for media overlay animations
 * 
 * Usage in screens:
 * ```
 * val animationProgress = rememberMediaAnimationProgress()
 * ```
 */
@Composable
fun rememberMediaAnimationProgress(): Animatable<Float, *> {
    return remember { Animatable(0f) }
}

/**
 * Remember animatable state for horizontal offset (swipe gestures)
 */
@Composable
fun rememberHorizontalOffsetAnimatable(): Animatable<Float, *> {
    return remember { Animatable(0f) }
}

/**
 * Remember animatable state for vertical offset (swipe gestures)
 */
@Composable
fun rememberVerticalOffsetAnimatable(): Animatable<Float, *> {
    return remember { Animatable(0f) }
}

/**
 * Animation State Holder
 * 
 * Holds all animation-related state for media overlays
 */
data class MediaAnimationState(
    val progress: Float = 0f,
    val horizontalOffset: Float = 0f,
    val verticalOffset: Float = 0f,
    val isClosing: Boolean = false
)
