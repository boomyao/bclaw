package com.bclaw.app.ui.tabshell.session

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bclaw.app.domain.v2.AgentDescriptor
import com.bclaw.app.domain.v2.CwdPath
import com.bclaw.app.domain.v2.TabState
import com.bclaw.app.service.AgentConnectionPhase
import com.bclaw.app.service.BclawV2Intent
import com.bclaw.app.ui.LocalBclawController
import com.bclaw.app.ui.components.BclawBottomSheet
import com.bclaw.app.ui.components.StatusDot
import com.bclaw.app.ui.theme.BclawColors
import com.bclaw.app.ui.theme.BclawTheme

/**
 * Agent picker sheet — bottom-up, opened from the locked agent chip on [SessionCrumb].
 *
 * UX_V2 §2.5: picking a different agent **forks** the current session into a new tab in the
 * same project, bound to the chosen agent. Existing sessions of the target agent in the same
 * project short-circuit to "already open in tab N" and just switch.
 *
 * Not-yet-connected agents are grayed and non-interactive per UX_V2 §6 (honest banners over
 * cheerful placeholders).
 */
@Composable
fun AgentPickerSheet(
    visible: Boolean,
    currentTab: TabState,
    onDismissRequest: () -> Unit,
) {
    val controller = LocalBclawController.current
    val uiState by controller.uiState.collectAsState()
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    val device = uiState.deviceBook.activeDevice
    val knownAgents = device?.knownAgents.orEmpty()
    val tabs = uiState.activeTabBook?.tabs.orEmpty()
    val cwd = currentTab.projectCwd

    BclawBottomSheet(
        visible = visible,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = sp.pageGutter, vertical = sp.sp5),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "pick an agent",
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
            Spacer(Modifier.height(sp.sp1))
            Text(
                text = "forking ${cwdShort(cwd)}. current context isn't carried.",
                style = type.bodySmall,
                color = colors.inkTertiary,
            )
            Spacer(Modifier.height(sp.sp4))

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.borderSubtle),
            )
            Spacer(Modifier.height(sp.sp2))

            if (knownAgents.isEmpty()) {
                Text(
                    text = "no agents reported by this device. repair to refresh.",
                    style = type.bodySmall,
                    color = colors.inkTertiary,
                    modifier = Modifier.padding(vertical = sp.sp3),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    knownAgents.forEach { agent ->
                        val matchingTab = tabs.firstOrNull {
                            it.agentId == agent.id && it.projectCwd == cwd
                        }
                        val phase = uiState.agentConnections[agent.id]
                        val notConnected = phase == AgentConnectionPhase.Offline ||
                            phase == AgentConnectionPhase.NotAvailable
                        AgentRow(
                            agent = agent,
                            state = when {
                                matchingTab != null && matchingTab.id != currentTab.id ->
                                    AgentRowState.OpenInTab(matchingTab.id.value.takeLast(4))
                                matchingTab?.id == currentTab.id ->
                                    AgentRowState.CurrentTab
                                notConnected -> AgentRowState.NotConnected
                                else -> AgentRowState.AvailableForNewTab
                            },
                            onClick = {
                                when {
                                    matchingTab != null && matchingTab.id != currentTab.id -> {
                                        controller.onIntent(BclawV2Intent.SwitchTab(matchingTab.id))
                                        onDismissRequest()
                                    }
                                    matchingTab?.id == currentTab.id -> {
                                        // No-op — you're already here.
                                        onDismissRequest()
                                    }
                                    !notConnected -> {
                                        controller.onIntent(
                                            BclawV2Intent.OpenNewTab(
                                                agentId = agent.id,
                                                cwd = cwd,
                                                forkedFrom = currentTab.id,
                                            ),
                                        )
                                        onDismissRequest()
                                    }
                                }
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(sp.sp3))
        }
    }
}

private sealed class AgentRowState {
    data class OpenInTab(val tabSuffix: String) : AgentRowState()
    data object CurrentTab : AgentRowState()
    data object AvailableForNewTab : AgentRowState()
    data object NotConnected : AgentRowState()
}

@Composable
private fun AgentRow(
    agent: AgentDescriptor,
    state: AgentRowState,
    onClick: () -> Unit,
) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    val enabled = state !is AgentRowState.NotConnected
    val dotColor = agentColorForId(agent.id.value, colors)
    val (suffix, suffixColor) = when (state) {
        is AgentRowState.OpenInTab -> "open in tab ${state.tabSuffix}" to colors.inkTertiary
        AgentRowState.CurrentTab -> "current tab" to colors.accent
        AgentRowState.AvailableForNewTab -> "→ new tab" to colors.accent
        AgentRowState.NotConnected -> "not connected" to colors.inkMuted
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = sp.sp2, vertical = sp.sp4)
            .alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(color = dotColor, size = 10.dp)
        Spacer(Modifier.height(1.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = sp.sp3),
        ) {
            Text(
                text = agent.id.value.lowercase(),
                style = type.h3,
                color = colors.inkPrimary,
            )
            Text(
                text = agent.displayName.ifBlank { "agent" },
                style = type.bodySmall,
                color = colors.inkTertiary,
            )
        }
        Text(
            text = suffix,
            style = type.mono,
            color = suffixColor,
        )
    }
}

private fun agentColorForId(id: String, colors: BclawColors): Color = when (id.lowercase()) {
    "codex" -> colors.roleAgentCodex
    "claude", "claude-code" -> colors.roleAgentClaude
    "gemini" -> colors.roleAgentGemini
    "kimi" -> colors.roleAgentKimi
    else -> colors.roleAgentReserved
}

private fun cwdShort(cwd: CwdPath): String {
    val parts = cwd.value.trimEnd('/').split("/").filter { it.isNotBlank() }
    return if (parts.isEmpty()) "this workspace" else parts.last()
}
