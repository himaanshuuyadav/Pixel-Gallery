@file:OptIn(ExperimentalMaterial3Api::class)

package com.prantiux.pixelgallery.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

val LocalPixelGalleryDarkTheme = staticCompositionLocalOf { false }

private val DarkColorScheme = darkColorScheme(
    primary = ExpressivePrimaryDark,
    onPrimary = ExpressiveOnPrimaryDark,
    primaryContainer = ExpressivePrimaryContainerDark,
    onPrimaryContainer = ExpressiveOnPrimaryContainerDark,
    secondary = ExpressiveSecondaryDark,
    onSecondary = ExpressiveOnSecondaryDark,
    secondaryContainer = ExpressiveSecondaryContainerDark,
    onSecondaryContainer = ExpressiveOnSecondaryContainerDark,
    tertiary = ExpressiveTertiaryDark,
    onTertiary = ExpressiveOnTertiaryDark,
    tertiaryContainer = ExpressiveTertiaryContainerDark,
    onTertiaryContainer = ExpressiveOnTertiaryContainerDark,
    error = ExpressiveErrorDark,
    onError = ExpressiveOnErrorDark,
    errorContainer = ExpressiveErrorContainerDark,
    onErrorContainer = ExpressiveOnErrorContainerDark,
    background = ExpressiveBackgroundDark,
    onBackground = ExpressiveOnBackgroundDark,
    surface = ExpressiveSurfaceDark,
    onSurface = ExpressiveOnSurfaceDark,
    surfaceVariant = ExpressiveSurfaceVariantDark,
    onSurfaceVariant = ExpressiveOnSurfaceVariantDark,
    surfaceTint = ExpressiveSurfaceTintDark,
    inverseSurface = ExpressiveInverseSurfaceDark,
    inverseOnSurface = ExpressiveInverseOnSurfaceDark,
    inversePrimary = ExpressiveInversePrimaryDark,
    outline = ExpressiveOutlineDark,
    outlineVariant = ExpressiveOutlineVariantDark,
    scrim = ExpressiveScrimDark,
    surfaceContainerLowest = ExpressiveSurfaceContainerLowestDark,
    surfaceContainerLow = ExpressiveSurfaceContainerLowDark,
    surfaceContainer = ExpressiveSurfaceContainerDark,
    surfaceContainerHigh = ExpressiveSurfaceContainerHighDark,
    surfaceContainerHighest = ExpressiveSurfaceContainerHighestDark
)

private val LightColorScheme = lightColorScheme(
    primary = ExpressivePrimaryLight,
    onPrimary = ExpressiveOnPrimaryLight,
    primaryContainer = ExpressivePrimaryContainerLight,
    onPrimaryContainer = ExpressiveOnPrimaryContainerLight,
    secondary = ExpressiveSecondaryLight,
    onSecondary = ExpressiveOnSecondaryLight,
    secondaryContainer = ExpressiveSecondaryContainerLight,
    onSecondaryContainer = ExpressiveOnSecondaryContainerLight,
    tertiary = ExpressiveTertiaryLight,
    onTertiary = ExpressiveOnTertiaryLight,
    tertiaryContainer = ExpressiveTertiaryContainerLight,
    onTertiaryContainer = ExpressiveOnTertiaryContainerLight,
    error = ExpressiveErrorLight,
    onError = ExpressiveOnErrorLight,
    errorContainer = ExpressiveErrorContainerLight,
    onErrorContainer = ExpressiveOnErrorContainerLight,
    background = ExpressiveBackgroundLight,
    onBackground = ExpressiveOnBackgroundLight,
    surface = ExpressiveSurfaceLight,
    onSurface = ExpressiveOnSurfaceLight,
    surfaceVariant = ExpressiveSurfaceVariantLight,
    onSurfaceVariant = ExpressiveOnSurfaceVariantLight,
    surfaceTint = ExpressiveSurfaceTintLight,
    inverseSurface = ExpressiveInverseSurfaceLight,
    inverseOnSurface = ExpressiveInverseOnSurfaceLight,
    inversePrimary = ExpressiveInversePrimaryLight,
    outline = ExpressiveOutlineLight,
    outlineVariant = ExpressiveOutlineVariantLight,
    scrim = ExpressiveScrimLight
)

@Composable
fun PixelGalleryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    
    // Prioritize dynamic colors from Android 12+ system
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            try {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } catch (e: Exception) {
                // Fallback if dynamic colors fail
                if (darkTheme) DarkColorScheme else LightColorScheme
            }
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    CompositionLocalProvider(LocalPixelGalleryDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}
