package com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments

import android.graphics.Bitmap
import com.prantiux.pixelgallery.domain.model.editor.Adjustment

data class Markup(val newBitmap: Bitmap): Adjustment {

    override fun apply(bitmap: Bitmap): Bitmap {
        return newBitmap
    }

}
