package com.bclaw.app.net.acp

import com.bclaw.app.net.BclawJson
import com.bclaw.app.net.JsonRpcSession
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MockAcpTransportTest {
    @Test
    fun connectReturnsInitializeResult() = runTest {
        val delegate = RecordingDelegate()
        val transport = MockAcpTransport(delegate, StandardTestDispatcher(testScheduler))

        val result = transport.connect("ws://localhost:8766/codex", "", AcpInitializeParams())

        assertTrue(delegate.opened)
        assertTrue(delegate.ready)
        assertEquals(1, result.protocolVersion)
        assertTrue(result.agentCapabilities.loadSession)
        assertEquals("codex", result.agentInfo?.name)
    }

    @Test
    fun sessionNewReturnsMockPrefixedId() = runTest {
        val delegate = RecordingDelegate()
        val transport = MockAcpTransport(delegate, StandardTestDispatcher(testScheduler))
        transport.connect("ws://localhost:8766/codex", "", AcpInitializeParams())

        val result = BclawJson.decodeFromJsonElement(
            AcpSessionNewResult.serializer(),
            transport.requestRaw(
                "session/new",
                BclawJson.encodeToJsonElement(AcpSessionNewParams.serializer(), AcpSessionNewParams(cwd = "/tmp/mock")),
            ),
        )

        assertTrue(result.sessionId.startsWith("mock-"))
    }

    @Test
    fun sessionPromptStreamsAgentMessageChunk() = runTest {
        val delegate = RecordingDelegate()
        val transport = MockAcpTransport(delegate, StandardTestDispatcher(testScheduler))
        transport.connect("ws://localhost:8766/codex", "", AcpInitializeParams())
        val sessionId = BclawJson.decodeFromJsonElement(
            AcpSessionNewResult.serializer(),
            transport.requestRaw(
                "session/new",
                BclawJson.encodeToJsonElement(AcpSessionNewParams.serializer(), AcpSessionNewParams(cwd = "/tmp/mock")),
            ),
        ).sessionId

        val prompt = async {
            BclawJson.decodeFromJsonElement(
                AcpPromptResult.serializer(),
                transport.requestRaw(
                    "session/prompt",
                    BclawJson.encodeToJsonElement(
                        AcpPromptParams.serializer(),
                        AcpPromptParams(sessionId, listOf(AcpContentBlock(type = "text", text = "Fix compose focus"))),
                    ),
                ),
            )
        }

        assertFalse(prompt.isCompleted)
        advanceTimeBy(1100)
        val firstChunk = delegate.agentMessageTexts().firstOrNull()
        assertNotNull(firstChunk)
        assertTrue(firstChunk!!.isNotBlank())
        advanceUntilIdle()
        assertEquals("end_turn", prompt.await().stopReason)
    }

    @Test
    fun sessionCancelStopsRunningTurn() = runTest {
        val delegate = RecordingDelegate()
        val transport = MockAcpTransport(delegate, StandardTestDispatcher(testScheduler))
        transport.connect("ws://localhost:8766/codex", "", AcpInitializeParams())
        val sessionId = BclawJson.decodeFromJsonElement(
            AcpSessionNewResult.serializer(),
            transport.requestRaw(
                "session/new",
                BclawJson.encodeToJsonElement(AcpSessionNewParams.serializer(), AcpSessionNewParams(cwd = "/tmp/mock")),
            ),
        ).sessionId
        val prompt = async {
            BclawJson.decodeFromJsonElement(
                AcpPromptResult.serializer(),
                transport.requestRaw(
                    "session/prompt",
                    BclawJson.encodeToJsonElement(
                        AcpPromptParams.serializer(),
                        AcpPromptParams(sessionId, listOf(AcpContentBlock(type = "text", text = "Cancel this turn"))),
                    ),
                ),
            )
        }

        advanceTimeBy(1800)
        transport.requestRaw(
            "session/cancel",
            BclawJson.encodeToJsonElement(AcpSessionCancelParams.serializer(), AcpSessionCancelParams(sessionId)),
        )
        advanceUntilIdle()

        assertEquals("cancelled", prompt.await().stopReason)
    }

    private class RecordingDelegate : JsonRpcSession.Delegate {
        var opened = false
        var ready = false
        val notifications = mutableListOf<JsonObject>()

        override fun onTransportOpening() { opened = true }
        override fun onTransportReady() { ready = true }
        override fun onTransportClosed(code: Int, reason: String) = Unit
        override fun onTransportFailure(throwable: Throwable) = throw AssertionError(throwable)
        override suspend fun onNotification(method: String, params: JsonObject) {
            if (method == "session/update") notifications += params
        }
        override suspend fun onServerRequest(id: JsonElement, method: String, params: JsonObject): JsonElement {
            error("mock server request not expected: $method")
        }

        fun agentMessageTexts(): List<String> = notifications.mapNotNull { params ->
            val update = params["update"]?.jsonObject ?: return@mapNotNull null
            if (update["sessionUpdate"]?.jsonPrimitive?.content != "agent_message_chunk") return@mapNotNull null
            update["content"]?.jsonObject?.get("text")?.jsonPrimitive?.content
        }
    }
}
