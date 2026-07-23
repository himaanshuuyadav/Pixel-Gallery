package com.smarttoolfactory.cropper.util

import androidx.compose.ui.graphics.GraphicsLayerScope
import com.smarttoolfactory.cropper.state.TransformState

/**
 * Calculate zoom level and zoom value when user double taps
 */
internal fun calculateZoom(
    zoomLevel: ZoomLevel,
    initial: Float,
    min: Float,
    max: Float
): Pair<ZoomLevel, Float> {

    val newZoomLevel: ZoomLevel
    val newZoom: Float

    when (zoomLevel) {
        ZoomLevel.Mid -> {
            newZoomLevel = ZoomLevel.Max
            newZoom = max.coerceAtMost(3f)
        }

        ZoomLevel.Max -> {
            newZoomLevel = ZoomLevel.Min
            newZoom = if (min == initial) initial else min
        }

        else -> {
            newZoomLevel = ZoomLevel.Mid
            newZoom = if (min == initial) (min + max.coerceAtMost(3f)) / 2 else initial
        }
    }
    return Pair(newZoomLevel, newZoom)
}

internal fun getNextZoomLevel(zoomLevel: ZoomLevel): ZoomLevel = when (zoomLevel) {
    ZoomLevel.Mid -> {
        ZoomLevel.Max
    }

    ZoomLevel.Max -> {
        ZoomLevel.Min
    }

    else -> {
        ZoomLevel.Mid
    }
}

/**
 * Update graphic layer with [transformState]
 */
internal fun GraphicsLayerScope.update(transformState: TransformState) {

    // Set zoom
    val zoom = transformState.zoom

    // Set pan
    val pan = transformState.pan
    val translationX = pan.x
    val translationY = pan.y
    this.translationX = translationX
    this.translationY = translationY

    // Set rotation
    val theta = transformState.rotation
    this.rotationZ = theta

    // Auto-zoom to keep viewport completely filled during rotation
    val rad = Math.toRadians(Math.abs(theta).toDouble())
    val cos = Math.cos(rad).toFloat()
    val sin = Math.sin(rad).toFloat()

    val w = transformState.imageSize.width.toFloat()
    val h = transformState.imageSize.height.toFloat()

    val s = if (w > 0 && h > 0) {
        maxOf(
            (w * cos + h * sin) / w,
            (w * sin + h * cos) / h
        )
    } else {
        1f
    }

    this.scaleX = zoom * s
    this.scaleY = zoom * s

    // Adjust rotation pivot to be the center of the image footprint, 
    // taking into account the initial vertical offset
    val offsetY = transformState.initialOffsetY.toFloat()
    val containerHeight = transformState.containerSize.height.toFloat()
    val pivotY = if (containerHeight > 0) {
        0.5f + (offsetY / containerHeight)
    } else {
        0.5f
    }
    this.transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, pivotY)
}
