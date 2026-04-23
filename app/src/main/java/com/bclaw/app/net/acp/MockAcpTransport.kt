package com.bclaw.app.net.acp

import com.bclaw.app.net.BclawJson
import com.bclaw.app.net.JsonRpcSession
import java.io.IOException
import java.net.URI
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer
import okhttp3.OkHttpClient

class MockAcpTransport(
    private val delegate: JsonRpcSession.Delegate,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    constructor(delegate: JsonRpcSession.Delegate, @Suppress("UNUSED_PARAMETER") okHttpClient: OkHttpClient) : this(delegate, Dispatchers.Default)
    @PublishedApi internal val json: Json = BclawJson
    private var scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val nextSessionId = AtomicLong(1)
    private val generation = AtomicLong(0)
    private val sessions = ConcurrentHashMap<String, MockSession>()
    private val activeTurns = ConcurrentHashMap<String, Job>()
    private var connected = false
    private var agentKey = "mock"

    suspend fun connect(url: String, @Suppress("UNUSED_PARAMETER") token: String, initializeParams: AcpInitializeParams): AcpInitializeResult {
        close()
        sessions.clear()
        agentKey = parseAgent(url)
        connected = true
        generation.incrementAndGet()
        delegate.onTransportOpening()
        return initializeResult(initializeParams.protocolVersion).also { delegate.onTransportReady() }
    }
    internal suspend inline fun <reified T> request(
        method: String,
        params: JsonElement = emptyJsonObject,
    ): T = json.decodeFromJsonElement(serializer<T>(), requestRaw(method, params))
    suspend fun requestRaw(method: String, params: JsonElement = emptyJsonObject): JsonElement {
        ensureConnected()
        return when (method) {
            "initialize" -> {
                val request = json.decodeFromJsonElement(AcpInitializeParams.serializer(), params)
                json.encodeToJsonElement(AcpInitializeResult.serializer(), initializeResult(request.protocolVersion))
            }
            "session/new" -> {
                val request = json.decodeFromJsonElement(AcpSessionNewParams.serializer(), params)
                val sessionId = "mock-${agentKey}-${nextSessionId.getAndIncrement()}"
                val session = MockSession(sessionId = sessionId, cwd = request.cwd, title = "composer · autofocus")
                sessions[sessionId] = session
                json.encodeToJsonElement(AcpSessionNewResult.serializer(), AcpSessionNewResult(sessionId = sessionId))
            }
            "session/list" -> {
                val result = AcpSessionListResult(
                    sessions = sessions.values.sortedByDescending { it.updatedAt }.map {
                        AcpSessionInfo(it.sessionId, it.cwd, it.title, it.updatedAt)
                    },
                )
                json.encodeToJsonElement(AcpSessionListResult.serializer(), result)
            }
            "session/load" -> handleLoad(params)
            "session/prompt" -> handlePrompt(params)
            "session/cancel" -> {
                cancelTurn(json.decodeFromJsonElement(AcpSessionCancelParams.serializer(), params).sessionId)
                emptyJsonObject
            }
            else -> throw IOException("mock does not implement $method")
        }
    }
    suspend fun notify(method: String, params: JsonElement = emptyJsonObject) {
        ensureConnected()
        when (method) {
            "session/cancel" -> cancelTurn(json.decodeFromJsonElement(AcpSessionCancelParams.serializer(), params).sessionId)
            else -> throw IOException("mock does not implement $method")
        }
    }
    fun close(code: Int = 1000, reason: String = "client closing") {
        val wasConnected = connected
        connected = false
        generation.incrementAndGet()
        activeTurns.values.forEach { it.cancel(CancellationException(reason)) }
        activeTurns.clear()
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + dispatcher)
        if (wasConnected) delegate.onTransportClosed(code, reason)
    }
    fun shutdown() { close(); scope.cancel() }
    private fun initializeResult(protocolVersion: Int = 1): AcpInitializeResult = AcpInitializeResult(
        protocolVersion = protocolVersion,
        agentCapabilities = AcpAgentCapabilities(
            loadSession = true,
            promptCapabilities = AcpPromptCapabilities(embeddedContext = true),
            mcpCapabilities = AcpMcpCapabilities(http = true),
            sessionCapabilities = AcpSessionCapabilities(list = AcpSessionListCapability()),
        ),
        agentInfo = AcpAgentInfo(name = agentKey, title = "Mock ${agentKey.replaceFirstChar { it.uppercase() }}", version = "mock"),
    )
    private suspend fun handleLoad(params: JsonElement): JsonElement {
        val request = json.decodeFromJsonElement(AcpSessionLoadParams.serializer(), params)
        val session = sessions[request.sessionId] ?: throw IOException("mock session not found: ${request.sessionId}")
        emitUpdate(request.sessionId, buildJsonObject {
            put("sessionUpdate", "session_info_update")
            session.title?.let { put("title", it) }
        })
        if (session.lastUserText.isNotBlank()) emitChunk(request.sessionId, "user_message_chunk", session.lastUserText)
        if (session.lastAgentText.isNotBlank()) emitChunk(request.sessionId, "agent_message_chunk", session.lastAgentText)
        return emptyJsonObject
    }
    private suspend fun handlePrompt(params: JsonElement): JsonElement {
        val request = json.decodeFromJsonElement(AcpPromptParams.serializer(), params)
        val session = sessions[request.sessionId] ?: throw IOException("mock session not found: ${request.sessionId}")
        if (activeTurns.containsKey(request.sessionId)) throw IOException("mock prompt already running for ${request.sessionId}")
        val result = CompletableDeferred<JsonElement>()
        val token = generation.get()
        val job = scope.launch {
            var toolPending = false
            val toolCallId = "call-${request.sessionId.takeLast(6)}"
            try {
                val promptText = request.prompt.joinToString("\n") { it.text.orEmpty() }.trim()
                session.lastUserText = promptText
                session.title = "composer · autofocus"
                touch(session)
                delay(400)
                emitUpdate(request.sessionId, token, buildJsonObject {
                    put("sessionUpdate", "session_info_update")
                    put("title", session.title!!)
                })
                val chunks = listOf(
                    "Looked at the composer focus issue.",
                    "Reproducing the flow through the mock ACP transport now.",
                    "Applying the fix and checking the build step.",
                    "Build is green and the turn can close cleanly.",
                )
                for ((index, text) in chunks.withIndex()) {
                    delay(600)
                    emitChunk(request.sessionId, "agent_message_chunk", text, token)
                    if (index == 0) {
                        delay(600)
                        toolPending = true
                        emitUpdate(request.sessionId, token, buildJsonObject {
                            put("sessionUpdate", "tool_call")
                            put("toolCallId", toolCallId)
                            put("title", "exec_command")
                            put("kind", "execute")
                            put("status", "pending")
                            put("rawInput", """{"cmd":"pnpm build"}""")
                        })
                    }
                    if (index == 2) {
                        delay(600)
                        toolPending = false
                        emitUpdate(request.sessionId, token, buildJsonObject {
                            put("sessionUpdate", "tool_call_update")
                            put("toolCallId", toolCallId)
                            put("status", "completed")
                            put("rawOutput", "Build OK · 24s")
                            put("content", buildJsonArray {
                                add(buildJsonObject {
                                    put("type", "content")
                                    put("content", textContent("Build OK · 24s"))
                                })
                            })
                        })
                    }
                }
                session.lastAgentText = chunks.joinToString(" ")
                touch(session)
                result.complete(promptResult("end_turn"))
            } catch (_: CancellationException) {
                if (toolPending) emitUpdate(request.sessionId, token, buildJsonObject {
                    put("sessionUpdate", "tool_call_update")
                    put("toolCallId", toolCallId)
                    put("status", "cancelled")
                    put("rawOutput", "Cancelled by user")
                })
                result.complete(promptResult("cancelled"))
            } catch (t: Throwable) {
                delegate.onTransportFailure(t)
                result.completeExceptionally(t)
            } finally {
                activeTurns.remove(request.sessionId)
            }
        }
        job.invokeOnCompletion { cause ->
            if (cause is CancellationException && !result.isCompleted) result.complete(promptResult("cancelled"))
        }
        activeTurns[request.sessionId] = job
        return result.await()
    }
    private suspend fun emitChunk(sessionId: String, kind: String, text: String, token: Long = generation.get()) {
        emitUpdate(sessionId, token, buildJsonObject {
            put("sessionUpdate", kind)
            put("content", textContent(text))
        })
    }
    private suspend fun emitUpdate(sessionId: String, update: JsonObject) = emitUpdate(sessionId, generation.get(), update)
    private suspend fun emitUpdate(sessionId: String, token: Long, update: JsonObject) {
        if (!connected || token != generation.get()) return
        delegate.onNotification(
            method = "session/update",
            params = buildJsonObject {
                put("sessionId", sessionId)
                put("update", update)
            },
        )
    }
    private fun promptResult(stopReason: String) = json.encodeToJsonElement(AcpPromptResult.serializer(), AcpPromptResult(stopReason))
    private fun textContent(text: String) = buildJsonObject {
        put("type", "text")
        put("text", text)
    }
    private fun cancelTurn(sessionId: String) { activeTurns.remove(sessionId)?.cancel(CancellationException("session cancelled")) }
    private fun touch(session: MockSession) { session.updatedAt = Instant.now().toString() }
    private fun ensureConnected() { if (!connected) throw IOException("mock transport is not connected") }
    private fun parseAgent(url: String): String =
        runCatching { URI(url).path.substringAfterLast('/') }.getOrDefault(url.substringAfterLast('/'))
            .ifBlank { "mock" }.replace(Regex("[^a-zA-Z0-9]+"), "-").trim('-').lowercase().ifBlank { "mock" }
    private data class MockSession(val sessionId: String, val cwd: String, var title: String?, var updatedAt: String = Instant.now().toString(), var lastUserText: String = "", var lastAgentText: String = "")
    internal companion object {
        val emptyJsonObject = JsonObject(emptyMap())
    }
}
