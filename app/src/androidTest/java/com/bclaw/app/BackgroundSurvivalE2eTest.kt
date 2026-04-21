package com.bclaw.app

import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bclaw.app.domain.model.TimelineItemUi
import com.bclaw.app.service.BclawRuntime
import com.bclaw.app.testing.connectToServer
import com.bclaw.app.testing.createThreadInWorkspace
import com.bclaw.app.testing.ForegroundServiceTeardownRule
import com.bclaw.app.testing.NotificationPermissionRule
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackgroundSurvivalE2eTest {
    private val foregroundServiceTeardown = ForegroundServiceTeardownRule()
    private val notificationPermissionRule = NotificationPermissionRule(
        grantBeforeTest = true,
        resetPromptState = true,
    )
    private val composeRuleDelegate = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(foregroundServiceTeardown)
        .around(notificationPermissionRule)
        .around(composeRuleDelegate)

    private val composeRule
        get() = composeRuleDelegate

    private lateinit var server: MockWebServer
    private lateinit var scenario: BackgroundSurvivalScenario

    @Before
    fun setUp() {
        scenario = BackgroundSurvivalScenario()
        server = MockWebServer()
        server.enqueue(MockResponse().withWebSocketUpgrade(scenario))
        server.start()
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
            .recoverCatching { server.close() }
    }

    @Test
    fun long_running_turn_survives_background_and_renders_full_agent_message_on_return() {
        composeRule.connectToServer(
            host = server.url("/").toString().replaceFirst("http", "ws"),
            workspaceNamesAndCwds = listOf("Background Workspace" to WORKSPACE_CWD),
        )

        waitForCondition(timeoutMs = 30_000) {
            composeHasTag("chat_root")
        }

        composeRule.createThreadInWorkspace("workspace-0")
        waitForCondition(timeoutMs = 15_000) {
            composeHasText("Background thread")
        }

        composeRule.onNodeWithTag("chat_input").performTextInput("Keep running while backgrounded")
        composeRule.onNodeWithTag("send_button").performClick()

        waitForCondition(timeoutMs = 15_000) {
            (currentAgentText(THREAD_ID)?.length ?: 0) >= scenario.firstVisibleThreshold()
        }
        val beforeBackgroundLength = currentAgentText(THREAD_ID)?.length ?: 0

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)

        waitForCondition(timeoutMs = 15_000) {
            val currentLength = currentAgentText(THREAD_ID)?.length ?: 0
            currentLength > beforeBackgroundLength + 24
        }
        waitForCondition(timeoutMs = 120_000) {
            currentThreadState(THREAD_ID)?.latestTurnStatus == "completed"
        }

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("running_turn_strip").fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithTag("send_button").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithTag("stop_button").fetchSemanticsNodes().isEmpty()
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Background thread").fetchSemanticsNodes().isNotEmpty()
        }

        val expected = scenario.expectedAgentText()
        val rendered = composeRule.onNodeWithTag(
            "agent_message_$AGENT_ITEM_ID",
            useUnmergedTree = true,
        ).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }

        assertEquals(expected, currentAgentText(THREAD_ID))
        assertEquals(expected, rendered)
    }

    private fun currentThreadState(threadId: String) =
        BclawRuntime.controller.value?.uiState?.value?.threadStates?.get(threadId)

    private fun currentAgentText(threadId: String): String? {
        return currentThreadState(threadId)
            ?.items
            ?.filterIsInstance<TimelineItemUi.AgentMessage>()
            ?.lastOrNull()
            ?.text
    }

    private fun waitForCondition(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(200)
        }
        throw AssertionError("Condition not satisfied within ${timeoutMs}ms")
    }

    private fun composeHasTag(tag: String): Boolean {
        return runCatching {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }.getOrDefault(false)
    }

    private fun composeHasText(text: String): Boolean {
        return runCatching {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }.getOrDefault(false)
    }

    private class BackgroundSurvivalScenario : WebSocketListener() {
        @Volatile
        private var activeTurn = false

        private val chunks = (1..90).map { index ->
            "chunk-%02d ".format(index)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val message = JSONObject(text)
            Log.d("BackgroundSurvivalScenario", "client -> server ${message.optString("method")}: $text")
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

                "thread/list" -> {
                    val data = if (threadExists()) {
                        JSONArray().put(threadSummary(active = activeTurn))
                    } else {
                        JSONArray()
                    }
                    reply(
                        webSocket = webSocket,
                        id = message.get("id"),
                        result = JSONObject()
                            .put("data", data)
                            .put("nextCursor", JSONObject.NULL),
                    )
                }

                "thread/start" -> {
                    reply(
                        webSocket = webSocket,
                        id = message.get("id"),
                        result = JSONObject().put("thread", threadSummary(active = false)),
                    )
                }

                "thread/resume" -> {
                    reply(
                        webSocket = webSocket,
                        id = message.get("id"),
                        result = JSONObject()
                            .put("thread", threadSummary(active = activeTurn))
                            .put("cwd", WORKSPACE_CWD)
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
                            .put("thread", threadSummary(active = activeTurn).put("turns", JSONArray())),
                    )
                }

                "turn/start" -> {
                    activeTurn = true
                    reply(
                        webSocket = webSocket,
                        id = message.get("id"),
                        result = JSONObject()
                            .put(
                                "turn",
                                JSONObject()
                                    .put("id", TURN_ID)
                                    .put("status", "inProgress")
                                    .put("items", JSONArray()),
                            ),
                    )
                    streamLongTurn(webSocket)
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

        fun expectedAgentText(): String = chunks.joinToString(separator = "")

        fun firstVisibleThreshold(): Int = chunks.take(2).sumOf { it.length }

        private fun streamLongTurn(webSocket: WebSocket) {
            Thread {
                notify(
                    webSocket = webSocket,
                    method = "turn/started",
                    params = JSONObject()
                        .put("threadId", THREAD_ID)
                        .put(
                            "turn",
                            JSONObject()
                                .put("id", TURN_ID)
                                .put("status", "inProgress")
                                .put("items", JSONArray()),
                        ),
                )

                notify(
                    webSocket = webSocket,
                    method = "item/started",
                    params = JSONObject()
                        .put("threadId", THREAD_ID)
                        .put("turnId", TURN_ID)
                        .put(
                            "item",
                            JSONObject()
                                .put("id", USER_ITEM_ID)
                                .put("type", "userMessage")
                                .put(
                                    "content",
                                    JSONArray().put(
                                        JSONObject()
                                            .put("type", "text")
                                            .put("text", "Keep running while backgrounded"),
                                    ),
                                ),
                        ),
                )
                notify(
                    webSocket = webSocket,
                    method = "item/completed",
                    params = JSONObject()
                        .put("threadId", THREAD_ID)
                        .put("turnId", TURN_ID)
                        .put(
                            "item",
                            JSONObject()
                                .put("id", USER_ITEM_ID)
                                .put("type", "userMessage")
                                .put(
                                    "content",
                                    JSONArray().put(
                                        JSONObject()
                                            .put("type", "text")
                                            .put("text", "Keep running while backgrounded"),
                                    ),
                                ),
                        ),
                )
                notify(
                    webSocket = webSocket,
                    method = "item/started",
                    params = JSONObject()
                        .put("threadId", THREAD_ID)
                        .put("turnId", TURN_ID)
                        .put(
                            "item",
                            JSONObject()
                                .put("id", AGENT_ITEM_ID)
                                .put("type", "agentMessage")
                                .put("text", ""),
                        ),
                )

                val builder = StringBuilder()
                chunks.forEach { chunk ->
                    Thread.sleep(1_000)
                    builder.append(chunk)
                    notify(
                        webSocket = webSocket,
                        method = "item/agentMessage/delta",
                        params = JSONObject()
                            .put("threadId", THREAD_ID)
                            .put("turnId", TURN_ID)
                            .put("itemId", AGENT_ITEM_ID)
                            .put("delta", chunk),
                    )
                }

                notify(
                    webSocket = webSocket,
                    method = "item/completed",
                    params = JSONObject()
                        .put("threadId", THREAD_ID)
                        .put("turnId", TURN_ID)
                        .put(
                            "item",
                            JSONObject()
                                .put("id", AGENT_ITEM_ID)
                                .put("type", "agentMessage")
                                .put("text", builder.toString()),
                        ),
                )

                activeTurn = false
                notify(
                    webSocket = webSocket,
                    method = "turn/completed",
                    params = JSONObject()
                        .put("threadId", THREAD_ID)
                        .put(
                            "turn",
                            JSONObject()
                                .put("id", TURN_ID)
                                .put("status", "completed")
                                .put(
                                    "items",
                                    JSONArray()
                                        .put(
                                            JSONObject()
                                                .put("id", USER_ITEM_ID)
                                                .put("type", "userMessage")
                                                .put(
                                                    "content",
                                                    JSONArray().put(
                                                        JSONObject()
                                                            .put("type", "text")
                                                            .put("text", "Keep running while backgrounded"),
                                                    ),
                                                ),
                                        )
                                        .put(
                                            JSONObject()
                                                .put("id", AGENT_ITEM_ID)
                                                .put("type", "agentMessage")
                                                .put("text", builder.toString()),
                                        ),
                                )
                                .put("completedAt", System.currentTimeMillis() / 1000),
                        ),
                )
            }.start()
        }

        private fun threadExists(): Boolean = true

        private fun threadSummary(active: Boolean): JSONObject {
            return JSONObject()
                .put("id", THREAD_ID)
                .put("preview", "Background thread")
                .put("modelProvider", "openai")
                .put("createdAt", 1_776_255_048L)
                .put("updatedAt", 1_776_255_108L)
                .put(
                    "status",
                    JSONObject()
                        .put("type", if (active) "active" else "idle")
                        .put("activeFlags", JSONArray()),
                )
                .put("cwd", WORKSPACE_CWD)
                .put("cliVersion", "0.120.0")
                .put("name", "Background thread")
                .put("turns", JSONArray())
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
            val payload = JSONObject()
                .put("method", method)
                .put("params", params)
                .toString()
            Log.d("BackgroundSurvivalScenario", "server -> client notification $method: $payload")
            webSocket.send(payload)
        }
    }

    companion object {
        private const val WORKSPACE_CWD = "/Users/test/background"
        private const val THREAD_ID = "thr-background"
        private const val TURN_ID = "turn-background"
        private const val USER_ITEM_ID = "item-user-background"
        private const val AGENT_ITEM_ID = "item-agent-background"
    }
}
