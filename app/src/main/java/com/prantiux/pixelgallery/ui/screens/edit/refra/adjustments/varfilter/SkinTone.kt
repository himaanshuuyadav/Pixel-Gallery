package com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter

import android.graphics.Bitmap
import androidx.annotation.FloatRange
import androidx.compose.ui.graphics.ColorMatrix
import com.prantiux.pixelgallery.domain.model.editor.VariableFilter
import com.prantiux.pixelgallery.ui.screens.edit.refra.util.applyColorMatrix

data class SkinTone(
    @param:FloatRange(from = -1.0, to = 1.0)
    override val value: Float = 0f
) : VariableFilter {
    override val maxValue = 1f
    override val minValue = -1f
    override val defaultValue = 0f

    override fun apply(bitmap: Bitmap): Bitmap {
        return applyColorMatrix(bitmap, colorMatrix().values)
    }

    override fun revert(bitmap: Bitmap): Bitmap = SkinTone(-value).apply(bitmap)

    override fun colorMatrix(): ColorMatrix {
        // Adjusts red-green balance in midtones to affect skin/flesh tones
        val v = value
        return ColorMatrix(floatArrayOf(
            1f + v * 0.15f, 0f, 0f, 0f, v * 8f,
            0f, 1f + v * 0.05f, 0f, 0f, 0f,
            0f, 0f, 1f - v * 0.05f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
    }
}
