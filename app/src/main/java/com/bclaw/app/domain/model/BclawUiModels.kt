package com.bclaw.app.domain.model

import com.bclaw.app.data.WorkspaceConfig
import com.bclaw.app.net.codex.ThreadSummary

enum class ConnectionPhase {
    Idle,
    Connecting,
    Connected,
    Reconnecting,
    Offline,
    Error,
    AuthFailed,
}

data class WorkspaceThreadsState(
    val workspaceId: String,
    val threads: List<ThreadSummary> = emptyList(),
    val nextCursor: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

sealed interface TimelineItemUi {
    val id: String
    val turnId: String

    data class FileChangeEntry(
        val path: String,
        val additions: Int,
        val deletions: Int,
        val diff: String,
    )

    data class UserMessage(
        override val id: String,
        override val turnId: String,
        val text: String,
        val optimistic: Boolean = false,
        val timestampEpochMillis: Long? = null,
    ) : TimelineItemUi

    data class AgentMessage(
        override val id: String,
        override val turnId: String,
        val text: String,
    ) : TimelineItemUi

    data class Reasoning(
        override val id: String,
        override val turnId: String,
        val summary: String,
    ) : TimelineItemUi

    data class CommandExecution(
        override val id: String,
        override val turnId: String,
        val command: String,
        val cwd: String,
        val status: String,
        val output: String,
        val exitCode: Int? = null,
        val durationMs: Long? = null,
    ) : TimelineItemUi

    data class FileChange(
        override val id: String,
        override val turnId: String,
        val paths: List<String>,
        val status: String,
        val diff: String,
        val changes: List<FileChangeEntry> = emptyList(),
    ) : TimelineItemUi

    data class McpToolCall(
        override val id: String,
        override val turnId: String,
        val server: String,
        val tool: String,
        val arguments: String,
        val status: String,
        val result: String,
        val error: String,
        val durationMs: Long? = null,
    ) : TimelineItemUi

    data class WebSearch(
        override val id: String,
        override val turnId: String,
        val query: String,
        val action: String,
    ) : TimelineItemUi

    data class Plan(
        override val id: String,
        override val turnId: String,
        val text: String,
    ) : TimelineItemUi

    data class ContextCompaction(
        override val id: String,
        override val turnId: String,
    ) : TimelineItemUi

    data class DynamicToolCall(
        override val id: String,
        override val turnId: String,
        val tool: String,
        val arguments: String,
        val status: String,
        val success: Boolean? = null,
        val durationMs: Long? = null,
    ) : TimelineItemUi

    data class SubAgent(
        override val id: String,
        override val turnId: String,
        val tool: String,
        val prompt: String,
        val status: String,
        val model: String,
    ) : TimelineItemUi

    data class Unsupported(
        override val id: String,
        override val turnId: String,
        val kind: String,
    ) : TimelineItemUi

    data class Error(
        override val id: String,
        override val turnId: String,
        val message: String,
    ) : TimelineItemUi
}

data class ChatThreadState(
    val thread: ThreadSummary? = null,
    val items: List<TimelineItemUi> = emptyList(),
    val activeTurnId: String? = null,
    val latestTurnId: String? = null,
    val latestTurnStatus: String? = null,
    val historyLoaded: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val diffByTurn: Map<String, String> = emptyMap(),
)

data class AgentSlot(
    val name: String,
    val displayName: String,
)

data class BclawUiState(
    val connectionPhase: ConnectionPhase = ConnectionPhase.Idle,
    val availableAgents: List<AgentSlot> = emptyList(),
    val activeAgentName: String? = null,
    val workspaces: List<WorkspaceConfig> = emptyList(),
    val workspaceThreads: Map<String, WorkspaceThreadsState> = emptyMap(),
    val threadStates: Map<String, ChatThreadState> = emptyMap(),
    val statusMessage: String? = null,
    val protocolWarning: String? = null,
)
