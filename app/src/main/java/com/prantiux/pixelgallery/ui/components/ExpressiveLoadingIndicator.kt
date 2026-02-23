package com.prantiux.pixelgallery.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prantiux.pixelgallery.R

/**
 * Material 3 Expressive Loading Indicator
 * 
 * Uses the NEW androidx.compose.material3.LoadingIndicator that morphs between shapes.
 * This provides smooth, expressive motion that aligns with Material 3 design principles.
 * 
 * USAGE RULES (Material 3 Expressive):
 * - Use for SHORT operations (100ms - 5s)
 * - Do NOT use for instant operations (<100ms)
 * - Do NOT use for determinate progress (use LinearProgressIndicator instead)
 * - Do NOT use for pull-to-refresh
 * 
 * TIMING:
 * - Show only after ~100ms delay to prevent flicker on fast operations
 * - Hide immediately when operation completes
 * 
 * @param modifier Modifier for the loading indicator
 * @param size Size of the indicator (default 48.dp)
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    // Material 3 Expressive LoadingIndicator with default shape morphing
    // Uses built-in expressive motion and shape transitions
    LoadingIndicator(
        modifier = modifier.size(size),
        color = MaterialTheme.colorScheme.primary
    )
}

/**
 * Echo-style Loading Indicator
 * 
 * Mirrors the echo app's loading layout with a larger indicator and label.
 */
@Composable
fun EchoLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    label: String = stringResource(R.string.loading)
) {
    Column(
        modifier = modifier.padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ExpressiveLoadingIndicator(size = size)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 22.sp),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 240.dp)
        )
    }
}

/**
 * Material 3 Expressive Loading Indicator with Surface Container
 * 
 * Wraps the LoadingIndicator in a circular Surface with subtle elevation.
 * Provides better visual separation on busy backgrounds.
 * 
 * WHEN TO USE:
 * - When loading indicator appears over complex content
 * - When you need better contrast/visibility
 * - For centered full-screen loading states
 * 
 * @param modifier Modifier for the container
 * @param size Size of the indicator (default 64.dp including padding)
 */
@Composable
fun ContainedExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp
) {
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        tonalElevation = 2.dp
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            ExpressiveLoadingIndicator(
                size = size * 0.6f // 60% of container size
            )
        }
    }
}
