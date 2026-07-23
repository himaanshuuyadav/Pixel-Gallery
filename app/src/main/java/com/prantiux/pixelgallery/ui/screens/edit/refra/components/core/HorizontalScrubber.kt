package com.prantiux.pixelgallery.ui.screens.edit.refra.components.core

import androidx.annotation.FloatRange
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import android.view.HapticFeedbackConstants
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.sp
import com.prantiux.pixelgallery.ui.icons.FontIcons
import com.prantiux.pixelgallery.ui.icons.MaterialSymbolsRounded
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import com.prantiux.pixelgallery.ui.screens.edit.refra.components.editor.LocalSharedTransitionScope
import com.prantiux.pixelgallery.ui.screens.edit.refra.components.editor.LocalAnimatedVisibilityScope

val LocalScrubberInteraction = compositionLocalOf { mutableStateOf(false) }

@Composable
fun HorizontalScrubber(
    modifier: Modifier = Modifier,
    icon: String? = null,
    sharedTransitionKey: String? = null,
    allowNegative: Boolean = true,
    spacerWidth: Dp = 6.dp,
    normalWidth: Dp = 2.dp,
    normalHeight: Dp = 20.dp,
    arrowHeight: Dp = 32.dp,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    normalColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
    highlightedColor: Color = MaterialTheme.colorScheme.onSurface,
    arrowColor: Color = MaterialTheme.colorScheme.primary,
    @FloatRange(from = 0.0, to = 1.0)
    horizontalFade: Float = 0.3f,
    minValue: Float = -40f,
    maxValue: Float = 40f,
    defaultValue: Float = 0f,
    currentValue: Float = defaultValue,
    displayValue: (Float) -> String = { (it * 10f).roundToInt().toString() },
    onReset: () -> Unit = {},
    onValueChanged: (isScrolling: Boolean, newValue: Float) -> Unit
) {
    require(minValue < maxValue) { "minValue() should be less than maxValue()" }
    val clampedCurrentValue = currentValue.coerceIn(minValue, maxValue)

    var currentValueInternal by rememberSaveable { mutableFloatStateOf(clampedCurrentValue) }
    var isDragging by remember { mutableStateOf(false) }
    val view = LocalView.current
    val density = LocalDensity.current

    // To shorten travel distance, we multiply drag delta.
    val dragSensitivity = 1.5f 
    
    val scrubberInteraction = LocalScrubberInteraction.current

    LaunchedEffect(clampedCurrentValue) {
        if (!isDragging) {
            currentValueInternal = clampedCurrentValue
        }
    }

    var lastHapticValue by remember { mutableFloatStateOf(currentValueInternal) }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = displayValue(currentValueInternal),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = textColor,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .align(Alignment.CenterHorizontally)
        )
        
        val primaryColor = MaterialTheme.colorScheme.primary
        
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(56.dp)
                .run {
                    val sharedScope = LocalSharedTransitionScope.current
                    val animScope = LocalAnimatedVisibilityScope.current
                    if (sharedTransitionKey != null && sharedScope != null && animScope != null) {
                        with(sharedScope) {
                            sharedBounds(
                                sharedContentState = rememberSharedContentState(key = "tool_bounds_$sharedTransitionKey"),
                                animatedVisibilityScope = animScope
                            )
                        }
                    } else this
                }
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = androidx.compose.foundation.shape.CircleShape
                )
                .clip(androidx.compose.foundation.shape.CircleShape)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        isDragging = true
                        scrubberInteraction.value = true
                        val valueDelta = -(delta / density.density) * dragSensitivity * (maxValue - minValue) / 400f
                        val newValue = (currentValueInternal + valueDelta).coerceIn(minValue, maxValue)
                        currentValueInternal = newValue
                        onValueChanged(true, newValue)

                        // Haptics logic
                        val oldInt = lastHapticValue.roundToInt()
                        val newInt = newValue.roundToInt()
                        if (oldInt != newInt) {
                            val crossedZero = (oldInt < 0 && newInt >= 0) || (oldInt > 0 && newInt <= 0)
                            val crossed50 = (oldInt / 50) != (newInt / 50)
                            val crossed5 = (oldInt / 5) != (newInt / 5)
                            
                            if (crossedZero || crossed50 || newInt == maxValue.roundToInt() || newInt == minValue.roundToInt()) {
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                lastHapticValue = newValue
                            } else if (crossed5) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                lastHapticValue = newValue
                            }
                        }
                    },
                    onDragStopped = {
                        isDragging = false
                        scrubberInteraction.value = false
                        onValueChanged(false, currentValueInternal)
                    }
                )
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val center = width / 2f
                
                val tickSpacing = (spacerWidth + normalWidth).toPx()
                val pxPerValue = tickSpacing / 5f 
                val startValueOffset = currentValueInternal * pxPerValue
                
                val minTick = minValue.roundToInt()
                val maxTick = maxValue.roundToInt()
                
                for (i in minTick..maxTick) {
                    if (i % 5 != 0) continue
                    val xPos = center + (i * pxPerValue) - startValueOffset
                    
                    if (xPos >= 0 && xPos <= width) {
                        val isCenter = i == 0
                        val isFifty = i % 50 == 0
                        
                        val isHighlighted = if (currentValueInternal >= 0) {
                            i in 1..currentValueInternal.roundToInt()
                        } else {
                            i in currentValueInternal.roundToInt()..-1
                        }
                        
                        val tickHeight = when {
                            isFifty || isCenter -> arrowHeight.toPx()
                            else -> normalHeight.toPx()
                        }
                        
                        val tickColor = when {
                            isFifty || isCenter -> highlightedColor
                            isHighlighted -> primaryColor
                            else -> normalColor
                        }
                        
                        val tickWidth = normalWidth.toPx()
                        
                        drawRect(
                            color = tickColor,
                            topLeft = Offset(xPos - tickWidth / 2f, (height - tickHeight) / 2f),
                            size = androidx.compose.ui.geometry.Size(tickWidth, tickHeight)
                        )
                    }
                }
            }
            
            // Selection arrow (center static arrow)
            Spacer(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(normalWidth)
                    .background(arrowColor)
            )

            // Gradient masks and icons
            val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(64.dp)
                        .background(
                            Brush.horizontalGradient(
                                0.0f to surfaceColor,
                                0.7f to surfaceColor,
                                1.0f to Color.Transparent
                            )
                        ),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (icon != null) {
                        Text(
                            text = icon,
                            fontFamily = MaterialSymbolsRounded,
                            fontSize = 20.sp,
                            color = textColor,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }

                // Right side
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(64.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                0.0f to Color.Transparent,
                                0.3f to surfaceColor,
                                1.0f to surfaceColor
                            )
                        )
                        .clickable {
                            currentValueInternal = defaultValue
                            onValueChanged(false, defaultValue)
                            onReset()
                        },
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = FontIcons.Refresh,
                        fontFamily = MaterialSymbolsRounded,
                        fontSize = 20.sp,
                        color = textColor,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
            }
        }
    }
}
