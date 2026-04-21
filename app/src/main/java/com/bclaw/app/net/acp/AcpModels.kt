package com.bclaw.app.net.acp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// ──────────────────────────────────────────────
// Initialize
// ──────────────────────────────────────────────

@Serializable
data class AcpClientInfo(
    val name: String = "bclaw",
    val title: String = "bclaw",
    val version: String = "0.1.0",
)

@Serializable
data class AcpFileSystemCapability(
    val readTextFile: Boolean = false,
    val writeTextFile: Boolean = false,
)

@Serializable
data class AcpClientCapabilities(
    val fs: AcpFileSystemCapability = AcpFileSystemCapability(),
    val terminal: Boolean = false,
)

@Serializable
data class AcpInitializeParams(
    val protocolVersion: Int = 1,
    val clientCapabilities: AcpClientCapabilities = AcpClientCapabilities(),
    val clientInfo: AcpClientInfo = AcpClientInfo(),
)

@Serializable
data class AcpPromptCapabilities(
    val image: Boolean = false,
    val audio: Boolean = false,
    val embeddedContext: Boolean = false,
)

@Serializable
data class AcpMcpCapabilities(
    val http: Boolean = false,
    val sse: Boolean = false,
)

@Serializable
data class AcpSessionListCapability(
    val placeholder: String? = null, // empty object in spec
)

@Serializable
data class AcpSessionCapabilities(
    val list: AcpSessionListCapability? = null,
)

@Serializable
data class AcpAgentCapabilities(
    val loadSession: Boolean = false,
    val promptCapabilities: AcpPromptCapabilities? = null,
    val mcpCapabilities: AcpMcpCapabilities? = null,
    val sessionCapabilities: AcpSessionCapabilities? = null,
)

@Serializable
data class AcpAgentInfo(
    val name: String = "",
    val title: String = "",
    val version: String = "",
)

@Serializable
data class AcpInitializeResult(
    val protocolVersion: Int = 1,
    val agentCapabilities: AcpAgentCapabilities = AcpAgentCapabilities(),
    val agentInfo: AcpAgentInfo? = null,
    val authMethods: List<JsonElement> = emptyList(),
)

// ──────────────────────────────────────────────
// Session
// ──────────────────────────────────────────────

@Serializable
data class AcpSessionNewParams(
    val cwd: String,
    val mcpServers: List<JsonElement> = emptyList(),
)

@Serializable
data class AcpSessionNewResult(
    val sessionId: String,
    val modes: List<JsonElement>? = null,
    val configOptions: List<JsonElement>? = null,
)

@Serializable
data class AcpSessionListParams(
    val cwd: String? = null,
    val cursor: String? = null,
)

@Serializable
data class AcpSessionInfo(
    val sessionId: String,
    val cwd: String? = null,
    val title: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class AcpSessionListResult(
    val sessions: List<AcpSessionInfo> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class AcpSessionLoadParams(
    val sessionId: String,
    val cwd: String,
    val mcpServers: List<JsonElement> = emptyList(),
)

// ──────────────────────────────────────────────
// Prompt (turn)
// ──────────────────────────────────────────────

@Serializable
data class AcpContentBlock(
    val type: String,       // "text", "image", "audio", "resource", "resource_link"
    val text: String? = null,
    val mimeType: String? = null,
    val data: String? = null,
    val uri: String? = null,
)

@Serializable
data class AcpPromptParams(
    val sessionId: String,
    val prompt: List<AcpContentBlock>,
)

@Serializable
data class AcpPromptResult(
    val stopReason: String,  // "end_turn", "cancelled", "max_turns", "error"
)

@Serializable
data class AcpSessionCancelParams(
    val sessionId: String,
)

// ──────────────────────────────────────────────
// session/update notification payloads
// ──────────────────────────────────────────────

// The session/update notification has params: { sessionId, update }
// update is a tagged union on "sessionUpdate" field.
// We parse it generically and dispatch by sessionUpdate type.

@Serializable
data class AcpSessionUpdateNotification(
    val sessionId: String,
    val update: JsonObject,
)

// ──────────────────────────────────────────────
// session/request_permission (agent → client)
// ──────────────────────────────────────────────

@Serializable
data class AcpToolCallInfo(
    val toolCallId: String = "",
    val title: String = "",
    val kind: String = "",  // "read", "edit", "execute", etc.
    val status: String = "",
)

@Serializable
data class AcpPermissionOption(
    val optionId: String,
    val name: String,
    val kind: String,   // "allow_once", "allow_always", "reject_once", etc.
)

@Serializable
data class AcpRequestPermissionParams(
    val sessionId: String,
    val toolCall: AcpToolCallInfo,
    val options: List<AcpPermissionOption> = emptyList(),
)

@Serializable
data class AcpPermissionOutcome(
    val outcome: String,       // "selected", "cancelled"
    val optionId: String? = null,
)

@Serializable
data class AcpRequestPermissionResult(
    val outcome: AcpPermissionOutcome,
)
