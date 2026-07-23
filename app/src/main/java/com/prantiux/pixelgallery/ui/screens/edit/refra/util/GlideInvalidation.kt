package com.prantiux.pixelgallery.ui.screens.edit.refra.util

import com.bumptech.glide.load.Key
import com.bumptech.glide.signature.ObjectKey

object GlideInvalidation {

    fun <T> signature(obj: T): Key {
        return ObjectKey(obj.toString())
    }
}
