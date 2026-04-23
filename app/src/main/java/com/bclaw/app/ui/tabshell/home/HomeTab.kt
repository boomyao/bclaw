package com.bclaw.app.ui.tabshell.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bclaw.app.domain.v2.AgentDescriptor
import com.bclaw.app.domain.v2.AgentId
import com.bclaw.app.domain.v2.CwdPath
import com.bclaw.app.domain.v2.Device
import com.bclaw.app.domain.v2.SessionRef
import com.bclaw.app.service.BclawV2Intent
import com.bclaw.app.ui.LocalBclawController
import com.bclaw.app.ui.theme.BclawTheme

/**
 * Home tab — permanent leftmost tab content.
 *
 * Project-first navigation (matches codex's model where "project" = a cwd folder, and chats
 * == threads == ACP sessions are anchored to a cwd at creation):
 *
 *   - Device chip   · tap opens [DeviceSwitcherDrawer]
 *   - Project chip  · tap opens [ProjectSwitcherDrawer]
 *   - + new session · spawns a new tab on the active project's cwd
 *   - SESSIONS      · unified list of past sessions for the current project; open tabs are
 *                     dedup'd (they live on the TabStrip). Tap to resume via ACP `session/load`.
 */
@Composable
fun HomeTab(homeReclickTick: Int = 0) {
    val controller = LocalBclawController.current
    val uiState by controller.uiState.collectAsState()
    val projectSessions by controller.projectSessions.collectAsState()
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    val device = uiState.deviceBook.activeDevice
    val activeCwd = device?.effectiveProjectCwd
    val allTabs = uiState.activeTabBook?.tabs.orEmpty()
    val supportedAgents = if (device != null && activeCwd != null) device.agentsFor(activeCwd) else emptyList()
    val openSessionIds = allTabs
        .filter { activeCwd != null && it.projectCwd == activeCwd }
        .mapNotNull { it.sessionId?.value }
        .toSet()
    // Show all known sessions for this cwd. Previously we filtered out sessions with an
    // open tab, but that hid history from users looking for "the one I just had open".
    // Rows for open sessions swap their trailing label to "open" and route to the tab
    // instead of replaying.
    val sessions = activeCwd?.let { projectSessions[it].orEmpty() }.orEmpty()

    var deviceSwitcherVisible by remember { mutableStateOf(false) }
    var projectSwitcherVisible by remember { mutableStateOf(false) }
    var newSessionPickerVisible by remember { mutableStateOf(false) }

    // Refresh history for the active project whenever it changes (incl. switching device).
    LaunchedEffect(device?.id, activeCwd) {
        if (device != null && activeCwd != null) {
            controller.onIntent(BclawV2Intent.RefreshSessionsForProject(activeCwd))
        }
    }

    // Second tap on the Home pin (while Home is already active) opens the project switcher.
    // Skip tick 0 — that's the initial value; we only react to real bumps from TabStrip.
    LaunchedEffect(homeReclickTick) {
        if (homeReclickTick > 0) projectSwitcherVisible = true
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = sp.edgeLeft, end = sp.edgeRight, top = sp.sp6, bottom = sp.sp8),
        ) {
            if (device != null) {
                DeviceChip(device = device, onClick = { deviceSwitcherVisible = true })
                Spacer(Modifier.height(sp.sp4))
                ProjectChip(
                    cwd = activeCwd,
                    projectCount = device.allKnownProjects.size,
                    onClick = { projectSwitcherVisible = true },
                )
                Spacer(Modifier.height(sp.sp8))
            }

            val canStart = activeCwd != null && supportedAgents.isNotEmpty()
            val singleAgent = supportedAgents.singleOrNull()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "SESSIONS",
                    style = type.meta,
                    color = colors.inkTertiary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = when {
                        singleAgent != null -> "+ new ${singleAgent.id.value}"
                        canStart -> "+ new · pick agent"
                        else -> "+ new"
                    },
                    style = type.bodySmall,
                    color = if (canStart) colors.accent else colors.inkMuted,
                    modifier = Modifier
                        .clickable(
                            enabled = canStart,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (canStart && activeCwd != null) {
                                    if (singleAgent != null) {
                                        controller.onIntent(
                                            BclawV2Intent.OpenNewTab(
                                                agentId = singleAgent.id,
                                                cwd = activeCwd,
                                            ),
                                        )
                                    } else {
                                        newSessionPickerVisible = true
                                    }
                                }
                            },
                        )
                        .padding(sp.sp1),
                )
            }

            if (!canStart && activeCwd != null) {
                Spacer(Modifier.height(sp.sp1))
                Text(
                    text = "no agent on this device has `$activeCwd` registered. open codex/claude/gemini in that folder on the mac first.",
                    style = type.bodySmall,
                    color = colors.inkTertiary,
                )
            }

            if (newSessionPickerVisible && activeCwd != null && supportedAgents.size > 1) {
                Spacer(Modifier.height(sp.sp3))
                NewSessionAgentPicker(
                    agents = supportedAgents,
                    onPick = { agent ->
                        newSessionPickerVisible = false
                        controller.onIntent(
                            BclawV2Intent.OpenNewTab(agentId = agent.id, cwd = activeCwd),
                        )
                    },
                    onDismiss = { newSessionPickerVisible = false },
                )
            }

            Spacer(Modifier.height(sp.sp3))

            if (activeCwd == null) {
                Text(
                    text = "pick a project to see its sessions.",
                    style = type.bodySmall,
                    color = colors.inkTertiary,
                )
            } else if (sessions.isEmpty()) {
                Text(
                    text = "no sessions here yet. codex reads from `~/.codex/sessions/…`; claude from `~/.claude/projects/…`.",
                    style = type.bodySmall,
                    color = colors.inkTertiary,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(sp.sp3)) {
                    sessions.forEach { ref ->
                        SessionRow(
                            session = ref,
                            alreadyOpen = ref.sessionId.value in openSessionIds,
                            onClick = {
                                controller.onIntent(BclawV2Intent.ResumeHistoricalSession(ref))
                            },
                        )
                    }
                }
            }

        }

        DeviceSwitcherDrawer(
            visible = deviceSwitcherVisible,
            onDismissRequest = { deviceSwitcherVisible = false },
        )

        ProjectSwitcherDrawer(
            visible = projectSwitcherVisible,
            onDismissRequest = { projectSwitcherVisible = false },
        )
    }
}

@Composable
private fun DeviceChip(device: Device, onClick: () -> Unit) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = sp.sp2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sp.sp3),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = device.displayName,
                style = type.h3,
                color = colors.inkPrimary,
            )
            Text(
                text = device.wsBaseUrl.removePrefix("ws://"),
                style = type.mono,
                color = colors.inkTertiary,
            )
        }
        Text(
            text = "▾",
            style = type.h2,
            color = colors.inkSecondary,
        )
    }
}

@Composable
private fun ProjectChip(
    cwd: CwdPath?,
    projectCount: Int,
    onClick: () -> Unit,
) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    val leaf = cwd?.value?.trimEnd('/')?.substringAfterLast('/')?.ifBlank { null }
    val headline = leaf ?: if (projectCount == 0) "no projects" else "select project"
    val subline = cwd?.value
        ?: if (projectCount == 0) {
            "run codex/claude/gemini in a folder on the mac"
        } else {
            "$projectCount known · tap to choose"
        }

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
        horizontalArrangement = Arrangement.spacedBy(sp.sp3),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = headline,
                style = type.h3,
                color = colors.inkPrimary,
            )
            Text(
                text = subline,
                style = type.monoSmall,
                color = colors.inkTertiary,
            )
        }
        Text(
            text = if (projectCount <= 1) "▾" else "$projectCount ▾",
            style = type.body,
            color = colors.inkSecondary,
        )
    }
}

@Composable
private fun NewSessionAgentPicker(
    agents: List<AgentDescriptor>,
    onPick: (AgentDescriptor) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceOverlay)
            .border(1.dp, colors.borderSubtle)
            .padding(horizontal = sp.sp3, vertical = sp.sp3),
        verticalArrangement = Arrangement.spacedBy(sp.sp2),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "PICK AGENT",
                style = type.meta,
                color = colors.inkTertiary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "cancel",
                style = type.bodySmall,
                color = colors.inkTertiary,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    )
                    .padding(sp.sp1),
            )
        }
        agents.forEach { agent ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceRaised)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onPick(agent) },
                    )
                    .padding(horizontal = sp.sp3, vertical = sp.sp3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(sp.sp3),
            ) {
                Box(
                    Modifier
                        .height(18.dp)
                        .width(2.dp)
                        .background(agentAccent(agent.id)),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = agent.displayName,
                        style = type.body,
                        color = colors.inkPrimary,
                    )
                    Text(
                        text = agent.id.value,
                        style = type.monoSmall,
                        color = colors.inkTertiary,
                    )
                }
                Text(
                    text = "›",
                    style = type.h3,
                    color = colors.inkSecondary,
                )
            }
        }
    }
}

@Composable
private fun SessionRow(session: SessionRef, alreadyOpen: Boolean, onClick: () -> Unit) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing
    val agentColor = agentAccent(session.agentId)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceOverlay)
            .border(1.dp, colors.borderSubtle)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = sp.sp4, vertical = sp.sp3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sp.sp3),
    ) {
        Box(
            Modifier
                .height(24.dp)
                .width(2.dp)
                .background(agentColor),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = session.title?.ifBlank { null } ?: "untitled",
                style = type.body,
                color = colors.inkPrimary,
                maxLines = 2,
            )
            Text(
                text = buildString {
                    append(session.agentId.value)
                    session.lastActivityEpochMs?.let {
                        append(" · ")
                        append(shortStamp(it))
                    }
                },
                style = type.monoSmall,
                color = colors.inkTertiary,
            )
        }
        Text(
            text = if (alreadyOpen) "open" else "resume",
            style = type.bodySmall,
            color = if (alreadyOpen) colors.inkTertiary else colors.accent,
        )
    }
}

@Composable
private fun agentAccent(agentId: AgentId): androidx.compose.ui.graphics.Color {
    val colors = BclawTheme.colors
    return when (agentId.value.lowercase()) {
        "codex" -> colors.roleAgentCodex
        "claude", "claude-code" -> colors.roleAgentClaude
        "gemini" -> colors.roleAgentGemini
        "kimi" -> colors.roleAgentKimi
        else -> colors.roleAgentReserved
    }
}

private fun shortStamp(epochMs: Long): String {
    val diffMs = System.currentTimeMillis() - epochMs
    if (diffMs < 0) return "just now"
    val minutes = diffMs / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 60 * 24 -> "${minutes / 60}h ago"
        else -> "${minutes / (60 * 24)}d ago"
    }
}

