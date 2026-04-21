package com.bclaw.app.ui.workspaces

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.bclaw.app.data.WorkspaceConfig
import com.bclaw.app.domain.model.BclawUiState
import com.bclaw.app.domain.model.ChatThreadState
import com.bclaw.app.domain.model.ConnectionPhase
import com.bclaw.app.domain.model.TimelineItemUi
import com.bclaw.app.ui.components.StatusDot
import com.bclaw.app.ui.components.TwoDotPulseIndicator
import com.bclaw.app.ui.theme.BclawSpacing
import com.bclaw.app.ui.theme.LocalBclawColors
import com.bclaw.app.ui.theme.LocalBclawTypography
import androidx.compose.material3.Text

data class WorkspacePresenceUi(
    val workspaceId: String,
    val connectionLabel: String,
    val connectionPhase: ConnectionPhase,
    val hasRunningTurn: Boolean,
    val runningPreview: String? = null,
)

fun buildWorkspacePresence(
    workspace: WorkspaceConfig,
    uiState: BclawUiState,
): WorkspacePresenceUi {
    val activeThreadStates = uiState.threadStates.values.filter { state ->
        state.thread?.cwd == workspace.cwd &&
            (state.activeTurnId != null || state.thread.status.type == "active")
    }
    val hasRunningTurn = activeThreadStates.isNotEmpty() ||
        uiState.workspaceThreads[workspace.id]?.threads.orEmpty().any { it.status.type == "active" }
    return WorkspacePresenceUi(
        workspaceId = workspace.id,
        connectionLabel = uiState.connectionPhase.toWorkspaceConnectionLabel(),
        connectionPhase = uiState.connectionPhase,
        hasRunningTurn = hasRunningTurn,
        runningPreview = activeThreadStates.firstNotNullOfOrNull { it.runningPreviewLine() },
    )
}

@Composable
fun WorkspacePresenceSummary(
    presence: WorkspacePresenceUi,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBclawColors.current
    val typography = LocalBclawTypography.current
    val labelColor = when (presence.connectionPhase) {
        ConnectionPhase.Connected -> colors.textPrimary
        ConnectionPhase.Connecting -> colors.accentCyan
        ConnectionPhase.Reconnecting -> colors.warningAmber
        ConnectionPhase.Offline,
        ConnectionPhase.AuthFailed,
        ConnectionPhase.Error,
        ConnectionPhase.Idle,
        -> colors.dangerRed
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BclawSpacing.DotToLabel),
        ) {
            StatusDot(
                connectionPhase = presence.connectionPhase,
                modifier = Modifier.testTag("workspace_connection_${presence.workspaceId}"),
            )
            Text(
                text = presence.connectionLabel,
                modifier = Modifier.testTag("workspace_state_label_${presence.workspaceId}"),
                style = typography.meta,
                color = labelColor,
            )
            if (presence.hasRunningTurn) {
                TwoDotPulseIndicator(
                    modifier = Modifier.testTag("workspace_running_${presence.workspaceId}"),
                )
            }
        }
        if (!presence.runningPreview.isNullOrBlank()) {
            Text(
                text = presence.runningPreview,
                modifier = Modifier.testTag("workspace_preview_${presence.workspaceId}"),
                style = typography.meta,
                color = colors.textMeta,
            )
        }
    }
}

private fun ConnectionPhase.toWorkspaceConnectionLabel(): String {
    return when (this) {
        ConnectionPhase.Connected -> "connected"
        ConnectionPhase.Connecting -> "connecting…"
        ConnectionPhase.Reconnecting -> "reconnecting"
        ConnectionPhase.AuthFailed -> "auth failed"
        ConnectionPhase.Offline,
        ConnectionPhase.Error,
        ConnectionPhase.Idle,
        -> "offline"
    }
}

private fun ChatThreadState.runningPreviewLine(): String? {
    return items
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
