package com.prantiux.pixelgallery.domain.model.editor

import androidx.annotation.Keep
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.prantiux.pixelgallery.ui.icons.FontIcons
import androidx.compose.ui.res.stringResource

import kotlinx.serialization.Serializable

@Keep
@Serializable
enum class EffectTool {
    Posterize,
    Edges,
    Borders;

    @get:Composable
    val translatedName: String
        get() = when (this) {
            Posterize -> this.name
            Edges -> this.name
            Borders -> this.name
        }

    val icon: String
        get() = when (this) {
            Posterize -> FontIcons.Texture
            Edges -> FontIcons.GridOn
            Borders -> FontIcons.CropDin
        }
}
