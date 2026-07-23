package com.prantiux.pixelgallery.domain.model.editor

import androidx.annotation.Keep
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.prantiux.pixelgallery.ui.icons.FontIcons
import androidx.compose.ui.res.stringResource

import kotlinx.serialization.Serializable

@Keep
@Serializable
enum class ColourTool {
    Saturation,
    Warmth,
    Tint,
    SkinTone,
    BlueTone,
    Hue,
    BlackWhite;

    @get:Composable
    val translatedName: String
        get() = when (this) {
            Saturation -> this.name
            Warmth -> this.name
            Tint -> this.name
            SkinTone -> this.name
            BlueTone -> this.name
            Hue -> this.name
            BlackWhite -> this.name
        }

    val icon: String
        get() = when (this) {
            Saturation -> FontIcons.WaterDrop
            Warmth -> FontIcons.Thermostat
            Tint -> FontIcons.Palette
            SkinTone -> FontIcons.InvertColors
            BlueTone -> FontIcons.Waves
            Hue -> FontIcons.Gradient
            BlackWhite -> FontIcons.FilterBAndW
        }

    val minValue: Float get() = -1f
    val maxValue: Float get() = 1f
    val defaultValue: Float get() = 0f
}
