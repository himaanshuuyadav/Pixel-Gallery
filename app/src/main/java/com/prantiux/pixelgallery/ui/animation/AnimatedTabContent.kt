package com.prantiux.pixelgallery.ui.animation

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * AnimatedTabContent
 *
 * Simple tab content container — no custom animation, uses default AnimatedContent behavior.
 * Tab switching renders immediately without transitions.
 */
@Composable
fun <T> AnimatedTabContent(
    currentTab: T,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit
) {
    // Simple content container - no custom animation
    Box(modifier = modifier) {
        content(currentTab)
    }
}

/**
 * AnimatedTabContentWithMotion
 *
 * Simple tab content container — no custom animation, instant content swap.
 * Main tabs (Photos, Albums, Search) render immediately without transitions.
 */
@Composable
fun <T> AnimatedTabContentWithMotion(
    currentTab: T,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit
) {
    // Simple content container - no custom animation
    Box(modifier = modifier) {
        content(currentTab)
    }
}

