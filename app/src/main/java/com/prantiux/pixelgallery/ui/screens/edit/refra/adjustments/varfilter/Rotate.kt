package com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ColorMatrix
import com.prantiux.pixelgallery.domain.model.editor.VariableFilter
import com.prantiux.pixelgallery.ui.screens.edit.refra.util.rotate

data class Rotate(
    override val value: Float = 0f
) : VariableFilter {

    override val maxValue = 180f
    override val minValue = -180f
    override val defaultValue = 0f

    override fun revert(bitmap: Bitmap): Bitmap = bitmap.rotate(-value)

    override fun colorMatrix(): ColorMatrix? = null

    override fun apply(bitmap: Bitmap): Bitmap = bitmap.rotate(value)

}
