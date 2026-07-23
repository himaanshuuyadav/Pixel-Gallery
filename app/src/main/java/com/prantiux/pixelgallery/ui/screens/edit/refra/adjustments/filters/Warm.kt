package com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.filters

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ColorMatrix
import com.prantiux.pixelgallery.domain.model.editor.ImageFilter
import com.prantiux.pixelgallery.ui.screens.edit.refra.util.applyColorMatrix

data class Warm(override val name: String = "Warm") : ImageFilter {

    override fun colorMatrix(): ColorMatrix = ColorMatrix(
        floatArrayOf(
            1.2f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 0.8f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )

    override fun apply(bitmap: Bitmap): Bitmap =
        applyColorMatrix(bitmap, colorMatrix().values)
}
