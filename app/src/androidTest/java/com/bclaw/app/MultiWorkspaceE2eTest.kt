package com.bclaw.app

import android.util.Log
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bclaw.app.domain.model.TimelineItemUi
import com.bclaw.app.service.BclawRuntime
import com.bclaw.app.testing.connectToServer
import com.bclaw.app.testing.createThreadInWorkspace
import com.bclaw.app.testing.ForegroundServiceTeardownRule
import com.bclaw.app.testing.NotificationPermissionRule
import com.bclaw.app.testing.openDrawer
import com.bclaw.app.testing.scrollOpenDrawerTo
import com.bclaw.app.testing.selectThread
import com.bclaw.app.testing.taggedNodeContainsText
import java.util.concurrent.ConcurrentHashMap
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MultiWorkspaceE2eTest {
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
        server.enqueue(MockResponse().withWebSocketUpgrade(MultiWorkspaceScenario()))
        server.start()
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
            .recoverCatching { server.close() }
    }

    @Test
    fun multi_workspace_turns_update_independent_drawer_presence_and_chat_routes() {
        composeRule.connectToServer(
            host = server.url("/").toString().replaceFirst("http", "ws"),
            workspaceNamesAndCwds = listOf(
                "Workspace A" to "/Users/test/a",
                "Workspace B" to "/Users/test/b",
            ),
        )

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("chat_root").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.createThreadInWorkspace("workspace-0")
        composeRule.waitUntil(10_000) {
            currentThreadState("thr-alpha")?.thread != null &&
                composeRule.onAllNodesWithText("Alpha thread").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("chat_input").performTextInput("Run alpha task")
        composeRule.onNodeWithTag("send_button").performClick()

        composeRule.waitUntil(10_000) {
            currentThreadState("thr-alpha")
                ?.items
                ?.any { item ->
                    item is TimelineItemUi.AgentMessage && item.text.contains("Alpha:")
                } == true
        }

        composeRule.createThreadInWorkspace("workspace-1")
        composeRule.waitUntil(10_000) {
            currentThreadState("thr-beta")?.thread != null &&
                composeRule.onAllNodesWithText("Beta thread").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("chat_input").performTextInput("Run beta task")
        composeRule.onNodeWithTag("send_button").performClick()

        composeRule.openDrawer()
        composeRule.waitUntil(10_000) {
            composeRule.scrollOpenDrawerTo("workspace_row_workspace-0")
            composeRule.onAllNodesWithTag(
                "workspace_running_workspace-0",
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(10_000) {
            composeRule.scrollOpenDrawerTo("workspace_row_workspace-1")
            composeRule.onAllNodesWithTag(
                "workspace_running_workspace-1",
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(10_000) {
            composeRule.scrollOpenDrawerTo("workspace_row_workspace-0")
            composeRule.taggedNodeContainsText("workspace_preview_workspace-0", "Alpha")
        }
        composeRule.waitUntil(10_000) {
            composeRule.scrollOpenDrawerTo("workspace_row_workspace-1")
            composeRule.taggedNodeContainsText("workspace_preview_workspace-1", "Beta")
        }

        composeRule.scrollOpenDrawerTo("thread_row_thr-alpha")
        composeRule.onNodeWithTag("thread_row_thr-alpha").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("drawer_panel_open").fetchSemanticsNodes().isEmpty()
        }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Alpha thread").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.openDrawer()
        composeRule.waitUntil(10_000) {
            composeRule.scrollOpenDrawerTo("workspace_row_workspace-1")
            composeRule.taggedNodeContainsText("workspace_preview_workspace-1", "Beta")
        }
        composeRule.scrollOpenDrawerTo("thread_row_thr-alpha")
        composeRule.onNodeWithTag("thread_row_thr-alpha").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("drawer_panel_open").fetchSemanticsNodes().isEmpty()
        }
        composeRule.waitUntil(15_000) {
            currentThreadState("thr-beta")?.latestTurnStatus == "completed"
        }
        assertEquals(
            0,
            composeRule.onAllNodesWithText("Beta final reply").fetchSemanticsNodes().size,
        )

        composeRule.waitUntil(15_000) {
            currentThreadState("thr-alpha")?.latestTurnStatus == "completed" &&
                currentThreadState("thr-beta")?.latestTurnStatus == "completed"
        }

        composeRule.selectThread("thr-alpha")
        composeRule.onNodeWithTag("chat_timeline")
            .performScrollToNode(hasTestTag("agent_message_item-agent-thr-alpha"))
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Alpha final reply").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.selectThread("thr-beta")
        composeRule.onNodeWithTag("chat_timeline")
            .performScrollToNode(hasTestTag("agent_message_item-agent-thr-beta"))
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Beta final reply").fetchSemanticsNodes().isNotEmpty()
        }

        assertEquals("completed", currentThreadState("thr-alpha")?.latestTurnStatus)
        assertEquals("completed", currentThreadState("thr-beta")?.latestTurnStatus)
    }

    private fun currentThreadState(threadId: String) =
        BclawRuntime.controller.value?.uiState?.value?.threadStates?.get(threadId)

    private class MultiWorkspaceScenario : WebSocketListener() {
        private val workspaceThreads = ConcurrentHashMap<String, MutableList<String>>(
            mapOf(
                WORKSPACE_A_CWD to mutableListOf(),
                WORKSPACE_B_CWD to mutableListOf(),
            ),
        )
        private val activeTurns = ConcurrentHashMap<String, Boolean>()
        private val completedTurns = ConcurrentHashMap<String, JSONObject>()
        private val streamedAlpha = AtomicBoolean(false)
        private val streamedBeta = AtomicBoolean(false)

        override fun onMessage(webSocket: WebSocket, text: String) {
            val message = JSONObject(text)
            Log.d("MultiWorkspaceScenario", "client -> server ${message.optString("method")}: $text")
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
                    val cwd = message.getJSONObject("params").getString("cwd")
                    val threads = workspaceThreads.getValue(cwd).map { threadId ->
                        threadSummary(threadId = threadId, cwd = cwd, active = activeTurns[threadId] == true)
                    }
                    reply(
                        webSocket = webSocket,
                        id = message.get("id"),
                        result = JSONObject()
                            .put("data", JSONArray(threads))
                            .put("nextCursor", JSONObject.NULL),
                    )
                }

                "thread/start" -> {
                    val cwd = message.getJSONObject("params").getString("cwd")
                    val threadId = if (cwd == WORKSPACE_A_CWD) "thr-alpha" else "thr-beta"
                    workspaceThreads.getValue(cwd).apply {
                        if (!contains(threadId)) add(0, threadId)
                    }
                    reply(
                        webSocket = webSocket,
                        id = message.get("id"),
                        result = JSONObject()
                            .put("thread", threadSummary(threadId = threadId, cwd = cwd, active = false)),
                    )
                }

                "thread/resume" -> {
                    val threadId = message.getJSONObject("params").getString("threadId")
                    val cwd = cwdForThread(threadId)
                    reply(
                        webSocket = webSocket,
                        id = message.get("id"),
                        result = JSONObject()
                            .put("thread", threadSummary(threadId = threadId, cwd = cwd, active = activeTurns[threadId] == true))
                            .put("cwd", cwd)
                            .put("approvalPolicy", "never")
                            .put("approvalsReviewer", "user")
                            .put("model", "gpt-5.4-mini")
                            .put("modelProvider", "openai")
                            .put("sandbox", JSONObject().put("type", "workspaceWrite")),
                    )
                }

                "thread/read" -> {
                    val threadId = message.getJSONObject("params").getString("threadId")
                    val cwd = cwdForThread(threadId)
                    val turns = completedTurns[threadId]?.let { JSONArray().put(it) } ?: JSONArray()
                    reply(
                        webSocket = webSocket,
                        id = message.get("id"),
                        result = JSONObject()
                            .put(
                                "thread",
                                threadSummary(threadId = threadId, cwd = cwd, active = activeTurns[threadId] == true)
                                    .put("turns", turns),
                            ),
                    )
                }

                "turn/start" -> {
                    val params = message.getJSONObject("params")
                    val threadId = params.getString("threadId")
                    val userText = params.getJSONArray("input").getJSONObject(0).getString("text")
                    val turnId = "turn-$threadId"
                    activeTurns[threadId] = true
                    reply(
                        webSocket = webSocket,
                        id = message.get("id"),
                        result = JSONObject()
                            .put(
                                "turn",
                                JSONObject()
                                    .put("id", turnId)
                                    .put("status", "inProgress")
                                    .put("items", JSONArray()),
                            ),
                    )
                    if (threadId == "thr-alpha" && streamedAlpha.compareAndSet(false, true)) {
                        streamThread(
                            webSocket = webSocket,
                            threadId = threadId,
                            turnId = turnId,
                            userText = userText,
                            partialOne = "Alpha: indexing",
                            partialTwo = " and checking",
                            finalText = "Alpha final reply",
                            finalDelayMs = 7_000L,
                        )
                    } else if (threadId == "thr-beta" && streamedBeta.compareAndSet(false, true)) {
                        streamThread(
                            webSocket = webSocket,
                            threadId = threadId,
                            turnId = turnId,
                            userText = userText,
                            partialOne = "Beta: compiling",
                            partialTwo = " and testing",
                            finalText = "Beta final reply",
                            finalDelayMs = 5_000L,
                        )
                    }
                }
            }
        }

        private fun streamThread(
            webSocket: WebSocket,
            threadId: String,
            turnId: String,
            userText: String,
            partialOne: String,
            partialTwo: String,
            finalText: String,
            finalDelayMs: Long,
        ) {
            Thread {
                Thread.sleep(150)
                notify(
                    webSocket,
                    "turn/started",
                    JSONObject()
                        .put("threadId", threadId)
                        .put(
                            "turn",
                            JSONObject()
                                .put("id", turnId)
                                .put("status", "inProgress")
                                .put("items", JSONArray()),
                        ),
                )
                val userItem = userMessageItem("item-user-$threadId", userText)
                notify(
                    webSocket,
                    "item/started",
                    JSONObject()
                        .put("threadId", threadId)
                        .put("turnId", turnId)
                        .put("item", userItem),
                )
                notify(
                    webSocket,
                    "item/completed",
                    JSONObject()
                        .put("threadId", threadId)
                        .put("turnId", turnId)
                        .put("item", userItem),
                )
                notify(
                    webSocket,
                    "item/started",
                    JSONObject()
                        .put("threadId", threadId)
                        .put("turnId", turnId)
                        .put(
                            "item",
                            JSONObject()
                                .put("id", "item-agent-$threadId")
                                .put("type", "agentMessage")
                                .put("text", ""),
                        ),
                )
                Thread.sleep(220)
                notify(
                    webSocket,
                    "item/agentMessage/delta",
                    JSONObject()
                        .put("threadId", threadId)
                        .put("turnId", turnId)
                        .put("itemId", "item-agent-$threadId")
                        .put("delta", partialOne),
                )
                Thread.sleep(finalDelayMs / 2)
                notify(
                    webSocket,
                    "item/agentMessage/delta",
                    JSONObject()
                        .put("threadId", threadId)
                        .put("turnId", turnId)
                        .put("itemId", "item-agent-$threadId")
                        .put("delta", partialTwo),
                )
                Thread.sleep(finalDelayMs / 2)
                val finalAgentItem = JSONObject()
                    .put("id", "item-agent-$threadId")
                    .put("type", "agentMessage")
                    .put("text", finalText)
                completedTurns[threadId] = JSONObject()
                    .put("id", turnId)
                    .put("status", "completed")
                    .put("items", JSONArray().put(userItem).put(finalAgentItem))
                activeTurns[threadId] = false
                notify(
                    webSocket,
                    "item/completed",
                    JSONObject()
                        .put("threadId", threadId)
                        .put("turnId", turnId)
                        .put("item", finalAgentItem),
                )
                notify(
                    webSocket,
                    "turn/completed",
                    JSONObject()
                        .put("threadId", threadId)
                        .put("turn", completedTurns.getValue(threadId)),
                )
            }.start()
        }

        private fun cwdForThread(threadId: String): String {
            return when (threadId) {
                "thr-alpha" -> WORKSPACE_A_CWD
                else -> WORKSPACE_B_CWD
            }
        }

        private companion object {
            private const val WORKSPACE_A_CWD = "/Users/test/a"
            private const val WORKSPACE_B_CWD = "/Users/test/b"

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

            fun threadSummary(
                threadId: String,
                cwd: String,
                active: Boolean,
            ): JSONObject {
                return JSONObject()
                    .put("id", threadId)
                    .put("name", if (threadId == "thr-alpha") "Alpha thread" else "Beta thread")
                    .put("preview", if (threadId == "thr-alpha") "Alpha preview" else "Beta preview")
                    .put("modelProvider", "openai")
                    .put("createdAt", 1_710_000_000L)
                    .put("updatedAt", 1_710_000_600L)
                    .put(
                        "status",
                        JSONObject()
                            .put("type", if (active) "active" else "idle")
                            .put("activeFlags", JSONArray()),
                    )
                    .put("cwd", cwd)
                    .put("cliVersion", "0.120.0")
                    .put("path", "/tmp/$threadId.json")
                    .put("source", "appServer")
                    .put("ephemeral", false)
            }
        }
    }
}
