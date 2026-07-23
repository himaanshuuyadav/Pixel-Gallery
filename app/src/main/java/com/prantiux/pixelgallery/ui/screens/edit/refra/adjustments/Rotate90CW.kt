package com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments

import android.graphics.Bitmap
import com.prantiux.pixelgallery.domain.model.editor.Adjustment
import com.prantiux.pixelgallery.ui.screens.edit.refra.util.rotate

data class Rotate90CW(
    val angle: Float
) : Adjustment {

    override fun apply(bitmap: Bitmap): Bitmap {
        return bitmap.rotate(angle)
    }

}
