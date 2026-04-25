package com.bclaw.app.ui.tabshell.session.sidecar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bclaw.app.ui.showcase.terminal.TerminalChipsContent
import com.bclaw.app.ui.theme.BclawTheme

/**
 * Live Terminal / Remote sidecar overlays — full-screen covers opened from the session's
 * composer-`+` → Tools → sidecar picker.
 *
 * Terminal is still visualization-only until the PTY adapter lands. Remote uses the
 * bridge-hosted streaming stack behind a product-level desktop surface.
 *
 * Pattern: `TopBar (title · mode tabs · ✕)` + body content + system-insets padding. The
 * terminal body still comes from the showcase helper so the design catalogue and production
 * path render identical pixels.
 */

/**
 * Full-screen terminal overlay — single-view chips + keyrow. No mode switcher: the
 * terminal always renders as the `TerminalChipsContent` pane. Recording was removed
 * per product feedback (purpose unclear); "split" is a different concept entirely —
 * a SessionTab layout that interleaves chat + terminal — exposed via [onRequestSplit].
 */
@Composable
fun LiveTerminalSidecar(
    onDismiss: () -> Unit,
    onRequestSplit: () -> Unit,
) {
    OverlayShell(
        title = "terminal",
        subtitle = "ember · ~/code/foo-api",
        modes = emptyList(),
        activeIndex = -1,
        onModeClick = { },
        onDismiss = onDismiss,
        extraAction = OverlayAction(label = "⇅ SPLIT", onClick = onRequestSplit),
        dark = true,
    ) { modifier ->
        TerminalChipsContent(modifier = modifier)
    }
}

/** Full-screen remote desktop overlay. */
@Composable
fun LiveRemoteSidecar(
    bridgeWsUrl: String?,
    deviceName: String,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        RemoteDesktopContent(
            bridgeWsUrl = bridgeWsUrl,
            deviceName = deviceName,
            onDismiss = onDismiss,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Extra header-bar action — e.g. "⇅ SPLIT" on the terminal overlay. */
private data class OverlayAction(val label: String, val onClick: () -> Unit)

/**
 * Shared overlay chrome: title bar with close button, mode tabs below, content fills the
 * rest. Handles system insets (status bar + nav bar) so the app stays edge-to-edge.
 */
@Composable
private fun OverlayShell(
    title: String,
    subtitle: String,
    modes: List<String>,
    activeIndex: Int,
    onModeClick: (Int) -> Unit,
    onDismiss: () -> Unit,
    extraAction: OverlayAction?,
    dark: Boolean,
    showTitleBar: Boolean = true,
    content: @Composable (Modifier) -> Unit,
) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val bg = if (dark) Color(0xFF000000) else colors.surfaceBase
    val chromeBg = if (dark) Color(0xFF0A0A0A) else colors.surfaceRaised
    val chromeInk = if (dark) Color(0xFFF4F0E3) else colors.inkPrimary
    val chromeSubdued = if (dark) Color(0xFF76746A) else colors.inkTertiary
    val chromeBorder = if (dark) Color(0xFF26261C) else colors.borderSubtle

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.navigationBars)),
    ) {
        if (showTitleBar) {
            // Title row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(chromeBg)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title.uppercase(),
                        style = type.meta,
                        color = chromeSubdued,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = type.mono,
                        color = chromeInk,
                    )
                }
                if (extraAction != null) {
                    Box(
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = extraAction.onClick,
                            )
                            .background(Color.Transparent)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = extraAction.label,
                            style = type.mono.copy(
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 1.5.sp,
                            ),
                            color = colors.accent,
                            fontSize = 11.sp,
                        )
                    }
                }
                Text(
                    text = "✕",
                    style = type.h2,
                    color = chromeSubdued,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss,
                        )
                        .padding(8.dp),
                )
            }
        }
        // Mode tabs — only drawn when the sidecar has more than one mode.
        if (modes.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(chromeBg),
            ) {
                modes.forEachIndexed { i, label ->
                    val selected = i == activeIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onModeClick(i) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 10.dp),
                        ) {
                            Text(
                                text = label,
                                style = type.mono.copy(
                                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                    letterSpacing = 1.5.sp,
                                ),
                                color = if (selected) chromeInk else chromeSubdued,
                                fontSize = 11.sp,
                            )
                            Spacer(Modifier.height(6.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(
                                        if (selected) colors.accent else Color.Transparent,
                                    ),
                            )
                        }
                    }
                }
            }
        }
        // Separator
        if (showTitleBar || modes.isNotEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(chromeBorder),
            )
        }
        // Content body — consumes remaining height
        content(Modifier.weight(1f).fillMaxWidth())
    }
}
