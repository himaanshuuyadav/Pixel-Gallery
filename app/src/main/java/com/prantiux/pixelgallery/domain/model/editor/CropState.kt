package com.prantiux.pixelgallery.domain.model.editor

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Immutable
@Serializable
@Parcelize
data class CropState(
    val isCropping: Boolean = false,
    val showCropper: Boolean = false,
    val hasCropChanged: Boolean = false,
    val requestSave: Boolean = false
) : Parcelable
