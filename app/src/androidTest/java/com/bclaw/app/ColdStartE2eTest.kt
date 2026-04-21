package com.bclaw.app

import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bclaw.app.MainActivity
import com.bclaw.app.testing.containsTextAnywhere
import com.bclaw.app.testing.ForegroundServiceTeardownRule
import com.bclaw.app.testing.NotificationPermissionRule
import com.bclaw.app.testing.connectToServer
import com.bclaw.app.testing.selectWorkspace
import java.util.concurrent.TimeUnit
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ColdStartE2eTest {
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
        server.enqueue(
            MockResponse().withWebSocketUpgrade(FakeCodexScenario()),
        )
        server.start()
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
            .recoverCatching {
                server.close()
            }
    }

    @Test
    fun coldStart_to_full_userAgentLoop_rendersStreamingConversation() {
        val host = server.url("/").toString().replaceFirst("http", "ws")

        composeRule.connectToServer(
            host = host,
            workspaceNamesAndCwds = listOf("Demo Workspace" to "/Users/test/demo"),
        )

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("chat_root").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.selectWorkspace("workspace-0")

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Spec thread").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(10_000) {
            composeRule.containsTextAnywhere("Historical answer from desktop")
        }

        composeRule.onNodeWithTag("chat_input").performTextInput("Ping from phone")
        composeRule.onNodeWithTag("send_button").performClick()

        composeRule.waitUntil(15_000) {
            composeRule.containsTextAnywhere("Hello from fake Codex stream")
        }
    }

    private class FakeCodexScenario : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            val message = JSONObject(text)
            Log.d("FakeCodexScenario", "client -> server ${message.optString("method")}: $text")
            when (message.optString("method")) {
                "initialize" -> {
                    reply(
                        webSocket = webSocket,
                        id = message.get("id"),
                        result = JSONObject()
                            .put("userAgent", "fake-codex")
                            .put("codexHome", "/tmp/codex")
                            .put("platformFamily", "unix")
                            .put("platformOs", "macos"),
                    )
                }

                "initialized" -> Unit

                "thread/list" -> {
                    reply(
                        webSocket = webSocket,
                        id = message.get("id"),
                        result = JSONObject()
                            .put("data", JSONArray().put(threadSummary()))
                            .put("nextCursor", JSONObject.NULL),
                    )
                }

                "thread/resume" -> {
                    reply(
                        webSocket = webSocket,
                        id = message.get("id"),
                        result = JSONObject()
                            .put("thread", threadSummary())
                            .put("cwd", "/Users/test/demo")
                            .put("approvalPolicy", "never")
                            .put("approvalsReviewer", "user")
                            .put("model", "gpt-5.4-mini")
                            .put("modelProvider", "openai")
                            .put("sandbox", JSONObject().put("type", "workspaceWrite")),
                    )
                }

                "thread/read" -> {
                    reply(
                        webSocket = webSocket,
                        id = message.get("id"),
                        result = JSONObject()
                            .put(
                                "thread",
                                threadSummary()
                                    .put("turns", JSONArray().put(historyTurn())),
                            ),
                    )
                }

                "turn/start" -> {
                    reply(
                        webSocket = webSocket,
                        id = message.get("id"),
                        result = JSONObject()
                            .put(
                                "turn",
                                JSONObject()
                                    .put("id", "turn-live")
                                    .put("status", "inProgress")
                                    .put("items", JSONArray()),
                            ),
                    )
                    streamConversation(webSocket)
                }

                "turn/interrupt" -> {
                    reply(
                        webSocket = webSocket,
                        id = message.get("id"),
                        result = JSONObject(),
                    )
                }
            }
        }

        private fun streamConversation(webSocket: WebSocket) {
            Thread {
                Thread.sleep(150)
                Log.d("FakeCodexScenario", "server streaming turn notifications")
                notify(
                    webSocket,
                    "turn/started",
                    JSONObject()
                        .put("threadId", "thr-existing")
                        .put(
                            "turn",
                            JSONObject()
                                .put("id", "turn-live")
                                .put("status", "inProgress")
                                .put("items", JSONArray()),
                        ),
                )

                val liveUserItem = JSONObject()
                    .put("id", "item-user-live")
                    .put("type", "userMessage")
                    .put(
                        "content",
                        JSONArray().put(
                            JSONObject()
                                .put("type", "text")
                                .put("text", "Ping from phone"),
                        ),
                    )

                notify(
                    webSocket,
                    "item/started",
                    JSONObject()
                        .put("threadId", "thr-existing")
                        .put("turnId", "turn-live")
                        .put("item", liveUserItem),
                )
                notify(
                    webSocket,
                    "item/completed",
                    JSONObject()
                        .put("threadId", "thr-existing")
                        .put("turnId", "turn-live")
                        .put("item", liveUserItem),
                )

                val liveAgentStarted = JSONObject()
                    .put("id", "item-agent-live")
                    .put("type", "agentMessage")
                    .put("text", "")

                notify(
                    webSocket,
                    "item/started",
                    JSONObject()
                        .put("threadId", "thr-existing")
                        .put("turnId", "turn-live")
                        .put("item", liveAgentStarted),
                )
                Thread.sleep(120)
                notifyDelta(webSocket, "Hello ")
                Thread.sleep(120)
                notifyDelta(webSocket, "from fake ")
                Thread.sleep(120)
                notifyDelta(webSocket, "Codex stream")

                val liveAgentCompleted = JSONObject()
                    .put("id", "item-agent-live")
                    .put("type", "agentMessage")
                    .put("text", "Hello from fake Codex stream")

                notify(
                    webSocket,
                    "item/completed",
                    JSONObject()
                        .put("threadId", "thr-existing")
                        .put("turnId", "turn-live")
                        .put("item", liveAgentCompleted),
                )
                notify(
                    webSocket,
                    "turn/completed",
                    JSONObject()
                        .put("threadId", "thr-existing")
                        .put(
                            "turn",
                            JSONObject()
                                .put("id", "turn-live")
                                .put("status", "completed")
                                .put("items", JSONArray().put(liveUserItem).put(liveAgentCompleted)),
                        ),
                )
            }.start()
        }

        private fun notifyDelta(webSocket: WebSocket, delta: String) {
            notify(
                webSocket,
                "item/agentMessage/delta",
                JSONObject()
                    .put("threadId", "thr-existing")
                    .put("turnId", "turn-live")
                    .put("itemId", "item-agent-live")
                    .put("delta", delta),
            )
        }

        private fun reply(webSocket: WebSocket, id: Any, result: JSONObject) {
            val payload = JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", id)
                    .put("result", result)
                    .toString()
            Log.d("FakeCodexScenario", "server -> client response: $payload")
            webSocket.send(payload)
        }

        private fun notify(webSocket: WebSocket, method: String, params: JSONObject) {
            val payload = JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("method", method)
                    .put("params", params)
                    .toString()
            Log.d("FakeCodexScenario", "server -> client notification $method: $payload")
            webSocket.send(payload)
        }

        private fun threadSummary(): JSONObject {
            return JSONObject()
                .put("id", "thr-existing")
                .put("preview", "Historical answer from desktop")
                .put("modelProvider", "openai")
                .put("createdAt", TimeUnit.SECONDS.convert(System.currentTimeMillis() - 60_000, TimeUnit.MILLISECONDS))
                .put("updatedAt", TimeUnit.SECONDS.convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS))
                .put("status", JSONObject().put("type", "idle").put("activeFlags", JSONArray()))
                .put("cwd", "/Users/test/demo")
                .put("cliVersion", "0.120.0")
                .put("name", "Spec thread")
                .put("turns", JSONArray())
                .put("source", "appServer")
                .put("ephemeral", false)
        }

        private fun historyTurn(): JSONObject {
            val oldUser = JSONObject()
                .put("id", "item-user-old")
                .put("type", "userMessage")
                .put(
                    "content",
                    JSONArray().put(
                        JSONObject()
                            .put("type", "text")
                            .put("text", "Historical question"),
                    ),
                )
            val oldAgent = JSONObject()
                .put("id", "item-agent-old")
                .put("type", "agentMessage")
                .put("text", "Historical answer from desktop")

            return JSONObject()
                .put("id", "turn-old")
                .put("status", "completed")
                .put("items", JSONArray().put(oldUser).put(oldAgent))
                .put("startedAt", TimeUnit.SECONDS.convert(System.currentTimeMillis() - 120_000, TimeUnit.MILLISECONDS))
                .put("completedAt", TimeUnit.SECONDS.convert(System.currentTimeMillis() - 119_000, TimeUnit.MILLISECONDS))
                .put("durationMs", 1000)
        }
    }
}
