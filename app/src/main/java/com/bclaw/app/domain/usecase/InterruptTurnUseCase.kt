package com.bclaw.app.domain.usecase

import com.bclaw.app.domain.state.BclawStateStore
import com.bclaw.app.net.JsonRpcSession
import com.bclaw.app.net.acp.AcpSessionCancelParams
import com.bclaw.app.net.codex.CodexJson

class InterruptTurnUseCase(
    private val session: JsonRpcSession,
    private val store: BclawStateStore,
) {
    suspend operator fun invoke(threadId: String) {
        store.setStatusMessage("Interrupting…")
        runCatching {
            session.notify(
                "session/cancel",
                CodexJson.json.encodeToJsonElement(
                    AcpSessionCancelParams.serializer(),
                    AcpSessionCancelParams(sessionId = threadId),
                ),
            )
        }.onFailure { throwable ->
            store.setStatusMessage(throwable.message ?: "Interrupt failed")
        }
    }
}
