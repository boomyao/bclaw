package com.bclaw.app.domain.usecase

import com.bclaw.app.domain.state.BclawStateStore
import com.bclaw.app.domain.state.ThreadTimelineStore
import com.bclaw.app.net.codex.CodexJson
import com.bclaw.app.net.codex.ThreadStatus
import com.bclaw.app.net.codex.TurnCompletedNotification
import com.bclaw.app.net.codex.TurnDiffUpdatedNotification
import com.bclaw.app.net.codex.TurnStartedNotification
import kotlinx.serialization.json.JsonObject

class HandleTurnNotificationUseCase(
    private val store: BclawStateStore,
    private val timelineStore: ThreadTimelineStore,
) {
    private val json = CodexJson.json

    operator fun invoke(method: String, params: JsonObject): Boolean {
        return when (method) {
            "turn/started" -> {
                val notification = json.decodeFromJsonElement(TurnStartedNotification.serializer(), params)
                store.setTurnStarted(notification.threadId, notification.turn.id, notification.turn.status)
                store.applyThreadStatus(notification.threadId, ThreadStatus(type = "active"))
                true
            }

            "turn/completed" -> {
                val notification = json.decodeFromJsonElement(TurnCompletedNotification.serializer(), params)
                store.setTurnCompleted(
                    threadId = notification.threadId,
                    turnId = notification.turn.id,
                    status = notification.turn.status,
                    errorMessage = notification.turn.error?.message,
                )
                notification.turn.error?.let { error ->
                    timelineStore.appendError(notification.threadId, notification.turn.id, error.message)
                }
                store.applyThreadStatus(notification.threadId, ThreadStatus(type = "idle"))
                store.setStatusMessage(
                    when (notification.turn.status) {
                        "interrupted" -> "Turn interrupted"
                        "failed" -> notification.turn.error?.message ?: "Turn failed"
                        else -> null
                    },
                )
                true
            }

            "turn/diff/updated" -> {
                val notification = json.decodeFromJsonElement(TurnDiffUpdatedNotification.serializer(), params)
                timelineStore.appendTurnDiff(notification.threadId, notification.turnId, notification.diff)
                true
            }

            else -> false
        }
    }
}
