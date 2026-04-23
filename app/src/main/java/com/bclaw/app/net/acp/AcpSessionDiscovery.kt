package com.bclaw.app.net.acp

import com.bclaw.app.domain.v2.AgentId
import com.bclaw.app.domain.v2.CwdPath
import com.bclaw.app.domain.v2.SessionId
import com.bclaw.app.domain.v2.SessionRef
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request

@Serializable
private data class BridgeSessionInfo(
    val id: String,
    val title: String? = null,
    // Deserialized as Double? because the bridge's claude scraper uses `fs.statSync.mtimeMs`,
    // which on macOS returns sub-millisecond precision floats (e.g. 1776871162312.4363).
    // A `Long?` field here would fail to parse and silently drop the whole session list.
    val lastActivityEpochMs: Double? = null,
)

@Serializable
private data class AcpSessionsResponse(
    val agent: String = "",
    val cwd: String = "",
    val sessions: List<BridgeSessionInfo> = emptyList(),
)

/**
 * Fetches historical sessions (codex rollouts / claude jsonl) for a given agent+cwd from the bridge.
 * Bridge URL is `ws://host:port` → `http://host:port/sessions?agent=X&cwd=Y`.
 *
 * The bridge caps results to its SESSIONS_LIMIT (currently 30) so this returns a small list
 * suitable for direct display without paging.
 */
object AcpSessionDiscovery {

    private val client = BridgeReachability.client

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchSessions(
        bridgeWsUrl: String,
        agentId: AgentId,
        cwd: CwdPath,
    ): List<SessionRef> {
        val base = bridgeWsUrl
            .replace("ws://", "http://")
            .replace("wss://", "https://")
            .trimEnd('/')
        val httpUrl = base + "/sessions?agent=" +
            URLEncoder.encode(agentId.value, "UTF-8") +
            "&cwd=" + URLEncoder.encode(cwd.value, "UTF-8")

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(httpUrl).get().build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                json.decodeFromString(AcpSessionsResponse.serializer(), body)
                    .sessions
                    .map { info ->
                        SessionRef(
                            agentId = agentId,
                            projectCwd = cwd,
                            sessionId = SessionId(info.id),
                            title = info.title,
                            lastActivityEpochMs = info.lastActivityEpochMs?.toLong(),
                        )
                    }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
