package com.bclaw.app

import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bclaw.app.testing.containsTextAnywhere
import com.bclaw.app.testing.ForegroundServiceTeardownRule
import com.bclaw.app.testing.NotificationPermissionRule
import com.bclaw.app.testing.connectToServer
import com.bclaw.app.testing.selectWorkspace
import java.util.concurrent.atomic.AtomicBoolean
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
class InterruptE2eTest {
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
            MockResponse().withWebSocketUpgrade(InterruptScenario()),
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
    fun interrupt_running_turn_marks_ui_interrupted_within_two_seconds() {
        val host = server.url("/").toString().replaceFirst("http", "ws")

        composeRule.connectToServer(
            host = host,
            workspaceNamesAndCwds = listOf("Interrupt Workspace" to "/Users/test/demo"),
        )

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("chat_root").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.selectWorkspace("workspace-0")

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Interrupt thread").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(10_000) {
            composeRule.containsTextAnywhere("Existing baseline reply")
        }

        composeRule.onNodeWithTag("chat_input").performTextInput("Please stop after partial output")
        composeRule.onNodeWithTag("send_button").performClick()

        composeRule.waitUntil(10_000) {
            composeRule.containsTextAnywhere("Streaming partial agent output")
        }
        composeRule.onNodeWithTag("stop_button").performClick()

        composeRule.waitUntil(2_000) {
            composeRule.onAllNodesWithText("Turn: Interrupted").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private class InterruptScenario : WebSocketListener() {
        private val interrupted = AtomicBoolean(false)

        override fun onMessage(webSocket: WebSocket, text: String) {
            val message = JSONObject(text)
            Log.d("InterruptScenario", "client -> server ${message.optString("method")}: $text")
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
                                    .put("id", "turn-interrupt")
                                    .put("status", "inProgress")
                                    .put("items", JSONArray()),
                            ),
                    )
                    streamPartialConversation(webSocket)
                }

                "turn/interrupt" -> {
                    interrupted.set(true)
                    reply(
                        webSocket = webSocket,
                        id = message.get("id"),
                        result = JSONObject(),
                    )
                    finishInterruptedTurn(webSocket)
                }
            }
        }

        private fun streamPartialConversation(webSocket: WebSocket) {
            Thread {
                Thread.sleep(150)
                notify(
                    webSocket,
                    "turn/started",
                    JSONObject()
                        .put("threadId", "thr-interrupt")
                        .put(
                            "turn",
                            JSONObject()
                                .put("id", "turn-interrupt")
                                .put("status", "inProgress")
                                .put("items", JSONArray()),
                        ),
                )

                val liveUserItem = JSONObject()
                    .put("id", "item-user-interrupt")
                    .put("type", "userMessage")
                    .put(
                        "content",
                        JSONArray().put(
                            JSONObject()
                                .put("type", "text")
                                .put("text", "Please stop after partial output"),
                        ),
                    )

                notify(
                    webSocket,
                    "item/started",
                    JSONObject()
                        .put("threadId", "thr-interrupt")
                        .put("turnId", "turn-interrupt")
                        .put("item", liveUserItem),
                )
                notify(
                    webSocket,
                    "item/completed",
                    JSONObject()
                        .put("threadId", "thr-interrupt")
                        .put("turnId", "turn-interrupt")
                        .put("item", liveUserItem),
                )

                val agentStarted = JSONObject()
                    .put("id", "item-agent-interrupt")
                    .put("type", "agentMessage")
                    .put("text", "")

                notify(
                    webSocket,
                    "item/started",
                    JSONObject()
                        .put("threadId", "thr-interrupt")
                        .put("turnId", "turn-interrupt")
                        .put("item", agentStarted),
                )
                Thread.sleep(120)
                notify(
                    webSocket,
                    "item/agentMessage/delta",
                    JSONObject()
                        .put("threadId", "thr-interrupt")
                        .put("turnId", "turn-interrupt")
                        .put("itemId", "item-agent-interrupt")
                        .put("delta", "Streaming partial agent output"),
                )
            }.start()
        }

        private fun finishInterruptedTurn(webSocket: WebSocket) {
            Thread {
                Thread.sleep(150)
                if (!interrupted.get()) return@Thread
                val finalAgentItem = JSONObject()
                    .put("id", "item-agent-interrupt")
                    .put("type", "agentMessage")
                    .put("text", "Streaming partial agent output")

                notify(
                    webSocket,
                    "item/completed",
                    JSONObject()
                        .put("threadId", "thr-interrupt")
                        .put("turnId", "turn-interrupt")
                        .put("item", finalAgentItem),
                )
                notify(
                    webSocket,
                    "turn/completed",
                    JSONObject()
                        .put("threadId", "thr-interrupt")
                        .put(
                            "turn",
                            JSONObject()
                                .put("id", "turn-interrupt")
                                .put("status", "interrupted")
                                .put(
                                    "items",
                                    JSONArray().put(
                                        JSONObject()
                                            .put("id", "item-user-interrupt")
                                            .put("type", "userMessage")
                                            .put(
                                                "content",
                                                JSONArray().put(
                                                    JSONObject()
                                                        .put("type", "text")
                                                        .put("text", "Please stop after partial output"),
                                                ),
                                            ),
                                    ).put(finalAgentItem),
                                ),
                        ),
                )
            }.start()
        }

        private fun historyTurn(): JSONObject {
            return JSONObject()
                .put("id", "turn-history")
                .put("status", "completed")
                .put(
                    "items",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("id", "item-user-history")
                                .put("type", "userMessage")
                                .put(
                                    "content",
                                    JSONArray().put(
                                        JSONObject()
                                            .put("type", "text")
                                            .put("text", "Historical prompt"),
                                    ),
                                ),
                        )
                        .put(
                            JSONObject()
                                .put("id", "item-agent-history")
                                .put("type", "agentMessage")
                                .put("text", "Existing baseline reply"),
                        ),
                )
        }

        private fun threadSummary(): JSONObject {
            return JSONObject()
                .put("id", "thr-interrupt")
                .put("name", "Interrupt thread")
                .put("preview", "Interrupt preview")
                .put("modelProvider", "openai")
                .put("createdAt", 1_710_000_000L)
                .put("updatedAt", 1_710_000_600L)
                .put("status", JSONObject().put("type", "idle").put("activeFlags", JSONArray()))
                .put("cwd", "/Users/test/demo")
                .put("cliVersion", "0.120.0")
                .put("path", "/tmp/thr-interrupt.json")
                .put("source", "appServer")
                .put("ephemeral", false)
        }

        private fun reply(
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

        private fun notify(
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

        override fun onOpen(webSocket: WebSocket, response: Response) = Unit
    }
}
