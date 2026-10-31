package com.gallery.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Registration marks from the Industry/Aperture spec: 7px corner ticks
 * sitting just outside the hairline frame.
 */
@Composable
fun CornerBrackets(
    modifier: Modifier = Modifier,
    bracketColor: Color = MaterialTheme.colorScheme.primary,
    bracketLengthPx: Float = 7f,
    strokeWidthPx: Float = 1.2f,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val inset = 3.dp.toPx()
        val w = size.width
        val h = size.height
        val stroke = strokeWidthPx.dp.toPx().coerceAtLeast(1f)
        val len = bracketLengthPx.dp.toPx() / 2.5f * 1.1f

        fun mark(cx: Float, cy: Float) {
            drawLine(bracketColor, Offset(cx, cy - len / 2), Offset(cx, cy + len / 2), stroke)
            drawLine(bracketColor, Offset(cx - len / 2, cy), Offset(cx + len / 2, cy), stroke)
        }

        mark(inset, inset)
        mark(w - inset, inset)
        mark(inset, h - inset)
        mark(w - inset, h - inset)
    }
}

@Composable
fun FrameCorners(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val len = 7.dp.toPx()
        val stroke = 1.2.dp.toPx()
        val inset = 3.dp.toPx()
        val w = size.width
        val h = size.height

        // Top-left
        drawLine(color, Offset(inset, inset), Offset(inset + len, inset), stroke)
        drawLine(color, Offset(inset, inset), Offset(inset, inset + len), stroke)
        // Top-right
        drawLine(color, Offset(w - inset, inset), Offset(w - inset - len, inset), stroke)
        drawLine(color, Offset(w - inset, inset), Offset(w - inset, inset + len), stroke)
        // Bottom-left
        drawLine(color, Offset(inset, h - inset), Offset(inset + len, h - inset), stroke)
        drawLine(color, Offset(inset, h - inset), Offset(inset, h - inset - len), stroke)
        // Bottom-right
        drawLine(color, Offset(w - inset, h - inset), Offset(w - inset - len, h - inset), stroke)
        drawLine(color, Offset(w - inset, h - inset), Offset(w - inset, h - inset - len), stroke)
    }
}
