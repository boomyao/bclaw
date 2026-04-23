package com.bclaw.app.net.acp

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient

/**
 * Process-wide signal for "is the bridge HTTP endpoint actually responding?"
 *
 * Discovery calls (`/agents`, `/projects`, `/sessions`) used to swallow connect/timeout
 * exceptions and return empty lists, which made a dead bridge indistinguishable from
 * "bridge is up but has nothing to report". The connection status bar only flipped to
 * Offline when an *agent's WebSocket* failed — and the WebSocket isn't even attempted
 * until the user opens a session tab. Result: bridge dead + user on Home → silent empty
 * lists, no offline indicator.
 *
 * The shared [client] below routes every discovery HTTP call through an interceptor that
 * flips this flag on success / IOException, so the controller can roll it into bridgePhase.
 *
 * `null` = unknown (no probe attempted yet); meaningful first value lands on the first
 * discovery call after pair / app start.
 */
object BridgeReachability {
    private val _reachable = MutableStateFlow<Boolean?>(null)
    val reachable: StateFlow<Boolean?> = _reachable.asStateFlow()

    fun markReachable() { _reachable.value = true }
    fun markUnreachable() { _reachable.value = false }
    fun reset() { _reachable.value = null }

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            try {
                val response = chain.proceed(chain.request())
                markReachable()
                response
            } catch (e: IOException) {
                markUnreachable()
                throw e
            }
        }
        .build()
}
