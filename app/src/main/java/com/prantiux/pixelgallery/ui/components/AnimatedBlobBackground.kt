package com.prantiux.pixelgallery.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AnimatedBlobBackground(
    modifier: Modifier = Modifier,
    color: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "blob")
    
    // We use different animation durations for each phase to create a non-repeating, organic morphing effect.
    // Slowed down durations for a much slower morphing speed.
    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(28000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase1"
    )
    
    val phase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(35000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase2"
    )

    val phase3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(45000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase3"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val center = Offset(width / 2, height / 2)
        val baseRadius = minOf(width, height) / 2.2f
        val amplitude = baseRadius * 0.15f
        
        // Draw the background blob (lower opacity, slightly larger, different phase shift)
        val bgPath = Path()
        val numPoints = 120
        val angleStep = (2f * Math.PI.toFloat()) / numPoints
        
        for (i in 0..numPoints) {
            val angle = i * angleStep
            
            // Use phase shifts for organic movement
            val rOffset1 = sin(angle * 2f + phase1 + 1f) * (amplitude * 1.2f)
            val rOffset2 = cos(angle * 3f - phase2) * (amplitude * 0.8f)
            val rOffset3 = sin(angle * 4f + phase3 + 2f) * (amplitude * 0.5f)
            
            val r = (baseRadius * 1.1f) + rOffset1 + rOffset2 + rOffset3
            
            val x = center.x + r * cos(angle)
            val y = center.y + r * sin(angle)
            
            if (i == 0) {
                bgPath.moveTo(x, y)
            } else {
                bgPath.lineTo(x, y)
            }
        }
        bgPath.close()
        drawPath(bgPath, color.copy(alpha = 0.2f))

        // Draw the foreground blob
        val fgPath = Path()
        for (i in 0..numPoints) {
            val angle = i * angleStep
            
            val rOffset1 = sin(angle * 2f + phase1) * amplitude
            val rOffset2 = cos(angle * 3f + phase2) * (amplitude * 0.7f)
            val rOffset3 = sin(angle * 4f + phase3) * (amplitude * 0.4f)
            
            val r = baseRadius + rOffset1 + rOffset2 + rOffset3
            
            val x = center.x + r * cos(angle)
            val y = center.y + r * sin(angle)
            
            if (i == 0) {
                fgPath.moveTo(x, y)
            } else {
                fgPath.lineTo(x, y)
            }
        }
        fgPath.close()
        drawPath(fgPath, color.copy(alpha = 0.5f))
    }
}
