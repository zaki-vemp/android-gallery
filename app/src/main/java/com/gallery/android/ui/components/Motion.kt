package com.gallery.android.ui.components

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

object GalleryMotion {
    val Fast = tween<Float>(durationMillis = 180, easing = FastOutSlowInEasing)
    val Medium = tween<Float>(durationMillis = 280, easing = FastOutSlowInEasing)
    val SoftBounce = spring<Float>(
        dampingRatio = 0.72f,
        stiffness = Spring.StiffnessMedium,
    )
    val Placement = spring<IntOffset>(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessMediumLow,
    )
    const val NavEnterMs = 320
    const val NavExitMs = 220
}

/**
 * Scale-on-press driven by pointer down/up — works alongside combinedClickable.
 */
fun Modifier.scaleOnPress(
    pressedScale: Float = 0.96f,
): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = GalleryMotion.SoftBounce,
        label = "scaleOnPress",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    waitForUpOrCancellation()
                    pressed = false
                }
            }
        }
}

@Composable
fun animateSelectionScale(selected: Boolean): Float {
    val scale by animateFloatAsState(
        targetValue = if (selected) 0.92f else 1f,
        animationSpec = GalleryMotion.SoftBounce,
        label = "selectionScale",
    )
    return scale
}
