package com.bclaw.app.net.acp

import com.bclaw.app.domain.v2.CwdPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request

@Serializable
data class BridgeProjectInfo(val cwd: String)

@Serializable
data class AcpProjectsResponse(
    val projects: List<BridgeProjectInfo> = emptyList(),
)

/**
 * Discovers projects (cwd folders from `~/.codex/config.toml`) from the bridge's REST endpoint.
 * Bridge URL is `ws://host:port`, converted to `http://host:port/projects`.
 */
object AcpProjectDiscovery {

    private val client = BridgeReachability.client

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchProjects(bridgeWsUrl: String, agentId: String): List<CwdPath> {
        val httpUrl = bridgeWsUrl
            .replace("ws://", "http://")
            .replace("wss://", "https://")
            .trimEnd('/') + "/projects?agent=" + java.net.URLEncoder.encode(agentId, "UTF-8")

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(httpUrl).get().build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                json.decodeFromString(AcpProjectsResponse.serializer(), body)
                    .projects
                    .map { CwdPath(it.cwd) }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
