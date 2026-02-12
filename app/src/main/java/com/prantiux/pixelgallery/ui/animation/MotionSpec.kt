package com.prantiux.pixelgallery.ui.animation

import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive Motion System Specifications
 *
 * Centralized animation values following Material Design 3 principles.
 * All animated properties derive from a single transition state per event.
 * Avoids multiple independent animations drifting out of sync.
 */
object MotionSpec {
    
    // ========== STARTUP ANIMATION ==========
    
    /**
     * Duration for app startup/entrance animation.
     * Runs ONCE when app content first appears.
     * Range: 220–260ms (Material 3 standard)
     */
    const val StartupDuration = 240
    
    /**
     * Easing for startup animation - smooth deceleration.
     * FastOutSlowInEasing: sharp entry, smooth exit.
     */
    val StartupEasing = FastOutSlowInEasing
    
    // Startup animation distances and values
    object Startup {
        val InitialAlpha = 0f
        val TargetAlpha = 1f
        
        val InitialTranslateY = 12.dp
        val TargetTranslateY = 0.dp
        
        val InitialScale = 0.995f
        val TargetScale = 1f
    }
    
    
    // ========== TAB SWITCHING ANIMATION ==========
    
    /**
     * Duration for tab switching animation.
     * Runs every time selectedTab changes.
     * Range: 180–220ms (Material 3 standard)
     */
    const val TabSwitchDuration = 200
    
    /**
     * Easing for tab switch - smooth deceleration.
     * Same as startup for consistent feel.
     */
    val TabSwitchEasing = FastOutSlowInEasing
    
    // Tab shift animation values
    object TabShift {
        /**
         * Distance incoming tab moves up from below.
         * Subtle, hierarchy-preserving motion.
         */
        val IncomingTranslateY = 6.dp
        
        /**
         * Distance outgoing tab moves up (fades out).
         * Subtle upward fade, not downward slide.
         */
        val OutgoingTranslateY = (-6).dp
        
        /**
         * Incoming tab starts slightly transparent, fades in.
         */
        val IncomingAlpha = 0.92f
        
        /**
         * Outgoing tab starts opaque, fades slightly out.
         */
        val OutgoingAlpha = 1f
        
        /**
         * Target alpha for outgoing tab (fading).
         */
        val OutgoingTargetAlpha = 0.92f
    }
    
    
    // ========== GENERAL MOTION PRINCIPLES ==========
    
    /**
     * All animations use GPU-friendly transforms only:
     * - Alpha (opacity)
     * - TranslateX/Y (position)
     * - Scale (size)
     *
     * NEVER use:
     * - Layout-based animation (height/width changes)
     * - Heavy recomposition during animation
     * - Nested AnimatedVisibility unless necessary
     * - Spring animations (use tween instead)
     */
    
    /**
     * Standard transition spec creator.
     * Use this for consistent timing across the app.
     */
    fun <T> transitionSpec(durationMillis: Int = TabSwitchDuration, easing: androidx.compose.animation.core.Easing = TabSwitchEasing) =
        androidx.compose.animation.core.tween<T>(durationMillis = durationMillis, easing = easing)
}
