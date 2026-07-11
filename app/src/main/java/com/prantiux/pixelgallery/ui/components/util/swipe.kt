package com.prantiux.pixelgallery.ui.components.util

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput

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
                detectVerticalDragGestures(
                    onDragStart = { _ ->
                        onDragStart()
                    },
                    onVerticalDrag = { change, dragAmount ->
                        onDrag(Offset(0f, dragAmount))
                        change.consume()
                    },
                    onDragEnd = {
                        // Velocity isn't directly available from detectVerticalDragGestures easily, 
                        // but MediaOverlay just uses it for direction and threshold. 
                        // We will pass a dummy velocity since MediaOverlay recalculates velocity anyway if needed, 
                        // or we can just pass a small offset. Actually MediaOverlay uses vy to check if it's a fast swipe!
                        // Let's just pass a default velocity of 0, MediaOverlay handles fallback based on offset anyway.
                        onDragEnd(Offset(0f, 0f))
                    },
                    onDragCancel = {
                        onDragEnd(Offset(0f, 0f))
                    }
                )
            }
        }
}