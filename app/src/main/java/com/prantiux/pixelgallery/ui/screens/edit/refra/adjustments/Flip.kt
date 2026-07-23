package com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments

import android.graphics.Bitmap
import com.prantiux.pixelgallery.domain.model.editor.Adjustment
import com.prantiux.pixelgallery.ui.screens.edit.refra.util.flipHorizontally
import com.prantiux.pixelgallery.ui.screens.edit.refra.util.flipVertically

data class Flip(
    val horizontal: Boolean,
) : Adjustment {

    override fun apply(bitmap: Bitmap): Bitmap {
        return if (horizontal) bitmap.flipHorizontally() else bitmap.flipVertically()
    }

}
