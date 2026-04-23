package com.bclaw.app.net.acp

import com.bclaw.app.domain.v2.TimelineItem
import com.bclaw.app.domain.v2.ToolStatus
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Pure reducer: apply one ACP `session/update` payload onto a timeline snapshot.
 *
 * Called from the controller's Delegate inside the WebSocket read loop. Returns a new list
 * instance so StateFlow diff'ing sees a change; structurally-shared tail is fine — LazyColumn
 * uses [TimelineItem.id] as key.
 *
 * Dispatches on the `sessionUpdate` tagged-union discriminator:
 *   - `user_message_chunk` · usually emitted in `session/load` replay; ignored here because
 *     the client appends its own optimistic UserMessage at send time
 *   - `agent_message_chunk` · streams into the last [TimelineItem.AgentMessage] (or starts one)
 *   - `agent_thought_chunk` · streams into [TimelineItem.Reasoning]
 *   - `tool_call` · appends a CommandExecution or FileChange, keyed by `toolCallId`
 *   - `tool_call_update` · mutates the existing tool card in place
 *   - `session_info_update` · NOT a timeline item (title lives on [com.bclaw.app.domain.v2.TabState])
 *   - anything else · [TimelineItem.Unsupported] row
 *
 * Spec variants come from `@zed-industries/agent-client-protocol`:
 *   user_message_chunk · agent_message_chunk · agent_thought_chunk ·
 *   tool_call · tool_call_update · plan · available_commands_update · current_mode_update
 *
 * Codex emits two extensions on top of the spec:
 *   session_info_update — title; consumed in BclawV2Controller.handleSessionUpdate
 *   usage_update        — token counters; not surfaced yet, silently dropped
 *
 * Anything else lands as [TimelineItem.Unsupported] only when it *might* be a renderable item
 * we forgot. Known metadata-only variants are listed in [METADATA_ONLY_UPDATES] so a future
 * agent extension stays quiet until we explicitly want it on the timeline.
 */
object AcpTimelineReducer {

    private val METADATA_ONLY_UPDATES = setOf(
        "session_info_update",       // title — consumed in controller
        "current_mode_update",       // header chip target (batch 4)
        "available_commands_update", // commands palette (batch 4)
        "plan",                      // plan card (batch 4)
        "usage_update",              // codex token counters — footer/strip target (batch 4)
    )

    /**
     * Reduce one update onto [existing]. Returns a new list; the caller is responsible for
     * storing it.
     */
    fun reduce(existing: List<TimelineItem>, update: JsonObject, nowEpochMs: Long): List<TimelineItem> {
        return when (val kind = update["sessionUpdate"]?.jsonPrimitive?.contentOrNull) {
            "user_message_chunk" -> appendUserChunk(existing, update, nowEpochMs)
            "agent_message_chunk" -> appendAgentChunk(existing, update, nowEpochMs)
            "agent_thought_chunk" -> appendThoughtChunk(existing, update, nowEpochMs)
            "tool_call" -> appendToolCall(existing, update, nowEpochMs)
            "tool_call_update" -> applyToolCallUpdate(existing, update)
            in METADATA_ONLY_UPDATES -> existing
            null -> existing // malformed — drop
            else -> existing + TimelineItem.Unsupported(
                createdAtEpochMs = nowEpochMs,
                kind = kind,
            )
        }
    }

    // ── user_message_chunk → UserMessage (drop optimistic echo / append on replay) ──
    //
    // Real-time send path: sendPrompt appends an optimistic UserMessage(streaming=true) holding
    // the full prompt before dispatching session/prompt. Codex echoes the prompt back as a
    // user_message_chunk — the optimistic already has it, so the echo is dropped.
    //
    // Replay path (session/load): no optimistic exists, so the chunk creates a UserMessage row.
    // We mark replay items streaming=false so they don't carry a stale caret until the next
    // turn's freezeStreaming runs.
    private fun appendUserChunk(
        existing: List<TimelineItem>,
        update: JsonObject,
        nowEpochMs: Long,
    ): List<TimelineItem> {
        val tail = existing.lastOrNull()
        if (tail is TimelineItem.UserMessage && tail.streaming) {
            // Optimistic in place — codex's echo would duplicate. Drop it.
            return existing
        }
        val text = extractTextFromContentBlock(update) ?: return existing
        return existing + TimelineItem.UserMessage(
            createdAtEpochMs = nowEpochMs,
            text = text,
        )
    }

    // ── agent_message_chunk → AgentMessage (create or extend last) ───────

    private fun appendAgentChunk(
        existing: List<TimelineItem>,
        update: JsonObject,
        nowEpochMs: Long,
    ): List<TimelineItem> {
        val text = extractTextFromContentBlock(update) ?: return existing
        val tail = existing.lastOrNull()
        return if (tail is TimelineItem.AgentMessage && tail.streaming) {
            existing.toMutableList().apply {
                set(lastIndex, tail.copy(text = tail.text + text))
            }
        } else {
            existing + TimelineItem.AgentMessage(
                createdAtEpochMs = nowEpochMs,
                text = text,
                streaming = true,
            )
        }
    }

    // ── agent_thought_chunk → Reasoning ──────────────────────────────────

    private fun appendThoughtChunk(
        existing: List<TimelineItem>,
        update: JsonObject,
        nowEpochMs: Long,
    ): List<TimelineItem> {
        val text = extractTextFromContentBlock(update) ?: return existing
        val tail = existing.lastOrNull()
        return if (tail is TimelineItem.Reasoning && tail.streaming) {
            existing.toMutableList().apply {
                set(lastIndex, tail.copy(summary = tail.summary + text))
            }
        } else {
            existing + TimelineItem.Reasoning(
                createdAtEpochMs = nowEpochMs,
                summary = text,
                streaming = true,
            )
        }
    }

    // ── tool_call → CommandExecution | FileChange ────────────────────────

    private fun appendToolCall(
        existing: List<TimelineItem>,
        update: JsonObject,
        nowEpochMs: Long,
    ): List<TimelineItem> {
        val toolCallId = update["toolCallId"]?.jsonPrimitive?.contentOrNull
            ?: return existing + TimelineItem.Unsupported(
                createdAtEpochMs = nowEpochMs,
                kind = "tool_call (no id)",
            )
        val title = update["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val kind = update["kind"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val status = mapStatus(update["status"]?.jsonPrimitive?.contentOrNull.orEmpty())

        // Codex's `view_image` tool: title is literally "view_image" and the image payload
        // rides in a later tool_call_update.rawOutput array. Emit a placeholder AgentImages
        // row up front with the path; images populate on update.
        if (title == "view_image") {
            val sourcePath = update["rawInput"]?.jsonObject
                ?.get("path")?.jsonPrimitive?.contentOrNull
            val initialImages = extractImageUrlsFromRawOutput(update["rawOutput"])
            return existing + TimelineItem.AgentImages(
                id = toolCallId,
                createdAtEpochMs = nowEpochMs,
                title = title,
                status = status,
                sourcePath = sourcePath,
                imageUrls = initialImages,
            )
        }

        val effectiveKind = kind.ifBlank { inferKindFromTitle(title) }

        val item = when (effectiveKind) {
            "execute", "read" -> {
                val command = update["rawInput"]?.jsonObject?.get("cmd")?.jsonPrimitive?.contentOrNull
                    ?: title.ifBlank { "(command)" }
                val cwd = update["rawInput"]?.jsonObject?.get("workdir")?.jsonPrimitive?.contentOrNull
                TimelineItem.CommandExecution(
                    id = toolCallId,
                    createdAtEpochMs = nowEpochMs,
                    command = command,
                    cwd = cwd,
                    status = status,
                    outputTail = "",
                )
            }
            "edit", "write" -> TimelineItem.FileChange(
                id = toolCallId,
                createdAtEpochMs = nowEpochMs,
                paths = extractPathsFromLocations(update),
                status = status,
            )
            else -> TimelineItem.Unsupported(
                createdAtEpochMs = nowEpochMs,
                kind = "tool_call · $effectiveKind",
            )
        }
        return existing + item
    }

    // ── tool_call_update → mutate existing card ──────────────────────────

    private fun applyToolCallUpdate(existing: List<TimelineItem>, update: JsonObject): List<TimelineItem> {
        val toolCallId = update["toolCallId"]?.jsonPrimitive?.contentOrNull ?: return existing
        val idx = existing.indexOfFirst { it.id == toolCallId }
        if (idx < 0) return existing

        val statusStr = update["status"]?.jsonPrimitive?.contentOrNull
        val newStatus = statusStr?.let { mapStatus(it) }

        val rawOutputText = update["rawOutput"]?.let { raw ->
            // String rawOutput (exec_command output). Array rawOutput is handled separately
            // by the AgentImages branch below, so it doesn't land here as stringified JSON.
            runCatching { raw.jsonPrimitive.contentOrNull }.getOrNull()
        }
        val rawOutputImages = extractImageUrlsFromRawOutput(update["rawOutput"])

        return existing.toMutableList().apply {
            when (val item = existing[idx]) {
                is TimelineItem.CommandExecution -> set(
                    idx,
                    item.copy(
                        status = newStatus ?: item.status,
                        outputTail = if (rawOutputText != null) appendTail(item.outputTail, rawOutputText) else item.outputTail,
                    ),
                )
                is TimelineItem.FileChange -> set(
                    idx,
                    item.copy(
                        status = newStatus ?: item.status,
                        unifiedDiff = rawOutputText ?: item.unifiedDiff,
                    ),
                )
                is TimelineItem.AgentImages -> {
                    // imageGeneration populates savedPath only at completion; the
                    // adapter forwards it via tool_call_update.rawInput.path so we can
                    // fill in the renderer's fetch reference late.
                    val updatedPath = update["rawInput"]?.jsonObject
                        ?.get("path")?.jsonPrimitive?.contentOrNull
                        ?.takeIf { it.isNotBlank() }
                    set(
                        idx,
                        item.copy(
                            status = newStatus ?: item.status,
                            imageUrls = if (rawOutputImages.isNotEmpty()) item.imageUrls + rawOutputImages else item.imageUrls,
                            sourcePath = updatedPath ?: item.sourcePath,
                        ),
                    )
                }
                else -> Unit
            }
        }
    }

    /**
     * Extracts `data:image/...;base64,...` URLs from codex's `rawOutput` array shape:
     * `[{type: "input_image", image_url: "data:..."}]`. Returns empty when rawOutput is
     * absent, a string, or doesn't contain any input_image entries.
     */
    private fun extractImageUrlsFromRawOutput(raw: kotlinx.serialization.json.JsonElement?): List<String> {
        if (raw == null) return emptyList()
        val arr = raw as? JsonArray ?: return emptyList()
        return arr.mapNotNull { entry ->
            val obj = (entry as? JsonObject) ?: return@mapNotNull null
            if (obj["type"]?.jsonPrimitive?.contentOrNull != "input_image") return@mapNotNull null
            obj["image_url"]?.jsonPrimitive?.contentOrNull
        }
    }

    /**
     * Mark any in-flight streaming messages as terminal. Called by the controller when the
     * ACP `session/prompt` RPC returns (i.e., the turn has a `stopReason`).
     */
    fun freezeStreaming(existing: List<TimelineItem>): List<TimelineItem> {
        return existing.map { item ->
            when (item) {
                is TimelineItem.UserMessage -> if (item.streaming) item.copy(streaming = false) else item
                is TimelineItem.AgentMessage -> if (item.streaming) item.copy(streaming = false) else item
                is TimelineItem.Reasoning -> if (item.streaming) item.copy(streaming = false) else item
                else -> item
            }
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private fun mapStatus(raw: String): ToolStatus = when (raw) {
        "pending" -> ToolStatus.Pending
        "running" -> ToolStatus.Running
        "completed" -> ToolStatus.Completed
        "failed" -> ToolStatus.Failed
        "cancelled" -> ToolStatus.Cancelled
        else -> ToolStatus.Pending
    }

    /** Codex-style fallback: title carries the tool name when `kind` is empty (v0 parity). */
    private fun inferKindFromTitle(title: String): String = when {
        title.contains("exec_command", ignoreCase = true) -> "execute"
        title.contains("file_edit", ignoreCase = true) ||
            title.contains("apply_diff", ignoreCase = true) ||
            title.contains("write", ignoreCase = true) -> "edit"
        title.contains("read", ignoreCase = true) -> "read"
        else -> ""
    }

    private fun extractTextFromContentBlock(update: JsonObject): String? {
        val content = update["content"]?.jsonObject ?: return null
        if (content["type"]?.jsonPrimitive?.contentOrNull != "text") return null
        return content["text"]?.jsonPrimitive?.contentOrNull
    }

    private fun extractPathsFromLocations(update: JsonObject): List<String> {
        val arr = update["locations"] ?: return emptyList()
        return runCatching {
            arr.jsonArray().mapNotNull { it.jsonObject["path"]?.jsonPrimitive?.contentOrNull }
        }.getOrDefault(emptyList())
    }

    private fun kotlinx.serialization.json.JsonElement.jsonArray() =
        this as kotlinx.serialization.json.JsonArray

    /** Keep the output tail bounded so UI doesn't bloat on long-running tools. */
    private fun appendTail(existing: String, new: String, maxLines: Int = 3): String {
        val combined = if (existing.isBlank()) new else "$existing\n$new"
        return combined.lines().takeLast(maxLines).joinToString("\n")
    }
}
