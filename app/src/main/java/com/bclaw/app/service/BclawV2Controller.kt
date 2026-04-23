package com.bclaw.app.service

import com.bclaw.app.data.BclawV2UrlParser
import com.bclaw.app.data.DeviceBookRepository
import com.bclaw.app.data.TabBookRepository
import com.bclaw.app.data.TimelineCacheRepository
import com.bclaw.app.domain.v2.AgentId
import com.bclaw.app.domain.v2.BclawV2UrlParseResult
import com.bclaw.app.domain.v2.CwdPath
import com.bclaw.app.domain.v2.Device
import com.bclaw.app.domain.v2.DeviceBook
import com.bclaw.app.domain.v2.DeviceId
import com.bclaw.app.domain.v2.SessionId
import com.bclaw.app.domain.v2.TabBook
import com.bclaw.app.domain.v2.TabId
import com.bclaw.app.domain.v2.TabState
import com.bclaw.app.domain.v2.TimelineItem
import com.bclaw.app.domain.v2.AgentDescriptor
import com.bclaw.app.domain.v2.SessionRef
import com.bclaw.app.net.BclawJson
import com.bclaw.app.net.JsonRpcSession
import com.bclaw.app.net.acp.AcpAgentDiscovery
import com.bclaw.app.net.acp.AcpContentBlock
import com.bclaw.app.net.acp.AcpProjectDiscovery
import com.bclaw.app.net.acp.AcpSessionDiscovery
import com.bclaw.app.net.acp.BridgeReachability
import com.bclaw.app.net.acp.AcpSessionLoadParams
import com.bclaw.app.net.acp.AcpInitializeParams
import com.bclaw.app.net.acp.AcpInitializeResult
import com.bclaw.app.net.acp.AcpPromptParams
import com.bclaw.app.net.acp.AcpPromptResult
import com.bclaw.app.net.acp.AcpSessionCancelParams
import com.bclaw.app.net.acp.AcpSessionNewParams
import com.bclaw.app.net.acp.AcpSessionNewResult
import com.bclaw.app.net.acp.AcpTimelineReducer
import com.bclaw.app.net.acp.AcpTransport
import com.bclaw.app.net.acp.AcpTransportFactory
import com.bclaw.app.net.acp.DefaultAcpTransportFactory
import com.bclaw.app.net.acp.request
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * v2 controller · Batch 1c cut.
 *
 * Manages:
 *   - Paired devices + active device via [DeviceBookRepository]
 *   - Per-device tab set via [TabBookRepository]
 *   - **Per-agent** [MockAcpTransport] connections (one WebSocket per agent, shared across
 *     tabs on the same agent). Switching device tears down everything.
 *   - **Per-tab** timeline of [TimelineItem] built from ACP `session/update` notifications.
 *
 * Real [com.bclaw.app.net.JsonRpcSession] isn't wired yet — transport is always a mock in
 * v2.0. Swapping in real WebSocket is a one-factory-call change in [ensureTransport].
 */
class BclawV2Controller(
    private val deviceBookRepository: DeviceBookRepository,
    private val tabBookRepository: TabBookRepository,
    private val timelineCacheRepository: TimelineCacheRepository,
    private val transportFactory: AcpTransportFactory = DefaultAcpTransportFactory,
    networkAvailableFlow: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow(),
    parentScope: CoroutineScope? = null,
) {
    private val supervisor: Job = SupervisorJob(parentScope?.coroutineContext?.get(Job))
    private val scope = CoroutineScope(supervisor + Dispatchers.Default)

    private val _pairError = MutableStateFlow<BclawV2UrlParseResult.Error?>(null)
    val pairError: StateFlow<BclawV2UrlParseResult.Error?> = _pairError.asStateFlow()

    private val _agentConnections = MutableStateFlow<Map<AgentId, AgentConnectionPhase>>(emptyMap())
    private val _tabRuntimes = MutableStateFlow<Map<TabId, TabRuntime>>(emptyMap())
    private val _timelines = MutableStateFlow<Map<TabId, List<TimelineItem>>>(emptyMap())
    private val _agentInit = MutableStateFlow<Map<AgentId, AcpInitializeResult>>(emptyMap())

    /**
     * Per-cwd historical session list, keyed by cwd. Runtime-only — fetched on demand from the
     * bridge's `/sessions` endpoint. Contains entries from all agents that knew the cwd.
     */
    private val _projectSessions = MutableStateFlow<Map<CwdPath, List<SessionRef>>>(emptyMap())
    val projectSessions: StateFlow<Map<CwdPath, List<SessionRef>>> = _projectSessions.asStateFlow()

    /** Public per-tab timeline stream. UI layer reads [timelineFor]. */
    val timelines: StateFlow<Map<TabId, List<TimelineItem>>> = _timelines.asStateFlow()

    /**
     * Per-agent initialize snapshot captured at connect time. Powers the Capabilities drawer
     * (skills / mcp / commands) and any other surface that needs to know what the agent
     * claimed during the ACP handshake.
     */
    val agentInit: StateFlow<Map<AgentId, AcpInitializeResult>> = _agentInit.asStateFlow()

    /** Device-level network flag, surfaced so the status bar can render "no network" plainly. */
    val networkAvailable: StateFlow<Boolean> = networkAvailableFlow

    // Mutable agent/session state — guarded by [transportMutex] during connect + dispose.
    private val transports = ConcurrentHashMap<AgentId, AgentConnection>()
    private val sessionToTab = ConcurrentHashMap<SessionId, TabId>()
    private val transportMutex = Mutex()

    /**
     * Tabs we've attempted to `session/load` during this process. Guards the auto-load path
     * below so a single activation triggers at most one load attempt — without this, every
     * uiState emission during load (phase flips, timeline grows) would re-queue.
     *
     * Cleared on transport close so the next activation retries with a fresh connection.
     */
    private val autoLoadAttempted = ConcurrentHashMap.newKeySet<TabId>()

    /** Exponential-backoff attempt counter per agent, reset on successful connect. */
    private val reconnectAttempts = ConcurrentHashMap<AgentId, Int>()

    /** In-flight delayed retry jobs per agent; cancelled on dispose / superseded on reschedule. */
    private val reconnectJobs = ConcurrentHashMap<AgentId, Job>()

    /**
     * Monotonic "which connect attempt" counter per agent. Every call to [ensureTransport]
     * bumps this. Each JsonRpcSession.Delegate captures the value it was born with and only
     * mutates shared state (phase, transports map, reconnect state) if it's still the active
     * generation. Without this the old socket's `onClosed`/`onFailure` could fire AFTER a
     * reconnect has already installed a working socket, and the old delegate would dutifully
     * evict the *new* working entry and scheduleReconnect again — producing flapping Offline
     * states and dead sockets in the map.
     */
    private val agentGeneration = ConcurrentHashMap<AgentId, Long>()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<BclawV2UiState> = deviceBookRepository.deviceBookFlow
        .flatMapLatest { book ->
            val activeBookFlow = book.activeDeviceId
                ?.let { tabBookRepository.observe(it) }
                ?: flowOf(null)
            combine(
                activeBookFlow,
                _agentConnections,
                _tabRuntimes,
                networkAvailableFlow,
                BridgeReachability.reachable,
            ) { tabBook, agents, tabs, online, bridgeReachable ->
                BclawV2UiState(
                    deviceBook = book,
                    activeTabBook = tabBook,
                    activeTabId = tabBook?.activeTabId,
                    agentConnections = agents,
                    tabRuntimes = tabs,
                    networkAvailable = online,
                    bridgePhase = aggregateBridgePhase(
                        agents = agents,
                        tabs = tabBook?.tabs.orEmpty(),
                        hasActiveDevice = book.activeDeviceId != null,
                        networkAvailable = online,
                        bridgeReachable = bridgeReachable,
                    ),
                )
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, BclawV2UiState())

    fun timelineFor(tabId: TabId): List<TimelineItem> =
        _timelines.value[tabId].orEmpty()

    init {
        // Auto-load: when the active tab has a persisted sessionId and we haven't loaded it
        // yet this process, fire session/load once. Covers the "relaunch into a resumed tab"
        // case — previously the user had to close + reopen the tab to see its history.
        scope.launch {
            var lastTabId: TabId? = null
            uiState.collect { state ->
                val tabId = state.activeTabId ?: return@collect
                if (tabId == lastTabId) return@collect
                lastTabId = tabId
                val tab = state.activeTabBook?.tabs?.firstOrNull { it.id == tabId } ?: return@collect
                val sessionId = tab.sessionId ?: return@collect

                // Hydrate from disk cache before deciding whether to fetch. After cold start the
                // in-memory timeline is empty even for sessions we've seen before; the cache lets
                // us render instantly and skip the full session/load replay. freezeStreaming
                // protects against items that were mid-stream when the app died.
                if (_timelines.value[tab.id].isNullOrEmpty()) {
                    val deviceId = state.deviceBook.activeDeviceId
                    if (deviceId != null) {
                        val cached = runCatching {
                            timelineCacheRepository.read(deviceId, sessionId)
                        }.getOrElse { emptyList() }
                        if (cached.isNotEmpty()) {
                            val frozen = AcpTimelineReducer.freezeStreaming(cached)
                            _timelines.update { it + (tab.id to frozen) }
                        }
                    }
                }

                // Timeline already populated (cache hydrate, real-time streaming, or a prior
                // resumeHistoricalSession load) — don't reload or we'd duplicate.
                if (_timelines.value[tab.id]?.isNotEmpty() == true) {
                    autoLoadAttempted.add(tab.id)
                    return@collect
                }
                if (!autoLoadAttempted.add(tab.id)) return@collect
                scope.launch { autoLoadSession(tab) }
            }
        }

        startTimelineCachePersistenceLoop()

        // Auto-refresh /agents + /projects whenever the active device changes (including the
        // first emission after app start). Fixes the "paired before bridge added Claude/Gemini"
        // case where the local knownAgents stays frozen to whatever the original URL announced.
        // After sync, re-fetch sessions for the active cwd — Home's LaunchedEffect re-keys only
        // on device.id, so newly-discovered agents would otherwise never get queried for history.
        scope.launch {
            deviceBookRepository.deviceBookFlow
                .map { it.activeDeviceId }
                .distinctUntilChanged()
                .collect { id ->
                    if (id == null) return@collect
                    scope.launch {
                        // syncDeviceMetadata returns the post-merge Device directly. We can't
                        // read uiState.value here: the DataStore write inside upsertDevice
                        // emits through deviceBookFlow → combine → uiState asynchronously, so
                        // a value read immediately after sync can still be pre-merge.
                        val synced = runCatching { syncDeviceMetadata(id) }.getOrNull()
                            ?: return@launch
                        val cwd = synced.effectiveProjectCwd ?: return@launch
                        runCatching { fetchSessionsFor(synced, cwd) }
                    }
                }
        }

        // Network awareness: drop stale transports when the radio goes away, and re-dial
        // whichever agents the user has tabs open for once it comes back. Without this the
        // phone looked "Connected" long after the Wi-Fi flipped, and a recovered network
        // just sat there Offline until the user manually re-sent.
        scope.launch {
            var previouslyAvailable = networkAvailableFlow.value
            networkAvailableFlow.collect { nowAvailable ->
                val was = previouslyAvailable
                previouslyAvailable = nowAvailable
                if (was && !nowAvailable) {
                    transportMutex.withLock {
                        transports.values.forEach { runCatching { it.transport.shutdown() } }
                        transports.clear()
                    }
                    autoLoadAttempted.clear()
                    _agentConnections.update { phases ->
                        phases.mapValues { AgentConnectionPhase.Offline }
                    }
                    // Drop stale bridge reachability — NoNetwork takes priority anyway, but
                    // clearing avoids "Offline" sticking after Wi-Fi returns and before the
                    // next discovery probe lands.
                    BridgeReachability.reset()
                } else if (!was && nowAvailable) {
                    BridgeReachability.reset()
                    val device = uiState.value.deviceBook.activeDevice ?: return@collect
                    val relevant = uiState.value.activeTabBook?.tabs?.map { it.agentId }?.toSet()
                        .orEmpty()
                    for (agentId in relevant) {
                        scope.launch { runCatching { ensureTransport(agentId, device) } }
                    }
                }
            }
        }
    }

    /**
     * Persist per-tab timelines to disk so reopens hit the cache. debounce(800) batches
     * streaming chunk bursts; drop(1) skips the StateFlow's initial empty emission so we
     * don't immediately overwrite cached entries with empty lists at startup.
     */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun startTimelineCachePersistenceLoop() {
        scope.launch {
            _timelines
                .drop(1)
                .debounce(800)
                .collect { map ->
                    val state = uiState.value
                    val deviceId = state.deviceBook.activeDeviceId ?: return@collect
                    val tabsById = state.activeTabBook?.tabs?.associateBy { it.id }
                        ?: return@collect
                    for ((tabId, items) in map) {
                        if (items.isEmpty()) continue
                        val tab = tabsById[tabId] ?: continue
                        val sessionId = tab.sessionId ?: continue
                        runCatching {
                            timelineCacheRepository.write(deviceId, sessionId, items)
                        }
                    }
                }
        }
    }

    private suspend fun autoLoadSession(tab: TabState) {
        val device = uiState.value.deviceBook.activeDevice ?: return
        val sessionId = tab.sessionId ?: return
        sessionToTab[sessionId] = tab.id
        updateTabRuntime(tab.id) { it.copy(historyLoading = true) }
        val loaded = runCatching {
            val connection = ensureTransport(tab.agentId, device)
            val params = BclawJson.encodeToJsonElement(
                AcpSessionLoadParams.serializer(),
                AcpSessionLoadParams(
                    sessionId = sessionId.value,
                    cwd = tab.projectCwd.value,
                ),
            )
            connection.transport.requestRaw("session/load", params)
        }
        if (loaded.isSuccess) {
            _timelines.update { current ->
                current.mapValues { (id, list) ->
                    if (id == tab.id) AcpTimelineReducer.freezeStreaming(list) else list
                }
            }
        } else {
            // Leave room for a retry on the next activation (e.g. user switches away and back,
            // or relaunches the app). Phase flips to Offline via the transport delegate so the
            // crumb signals the failure.
            autoLoadAttempted.remove(tab.id)
        }
        updateTabRuntime(tab.id) { it.copy(historyLoading = false) }
    }

    // ── Intents ─────────────────────────────────────────────────────────

    fun onIntent(intent: BclawV2Intent) {
        when (intent) {
            is BclawV2Intent.PairNewDevice -> pairNewDevice(intent.rawUrl)
            is BclawV2Intent.SwitchDevice -> switchDevice(intent.id)
            is BclawV2Intent.RemoveDevice -> removeDevice(intent.id)
            is BclawV2Intent.RefreshActiveDeviceMetadata -> refreshActiveDeviceMetadata()
            is BclawV2Intent.SelectProject -> selectProject(intent.cwd)
            is BclawV2Intent.AddProjectToActiveDevice -> addProjectToActiveDevice(intent.cwd)
            is BclawV2Intent.RefreshSessionsForProject -> refreshSessionsFor(intent.cwd)
            is BclawV2Intent.OpenNewTab -> openNewTab(intent.agentId, intent.cwd, intent.forkedFrom)
            is BclawV2Intent.CloseTab -> closeTab(intent.id)
            is BclawV2Intent.SwitchTab -> switchTab(intent.id)
            is BclawV2Intent.SelectHomeTab -> switchToHome()
            is BclawV2Intent.ForkTabToAgent -> Unit // Batch 2
            is BclawV2Intent.SendPrompt -> sendPrompt(intent.tabId, intent.text)
            is BclawV2Intent.CancelPrompt -> cancelPrompt(intent.tabId)
            is BclawV2Intent.ResumeHistoricalSession -> resumeHistoricalSession(intent.session)
        }
    }

    fun clearPairError() { _pairError.value = null }

    fun shutdown() {
        transports.values.forEach { runCatching { it.transport.shutdown() } }
        transports.clear()
        sessionToTab.clear()
        supervisor.cancel()
    }

    // ── Pair / device lifecycle ─────────────────────────────────────────

    private fun pairNewDevice(rawUrl: String) {
        when (val parsed = BclawV2UrlParser.parse(rawUrl)) {
            is BclawV2UrlParseResult.Error -> _pairError.value = parsed
            is BclawV2UrlParseResult.Success -> {
                _pairError.value = null
                scope.launch {
                    val device = Device(
                        id = DeviceId.generate(),
                        displayName = defaultDeviceName(parsed.wsBaseUrl),
                        wsBaseUrl = parsed.wsBaseUrl,
                        token = parsed.token,
                        knownAgents = parsed.agents,
                        knownProjects = parsed.projects,
                        pairedAtEpochMs = System.currentTimeMillis(),
                    )
                    deviceBookRepository.upsertDevice(device)
                    deviceBookRepository.setActiveDevice(device.id)
                    syncDeviceMetadata(device.id)
                }
            }
        }
    }

    private fun switchDevice(id: DeviceId) {
        scope.launch {
            disposeAllTransports()
            deviceBookRepository.setActiveDevice(id)
            syncDeviceMetadata(id)
        }
    }

    private fun refreshActiveDeviceMetadata() {
        val id = uiState.value.deviceBook.activeDeviceId ?: return
        scope.launch {
            val synced = syncDeviceMetadata(id) ?: return@launch
            synced.effectiveProjectCwd?.let { cwd -> fetchSessionsFor(synced, cwd) }
        }
    }

    /**
     * Fetches `/agents` + `/projects?agent=<id>` (per known agent) from the bridge, merges with
     * URL-declared entries, and persists. Safe to call repeatedly — failed fetches return empty
     * lists, so stored metadata is preserved.
     */
    private suspend fun syncDeviceMetadata(id: DeviceId): Device? {
        // Called from the activeDeviceId observer during cold start / first pair. That flow can
        // emit before uiState's stateIn(combine(...)) snapshot has caught up, so reading uiState
        // here races and incorrectly no-ops before any HTTP sync runs. Read the repo flow directly.
        val device = deviceBookRepository.deviceBookFlow.first()
            .devices
            .firstOrNull { it.id == id }
            ?: return null

        // 1. Agents: merge URL-declared ∪ bridge-declared.
        val remoteAgents = AcpAgentDiscovery.fetchAgents(device.wsBaseUrl)
        val mergedAgents = if (remoteAgents.isEmpty()) {
            device.knownAgents
        } else {
            val byId = device.knownAgents.associateBy { it.id }.toMutableMap()
            remoteAgents.forEach { info ->
                val agentId = AgentId(info.name)
                if (agentId !in byId) {
                    byId[agentId] = AgentDescriptor(id = agentId, displayName = info.displayName)
                }
            }
            byId.values.toList()
        }

        // 2. Per-agent projects: one call per agent. We query *all* merged agents so a newly-
        //    discovered agent gets its project list on the same pass.
        val projectsByAgent = mutableMapOf<AgentId, List<CwdPath>>()
        for (agent in mergedAgents) {
            val remote = AcpProjectDiscovery.fetchProjects(device.wsBaseUrl, agent.id.value)
            if (remote.isNotEmpty()) {
                projectsByAgent[agent.id] = remote.distinct()
            } else {
                // Preserve existing entry if remote briefly fails; else skip (missing = agent
                // had no projects on the mac, which is a legitimate empty).
                device.knownProjectsByAgent[agent.id]?.let { projectsByAgent[agent.id] = it }
            }
        }

        val noChange = mergedAgents == device.knownAgents &&
            projectsByAgent == device.knownProjectsByAgent
        if (noChange) return device

        val current = deviceBookRepository.deviceBookFlow.first()
            .devices
            .firstOrNull { it.id == id }
            ?: return null
        val updated = current.copy(
            knownAgents = mergedAgents,
            knownProjectsByAgent = projectsByAgent,
        )
        deviceBookRepository.upsertDevice(updated)
        return updated
    }

    // ── Session history ─────────────────────────────────────────────────

    private fun refreshSessionsFor(cwd: CwdPath) {
        val device = uiState.value.deviceBook.activeDevice ?: return
        scope.launch { fetchSessionsFor(device, cwd) }
    }

    /**
     * Calls `/sessions?agent=X&cwd=Y` for every agent that knows [cwd], merges the results into
     * [_projectSessions]. De-dupes by (agentId, sessionId). Sorted by lastActivityEpochMs desc,
     * nulls last.
     */
    private suspend fun fetchSessionsFor(device: Device, cwd: CwdPath) {
        // Query *every* known agent, not just those whose knownProjectsByAgent lists this cwd.
        // The agent-project list tells us who can *open* a new session here; historical sessions
        // can still exist for agents we haven't classified yet (stale metadata, bridge schema
        // drift, etc). Each call is a cheap `/sessions?agent=X&cwd=Y` GET — an empty response is
        // the cheap-and-correct signal that there's nothing to show.
        if (device.knownAgents.isEmpty()) {
            _projectSessions.update { it + (cwd to emptyList()) }
            return
        }
        val merged = mutableListOf<SessionRef>()
        for (agent in device.knownAgents) {
            merged += AcpSessionDiscovery.fetchSessions(device.wsBaseUrl, agent.id, cwd)
        }
        val deduped = merged
            .distinctBy { it.agentId.value to it.sessionId.value }
            .sortedWith(
                compareByDescending<SessionRef> { it.lastActivityEpochMs != null }
                    .thenByDescending { it.lastActivityEpochMs ?: 0L },
            )
        _projectSessions.update { it + (cwd to deduped) }
    }

    private fun resumeHistoricalSession(session: SessionRef) {
        val deviceId = uiState.value.deviceBook.activeDeviceId ?: return
        scope.launch {
            // If a tab for this (agent, sessionId) already exists, just switch to it.
            // The auto-load listener will fire session/load on activation if needed.
            val existing = uiState.value.activeTabBook?.tabs?.firstOrNull {
                it.agentId == session.agentId && it.sessionId == session.sessionId
            }
            if (existing != null) {
                tabBookRepository.setActiveTab(deviceId, existing.id)
                return@launch
            }

            val tab = TabState(
                id = TabId.generate(),
                agentId = session.agentId,
                projectCwd = session.projectCwd,
                sessionId = session.sessionId,
                sessionName = session.title,
                unread = false,
                lastActivityEpochMs = System.currentTimeMillis(),
                forkedFromTabId = null,
            )
            tabBookRepository.upsertTab(deviceId, tab)
            tabBookRepository.setActiveTab(deviceId, tab.id)
            // session/load is dispatched from the auto-load listener once activeTabId flips,
            // which also handles the "app relaunched into this tab" case uniformly.
        }
    }

    private fun selectProject(cwd: CwdPath) {
        val device = uiState.value.deviceBook.activeDevice ?: return
        if (device.activeProjectCwd == cwd) return
        scope.launch {
            deviceBookRepository.upsertDevice(device.copy(activeProjectCwd = cwd))
        }
    }

    /**
     * Adds a user-typed cwd to the active device's knownProjects and selects it. The phone owns
     * the entry locally; the Mac-side agent registers it in its config only once the user starts
     * a session on that cwd (codex writes `[projects."<cwd>"]` to config.toml, claude lands a
     * jsonl under `~/.claude/projects/<slug>/` on the first event).
     *
     * No-op if the cwd is already tracked on this device.
     */
    private fun addProjectToActiveDevice(cwd: CwdPath) {
        val device = uiState.value.deviceBook.activeDevice ?: return
        if (cwd in device.allKnownProjects) {
            selectProject(cwd)
            return
        }
        scope.launch {
            deviceBookRepository.upsertDevice(
                device.copy(
                    knownProjects = device.knownProjects + cwd,
                    activeProjectCwd = cwd,
                ),
            )
        }
    }

    private fun removeDevice(id: DeviceId) {
        scope.launch {
            if (uiState.value.deviceBook.activeDeviceId == id) disposeAllTransports()
            deviceBookRepository.removeDevice(id)
            tabBookRepository.removeDeviceTabs(id)
            runCatching { timelineCacheRepository.removeForDevice(id) }
        }
    }

    // ── Tab lifecycle ───────────────────────────────────────────────────

    private fun openNewTab(agentId: AgentId, cwd: CwdPath, forkedFrom: TabId?) {
        val deviceId = uiState.value.deviceBook.activeDeviceId ?: return
        scope.launch {
            val tab = TabState(
                id = TabId.generate(),
                agentId = agentId,
                projectCwd = cwd,
                sessionId = null,
                sessionName = null,
                unread = false,
                lastActivityEpochMs = System.currentTimeMillis(),
                forkedFromTabId = forkedFrom,
            )
            tabBookRepository.upsertTab(deviceId, tab)
            tabBookRepository.setActiveTab(deviceId, tab.id)
        }
    }

    private fun closeTab(id: TabId) {
        val deviceId = uiState.value.deviceBook.activeDeviceId ?: return
        scope.launch {
            val sessionId = uiState.value.activeTabBook?.tabs
                ?.firstOrNull { it.id == id }?.sessionId
            sessionId?.let {
                sessionToTab.remove(it)
                runCatching { timelineCacheRepository.removeForSession(deviceId, it) }
            }
            tabBookRepository.removeTab(deviceId, id)
            _timelines.update { it - id }
            _tabRuntimes.update { it - id }
            autoLoadAttempted.remove(id)
        }
    }

    private fun switchTab(id: TabId) {
        val deviceId = uiState.value.deviceBook.activeDeviceId ?: return
        scope.launch {
            tabBookRepository.setActiveTab(deviceId, id)
            // Clear the unread flag on entry
            val tab = uiState.value.activeTabBook?.tabs?.firstOrNull { it.id == id } ?: return@launch
            if (tab.unread) tabBookRepository.upsertTab(deviceId, tab.copy(unread = false))
        }
    }

    private fun switchToHome() {
        val deviceId = uiState.value.deviceBook.activeDeviceId ?: return
        scope.launch { tabBookRepository.setActiveTab(deviceId, null) }
    }

    // ── Prompt send / cancel ────────────────────────────────────────────

    private fun sendPrompt(tabId: TabId, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        scope.launch {
            val tab = uiState.value.activeTabBook?.tabs?.firstOrNull { it.id == tabId } ?: return@launch
            val device = uiState.value.deviceBook.activeDevice ?: return@launch

            // 1. Optimistic: user message immediately (streaming=true so codex's
            //    user_message_chunk echo merges into this bubble instead of duplicating).
            appendToTimeline(tabId, TimelineItem.UserMessage(
                createdAtEpochMs = System.currentTimeMillis(),
                text = trimmed,
                streaming = true,
            ))

            // 2. Connection + session lifecycle
            val connection = runCatching { ensureTransport(tab.agentId, device) }
                .getOrElse {
                    updateAgentPhase(tab.agentId, AgentConnectionPhase.Offline)
                    return@launch
                }

            val sessionId = runCatching { ensureSession(tab, connection) }.getOrElse {
                return@launch
            }

            // 3. Dispatch session/prompt; streaming notifications drive the timeline.
            updateTabRuntime(tabId) { it.copy(
                streamingTurnInFlight = true,
                runningStripLabel = "thinking…",
            ) }
            runCatching {
                val params = BclawJson.encodeToJsonElement(
                    AcpPromptParams.serializer(),
                    AcpPromptParams(
                        sessionId = sessionId.value,
                        prompt = listOf(AcpContentBlock(type = "text", text = trimmed)),
                    ),
                )
                connection.transport.request<AcpPromptResult>("session/prompt", params)
            }

            // 4. Freeze streaming states + clear running strip.
            _timelines.update { current ->
                current.mapValues { (id, list) ->
                    if (id == tabId) AcpTimelineReducer.freezeStreaming(list) else list
                }
            }
            updateTabRuntime(tabId) { it.copy(
                streamingTurnInFlight = false,
                runningStripLabel = null,
            ) }

            // 5. Record last-activity timestamp for sorting. Session title (when provided)
            //    is picked up from `session_info_update` in handleSessionUpdate; don't
            //    synthesize one here — a prior mock-leak hardcoded "composer · autofocus"
            //    onto real sessions at every turn-end.
            val active = uiState.value.deviceBook.activeDeviceId ?: return@launch
            tabBookRepository.upsertTab(active, tab.copy(
                sessionId = sessionId,
                lastActivityEpochMs = System.currentTimeMillis(),
            ))
        }
    }

    private fun cancelPrompt(tabId: TabId) {
        scope.launch {
            val tab = uiState.value.activeTabBook?.tabs?.firstOrNull { it.id == tabId } ?: return@launch
            val sessionId = tab.sessionId ?: return@launch
            val connection = transports[tab.agentId] ?: return@launch
            runCatching {
                val params = BclawJson.encodeToJsonElement(
                    AcpSessionCancelParams.serializer(),
                    AcpSessionCancelParams(sessionId = sessionId.value),
                )
                connection.transport.requestRaw("session/cancel", params)
            }
        }
    }

    // ── Transport + session plumbing ────────────────────────────────────

    private suspend fun ensureTransport(agentId: AgentId, device: Device): AgentConnection {
        transports[agentId]?.let { return it }
        return transportMutex.withLock {
            transports[agentId]?.let { return@withLock it }
            val myGen = agentGeneration.compute(agentId) { _, prev -> (prev ?: 0L) + 1L } ?: 1L
            val isCurrent = { agentGeneration[agentId] == myGen }
            val delegate = object : JsonRpcSession.Delegate {
                override fun onTransportOpening() {
                    if (!isCurrent()) return
                    updateAgentPhase(agentId, AgentConnectionPhase.Connecting)
                }
                override fun onTransportReady() {
                    if (!isCurrent()) return
                    updateAgentPhase(agentId, AgentConnectionPhase.Connected)
                    // Success — reset the backoff counter so the next failure starts fast.
                    reconnectAttempts.remove(agentId)
                    reconnectJobs.remove(agentId)?.cancel()
                }
                override fun onTransportClosed(code: Int, reason: String) {
                    if (!isCurrent()) return
                    transports.remove(agentId)?.transport?.shutdown()
                    autoLoadAttempted.clear()
                    updateAgentPhase(agentId, AgentConnectionPhase.Offline)
                    scheduleReconnect(agentId)
                }
                override fun onTransportFailure(throwable: Throwable) {
                    if (!isCurrent()) return
                    transports.remove(agentId)?.transport?.shutdown()
                    autoLoadAttempted.clear()
                    updateAgentPhase(agentId, AgentConnectionPhase.Offline)
                    scheduleReconnect(agentId)
                }
                override suspend fun onNotification(method: String, params: JsonObject) {
                    if (!isCurrent()) return
                    if (method == "session/update") handleSessionUpdate(params)
                }
                override suspend fun onServerRequest(id: JsonElement, method: String, params: JsonObject): JsonElement {
                    // Auto-accept any permission / fs request in v2.0 — matches v0's belt-and-
                    // suspenders approach. Batch 4 replaces this with real UI when approval
                    // message-type lands.
                    return JsonObject(emptyMap())
                }
            }
            val transport: AcpTransport = transportFactory(delegate)
            val url = "${device.wsBaseUrl}/${agentId.value}"
            val initResult: AcpInitializeResult = transport.connect(
                url = url,
                token = device.token,
                initializeParams = AcpInitializeParams(),
            )
            val connection = AgentConnection(transport = transport, init = initResult)
            transports[agentId] = connection
            _agentInit.update { it + (agentId to initResult) }
            connection
        }
    }

    private suspend fun ensureSession(tab: TabState, connection: AgentConnection): SessionId {
        tab.sessionId?.let { existing ->
            sessionToTab[existing] = tab.id
            return existing
        }
        val params = BclawJson.encodeToJsonElement(
            AcpSessionNewParams.serializer(),
            AcpSessionNewParams(cwd = tab.projectCwd.value),
        )
        val result: AcpSessionNewResult = connection.transport.request("session/new", params)
        val sessionId = SessionId(result.sessionId)
        sessionToTab[sessionId] = tab.id

        val deviceId = uiState.value.deviceBook.activeDeviceId ?: return sessionId
        tabBookRepository.upsertTab(
            deviceId,
            tab.copy(
                sessionId = sessionId,
                lastActivityEpochMs = System.currentTimeMillis(),
            ),
        )
        return sessionId
    }

    private fun handleSessionUpdate(params: JsonObject) {
        val sessionIdValue = params["sessionId"]?.jsonPrimitive?.contentOrNull ?: return
        val tabId = sessionToTab[SessionId(sessionIdValue)] ?: return
        val update = params["update"]?.jsonObject ?: return

        // Session info update → persist session title (UX_V2 §2.3: tab label = session name).
        val updateKind = update["sessionUpdate"]?.jsonPrimitive?.contentOrNull
        if (updateKind == "session_info_update") {
            val title = update["title"]?.jsonPrimitive?.contentOrNull
            val tab = uiState.value.activeTabBook?.tabs?.firstOrNull { it.id == tabId }
            val deviceId = uiState.value.deviceBook.activeDeviceId
            if (!title.isNullOrBlank() && tab != null && deviceId != null) {
                scope.launch { tabBookRepository.upsertTab(deviceId, tab.copy(sessionName = title)) }
            }
        }

        // Running strip label: best-effort based on the update kind we just got.
        updateTabRuntime(tabId) { runtime ->
            runtime.copy(runningStripLabel = runningLabelFor(updateKind, update) ?: runtime.runningStripLabel)
        }

        // Reduce onto timeline. StateFlow.update is atomic CAS — required because
        // session/load replay fires ~200 updates in a burst and a read-modify-write
        // on `.value` races even with serialized inbound dispatch if the reducer runs
        // on a different coroutine than the writer (belt-and-braces).
        val now = System.currentTimeMillis()
        _timelines.update { current ->
            current + (tabId to AcpTimelineReducer.reduce(current[tabId].orEmpty(), update, now))
        }

        // Mark unread if this tab isn't the currently-active one.
        if (uiState.value.activeTabId != tabId) {
            val deviceId = uiState.value.deviceBook.activeDeviceId
            val tab = uiState.value.activeTabBook?.tabs?.firstOrNull { it.id == tabId }
            if (deviceId != null && tab != null && !tab.unread) {
                scope.launch { tabBookRepository.upsertTab(deviceId, tab.copy(unread = true)) }
            }
        }
    }

    private fun runningLabelFor(kind: String?, update: JsonObject): String? = when (kind) {
        "agent_message_chunk" -> "responding…"
        "agent_thought_chunk" -> "thinking…"
        "tool_call" -> {
            val title = update["title"]?.jsonPrimitive?.contentOrNull
            val cmd = update["rawInput"]?.jsonObject?.get("cmd")?.jsonPrimitive?.contentOrNull
            cmd?.let { "running $it" } ?: title?.let { "running $it" } ?: "tool…"
        }
        else -> null
    }

    // ── internal helpers ────────────────────────────────────────────────

    private suspend fun disposeAllTransports() {
        transportMutex.withLock {
            reconnectJobs.values.forEach { it.cancel() }
            reconnectJobs.clear()
            reconnectAttempts.clear()
            transports.values.forEach { runCatching { it.transport.shutdown() } }
            transports.clear()
            sessionToTab.clear()
            _agentConnections.value = emptyMap()
            _tabRuntimes.value = emptyMap()
            _timelines.value = emptyMap()
            _agentInit.value = emptyMap()
            _projectSessions.value = emptyMap()
        }
    }

    private fun appendToTimeline(tabId: TabId, item: TimelineItem) {
        _timelines.update { current ->
            current + (tabId to (current[tabId].orEmpty() + item))
        }
    }

    private fun updateTabRuntime(tabId: TabId, transform: (TabRuntime) -> TabRuntime) {
        _tabRuntimes.update { current ->
            val tab = current[tabId] ?: TabRuntime()
            current + (tabId to transform(tab))
        }
    }

    private fun updateAgentPhase(agentId: AgentId, phase: AgentConnectionPhase) {
        _agentConnections.update { it + (agentId to phase) }
    }

    /**
     * Re-dial a dropped agent on a backoff without waiting for user action. Covers the "socket
     * silently died" path — VPN flap, Mac sleep, bridge restart — where the OS default-network
     * flag never flipped so the network-transition block didn't fire.
     *
     * Backoff: 1.5s → 3s → 6s → 12s → 15s cap. Reset by [onTransportReady]. Only runs if the
     * phone has a network route and at least one tab still targets this agent.
     */
    private fun scheduleReconnect(agentId: AgentId) {
        if (!networkAvailable.value) return
        uiState.value.deviceBook.activeDevice ?: return
        val stillRelevant = uiState.value.activeTabBook?.tabs?.any { it.agentId == agentId } == true
        if (!stillRelevant) return

        val attempt = reconnectAttempts.merge(agentId, 1, Int::plus) ?: 1
        val shift = (attempt - 1).coerceIn(0, 4)
        val delayMs = (1500L shl shift).coerceAtMost(15_000L)

        reconnectJobs.remove(agentId)?.cancel()
        reconnectJobs[agentId] = scope.launch {
            delay(delayMs)
            if (!networkAvailable.value) return@launch
            val currentDevice = uiState.value.deviceBook.activeDevice ?: return@launch
            val agentStillInUse = uiState.value.activeTabBook?.tabs?.any { it.agentId == agentId } == true
            if (!agentStillInUse) return@launch
            if (transports[agentId] != null) return@launch // already reconnected elsewhere
            runCatching { ensureTransport(agentId, currentDevice) }
        }
    }

    private fun defaultDeviceName(wsBaseUrl: String): String =
        Regex("""ws://([^:/]+)""").find(wsBaseUrl)?.groupValues?.getOrNull(1) ?: wsBaseUrl
}

// ── Intent surface ──────────────────────────────────────────────────────

sealed class BclawV2Intent {
    data class PairNewDevice(val rawUrl: String) : BclawV2Intent()
    data class SwitchDevice(val id: DeviceId) : BclawV2Intent()
    data class RemoveDevice(val id: DeviceId) : BclawV2Intent()
    /** Re-fetch `/agents` + `/projects` from the active device's bridge and merge into state. */
    data object RefreshActiveDeviceMetadata : BclawV2Intent()
    /** Select which project (cwd) is active on the current device's Home. */
    data class SelectProject(val cwd: CwdPath) : BclawV2Intent()
    data class AddProjectToActiveDevice(val cwd: CwdPath) : BclawV2Intent()
    /** Fetch (or re-fetch) historical sessions under [cwd] for the active device's agents. */
    data class RefreshSessionsForProject(val cwd: CwdPath) : BclawV2Intent()
    /** Open a tab pointing at an existing session on disk and try to `session/load` it. */
    data class ResumeHistoricalSession(val session: SessionRef) : BclawV2Intent()

    data class OpenNewTab(
        val agentId: AgentId,
        val cwd: CwdPath,
        val forkedFrom: TabId? = null,
    ) : BclawV2Intent()
    data class CloseTab(val id: TabId) : BclawV2Intent()
    data class SwitchTab(val id: TabId) : BclawV2Intent()
    data object SelectHomeTab : BclawV2Intent()

    data class ForkTabToAgent(val fromTabId: TabId, val toAgent: AgentId) : BclawV2Intent()

    data class SendPrompt(val tabId: TabId, val text: String) : BclawV2Intent()
    data class CancelPrompt(val tabId: TabId) : BclawV2Intent()
}

// ── UI state snapshot ───────────────────────────────────────────────────

data class BclawV2UiState(
    val deviceBook: DeviceBook = DeviceBook(),
    val activeTabBook: TabBook? = null,
    val activeTabId: TabId? = null,
    val agentConnections: Map<AgentId, AgentConnectionPhase> = emptyMap(),
    val tabRuntimes: Map<TabId, TabRuntime> = emptyMap(),
    val networkAvailable: Boolean = true,
    val bridgePhase: BridgePhase = BridgePhase.Idle,
) {
    val hasActiveDevice: Boolean get() = deviceBook.activeDeviceId != null
}

enum class AgentConnectionPhase {
    Connecting, Connected, Reconnecting, Offline, NotAvailable
}

/**
 * Device-level rollup of per-agent phases, intended as the *single* surface the UI shows
 * for "am I connected to my Mac?". Scoped to agents referenced by at least one open tab —
 * we don't want the bar to flag an agent the user hasn't asked to use yet.
 */
enum class BridgePhase {
    /** No open tabs yet, or the user hasn't exercised any agent. Bar hides. */
    Idle,

    /** Phone has no route to the internet (Wi-Fi off, airplane mode, etc). */
    NoNetwork,

    /** Any relevant agent is negotiating or retrying. */
    Connecting,

    /** Every relevant agent is Connected — happy path; bar hides. */
    Connected,

    /** Mix: at least one Connected, at least one Offline/NotAvailable. */
    Degraded,

    /** All relevant agents are Offline/NotAvailable. */
    Offline,
}

private fun aggregateBridgePhase(
    agents: Map<AgentId, AgentConnectionPhase>,
    tabs: List<TabState>,
    hasActiveDevice: Boolean,
    networkAvailable: Boolean,
    bridgeReachable: Boolean?,
): BridgePhase {
    if (!networkAvailable) return BridgePhase.NoNetwork
    // HTTP discovery (`/agents`, `/projects`, `/sessions`) hits the bridge the moment
    // the user lands on Home — long before any agent WebSocket is dialed. Surface those
    // failures here so a dead bridge shows up as Offline immediately, instead of staying
    // silent until the user opens a session tab.
    if (hasActiveDevice && bridgeReachable == false) return BridgePhase.Offline
    val relevantAgentIds = tabs.map { it.agentId }.toSet()
    if (relevantAgentIds.isEmpty()) return BridgePhase.Idle
    val phases = relevantAgentIds.map { agents[it] }
    // Null phase = never tried to connect this agent yet (tab created but no prompt sent).
    // Treat as Idle-equivalent so the bar doesn't shout about something the user hasn't
    // attempted.
    val observed = phases.filterNotNull()
    if (observed.isEmpty()) return BridgePhase.Idle
    val connecting = observed.any {
        it == AgentConnectionPhase.Connecting || it == AgentConnectionPhase.Reconnecting
    }
    if (connecting) return BridgePhase.Connecting
    val connected = observed.count { it == AgentConnectionPhase.Connected }
    val offline = observed.count {
        it == AgentConnectionPhase.Offline || it == AgentConnectionPhase.NotAvailable
    }
    return when {
        offline == 0 -> BridgePhase.Connected
        connected == 0 -> BridgePhase.Offline
        else -> BridgePhase.Degraded
    }
}

data class TabRuntime(
    val sessionId: SessionId? = null,
    val streamingTurnInFlight: Boolean = false,
    val runningStripLabel: String? = null,
    val lastOutputPreview: String? = null,
    /**
     * True while `session/load` is in flight for this tab — the bridge replays history as
     * a burst of `session/update` notifications, so the timeline grows incrementally and
     * we don't yet know its final size. UI uses this to show a loading hint and to defer
     * "snap to bottom" until the final row count is known.
     */
    val historyLoading: Boolean = false,
)

// ── internal ────────────────────────────────────────────────────────────

private data class AgentConnection(
    val transport: AcpTransport,
    val init: AcpInitializeResult,
)
