package com.bclaw.app.net.acp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@Serializable
data class BridgeAgentInfo(
    val name: String,
    val displayName: String = name,
    val command: String = "",
)

@Serializable
data class AcpAgentsResponse(
    val agents: List<BridgeAgentInfo> = emptyList(),
)

/**
 * Discovers available ACP agents from the bridge's REST endpoint.
 * Bridge URL is `ws://host:port`, we convert to `http://host:port/agents`.
 */
object AcpAgentDiscovery {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchAgents(bridgeWsUrl: String): List<BridgeAgentInfo> {
        val httpUrl = bridgeWsUrl
            .replace("ws://", "http://")
            .replace("wss://", "https://")
            .trimEnd('/') + "/agents"

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(httpUrl).get().build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                json.decodeFromString(AcpAgentsResponse.serializer(), body).agents
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
