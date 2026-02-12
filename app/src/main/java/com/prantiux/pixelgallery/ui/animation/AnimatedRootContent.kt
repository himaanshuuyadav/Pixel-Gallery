package com.prantiux.pixelgallery.ui.animation

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * AnimatedRootContent
 *
 * Handles app startup animation — runs ONLY once when app content first appears.
 *
 * NOT a tab switch. This is the initial content entrance.
 *
 * Animation behavior (Material 3 Expressive):
 * - Duration: 240ms
 * - Easing: FastOutSlowInEasing
 * - Alpha: 0f → 1f
 * - TranslateY: 12dp → 0dp
 * - Scale: 0.995f → 1f (very subtle)
 * - No horizontal movement.
 * - No exaggerated motion.
 * - No overshoot or spring.
 *
 * Implementation:
 * - Uses LaunchedEffect to trigger entrance state.
 * - Content does not re-run animation on recomposition.
 * - Ensures it only runs once on first composition.
 */
@Composable
fun AnimatedRootContent(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // State to control when the animation starts
    var isEntering by remember { mutableStateOf(false) }
    
    // Trigger animation immediately on first composition
    LaunchedEffect(Unit) {
        isEntering = true
    }
    
    // Define the transition
    val transition = updateTransition(
        targetState = if (isEntering) 1f else 0f,
        label = "RootContentEntrance"
    )
    
    // Animate alpha
    val animatedAlpha by transition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = MotionSpec.StartupDuration,
                easing = MotionSpec.StartupEasing
            )
        },
        label = "rootAlpha"
    ) { state ->
        MotionSpec.Startup.InitialAlpha + state * (MotionSpec.Startup.TargetAlpha - MotionSpec.Startup.InitialAlpha)
    }
    
    // Animate translateY in dp, convert to float for graphicsLayer
    val animatedTranslateY by transition.animateDp(
        transitionSpec = {
            tween(
                durationMillis = MotionSpec.StartupDuration,
                easing = MotionSpec.StartupEasing
            )
        },
        label = "rootTranslateY"
    ) { state ->
        MotionSpec.Startup.InitialTranslateY + (state * (MotionSpec.Startup.TargetTranslateY.value - MotionSpec.Startup.InitialTranslateY.value)).dp
    }
    
    // Animate scale
    val animatedScale by transition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = MotionSpec.StartupDuration,
                easing = MotionSpec.StartupEasing
            )
        },
        label = "rootScale"
    ) { state ->
        MotionSpec.Startup.InitialScale + state * (MotionSpec.Startup.TargetScale - MotionSpec.Startup.InitialScale)
    }
    
    Box(
        modifier = modifier.graphicsLayer {
            alpha = animatedAlpha
            translationY = animatedTranslateY.toPx()
            scaleX = animatedScale
            scaleY = animatedScale
        }
    ) {
        content()
    }
}
