package com.prantiux.pixelgallery.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons

/**
 * Normal header for the Photos tab.
 * Keeps the extended look but does not collapse on scroll.
 */
@Composable
fun PhotosTabHeader(
    title: String,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val height = 216.dp
    val titleSize = 45.sp
    val titleFontWeight = FontWeight(900)
    val titleBottomPadding = 46.dp
    
    val backgroundColor = MaterialTheme.colorScheme.surface
    
    // Set status bar color
    SetStatusBarColor(backgroundColor)
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = backgroundColor,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .statusBarsPadding()
        ) {
            // Content container - always anchored to bottom of app bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = titleBottomPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Title
                Text(
                    text = title,
                    fontSize = titleSize,
                    fontFamily = com.prantiux.pixelgallery.ui.theme.zenithTimeFont,
                    fontWeight = titleFontWeight,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = titleSize * 1.1f
                )
                
                // Settings icon button
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .size(48.dp)
                        .padding(bottom = 0.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            FontIcon(
                                unicode = FontIcons.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurface,
                                size = 22.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
