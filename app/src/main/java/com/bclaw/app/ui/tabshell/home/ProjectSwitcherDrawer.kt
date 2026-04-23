package com.bclaw.app.ui.tabshell.home

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.bclaw.app.domain.v2.AgentDescriptor
import com.bclaw.app.domain.v2.CwdPath
import com.bclaw.app.service.BclawV2Intent
import com.bclaw.app.ui.LocalBclawController
import com.bclaw.app.ui.components.BclawSideDrawer
import com.bclaw.app.ui.components.DrawerEdge
import com.bclaw.app.ui.components.MetroButton
import com.bclaw.app.ui.components.MetroButtonSize
import com.bclaw.app.ui.components.MetroButtonVariant
import com.bclaw.app.ui.components.MetroTextField
import com.bclaw.app.ui.components.StatusDot
import com.bclaw.app.ui.theme.BclawTheme

/**
 * Project switcher — left-edge drawer, opened from the Home project chip.
 *
 * Codex / claude / gemini each carry their own project list (cwd trust ACL on codex, session
 * directory on claude, projects.json on gemini). This drawer shows the *union* across every
 * known agent on the active device, tagging each row with the agents that own it so the user
 * can see at a glance "this cwd is paired with codex + claude".
 *
 * Selecting a project filters Home's tabs and history to that cwd; `+ new session` on Home then
 * only offers agents that actually have that cwd registered.
 */
@Composable
fun ProjectSwitcherDrawer(
    visible: Boolean,
    onDismissRequest: () -> Unit,
) {
    val controller = LocalBclawController.current
    val uiState by controller.uiState.collectAsState()
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    val device = uiState.deviceBook.activeDevice
    val projects = device?.allKnownProjects.orEmpty()
    val activeCwd = device?.effectiveProjectCwd

    var addingProject by remember { mutableStateOf(false) }
    var inputCwd by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }

    // Re-sync per-agent project lists from the bridge every time the drawer opens.
    LaunchedEffect(visible, device?.id) {
        if (visible && device != null) {
            controller.onIntent(BclawV2Intent.RefreshActiveDeviceMetadata)
        }
        if (!visible) {
            addingProject = false
            inputCwd = ""
            inputError = null
        }
    }

    fun submitNewProject() {
        val trimmed = inputCwd.trim().trimEnd('/')
        if (trimmed.isEmpty()) {
            inputError = "path required"
            return
        }
        if (!trimmed.startsWith('/')) {
            inputError = "absolute path only (must start with /)"
            return
        }
        controller.onIntent(BclawV2Intent.AddProjectToActiveDevice(CwdPath(trimmed)))
        addingProject = false
        inputCwd = ""
        inputError = null
        onDismissRequest()
    }

    BclawSideDrawer(
        visible = visible,
        edge = DrawerEdge.Left,
        onDismissRequest = onDismissRequest,
        width = 320.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = sp.pageGutter, vertical = sp.sp6),
        ) {
            Text(
                text = "PROJECTS",
                style = type.meta,
                color = colors.inkTertiary,
            )
            Spacer(Modifier.height(sp.sp1))
            Text(
                text = device?.displayName?.let { "on $it" }
                    ?: "no device paired",
                style = type.bodySmall,
                color = colors.inkTertiary,
            )
            Spacer(Modifier.height(sp.sp3))

            if (projects.isEmpty() || device == null) {
                EmptyProjectsHint()
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(sp.sp1)) {
                    projects.forEach { cwd ->
                        ProjectRow(
                            cwd = cwd,
                            isActive = cwd == activeCwd,
                            agents = device.agentsFor(cwd),
                            onClick = {
                                if (cwd != activeCwd) {
                                    controller.onIntent(BclawV2Intent.SelectProject(cwd))
                                }
                                onDismissRequest()
                            },
                        )
                    }
                }
            }

            if (device != null) {
                Spacer(Modifier.height(sp.sp3))
                if (!addingProject) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    addingProject = true
                                    inputError = null
                                },
                            )
                            .padding(horizontal = sp.sp3, vertical = sp.sp3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "+ add project",
                            style = type.body,
                            color = colors.accent,
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(sp.sp2)) {
                        MetroTextField(
                            value = inputCwd,
                            onValueChange = {
                                inputCwd = it
                                if (inputError != null) inputError = null
                            },
                            placeholder = "/absolute/path/to/project",
                            errorMessage = inputError,
                            mono = true,
                            singleLine = true,
                            imeAction = ImeAction.Done,
                            keyboardActions = KeyboardActions(onDone = { submitNewProject() }),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(sp.sp2),
                        ) {
                            MetroButton(
                                label = "cancel",
                                onClick = {
                                    addingProject = false
                                    inputCwd = ""
                                    inputError = null
                                },
                                variant = MetroButtonVariant.Ghost,
                                size = MetroButtonSize.Sm,
                            )
                            MetroButton(
                                label = "add",
                                onClick = { submitNewProject() },
                                variant = MetroButtonVariant.Accent,
                                size = MetroButtonSize.Sm,
                                mono = true,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(sp.sp4))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.borderSubtle),
            )
            Spacer(Modifier.height(sp.sp3))

            Text(
                text = "list syncs from each agent's config every time this drawer opens. codex pulls from `~/.codex/config.toml`; claude from `~/.claude/projects/`; gemini from `~/.gemini/projects.json`.",
                style = type.bodySmall,
                color = colors.inkTertiary,
            )
        }
    }
}

@Composable
private fun ProjectRow(
    cwd: CwdPath,
    isActive: Boolean,
    agents: List<AgentDescriptor>,
    onClick: () -> Unit,
) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isActive) colors.surfaceRaised else colors.surfaceOverlay)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = sp.sp3, vertical = sp.sp3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(
            color = if (isActive) colors.accent else colors.inkMuted,
            size = 8.dp,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = sp.sp3),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = leafName(cwd.value),
                style = type.h3,
                color = colors.inkPrimary,
            )
            Text(
                text = cwd.value,
                style = type.monoSmall,
                color = colors.inkTertiary,
            )
            if (agents.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(sp.sp2)) {
                    agents.forEach { agent ->
                        AgentChip(agent = agent)
                    }
                }
            }
        }
        if (isActive) {
            Text(
                text = "active",
                style = type.bodySmall,
                color = colors.accent,
            )
        }
    }
}

@Composable
private fun AgentChip(agent: AgentDescriptor) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing
    val accent = agentAccent(agent)
    Row(
        modifier = Modifier
            .background(colors.surfaceOverlay)
            .padding(horizontal = sp.sp2, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sp.sp1),
    ) {
        StatusDot(color = accent, size = 6.dp)
        Text(
            text = agent.id.value,
            style = type.monoSmall,
            color = colors.inkSecondary,
        )
    }
}

@Composable
private fun agentAccent(agent: AgentDescriptor): androidx.compose.ui.graphics.Color {
    val colors = BclawTheme.colors
    return when (agent.id.value.lowercase()) {
        "codex" -> colors.roleAgentCodex
        "claude", "claude-code" -> colors.roleAgentClaude
        "gemini" -> colors.roleAgentGemini
        "kimi" -> colors.roleAgentKimi
        else -> colors.roleAgentReserved
    }
}

@Composable
private fun EmptyProjectsHint() {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    Column(
        modifier = Modifier.padding(vertical = sp.sp3),
        verticalArrangement = Arrangement.spacedBy(sp.sp2),
    ) {
        Text(
            text = "no projects on this mac yet.",
            style = type.body,
            color = colors.inkSecondary,
        )
        Text(
            text = "run `codex`, `claude`, or `gemini` in any folder on the mac to register it — the cwd shows up here on next open.",
            style = type.bodySmall,
            color = colors.inkTertiary,
        )
    }
}

/** "/Users/you/projects/foo" → "foo"; fallback to full path. */
private fun leafName(path: String): String {
    val trimmed = path.trimEnd('/')
    if (trimmed.isEmpty()) return path
    return trimmed.substringAfterLast('/').ifBlank { trimmed }
}
