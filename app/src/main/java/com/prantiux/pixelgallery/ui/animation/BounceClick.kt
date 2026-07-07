package com.prantiux.pixelgallery.ui.animation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Adds a bouncing scale animation to a component when pressed, WITHOUT consuming the click.
 * Gives a premium "squishy" physical feel. Use this alongside clickable or combinedClickable.
 */
fun Modifier.bounceScale(
    scaleDown: Float = 0.92f,
    enabled: Boolean = true
) = composed {
    var isPressed by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = 0.6f, // Slightly bouncy
            stiffness = 500f // Fast
        ),
        label = "bounce_scale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitPointerEventScope {
                while (true) {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    
                    waitForUpOrCancellation()
                    isPressed = false
                }
            }
        }
}
