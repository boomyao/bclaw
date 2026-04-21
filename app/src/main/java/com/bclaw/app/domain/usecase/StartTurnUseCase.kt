package com.bclaw.app.domain.usecase

import com.bclaw.app.domain.model.TimelineItemUi
import com.bclaw.app.domain.state.BclawStateStore
import com.bclaw.app.domain.state.ThreadTimelineStore
import com.bclaw.app.net.JsonRpcSession
import com.bclaw.app.net.acp.AcpContentBlock
import com.bclaw.app.net.acp.AcpPromptParams
import com.bclaw.app.net.acp.AcpPromptResult
import com.bclaw.app.net.codex.CodexJson
import java.util.UUID

class StartTurnUseCase(
    private val session: JsonRpcSession,
    private val store: BclawStateStore,
    private val timelineStore: ThreadTimelineStore,
) {
    /**
     * Callback so the controller can track the synthetic turn ID for this prompt lifecycle.
     */
    var onPromptStarted: ((threadId: String, syntheticTurnId: String, requestId: String?) -> Unit)? = null
    var onPromptCompleted: ((threadId: String, syntheticTurnId: String, stopReason: String) -> Unit)? = null

    suspend operator fun invoke(
        threadId: String,
        text: String,
        ensureConnected: suspend () -> Unit,
    ) {
        if (text.isBlank()) return
        val syntheticTurnId = "turn:${UUID.randomUUID()}"
        val optimistic = TimelineItemUi.UserMessage(
            id = "optimistic:${UUID.randomUUID()}",
            turnId = syntheticTurnId,
            text = text,
            optimistic = true,
            timestampEpochMillis = System.currentTimeMillis(),
        )
        ensureConnected()
        timelineStore.upsertTimelineItem(threadId, optimistic)
        store.setTurnStarted(threadId, syntheticTurnId, "inProgress")
        onPromptStarted?.invoke(threadId, syntheticTurnId, null)
        runCatching {
            session.request<AcpPromptResult>(
                "session/prompt",
                CodexJson.json.encodeToJsonElement(
                    AcpPromptParams.serializer(),
                    AcpPromptParams(
                        sessionId = threadId,
                        prompt = listOf(
                            AcpContentBlock(type = "text", text = text),
                        ),
                    ),
                ),
            )
        }.onSuccess { response ->
            onPromptCompleted?.invoke(threadId, syntheticTurnId, response.stopReason)
            store.setTurnCompleted(threadId, syntheticTurnId, response.stopReason, null)
        }.onFailure { throwable ->
            val message = throwable.message ?: "Failed to send message"
            onPromptCompleted?.invoke(threadId, syntheticTurnId, "error")
            store.setTurnCompleted(threadId, syntheticTurnId, "error", message)
            store.setStatusMessage(message)
            timelineStore.appendError(threadId, syntheticTurnId, message)
        }
    }
}
