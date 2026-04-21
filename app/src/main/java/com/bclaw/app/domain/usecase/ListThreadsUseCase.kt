package com.bclaw.app.domain.usecase

import com.bclaw.app.domain.state.BclawStateStore
import com.bclaw.app.net.JsonRpcSession
import com.bclaw.app.net.acp.AcpSessionListParams
import com.bclaw.app.net.acp.AcpSessionListResult
import com.bclaw.app.net.codex.CodexJson
import com.bclaw.app.net.codex.ThreadSummary

class ListThreadsUseCase(
    private val session: JsonRpcSession,
    private val store: BclawStateStore,
) {
    suspend operator fun invoke(
        workspaceId: String,
        append: Boolean,
        ensureConnected: suspend () -> Unit,
    ) {
        val workspace = store.workspaceForId(workspaceId) ?: return
        val current = store.workspaceThreadsState(workspaceId)
        ensureConnected()
        store.markWorkspaceThreadsLoading(workspaceId)
        runCatching {
            session.request<AcpSessionListResult>(
                "session/list",
                CodexJson.json.encodeToJsonElement(
                    AcpSessionListParams.serializer(),
                    AcpSessionListParams(
                        cwd = workspace.cwd,
                        cursor = if (append) current?.nextCursor else null,
                    ),
                ),
            )
        }.onSuccess { response ->
            val threads = response.sessions.map { info ->
                ThreadSummary(
                    id = info.sessionId,
                    cwd = info.cwd.orEmpty(),
                    name = info.title,
                    updatedAt = info.updatedAt?.toLongOrNull() ?: 0L,
                )
            }
            store.applyWorkspaceThreadPage(workspaceId, threads, response.nextCursor, append)
        }.onFailure { throwable ->
            store.setWorkspaceThreadsError(workspaceId, throwable.message ?: "Failed to load threads")
        }
    }
}
