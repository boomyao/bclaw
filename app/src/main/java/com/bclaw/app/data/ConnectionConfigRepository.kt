package com.bclaw.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "bclaw_connection")

class ConnectionConfigRepository(
    private val context: Context,
    private val ioContext: CoroutineContext = Dispatchers.IO,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val hostKey = stringPreferencesKey("host")
    private val lastOpenedWorkspaceIdKey = stringPreferencesKey("last_opened_workspace_id")
    private val workspacesKey = stringPreferencesKey("workspaces_json")
    private val tokenKey = "capability_token"

    private val encryptedPrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "bclaw_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    val configFlow: Flow<ConnectionConfig> = context.dataStore.data.map { preferences ->
        val host = preferences[hostKey].orEmpty()
        val lastOpenedWorkspaceId = preferences[lastOpenedWorkspaceIdKey]
        val workspacesJson = preferences[workspacesKey].orEmpty()
        val workspaces = if (workspacesJson.isBlank()) {
            emptyList()
        } else {
            runCatching {
                json.decodeFromString<List<WorkspaceConfig>>(workspacesJson)
            }.getOrDefault(emptyList())
        }
        val token = encryptedPrefs.getString(tokenKey, "").orEmpty()
        ConnectionConfig(
            host = host,
            token = token,
            lastOpenedWorkspaceId = lastOpenedWorkspaceId,
            workspaces = workspaces,
        )
    }

    suspend fun getSnapshot(): ConnectionConfig = configFlow.first()

    suspend fun saveConfig(config: ConnectionConfig) {
        withContext(ioContext) {
            encryptedPrefs.edit().putString(tokenKey, config.token).commit()
            context.dataStore.edit { preferences ->
                preferences[hostKey] = config.host
                if (config.lastOpenedWorkspaceId == null) {
                    preferences.remove(lastOpenedWorkspaceIdKey)
                } else {
                    preferences[lastOpenedWorkspaceIdKey] = config.lastOpenedWorkspaceId
                }
                preferences[workspacesKey] = json.encodeToString(config.workspaces)
            }
        }
    }

    suspend fun updateLastOpenedThreadId(workspaceId: String, threadId: String?) {
        withContext(ioContext) {
            val current = getSnapshot()
            val updated = current.copy(
                lastOpenedWorkspaceId = workspaceId,
                workspaces = current.workspaces.map { workspace ->
                    if (workspace.id == workspaceId) {
                        workspace.copy(lastOpenedThreadId = threadId)
                    } else {
                        workspace
                    }
                },
            )
            saveConfig(updated)
        }
    }

    suspend fun updateLastOpenedWorkspaceId(workspaceId: String) {
        withContext(ioContext) {
            val current = getSnapshot()
            saveConfig(current.copy(lastOpenedWorkspaceId = workspaceId))
        }
    }
}
