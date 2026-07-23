package com.prantiux.pixelgallery.domain.model.editor

import android.graphics.Bitmap
import androidx.annotation.Keep


@Keep
interface Adjustment {
    fun apply(bitmap: Bitmap): Bitmap

    val name: String get() = this::class.simpleName.toString()

}
