package com.prantiux.pixelgallery.ui.screens.edit.refra.components.adjustment

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.prantiux.pixelgallery.domain.model.editor.Adjustment
import com.prantiux.pixelgallery.domain.model.editor.VariableFilter
import com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.varfilter.VariableFilterTypes
import com.prantiux.pixelgallery.ui.screens.edit.refra.components.core.HorizontalScrubber
import com.prantiux.pixelgallery.ui.screens.edit.refra.components.core.VerticalScrubber
import com.prantiux.pixelgallery.ui.icons.FontIcons
import com.prantiux.pixelgallery.ui.icons.MaterialSymbolsRounded
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun AdjustScrubber(
    adjustment: VariableFilterTypes,
    appliedAdjustments: List<Adjustment> = emptyList(),
    modifier: Modifier = Modifier,
    displayValue: (Float) -> String = { (it * 100f).roundToInt().toString() },
    onAdjustmentPreview: (Adjustment) -> Unit = {},
    onAdjustmentChange: (Adjustment) -> Unit = {},
    onCancel: () -> Unit = {},
    onDone: (Adjustment) -> Unit = {},
    isSupportingPanel: Boolean,
) {
    val defaultAdjustment = remember(adjustment, appliedAdjustments) {
        (appliedAdjustments.findLast { it.name.equals(adjustment.name, ignoreCase = true) } as VariableFilter?)
            ?: adjustment.createDefaultFilter()
    }
    var currentAdjustment by remember(defaultAdjustment) {
        mutableStateOf(defaultAdjustment)
    }
    var currentValue by rememberSaveable(currentAdjustment, appliedAdjustments) {
        mutableFloatStateOf(currentAdjustment.value)
    }
    val handleCancel = {
        val applied = appliedAdjustments.findLast {
            it.name.equals(adjustment.name, ignoreCase = true)
        } as VariableFilter?
        
        if (applied != null) {
            onAdjustmentPreview(applied)
        } else {
            onAdjustmentPreview(adjustment.createDefaultFilter())
        }
        onCancel()
    }
    
    BackHandler {
        handleCancel()
    }

    if (isSupportingPanel) {
        Box(modifier = modifier.fillMaxWidth(0.5f), contentAlignment = Alignment.Center) {
            VerticalScrubber(
                minValue = defaultAdjustment.minValue * 100f,
                maxValue = defaultAdjustment.maxValue * 100f,
                defaultValue = defaultAdjustment.defaultValue * 100f,
                allowNegative = defaultAdjustment.minValue < 0f,
                currentValue = currentValue * 100f,
                displayValue = { it.roundToInt().toString() },
                onValueChanged = { _, newValue ->
                    currentValue = newValue / 100f
                    currentAdjustment = adjustment.createFilter(newValue / 100f)
                    onAdjustmentPreview(currentAdjustment)
                }
            )
        }
    } else {
        Column(modifier = modifier) {
            HorizontalScrubber(
                modifier = Modifier,
                sharedTransitionKey = adjustment.name,
                icon = adjustment.icon,
                minValue = defaultAdjustment.minValue * 100f,
                maxValue = defaultAdjustment.maxValue * 100f,
                defaultValue = defaultAdjustment.defaultValue * 100f,
                allowNegative = defaultAdjustment.minValue < 0f,
                currentValue = currentValue * 100f,
                displayValue = { it.roundToInt().toString() },
                onReset = {
                    val defaultVal = defaultAdjustment.defaultValue
                    currentValue = defaultVal
                    currentAdjustment = adjustment.createFilter(defaultVal)
                    onAdjustmentPreview(currentAdjustment)
                },
                onValueChanged = { _, newValue ->
                    currentValue = newValue / 100f
                    currentAdjustment = adjustment.createFilter(newValue / 100f)
                    onAdjustmentPreview(currentAdjustment)
                }
            )
            
            val isChanged = currentValue != defaultAdjustment.defaultValue

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = { handleCancel() },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(width = 64.dp, height = 48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = FontIcons.Close,
                            fontFamily = MaterialSymbolsRounded,
                            fontSize = 24.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Text(
                    text = adjustment.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Surface(
                    onClick = { onDone(currentAdjustment) },
                    enabled = isChanged,
                    shape = CircleShape,
                    color = if (isChanged) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(width = 64.dp, height = 48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = FontIcons.Done,
                            fontFamily = MaterialSymbolsRounded,
                            fontSize = 24.sp,
                            color = if (isChanged) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}
