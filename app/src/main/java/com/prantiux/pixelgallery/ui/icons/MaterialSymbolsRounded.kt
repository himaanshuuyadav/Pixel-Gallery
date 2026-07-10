@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.prantiux.pixelgallery.ui.icons

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.prantiux.pixelgallery.R

import androidx.compose.ui.text.font.FontVariation

/**
 * Material Symbols Rounded FontFamily
 * 
 * This font family references the Google Material Symbols Rounded font file
 * located at res/font/material_symbols_rounded.ttf
 * 
 * Use this with the FontIcon composable to render font-based icons.
 */
val MaterialSymbolsRounded = FontFamily(
    Font(R.font.material_symbols_rounded, FontWeight.Normal)
)

val MaterialSymbolsRoundedFilled = FontFamily(
    Font(
        R.font.material_symbols_rounded, 
        FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.Setting("FILL", 1f))
    )
)
