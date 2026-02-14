@file:OptIn(ExperimentalMaterial3Api::class)

package com.prantiux.pixelgallery.ui.components

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons

private const val TAG = "SystemBars"

/**
 * Sets the status bar color to match the provided header background color
 * and adjusts icon colors based on luminance
 */
@Composable
fun SetStatusBarColor(color: Color) {
    val view = LocalView.current
    
    SideEffect {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val colorArgb = color.toArgb()
            
            // Set status bar color to match header
            @Suppress("DEPRECATION")
            window.statusBarColor = colorArgb
            
            // Calculate luminance to determine if icons should be light or dark
            val isLightStatusBar = ColorUtils.calculateLuminance(colorArgb) > 0.55
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isLightStatusBar
        }
    }
}

/**
 * Main tab header component with large title
 * Used for Photos, Albums, Search, Settings screens
 */
@Composable
fun MainTabHeader(
    title: String,
    modifier: Modifier = Modifier,
    actions: (@Composable () -> Unit)? = null
) {
    val headerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    
    // Set status bar to match header background
    SetStatusBarColor(headerColor)
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = headerColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (actions != null) {
                    actions()
                }
            }
        }
    }
}

/**
 * Consistent header component used across detail screens
 * Smaller font size for detail pages, aligned back button
 */
@Composable
fun ConsistentHeader(
    title: String,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val headerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    
    // Set status bar to match header background
    SetStatusBarColor(headerColor)
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = headerColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button if navigation is provided
            if (onNavigateBack != null) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(40.dp)
                ) {
                    FontIcon(
                        unicode = FontIcons.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
            
            // Title with smaller font
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
    }
}
