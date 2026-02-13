package com.prantiux.pixelgallery.ui.animation

import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive Motion System Specifications
 *
 * Centralized animation values for app-wide motion effects.
 * 
 * NOTE: Tab switching and startup animations have been removed.
 * Custom animations no longer used but motion spec retained for future use.
 */
object MotionSpec {
    
    // ========== UNUSED - TAB SWITCHING & STARTUP ANIMATIONS REMOVED ==========
    
    // Previously used for tab switching (220ms fade-through) - REMOVED
    // Previously used for startup animation (240ms entrance) - REMOVED
    
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
    fun <T> transitionSpec(durationMillis: Int = 200, easing: androidx.compose.animation.core.Easing = FastOutSlowInEasing) =
        androidx.compose.animation.core.tween<T>(durationMillis = durationMillis, easing = easing)
}
