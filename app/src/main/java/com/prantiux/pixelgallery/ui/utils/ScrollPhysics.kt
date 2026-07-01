package com.prantiux.pixelgallery.ui.utils

import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * ZenithFlingBehavior creates a perfectly smooth, high-momentum scrolling physics
 * that feels buttery and less rigid than the default Android SplineBasedDecay.
 */
@Composable
fun rememberZenithFlingBehavior(): FlingBehavior {
    // A custom exponential decay with lower friction feels more physical and "heavy"
    // like a true mechanical wheel or heavy page, matching Zenith's smooth scroll aesthetic.
    val decay = remember {
        exponentialDecay<Float>(
            frictionMultiplier = 0.5f, // Lower friction for faster, longer scrolling
            absVelocityThreshold = 0.1f // Allows the fling to settle more gradually
        )
    }
    
    return remember(decay) { DecayFlingBehavior(decay) }
}

private class DecayFlingBehavior(
    private val decay: androidx.compose.animation.core.DecayAnimationSpec<Float>
) : FlingBehavior {
    override suspend fun androidx.compose.foundation.gestures.ScrollScope.performFling(initialVelocity: Float): Float {
        var velocityLeft = initialVelocity
        var lastValue = 0f
        androidx.compose.animation.core.AnimationState(
            initialValue = 0f,
            initialVelocity = initialVelocity,
        ).animateDecay(decay) {
            val delta = value - lastValue
            val consumed = scrollBy(delta)
            lastValue = value
            velocityLeft = this.velocity
            if (kotlin.math.abs(delta - consumed) > 0.5f) this.cancelAnimation()
        }
        return velocityLeft
    }
}
