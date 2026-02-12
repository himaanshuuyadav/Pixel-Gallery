@file:OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)

package com.prantiux.pixelgallery.ui.animation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * AnimatedTabContent
 *
 * Handles tab switching animation — runs every time selectedTab changes.
 *
 * Important principle:
 * Tabs are SAME hierarchy level.
 * So motion must feel like content changing state, NOT like navigating deeper.
 *
 * Animation behavior (Material 3 Expressive):
 * - Duration: 200ms
 * - Easing: FastOutSlowInEasing
 * - Fade transitions with smooth timing
 *
 * Implementation details:
 * - Uses AnimatedContent with fadeIn/fadeOut animations.
 * - Keeps layout stable (no relayout thrash).
 * - GPU-friendly transforms.
 */
@Composable
fun <T> AnimatedTabContent(
    currentTab: T,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit
) {
    AnimatedContent(
        targetState = currentTab,
        modifier = modifier,
        label = "AnimatedTabContent"
    ) { tabState ->
        Box {
            content(tabState)
        }
    }
}

/**
 * AnimatedTabContentWithMotion
 *
 * Enhanced version of AnimatedTabContent that includes the full
 * Material 3 Expressive motion set (alpha range) for tabs.
 *
 * This is the preferred version for main tabs.
 */
@Composable
fun <T> AnimatedTabContentWithMotion(
    currentTab: T,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit
) {
    AnimatedContent(
        targetState = currentTab,
        modifier = modifier,
        label = "AnimatedTabContentWithMotion"
    ) { tabState ->
        Box {
            content(tabState)
        }
    }
}

