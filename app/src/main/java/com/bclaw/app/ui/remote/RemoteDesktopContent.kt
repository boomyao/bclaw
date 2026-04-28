package com.bclaw.app.ui.remote

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.SurfaceTexture
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bclaw.app.R
import com.bclaw.app.remote.SunshineApp
import com.bclaw.app.remote.SunshineDisplay
import com.bclaw.app.remote.SunshineKey
import com.bclaw.app.remote.SunshineLaunchPlan
import com.bclaw.app.remote.SunshineModifier
import com.bclaw.app.remote.SunshineMouseButton
import com.bclaw.app.remote.SunshineStreamRequest
import com.bclaw.app.remote.SunshineStreamStats
import com.bclaw.app.remote.SunshineStatus
import com.bclaw.app.remote.SunshineTouchEvent
import com.bclaw.app.remote.SunshineVideoStream
import com.bclaw.app.remote.SunshineWakeTarget
import com.bclaw.app.remote.fetchSunshineCatalog
import com.bclaw.app.remote.generateRemoteAiInputText
import com.bclaw.app.remote.invokeSunshineAction
import com.bclaw.app.remote.sendRemoteMacosPinch
import com.bclaw.app.remote.sendWakeOnLan
import com.bclaw.app.remote.startSunshineSession
import com.bclaw.app.ui.theme.BclawTheme
import com.bclaw.app.ui.theme.MetroCyan
import com.bclaw.app.ui.theme.MetroOrange
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/** Remote desktop body. Streaming-service details stay behind the host-agent boundary. */
@Composable
fun RemoteDesktopContent(
    hostApiBaseUrl: String?,
    hostAgentToken: String?,
    deviceName: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hostAgentBase = remember(hostApiBaseUrl) { hostApiBaseUrl?.toHostAgentHttpBase() }
    val context = LocalContext.current
    val wifiConnected = rememberWifiConnected(context)
    val imeWindowMetrics = rememberRemoteImeWindowMetrics()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var status by remember(hostAgentBase) { mutableStateOf<SunshineStatus?>(null) }
    val lanConnected = remember(hostAgentBase, status, wifiConnected) {
        wifiConnected && isLanRemoteEndpoint(hostAgentBase, status)
    }
    var apps by remember(hostAgentBase) { mutableStateOf(emptyList<SunshineApp>()) }
    var selectedAppIndex by remember(hostAgentBase) { mutableStateOf<Int?>(null) }
    var selectedDisplayId by remember(hostAgentBase) { mutableStateOf<String?>(null) }
    var launchPlan by remember(hostAgentBase) { mutableStateOf<SunshineLaunchPlan?>(null) }
    var streamSurface by remember(hostAgentBase) { mutableStateOf<Surface?>(null) }
    var streamStats by remember(hostAgentBase) { mutableStateOf<SunshineStreamStats?>(null) }
    var streamError by remember(hostAgentBase) { mutableStateOf<String?>(null) }
    var activeStream by remember(hostAgentBase) { mutableStateOf<SunshineVideoStream?>(null) }
    var loading by remember(hostAgentBase) { mutableStateOf(false) }
    var actionInFlight by remember(hostAgentBase) { mutableStateOf<String?>(null) }
    var error by remember(hostAgentBase) { mutableStateOf<String?>(null) }
    var autoConnectAttempted by remember(hostAgentBase) { mutableStateOf(false) }
    var forceLowStreamProfile by remember(hostAgentBase) { mutableStateOf(false) }
    var forceH264Stream by remember(hostAgentBase) { mutableStateOf(false) }
    var desktopRotated by remember(hostAgentBase) { mutableStateOf(false) }
    var appVisible by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    var streamLeaseActive by remember(hostAgentBase) { mutableStateOf(true) }
    var streamAutoReconnectAttempted by remember(hostAgentBase) { mutableStateOf(false) }
    var streamGeneration by remember(hostAgentBase) { mutableIntStateOf(0) }
    var cachedWakeTargets by remember(hostAgentBase) {
        mutableStateOf(loadCachedWakeTargets(context, hostAgentBase))
    }
    var refreshKey by remember { mutableIntStateOf(0) }
    suspend fun refresh() {
        val base = hostAgentBase ?: return
        loading = true
        error = null
        runCatching { fetchSunshineCatalog(base, token = hostAgentToken) }
            .onSuccess { catalog ->
                status = catalog.status
                apps = catalog.apps
                if (selectedAppIndex == null) {
                    selectedAppIndex = catalog.apps.firstOrNull { it.desktop }?.index
                        ?: catalog.apps.firstOrNull()?.index
                }
                selectedDisplayId = resolveUsableDisplayId(selectedDisplayId, catalog.status)
                if (catalog.status.wake.targets.isNotEmpty()) {
                    cachedWakeTargets = catalog.status.wake.targets
                    saveCachedWakeTargets(context, hostAgentBase, cachedWakeTargets)
                }
            }
            .onFailure { error = it.message ?: "remote status failed" }
        loading = false
    }

    suspend fun closeRemoteSession(base: String, refreshCatalog: Boolean = true) {
        runCatching {
            invokeSunshineAction(base, "/v1/sunshine/close", token = hostAgentToken)
            delay(300)
            if (refreshCatalog) fetchSunshineCatalog(base, token = hostAgentToken) else null
        }.onSuccess { catalog ->
            if (catalog != null) {
                status = catalog.status
                apps = catalog.apps
            }
        }
    }

    fun stopLocalStream() {
        streamGeneration += 1
        activeStream?.close()
        activeStream = null
        launchPlan = null
        streamStats = null
    }

    fun startSession() {
        val base = hostAgentBase ?: return
        val app = apps.firstOrNull { it.index == selectedAppIndex } ?: apps.firstOrNull()
        val displayId = resolveUsableDisplayId(selectedDisplayId, status)
        selectedDisplayId = displayId
        scope.launch {
            actionInFlight = "LIVE"
            error = null
            streamError = null
            streamAutoReconnectAttempted = false
            stopLocalStream()
            runCatching {
                startSunshineSession(
                    base,
                    buildReadableSunshineStreamRequest(
                        app = app,
                        displayId = displayId,
                        useWifiProfile = wifiConnected && !forceLowStreamProfile,
                        useLanProfile = lanConnected && !forceLowStreamProfile,
                        preferAv1 = !forceH264Stream,
                    ),
                    token = hostAgentToken,
                )
            }
                .onSuccess { plan -> launchPlan = plan }
                .onFailure { error = it.message ?: "stream start failed" }
            actionInFlight = null
        }
    }

    fun connectRemote() {
        val base = hostAgentBase ?: return
        scope.launch {
            actionInFlight = "CONNECTING"
            error = null
            streamError = null
            stopLocalStream()
            runCatching {
                var catalog = fetchSunshineCatalog(base, token = hostAgentToken)
                if (!catalog.status.running) {
                    invokeSunshineAction(base, "/v1/sunshine/start", token = hostAgentToken)
                    delay(900)
                    catalog = fetchSunshineCatalog(base, token = hostAgentToken)
                }
                if (catalog.status.ready && !catalog.status.paired) {
                    invokeSunshineAction(base, "/v1/sunshine/client/pair", token = hostAgentToken)
                    delay(600)
                    catalog = fetchSunshineCatalog(base, token = hostAgentToken)
                }
                status = catalog.status
                apps = catalog.apps
                if (selectedAppIndex == null) {
                    selectedAppIndex = catalog.apps.firstOrNull { it.desktop }?.index
                        ?: catalog.apps.firstOrNull()?.index
                }
                val displayId = resolveUsableDisplayId(selectedDisplayId, catalog.status)
                selectedDisplayId = displayId
                if (catalog.status.wake.targets.isNotEmpty()) {
                    cachedWakeTargets = catalog.status.wake.targets
                    saveCachedWakeTargets(context, hostAgentBase, cachedWakeTargets)
                }
                if (!catalog.status.ready || !catalog.status.paired) {
                    throw IllegalStateException("remote host is not ready")
                }
                val app = catalog.apps.firstOrNull { it.index == selectedAppIndex }
                    ?: catalog.apps.firstOrNull { it.desktop }
                    ?: catalog.apps.firstOrNull()
                startSunshineSession(
                    base,
                    buildReadableSunshineStreamRequest(
                        app = app,
                        displayId = displayId,
                        useWifiProfile = wifiConnected && !forceLowStreamProfile,
                        useLanProfile = lanConnected && !forceLowStreamProfile,
                        preferAv1 = !forceH264Stream,
                    ),
                    token = hostAgentToken,
                )
            }
                .onSuccess { plan -> launchPlan = plan }
                .onFailure { error = it.message ?: "remote connection failed" }
            actionInFlight = null
        }
    }

    fun selectDisplay(displayId: String) {
        val base = hostAgentBase ?: return
        scope.launch {
            actionInFlight = "DISPLAY"
            error = null
            streamError = null
            streamAutoReconnectAttempted = false
            stopLocalStream()
            selectedDisplayId = displayId
            runCatching {
                val app = apps.firstOrNull { it.index == selectedAppIndex }
                    ?: apps.firstOrNull { it.desktop }
                    ?: apps.firstOrNull()
                val plan = startSunshineSession(
                    base,
                    buildReadableSunshineStreamRequest(
                        app = app,
                        displayId = displayId,
                        useWifiProfile = wifiConnected && !forceLowStreamProfile,
                        useLanProfile = lanConnected && !forceLowStreamProfile,
                        preferAv1 = !forceH264Stream,
                    ),
                    token = hostAgentToken,
                )
                val nextCatalog = fetchSunshineCatalog(base, token = hostAgentToken)
                status = nextCatalog.status
                apps = nextCatalog.apps
                selectedDisplayId = nextCatalog.status.selectedDisplayId ?: displayId
                plan
            }
                .onSuccess { plan -> launchPlan = plan }
                .onFailure { error = it.message ?: "display switch failed" }
            actionInFlight = null
        }
    }

    fun wakeMac() {
        val targets = status?.wake?.targets.orEmpty().ifEmpty { cachedWakeTargets }
        scope.launch {
            actionInFlight = "WAKE"
            error = null
            runCatching { sendWakeOnLan(targets) }
                .onSuccess { sent ->
                    if (sent <= 0) error = "No Wake-on-LAN target is available yet"
                    delay(900)
                    streamError = null
                    error = null
                    streamAutoReconnectAttempted = false
                    autoConnectAttempted = false
                    refreshKey += 1
                }
                .onFailure { error = it.message ?: "wake failed" }
            actionInFlight = null
        }
    }

    fun closeAndDismiss() {
        val base = hostAgentBase
        scope.launch {
            stopLocalStream()
            streamError = null
            if (base != null) {
                actionInFlight = "CLOSE"
                closeRemoteSession(base)
                actionInFlight = null
            }
            onDismiss()
        }
    }

    LaunchedEffect(hostAgentBase, hostAgentToken, refreshKey) {
        refresh()
    }

    LaunchedEffect(streamStats?.decodedFrames) {
        if ((streamStats?.decodedFrames ?: 0L) > 0L) {
            streamAutoReconnectAttempted = false
        }
    }

    LaunchedEffect(status, apps, selectedDisplayId, launchPlan, actionInFlight, loading) {
        if (hostAgentBase == null || autoConnectAttempted || loading || actionInFlight != null || launchPlan != null) {
            return@LaunchedEffect
        }
        if (status != null) {
            autoConnectAttempted = true
            connectRemote()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    appVisible = true
                    streamLeaseActive = true
                }
                Lifecycle.Event.ON_STOP -> appVisible = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(context, desktopRotated) {
        val activity = context.findActivity()
        activity?.requestedOrientation = if (desktopRotated) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        onDispose {
            if (desktopRotated) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    DisposableEffect(context) {
        val window = context.findActivity()?.window
        if (window == null) {
            onDispose { }
        } else {
            val previousSoftInputMode = window.attributes.softInputMode
            val hadKeepScreenOn =
                (window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
            val nextSoftInputMode =
                (previousSoftInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            window.setSoftInputMode(nextSoftInputMode)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose {
                window.setSoftInputMode(previousSoftInputMode)
                if (!hadKeepScreenOn) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
    }

    LaunchedEffect(appVisible, launchPlan) {
        if (appVisible) {
            streamLeaseActive = true
            return@LaunchedEffect
        }
        if (launchPlan == null) {
            return@LaunchedEffect
        }
        delay(REMOTE_STREAM_BACKGROUND_GRACE_MS)
        streamLeaseActive = false
        streamStats = null
        streamError = null
    }

    DisposableEffect(hostAgentBase, launchPlan, streamSurface, streamLeaseActive) {
        val base = hostAgentBase
        val plan = launchPlan
        val surface = streamSurface
        if (!streamLeaseActive || base == null || plan == null || surface == null || !surface.isValid) {
            onDispose { }
        } else {
            val generation = streamGeneration + 1
            streamGeneration = generation
            val stream = SunshineVideoStream.start(
                base = base,
                plan = plan,
                surface = surface,
                onStats = { stats ->
                    scope.launch {
                        if (streamGeneration == generation) {
                            streamStats = stats
                            if (stats.stage != "failed") {
                                streamError = null
                            }
                        }
                    }
                },
                onError = { throwable ->
                    scope.launch {
                        if (streamGeneration == generation) {
                            streamError = throwable.message ?: "native stream failed"
                            val hadDecodedFrames = (streamStats?.decodedFrames ?: 0L) > 0L
                            val failedCodec = streamStats?.codec ?: launchPlan?.codec.orEmpty()
                            when {
                                !hadDecodedFrames &&
                                    failedCodec.contains("AV1", ignoreCase = true) &&
                                    !forceH264Stream -> {
                                    forceH264Stream = true
                                }
                                !hadDecodedFrames && wifiConnected && !forceLowStreamProfile -> {
                                    forceLowStreamProfile = true
                                }
                            }
                            stopLocalStream()
                            if (appVisible && !streamAutoReconnectAttempted) {
                                streamAutoReconnectAttempted = true
                                connectRemote()
                            }
                        }
                    }
                },
            )
            activeStream = stream
            onDispose {
                if (streamGeneration == generation && activeStream === stream) {
                    activeStream = null
                }
                stream.close()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (hostAgentBase == null) {
            RemoteUnavailable("NO ACTIVE DEVICE")
            return@Box
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            val wakeTargets = status?.wake?.targets.orEmpty().ifEmpty { cachedWakeTargets }
            val currentError = streamError ?: error
            val shouldShowWake = wakeTargets.isNotEmpty() &&
                (status?.running == false || status?.apiAvailable == false)
            RemoteTopBar(
                status = status,
                displays = status?.displays.orEmpty(),
                selectedDisplayId = selectedDisplayId ?: status?.selectedDisplayId,
                streamStats = streamStats,
                error = currentError,
                isAsleep = shouldShowWake,
                desktopRotated = desktopRotated,
                onToggleDesktopRotation = { desktopRotated = !desktopRotated },
                onSelectDisplay = { selectDisplay(it) },
                onClose = { closeAndDismiss() },
                modifier = Modifier.fillMaxWidth(),
            )

            RemoteStreamSurface(
                deviceName = deviceName,
                hostAgentBase = hostAgentBase,
                hostAgentToken = hostAgentToken,
                status = status,
                selectedApp = apps.firstOrNull { it.index == selectedAppIndex },
                launchPlan = launchPlan,
                streamStats = streamStats,
                streamInput = activeStream,
                desktopRotated = desktopRotated,
                imeWindowMetrics = imeWindowMetrics,
                loading = loading,
                actionInFlight = actionInFlight,
                error = currentError,
                showWakeAction = shouldShowWake,
                onWake = { wakeMac() },
                onReconnect = {
                    streamAutoReconnectAttempted = false
                    connectRemote()
                },
                onSurfaceChanged = { surface -> streamSurface = surface },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun RemoteStreamSurface(
    deviceName: String,
    hostAgentBase: String?,
    hostAgentToken: String?,
    status: SunshineStatus?,
    selectedApp: SunshineApp?,
    launchPlan: SunshineLaunchPlan?,
    streamStats: SunshineStreamStats?,
    streamInput: SunshineVideoStream?,
    desktopRotated: Boolean,
    imeWindowMetrics: RemoteImeWindowMetrics,
    loading: Boolean,
    actionInFlight: String?,
    error: String?,
    showWakeAction: Boolean,
    onWake: () -> Unit,
    onReconnect: () -> Unit,
    onSurfaceChanged: (Surface?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = BclawTheme.typography
    val configuration = LocalConfiguration.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val canvasRotated = desktopRotated && configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    var imeTargetView by remember { mutableStateOf<RemoteImeInputView?>(null) }
    var aiInputEnabled by remember { mutableStateOf(false) }
    var aiInputGenerationCount by remember { mutableIntStateOf(0) }
    val aiInputMutex = remember { Mutex() }
    val keyboardVisible = imeWindowMetrics.visible
    var holdEngaged by remember { mutableStateOf(false) }
    val stickyModifiers = remember { RemoteStickyModifiers() }
    val aiInputBusy = aiInputGenerationCount > 0
    var popupAnchorSize by remember { mutableStateOf(IntSize.Zero) }
    var surfaceBoundsInWindow by remember { mutableStateOf(Rect.Zero) }
    var viewportSize by remember(launchPlan?.width, launchPlan?.height, launchPlan?.displayId, canvasRotated) {
        mutableStateOf(IntSize.Zero)
    }
    var renderSize by remember(launchPlan?.width, launchPlan?.height, launchPlan?.displayId, canvasRotated) {
        mutableStateOf(IntSize.Zero)
    }
    var viewportScale by remember(launchPlan?.width, launchPlan?.height, launchPlan?.displayId, canvasRotated) {
        mutableStateOf(REMOTE_VIEW_MIN_SCALE)
    }
    var viewportOffset by remember(launchPlan?.width, launchPlan?.height, launchPlan?.displayId, canvasRotated) {
        mutableStateOf(Offset.Zero)
    }
    val showOverlay = error != null ||
        loading ||
        actionInFlight != null ||
        launchPlan == null ||
        streamStats == null ||
        streamStats.decodedFrames <= 0L ||
        showWakeAction
    val showControls = launchPlan != null && error == null && !showWakeAction
    val latestStreamInput by rememberUpdatedState(streamInput)

    val viewportHeightPx = remoteImeConstrainedViewportHeightPx(
        isPortrait = isPortrait,
        imeWindowMetrics = imeWindowMetrics,
        surfaceBoundsInWindow = surfaceBoundsInWindow,
        surfaceSize = popupAnchorSize,
    )
    val density = LocalDensity.current
    val viewportHeightModifier = if (viewportHeightPx > 0) {
        Modifier.height(with(density) { viewportHeightPx.toDp() })
    } else {
        Modifier.fillMaxSize()
    }
    val imeBottomInsetDp = with(density) {
        if (popupAnchorSize.height > 0 && viewportHeightPx > 0) {
            (popupAnchorSize.height - viewportHeightPx).coerceAtLeast(0).toDp()
        } else {
            0.dp
        }
    }

    fun showRemoteKeyboard() {
        imeTargetView?.streamInput = streamInput
        imeTargetView?.showKeyboard()
    }

    fun sendRemoteImeText(text: String) {
        val base = hostAgentBase
        val stream = streamInput ?: return
        // Accessory-bar modifiers must apply to whatever the soft IME commits next.
        // For a single mappable char ("v", " ", "a") with a modifier active, route as a
        // keyboard shortcut (Ctrl+V / Cmd+Space). Multi-char or unmappable text falls
        // through to UTF-8 with the modifier dropped — still consume one-shot mods so
        // they don't linger and surprise the next keystroke.
        val modMask = stickyModifiers.mask()
        if (modMask != SunshineModifier.NONE) {
            if (text.length == 1) {
                val vk = remoteCharToSunshineKey(text[0])
                if (vk != null) {
                    stream.sendKeyboardChord(keyCode = vk, modifiers = modMask)
                    stickyModifiers.consumeOneShot()
                    return
                }
            }
            stream.sendUtf8Text(text)
            stickyModifiers.consumeOneShot()
            return
        }
        if (!aiInputEnabled || text.isBlank() || base == null) {
            stream.sendUtf8Text(text)
            return
        }
        scope.launch {
            aiInputGenerationCount += 1
            try {
                aiInputMutex.withLock {
                    val output = runCatching {
                        generateRemoteAiInputText(base, text, token = hostAgentToken).text
                    }.getOrElse {
                        text
                    }
                    if (output.isNotEmpty() && latestStreamInput === stream) {
                        stream.sendUtf8Text(output)
                    }
                }
            } finally {
                aiInputGenerationCount = (aiInputGenerationCount - 1).coerceAtLeast(0)
            }
        }
    }

    fun sendRemoteImeKeyEvent(event: KeyEvent, keyCode: Int, eventModifiers: Int): Boolean {
        val stream = streamInput ?: return false
        val modMask = stickyModifiers.mask()
        if (
            event.action != KeyEvent.ACTION_DOWN ||
            modMask == SunshineModifier.NONE ||
            remoteSunshineKeyIsModifier(keyCode)
        ) {
            return false
        }
        stream.sendKeyboardChord(keyCode = keyCode, modifiers = modMask or eventModifiers)
        stickyModifiers.consumeOneShot()
        return true
    }

    DisposableEffect(lifecycleOwner, imeTargetView) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                imeTargetView?.hideKeyboard()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(viewportSize, renderSize, canvasRotated) {
        viewportOffset = clampRemoteViewportOffset(
            offset = viewportOffset,
            scale = viewportScale,
            renderSize = renderSize,
            viewportSize = viewportSize,
        )
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .border(1.dp, Color(0xFF26261C))
            .clipToBounds()
            .onSizeChanged { popupAnchorSize = it }
            .onGloballyPositioned { surfaceBoundsInWindow = it.boundsInWindow() },
        contentAlignment = Alignment.TopCenter,
    ) {
        if (launchPlan != null) {
            BoxWithConstraints(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .then(viewportHeightModifier)
                    .clipToBounds()
                    .onSizeChanged { viewportSize = it },
                contentAlignment = Alignment.Center,
            ) {
                val streamAspect = launchPlan.width.toFloat() / launchPlan.height.toFloat()
                val visualAspect = if (canvasRotated) 1f / streamAspect else streamAspect
                val containerAspect = if (maxHeight.value > 0f) {
                    maxWidth.value / maxHeight.value
                } else {
                    visualAspect
                }
                val visualWidth = if (containerAspect > visualAspect) {
                    maxHeight * visualAspect
                } else {
                    maxWidth
                }
                val visualHeight = if (containerAspect > visualAspect) {
                    maxHeight
                } else {
                    maxWidth / visualAspect
                }
                val sourceWidth = if (canvasRotated) {
                    visualHeight
                } else {
                    visualWidth
                }
                val sourceHeight = if (canvasRotated) {
                    visualWidth
                } else {
                    visualHeight
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(visualWidth)
                            .height(visualHeight)
                            .onSizeChanged { renderSize = it }
                            .graphicsLayer {
                                scaleX = viewportScale
                                scaleY = viewportScale
                                translationX = viewportOffset.x
                                translationY = viewportOffset.y
                            },
                    ) {
                        Box(
                            modifier = Modifier
                                .requiredWidth(sourceWidth)
                                .requiredHeight(sourceHeight)
                                .align(Alignment.Center)
                                .graphicsLayer {
                                    rotationZ = if (canvasRotated) 90f else 0f
                                },
                        ) {
                            key(launchPlan.width, launchPlan.height, launchPlan.displayId) {
                                AndroidView(
                                    factory = { context ->
                                        TextureView(context).apply {
                                            val listener = object : TextureView.SurfaceTextureListener {
                                                private var outputSurface: Surface? = null

                                                private fun publish(texture: SurfaceTexture) {
                                                    texture.setDefaultBufferSize(launchPlan.width, launchPlan.height)
                                                    val surface = outputSurface ?: Surface(texture).also { outputSurface = it }
                                                    onSurfaceChanged(surface)
                                                }

                                                override fun onSurfaceTextureAvailable(
                                                    surface: SurfaceTexture,
                                                    width: Int,
                                                    height: Int,
                                                ) {
                                                    publish(surface)
                                                }

                                                override fun onSurfaceTextureSizeChanged(
                                                    surface: SurfaceTexture,
                                                    width: Int,
                                                    height: Int,
                                                ) {
                                                    publish(surface)
                                                }

                                                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                                                    onSurfaceChanged(null)
                                                    outputSurface?.release()
                                                    outputSurface = null
                                                    return true
                                                }

                                                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                                            }
                                            surfaceTextureListener = listener
                                            if (isAvailable) {
                                                surfaceTexture?.let {
                                                    listener.onSurfaceTextureAvailable(it, width, height)
                                                }
                                            }
                                        }
                                    },
                                    update = { textureView ->
                                        textureView.surfaceTexture?.setDefaultBufferSize(launchPlan.width, launchPlan.height)
                                    },
                                    modifier = Modifier
                                        .fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
        }
        if (launchPlan != null) {
            AndroidView(
                factory = { context ->
                    RemoteImeInputView(context).also { target ->
                        imeTargetView = target
                    }
                },
                update = { target ->
                    target.streamInput = streamInput
                    target.textCommitInterceptor = { text -> sendRemoteImeText(text) }
                    target.keyEventInterceptor = { event, keyCode, modifiers ->
                        sendRemoteImeKeyEvent(event, keyCode, modifiers)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size(1.dp)
                    .graphicsLayer { alpha = 0f },
            )
            RemoteNativeOverlay(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .then(viewportHeightModifier)
                    .zIndex(1f),
                wrapContent = false,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(
                            launchPlan.width,
                            launchPlan.height,
                            launchPlan.displayId,
                            canvasRotated,
                            renderSize,
                            viewportSize,
                            status?.hostPlatform,
                            hostAgentBase,
                            streamInput,
                            imeTargetView,
                        ) {
                            detectRemoteZoomViewportGestures(
                                streamInput = streamInput,
                                launchPlan = launchPlan,
                                desktopRotated = canvasRotated,
                                renderSize = renderSize,
                                viewportSize = viewportSize,
                                viewportScale = { viewportScale },
                                viewportOffset = { viewportOffset },
                                onViewportTransform = { nextScale, nextOffset ->
                                    viewportScale = nextScale
                                    viewportOffset = nextOffset
                                },
                                view = view,
                                onKeyboardGesture = { showRemoteKeyboard() },
                                onHoldChange = { holdEngaged = it },
                            )
                        },
                )
            }
            if (holdEngaged) {
                // Cyan border telegraphs Hold engaged: 1-finger drag now drives the
                // host cursor (LMB held down) instead of panning the local viewport.
                RemoteNativeOverlay(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .then(viewportHeightModifier)
                        .zIndex(2f),
                    wrapContent = false,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(3.dp, MetroCyan),
                    )
                }
            }
        }
        if (showControls && keyboardVisible) {
            // Keyboard up: accessory keys above the soft IME for one-thumb access.
            RemoteNativeOverlay(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp)
                    .padding(bottom = imeBottomInsetDp)
                    .zIndex(4f),
                wrapContent = false,
            ) {
                RemoteImeAccessoryBar(
                    streamInput = streamInput,
                    hostPlatform = status?.hostPlatform,
                    aiEnabled = aiInputEnabled,
                    aiBusy = aiInputBusy,
                    onToggleAi = { aiInputEnabled = !aiInputEnabled },
                    stickyModifiers = stickyModifiers,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (showOverlay) {
            RemoteNativeOverlay(
                modifier = Modifier
                    .align(Alignment.Center)
                    .zIndex(5f),
            ) {
                if (showWakeAction) {
                    Box(
                        modifier = Modifier
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = 320.dp)
                                .background(Color(0xFF0A0A0A))
                                .border(1.dp, MetroOrange.copy(alpha = 0.55f))
                                .padding(horizontal = 22.dp, vertical = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(MetroOrange),
                                )
                                Text(
                                    text = "ASLEEP",
                                    style = type.meta,
                                    color = MetroOrange,
                                )
                            }
                            Text(
                                text = deviceName,
                                style = type.h3,
                                color = Color(0xFFF4F0E3),
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = if (actionInFlight == "WAKE")
                                    "Sending wake-on-LAN packet…"
                                else
                                    "Send a magic packet to bring it back online.",
                                style = type.bodySmall,
                                color = Color(0xFF9A9788),
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(4.dp))
                            RemoteControlButton(
                                label = if (actionInFlight == "WAKE") "WAKING…" else "WAKE",
                                accent = true,
                                onClick = onWake,
                                modifier = Modifier.fillMaxWidth(),
                                height = 44,
                            )
                            if (error != null) {
                                Text(
                                    text = error,
                                    style = type.micro,
                                    color = Color(0xFFFF6B5A),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                } else {
                    val overlayWidth = with(LocalDensity.current) {
                        (popupAnchorSize.width - 44).coerceAtLeast(240).toDp()
                    }
                    Column(
                        modifier = Modifier
                            .width(overlayWidth)
                            .padding(horizontal = 22.dp),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "REMOTE DESKTOP",
                            style = type.meta,
                            color = MetroCyan,
                        )
                        Text(
                            text = when {
                                loading -> "Checking $deviceName"
                                actionInFlight != null -> when (actionInFlight) {
                                    "WAKE" -> "Waking $deviceName"
                                    "DISPLAY" -> "Switching display"
                                    else -> "Connecting to $deviceName"
                                }
                                error != null -> "Connection failed"
                                streamStats?.stage == "streaming" -> "Live desktop is rendering"
                                streamStats != null -> "Remote desktop is ${streamStats.stage}"
                                launchPlan != null -> "Opening ${launchPlan.displayName ?: "desktop"}"
                                status?.ready == true && status.paired == true -> "Ready to go live"
                                status?.running == false -> "Remote desktop is offline"
                                status?.apiAvailable == false -> "Remote desktop is unreachable"
                                else -> "Waiting for remote desktop"
                            },
                            style = type.mono,
                            color = Color(0xFFF4F0E3),
                        )
                        Text(
                            text = when {
                                error != null -> error
                                streamStats != null -> "${streamStats.codec} · ${streamStats.decodedFrames} frames"
                                launchPlan != null -> "${launchPlan.width}x${launchPlan.height}@${launchPlan.fps}"
                                selectedApp != null -> "Preparing ${selectedApp.name}"
                                else -> "Choose a display to go live."
                            },
                            style = type.micro,
                            color = if (error == null) Color(0xFF76746A) else Color(0xFFFF6B5A),
                        )
                        if (error != null && actionInFlight == null) {
                            Spacer(Modifier.height(4.dp))
                            RemoteControlButton(
                                label = "RECONNECT",
                                accent = true,
                                onClick = onReconnect,
                                modifier = Modifier.fillMaxWidth(),
                                height = 38,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.RemoteNativeOverlay(
    modifier: Modifier = Modifier,
    wrapContent: Boolean = true,
    content: @Composable () -> Unit,
) {
    val parentComposition = rememberCompositionContext()
    val latestContent by rememberUpdatedState(content)
    val overlayModifier = if (wrapContent) modifier.wrapContentSize() else modifier
    AndroidView(
        factory = { context ->
            ComposeView(context).apply {
                setParentCompositionContext(parentComposition)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setContent {
                    latestContent()
                }
            }
        },
        update = { composeView ->
            composeView.setParentCompositionContext(parentComposition)
        },
        modifier = overlayModifier,
    )
}

internal data class RemoteImeWindowMetrics(
    val bottomPx: Int = 0,
    val rootHeightPx: Int = 0,
    val visible: Boolean = false,
)

@Composable
private fun rememberRemoteImeWindowMetrics(): RemoteImeWindowMetrics {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var metrics by remember { mutableStateOf(RemoteImeWindowMetrics()) }

    DisposableEffect(context, view, lifecycleOwner) {
        val localRoot = view.rootView
        val activityRoot = context.findActivity()?.window?.decorView
        val roots = listOfNotNull(localRoot, activityRoot)
            .distinctBy { System.identityHashCode(it) }

        fun resetMetrics() {
            metrics = RemoteImeWindowMetrics(
                rootHeightPx = roots.maxOfOrNull { it.height } ?: 0,
            )
        }

        fun updateMetrics(extraMetrics: RemoteImeWindowMetrics? = null) {
            metrics = (roots.map { it.remoteImeWindowMetrics() } + listOfNotNull(extraMetrics))
                .maxWithOrNull(
                    compareBy<RemoteImeWindowMetrics> { if (it.visible) 1 else 0 }
                        .thenBy { it.bottomPx }
                        .thenBy { it.rootHeightPx },
                )
                ?: RemoteImeWindowMetrics()
        }

        fun requestImeMetricsRefresh() {
            roots.forEach { ViewCompat.requestApplyInsets(it) }
            updateMetrics()
        }

        val registrations = roots.map { root ->
            val observer = root.viewTreeObserver
            val listener = ViewTreeObserver.OnGlobalLayoutListener { updateMetrics() }
            observer.addOnGlobalLayoutListener(listener)
            Triple(root, observer, listener)
        }
        val animationRoots = roots.map { root ->
            val callback = object : WindowInsetsAnimationCompat.Callback(
                WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE,
            ) {
                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>,
                ): WindowInsetsCompat {
                    updateMetrics(root.remoteImeWindowMetrics(insets))
                    return insets
                }

                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    updateMetrics()
                }
            }
            ViewCompat.setWindowInsetsAnimationCallback(root, callback)
            root
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> resetMetrics()
                Lifecycle.Event.ON_START,
                Lifecycle.Event.ON_RESUME -> view.post { requestImeMetricsRefresh() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        view.post {
            requestImeMetricsRefresh()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            animationRoots.forEach { root ->
                ViewCompat.setWindowInsetsAnimationCallback(root, null)
            }
            registrations.forEach { (root, observer, listener) ->
                if (observer.isAlive) {
                    observer.removeOnGlobalLayoutListener(listener)
                } else {
                    root.viewTreeObserver.removeOnGlobalLayoutListener(listener)
                }
            }
        }
    }

    return metrics
}

private fun View.remoteImeWindowMetrics(): RemoteImeWindowMetrics {
    val rootHeight = height
    val insets = rootWindowInsets ?: return RemoteImeWindowMetrics(rootHeightPx = rootHeight)
    val compat = WindowInsetsCompat.toWindowInsetsCompat(insets, this)
    return remoteImeWindowMetrics(compat)
}

private fun View.remoteImeWindowMetrics(insets: WindowInsetsCompat): RemoteImeWindowMetrics {
    return RemoteImeWindowMetrics(
        bottomPx = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom,
        rootHeightPx = height,
        visible = insets.isVisible(WindowInsetsCompat.Type.ime()),
    )
}

internal fun remoteImeConstrainedViewportHeightPx(
    isPortrait: Boolean,
    imeWindowMetrics: RemoteImeWindowMetrics,
    surfaceBoundsInWindow: Rect,
    surfaceSize: IntSize,
): Int {
    if (
        surfaceSize.height <= 0 ||
        !isPortrait ||
        !imeWindowMetrics.visible ||
        imeWindowMetrics.bottomPx <= 0 ||
        imeWindowMetrics.rootHeightPx <= 0 ||
        surfaceBoundsInWindow.width <= 0f ||
        surfaceBoundsInWindow.height <= 0f
    ) {
        return surfaceSize.height
    }

    val keyboardTopInWindow = imeWindowMetrics.rootHeightPx - imeWindowMetrics.bottomPx.toFloat()
    return (keyboardTopInWindow - surfaceBoundsInWindow.top)
        .roundToInt()
        .coerceIn(0, surfaceSize.height)
}

private const val REMOTE_VIEW_MIN_SCALE = 1f
private const val REMOTE_VIEW_MAX_SCALE = 4f

internal fun clampRemoteViewportOffset(
    offset: Offset,
    scale: Float,
    renderSize: IntSize,
    viewportSize: IntSize,
): Offset {
    if (
        renderSize.width <= 0 ||
        renderSize.height <= 0 ||
        viewportSize.width <= 0 ||
        viewportSize.height <= 0 ||
        scale <= 0f
    ) {
        return Offset.Zero
    }
    val scaledRenderWidth = renderSize.width * scale
    val scaledRenderHeight = renderSize.height * scale
    val maxX = max(0f, (scaledRenderWidth - viewportSize.width) / 2f)
    val maxY = max(0f, (scaledRenderHeight - viewportSize.height) / 2f)
    return Offset(
        x = offset.x.coerceIn(-maxX, maxX),
        y = offset.y.coerceIn(-maxY, maxY),
    )
}

internal data class RemoteViewportTransform(
    val scale: Float,
    val offset: Offset,
)

internal fun remoteViewportTransformForGesture(
    oldScale: Float,
    oldOffset: Offset,
    zoom: Float,
    pan: Offset,
    centroid: Offset,
    renderSize: IntSize,
    viewportSize: IntSize,
): RemoteViewportTransform? {
    if (
        oldScale <= 0f ||
        !oldScale.isFinite() ||
        !oldOffset.x.isFinite() ||
        !oldOffset.y.isFinite() ||
        !zoom.isFinite() ||
        zoom <= 0f ||
        !pan.x.isFinite() ||
        !pan.y.isFinite() ||
        !centroid.x.isFinite() ||
        !centroid.y.isFinite() ||
        viewportSize.width <= 0 ||
        viewportSize.height <= 0 ||
        renderSize.width <= 0 ||
        renderSize.height <= 0
    ) {
        return null
    }

    val nextScale = (oldScale * zoom).coerceIn(REMOTE_VIEW_MIN_SCALE, REMOTE_VIEW_MAX_SCALE)
    val viewportCenter = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
    val centroidFromCenter = centroid - viewportCenter
    val scaledOffset = (oldOffset + centroidFromCenter) * (nextScale / oldScale) -
        centroidFromCenter +
        pan
    return RemoteViewportTransform(
        scale = nextScale,
        offset = clampRemoteViewportOffset(
            offset = scaledOffset,
            scale = nextScale,
            renderSize = renderSize,
            viewportSize = viewportSize,
        ),
    )
}

private suspend fun PointerInputScope.detectRemoteZoomViewportGestures(
    streamInput: SunshineVideoStream?,
    launchPlan: SunshineLaunchPlan,
    desktopRotated: Boolean,
    renderSize: IntSize,
    viewportSize: IntSize,
    viewportScale: () -> Float,
    viewportOffset: () -> Offset,
    onViewportTransform: (Float, Offset) -> Unit,
    view: View,
    onKeyboardGesture: () -> Unit,
    onHoldChange: (Boolean) -> Unit,
) {
    val tapSlopPx = TRACKPAD_TAP_SLOP_DP.dp.toPx()
    val scrollLockSlopPx = TRACKPAD_SCROLL_LOCK_SLOP_DP.dp.toPx()
    val pinchLockSlopPx = TRACKPAD_PINCH_LOCK_SLOP_DP.dp.toPx()
    val doubleClickProximityPx = REMOTE_DOUBLECLICK_PROXIMITY_DP.dp.toPx()
    // System long-press (~500ms) feels sluggish here — the user is voluntarily
    // engaging hold, not avoiding accidental triggers. 280ms is fast enough to
    // feel responsive without firing on incidental still moments.
    val longPressTimeoutMs = REMOTE_HOLD_LONG_PRESS_TIMEOUT_MS
    coroutineScope {
        var scrollMomentumJob: Job? = null
        // The "tombstone" — set true after a Tap event, consumed by a TapTap event,
        // destroyed by anything else (drag, multi-finger, etc.). Per the user's
        // model, TapTap is ONLY recognized when this is true. So when it's false,
        // a Tap is unambiguous and the cursor moves with zero latency. When it's
        // true, a fresh single-finger touch+release has to wait the double-tap
        // window to disambiguate Tap-as-refine vs first-of-TapTap.
        var tombstoneSet = false

        awaitEachGesture {
            val first = awaitFirstDown(requireUnconsumed = false)
            scrollMomentumJob?.cancel()
            scrollMomentumJob = null

            val downTime = first.uptimeMillis
            val initialPositions = mutableMapOf(first.id to first.position)
            var lastUptime = downTime
            var maxDisplacement = 0f
            var sawSecondPointer = false
            var secondPointerDownTime: Long? = null
            var sawThirdPointer = false
            var thirdPointerDownTime: Long? = null
            var stillPressed = true
            // 1-finger long-press → Hold mode. While engaged, finger movement drives
            // the host cursor relatively (LMB held down for drag-select etc.) instead
            // of panning the local viewport. Released on lift.
            var inHold = false
            // Sub-pixel residue carried across events while in hold so slow finger
            // movement isn't lost to integer rounding when the multiplier is < 1.
            var holdRemainderX = 0f
            var holdRemainderY = 0f

            // 2-finger gesture state — classifier disambiguates pinch (local zoom) vs
            // scroll (wheel events to host). 1-finger drag still pans the local viewport.
            var twoFingerMode: RemoteTrackpadTwoFingerGesture? = null
            var initialTwoFingerSpan: Float? = null
            var initialTwoFingerCentroid: Offset? = null
            var lastScrollCentroid: Offset? = null
            var lastScrollUptime: Long = downTime
            var scrollRemainder = Offset.Zero
            var scrollMomentumVelocity = Offset.Zero

            while (stillPressed) {
                val event = awaitPointerEvent()
                val pressedChanges = event.changes.filter { it.pressed }
                val eventUptime = event.changes.maxOfOrNull { it.uptimeMillis } ?: lastUptime

                if (pressedChanges.size >= 2 && !sawSecondPointer) {
                    sawSecondPointer = true
                    secondPointerDownTime = eventUptime
                }
                if (pressedChanges.size >= 3 && !sawThirdPointer) {
                    sawThirdPointer = true
                    thirdPointerDownTime = eventUptime
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                }
                for (change in pressedChanges) {
                    initialPositions.putIfAbsent(change.id, change.position)
                }
                for (change in event.changes) {
                    val startPosition = initialPositions[change.id]
                    if (startPosition != null) {
                        maxDisplacement = max(maxDisplacement, (change.position - startPosition).getDistance())
                    }
                }

                if (pressedChanges.size >= 3) {
                    // 3-finger reserved for popover tap; ignore motion here.
                } else if (pressedChanges.size == 2 && streamInput != null) {
                    val twoFingerChanges = pressedChanges.take(2)
                    val currentSpan = twoFingerSpan(twoFingerChanges)
                    val currentCentroid = twoFingerCentroid(twoFingerChanges)
                    val previousTwoFingerMode = twoFingerMode
                    if (initialTwoFingerSpan == null && currentSpan != null) {
                        initialTwoFingerSpan = currentSpan
                    }
                    if (initialTwoFingerCentroid == null && currentCentroid != null) {
                        initialTwoFingerCentroid = currentCentroid
                    }
                    val initialSpan = initialTwoFingerSpan
                    val cumulativeZoom = if (currentSpan != null && initialSpan != null && initialSpan > 0f) {
                        currentSpan / initialSpan
                    } else 1f
                    if (
                        twoFingerMode == null &&
                        initialSpan != null &&
                        currentSpan != null &&
                        currentCentroid != null
                    ) {
                        val initialCentroid = initialTwoFingerCentroid ?: currentCentroid
                        twoFingerMode = classifyRemoteTrackpadTwoFingerGesture(
                            panDistance = (currentCentroid - initialCentroid).getDistance(),
                            spanDelta = abs(currentSpan - initialSpan),
                            cumulativeZoom = cumulativeZoom,
                            scrollSlopPx = scrollLockSlopPx,
                            pinchSlopPx = pinchLockSlopPx,
                        )
                    }
                    if (
                        twoFingerMode == RemoteTrackpadTwoFingerGesture.Scroll &&
                        previousTwoFingerMode != RemoteTrackpadTwoFingerGesture.Scroll
                    ) {
                        lastScrollUptime = secondPointerDownTime ?: downTime
                        lastScrollCentroid = initialTwoFingerCentroid ?: currentCentroid
                        scrollRemainder = Offset.Zero
                        scrollMomentumVelocity = Offset.Zero
                    }

                    when (twoFingerMode) {
                        RemoteTrackpadTwoFingerGesture.Zoom -> {
                            if (
                                viewportSize.width > 0 &&
                                viewportSize.height > 0 &&
                                renderSize.width > 0 &&
                                renderSize.height > 0
                            ) {
                                remoteViewportTransformForGesture(
                                    oldScale = viewportScale(),
                                    oldOffset = viewportOffset(),
                                    zoom = event.calculateZoom(),
                                    pan = Offset.Zero,
                                    centroid = pressedChanges.centroid(),
                                    renderSize = renderSize,
                                    viewportSize = viewportSize,
                                )?.let { onViewportTransform(it.scale, it.offset) }
                            }
                        }
                        RemoteTrackpadTwoFingerGesture.Scroll -> {
                            val previousCentroid = lastScrollCentroid ?: initialTwoFingerCentroid ?: currentCentroid
                            val pan = if (currentCentroid != null && previousCentroid != null) {
                                currentCentroid - previousCentroid
                            } else {
                                event.calculatePan()
                            }
                            lastScrollCentroid = currentCentroid
                            val remotePan = rotateRemoteDelta(pan, desktopRotated)
                            val scrollDelta = remotePanToScrollDelta(remotePan)
                            scrollRemainder = sendRemoteScrollDelta(
                                streamInput = streamInput,
                                delta = scrollDelta,
                                remainder = scrollRemainder,
                            )

                            val elapsedSeconds = ((eventUptime - lastScrollUptime).coerceAtLeast(1L)).toFloat() / 1000f
                            val instantVelocity = scrollDelta / elapsedSeconds
                            scrollMomentumVelocity = blendRemoteScrollMomentumVelocity(
                                current = scrollMomentumVelocity,
                                sample = instantVelocity,
                            )
                            lastScrollUptime = eventUptime
                        }
                        null -> Unit
                    }
                } else if (pressedChanges.size == 1 && !sawSecondPointer) {
                    if (inHold && streamInput != null) {
                        // Held — relative cursor drag with speed-adaptive acceleration,
                        // additionally inversely scaled by viewport zoom. Reasoning:
                        // user-intent tracks the lens. Zoomed in = "I want precision"
                        // (looking at characters), so multiplier shrinks. Zoomed out
                        // = "I want reach" (selecting a lot at a glance), so multiplier
                        // grows. The inverse relationship keeps the cursor's
                        // *visual on-screen speed* roughly constant regardless of zoom.
                        val pan = event.calculatePan()
                        val dtMs = (eventUptime - lastUptime).coerceAtLeast(1L).toFloat()
                        val speedPxPerMs = pan.getDistance() / dtMs
                        val zoomFactor = (1f / viewportScale().coerceAtLeast(0.1f))
                            .coerceIn(0.3f, 4f)
                        val mult = holdMouseMultiplier(speedPxPerMs, launchPlan) * zoomFactor
                        val remotePan = rotateRemoteDelta(pan, desktopRotated)
                        val dxF = remotePan.x * mult + holdRemainderX
                        val dyF = remotePan.y * mult + holdRemainderY
                        val mx = dxF.roundToInt()
                        val my = dyF.roundToInt()
                        holdRemainderX = dxF - mx
                        holdRemainderY = dyF - my
                        if (mx != 0 || my != 0) {
                            streamInput.sendMouseMove(mx, my)
                        }
                    } else {
                        // Engage Hold if the finger has been still long enough.
                        val held = eventUptime - downTime
                        if (
                            !inHold &&
                            streamInput != null &&
                            held >= longPressTimeoutMs &&
                            maxDisplacement < tapSlopPx
                        ) {
                            inHold = true
                            streamInput.sendMouseButton(pressed = true, button = SunshineMouseButton.LEFT)
                            onHoldChange(true)
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        } else if (
                            viewportSize.width > 0 &&
                            viewportSize.height > 0 &&
                            renderSize.width > 0 &&
                            renderSize.height > 0
                        ) {
                            // 1-finger drag pans the local viewport (only meaningful zoomed in).
                            remoteViewportTransformForGesture(
                                oldScale = viewportScale(),
                                oldOffset = viewportOffset(),
                                zoom = 1f,
                                pan = event.calculatePan(),
                                centroid = pressedChanges.centroid(),
                                renderSize = renderSize,
                                viewportSize = viewportSize,
                            )?.let { onViewportTransform(it.scale, it.offset) }
                        }
                    }
                }

                event.changes.forEach { if (it.positionChanged()) it.consume() }
                lastUptime = eventUptime
                stillPressed = event.changes.any { it.pressed }
            }

            if (inHold) {
                // Hold released — drop LMB, clear indicator, skip tap recognition.
                if (streamInput != null) {
                    streamInput.sendMouseButton(pressed = false, button = SunshineMouseButton.LEFT)
                }
                onHoldChange(false)
                tombstoneSet = false
                return@awaitEachGesture
            }

            if (sawThirdPointer) {
                // 3-finger tap → summon IME. Qualifies if all fingers stayed put
                // and lifted within the keyboard-tap window.
                val thirdTapElapsed = lastUptime - (thirdPointerDownTime ?: downTime)
                if (maxDisplacement < tapSlopPx && thirdTapElapsed < TRACKPAD_KEYBOARD_TAP_TIMEOUT_MS) {
                    onKeyboardGesture()
                }
                tombstoneSet = false
                return@awaitEachGesture
            }

            if (twoFingerMode == RemoteTrackpadTwoFingerGesture.Scroll && streamInput != null) {
                val initialMomentumVelocity = startRemoteScrollMomentumVelocity(scrollMomentumVelocity)
                if (initialMomentumVelocity != Offset.Zero) {
                    scrollMomentumJob = launch {
                        runRemoteScrollMomentum(streamInput, initialMomentumVelocity)
                    }
                }
            }

            if (sawSecondPointer) {
                // 2-finger tap → right click. Qualifies if no scroll/pinch motion
                // happened (displacement under slop) and fingers lifted within the
                // tap window. Fires at cursor's current position.
                val secondaryTapElapsed = lastUptime - (secondPointerDownTime ?: downTime)
                if (
                    streamInput != null &&
                    maxDisplacement < tapSlopPx &&
                    secondaryTapElapsed < TRACKPAD_KEYBOARD_TAP_TIMEOUT_MS
                ) {
                    streamInput.sendMouseButton(pressed = true, button = SunshineMouseButton.RIGHT)
                    streamInput.sendMouseButton(pressed = false, button = SunshineMouseButton.RIGHT)
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                }
                tombstoneSet = false
                return@awaitEachGesture
            }

            val tapElapsed = lastUptime - downTime
            val firstTapQualifies = maxDisplacement < tapSlopPx && tapElapsed < TRACKPAD_TAP_TIMEOUT_MS

            if (!firstTapQualifies || streamInput == null) {
                // Drag, long-hold, stream-offline — destroys the tombstone.
                tombstoneSet = false
                return@awaitEachGesture
            }

            if (!tombstoneSet) {
                // No tombstone → TapTap can't apply (per the user's rule). This is
                // unambiguously a Tap event. Move cursor instantly with zero latency.
                moveRemoteMouseToCanvasPoint(
                    streamInput = streamInput,
                    launchPlan = launchPlan,
                    desktopRotated = desktopRotated,
                    renderSize = renderSize,
                    viewportSize = viewportSize,
                    viewportScale = viewportScale(),
                    viewportOffset = viewportOffset(),
                    point = first.position,
                )
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                tombstoneSet = true
                return@awaitEachGesture
            }

            // Tombstone set → could be Tap-refine or first-of-TapTap. Wait the
            // double-tap window for a follow-up touch.
            val secondDown = withTimeoutOrNull(CLICK_DOUBLE_TAP_WINDOW_MS) {
                awaitFirstDown(requireUnconsumed = false)
            }

            if (secondDown == null) {
                // No follow-up — first was a Tap event (refine). Move cursor;
                // tombstone refreshed (still in post-Tap state).
                moveRemoteMouseToCanvasPoint(
                    streamInput = streamInput,
                    launchPlan = launchPlan,
                    desktopRotated = desktopRotated,
                    renderSize = renderSize,
                    viewportSize = viewportSize,
                    viewportScale = viewportScale(),
                    viewportOffset = viewportOffset(),
                    point = first.position,
                )
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                tombstoneSet = true
                return@awaitEachGesture
            }

            // Follow-up touch arrived. Track its release to see if the pair
            // qualifies as a TapTap event.
            val secondDownTime = secondDown.uptimeMillis
            val secondInitialPos = secondDown.position
            var secondMaxDisplacement = 0f
            var secondLastUptime = secondDownTime
            var secondSawAdditionalPointer = false
            var secondStillPressed = true

            while (secondStillPressed) {
                val event = awaitPointerEvent()
                val pressedChanges = event.changes.filter { it.pressed }
                val eventUptime = event.changes.maxOfOrNull { it.uptimeMillis } ?: secondLastUptime

                if (pressedChanges.size >= 2) secondSawAdditionalPointer = true

                for (change in event.changes) {
                    if (change.id == secondDown.id) {
                        secondMaxDisplacement = max(
                            secondMaxDisplacement,
                            (change.position - secondInitialPos).getDistance(),
                        )
                    }
                }

                event.changes.forEach { if (it.positionChanged()) it.consume() }
                secondLastUptime = eventUptime
                secondStillPressed = event.changes.any { it.pressed }
            }

            val secondTapElapsed = secondLastUptime - secondDownTime
            val secondTapQualifies = !secondSawAdditionalPointer &&
                secondMaxDisplacement < tapSlopPx &&
                secondTapElapsed < TRACKPAD_TAP_TIMEOUT_MS

            if (secondTapQualifies) {
                // TapTap event. The second tap's screen position discriminates intent:
                //   - near the first tap → double-click (user is "clicking the same
                //     spot twice"). Fire two LMB pairs; the host OS interprets back-
                //     to-back clicks as a system double-click.
                //   - far from the first tap → commit click. The first tap was a
                //     refine; the second tap is just "fire" — single click at the
                //     cursor's tombstone position.
                val tapSeparation = (secondInitialPos - first.position).getDistance()
                val isDoubleClick = tapSeparation < doubleClickProximityPx
                streamInput.sendMouseButton(pressed = true, button = SunshineMouseButton.LEFT)
                streamInput.sendMouseButton(pressed = false, button = SunshineMouseButton.LEFT)
                if (isDoubleClick) {
                    streamInput.sendMouseButton(pressed = true, button = SunshineMouseButton.LEFT)
                    streamInput.sendMouseButton(pressed = false, button = SunshineMouseButton.LEFT)
                }
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                tombstoneSet = false
            } else {
                // Follow-up wasn't a tap (drag, multi-finger, long-hold) — treat
                // the first as a Tap event. The follow-up's intent is lost as
                // best-effort fallback.
                moveRemoteMouseToCanvasPoint(
                    streamInput = streamInput,
                    launchPlan = launchPlan,
                    desktopRotated = desktopRotated,
                    renderSize = renderSize,
                    viewportSize = viewportSize,
                    viewportScale = viewportScale(),
                    viewportOffset = viewportOffset(),
                    point = first.position,
                )
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                tombstoneSet = true
            }
        }
    }
}

private fun List<PointerInputChange>.centroid(): Offset {
    if (isEmpty()) return Offset.Zero
    var x = 0f
    var y = 0f
    for (change in this) {
        x += change.position.x
        y += change.position.y
    }
    return Offset(x / size, y / size)
}


internal fun remotePanToScrollDelta(remotePan: Offset): Offset =
    Offset(
        x = remotePan.x * DISPLAY_SCROLL_SENSITIVITY,
        y = remotePan.y * DISPLAY_SCROLL_SENSITIVITY,
    )

private fun sendRemoteScrollDelta(
    streamInput: SunshineVideoStream,
    delta: Offset,
    remainder: Offset,
): Offset {
    val nextRemainder = remainder + delta
    val units = wholeRemoteScrollUnits(nextRemainder)
    if (units != Offset.Zero) {
        sendRemoteScrollUnits(
            streamInput = streamInput,
            horizontal = units.x.toInt(),
            vertical = units.y.toInt(),
        )
    }
    return nextRemainder - units
}

internal fun wholeRemoteScrollUnits(remainder: Offset): Offset =
    Offset(
        x = remainder.x.toInt().toFloat(),
        y = remainder.y.toInt().toFloat(),
    )

internal fun unsentRemoteScrollRemainder(remainder: Offset, delta: Offset): Offset {
    val nextRemainder = remainder + delta
    return nextRemainder - wholeRemoteScrollUnits(nextRemainder)
}

private fun sendRemoteScrollUnits(
    streamInput: SunshineVideoStream,
    horizontal: Int,
    vertical: Int,
) {
    if (horizontal != 0) streamInput.sendMouseHScroll(horizontal)
    if (vertical != 0) streamInput.sendMouseScroll(vertical)
}

internal fun blendRemoteScrollMomentumVelocity(current: Offset, sample: Offset): Offset {
    if (!sample.x.isFinite() || !sample.y.isFinite()) return current
    if (sample.getDistance() < TRACKPAD_SCROLL_MOMENTUM_SAMPLE_MIN_SPEED) return current
    if (current.getDistance() < TRACKPAD_SCROLL_MOMENTUM_SAMPLE_MIN_SPEED) return sample
    return current * (1f - TRACKPAD_SCROLL_MOMENTUM_SAMPLE_WEIGHT) +
        sample * TRACKPAD_SCROLL_MOMENTUM_SAMPLE_WEIGHT
}

internal fun startRemoteScrollMomentumVelocity(velocity: Offset): Offset {
    val speed = velocity.getDistance()
    if (!speed.isFinite() || speed < TRACKPAD_SCROLL_MOMENTUM_START_SPEED) {
        return Offset.Zero
    }

    val gained = velocity * TRACKPAD_SCROLL_MOMENTUM_GAIN
    val gainedSpeed = gained.getDistance()
    return if (gainedSpeed > TRACKPAD_SCROLL_MOMENTUM_MAX_SPEED) {
        gained * (TRACKPAD_SCROLL_MOMENTUM_MAX_SPEED / gainedSpeed)
    } else {
        gained
    }
}

internal fun decayRemoteScrollMomentumVelocity(velocity: Offset, elapsedMs: Long): Offset {
    val decay = TRACKPAD_SCROLL_MOMENTUM_DECAY_PER_FRAME
        .toDouble()
        .pow(elapsedMs.toDouble() / TRACKPAD_SCROLL_MOMENTUM_FRAME_MS.toDouble())
        .toFloat()
    return velocity * decay
}

private suspend fun runRemoteScrollMomentum(
    streamInput: SunshineVideoStream,
    initialVelocity: Offset,
) {
    var velocity = initialVelocity
    var remainder = Offset.Zero
    var elapsedMs = 0L

    while (
        elapsedMs < TRACKPAD_SCROLL_MOMENTUM_MAX_DURATION_MS &&
        velocity.getDistance() >= TRACKPAD_SCROLL_MOMENTUM_STOP_SPEED
    ) {
        delay(TRACKPAD_SCROLL_MOMENTUM_FRAME_MS)
        val scrollDelta = velocity * (TRACKPAD_SCROLL_MOMENTUM_FRAME_MS.toFloat() / 1000f)
        remainder += scrollDelta

        val horizontal = remainder.x.toInt()
        val vertical = remainder.y.toInt()
        if (horizontal != 0 || vertical != 0) {
            sendRemoteScrollUnits(streamInput, horizontal, vertical)
            remainder -= Offset(horizontal.toFloat(), vertical.toFloat())
        }

        velocity = decayRemoteScrollMomentumVelocity(velocity, TRACKPAD_SCROLL_MOMENTUM_FRAME_MS)
        elapsedMs += TRACKPAD_SCROLL_MOMENTUM_FRAME_MS
    }
}

private fun moveRemoteMouseToCanvasPoint(
    streamInput: SunshineVideoStream,
    launchPlan: SunshineLaunchPlan,
    desktopRotated: Boolean,
    renderSize: IntSize,
    viewportSize: IntSize,
    viewportScale: Float,
    viewportOffset: Offset,
    point: Offset,
): Boolean {
    val normalized = remoteTouchPosition(
        point = point,
        desktopRotated = desktopRotated,
        renderSize = renderSize,
        viewportSize = viewportSize,
        viewportScale = viewportScale,
        viewportOffset = viewportOffset,
    ) ?: return false
    val x = (normalized.x * (launchPlan.width - 1).coerceAtLeast(1))
        .roundToInt()
        .coerceIn(0, (launchPlan.width - 1).coerceAtLeast(0))
    val y = (normalized.y * (launchPlan.height - 1).coerceAtLeast(1))
        .roundToInt()
        .coerceIn(0, (launchPlan.height - 1).coerceAtLeast(0))
    streamInput.sendMousePosition(x, y, reliable = true)
    return true
}

private fun remoteTouchPosition(
    point: Offset,
    desktopRotated: Boolean,
    renderSize: IntSize,
    viewportSize: IntSize,
    viewportScale: Float,
    viewportOffset: Offset,
): Offset? {
    if (
        renderSize.width <= 0 ||
        renderSize.height <= 0 ||
        viewportSize.width <= 0 ||
        viewportSize.height <= 0 ||
        viewportScale <= 0f
    ) {
        return null
    }
    val viewportCenter = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
    val renderCenter = Offset(renderSize.width / 2f, renderSize.height / 2f)
    val visualPoint = (point - viewportCenter - viewportOffset) / viewportScale + renderCenter
    if (!desktopRotated) {
        return Offset(
            x = (visualPoint.x / renderSize.width).coerceIn(0f, 1f),
            y = (visualPoint.y / renderSize.height).coerceIn(0f, 1f),
        )
    }

    val dx = visualPoint.x - renderCenter.x
    val dy = visualPoint.y - renderCenter.y
    val sourceWidth = renderSize.height.toFloat()
    val sourceHeight = renderSize.width.toFloat()
    val sourceX = sourceWidth / 2f + dy
    val sourceY = sourceHeight / 2f - dx
    return Offset(
        x = (sourceX / sourceWidth).coerceIn(0f, 1f),
        y = (sourceY / sourceHeight).coerceIn(0f, 1f),
    )
}

private fun rotateRemoteDelta(delta: Offset, desktopRotated: Boolean): Offset =
    if (desktopRotated) {
        Offset(x = delta.y, y = -delta.x)
    } else {
        delta
    }

private fun twoFingerSpan(changes: List<PointerInputChange>): Float? {
    if (changes.size < 2) return null
    return (changes[0].position - changes[1].position).getDistance()
}

private fun twoFingerCentroid(changes: List<PointerInputChange>): Offset? {
    if (changes.size < 2) return null
    return Offset(
        x = (changes[0].position.x + changes[1].position.x) / 2f,
        y = (changes[0].position.y + changes[1].position.y) / 2f,
    )
}

internal fun dampRemoteZoomTouchPosition(
    startPosition: Offset,
    currentPosition: Offset,
    startCentroid: Offset,
    currentCentroid: Offset,
    gain: Float,
): Offset {
    val startVector = startPosition - startCentroid
    val currentVector = currentPosition - currentCentroid
    return currentCentroid + startVector + (currentVector - startVector) * gain.coerceIn(0f, 1f)
}

internal enum class RemoteTrackpadTwoFingerGesture {
    Scroll,
    Zoom,
}

internal fun classifyRemoteTrackpadTwoFingerGesture(
    panDistance: Float,
    spanDelta: Float,
    cumulativeZoom: Float,
    scrollSlopPx: Float,
    pinchSlopPx: Float,
): RemoteTrackpadTwoFingerGesture? {
    if (!panDistance.isFinite() || !spanDelta.isFinite() || !cumulativeZoom.isFinite() || cumulativeZoom <= 0f) {
        return null
    }

    val zoomRatio = if (cumulativeZoom >= 1f) cumulativeZoom else 1f / cumulativeZoom
    val scrollPassed = panDistance >= scrollSlopPx
    val zoomPassed = spanDelta >= pinchSlopPx && zoomRatio >= TRACKPAD_PINCH_LOCK_ZOOM_RATIO

    if (zoomPassed && (!scrollPassed || spanDelta >= panDistance * TRACKPAD_PINCH_LOCK_DOMINANCE_RATIO)) {
        return RemoteTrackpadTwoFingerGesture.Zoom
    }
    if (scrollPassed && (!zoomPassed || panDistance >= spanDelta * TRACKPAD_SCROLL_LOCK_DOMINANCE_RATIO)) {
        return RemoteTrackpadTwoFingerGesture.Scroll
    }
    if (zoomPassed && spanDelta >= pinchSlopPx * TRACKPAD_PINCH_LOCK_STRONG_MULTIPLIER) {
        return RemoteTrackpadTwoFingerGesture.Zoom
    }
    if (scrollPassed && panDistance >= scrollSlopPx * TRACKPAD_SCROLL_LOCK_STRONG_MULTIPLIER) {
        return RemoteTrackpadTwoFingerGesture.Scroll
    }
    return null
}

private fun SunshineVideoStream.sendHostZoomShortcut(hostPlatform: String?, zoomIn: Boolean) {
    val modifier = if (hostPlatform == null || hostPlatform.equals("darwin", ignoreCase = true)) {
        SunshineKey.LEFT_META
    } else {
        SunshineKey.LEFT_CONTROL
    }
    val key = if (zoomIn) SunshineKey.OEM_PLUS else SunshineKey.OEM_MINUS
    sendKeyboardShortcut(modifier, key)
}

@Composable
private fun RemoteTopBar(
    status: SunshineStatus?,
    displays: List<SunshineDisplay>,
    selectedDisplayId: String?,
    streamStats: SunshineStreamStats?,
    error: String?,
    isAsleep: Boolean,
    desktopRotated: Boolean,
    onToggleDesktopRotation: () -> Unit,
    onSelectDisplay: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val live = (streamStats?.decodedFrames ?: 0L) > 0L
    val statusText = when {
        live -> "LIVE"
        isAsleep -> "ASLEEP"
        error != null -> "FAILED"
        status?.running == false -> "OFFLINE"
        streamStats != null -> "LIVE"
        else -> "LINKING"
    }
    Row(
        modifier = modifier
            .background(Color(0xFF0A0A0A))
            .border(1.dp, Color(0xFF26261C))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteConnectionIndicator(
            label = statusText,
            live = live,
            asleep = isAsleep,
            failed = !isAsleep && (error != null || status?.running == false),
            modifier = Modifier.weight(1f),
        )
        if (displays.isNotEmpty()) {
            RemoteDisplayDropdown(
                displays = displays,
                selectedDisplayId = selectedDisplayId,
                onSelectDisplay = onSelectDisplay,
                modifier = Modifier.weight(2.4f),
                height = 30,
            )
        } else {
            RemoteDisplayPlaceholder(
                modifier = Modifier.weight(2.4f),
                height = 30,
            )
        }
        RemoteTopBarRotateButton(
            rotated = desktopRotated,
            onToggle = onToggleDesktopRotation,
        )
        RemoteControlButton(
            label = "×",
            accent = false,
            onClick = onClose,
            modifier = Modifier.weight(0.5f),
            height = 30,
        )
    }
}

@Composable
private fun RemoteTopBarRotateButton(
    rotated: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (rotated) MetroCyan else Color(0xFF141410)
    val fg = if (rotated) Color.Black else Color(0xFFD8D2BE)
    val border = if (rotated) MetroCyan else Color(0xFF26261C)
    Box(
        modifier = modifier
            .requiredSize(width = 30.dp, height = 30.dp)
            .background(bg)
            .border(BorderStroke(1.dp, border))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_remote_rotate),
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(18.dp),
        )
    }
}

// When the tombstone is set (post-Tap state), the detector waits this long after
// a single-finger touch+release for a follow-up touch to disambiguate the EVENT
// TYPE. No follow-up → another Tap event (cursor moves, tombstone refreshed).
// Follow-up that also lifts as a tap → TapTap event (commit click, tombstone
// consumed). When the tombstone is unset, TapTap is not recognized at all and a
// Tap fires the cursor move with zero latency.
private const val CLICK_DOUBLE_TAP_WINDOW_MS = 300L
// Display-area scroll: finger pixel × this = high-resolution scroll units sent to the host.
private const val DISPLAY_SCROLL_SENSITIVITY = 3.0f
// Trackpad tap = displacement < slop AND duration < timeout.
private const val TRACKPAD_TAP_SLOP_DP = 10
private const val TRACKPAD_SCROLL_LOCK_SLOP_DP = 8
private const val TRACKPAD_PINCH_LOCK_SLOP_DP = 18
private const val TRACKPAD_TAP_TIMEOUT_MS = 280L
// 1-finger long-press timeout for engaging Hold mode. Shorter than the system
// default (~500ms) so the gesture feels responsive when the user is intentionally
// initiating a text-selection drag.
private const val REMOTE_HOLD_LONG_PRESS_TIMEOUT_MS = 280L
// TapTap proximity radius. If the second tap lands within this distance of the
// first tap's screen position, the gesture fires a double-click; otherwise it
// fires a single commit click at the cursor's tombstone position.
private const val REMOTE_DOUBLECLICK_PROXIMITY_DP = 48
private const val TRACKPAD_SECONDARY_TAP_TIMEOUT_MS = 450L
private const val TRACKPAD_KEYBOARD_TAP_TIMEOUT_MS = 500L
private const val TRACKPAD_PINCH_LOCK_ZOOM_RATIO = 1.08f
private const val TRACKPAD_PINCH_LOCK_DOMINANCE_RATIO = 0.75f
private const val TRACKPAD_SCROLL_LOCK_DOMINANCE_RATIO = 1.35f
private const val TRACKPAD_PINCH_LOCK_STRONG_MULTIPLIER = 2.0f
private const val TRACKPAD_SCROLL_LOCK_STRONG_MULTIPLIER = 2.5f
private const val TRACKPAD_REMOTE_ZOOM_TOUCH_GAIN = 0.38f
private const val TRACKPAD_SCROLL_MOMENTUM_FRAME_MS = 16L
private const val TRACKPAD_SCROLL_MOMENTUM_MAX_DURATION_MS = 900L
private const val TRACKPAD_SCROLL_MOMENTUM_START_SPEED = 240f
private const val TRACKPAD_SCROLL_MOMENTUM_STOP_SPEED = 24f
private const val TRACKPAD_SCROLL_MOMENTUM_MAX_SPEED = 3_200f
private const val TRACKPAD_SCROLL_MOMENTUM_GAIN = 0.75f
private const val TRACKPAD_SCROLL_MOMENTUM_DECAY_PER_FRAME = 0.90f
private const val TRACKPAD_SCROLL_MOMENTUM_SAMPLE_WEIGHT = 0.65f
private const val TRACKPAD_SCROLL_MOMENTUM_SAMPLE_MIN_SPEED = 0.01f
private const val TRACKPAD_ZOOM_SHORTCUT_STEP_LOG = 0.15f
private const val TRACKPAD_ZOOM_SHORTCUT_FLUSH_THRESHOLD = 0.65f
private const val MAC_PINCH_EVENT_UNITS_PER_LOG_STEP = 900f
private const val REMOTE_TOUCH_PRESSURE = 0.5f
private const val REMOTE_TOUCH_CONTACT_AREA = 0.02f

// The user has the full canvas to move across, so a 1:1.6 scale feels closer to a real trackpad.
private fun trackpadMouseSensitivity(plan: SunshineLaunchPlan): Float {
    val longEdge = maxOf(plan.width, plan.height).toFloat()
    return (longEdge / 1920f * 1.6f).coerceIn(1.2f, 3.5f)
}

// Hold-mode acceleration curve, optimized for text selection rather than canvas
// traversal. Below `slowSpeed` finger movement → multiplier ~`slowMult` (still ≥1:1,
// since sub-1:1 made paragraph-length selections impossible). Above `fastSpeed` →
// multiplier ~`fastMult` (reach: long selections without lifting). Linear ramp in
// between. Multiplied by a screen-resolution factor so high-DPI hosts get more
// remote-pixel coverage per finger pixel.
private fun holdMouseMultiplier(speedPxPerMs: Float, plan: SunshineLaunchPlan): Float {
    val slowSpeed = 0.2f
    val fastSpeed = 2.5f
    val slowMult = 1.0f
    val fastMult = 3.5f
    val t = ((speedPxPerMs - slowSpeed) / (fastSpeed - slowSpeed)).coerceIn(0f, 1f)
    val baseMult = slowMult + t * (fastMult - slowMult)
    val longEdge = maxOf(plan.width, plan.height).toFloat()
    val scale = (longEdge / 1920f).coerceIn(0.8f, 2.0f)
    return baseMult * scale
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

@Composable
private fun RemoteConnectionIndicator(
    label: String,
    live: Boolean,
    asleep: Boolean,
    failed: Boolean,
    modifier: Modifier = Modifier,
) {
    val dot = when {
        live -> Color(0xFFA4C400)
        asleep -> MetroOrange
        failed -> Color(0xFFFF6B5A)
        else -> Color(0xFFD8D2BE)
    }
    Row(
        modifier = modifier
            .height(30.dp)
            .border(1.dp, Color(0xFF26261C))
            .background(Color(0xFF11110D))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dot),
        )
        Text(
            text = label,
            style = BclawTheme.typography.micro,
            color = Color(0xFFD8D2BE),
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
private fun RemoteDisplayDropdown(
    displays: List<SunshineDisplay>,
    selectedDisplayId: String?,
    onSelectDisplay: (String) -> Unit,
    modifier: Modifier = Modifier,
    height: Int = 38,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = displays.firstOrNull { it.id == selectedDisplayId || it.selected }
        ?: displays.firstOrNull()
    Box(
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height.dp)
                .background(Color(0xFF141410))
                .border(BorderStroke(1.dp, if (selected != null) MetroCyan else Color(0xFF26261C)))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { expanded = true },
                )
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = selected?.name ?: "Display",
                style = BclawTheme.typography.mono,
                color = Color(0xFFD8D2BE),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "▼",
                style = BclawTheme.typography.micro,
                color = MetroCyan,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF11110D)),
        ) {
            displays.forEach { display ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = display.name,
                            style = BclawTheme.typography.mono,
                            color = if (display.connected) Color(0xFFD8D2BE) else Color(0xFF76746A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelectDisplay(display.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun RemoteDisplayPlaceholder(
    modifier: Modifier = Modifier,
    height: Int = 38,
) {
    Box(
        modifier = modifier
            .height(height.dp)
            .background(Color(0xFF141410))
            .border(1.dp, Color(0xFF26261C))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = "Display",
            style = BclawTheme.typography.mono,
            color = Color(0xFF76746A),
        )
    }
}

@Composable
private fun RemoteAppPicker(
    apps: List<SunshineApp>,
    selectedAppIndex: Int?,
    onSelectApp: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        apps.take(3).forEach { app ->
            RemoteControlButton(
                label = app.name.uppercase(),
                accent = app.index == selectedAppIndex,
                onClick = { onSelectApp(app.index) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun loadCachedWakeTargets(context: Context, hostAgentBase: String?): List<SunshineWakeTarget> {
    if (hostAgentBase == null) return emptyList()
    val raw = context
        .getSharedPreferences(REMOTE_WAKE_PREFS, Context.MODE_PRIVATE)
        .getString(wakeCacheKey(hostAgentBase), null)
        ?: return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val mac = item.optString("macAddress").takeIf { it.isNotBlank() } ?: continue
                add(
                    SunshineWakeTarget(
                        interfaceName = item.optString("interfaceName").takeIf { it.isNotBlank() } ?: "network",
                        address = item.optString("address"),
                        broadcast = item.optString("broadcast").takeIf { it.isNotBlank() } ?: "255.255.255.255",
                        macAddress = mac,
                        privateLan = item.optBoolean("privateLan"),
                        tailnet = item.optBoolean("tailnet"),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())
}

private fun saveCachedWakeTargets(
    context: Context,
    hostAgentBase: String?,
    targets: List<SunshineWakeTarget>,
) {
    if (hostAgentBase == null || targets.isEmpty()) return
    val array = JSONArray()
    targets.forEach { target ->
        array.put(
            JSONObject()
                .put("interfaceName", target.interfaceName)
                .put("address", target.address)
                .put("broadcast", target.broadcast)
                .put("macAddress", target.macAddress)
                .put("privateLan", target.privateLan)
                .put("tailnet", target.tailnet),
        )
    }
    context
        .getSharedPreferences(REMOTE_WAKE_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(wakeCacheKey(hostAgentBase), array.toString())
        .apply()
}

private fun wakeCacheKey(hostAgentBase: String): String = "wake:$hostAgentBase"

private const val REMOTE_WAKE_PREFS = "remote_wake_targets"

@Composable
private fun RemoteInfoRow(label: String, value: String) {
    val type = BclawTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label.uppercase(),
            style = type.micro,
            color = Color(0xFF55554A),
            modifier = Modifier.weight(0.34f),
        )
        Text(
            text = value,
            style = type.mono,
            color = Color(0xFFD8D2BE),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RemoteUnavailable(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = BclawTheme.typography.meta,
            color = Color(0xFF76746A),
        )
    }
}

@Composable
private fun RemoteHeaderAction(
    label: String,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = BclawTheme.typography.micro.copy(fontWeight = FontWeight.Medium),
        color = MetroCyan,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 2.dp, vertical = 4.dp),
    )
}

@Composable
private fun RemoteControlButton(
    label: String,
    accent: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Int = 38,
) {
    val type = BclawTheme.typography
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val bg = when {
        pressed && accent -> Color(0xFF7DEBFF)
        pressed -> Color(0xFF24241B)
        accent -> MetroCyan
        else -> Color(0xFF141410)
    }
    val fg = if (accent) Color.Black else Color(0xFFD8D2BE)
    val border = if (accent || pressed) MetroCyan else Color(0xFF26261C)
    Box(
        modifier = modifier
            .height(height.dp)
            .background(bg)
            .border(BorderStroke(1.dp, border))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = type.mono,
            color = fg,
        )
    }
}

private fun String.toHostAgentHttpBase(): String =
    replace("ws://", "http://")
        .replace("wss://", "https://")
        .trimEnd('/')

private fun isLanRemoteEndpoint(hostAgentBase: String?, status: SunshineStatus?): Boolean {
    val hosts = buildList {
        hostAgentBase?.urlHostOrSelf()?.let(::add)
        status?.streamHost?.takeIf { it.isNotBlank() }?.let(::add)
        status?.streamHosts.orEmpty().filterTo(this) { it.isNotBlank() }
    }
    return hosts.any(::isLanHost)
}

private fun String.urlHostOrSelf(): String? {
    val trimmed = trim()
    if (trimmed.isEmpty()) return null
    val uriHost = runCatching { URI(trimmed).host }.getOrNull()
    if (!uriHost.isNullOrBlank()) return uriHost
    val authority = trimmed
        .substringAfter("://", trimmed)
        .substringBefore('/')
        .substringAfterLast('@')
    val host = when {
        authority.startsWith("[") -> authority.substringAfter('[').substringBefore(']')
        authority.count { it == ':' } > 1 -> authority
        else -> authority.substringBefore(':')
    }
    return host.trim('[', ']').takeIf { it.isNotBlank() }
}

private fun isLanHost(host: String): Boolean {
    val normalized = host.trim().trim('[', ']').lowercase()
    if (normalized == "localhost" || normalized.endsWith(".local")) return true
    if (normalized == "::1" ||
        normalized.startsWith("fe80:") ||
        normalized.startsWith("fc") ||
        normalized.startsWith("fd")
    ) {
        return true
    }

    val parts = normalized.split('.').mapNotNull { it.toIntOrNull() }
    if (parts.size != 4 || parts.any { it !in 0..255 }) {
        return false
    }
    val first = parts[0]
    val second = parts[1]
    return first == 10 ||
        (first == 172 && second in 16..31) ||
        (first == 192 && second == 168) ||
        (first == 169 && second == 254) ||
        first == 127
}

private const val RemoteReadableWidth = 1600
private const val RemoteReadableHeight = 900
private const val RemoteReadableFps = 30
private const val RemoteReadableBitrateKbps = 14_000
private const val RemoteWifiReadableWidth = 1920
private const val RemoteWifiReadableHeight = 1080
private const val RemoteWifiReadableBitrateKbps = 22_000
private const val RemoteWifiReadableAv1BitrateKbps = 14_000
private const val RemoteLanReadableBitrateKbps = 40_000
private const val RemoteLanReadableAv1BitrateKbps = 24_000
private const val REMOTE_STREAM_BACKGROUND_GRACE_MS = 30_000L

private fun buildReadableSunshineStreamRequest(
    app: SunshineApp?,
    displayId: String?,
    useWifiProfile: Boolean,
    useLanProfile: Boolean,
    preferAv1: Boolean,
): SunshineStreamRequest {
    return SunshineStreamRequest(
        appIndex = app?.index ?: 0,
        displayId = displayId,
        width = if (useWifiProfile) RemoteWifiReadableWidth else RemoteReadableWidth,
        height = if (useWifiProfile) RemoteWifiReadableHeight else RemoteReadableHeight,
        fps = RemoteReadableFps,
        bitrateKbps = when {
            useLanProfile && preferAv1 -> RemoteLanReadableAv1BitrateKbps
            useLanProfile -> RemoteLanReadableBitrateKbps
            useWifiProfile && preferAv1 -> RemoteWifiReadableAv1BitrateKbps
            useWifiProfile -> RemoteWifiReadableBitrateKbps
            else -> RemoteReadableBitrateKbps
        },
        codec = if (preferAv1) "av1" else "h264",
    )
}

@Composable
private fun rememberWifiConnected(context: Context): Boolean {
    val appContext = context.applicationContext
    val connectivityManager = remember(appContext) {
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    var wifiConnected by remember(connectivityManager) {
        mutableStateOf(connectivityManager.hasWifiNetwork())
    }
    DisposableEffect(connectivityManager) {
        val mainHandler = Handler(Looper.getMainLooper())
        fun update() {
            val next = connectivityManager.hasWifiNetwork()
            if (Looper.myLooper() == Looper.getMainLooper()) {
                wifiConnected = next
            } else {
                mainHandler.post { wifiConnected = next }
            }
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = update()
            override fun onLost(network: Network) = update()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = update()
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching { connectivityManager.registerNetworkCallback(request, callback) }
        onDispose {
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        }
    }
    return wifiConnected
}

@Suppress("DEPRECATION")
private fun ConnectivityManager.hasWifiNetwork(): Boolean {
    val activeCaps = activeNetwork?.let { getNetworkCapabilities(it) }
    if (activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
        return true
    }
    return allNetworks.any { network ->
        val caps = getNetworkCapabilities(network) ?: return@any false
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

private fun resolveUsableDisplayId(
    preferredDisplayId: String?,
    status: SunshineStatus?,
): String? {
    val displays = status?.displays.orEmpty()
    fun String?.validDisplayId(): String? =
        this
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.takeUnless { it.equals("null", ignoreCase = true) || it.equals("undefined", ignoreCase = true) }
    fun String?.connectedDisplayId(): String? =
        validDisplayId()?.takeIf { id -> displays.any { it.id == id && it.connected } }
    fun String?.knownDisplayId(): String? =
        validDisplayId()?.takeIf { id -> displays.any { it.id == id } }

    return preferredDisplayId.connectedDisplayId()
        ?: status?.selectedDisplayId.connectedDisplayId()
        ?: displays.firstOrNull { it.connected }?.id
        ?: preferredDisplayId.knownDisplayId()
        ?: status?.selectedDisplayId.knownDisplayId()
        ?: preferredDisplayId.validDisplayId()
        ?: status?.selectedDisplayId.validDisplayId()
}
