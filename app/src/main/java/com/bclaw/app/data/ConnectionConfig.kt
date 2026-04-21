package com.bclaw.app.data

import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class WorkspaceConfig(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val cwd: String,
    val lastOpenedThreadId: String? = null,
)

@Serializable
data class ConnectionConfig(
    val host: String = "",
    val token: String = "",
    val lastOpenedWorkspaceId: String? = null,
    val workspaces: List<WorkspaceConfig> = emptyList(),
) {
    val isConfigured: Boolean
        get() = host.isNotBlank() && token.isNotBlank()
}
