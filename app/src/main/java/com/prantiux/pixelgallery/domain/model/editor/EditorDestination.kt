package com.prantiux.pixelgallery.domain.model.editor

import com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.VariableFilterTypes
import kotlinx.serialization.Serializable

@Serializable
sealed class EditorDestination {
    @Serializable
    data object Editor : EditorDestination()
    @Serializable
    data object Crop : EditorDestination()
    @Serializable
    data object Lighting : EditorDestination()
    @Serializable
    data object Filters : EditorDestination()
    @Serializable
    data object Markup : EditorDestination()
    @Serializable
    data object Colour : EditorDestination()
    @Serializable
    data object Effects : EditorDestination()
    @Serializable
    data object More : EditorDestination()
    @Serializable
    data class AdjustDetail(val adjustment: VariableFilterTypes) : EditorDestination()
    @Serializable
    data object MarkupDraw : EditorDestination()
    @Serializable
    data object CropDetail : EditorDestination()
}
