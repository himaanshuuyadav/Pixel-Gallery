package com.prantiux.pixelgallery.domain.model.editor

import android.os.Parcelable
import androidx.compose.runtime.Composable
import com.prantiux.pixelgallery.ui.icons.FontIcons
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
enum class MarkupItems : Parcelable {
    Stylus,
    Highlighter,
    Marker,
    Text,
    Eraser,
    Pan;

    @get:Composable
    val translatedName get() = when (this) {
        Stylus -> "Stylus"
        Highlighter -> "Highlighter"
        Marker -> "Marker"
        Text -> "Text"
        Eraser -> "Erase"
        Pan -> "Pan"
    }

    @IgnoredOnParcel
    val icon: String
        get() = when (this) {
            Stylus -> FontIcons.Edit
            Highlighter -> FontIcons.Brush
            Marker -> FontIcons.Edit
            Text -> FontIcons.TextFields
            Eraser -> FontIcons.Delete
            Pan -> FontIcons.PanTool
        }
}
