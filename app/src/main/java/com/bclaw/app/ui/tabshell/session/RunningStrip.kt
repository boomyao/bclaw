package com.bclaw.app.ui.tabshell.session

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
import com.bclaw.app.ui.theme.BclawTheme

/**
 * Running strip — 4dp bar above the composer shown while a turn is in flight.
 *
 * Renders the "5-dot sliding right" Metro animation per UX_V2 §7.5:
 *   - five 4×4 dp accent-cyan squares, traveling left → right
 *   - 1800ms cycle
 *   - each dot offset 360ms from the next
 *
 * Below the bar: an item-type-aware label ("responding…" / "running pnpm build" / etc).
 * The label is what the controller sets via [com.bclaw.app.service.TabRuntime.runningStripLabel]
 * in response to streaming `session/update` notifications.
 */
@Composable
fun RunningStrip(
    label: String?,
    modifier: Modifier = Modifier,
) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing
    val motion = BclawTheme.motion

    Column(modifier = modifier.fillMaxWidth()) {
        FiveDotProgress(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            loopMs = motion.runningStripLoop,
            dotCount = 5,
        )
        if (!label.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sp.pageGutter, vertical = sp.sp1),
                horizontalArrangement = Arrangement.Start,
            ) {
                Text(
                    text = label,
                    style = type.meta,
                    color = colors.accent,
                )
            }
        }
    }
}

@Composable
private fun FiveDotProgress(
    modifier: Modifier = Modifier,
    loopMs: Int,
    dotCount: Int,
) {
    val accent = BclawTheme.colors.accent
    val transition = rememberInfiniteTransition(label = "running-strip-loop")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = loopMs, easing = LinearEasing),
        ),
        label = "running-strip-progress",
    )

    Box(
        modifier = modifier.drawBehind {
            val dotSize = 4.dp.toPx()
            val trackLength = size.width - dotSize
            val step = 1f / dotCount.toFloat()
            for (i in 0 until dotCount) {
                val localOffset = (progress + (i.toFloat() * step)) % 1f
                val x = trackLength * localOffset
                drawRect(
                    color = accent,
                    topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
                    size = androidx.compose.ui.geometry.Size(dotSize, dotSize),
                )
            }
        },
    )
}
