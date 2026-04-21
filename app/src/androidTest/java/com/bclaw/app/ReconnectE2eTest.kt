package com.bclaw.app

import android.util.Log
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bclaw.app.domain.model.TimelineItemUi
import com.bclaw.app.service.BclawRuntime
import com.bclaw.app.testing.containsTextAnywhere
import com.bclaw.app.testing.connectToServer
import com.bclaw.app.testing.countNodesContainingText
import com.bclaw.app.testing.ForegroundServiceTeardownRule
import com.bclaw.app.testing.NotificationPermissionRule
import com.bclaw.app.testing.openDrawer
import com.bclaw.app.testing.selectThread
import com.bclaw.app.testing.taggedNodeHasText
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReconnectE2eTest {
    private val foregroundServiceTeardown = ForegroundServiceTeardownRule()
    private val notificationPermissionRule = NotificationPermissionRule()
    private val composeRuleDelegate = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(foregroundServiceTeardown)
        .around(notificationPermissionRule)
        .around(composeRuleDelegate)

    private val composeRule
        get() = composeRuleDelegate

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
            .recoverCatching { server.close() }
    }

    @Test
    fun idle_disconnect_reconnects_without_duplicate_history() {
        val phaseOne = IdleReconnectPhaseOne()
        val phaseTwo = IdleReconnectPhaseTwo()
        server.enqueue(MockResponse().withWebSocketUpgrade(phaseOne))
        server.enqueue(MockResponse().withWebSocketUpgrade(phaseTwo))
        server.start()

        composeRule.connectToServer(
            host = server.url("/").toString().replaceFirst("http", "ws"),
            workspaceNamesAndCwds = listOf("Reconnect Workspace" to "/Users/test/demo"),
        )

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("chat_root").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Reconnect thread").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(10_000) {
            composeRule.containsTextAnywhere("Historical idle reply")
        }
        composeRule.openDrawer()

        composeRule.waitUntil(15_000) {
            composeRule.taggedNodeHasText("workspace_state_label_workspace-0", "reconnecting")
        }
        composeRule.waitUntil(15_000) {
            composeRule.taggedNodeHasText("workspace_state_label_workspace-0", "connected")
        }

        composeRule.selectThread("thr-reconnect-idle")
        composeRule.waitUntil(10_000) {
            composeRule.countNodesContainingText("Historical idle reply") == 1
        }

        assertTrue("thread/resume should be called after reconnect", phaseTwo.resumeObserved.get())
        assertEquals(
            1,
            composeRule.countNodesContainingText("Historical idle reply"),
        )
    }

    @Test
    fun mid_turn_disconnect_resumes_stream_without_duplicate_items() {
        val phaseOne = MidTurnReconnectPhaseOne()
        val phaseTwo = MidTurnReconnectPhaseTwo()
        server.enqueue(MockResponse().withWebSocketUpgrade(phaseOne))
        server.enqueue(MockResponse().withWebSocketUpgrade(phaseTwo))
        server.start()

        composeRule.connectToServer(
            host = server.url("/").toString().replaceFirst("http", "ws"),
            workspaceNamesAndCwds = listOf("Reconnect Workspace" to "/Users/test/demo"),
        )

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("chat_root").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Reconnect thread").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(10_000) {
            composeRule.containsTextAnywhere("Historical reconnect baseline")
        }

        composeRule.onNodeWithTag("chat_input").performTextInput("Continue across reconnect")
        composeRule.onNodeWithTag("send_button").performClick()

        composeRule.waitUntil(10_000) {
            currentThreadState("thr-reconnect-live")
                ?.items
                ?.any { item ->
                    item is TimelineItemUi.AgentMessage &&
                        item.text.contains("Streaming before reconnect")
                } == true
        }
        composeRule.waitUntil(15_000) {
            phaseTwo.resumeObserved.get()
        }
        composeRule.waitUntil(15_000) {
            currentThreadState("thr-reconnect-live")?.latestTurnStatus == "completed"
        }
        composeRule.waitUntil(15_000) {
            currentThreadState("thr-reconnect-live")
                ?.items
                ?.count { item ->
                    item is TimelineItemUi.AgentMessage &&
                        item.text == "Streaming before reconnect and after reconnect"
                } == 1
        }

        assertTrue("thread/resume should be called after reconnect", phaseTwo.resumeObserved.get())
        assertEquals(
            1,
            composeRule.onAllNodesWithText("Continue across reconnect").fetchSemanticsNodes().size,
        )
        assertEquals(
            1,
            currentThreadState("thr-reconnect-live")
                ?.items
                ?.count { item ->
                    item is TimelineItemUi.UserMessage &&
                        item.text == "Continue across reconnect"
                },
        )
        assertEquals(
            1,
            currentThreadState("thr-reconnect-live")
                ?.items
                ?.count { item ->
                    item is TimelineItemUi.AgentMessage &&
                        item.text == "Streaming before reconnect and after reconnect"
                },
        )
    }

    private fun currentThreadState(threadId: String) =
        BclawRuntime.controller.value?.uiState?.value?.threadStates?.get(threadId)

    private class IdleReconnectPhaseOne : BaseReconnectListener("thr-reconnect-idle", "Historical idle reply") {
        private val disconnectScheduled = AtomicBoolean(false)

        override fun onMessage(webSocket: WebSocket, text: String) {
            super.onMessage(webSocket, text)
            val message = JSONObject(text)
            if (message.optString("method") == "thread/read" && disconnectScheduled.compareAndSet(false, true)) {
                Thread {
                    Thread.sleep(350)
                    webSocket.close(1012, "idle disconnect")
                }.start()
            }
        }
    }

    private class IdleReconnectPhaseTwo : BaseReconnectListener("thr-reconnect-idle", "Historical idle reply") {
        val resumeObserved = AtomicBoolean(false)

        override fun onMessage(webSocket: WebSocket, text: String) {
            super.onMessage(webSocket, text)
            val message = JSONObject(text)
            if (message.optString("method") == "thread/resume") {
                resumeObserved.set(true)
            }
        }
    }

    private class MidTurnReconnectPhaseOne : BaseReconnectListener("thr-reconnect-live", "Historical reconnect baseline") {
        private val disconnectScheduled = AtomicBoolean(false)

        override fun onMessage(webSocket: WebSocket, text: String) {
            val message = JSONObject(text)
            super.onMessage(webSocket, text)
            if (message.optString("method") == "turn/start") {
                reply(
                    webSocket = webSocket,
                    id = message.get("id"),
                    result = JSONObject()
                        .put(
                            "turn",
                            JSONObject()
                                .put("id", "turn-reconnect")
                                .put("status", "inProgress")
                                .put("items", JSONArray()),
                        ),
                )
                streamBeforeDisconnect(webSocket)
            }
        }

        private fun streamBeforeDisconnect(webSocket: WebSocket) {
            Thread {
                Thread.sleep(150)
                notify(
                    webSocket,
                    "turn/started",
                    JSONObject()
                        .put("threadId", "thr-reconnect-live")
                        .put(
                            "turn",
                            JSONObject()
                                .put("id", "turn-reconnect")
                                .put("status", "inProgress")
                                .put("items", JSONArray()),
                        ),
                )
                val liveUserItem = userMessageItem(
                    itemId = "item-user-reconnect",
                    text = "Continue across reconnect",
                )
                notify(
                    webSocket,
                    "item/started",
                    JSONObject()
                        .put("threadId", "thr-reconnect-live")
                        .put("turnId", "turn-reconnect")
                        .put("item", liveUserItem),
                )
                notify(
                    webSocket,
                    "item/completed",
                    JSONObject()
                        .put("threadId", "thr-reconnect-live")
                        .put("turnId", "turn-reconnect")
                        .put("item", liveUserItem),
                )
                notify(
                    webSocket,
                    "item/started",
                    JSONObject()
                        .put("threadId", "thr-reconnect-live")
                        .put("turnId", "turn-reconnect")
                        .put("item", JSONObject()
                            .put("id", "item-agent-reconnect")
                            .put("type", "agentMessage")
                            .put("text", "")),
                )
                Thread.sleep(120)
                notify(
                    webSocket,
                    "item/agentMessage/delta",
                    JSONObject()
                        .put("threadId", "thr-reconnect-live")
                        .put("turnId", "turn-reconnect")
                        .put("itemId", "item-agent-reconnect")
                        .put("delta", "Streaming before reconnect"),
                )
                if (disconnectScheduled.compareAndSet(false, true)) {
                    Thread.sleep(180)
                    webSocket.close(1012, "mid-turn disconnect")
                }
            }.start()
        }
    }

    private class MidTurnReconnectPhaseTwo : BaseReconnectListener("thr-reconnect-live", "Historical reconnect baseline") {
        val resumeObserved = AtomicBoolean(false)
        private val completed = AtomicBoolean(false)

        override fun onMessage(webSocket: WebSocket, text: String) {
            super.onMessage(webSocket, text)
            val message = JSONObject(text)
            if (message.optString("method") == "thread/resume" && resumeObserved.compareAndSet(false, true)) {
                Thread {
                    Thread.sleep(150)
                    if (!completed.compareAndSet(false, true)) return@Thread
                    notify(
                        webSocket,
                        "item/agentMessage/delta",
                        JSONObject()
                            .put("threadId", "thr-reconnect-live")
                            .put("turnId", "turn-reconnect")
                            .put("itemId", "item-agent-reconnect")
                            .put("delta", " and after reconnect"),
                    )
                    val finalAgentItem = JSONObject()
                        .put("id", "item-agent-reconnect")
                        .put("type", "agentMessage")
                        .put("text", "Streaming before reconnect and after reconnect")
                    notify(
                        webSocket,
                        "item/completed",
                        JSONObject()
                            .put("threadId", "thr-reconnect-live")
                            .put("turnId", "turn-reconnect")
                            .put("item", finalAgentItem),
                    )
                    notify(
                        webSocket,
                        "turn/completed",
                        JSONObject()
                            .put("threadId", "thr-reconnect-live")
                            .put(
                                "turn",
                                JSONObject()
                                    .put("id", "turn-reconnect")
                                    .put("status", "completed")
                                    .put(
                                        "items",
                                        JSONArray()
                                            .put(
                                                userMessageItem(
                                                    itemId = "item-user-reconnect",
                                                    text = "Continue across reconnect",
                                                ),
                                            )
                                            .put(finalAgentItem),
                                    ),
                            ),
                    )
                }.start()
            }
        }
    }

    private abstract class BaseReconnectListener(
        private val threadId: String,
        private val historyReply: String,
    ) : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            val message = JSONObject(text)
            Log.d("ReconnectScenario", "client -> server ${message.optString("method")}: $text")
            when (message.optString("method")) {
                "initialize" -> reply(
                    webSocket = webSocket,
                    id = message.get("id"),
                    result = JSONObject()
                        .put("userAgent", "fake-codex")
                        .put("codexHome", "/tmp/codex")
                        .put("platformFamily", "unix")
                        .put("platformOs", "macos"),
                )

                "initialized" -> Unit

                "thread/list" -> reply(
                    webSocket = webSocket,
                    id = message.get("id"),
                    result = JSONObject()
                        .put("data", JSONArray().put(threadSummary(threadId)))
                        .put("nextCursor", JSONObject.NULL),
                )

                "thread/resume" -> reply(
                    webSocket = webSocket,
                    id = message.get("id"),
                    result = JSONObject()
                        .put("thread", threadSummary(threadId))
                        .put("cwd", "/Users/test/demo")
                        .put("approvalPolicy", "never")
                        .put("approvalsReviewer", "user")
                        .put("model", "gpt-5.4-mini")
                        .put("modelProvider", "openai")
                        .put("sandbox", JSONObject().put("type", "workspaceWrite")),
                )

                "thread/read" -> reply(
                    webSocket = webSocket,
                    id = message.get("id"),
                    result = JSONObject()
                        .put(
                            "thread",
                            threadSummary(threadId)
                                .put("turns", JSONArray().put(historyTurn(historyReply))),
                        ),
                )
            }
        }

        override fun onOpen(webSocket: WebSocket, response: Response) = Unit
    }

    private companion object {
        fun reply(
            webSocket: WebSocket,
            id: Any,
            result: JSONObject,
        ) {
            webSocket.send(
                JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", id)
                    .put("result", result)
                    .toString(),
            )
        }

        fun notify(
            webSocket: WebSocket,
            method: String,
            params: JSONObject,
        ) {
            webSocket.send(
                JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("method", method)
                    .put("params", params)
                    .toString(),
            )
        }

        fun userMessageItem(itemId: String, text: String): JSONObject {
            return JSONObject()
                .put("id", itemId)
                .put("type", "userMessage")
                .put(
                    "content",
                    JSONArray().put(
                        JSONObject()
                            .put("type", "text")
                            .put("text", text),
                    ),
                )
        }

        fun historyTurn(agentText: String): JSONObject {
            return JSONObject()
                .put("id", "turn-history")
                .put("status", "completed")
                .put(
                    "items",
                    JSONArray()
                        .put(
                            userMessageItem(
                                itemId = "item-user-history",
                                text = "Historical prompt",
                            ),
                        )
                        .put(
                            JSONObject()
                                .put("id", "item-agent-history")
                                .put("type", "agentMessage")
                                .put("text", agentText),
                        ),
                )
        }

        fun threadSummary(threadId: String): JSONObject {
            return JSONObject()
                .put("id", threadId)
                .put("name", "Reconnect thread")
                .put("preview", "Reconnect preview")
                .put("modelProvider", "openai")
                .put("createdAt", 1_710_000_000L)
                .put("updatedAt", 1_710_000_600L)
                .put("status", JSONObject().put("type", "idle").put("activeFlags", JSONArray()))
                .put("cwd", "/Users/test/demo")
                .put("cliVersion", "0.120.0")
                .put("path", "/tmp/$threadId.json")
                .put("source", "appServer")
                .put("ephemeral", false)
        }
    }
}
