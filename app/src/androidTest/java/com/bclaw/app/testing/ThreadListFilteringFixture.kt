package com.bclaw.app.testing

import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

data class ThreadListFixture(
    val id: String,
    val cwd: String,
    val name: String,
    val preview: String,
    val createdAt: Long = 1_776_255_000L,
    val updatedAt: Long = 1_776_255_600L,
)

class ThreadListFilteringFixture(
    private val threads: List<ThreadListFixture>,
) : WebSocketListener() {
    val threadListRequests: List<JSONObject>
        get() = recordedThreadListRequests

    private val recordedThreadListRequests = CopyOnWriteArrayList<JSONObject>()

    override fun onMessage(webSocket: WebSocket, text: String) {
        val message = JSONObject(text)
        Log.d("ThreadListFilteringFixture", "client -> server ${message.optString("method")}: $text")
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
                val params = message.getJSONObject("params")
                recordedThreadListRequests += JSONObject(params.toString())
                val cwd = params.optString("cwd")
                val filtered = threads.filter { thread ->
                    cwd.isBlank() || thread.cwd == cwd
                }
                reply(
                    webSocket = webSocket,
                    id = message.get("id"),
                    result = JSONObject()
                        .put(
                            "data",
                            JSONArray(
                                filtered.map { thread ->
                                    threadSummary(thread)
                                },
                            ),
                        )
                        .put("nextCursor", JSONObject.NULL),
                )
            }

            "thread/resume" -> {
                val threadId = message.getJSONObject("params").getString("threadId")
                val thread = threads.first { it.id == threadId }
                reply(
                    webSocket = webSocket,
                    id = message.get("id"),
                    result = JSONObject()
                        .put("thread", threadSummary(thread))
                        .put("cwd", thread.cwd)
                        .put("approvalPolicy", "never")
                        .put("approvalsReviewer", "user")
                        .put("model", "gpt-5.4-mini")
                        .put("modelProvider", "openai")
                        .put("sandbox", JSONObject().put("type", "workspaceWrite")),
                )
            }

            "thread/read" -> {
                val threadId = message.getJSONObject("params").getString("threadId")
                val thread = threads.first { it.id == threadId }
                reply(
                    webSocket = webSocket,
                    id = message.get("id"),
                    result = JSONObject()
                        .put(
                            "thread",
                            threadSummary(thread).put("turns", JSONArray()),
                        ),
                )
            }
        }
    }

    private fun threadSummary(thread: ThreadListFixture): JSONObject {
        return JSONObject()
            .put("id", thread.id)
            .put("name", thread.name)
            .put("preview", thread.preview)
            .put("modelProvider", "openai")
            .put("createdAt", thread.createdAt)
            .put("updatedAt", thread.updatedAt)
            .put(
                "status",
                JSONObject()
                    .put("type", "idle")
                    .put("activeFlags", JSONArray()),
            )
            .put("cwd", thread.cwd)
            .put("cliVersion", "0.120.0")
            .put("turns", JSONArray())
            .put("source", "appServer")
            .put("ephemeral", false)
    }

    private fun reply(webSocket: WebSocket, id: Any, result: JSONObject) {
        val payload = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", id)
            .put("result", result)
        webSocket.send(payload.toString())
    }
}
