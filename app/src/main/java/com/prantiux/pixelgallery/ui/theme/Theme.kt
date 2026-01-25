@file:OptIn(ExperimentalMaterial3Api::class)

package com.prantiux.pixelgallery.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext

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
    amoledMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    
    // Log theme parameters
    androidx.compose.runtime.LaunchedEffect(darkTheme, dynamicColor, amoledMode) {
        android.util.Log.d("PixelGalleryTheme", "=== THEME APPLIED ===")
        android.util.Log.d("PixelGalleryTheme", "darkTheme: $darkTheme")
        android.util.Log.d("PixelGalleryTheme", "dynamicColor: $dynamicColor")
        android.util.Log.d("PixelGalleryTheme", "amoledMode: $amoledMode")
        android.util.Log.d("PixelGalleryTheme", "Android version: ${Build.VERSION.SDK_INT}")
    }
    
    // Prioritize dynamic colors from Android 12+ system
    val baseColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            try {
                val scheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                android.util.Log.d("PixelGalleryTheme", "Using DYNAMIC color scheme (${if (darkTheme) "dark" else "light"})")
                android.util.Log.d("PixelGalleryTheme", "Dynamic surface color: #${Integer.toHexString(scheme.surface.toArgb())}")
                scheme
            } catch (e: Exception) {
                // Fallback if dynamic colors fail
                android.util.Log.w("PixelGalleryTheme", "Dynamic colors failed, using static scheme", e)
                if (darkTheme) DarkColorScheme else LightColorScheme
            }
        }
        darkTheme -> {
            android.util.Log.d("PixelGalleryTheme", "Using STATIC DARK color scheme")
            DarkColorScheme
        }
        else -> {
            android.util.Log.d("PixelGalleryTheme", "Using STATIC LIGHT color scheme")
            LightColorScheme
        }
    }
    
    // Apply AMOLED mode (pure black) if enabled in dark theme
    val colorScheme = if (darkTheme && amoledMode) {
        android.util.Log.d("PixelGalleryTheme", "Applying AMOLED mode (pure black)")
        baseColorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF1A1A1A),
            surfaceContainer = Color(0xFF0D0D0D),
            surfaceContainerLow = Color(0xFF050505),
            surfaceContainerLowest = Color.Black,
            surfaceContainerHigh = Color(0xFF1F1F1F),
            surfaceContainerHighest = Color(0xFF2A2A2A)
        )
    } else {
        android.util.Log.d("PixelGalleryTheme", "Using base color scheme (surface: #${Integer.toHexString(baseColorScheme.surface.toArgb())})")
        android.util.Log.d("PixelGalleryTheme", "====================")
        baseColorScheme
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
