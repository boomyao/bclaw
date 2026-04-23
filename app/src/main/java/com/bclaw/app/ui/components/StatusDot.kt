package com.bclaw.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 8×8 dp sharp-square status dot — the Metro "live tile glyph."
 *
 * Mirrors `design/project/design/ds-status.html` §A + ds-motion §C "live pulse":
 * alpha 0.3 ↔ 1.0, 1600 ms loop (default) when [pulsing] = true.
 *
 * There is NO circular status dot in bclaw (UX_V2 §7.4 zero-radius rule).
 */
@Composable
fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 8.dp,
    pulsing: Boolean = false,
    pulseDurationMs: Int = 1600,
) {
    val alpha: Float = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "status-dot-pulse")
        val animated by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(pulseDurationMs / 2, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "status-dot-alpha",
        )
        animated
    } else {
        1f
    }

    Box(
        modifier = modifier
            .size(size)
            .alpha(alpha)
            .background(color),
    )
}
