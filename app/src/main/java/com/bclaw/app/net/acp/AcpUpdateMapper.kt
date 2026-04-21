package com.bclaw.app.net.acp

import com.bclaw.app.domain.model.TimelineItemUi
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.UUID

/**
 * Maps ACP `session/update` notifications to bclaw's TimelineItemUi model.
 *
 * ACP session/update.update is a tagged union on the "sessionUpdate" field:
 * - user_message_chunk
 * - agent_message_chunk
 * - agent_thought_chunk
 * - tool_call
 * - tool_call_update
 * - plan
 * - session_info_update
 * - available_commands_update
 * - current_mode_update
 * - config_option_update
 */
object AcpUpdateMapper {

    /**
     * Extracts the update type from a session/update notification.
     */
    fun updateType(update: JsonObject): String {
        return update["sessionUpdate"]?.jsonPrimitive?.contentOrNull ?: "unknown"
    }

    /**
     * Maps an ACP agent_message_chunk to text delta.
     * Returns the text content, or null if not a text block.
     */
    fun extractAgentMessageText(update: JsonObject): String? {
        val content = update["content"]?.jsonObject ?: return null
        if (content["type"]?.jsonPrimitive?.contentOrNull != "text") return null
        return content["text"]?.jsonPrimitive?.contentOrNull
    }

    /**
     * Maps an ACP agent_thought_chunk to reasoning text delta.
     */
    fun extractThoughtText(update: JsonObject): String? {
        val content = update["content"]?.jsonObject ?: return null
        if (content["type"]?.jsonPrimitive?.contentOrNull != "text") return null
        return content["text"]?.jsonPrimitive?.contentOrNull
    }

    /**
     * Maps an ACP user_message_chunk to user message text.
     */
    fun extractUserMessageText(update: JsonObject): String? {
        val content = update["content"]?.jsonObject ?: return null
        if (content["type"]?.jsonPrimitive?.contentOrNull != "text") return null
        return content["text"]?.jsonPrimitive?.contentOrNull
    }

    /**
     * Maps an ACP tool_call to a TimelineItemUi.
     * tool_call has: toolCallId, title, kind, status, locations?, content?
     */
    fun toolCallToTimelineItem(
        update: JsonObject,
        syntheticTurnId: String,
    ): TimelineItemUi {
        val toolCallId = update["toolCallId"]?.jsonPrimitive?.contentOrNull
            ?: "tc-${UUID.randomUUID()}"
        val title = update["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val kind = update["kind"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val status = update["status"]?.jsonPrimitive?.contentOrNull.orEmpty()

        // Codex ACP adapter uses title="exec_command" with rawInput instead of kind="execute"
        val rawInput = update["rawInput"]?.jsonObject
        val rawOutput = update["rawOutput"]

        // Determine effective kind from ACP `kind` field or Codex `title` convention
        val effectiveKind = kind.ifBlank {
            when {
                title.contains("exec_command", ignoreCase = true) -> "execute"
                title.contains("file_edit", ignoreCase = true) ||
                    title.contains("apply_diff", ignoreCase = true) ||
                    title.contains("write", ignoreCase = true) -> "edit"
                title.contains("read", ignoreCase = true) -> "read"
                else -> ""
            }
        }

        val command = rawInput?.get("cmd")?.jsonPrimitive?.contentOrNull ?: title
        val cwd = rawInput?.get("workdir")?.jsonPrimitive?.contentOrNull
            ?: extractFirstLocation(update)
        val output = rawOutput?.let {
            try { it.jsonPrimitive.contentOrNull.orEmpty() } catch (_: Exception) { it.toString() }
        } ?: extractTerminalOutput(update)

        return when (effectiveKind) {
            "execute" -> TimelineItemUi.CommandExecution(
                id = toolCallId,
                turnId = syntheticTurnId,
                command = command,
                cwd = cwd,
                status = mapToolCallStatus(status),
                output = output,
                exitCode = null,
                durationMs = null,
            )
            "edit", "write" -> TimelineItemUi.FileChange(
                id = toolCallId,
                turnId = syntheticTurnId,
                paths = extractLocationPaths(update),
                status = mapToolCallStatus(status),
                diff = extractDiffContent(update).ifBlank { output },
                changes = emptyList(),
            )
            "read" -> TimelineItemUi.CommandExecution(
                id = toolCallId,
                turnId = syntheticTurnId,
                command = command,
                cwd = cwd,
                status = mapToolCallStatus(status),
                output = output,
                exitCode = null,
                durationMs = null,
            )
            else -> TimelineItemUi.DynamicToolCall(
                id = toolCallId,
                turnId = syntheticTurnId,
                tool = title.ifBlank { kind },
                arguments = rawInput?.toString().orEmpty().ifBlank { extractContentText(update) },
                status = mapToolCallStatus(status),
                success = status == "completed",
                durationMs = null,
            )
        }
    }

    /**
     * Updates an existing timeline item with tool_call_update data.
     * Returns updated fields as a map, or null if nothing to update.
     */
    fun extractToolCallUpdate(update: JsonObject): ToolCallUpdateData {
        val toolCallId = update["toolCallId"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val status = update["status"]?.jsonPrimitive?.contentOrNull
        val content = update["content"] as? JsonArray

        // Codex ACP adapter sends rawOutput as a string instead of content[]
        val rawOutput = update["rawOutput"]?.let {
            try { it.jsonPrimitive.contentOrNull } catch (_: Exception) { it.toString() }
        }

        return ToolCallUpdateData(
            toolCallId = toolCallId,
            status = status?.let { mapToolCallStatus(it) },
            appendOutput = rawOutput ?: content?.let { extractTextFromContentArray(it) },
            diffContent = content?.let { extractDiffFromContentArray(it) },
        )
    }

    /**
     * Maps an ACP plan update to a TimelineItemUi.Plan.
     */
    fun planToTimelineItem(
        update: JsonObject,
        syntheticTurnId: String,
    ): TimelineItemUi.Plan {
        val entries = update["entries"] as? JsonArray
        val text = entries?.joinToString("\n") { entry ->
            val obj = entry.jsonObject
            val content = obj["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val status = obj["status"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val statusIcon = when (status) {
                "completed" -> "✓"
                "in_progress" -> "→"
                else -> "○"
            }
            "$statusIcon $content"
        }.orEmpty()

        return TimelineItemUi.Plan(
            id = "plan-${UUID.randomUUID()}",
            turnId = syntheticTurnId,
            text = text,
        )
    }

    /**
     * Extracts session title from session_info_update.
     */
    fun extractSessionTitle(update: JsonObject): String? {
        return update["title"]?.jsonPrimitive?.contentOrNull
    }

    // ── Helpers ──

    private fun mapToolCallStatus(acpStatus: String): String {
        return when (acpStatus) {
            "pending" -> "in_progress"
            "running" -> "in_progress"
            "completed" -> "completed"
            "cancelled" -> "interrupted"
            "failed" -> "failed"
            else -> acpStatus
        }
    }

    private fun extractFirstLocation(update: JsonObject): String {
        val locations = update["locations"] as? JsonArray ?: return ""
        val first = locations.firstOrNull()?.jsonObject ?: return ""
        return first["path"]?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    private fun extractLocationPaths(update: JsonObject): List<String> {
        val locations = update["locations"] as? JsonArray ?: return emptyList()
        return locations.mapNotNull { it.jsonObject["path"]?.jsonPrimitive?.contentOrNull }
    }

    private fun extractTerminalOutput(update: JsonObject): String {
        val content = update["content"] as? JsonArray ?: return ""
        return content.mapNotNull { element ->
            val obj = element.jsonObject
            when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                "terminal" -> obj["output"]?.jsonPrimitive?.contentOrNull
                "content" -> obj["content"]?.jsonObject
                    ?.takeIf { it["type"]?.jsonPrimitive?.contentOrNull == "text" }
                    ?.get("text")?.jsonPrimitive?.contentOrNull
                else -> null
            }
        }.joinToString("\n")
    }

    private fun extractDiffContent(update: JsonObject): String {
        val content = update["content"] as? JsonArray ?: return ""
        return content.mapNotNull { element ->
            val obj = element.jsonObject
            if (obj["type"]?.jsonPrimitive?.contentOrNull == "diff") {
                val path = obj["path"]?.jsonPrimitive?.contentOrNull.orEmpty()
                "--- $path\n+++ $path"
            } else null
        }.joinToString("\n\n")
    }

    private fun extractContentText(update: JsonObject): String {
        val content = update["content"] as? JsonArray ?: return ""
        return extractTextFromContentArray(content)
    }

    private fun extractTextFromContentArray(content: JsonArray): String {
        return content.mapNotNull { element ->
            val obj = element.jsonObject
            when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                "content" -> obj["content"]?.jsonObject
                    ?.takeIf { it["type"]?.jsonPrimitive?.contentOrNull == "text" }
                    ?.get("text")?.jsonPrimitive?.contentOrNull
                "terminal" -> obj["output"]?.jsonPrimitive?.contentOrNull
                else -> null
            }
        }.joinToString("\n")
    }

    private fun extractDiffFromContentArray(content: JsonArray): String? {
        val diffs = content.mapNotNull { element ->
            val obj = element.jsonObject
            if (obj["type"]?.jsonPrimitive?.contentOrNull == "diff") {
                obj["path"]?.jsonPrimitive?.contentOrNull
            } else null
        }
        return if (diffs.isNotEmpty()) diffs.joinToString("\n") else null
    }
}

data class ToolCallUpdateData(
    val toolCallId: String,
    val status: String?,
    val appendOutput: String?,
    val diffContent: String?,
)
