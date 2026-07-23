package com.prantiux.pixelgallery.ui.screens.edit.refra.components.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.prantiux.pixelgallery.ui.screens.edit.refra.components.core.HorizontalScrubber
import com.prantiux.pixelgallery.ui.screens.edit.refra.components.core.VerticalScrubber
import com.prantiux.pixelgallery.ui.icons.FontIcons
import com.prantiux.pixelgallery.ui.icons.MaterialSymbolsRounded
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun CropScrubber(
    modifier: Modifier = Modifier,
    isSupportingPanel: Boolean,
    currentValue: Float,
    onValueChanged: (Float) -> Unit,
    onCancel: () -> Unit = {},
    onDone: () -> Unit = {}
) {
    BackHandler {
        onCancel()
    }

    if (isSupportingPanel) {
        Box(modifier = modifier.fillMaxWidth(0.5f), contentAlignment = Alignment.Center) {
            VerticalScrubber(
                minValue = -45f,
                maxValue = 45f,
                defaultValue = 0f,
                allowNegative = true,
                currentValue = currentValue,
                displayValue = { "${it.roundToInt()}°" },
                onValueChanged = { _, newValue ->
                    onValueChanged(newValue)
                }
            )
        }
    } else {
        Column(modifier = modifier) {
            HorizontalScrubber(
                modifier = Modifier,
                sharedTransitionKey = "CropScrubber",
                icon = FontIcons.RotateRight, // Using RotateRight as closest match to crop rotation
                minValue = -45f,
                maxValue = 45f,
                defaultValue = 0f,
                allowNegative = true,
                currentValue = currentValue,
                displayValue = { "${it.roundToInt()}°" },
                onReset = {
                    onValueChanged(0f)
                },
                onValueChanged = { _, newValue ->
                    onValueChanged(newValue)
                }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = { onCancel() },
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
                    text = "Straighten",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Surface(
                    onClick = { onDone() },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(width = 64.dp, height = 48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = FontIcons.Check,
                            fontFamily = MaterialSymbolsRounded,
                            fontSize = 24.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}
