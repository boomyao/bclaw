package com.bclaw.app.domain.usecase

import com.bclaw.app.domain.model.TimelineItemUi
import com.bclaw.app.domain.state.BclawStateStore
import com.bclaw.app.domain.state.ThreadTimelineStore
import com.bclaw.app.net.acp.AcpUpdateMapper
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/**
 * Handles the ACP `session/update` notification.
 *
 * ACP collapses all server-to-client streaming into a single notification method
 * whose `params` contain `{ sessionId, update }`. The `update` object is a tagged
 * union keyed on the `sessionUpdate` field.
 */
class HandleSessionUpdateUseCase(
    private val store: BclawStateStore,
    private val timelineStore: ThreadTimelineStore,
) {
    /**
     * Callback to retrieve the current synthetic turn ID for a given session.
     * The controller sets this so the use case can attach items to the right turn.
     */
    var syntheticTurnIdProvider: ((sessionId: String) -> String)? = null

    // Sequence counter per session — increments when a non-text update
    // interrupts agent_message_chunk or agent_thought_chunk, so the next
    // text chunk starts a new timeline item instead of appending to the old one.
    private val messageSeq = mutableMapOf<String, Int>()
    private val lastUpdateType = mutableMapOf<String, String>()

    private fun nextMessageSeq(sessionId: String, currentType: String): Int {
        val prev = lastUpdateType[sessionId]
        lastUpdateType[sessionId] = currentType
        if (prev != null && prev != currentType) {
            messageSeq[sessionId] = (messageSeq[sessionId] ?: 0) + 1
        }
        return messageSeq[sessionId] ?: 0
    }

    /**
     * Returns true if the notification was handled, false if the method didn't match.
     */
    operator fun invoke(method: String, params: JsonObject): Boolean {
        if (method != "session/update") return false

        val sessionId = params["sessionId"]?.jsonPrimitive?.contentOrNull ?: return false
        val update = params["update"]?.jsonObject ?: return false
        val updateType = AcpUpdateMapper.updateType(update)
        val turnId = syntheticTurnIdProvider?.invoke(sessionId) ?: "turn:unknown"

        when (updateType) {
            "agent_message_chunk" -> {
                val text = AcpUpdateMapper.extractAgentMessageText(update) ?: return true
                val seq = nextMessageSeq(sessionId, "agent_message")
                val itemId = "agent-msg:$sessionId:$turnId:$seq"
                timelineStore.appendAgentDelta(sessionId, turnId, itemId, text)
            }

            "agent_thought_chunk" -> {
                val text = AcpUpdateMapper.extractThoughtText(update) ?: return true
                val seq = nextMessageSeq(sessionId, "agent_thought")
                val itemId = "reasoning:$sessionId:$turnId:$seq"
                timelineStore.appendReasoningDelta(sessionId, turnId, itemId, text)
            }

            "user_message_chunk" -> {
                val text = AcpUpdateMapper.extractUserMessageText(update) ?: return true
                val itemId = "user-msg:$sessionId:${UUID.randomUUID()}"
                timelineStore.upsertTimelineItem(
                    sessionId,
                    TimelineItemUi.UserMessage(
                        id = itemId,
                        turnId = turnId,
                        text = text,
                        optimistic = false,
                        timestampEpochMillis = System.currentTimeMillis(),
                    ),
                )
            }

            "tool_call" -> {
                nextMessageSeq(sessionId, "tool_call") // break text sequence
                val item = AcpUpdateMapper.toolCallToTimelineItem(update, turnId)
                timelineStore.upsertTimelineItem(sessionId, item)
            }

            "tool_call_update" -> {
                val data = AcpUpdateMapper.extractToolCallUpdate(update)
                if (data.toolCallId.isBlank()) return true
                // Try to update existing item in-place
                store.mutate { state ->
                    val threadState = state.threadStates[sessionId] ?: return@mutate state
                    val updatedItems = threadState.items.map { item ->
                        updateToolCallItem(item, data)
                    }
                    state.copy(
                        threadStates = state.threadStates + (
                            sessionId to threadState.copy(items = updatedItems)
                        ),
                    )
                }
            }

            "plan" -> {
                nextMessageSeq(sessionId, "plan") // break text sequence
                val item = AcpUpdateMapper.planToTimelineItem(update, turnId)
                timelineStore.upsertTimelineItem(sessionId, item)
            }

            "session_info_update" -> {
                val title = AcpUpdateMapper.extractSessionTitle(update)
                if (title != null) {
                    store.applyThreadName(sessionId, title)
                }
            }

            // Ignore update types we don't need to handle:
            // available_commands_update, current_mode_update, config_option_update
        }
        return true
    }

    private fun updateToolCallItem(
        item: TimelineItemUi,
        data: com.bclaw.app.net.acp.ToolCallUpdateData,
    ): TimelineItemUi {
        if (item.id != data.toolCallId) return item
        return when (item) {
            is TimelineItemUi.CommandExecution -> item.copy(
                status = data.status ?: item.status,
                output = if (data.appendOutput != null) item.output + data.appendOutput else item.output,
            )
            is TimelineItemUi.FileChange -> item.copy(
                status = data.status ?: item.status,
                diff = if (data.diffContent != null) item.diff + "\n" + data.diffContent else item.diff,
            )
            is TimelineItemUi.DynamicToolCall -> item.copy(
                status = data.status ?: item.status,
                success = data.status == "completed",
            )
            else -> item
        }
    }
}
