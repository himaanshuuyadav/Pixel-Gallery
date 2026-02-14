package com.prantiux.pixelgallery.ui.animation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * AnimatedRootContent
 *
 * PixelPlayer-inspired transition for app startup entrance.
 * Runs ONCE on first composition, never re-triggers.
 * 
 * Specification (from PixelPlayer-master app):
 * - Duration: 500ms
 * - Easing: Tween (linear progression)
 * - Content: slideInVertically (5% from bottom) + slideInHorizontally (5% from left) + fadeIn + scaleIn (0.95f → 1.0f)
 * - Complex multi-axis animation with subtle scale effect
 */
@Composable
fun AnimatedRootContent(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    
    // Trigger entrance animation once on first composition
    LaunchedEffect(Unit) {
        visible = true
    }
    
    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                animationSpec = tween(500),
                initialOffsetY = { it / 20 }
            ) + slideInHorizontally(
                animationSpec = tween(500),
                initialOffsetX = { -it / 20 }
            ) + fadeIn(
                animationSpec = tween(500)
            ) + scaleIn(
                animationSpec = tween(500),
                initialScale = 0.95f
            )
        ) {
            content()
        }
    }
}
