package com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter

import androidx.annotation.Keep
import com.prantiux.pixelgallery.ui.icons.FontIcons
import com.prantiux.pixelgallery.domain.model.editor.VariableFilter
import kotlinx.serialization.Serializable

@Keep
@Serializable
enum class VariableFilterTypes {
    // Legacy
    Brightness, Contrast, Saturation, Rotate,
    // Lighting
    Tone, BlackPoint, WhitePoint, Highlights, Shadows, Vignette,
    // Colour
    Warmth, Tint, SkinTone, BlueTone, Hue, BlackWhite,
    // Effects
    Posterize, Edges, Borders,
    // Actions
    Pop, Sharpen, Denoise;

    fun createFilter(value: Float): VariableFilter =
        when (this) {
            Brightness -> Brightness(value)
            Contrast -> Contrast(value)
            Saturation -> Saturation(value)
            Rotate -> Rotate(value)
            Tone -> com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.Tone(value)
            BlackPoint -> com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.BlackPoint(value)
            WhitePoint -> com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.WhitePoint(value)
            Highlights -> com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.Highlights(value)
            Shadows -> com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.Shadows(value)
            Vignette -> com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.Vignette(value)
            Warmth -> com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.Warmth(value)
            Tint -> com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.Tint(value)
            SkinTone -> com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.SkinTone(value)
            BlueTone -> com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.BlueTone(value)
            Hue -> com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.Hue(value)
            BlackWhite -> com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.BlackWhite(value)
            Posterize -> com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.Posterize(value)
            Edges -> com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.Edges(value)
            Borders -> com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.Borders(value)
            Pop -> com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.Pop(value)
            Sharpen -> Sharpness(value)
            Denoise -> com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.Denoise(value)
        }

    fun createDefaultFilter(): VariableFilter =
        when (this) {
            Brightness -> Brightness()
            Contrast -> Contrast()
            Saturation -> Saturation()
            Rotate -> Rotate()
            Tone -> com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.Tone()
            BlackPoint -> com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.BlackPoint()
            WhitePoint -> com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.WhitePoint()
            Highlights -> com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.Highlights()
            Shadows -> com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.Shadows()
            Vignette -> com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.Vignette()
            Warmth -> com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.Warmth()
            Tint -> com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.Tint()
            SkinTone -> com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.SkinTone()
            BlueTone -> com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.BlueTone()
            Hue -> com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.Hue()
            BlackWhite -> com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.BlackWhite()
            Posterize -> com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.Posterize()
            Edges -> com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.Edges()
            Borders -> com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.Borders()
            Pop -> com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.Pop()
            Sharpen -> Sharpness()
            Denoise -> com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.Denoise()
        }

    val icon: String get() =
        when (this) {
            Brightness -> FontIcons.Brightness5
            Contrast -> FontIcons.Contrast
            Saturation -> FontIcons.WaterDrop
            Rotate -> FontIcons.Rotate90DegreesCcw
            Tone -> FontIcons.Tonality
            BlackPoint -> FontIcons.RadioButtonUnchecked
            WhitePoint -> FontIcons.Circle
            Highlights -> FontIcons.Layers
            Shadows -> FontIcons.FilterDrama
            Vignette -> FontIcons.Vignette
            Warmth -> FontIcons.Thermostat
            Tint -> FontIcons.Palette
            SkinTone -> FontIcons.InvertColors
            BlueTone -> FontIcons.Waves
            Hue -> FontIcons.Gradient
            BlackWhite -> FontIcons.FilterBAndW
            Posterize -> FontIcons.Texture
            Edges -> FontIcons.GridOn
            Borders -> FontIcons.CropDin
            Pop -> FontIcons.Contrast
            Sharpen -> FontIcons.Details
            Denoise -> FontIcons.Brightness4
        }
}
