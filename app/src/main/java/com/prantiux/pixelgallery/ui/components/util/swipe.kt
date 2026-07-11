package com.prantiux.pixelgallery.ui.components.util

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs

@Composable
fun Modifier.swipe(
    enabled: Boolean = true,
    onDragStart: () -> Unit = {},
    onDrag: (dragAmount: Offset) -> Unit = {},
    onDragEnd: (velocity: Offset) -> Unit = {}
): Modifier {
    return this then Modifier
        .pointerInput(enabled) {
            if (enabled) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val velocityTracker = VelocityTracker()
                    velocityTracker.addPosition(down.uptimeMillis, down.position)
                    
                    var isVerticalDrag = false
                    var isHorizontalDrag = false
                    
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        
                        if (!isVerticalDrag && !isHorizontalDrag) {
                            val dx = abs(change.position.x - down.position.x)
                            val dy = abs(change.position.y - down.position.y)
                            
                            val touchSlop = viewConfiguration.touchSlop
                            if (dy > touchSlop && dy > dx) {
                                isVerticalDrag = true
                                onDragStart()
                                change.consume()
                            } else if (dx > touchSlop && dx > dy) {
                                isHorizontalDrag = true
                                // Let the Pager handle it
                            }
                        }
                        
                        if (isVerticalDrag) {
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            onDrag(change.positionChange())
                            change.consume()
                        }
                    } while (event.changes.any { it.pressed })
                    
                    if (isVerticalDrag) {
                        val velocity = velocityTracker.calculateVelocity()
                        onDragEnd(Offset(velocity.x, velocity.y))
                    }
                }
            }
        }
}