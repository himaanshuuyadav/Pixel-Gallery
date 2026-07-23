package com.prantiux.pixelgallery.domain.model.editor

import androidx.annotation.Keep
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.prantiux.pixelgallery.ui.icons.FontIcons
import androidx.compose.ui.res.stringResource

import kotlinx.serialization.Serializable

@Keep
@Serializable
enum class LightingTool {
    Brightness,
    Tone,
    Contrast,
    BlackPoint,
    WhitePoint,
    Highlights,
    Shadows,
    Vignette;

    @get:Composable
    val translatedName: String
        get() = when (this) {
            Brightness -> this.name
            Tone -> this.name
            Contrast -> this.name
            BlackPoint -> this.name
            WhitePoint -> this.name
            Highlights -> this.name
            Shadows -> this.name
            Vignette -> this.name
        }

    val icon: String
        get() = when (this) {
            Brightness -> FontIcons.Brightness5
            Tone -> FontIcons.Tonality
            Contrast -> FontIcons.Contrast
            BlackPoint -> FontIcons.RadioButtonUnchecked
            WhitePoint -> FontIcons.Circle
            Highlights -> FontIcons.Layers
            Shadows -> FontIcons.FilterDrama
            Vignette -> FontIcons.Vignette
        }

    val allowNegative: Boolean
        get() = this != Vignette

    val minValue: Float
        get() = if (allowNegative) -1f else 0f

    val maxValue: Float
        get() = 1f

    val defaultValue: Float
        get() = 0f
}
