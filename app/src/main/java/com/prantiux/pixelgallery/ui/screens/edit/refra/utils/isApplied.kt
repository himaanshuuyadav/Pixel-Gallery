package com.prantiux.pixelgallery.ui.screens.edit.refra.utils

import androidx.annotation.Keep
import com.prantiux.pixelgallery.domain.model.editor.Adjustment
import com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.VariableFilterTypes

@Keep
fun List<Adjustment>.isApplied(variableFilterTypes: VariableFilterTypes): Boolean {
    return any { it.name.equals(variableFilterTypes.name, ignoreCase = true) }
}
