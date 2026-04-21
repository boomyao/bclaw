package com.bclaw.app.net.codex

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class ClientInfo(
    val name: String,
    val title: String,
    val version: String,
)

@Serializable
data class InitializeCapabilities(
    val experimentalApi: Boolean = false,
    val optOutNotificationMethods: List<String> = emptyList(),
)

@Serializable
data class InitializeParams(
    val clientInfo: ClientInfo,
    val capabilities: InitializeCapabilities = InitializeCapabilities(),
)

@Serializable
data class InitializeResult(
    val userAgent: String? = null,
    val codexHome: String? = null,
    val platformFamily: String? = null,
    val platformOs: String? = null,
)

@Serializable
data class ThreadStatus(
    val type: String,
    val activeFlags: List<String> = emptyList(),
)

@Serializable
data class GitInfo(
    val sha: String? = null,
    val branch: String? = null,
    val originUrl: String? = null,
)

@Serializable
data class ThreadSummary(
    val id: String,
    val preview: String = "",
    val modelProvider: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val status: ThreadStatus = ThreadStatus(type = "notLoaded"),
    val cwd: String = "",
    val cliVersion: String = "",
    val name: String? = null,
    val turns: List<TurnModel> = emptyList(),
    val path: String? = null,
    val source: String = "appServer",
    val ephemeral: Boolean = false,
    val forkedFromId: String? = null,
    val gitInfo: GitInfo? = null,
    val agentNickname: String? = null,
    val agentRole: String? = null,
)

@Serializable
data class ThreadListResponse(
    val data: List<ThreadSummary>,
    val nextCursor: String? = null,
)

@Serializable
data class ThreadReadResponse(
    val thread: ThreadSummary,
)

@Serializable
data class ThreadStartResponse(
    val thread: ThreadSummary,
)

@Serializable
data class ThreadResumeResponse(
    val thread: ThreadSummary,
    val cwd: String? = null,
    val approvalPolicy: JsonElement? = null,
    val sandbox: JsonElement? = null,
    val model: String? = null,
    val modelProvider: String? = null,
)

@Serializable
data class TurnError(
    val message: String,
    val codexErrorInfo: JsonElement? = null,
    val additionalDetails: String? = null,
)

@Serializable
data class TurnModel(
    val id: String,
    val status: String,
    val items: List<JsonObject> = emptyList(),
    val error: TurnError? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val durationMs: Long? = null,
)

@Serializable
data class TurnStartResponse(
    val turn: TurnModel,
)

@Serializable
data class ThreadStartedNotification(
    val thread: ThreadSummary,
)

@Serializable
data class ThreadClosedNotification(
    val threadId: String,
)

@Serializable
data class ThreadStatusChangedNotification(
    val threadId: String,
    val status: ThreadStatus,
)

@Serializable
data class ThreadNameUpdatedNotification(
    val threadId: String,
    val name: String?,
)

@Serializable
data class ThreadTokenUsageUpdatedNotification(
    val threadId: String,
    val turnId: String? = null,
    val tokenUsage: JsonElement? = null,
)

@Serializable
data class TurnStartedNotification(
    val threadId: String,
    val turn: TurnModel,
)

@Serializable
data class TurnCompletedNotification(
    val threadId: String,
    val turn: TurnModel,
)

@Serializable
data class TurnDiffUpdatedNotification(
    val threadId: String,
    val turnId: String,
    val diff: String,
)

@Serializable
data class ItemLifecycleNotification(
    val threadId: String,
    val turnId: String,
    val item: JsonObject,
)

@Serializable
data class AgentMessageDeltaNotification(
    val threadId: String,
    val turnId: String,
    val itemId: String,
    val delta: String,
)

@Serializable
data class CommandExecutionOutputDeltaNotification(
    val threadId: String,
    val turnId: String,
    val itemId: String,
    val delta: String,
)

@Serializable
data class ReasoningSummaryTextDeltaNotification(
    val threadId: String,
    val turnId: String,
    val itemId: String,
    val summaryIndex: Long,
    val delta: String,
)

@Serializable
data class ErrorNotification(
    val threadId: String,
    val turnId: String,
    val error: TurnError,
    val willRetry: Boolean,
)

@Serializable
data class ServerRequestResolvedNotification(
    val threadId: String,
    val requestId: JsonElement,
)

@Serializable
data class JsonRpcErrorData(
    val code: Int,
    val message: String,
    val data: JsonElement? = null,
)

@Serializable
data class JsonRpcResponseEnvelope(
    val id: JsonElement,
    val result: JsonElement? = null,
    val error: JsonRpcErrorData? = null,
)

@Serializable
data class ItemApprovalDecisionResponse(
    val decision: String,
)

@Serializable
data class ToolUserInputAnswer(
    val answers: List<String>,
)

@Serializable
data class ToolRequestUserInputResponse(
    val answers: Map<String, ToolUserInputAnswer>,
)

@Serializable
data class PermissionsRequestApprovalResponse(
    val permissions: JsonObject,
    val scope: String = "turn",
)

@Serializable
data class McpServerElicitationResponse(
    val action: String,
    val content: JsonElement? = null,
)

object CodexJson {
    val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }
}
