package com.prantiux.pixelgallery.ui.components.util

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker

@Composable
fun Modifier.swipe(
    enabled: Boolean = true,
    onDragStart: () -> Unit = {},
    onDrag: (dragAmount: Float) -> Unit = {},
    onDragEnd: (velocity: Float) -> Unit = {}
): Modifier {
    return this then Modifier
        .pointerInput(enabled) {
            if (enabled) {
                val velocityTracker = VelocityTracker()
                detectVerticalDragGestures(
                    onDragStart = {
                        velocityTracker.resetTracking()
                        onDragStart()
                    },
                    onVerticalDrag = { change, dragAmount ->
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        onDrag(dragAmount)
                        change.consume()
                    },
                    onDragEnd = {
                        val velocity = velocityTracker.calculateVelocity().y
                        onDragEnd(velocity)
                    },
                    onDragCancel = {
                        onDragEnd(0f)
                    }
                )
            }
        }
}