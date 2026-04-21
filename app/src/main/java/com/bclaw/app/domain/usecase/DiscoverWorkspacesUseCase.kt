package com.bclaw.app.domain.usecase

import com.bclaw.app.data.ConnectionConfigRepository
import com.bclaw.app.data.WorkspaceConfig
import com.bclaw.app.domain.state.BclawStateStore
import com.bclaw.app.net.JsonRpcSession
import com.bclaw.app.net.acp.AcpSessionListParams
import com.bclaw.app.net.acp.AcpSessionListResult
import com.bclaw.app.net.codex.CodexJson

class DiscoverWorkspacesUseCase(
    private val session: JsonRpcSession,
    private val store: BclawStateStore,
    private val configRepository: ConnectionConfigRepository,
) {
    suspend operator fun invoke() {
        runCatching {
            session.request<AcpSessionListResult>(
                "session/list",
                CodexJson.json.encodeToJsonElement(
                    AcpSessionListParams.serializer(),
                    AcpSessionListParams(),
                ),
            )
        }.onSuccess { response ->
            val discoveredWorkspaces = response.sessions
                .mapNotNull { it.cwd }
                .filter { it.isNotBlank() }
                .distinct()
                .mapIndexed { index, cwd ->
                    WorkspaceConfig(
                        id = "workspace-$index",
                        displayName = cwd.substringAfterLast('/'),
                        cwd = cwd,
                    )
                }
            store.setWorkspaces(discoveredWorkspaces)
        }
    }
}
