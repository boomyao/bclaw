package com.bclaw.app.domain.usecase

import android.util.Log
import com.bclaw.app.data.ConnectionConfigRepository
import com.bclaw.app.domain.ThreadSubscriptionRegistry
import com.bclaw.app.domain.state.BclawStateStore
import com.bclaw.app.net.JsonRpcSession
import com.bclaw.app.net.acp.AcpSessionLoadParams
import com.bclaw.app.net.codex.CodexJson

private const val TAG = "OpenThreadUseCase"

class OpenThreadUseCase(
    private val session: JsonRpcSession,
    private val store: BclawStateStore,
    private val configRepository: ConnectionConfigRepository,
    private val subscriptions: ThreadSubscriptionRegistry,
) {
    suspend operator fun invoke(
        workspaceId: String,
        threadId: String,
        ensureConnected: suspend () -> Unit,
    ) {
        ensureConnected()
        configRepository.updateLastOpenedThreadId(workspaceId, threadId)
        val alreadySubscribed = subscriptions.isSubscribed(threadId)

        if (!alreadySubscribed) {
            subscriptions.subscribe(threadId)
        }
        store.markThreadLoading(threadId)

        // ACP: session/load subscribes to the session. History arrives via session/update notifications.
        runCatching {
            val workspace = store.workspaceForId(workspaceId)
            val cwd = workspace?.cwd.orEmpty()
            session.requestRaw(
                "session/load",
                CodexJson.json.encodeToJsonElement(
                    AcpSessionLoadParams.serializer(),
                    AcpSessionLoadParams(
                        sessionId = threadId,
                        cwd = cwd,
                    ),
                ),
            )
        }.onSuccess {
            // Mark history as loaded; actual items arrive via session/update notifications
            store.mutate { state ->
                val current = state.threadStates[threadId]
                    ?: com.bclaw.app.domain.model.ChatThreadState()
                state.copy(
                    threadStates = state.threadStates + (
                        threadId to current.copy(
                            historyLoaded = true,
                            loading = false,
                            error = null,
                        )
                    ),
                )
            }
        }.onFailure { throwable ->
            Log.w(TAG, "session/load failed for $threadId", throwable)
            store.setThreadError(threadId, throwable.message ?: "Failed to load session")
        }
    }
}
