package com.bclaw.app.domain.usecase

import com.bclaw.app.domain.TimelineItemMapper
import com.bclaw.app.domain.state.BclawStateStore
import com.bclaw.app.domain.state.ThreadTimelineStore
import com.bclaw.app.net.codex.AgentMessageDeltaNotification
import com.bclaw.app.net.codex.CodexJson
import com.bclaw.app.net.codex.CommandExecutionOutputDeltaNotification
import com.bclaw.app.net.codex.ErrorNotification
import com.bclaw.app.net.codex.ItemLifecycleNotification
import com.bclaw.app.net.codex.ReasoningSummaryTextDeltaNotification
import kotlinx.serialization.json.JsonObject

class HandleItemNotificationUseCase(
    private val store: BclawStateStore,
    private val timelineItemMapper: TimelineItemMapper,
    private val timelineStore: ThreadTimelineStore,
) {
    private val json = CodexJson.json

    operator fun invoke(method: String, params: JsonObject): Boolean {
        return when (method) {
            "item/started",
            "item/completed",
            -> {
                val notification = json.decodeFromJsonElement(ItemLifecycleNotification.serializer(), params)
                timelineStore.upsertTimelineItem(
                    notification.threadId,
                    timelineItemMapper.fromJson(
                        turnId = notification.turnId,
                        item = notification.item,
                        diffByTurn = store.threadState(notification.threadId)?.diffByTurn.orEmpty(),
                    ),
                )
                true
            }

            "item/agentMessage/delta" -> {
                val notification = json.decodeFromJsonElement(AgentMessageDeltaNotification.serializer(), params)
                timelineStore.appendAgentDelta(notification.threadId, notification.turnId, notification.itemId, notification.delta)
                true
            }

            "item/commandExecution/outputDelta" -> {
                val notification = json.decodeFromJsonElement(CommandExecutionOutputDeltaNotification.serializer(), params)
                timelineStore.appendCommandOutput(notification.threadId, notification.turnId, notification.itemId, notification.delta)
                true
            }

            "item/reasoning/summaryTextDelta" -> {
                val notification = json.decodeFromJsonElement(ReasoningSummaryTextDeltaNotification.serializer(), params)
                timelineStore.appendReasoningDelta(notification.threadId, notification.turnId, notification.itemId, notification.delta)
                true
            }

            "error" -> {
                val notification = json.decodeFromJsonElement(ErrorNotification.serializer(), params)
                timelineStore.appendError(notification.threadId, notification.turnId, notification.error.message)
                true
            }

            else -> false
        }
    }
}
