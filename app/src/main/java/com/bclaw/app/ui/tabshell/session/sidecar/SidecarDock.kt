package com.bclaw.app.ui.tabshell.session.sidecar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bclaw.app.domain.v2.AgentId
import com.bclaw.app.net.acp.AcpInitializeResult
import com.bclaw.app.ui.LocalBclawController
import com.bclaw.app.ui.components.BclawBottomSheet
import com.bclaw.app.ui.theme.BclawTheme

/**
 * Session tools sheet — bottom sheet opened from the composer `+`.
 *
 * Two top-level tabs:
 *   - sidecars: wrapped tools (terminal / remote / files) that scaffold the chat
 *   - capabilities: what the agent CLI reports from ACP `initialize` (mcp / skills / commands)
 *
 * Single sheet so the user doesn't context-swap between a right-side drawer (caps) and a
 * bottom sheet (sidecars) — both are "stuff the agent can do", shown together.
 *
 * Capabilities attach to the AGENT, not the session — all tabs using the same agent share the
 * same capabilities set (UX_V2 §2.7 + §4).
 */
enum class Sidecar { Terminal, Remote, Files }

enum class SessionToolsTab(val label: String) {
    Sidecars("sidecars"),
    Capabilities("capabilities"),
}

@Composable
fun SessionToolsSheet(
    visible: Boolean,
    initialTab: SessionToolsTab,
    agentId: AgentId,
    onDismissRequest: () -> Unit,
    onSelectSidecar: (Sidecar) -> Unit,
) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    // Reset the active tab every time the sheet re-opens so the entry point controls which
    // tab the user sees first (composer `+` → sidecars; future triggers can land on caps).
    var selected by remember(visible, initialTab) { mutableStateOf(initialTab) }

    BclawBottomSheet(visible = visible, onDismissRequest = onDismissRequest) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sp.pageGutter, vertical = sp.sp4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "tools",
                    style = type.h2,
                    color = colors.inkPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "✕",
                    style = type.h2,
                    color = colors.inkTertiary,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismissRequest,
                        )
                        .padding(sp.sp2),
                )
            }

            SegmentedTabs(selected = selected, onSelect = { selected = it })

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.borderSubtle),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = sp.pageGutter, vertical = sp.sp4),
            ) {
                when (selected) {
                    SessionToolsTab.Sidecars -> SidecarsPanel(
                        onSelect = { onSelectSidecar(it); onDismissRequest() },
                    )
                    SessionToolsTab.Capabilities -> CapabilitiesPanel(agentId = agentId)
                }
            }
        }
    }
}

@Composable
private fun SegmentedTabs(
    selected: SessionToolsTab,
    onSelect: (SessionToolsTab) -> Unit,
) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = sp.pageGutter),
    ) {
        SessionToolsTab.values().forEach { tab ->
            val isSelected = tab == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(tab) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = tab.label,
                        style = type.body,
                        color = if (isSelected) colors.inkPrimary else colors.inkTertiary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .height(2.dp)
                            .fillMaxWidth()
                            .background(if (isSelected) colors.accent else Color.Transparent),
                    )
                }
            }
        }
    }
}

// ── Sidecars panel ──────────────────────────────────────────────────────

@Composable
private fun SidecarsPanel(onSelect: (Sidecar) -> Unit) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    Column(verticalArrangement = Arrangement.spacedBy(sp.sp2)) {
        Text(
            text = "tools that wrap the conversation — never replace it.",
            style = type.bodySmall,
            color = colors.inkTertiary,
        )
        Spacer(Modifier.height(sp.sp2))
        SidecarRow(
            title = "terminal",
            glyph = "▦",
            subtitle = "split mode · agent-shared shell",
            badge = "v2.1",
            accent = colors.accent,
            onClick = { onSelect(Sidecar.Terminal) },
        )
        SidecarRow(
            title = "remote",
            glyph = "⎚",
            subtitle = "peek mode · gui over the tailnet",
            badge = "v2.1",
            accent = colors.roleAgentClaude,
            onClick = { onSelect(Sidecar.Remote) },
        )
        SidecarRow(
            title = "files",
            glyph = "⊟",
            subtitle = "sheet mode · attach from repo",
            badge = null,
            accent = colors.roleLive,
            onClick = { onSelect(Sidecar.Files) },
        )
    }
}

@Composable
private fun SidecarRow(
    title: String,
    glyph: String,
    subtitle: String,
    badge: String?,
    accent: Color,
    onClick: () -> Unit,
) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceRaised)
            .border(1.dp, colors.borderSubtle)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = sp.sp3, vertical = sp.sp3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(40.dp)
                .background(accent.copy(alpha = 0.12f))
                .border(1.dp, accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = glyph, style = type.h2, color = accent)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = sp.sp3),
        ) {
            Text(text = title, style = type.h3, color = colors.inkPrimary)
            Text(text = subtitle, style = type.mono, color = colors.inkTertiary)
        }
        if (badge != null) {
            Box(
                modifier = Modifier
                    .background(colors.surfaceDeep)
                    .border(1.dp, colors.borderStrong)
                    .padding(horizontal = sp.sp2, vertical = 2.dp),
            ) {
                Text(
                    text = badge.uppercase(),
                    style = type.micro,
                    color = colors.inkSecondary,
                )
            }
        }
    }
}

// ── Capabilities panel ──────────────────────────────────────────────────

@Composable
private fun CapabilitiesPanel(agentId: AgentId) {
    val controller = LocalBclawController.current
    val agentInitMap by controller.agentInit.collectAsState()
    val init = agentInitMap[agentId]
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    var pivot by remember { mutableStateOf(CapabilityPivot.Skills) }

    Column {
        Text(
            text = "${agentId.value.lowercase()} · shared across all tabs",
            style = type.bodySmall,
            color = colors.inkTertiary,
        )
        Spacer(Modifier.height(sp.sp3))

        Row(modifier = Modifier.fillMaxWidth()) {
            CapabilityPivot.values().forEach { p ->
                val isSelected = p == pivot
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { pivot = p },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = p.label,
                        style = type.mono,
                        color = if (isSelected) colors.inkPrimary else colors.inkTertiary,
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.borderSubtle),
        )
        Spacer(Modifier.height(sp.sp3))

        when (pivot) {
            CapabilityPivot.Skills -> EmptyPivot(
                title = "no skills reported by the agent.",
                subtitle = "in v2.1 the bridge will expose @mention-able procedures here.",
            )
            CapabilityPivot.Mcp -> McpPivot(init)
            CapabilityPivot.Commands -> EmptyPivot(
                title = "no commands enumerated yet.",
                subtitle = "type `/` in the composer to see the live palette (batch after this).",
            )
        }
    }
}

private enum class CapabilityPivot(val label: String) {
    Skills("skills"),
    Mcp("mcp"),
    Commands("commands"),
}

@Composable
private fun McpPivot(init: AcpInitializeResult?) {
    val mcp = init?.agentCapabilities?.mcpCapabilities
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    if (init == null || mcp == null) {
        EmptyPivot(
            title = "no mcp servers reported.",
            subtitle = "agent either hasn't finished initialize, or doesn't support mcp.",
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(sp.sp2)) {
        CapabilityCard(
            title = "http transport",
            status = if (mcp.http) "supported" else "not supported",
            supported = mcp.http,
        )
        CapabilityCard(
            title = "sse transport",
            status = if (mcp.sse) "supported" else "not supported",
            supported = mcp.sse,
        )
        Text(
            text = "tool-level inventory lands when the bridge forwards `mcp/list_tools` · v2.1.",
            style = type.bodySmall,
            color = colors.inkTertiary,
            modifier = Modifier.padding(top = sp.sp3),
        )
    }
}

@Composable
private fun EmptyPivot(title: String, subtitle: String) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    Column(verticalArrangement = Arrangement.spacedBy(sp.sp2)) {
        Text(text = title, style = type.body, color = colors.inkSecondary)
        Text(text = subtitle, style = type.bodySmall, color = colors.inkTertiary)
    }
}

@Composable
private fun CapabilityCard(title: String, status: String, supported: Boolean) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceRaised)
            .border(1.dp, colors.borderSubtle)
            .padding(horizontal = sp.sp3, vertical = sp.sp3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = type.h3, color = colors.inkPrimary)
            Text(
                text = status,
                style = type.mono,
                color = if (supported) colors.roleLive else colors.inkTertiary,
            )
        }
    }
}
