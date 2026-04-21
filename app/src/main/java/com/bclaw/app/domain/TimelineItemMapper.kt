package com.bclaw.app.domain

import com.bclaw.app.domain.model.TimelineItemUi
import com.bclaw.app.net.codex.ThreadSummary
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class TimelineItemMapper {
    fun historyFromThread(thread: ThreadSummary): List<TimelineItemUi> {
        return thread.turns.flatMap { turn ->
            buildList {
                addAll(
                    turn.items.map { item ->
                        fromJson(
                            turnId = turn.id,
                            item = item,
                            turnTimestampEpochMillis = turn.startedAt?.times(1000),
                        )
                    },
                )
                turn.error?.let { error ->
                    add(
                        TimelineItemUi.Error(
                            id = "error:${turn.id}",
                            turnId = turn.id,
                            message = error.message,
                        ),
                    )
                }
            }
        }
    }

    fun fromJson(
        turnId: String,
        item: JsonObject,
        diffByTurn: Map<String, String> = emptyMap(),
        turnTimestampEpochMillis: Long? = null,
    ): TimelineItemUi {
        val itemId = item["id"]?.jsonPrimitive?.content.orEmpty()
        return try {
            parseItem(itemId, turnId, item, diffByTurn, turnTimestampEpochMillis)
        } catch (_: Exception) {
            TimelineItemUi.Unsupported(
                id = if (itemId.isBlank()) "unsupported:${UUID.randomUUID()}" else itemId,
                turnId = turnId,
                kind = item["type"]?.jsonPrimitive?.contentOrNull ?: "parse-error",
            )
        }
    }

    private fun parseItem(
        itemId: String,
        turnId: String,
        item: JsonObject,
        diffByTurn: Map<String, String>,
        turnTimestampEpochMillis: Long?,
    ): TimelineItemUi {
        return when (item["type"]?.jsonPrimitive?.contentOrNull) {
            "userMessage" -> TimelineItemUi.UserMessage(
                id = itemId,
                turnId = turnId,
                text = item.extractUserMessageText(),
                optimistic = false,
                timestampEpochMillis = turnTimestampEpochMillis ?: System.currentTimeMillis(),
            )

            "agentMessage" -> TimelineItemUi.AgentMessage(
                id = itemId,
                turnId = turnId,
                text = item["text"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )

            "reasoning" -> TimelineItemUi.Reasoning(
                id = itemId,
                turnId = turnId,
                summary = item.stringArray("summary"),
            )

            "commandExecution" -> TimelineItemUi.CommandExecution(
                id = itemId,
                turnId = turnId,
                command = item["command"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                cwd = item["cwd"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                status = item["status"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                output = item["aggregatedOutput"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                exitCode = item["exitCode"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                durationMs = item["durationMs"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
            )

            "fileChange" -> TimelineItemUi.FileChange(
                id = itemId,
                turnId = turnId,
                paths = item.extractFilePaths(),
                status = item["status"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                diff = diffByTurn[turnId].orEmpty().ifBlank { item.extractFileDiff() },
                changes = item.extractFileChanges(),
            )

            "mcpToolCall" -> TimelineItemUi.McpToolCall(
                id = itemId,
                turnId = turnId,
                server = item["server"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                tool = item["tool"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                arguments = item["arguments"]?.toString().orEmpty(),
                status = item.safeString("status"),
                result = item["result"]?.toString().orEmpty(),
                error = item["error"]?.toString().orEmpty(),
                durationMs = item["durationMs"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
            )

            "webSearch" -> TimelineItemUi.WebSearch(
                id = itemId,
                turnId = turnId,
                query = item["query"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                action = item["action"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )

            "plan" -> TimelineItemUi.Plan(
                id = itemId,
                turnId = turnId,
                text = item["text"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )

            "contextCompaction" -> TimelineItemUi.ContextCompaction(
                id = itemId,
                turnId = turnId,
            )

            "dynamicToolCall" -> TimelineItemUi.DynamicToolCall(
                id = itemId,
                turnId = turnId,
                tool = item["tool"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                arguments = item["arguments"]?.toString().orEmpty(),
                status = item.safeString("status"),
                success = item["success"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull(),
                durationMs = item["durationMs"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
            )

            "collabAgentToolCall" -> TimelineItemUi.SubAgent(
                id = itemId,
                turnId = turnId,
                tool = item.safeString("tool"),
                prompt = item["prompt"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                status = item.safeString("status"),
                model = item["model"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )

            else -> TimelineItemUi.Unsupported(
                id = if (itemId.isBlank()) "unsupported:${UUID.randomUUID()}" else itemId,
                turnId = turnId,
                kind = item["type"]?.jsonPrimitive?.contentOrNull ?: "unknown",
            )
        }
    }

    private fun JsonObject.extractUserMessageText(): String {
        val content = this["content"] as? JsonArray ?: return ""
        return content.mapNotNull { element ->
            element.jsonObject.takeIf {
                it["type"]?.jsonPrimitive?.contentOrNull == "text"
            }?.get("text")?.jsonPrimitive?.contentOrNull
        }.joinToString("\n")
    }

    private fun JsonObject.extractFilePaths(): List<String> {
        val changes = this["changes"] as? JsonArray ?: return emptyList()
        return changes.mapNotNull { change ->
            change.jsonObject["path"]?.jsonPrimitive?.contentOrNull
        }
    }

    private fun JsonObject.extractFileDiff(): String {
        val changes = this["changes"] as? JsonArray ?: return ""
        return changes.mapNotNull { change ->
            change.jsonObject["diff"]?.jsonPrimitive?.contentOrNull
        }.joinToString("\n\n")
    }

    private fun JsonObject.extractFileChanges(): List<TimelineItemUi.FileChangeEntry> {
        val changes = this["changes"] as? JsonArray ?: return emptyList()
        return changes.mapNotNull { change ->
            val obj = change.jsonObject
            val path = obj["path"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val diff = obj["diff"]?.jsonPrimitive?.contentOrNull.orEmpty()
            TimelineItemUi.FileChangeEntry(
                path = path,
                additions = obj["additions"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: diff.lineSequence().count { line -> line.startsWith("+") && !line.startsWith("+++") },
                deletions = obj["deletions"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: diff.lineSequence().count { line -> line.startsWith("-") && !line.startsWith("---") },
                diff = diff,
            )
        }
    }

    private fun JsonObject.stringArray(name: String): String {
        return (this[name] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?.joinToString("\n\n")
            .orEmpty()
    }

    private fun JsonObject.safeString(name: String): String {
        val el = this[name] ?: return ""
        return try {
            el.jsonPrimitive.contentOrNull.orEmpty()
        } catch (_: IllegalArgumentException) {
            el.toString()
        }
    }
}
