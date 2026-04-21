package com.bclaw.app.net

import com.bclaw.app.net.acp.AcpInitializeParams
import com.bclaw.app.net.acp.AcpInitializeResult
import com.bclaw.app.net.codex.CodexJson
import com.bclaw.app.net.codex.JsonRpcResponseEnvelope
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import org.jetbrains.annotations.VisibleForTesting

class JsonRpcException(
    val code: Int,
    override val message: String,
    val data: JsonElement? = null,
) : IOException(message)

class JsonRpcSession(
    private val delegate: Delegate,
    okHttpClient: OkHttpClient = OkHttpClient.Builder().build(),
) {
    interface Delegate {
        fun onTransportOpening()
        fun onTransportReady()
        fun onTransportClosed(code: Int, reason: String)
        fun onTransportFailure(throwable: Throwable)
        suspend fun onNotification(method: String, params: JsonObject)
        suspend fun onServerRequest(id: JsonElement, method: String, params: JsonObject): JsonElement
    }

    @PublishedApi
    internal val json = CodexJson.json
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val webSocketClient = WebSocketClient(okHttpClient)
    private val nextRequestId = AtomicLong(1)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JsonElement>>()
    private var webSocket: WebSocket? = null
    suspend fun connect(
        url: String,
        token: String,
        initializeParams: AcpInitializeParams,
    ): AcpInitializeResult {
        close()
        delegate.onTransportOpening()
        val ready = CompletableDeferred<AcpInitializeResult>()
        webSocket = webSocketClient.connect(
            url = url,
            bearerToken = token,
                listener = object : WebSocketClient.Listener {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        if (!isCurrentSocket(webSocket)) {
                            webSocket.close(1000, "stale socket")
                            return
                        }
                        scope.launch {
                            runCatching {
                                val init = requestRaw(
                                "initialize",
                                json.encodeToJsonElement(
                                    AcpInitializeParams.serializer(),
                                    initializeParams,
                                ),
                            )
                            val initResult = json.decodeFromJsonElement(
                                AcpInitializeResult.serializer(),
                                init,
                            )
                            delegate.onTransportReady()
                            ready.complete(initResult)
                        }.onFailure { error ->
                            ready.completeExceptionally(error)
                        }
                    }
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        if (!isCurrentSocket(webSocket)) return
                        scope.launch { handleMessage(text) }
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        if (!isCurrentSocket(webSocket)) return
                        webSocket.close(code, reason)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        if (!markSocketClosed(webSocket)) return
                        failPending(IOException("socket closed: $code $reason"))
                        delegate.onTransportClosed(code, reason)
                    }

                    override fun onFailure(webSocket: WebSocket?, throwable: Throwable, response: Response?) {
                        if (!markSocketClosed(webSocket)) return
                        failPending(throwable)
                        if (!ready.isCompleted) {
                            ready.completeExceptionally(throwable)
                    }
                    delegate.onTransportFailure(throwable)
                }
            },
        )
        return ready.await()
    }

    internal suspend inline fun <reified T> request(
        method: String,
        params: JsonElement = emptyJsonObject,
    ): T {
        val result = requestRaw(method, params)
        return json.decodeFromJsonElement(serializer<T>(), result)
    }

    suspend fun requestRaw(method: String, params: JsonElement = emptyJsonObject): JsonElement {
        val requestId = nextRequestId.getAndIncrement().toString()
        val deferred = CompletableDeferred<JsonElement>()
        pending[requestId] = deferred
        sendEnvelope(
            buildJsonObject {
                put("id", JsonPrimitive(requestId.toLong()))
                put("method", method)
                put("params", params)
            },
        )
        return deferred.await()
    }

    suspend fun notify(method: String, params: JsonElement = emptyJsonObject) {
        sendEnvelope(
            buildJsonObject {
                put("method", method)
                put("params", params)
            },
        )
    }

    fun close(code: Int = 1000, reason: String = "client closing") {
        webSocket?.close(code, reason)
        webSocket = null
    }

    fun shutdown() {
        close()
        scope.cancel()
    }

    private suspend fun handleMessage(text: String) {
        val envelope = json.parseToJsonElement(text).jsonObject
        when {
            envelope["method"] != null && envelope["id"] != null -> {
                val id = envelope.getValue("id")
                val method = envelope.getValue("method").jsonPrimitive.content
                val params = envelope["params"]?.jsonObject ?: emptyJsonObject
                val result = delegate.onServerRequest(id, method, params)
                sendEnvelope(
                    buildJsonObject {
                        put("id", id)
                        put("result", result)
                    },
                )
            }

            envelope["method"] != null -> {
                val method = envelope.getValue("method").jsonPrimitive.content
                val params = envelope["params"]?.jsonObject ?: emptyJsonObject
                delegate.onNotification(method, params)
            }

            envelope["id"] != null -> {
                val response = json.decodeFromJsonElement(JsonRpcResponseEnvelope.serializer(), envelope)
                val requestId = response.id.jsonPrimitive.content
                val deferred = pending.remove(requestId) ?: return
                if (response.error != null) {
                    deferred.completeExceptionally(
                        JsonRpcException(
                            code = response.error.code,
                            message = response.error.message,
                            data = response.error.data,
                        ),
                    )
                } else {
                    deferred.complete(response.result ?: emptyJsonObject)
                }
            }
        }
    }

    private suspend fun sendEnvelope(payload: JsonObject) {
        val encoded = json.encodeToString(JsonObject.serializer(), payload)
        val sent = webSocket?.send(encoded) ?: false
        if (!sent) {
            throw IOException("websocket is not connected")
        }
    }

    private fun failPending(throwable: Throwable) {
        val iterator = pending.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            entry.value.completeExceptionally(throwable)
            iterator.remove()
        }
    }

    private fun isCurrentSocket(candidate: WebSocket?): Boolean {
        return candidate != null && candidate == webSocket
    }

    private fun markSocketClosed(candidate: WebSocket?): Boolean {
        if (!isCurrentSocket(candidate)) return false
        webSocket = null
        return true
    }

    @VisibleForTesting
    @PublishedApi
    internal companion object {
        val emptyJsonObject = JsonObject(emptyMap())
    }
}
