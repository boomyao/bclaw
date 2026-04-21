package com.bclaw.app.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.bclaw.app.BuildConfig
import com.bclaw.app.data.ConnectionConfig
import com.bclaw.app.data.ConnectionConfigRepository
import com.bclaw.app.domain.ThreadSubscriptionRegistry
import com.bclaw.app.domain.model.ConnectionPhase
import com.bclaw.app.domain.state.BclawStateStore
import com.bclaw.app.domain.state.ThreadTimelineStore
import com.bclaw.app.domain.usecase.DiscoverWorkspacesUseCase
import com.bclaw.app.domain.usecase.HandleServerRequestUseCase
import com.bclaw.app.domain.usecase.HandleSessionUpdateUseCase
import com.bclaw.app.domain.usecase.InterruptTurnUseCase
import com.bclaw.app.domain.usecase.ListThreadsUseCase
import com.bclaw.app.domain.usecase.OpenThreadUseCase
import com.bclaw.app.domain.usecase.StartThreadUseCase
import com.bclaw.app.domain.usecase.StartTurnUseCase
import com.bclaw.app.net.JsonRpcException
import com.bclaw.app.net.JsonRpcSession
import com.bclaw.app.net.acp.AcpInitializeParams
import com.bclaw.app.net.acp.AcpClientInfo
import com.bclaw.app.net.acp.AcpAgentDiscovery
import com.bclaw.app.net.acp.AcpSessionLoadParams
import com.bclaw.app.domain.model.AgentSlot
import com.bclaw.app.net.codex.CodexJson
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class BclawSessionController(
    private val context: Context,
    private val configRepository: ConnectionConfigRepository,
    parentScope: CoroutineScope? = null,
) : JsonRpcSession.Delegate {
    private val json = CodexJson.json
    private val okHttpClient = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val scope = parentScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val session = JsonRpcSession(this, okHttpClient)
    private val store = BclawStateStore()
    private val timelineStore = ThreadTimelineStore(store)
    val uiState: StateFlow<com.bclaw.app.domain.model.BclawUiState> = store.uiState

    private val subscriptions = ThreadSubscriptionRegistry()
    private val listThreadsUseCase = ListThreadsUseCase(session, store)
    private val startThreadUseCase = StartThreadUseCase(session, store, configRepository, subscriptions)
    private val openThreadUseCase = OpenThreadUseCase(
        session = session,
        store = store,
        configRepository = configRepository,
        subscriptions = subscriptions,
    )
    private val startTurnUseCase = StartTurnUseCase(session, store, timelineStore)
    private val interruptTurnUseCase = InterruptTurnUseCase(session, store)
    private val handleSessionUpdate = HandleSessionUpdateUseCase(store, timelineStore)
    private val handleServerRequest = HandleServerRequestUseCase(store)
    private val discoverWorkspacesUseCase = DiscoverWorkspacesUseCase(session, store, configRepository)

    // Track synthetic turn IDs per session so session/update items attach to the right turn.
    private val syntheticTurnIds = ConcurrentHashMap<String, String>()

    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0
    private var shutdown = false
    private var suppressReconnectOnce = false
    private var lastConfig: ConnectionConfig? = null

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (store.snapshot.connectionPhase != ConnectionPhase.Connected) {
                scope.launch { ensureConnectedIfConfigured() }
            }
        }

        override fun onLost(network: Network) {
            if (!hasActiveNetwork()) {
                store.setConnectionPhase(ConnectionPhase.Offline, "Network unavailable")
            }
        }
    }

    init {
        scope.launch {
            configRepository.configFlow.collect { config ->
                lastConfig = config
                store.setWorkspaces(config.workspaces)
            }
        }
        runCatching {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
        }

        // Wire up the turn ID provider for session/update dispatching
        handleSessionUpdate.syntheticTurnIdProvider = { sessionId ->
            syntheticTurnIds[sessionId] ?: "turn:unknown"
        }

        // Wire up the StartTurnUseCase callbacks
        startTurnUseCase.onPromptStarted = { threadId, syntheticTurnId, _ ->
            syntheticTurnIds[threadId] = syntheticTurnId
        }
        startTurnUseCase.onPromptCompleted = { _, _, _ ->
            // Keep the turn ID around for any trailing session/update notifications
        }
    }

    fun bootstrap() { scope.launch { ensureConnectedIfConfigured() } }

    fun saveConfigAndConnect(config: ConnectionConfig) {
        scope.launch {
            val normalizedConfig = normalizeConfig(config)
            reconnectJob?.cancel()
            reconnectJob = null
            reconnectAttempt = 0
            configRepository.saveConfig(normalizedConfig)
            lastConfig = normalizedConfig
            store.setWorkspaces(normalizedConfig.workspaces)
            // Discover available agents from bridge
            val agents = AcpAgentDiscovery.fetchAgents(normalizedConfig.host)
            store.setAvailableAgents(agents.map { AgentSlot(name = it.name, displayName = it.displayName) })
            if (agents.isNotEmpty()) {
                // Auto-connect to first agent
                connectToAgent(normalizedConfig, agents.first().name)
            } else {
                // Fallback: connect directly (non-bridge mode)
                connectNow(normalizedConfig, reconnecting = false)
            }
        }
    }

    fun connectToAgent(agentName: String) {
        scope.launch {
            val config = lastConfig ?: configRepository.getSnapshot()
            lastConfig = config
            if (!config.isConfigured) return@launch
            connectToAgent(config, agentName)
        }
    }

    private suspend fun connectToAgent(config: ConnectionConfig, agentName: String) {
        session.close()
        store.setActiveAgent(agentName)
        // Clear old agent's state so drawer refreshes
        store.setWorkspaces(emptyList())
        store.clearAllThreadStates()
        val agentUrl = "${config.host.trimEnd('/')}/$agentName"
        val agentConfig = config.copy(host = agentUrl)
        connectNow(agentConfig, reconnecting = false)
    }

    fun requestReconnectNow() {
        scope.launch {
            val config = lastConfig ?: configRepository.getSnapshot()
            lastConfig = config
            if (!config.isConfigured || shutdown) return@launch
            if (store.snapshot.connectionPhase == ConnectionPhase.Connected ||
                store.snapshot.connectionPhase == ConnectionPhase.Connecting
            ) {
                return@launch
            }
            reconnectJob?.cancel()
            reconnectJob = null
            reconnectAttempt = 0
            connectNow(config, reconnecting = true)
        }
    }

    fun loadThreads(workspaceId: String, append: Boolean = false) {
        scope.launch {
            primeWorkspaceCache()
            listThreadsUseCase(workspaceId, append) { ensureConnectedIfConfigured() }
        }
    }

    fun startThread(workspaceId: String, onStarted: (String) -> Unit) {
        scope.launch {
            primeWorkspaceCache()
            startThreadUseCase(
                workspaceId,
                ensureConnected = { ensureConnectedIfConfigured() },
                onStarted = onStarted,
            )
        }
    }

    fun openThread(workspaceId: String, threadId: String) {
        scope.launch {
            primeWorkspaceCache()
            // Generate an initial synthetic turn ID for this session so session/update
            // notifications during history replay have a turn to attach to.
            if (!syntheticTurnIds.containsKey(threadId)) {
                syntheticTurnIds[threadId] = "turn:${UUID.randomUUID()}"
            }
            openThreadUseCase(workspaceId, threadId) { ensureConnectedIfConfigured() }
        }
    }

    fun rememberWorkspace(workspaceId: String) {
        scope.launch { configRepository.updateLastOpenedWorkspaceId(workspaceId) }
    }

    fun sendMessage(threadId: String, text: String) {
        scope.launch { startTurnUseCase(threadId, text) { ensureConnectedIfConfigured() } }
    }

    fun interruptTurn(threadId: String) { scope.launch { interruptTurnUseCase(threadId) } }

    fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempt = 0
        suppressReconnectOnce = true
        session.close(code = 1000, reason = "manual disconnect")
        store.setConnectionPhase(ConnectionPhase.Idle, "Disconnected")
    }

    suspend fun ensureConnectedIfConfigured() {
        val config = lastConfig ?: configRepository.getSnapshot()
        lastConfig = config
        if (!config.isConfigured || shutdown) {
            store.setConnectionPhase(ConnectionPhase.Idle, null)
            return
        }
        if (store.snapshot.connectionPhase == ConnectionPhase.Connected ||
            store.snapshot.connectionPhase == ConnectionPhase.Connecting
        ) {
            return
        }
        if (reconnectJob?.isActive == true) {
            reconnectJob?.cancel()
            reconnectJob = null
        }
        // Discover agents from bridge and connect to active (or first)
        val agents = AcpAgentDiscovery.fetchAgents(config.host)
        if (agents.isNotEmpty()) {
            store.setAvailableAgents(agents.map { AgentSlot(name = it.name, displayName = it.displayName) })
            val targetAgent = store.snapshot.activeAgentName ?: agents.first().name
            connectToAgent(config, targetAgent)
        } else {
            connectNow(config, reconnecting = false)
        }
    }

    fun shutdown() {
        shutdown = true
        reconnectJob?.cancel()
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        session.shutdown()
        okHttpClient.dispatcher.executorService.shutdown()
    }

    override fun onTransportOpening() {
        val reconnecting = store.snapshot.connectionPhase == ConnectionPhase.Connected ||
            store.snapshot.connectionPhase == ConnectionPhase.Reconnecting ||
            store.snapshot.connectionPhase == ConnectionPhase.Offline
        store.setConnectionPhase(
            phase = if (reconnecting) ConnectionPhase.Reconnecting else ConnectionPhase.Connecting,
            statusMessage = null,
        )
    }

    override fun onTransportReady() {
        reconnectAttempt = 0
        store.setConnectionPhase(ConnectionPhase.Connected, null)
        scope.launch { runCatching { discoverWorkspacesUseCase() } }
    }

    override fun onTransportClosed(code: Int, reason: String) {
        if (shutdown) return
        if (suppressReconnectOnce) {
            suppressReconnectOnce = false
            store.setConnectionPhase(ConnectionPhase.Idle, "Disconnected")
            return
        }
        store.setConnectionPhase(ConnectionPhase.Offline, "Connection closed: $reason")
        scheduleReconnect(null)
    }

    override fun onTransportFailure(throwable: Throwable) {
        if (shutdown) return
        if (suppressReconnectOnce) {
            suppressReconnectOnce = false
            store.setConnectionPhase(ConnectionPhase.Idle, "Disconnected")
            return
        }
        store.setConnectionPhase(ConnectionPhase.Offline, throwable.message ?: "Connection failed")
        scheduleReconnect(throwable)
    }

    override suspend fun onNotification(method: String, params: JsonObject) {
        when {
            handleSessionUpdate(method, params) -> Unit
        }
    }

    override suspend fun onServerRequest(id: JsonElement, method: String, params: JsonObject): JsonElement =
        handleServerRequest(method, params)

    private suspend fun connectNow(config: ConnectionConfig, reconnecting: Boolean) {
        if (shutdown) return
        runCatching {
            session.connect(
                url = config.host,
                token = config.token,
                initializeParams = AcpInitializeParams(
                    clientInfo = AcpClientInfo(
                        name = "bclaw",
                        title = "bclaw",
                        version = BuildConfig.VERSION_NAME,
                    ),
                ),
            )
        }.onSuccess {
            if (reconnecting) {
                restoreSubscriptions()
            }
            reconnectAttempt = 0
            reconnectJob = null
        }.onFailure { throwable ->
            if (throwable is JsonRpcException &&
                throwable.message.contains("Unauthorized", ignoreCase = true)
            ) {
                store.setConnectionPhase(ConnectionPhase.AuthFailed, throwable.message)
            } else {
                scheduleReconnect(throwable)
            }
        }
    }

    private fun scheduleReconnect(throwable: Throwable?) {
        if (shutdown) return
        val config = lastConfig
        if (config == null || !config.isConfigured) return
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            while (!shutdown) {
                reconnectAttempt += 1
                val baseDelayMs = min(30_000L, 1_500L * (1L shl min(reconnectAttempt - 1, 6)))
                val jitter = (baseDelayMs * 0.2f).toLong()
                val actualDelay = baseDelayMs + Random.nextLong(-jitter, jitter + 1)
                store.setConnectionPhase(ConnectionPhase.Reconnecting, throwable?.message ?: "Reconnecting…")
                delay(actualDelay)
                runCatching {
                    connectNow(config, reconnecting = true)
                }
                if (store.snapshot.connectionPhase == ConnectionPhase.Connected) {
                    return@launch
                }
            }
        }
    }

    private suspend fun restoreSubscriptions() {
        val targets = subscriptions.allKnown(store.snapshot.workspaces.mapNotNull { it.lastOpenedThreadId })
        targets.forEach { threadId ->
            runCatching {
                val cwd = store.threadState(threadId)?.thread?.cwd.orEmpty()
                session.requestRaw(
                    "session/load",
                    json.encodeToJsonElement(
                        AcpSessionLoadParams.serializer(),
                        AcpSessionLoadParams(
                            sessionId = threadId,
                            cwd = cwd,
                        ),
                    ),
                )
            }
        }
    }

    private fun hasActiveNetwork(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private suspend fun primeWorkspaceCache() {
        val config = lastConfig ?: configRepository.getSnapshot()
        lastConfig = config
        if (store.snapshot.workspaces != config.workspaces) {
            store.setWorkspaces(config.workspaces)
        }
    }

    private fun normalizeConfig(config: ConnectionConfig): ConnectionConfig {
        val fallbackWorkspaceId = config.workspaces.firstOrNull()?.id
        val workspaceId = config.lastOpenedWorkspaceId
            ?.takeIf { candidate -> config.workspaces.any { it.id == candidate } }
            ?: fallbackWorkspaceId
        return config.copy(lastOpenedWorkspaceId = workspaceId)
    }
}
