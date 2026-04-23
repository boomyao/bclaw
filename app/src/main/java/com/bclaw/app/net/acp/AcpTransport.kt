package com.bclaw.app.net.acp

import com.bclaw.app.net.BclawJson
import com.bclaw.app.net.JsonRpcSession
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer

/**
 * Uniform ACP transport surface.
 *
 * Exists so `BclawV2Controller` can be wired against either:
 *   - the real `JsonRpcSession` (which talks OkHttp WebSocket → `bridge/server.js` → agent CLI)
 *   - the offline `MockAcpTransport` (which fakes a canned turn for UI dev and unit tests)
 *
 * Kotlin interfaces can't carry a reified inline `request<T>`, so the typed decoder lives as
 * the [request] extension below. Concrete wrappers implement only [requestRaw].
 */
interface AcpTransport {
    suspend fun connect(
        url: String,
        token: String,
        initializeParams: AcpInitializeParams,
    ): AcpInitializeResult

    suspend fun requestRaw(method: String, params: JsonElement): JsonElement

    suspend fun notify(method: String, params: JsonElement)

    fun close(code: Int = 1000, reason: String = "client closing")

    fun shutdown()
}

/**
 * Decode the ACP response into the expected kotlinx-serialization type. Uses [BclawJson] so
 * the lenient config (ignore unknown keys, explicit nulls off) is applied uniformly.
 */
suspend inline fun <reified T> AcpTransport.request(
    method: String,
    params: JsonElement = JsonObject(emptyMap()),
): T = BclawJson.decodeFromJsonElement(serializer<T>(), requestRaw(method, params))

// ── Real (WebSocket) adapter ────────────────────────────────────────────

/**
 * Wraps a [JsonRpcSession] so its API shape fits [AcpTransport]. Construction pattern:
 *
 * ```kotlin
 * val session = JsonRpcSession(delegate = ...)
 * val transport: AcpTransport = JsonRpcAcpTransport(session)
 * ```
 */
class JsonRpcAcpTransport(private val session: JsonRpcSession) : AcpTransport {
    override suspend fun connect(
        url: String,
        token: String,
        initializeParams: AcpInitializeParams,
    ): AcpInitializeResult = session.connect(url, token, initializeParams)

    override suspend fun requestRaw(method: String, params: JsonElement): JsonElement =
        session.requestRaw(method, params)

    override suspend fun notify(method: String, params: JsonElement) {
        session.notify(method, params)
    }

    override fun close(code: Int, reason: String) = session.close(code, reason)

    override fun shutdown() = session.shutdown()
}

// ── Mock adapter (tests + offline UI dev) ───────────────────────────────

/**
 * Wraps a [MockAcpTransport] so its API shape fits [AcpTransport]. Used in:
 *   - the unit test suite (`MockAcpTransportTest`)
 *   - developer-mode offline UI iteration (no bridge running)
 *
 * Production app uses [JsonRpcAcpTransport]; this adapter is NOT a production fallback.
 */
class MockAcpTransportAdapter(private val mock: MockAcpTransport) : AcpTransport {
    override suspend fun connect(
        url: String,
        token: String,
        initializeParams: AcpInitializeParams,
    ): AcpInitializeResult = mock.connect(url, token, initializeParams)

    override suspend fun requestRaw(method: String, params: JsonElement): JsonElement =
        mock.requestRaw(method, params)

    override suspend fun notify(method: String, params: JsonElement) {
        mock.notify(method, params)
    }

    override fun close(code: Int, reason: String) = mock.close(code, reason)

    override fun shutdown() = mock.shutdown()
}

// ── Factory typealias ───────────────────────────────────────────────────

/**
 * Produces an [AcpTransport] bound to the given delegate. The controller calls this once per
 * agent id to get that agent's transport.
 *
 * Production: `{ delegate -> JsonRpcAcpTransport(JsonRpcSession(delegate)) }` (default).
 * Tests: `{ delegate -> MockAcpTransportAdapter(MockAcpTransport(delegate)) }`.
 */
typealias AcpTransportFactory = (JsonRpcSession.Delegate) -> AcpTransport

val DefaultAcpTransportFactory: AcpTransportFactory = { delegate ->
    JsonRpcAcpTransport(JsonRpcSession(delegate))
}
