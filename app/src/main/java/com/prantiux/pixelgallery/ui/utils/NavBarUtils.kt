package com.prantiux.pixelgallery.ui.utils

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Calculate the total height of our floating navigation bar
 * Including the 72dp pill + 16dp margin + system navigation bar insets
 */
@Composable
fun calculateFloatingNavBarHeight(): Dp {
    val density = LocalDensity.current
    val systemNavBarHeight = with(density) {
        WindowInsets.navigationBars.getBottom(density).toDp()
    }
    
    // 72dp pill + 16dp bottom margin + system nav bar
    return 72.dp + 16.dp + systemNavBarHeight
}
