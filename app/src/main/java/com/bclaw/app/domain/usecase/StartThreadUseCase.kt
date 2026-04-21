package com.bclaw.app.domain.usecase

import com.bclaw.app.data.ConnectionConfigRepository
import com.bclaw.app.domain.ThreadSubscriptionRegistry
import com.bclaw.app.domain.state.BclawStateStore
import com.bclaw.app.net.JsonRpcSession
import com.bclaw.app.net.acp.AcpSessionNewParams
import com.bclaw.app.net.acp.AcpSessionNewResult
import com.bclaw.app.net.codex.CodexJson
import com.bclaw.app.net.codex.ThreadSummary

class StartThreadUseCase(
    private val session: JsonRpcSession,
    private val store: BclawStateStore,
    private val configRepository: ConnectionConfigRepository,
    private val subscriptions: ThreadSubscriptionRegistry,
) {
    suspend operator fun invoke(
        workspaceId: String,
        ensureConnected: suspend () -> Unit,
        onStarted: (String) -> Unit,
    ) {
        val workspace = store.workspaceForId(workspaceId) ?: return
        ensureConnected()
        runCatching {
            session.request<AcpSessionNewResult>(
                "session/new",
                CodexJson.json.encodeToJsonElement(
                    AcpSessionNewParams.serializer(),
                    AcpSessionNewParams(cwd = workspace.cwd),
                ),
            )
        }.onSuccess { response ->
            val thread = ThreadSummary(
                id = response.sessionId,
                cwd = workspace.cwd,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
            subscriptions.subscribe(response.sessionId)
            configRepository.updateLastOpenedThreadId(workspaceId, response.sessionId)
            store.mergeThreadSummary(thread)
            onStarted(response.sessionId)
        }.onFailure { throwable ->
            store.setStatusMessage(throwable.message ?: "Failed to start thread")
        }
    }
}
