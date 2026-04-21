package com.bclaw.app.ui.workspaces

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bclaw.app.data.WorkspaceConfig
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.bclaw.app.domain.model.AgentSlot
import com.bclaw.app.domain.model.BclawUiState
import com.bclaw.app.domain.model.ChatThreadState
import com.bclaw.app.domain.model.TimelineItemUi
import com.bclaw.app.net.codex.ThreadSummary
import com.bclaw.app.ui.theme.BclawSpacing
import com.bclaw.app.ui.theme.LocalBclawColors
import com.bclaw.app.ui.theme.LocalBclawTypography
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Composable
fun BclawDrawerContent(
    drawerOpen: Boolean,
    workspaces: List<WorkspaceConfig>,
    uiState: BclawUiState,
    workspacePresenceById: Map<String, WorkspacePresenceUi>,
    currentWorkspaceId: String?,
    currentThreadId: String?,
    onSelectWorkspace: (WorkspaceConfig) -> Unit,
    onSelectThread: (WorkspaceConfig, String) -> Unit,
    onCreateThread: (WorkspaceConfig) -> Unit,
    onAddWorkspace: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectAgent: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBclawColors.current
    val typography = LocalBclawTypography.current
    LazyColumn(
        modifier = modifier
            .background(colors.surfaceNear)
            .testTag(if (drawerOpen) "drawer_panel_open" else "drawer_panel_closed"),
        contentPadding = PaddingValues(
            start = BclawSpacing.EdgeLeft,
            end = BclawSpacing.EdgeRight,
            top = BclawSpacing.SectionGap,
            bottom = BclawSpacing.SectionGap,
        ),
        verticalArrangement = Arrangement.spacedBy(BclawSpacing.SectionGap),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "bclaw",
                    modifier = Modifier.weight(1f),
                    style = typography.title,
                    color = colors.textPrimary,
                )
                Text(
                    text = "SETTINGS",
                    modifier = Modifier
                        .clickable(onClick = onOpenSettings)
                        .testTag("drawer_settings"),
                    style = typography.body,
                    color = colors.accentCyan,
                )
            }
        }
        if (uiState.availableAgents.isNotEmpty()) {
            item {
                AgentTabBar(
                    agents = uiState.availableAgents,
                    activeAgentName = uiState.activeAgentName,
                    onSelectAgent = onSelectAgent,
                )
            }
        }
        items(workspaces, key = { it.id }) { workspace ->
            WorkspaceDrawerSection(
                workspace = workspace,
                workspacePresence = workspacePresenceById[workspace.id] ?: WorkspacePresenceUi(
                    workspaceId = workspace.id,
                    connectionLabel = "offline",
                    connectionPhase = com.bclaw.app.domain.model.ConnectionPhase.Offline,
                    hasRunningTurn = false,
                ),
                threads = uiState.workspaceThreads[workspace.id]?.threads.orEmpty(),
                loading = uiState.workspaceThreads[workspace.id]?.loading == true,
                currentWorkspaceId = currentWorkspaceId,
                currentThreadId = currentThreadId,
                threadStates = uiState.threadStates,
                onSelectWorkspace = { onSelectWorkspace(workspace) },
                onSelectThread = { onSelectThread(workspace, it) },
                onCreateThread = { onCreateThread(workspace) },
            )
        }
        item {
            Text(
                text = "+ ADD WORKSPACE",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAddWorkspace)
                    .padding(vertical = 12.dp)
                    .testTag("drawer_add_workspace"),
                style = typography.body,
                color = colors.accentCyan,
            )
        }
    }
}

private const val COLLAPSED_THREAD_LIMIT = 8

@Composable
@Suppress("UNUSED_PARAMETER")
private fun WorkspaceDrawerSection(
    workspace: WorkspaceConfig,
    workspacePresence: WorkspacePresenceUi,
    threads: List<ThreadSummary>,
    loading: Boolean,
    currentWorkspaceId: String?,
    currentThreadId: String?,
    threadStates: Map<String, ChatThreadState>,
    onSelectWorkspace: () -> Unit,
    onSelectThread: (String) -> Unit,
    onCreateThread: () -> Unit,
) {
    val colors = LocalBclawColors.current
    val typography = LocalBclawTypography.current
    val selected = currentWorkspaceId == workspace.id
    var expanded by rememberSaveable(workspace.id) { mutableStateOf(selected) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceNear)
            .then(
                if (selected) {
                    Modifier.metroSelectionStripe(colors.accentCyan, 3.dp)
                } else {
                    Modifier
                },
            )
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = { expanded = !expanded })
                .testTag("workspace_row_${workspace.id}"),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = workspace.displayName,
                    modifier = Modifier.weight(1f),
                    style = typography.hero,
                    color = if (selected) colors.accentCyan else colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (expanded) "▾" else "▸",
                    style = typography.title,
                    color = colors.textDim,
                )
            }
            Text(
                text = workspace.cwd,
                style = typography.code,
                color = colors.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (threads.isNotEmpty()) {
                Text(
                    text = "${threads.size} threads",
                    style = typography.meta,
                    color = colors.textDim,
                )
            }
        }
        if (expanded) {
            if (loading && threads.isEmpty()) {
                Text(
                    text = "Loading threads...",
                    style = typography.meta,
                    color = colors.textMeta,
                )
            } else {
                val visibleThreads = threads.take(COLLAPSED_THREAD_LIMIT)
                visibleThreads.forEach { thread ->
                    DrawerThreadRow(
                        thread = thread,
                        current = currentThreadId == thread.id,
                        state = threadStates[thread.id],
                        onClick = { onSelectThread(thread.id) },
                    )
                }
                if (threads.size > COLLAPSED_THREAD_LIMIT) {
                    Text(
                        text = "${threads.size - COLLAPSED_THREAD_LIMIT} more threads",
                        style = typography.meta,
                        color = colors.textDim,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                    )
                }
            }
            Text(
                text = "+ NEW THREAD",
                modifier = Modifier
                    .clickable(onClick = onCreateThread)
                    .padding(top = 4.dp)
                    .testTag("new_thread_${workspace.id}"),
                style = typography.body,
                color = colors.accentCyan,
            )
        }
    }
}

@Composable
private fun DrawerThreadRow(
    thread: ThreadSummary,
    current: Boolean,
    state: ChatThreadState?,
    onClick: () -> Unit,
) {
    val colors = LocalBclawColors.current
    val typography = LocalBclawTypography.current
    val preview = state.runningPreviewLine()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
            .testTag("thread_row_${thread.id}"),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BclawSpacing.InlineGap),
        ) {
            Text(
                text = thread.name?.takeIf { it.isNotBlank() }
                    ?: thread.preview.takeIf { it.isNotBlank() }
                    ?: thread.id.take(12),
                modifier = Modifier.weight(1f),
                style = typography.body,
                color = if (current) colors.accentCyan else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = thread.updatedAt.toRelativeTimestamp(),
                style = typography.meta,
                color = colors.textMeta,
            )
        }
        if (!preview.isNullOrBlank()) {
            Text(
                text = preview,
                modifier = Modifier.testTag("drawer_preview_${thread.id}"),
                style = typography.meta,
                color = colors.textMeta,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun Modifier.metroSelectionStripe(color: androidx.compose.ui.graphics.Color, width: androidx.compose.ui.unit.Dp): Modifier {
    return drawBehind {
        drawRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset.Zero,
            size = androidx.compose.ui.geometry.Size(width.toPx(), size.height),
        )
    }
}

private fun ChatThreadState?.runningPreviewLine(): String? {
    val state = this ?: return null
    if (state.activeTurnId == null && state.thread?.status?.type != "active") return null
    return state.items
        .asReversed()
        .firstNotNullOfOrNull { item ->
            when (item) {
                is TimelineItemUi.AgentMessage -> item.text.lastMeaningfulLine()
                is TimelineItemUi.CommandExecution -> item.output.lastMeaningfulLine()
                is TimelineItemUi.Reasoning -> item.summary.lastMeaningfulLine()
                is TimelineItemUi.Error -> item.message.lastMeaningfulLine()
                else -> null
            }
        }
        ?.truncatePreview()
}

private fun String.lastMeaningfulLine(): String? {
    return lineSequence()
        .map { it.trim() }
        .lastOrNull { it.isNotEmpty() }
}

private fun String.truncatePreview(maxChars: Int = 40): String {
    return if (length <= maxChars) this else take(maxChars - 1) + "…"
}

@Composable
private fun AgentTabBar(
    agents: List<AgentSlot>,
    activeAgentName: String?,
    onSelectAgent: (String) -> Unit,
) {
    val colors = LocalBclawColors.current
    val typography = LocalBclawTypography.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        agents.forEach { agent ->
            val isActive = agent.name == activeAgentName
            Text(
                text = agent.displayName,
                modifier = Modifier
                    .clickable(onClick = { onSelectAgent(agent.name) })
                    .background(if (isActive) colors.accentCyan else colors.surfaceElevated)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                style = typography.body,
                color = if (isActive) colors.terminalBlack else colors.textMeta,
            )
        }
    }
}

private fun Long.toRelativeTimestamp(): String {
    val instant = Instant.ofEpochSecond(this)
    val now = Instant.now()
    val minutes = ChronoUnit.MINUTES.between(instant, now)
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        minutes < 24 * 60 -> "${minutes / 60}h"
        else -> instant.atZone(ZoneId.systemDefault()).toLocalDate().toString()
    }
}
