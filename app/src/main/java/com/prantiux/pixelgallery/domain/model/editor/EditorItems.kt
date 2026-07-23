package com.prantiux.pixelgallery.domain.model.editor;

import android.os.Parcelable
import androidx.annotation.Keep
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.prantiux.pixelgallery.ui.icons.FontIcons
import androidx.compose.ui.res.stringResource

import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Keep
@Serializable
@Parcelize
enum class EditorItems : Parcelable {
    Crop,
    Lighting,
    Filters,
    Markup,
    Colour,
    Effects,
    More;

    @get:Composable
    val translatedName : String
        get() = when (this) {
            Crop -> "Crop"
            Lighting -> this.name
            Filters -> this.name
            Markup -> this.name
            Colour -> this.name
            Effects -> this.name
            More -> this.name
        }

    @IgnoredOnParcel
    val icon: String
        get() = when (this) {
            Crop -> FontIcons.Crop
            Lighting -> FontIcons.WbSunny
            Filters -> FontIcons.Filter
            Markup -> FontIcons.Draw
            Colour -> FontIcons.Palette
            Effects -> FontIcons.AutoFixHigh
            More -> FontIcons.MoreHoriz
        }
}
