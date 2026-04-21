package com.bclaw.app.domain.usecase

import com.bclaw.app.domain.state.BclawStateStore
import com.bclaw.app.net.codex.CodexJson
import com.bclaw.app.net.codex.ThreadClosedNotification
import com.bclaw.app.net.codex.ThreadNameUpdatedNotification
import com.bclaw.app.net.codex.ThreadStartedNotification
import com.bclaw.app.net.codex.ThreadStatusChangedNotification
import kotlinx.serialization.json.JsonObject

class HandleThreadNotificationUseCase(
    private val store: BclawStateStore,
) {
    private val json = CodexJson.json

    operator fun invoke(method: String, params: JsonObject): Boolean {
        return when (method) {
            "thread/started" -> {
                val notification = json.decodeFromJsonElement(ThreadStartedNotification.serializer(), params)
                store.mergeThreadSummary(notification.thread)
                true
            }

            "thread/closed" -> {
                val notification = json.decodeFromJsonElement(ThreadClosedNotification.serializer(), params)
                store.markThreadClosed(notification.threadId)
                true
            }

            "thread/status/changed" -> {
                val notification = json.decodeFromJsonElement(ThreadStatusChangedNotification.serializer(), params)
                store.applyThreadStatus(notification.threadId, notification.status)
                true
            }

            "thread/name/updated" -> {
                val notification = json.decodeFromJsonElement(ThreadNameUpdatedNotification.serializer(), params)
                store.applyThreadName(notification.threadId, notification.name)
                true
            }

            else -> false
        }
    }
}
