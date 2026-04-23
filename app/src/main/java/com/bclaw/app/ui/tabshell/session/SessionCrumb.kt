package com.bclaw.app.ui.tabshell.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bclaw.app.domain.v2.AgentId
import com.bclaw.app.domain.v2.TabState
import com.bclaw.app.ui.components.StatusDot
import com.bclaw.app.ui.theme.BclawColors
import com.bclaw.app.ui.theme.BclawTheme

/**
 * Session crumb — 52dp header row per ds-spacing §B.
 *
 * Left: small agent-color identity dot (identity only; device-level reachability is
 *   owned by [com.bclaw.app.ui.tabshell.ConnectionStatusBar] per unified-status design).
 * Center: session name · shortened cwd.
 * Right: `···` opens a session-actions sheet (rename / fork / close / copy id).
 *
 * The crumb auto-collapses when the user scrolls the message list down and reappears
 * when they scroll back to the top — see [SessionTab] for the AnimatedVisibility driver.
 */
@Composable
fun SessionCrumb(
    tab: TabState,
    onOverflowClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    val agentColor = agentColorFor(tab.agentId, colors)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(sp.crumbHeight)
            .background(colors.surfaceBase)
            .padding(horizontal = sp.pageGutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sp.sp3),
    ) {
        StatusDot(color = agentColor, size = 8.dp, pulsing = false)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tab.sessionName?.trim()?.takeIf { it.isNotEmpty() } ?: "untitled",
                style = type.h3,
                color = colors.inkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = shortenCwd(tab.projectCwd.value),
                style = type.monoSmall,
                color = colors.inkTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Box(
            modifier = Modifier
                .width(32.dp)
                .height(32.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOverflowClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("···", style = type.body, color = colors.inkSecondary)
        }
    }
}

private fun agentColorFor(agentId: AgentId, colors: BclawColors): Color = when (agentId.value.lowercase()) {
    "codex" -> colors.roleAgentCodex
    "claude", "claude-code" -> colors.roleAgentClaude
    "gemini" -> colors.roleAgentGemini
    "kimi" -> colors.roleAgentKimi
    else -> colors.roleAgentReserved
}

/** "/Users/you/projects/foo" → "projects/foo" so the crumb stays scannable. */
private fun shortenCwd(path: String): String {
    val parts = path.trimEnd('/').split("/").filter { it.isNotBlank() }
    return if (parts.size <= 2) path else parts.takeLast(2).joinToString("/")
}
