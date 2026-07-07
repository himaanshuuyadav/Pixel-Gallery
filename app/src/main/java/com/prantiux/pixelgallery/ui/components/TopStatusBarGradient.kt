package com.prantiux.pixelgallery.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
fun TopStatusBarGradient(
    modifier: Modifier = Modifier
) {
    val isDarkTheme = isSystemInDarkTheme()
    val gradientColor = if (isDarkTheme) {
        Color.Black
    } else {
        Color.White
    }

    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topExtendedHeight = statusBarInset + 40.dp
    val density = LocalDensity.current
    val topTotalHeightPx = with(density) { topExtendedHeight.toPx() }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(topExtendedHeight)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        gradientColor,
                        gradientColor,
                        gradientColor.copy(alpha = 0.85f),
                        gradientColor.copy(alpha = 0.6f),
                        gradientColor.copy(alpha = 0.3f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = topTotalHeightPx
                )
            )
    )
}
