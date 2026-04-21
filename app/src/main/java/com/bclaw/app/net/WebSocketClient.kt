package com.bclaw.app.net

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class WebSocketClient(
    private val okHttpClient: OkHttpClient,
) {
    interface Listener {
        fun onOpen(webSocket: WebSocket, response: Response)
        fun onMessage(webSocket: WebSocket, text: String)
        fun onClosing(webSocket: WebSocket, code: Int, reason: String)
        fun onClosed(webSocket: WebSocket, code: Int, reason: String)
        fun onFailure(webSocket: WebSocket?, throwable: Throwable, response: Response?)
    }

    fun connect(
        url: String,
        bearerToken: String,
        listener: Listener,
    ): WebSocket {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $bearerToken")
            .build()

        return okHttpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    listener.onOpen(webSocket, response)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    listener.onMessage(webSocket, text)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    listener.onClosing(webSocket, code, reason)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    listener.onClosed(webSocket, code, reason)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    listener.onFailure(webSocket, t, response)
                }
            },
        )
    }
}
