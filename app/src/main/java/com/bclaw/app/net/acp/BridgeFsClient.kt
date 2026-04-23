package com.bclaw.app.net.acp

import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request

@Serializable
data class FsEntry(
    val name: String,
    val kind: String,        // "dir" | "file" | "other"
    val size: Long? = null,
)

@Serializable
data class FsListing(
    val cwd: String = "",
    val rel: String = ".",
    /** Resolved absolute path of the listed directory (post-realpath). */
    val absPath: String = "",
    /** Sandbox root above which navigation must not go — clamp point for the `..` button. */
    val safeRoot: String = "",
    val entries: List<FsEntry> = emptyList(),
)

@Serializable
data class FsFile(
    val cwd: String = "",
    val rel: String = "",
    val sizeBytes: Long = 0,
    val truncated: Boolean = false,
    val bytesRead: Int = 0,
    val data: String = "",   // base64
)

/**
 * HTTP client for the bridge's `/fs/list` + `/fs/read` endpoints. Used by Tools→files →
 * "browse mac files" to walk the project tree and fetch file content for embedding in an
 * ACP `resource` content block.
 *
 * Reuses [BridgeReachability.client] so connect/timeout failures flip the bridge phase
 * the same way the discovery calls do — a dead bridge surfaces as Offline immediately.
 */
object BridgeFsClient {

    private val client = BridgeReachability.client
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun list(bridgeWsUrl: String, cwd: String, rel: String = "."): FsListing? =
        withContext(Dispatchers.IO) {
            val httpUrl = httpBase(bridgeWsUrl) +
                "/fs/list?cwd=" + URLEncoder.encode(cwd, "UTF-8") +
                "&rel=" + URLEncoder.encode(rel, "UTF-8")
            runCatching {
                val req = Request.Builder().url(httpUrl).get().build()
                val resp = client.newCall(req).execute()
                if (!resp.isSuccessful) return@runCatching null
                val body = resp.body?.string() ?: return@runCatching null
                json.decodeFromString(FsListing.serializer(), body)
            }.getOrNull()
        }

    suspend fun read(
        bridgeWsUrl: String,
        cwd: String,
        rel: String,
        maxBytes: Int = DEFAULT_MAX_READ_BYTES,
    ): FsFile? = withContext(Dispatchers.IO) {
        val httpUrl = httpBase(bridgeWsUrl) +
            "/fs/read?cwd=" + URLEncoder.encode(cwd, "UTF-8") +
            "&rel=" + URLEncoder.encode(rel, "UTF-8") +
            "&maxBytes=" + maxBytes
        runCatching {
            val req = Request.Builder().url(httpUrl).get().build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@runCatching null
            val body = resp.body?.string() ?: return@runCatching null
            json.decodeFromString(FsFile.serializer(), body)
        }.getOrNull()
    }

    private fun httpBase(bridgeWsUrl: String) = bridgeWsUrl
        .replace("ws://", "http://")
        .replace("wss://", "https://")
        .trimEnd('/')

    /**
     * URL for the bridge's `/fs/raw` endpoint, which streams the file as raw bytes
     * (no base64/JSON wrapping). Fed to Coil's AsyncImage for image loading — the
     * URL is a stable cache key, so Coil's memory + disk cache front-run repeated
     * loads of the same path with zero server work.
     */
    fun rawUrl(bridgeWsUrl: String, absPath: String): String {
        val lastSlash = absPath.lastIndexOf('/')
        val cwd = if (lastSlash > 0) absPath.substring(0, lastSlash) else "/"
        val rel = if (lastSlash >= 0) absPath.substring(lastSlash + 1) else absPath
        return httpBase(bridgeWsUrl) +
            "/fs/raw?cwd=" + URLEncoder.encode(cwd, "UTF-8") +
            "&rel=" + URLEncoder.encode(rel, "UTF-8")
    }

    /**
     * Default per-read cap. Images (generated PNGs, pasted screenshots) can be several MB;
     * a smaller cap truncates the PNG stream and the phone decoder renders garbage. Keep
     * this generous — callers that want a smaller fetch (e.g. a text preview) should pass
     * an explicit `maxBytes`.
     */
    const val DEFAULT_MAX_READ_BYTES = 10 * 1024 * 1024
}
