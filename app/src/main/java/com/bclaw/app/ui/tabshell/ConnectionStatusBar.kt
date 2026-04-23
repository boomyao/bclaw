package com.bclaw.app.ui.tabshell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bclaw.app.service.BridgePhase
import com.bclaw.app.ui.LocalBclawController
import com.bclaw.app.ui.components.StatusDot
import com.bclaw.app.ui.theme.BclawColors
import com.bclaw.app.ui.theme.BclawTheme

/**
 * Single device-level connection surface, sitting just under the [TabStrip]. Previously
 * connection state was reflected per-tab in [com.bclaw.app.ui.tabshell.session.SessionCrumb];
 * per user feedback (2026-04-22) we collapse that to one always-visible bar so reachability
 * doesn't hide behind the current tab.
 *
 * Hides (0dp) on the happy path — [BridgePhase.Idle] and [BridgePhase.Connected] — so the
 * chrome stays quiet when there's nothing interesting to say.
 */
@Composable
fun ConnectionStatusBar() {
    val controller = LocalBclawController.current
    val uiState by controller.uiState.collectAsState()
    val phase = uiState.bridgePhase

    val copy = textFor(phase)
    val visible = copy != null

    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(tween(180)) + fadeIn(tween(180)),
        exit = shrinkVertically(tween(140)) + fadeOut(tween(140)),
    ) {
        val colors = BclawTheme.colors
        val type = BclawTheme.typography
        val sp = BclawTheme.spacing
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundFor(phase, colors))
                .height(22.dp)
                .padding(horizontal = sp.pageGutter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(sp.sp2),
        ) {
            StatusDot(
                color = dotColorFor(phase, colors),
                size = 8.dp,
                pulsing = phase == BridgePhase.Connecting,
            )
            Text(
                text = copy.orEmpty(),
                style = type.monoSmall,
                color = colors.inkPrimary,
            )
        }
    }
}

private fun textFor(phase: BridgePhase): String? = when (phase) {
    BridgePhase.Idle, BridgePhase.Connected -> null
    BridgePhase.NoNetwork -> "offline · phone has no network"
    BridgePhase.Connecting -> "connecting to bridge…"
    BridgePhase.Degraded -> "bridge degraded · some agents offline"
    BridgePhase.Offline -> "offline · bridge unreachable"
}

private fun backgroundFor(phase: BridgePhase, colors: BclawColors): Color = when (phase) {
    BridgePhase.Offline, BridgePhase.NoNetwork, BridgePhase.Degraded -> colors.surfaceDeep
    else -> colors.surfaceOverlay
}

private fun dotColorFor(phase: BridgePhase, colors: BclawColors): Color = when (phase) {
    BridgePhase.Offline, BridgePhase.NoNetwork -> colors.roleError
    BridgePhase.Degraded -> colors.roleWarn
    BridgePhase.Connecting -> colors.accent
    else -> colors.roleLive
}
