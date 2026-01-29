package com.prantiux.pixelgallery.ui.shapes

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Smooth corner shape with iOS-like continuous curves
 * 
 * Parameters:
 * - cornerRadius: The radius for all corners (if uniform)
 * - smoothness: Percentage (0-100) of how smooth the corners should be. 60 is a good default.
 * - Individual corner radii can be specified
 * 
 * Smooth corners create a more organic, continuous curve compared to standard rounded corners.
 * This is achieved by using cubic Bezier curves with control points positioned to create
 * a smoother transition between the straight edges and the curved corners.
 */
class SmoothCornerShape(
    private val cornerRadiusTL: Dp = 0.dp,
    private val cornerRadiusTR: Dp = 0.dp,
    private val cornerRadiusBR: Dp = 0.dp,
    private val cornerRadiusBL: Dp = 0.dp,
    private val smoothnessAsPercent: Int = 60
) : Shape {
    
    constructor(
        cornerRadius: Dp,
        smoothnessAsPercent: Int = 60
    ) : this(cornerRadius, cornerRadius, cornerRadius, cornerRadius, smoothnessAsPercent)
    
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()
        
        with(density) {
            val topLeft = min(cornerRadiusTL.toPx(), min(size.width, size.height) / 2f)
            val topRight = min(cornerRadiusTR.toPx(), min(size.width, size.height) / 2f)
            val bottomRight = min(cornerRadiusBR.toPx(), min(size.width, size.height) / 2f)
            val bottomLeft = min(cornerRadiusBL.toPx(), min(size.width, size.height) / 2f)
            
            // Smoothness factor (0.0 to 1.0, typically 0.6)
            val smoothFactor = (smoothnessAsPercent.coerceIn(0, 100) / 100f)
            
            // Start from top-left corner
            path.moveTo(topLeft, 0f)
            
            // Top edge
            path.lineTo(size.width - topRight, 0f)
            
            // Top-right corner (smooth curve)
            if (topRight > 0) {
                val controlPoint1X = size.width - topRight * (1 - smoothFactor)
                val controlPoint1Y = 0f
                val controlPoint2X = size.width
                val controlPoint2Y = topRight * (1 - smoothFactor)
                path.cubicTo(
                    controlPoint1X, controlPoint1Y,
                    controlPoint2X, controlPoint2Y,
                    size.width, topRight
                )
            }
            
            // Right edge
            path.lineTo(size.width, size.height - bottomRight)
            
            // Bottom-right corner (smooth curve)
            if (bottomRight > 0) {
                val controlPoint1X = size.width
                val controlPoint1Y = size.height - bottomRight * (1 - smoothFactor)
                val controlPoint2X = size.width - bottomRight * (1 - smoothFactor)
                val controlPoint2Y = size.height
                path.cubicTo(
                    controlPoint1X, controlPoint1Y,
                    controlPoint2X, controlPoint2Y,
                    size.width - bottomRight, size.height
                )
            }
            
            // Bottom edge
            path.lineTo(bottomLeft, size.height)
            
            // Bottom-left corner (smooth curve)
            if (bottomLeft > 0) {
                val controlPoint1X = bottomLeft * (1 - smoothFactor)
                val controlPoint1Y = size.height
                val controlPoint2X = 0f
                val controlPoint2Y = size.height - bottomLeft * (1 - smoothFactor)
                path.cubicTo(
                    controlPoint1X, controlPoint1Y,
                    controlPoint2X, controlPoint2Y,
                    0f, size.height - bottomLeft
                )
            }
            
            // Left edge
            path.lineTo(0f, topLeft)
            
            // Top-left corner (smooth curve)
            if (topLeft > 0) {
                val controlPoint1X = 0f
                val controlPoint1Y = topLeft * (1 - smoothFactor)
                val controlPoint2X = topLeft * (1 - smoothFactor)
                val controlPoint2Y = 0f
                path.cubicTo(
                    controlPoint1X, controlPoint1Y,
                    controlPoint2X, controlPoint2Y,
                    topLeft, 0f
                )
            }
            
            path.close()
        }
        
        return Outline.Generic(path)
    }
}
