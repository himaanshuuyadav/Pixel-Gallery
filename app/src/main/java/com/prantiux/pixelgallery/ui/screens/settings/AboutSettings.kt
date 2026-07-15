package com.prantiux.pixelgallery.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prantiux.pixelgallery.ui.icons.FontIcon
import com.prantiux.pixelgallery.ui.icons.FontIcons

@Composable
fun AboutSettings(
    onNavigateToDebug: () -> Unit
) {
    val context = LocalContext.current
    
    // In a real app you'd get this from BuildConfig, using a fallback for now.
    val versionName = "1.0.0" 
    
    var developerModeEnabled by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        CategoryHeader("About", topPadding = 0.dp)

        AppInfoCard(
            versionName = versionName,
            developerModeEnabled = developerModeEnabled,
            onDeveloperModeChange = { developerModeEnabled = it },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
        )

        DeveloperCard(
            name = "Prantiux",
            onGithubClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com"))
                context.startActivity(intent)
            },
            onWebsiteClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
                context.startActivity(intent)
            },
            onChatClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://whatsapp.com"))
                context.startActivity(intent)
            },
            shape = if (developerModeEnabled) RoundedCornerShape(8.dp) else RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        )

        if (developerModeEnabled) {
            AboutActionCard(
                title = "Debug",
                subtitle = "Developer tools",
                iconUnicode = FontIcons.Settings,
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
                onClick = onNavigateToDebug
            )
        }
    }
}

@Composable
fun AppInfoCard(
    versionName: String,
    developerModeEnabled: Boolean,
    onDeveloperModeChange: (Boolean) -> Unit,
    shape: Shape = RoundedCornerShape(24.dp)
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var clickCount by remember { mutableIntStateOf(0) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable {
                clickCount++
                if (!developerModeEnabled) {
                    if (clickCount >= 3) {
                        onDeveloperModeChange(true)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        Toast.makeText(context, "Developer mode enabled!", Toast.LENGTH_SHORT).show()
                        clickCount = 0
                    } else {
                        val remaining = 3 - clickCount
                        Toast.makeText(context, "Tap $remaining more times for Developer Mode", Toast.LENGTH_SHORT).show()
                    }
                }
            },
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    FontIcon(
                        unicode = FontIcons.Image,
                        contentDescription = null,
                        size = 32.sp,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(20.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Pixel Gallery",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = CircleShape
                ) {
                    Text(
                        text = "v$versionName",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun DeveloperCard(
    name: String,
    onGithubClick: () -> Unit,
    onWebsiteClick: () -> Unit,
    onChatClick: () -> Unit,
    shape: Shape
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                FontIcon(
                    unicode = FontIcons.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    size = 20.sp
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Text(
                text = "Created by $name",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                QuickLinkButton(
                    iconUnicode = FontIcons.Settings, // Placeholder for Code/Github
                    shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = 6.dp, bottomEnd = 6.dp),
                    onClick = onGithubClick
                )
                QuickLinkButton(
                    iconUnicode = FontIcons.Info, // Placeholder for Website/Language
                    shape = RoundedCornerShape(6.dp),
                    onClick = onWebsiteClick
                )
                QuickLinkButton(
                    iconUnicode = FontIcons.Feedback, // Placeholder for Chat
                    shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp, topStart = 6.dp, bottomStart = 6.dp),
                    onClick = onChatClick
                )
            }
        }
    }
}

@Composable
fun AboutActionCard(
    title: String,
    subtitle: String = "",
    iconUnicode: String,
    shape: Shape,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        val isPressed by interactionSource.collectIsPressedAsState()
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FontIcon(
                unicode = iconUnicode,
                contentDescription = null,
                size = 24.sp,
                tint = MaterialTheme.colorScheme.primary,
                filled = isPressed
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            FontIcon(
                unicode = FontIcons.KeyboardArrowRight,
                contentDescription = null,
                size = 24.sp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuickLinkButton(
    iconUnicode: String,
    shape: Shape,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "quickLinkScale"
    )

    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        interactionSource = interactionSource,
        modifier = Modifier
            .size(width = 44.dp, height = 40.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            FontIcon(
                unicode = iconUnicode,
                contentDescription = null,
                size = 18.sp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
