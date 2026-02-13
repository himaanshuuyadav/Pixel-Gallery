package com.prantiux.pixelgallery.ui.animation

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * AnimatedRootContent
 *
 * Root content container — renders immediately without animation.
 * App startup shows content instantly.
 */
@Composable
fun AnimatedRootContent(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        content()
    }
}
