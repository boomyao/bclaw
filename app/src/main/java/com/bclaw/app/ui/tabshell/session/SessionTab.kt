package com.bclaw.app.ui.tabshell.session

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bclaw.app.domain.v2.TabState
import com.bclaw.app.service.BclawV2Intent
import com.bclaw.app.ui.LocalBclawController
import com.bclaw.app.ui.tabshell.session.sidecar.Sidecar
import com.bclaw.app.ui.tabshell.session.sidecar.SessionToolsSheet
import com.bclaw.app.ui.tabshell.session.sidecar.SessionToolsTab
import com.bclaw.app.ui.tabshell.session.sidecar.SidecarStubSheet
import com.bclaw.app.ui.theme.BclawTheme

/**
 * Session tab — the workhorse screen (UX_V2 §2.3).
 *
 * Stacks (top → bottom):
 *   - [SessionCrumb] · collapses when user scrolls the message list past the top
 *   - [MessageList] or [EmptySessionStarters]
 *   - [RunningStrip] while a turn streams
 *   - [Composer]
 *
 * Sheets:
 *   - [SessionActionsSheet] · crumb `···` — rename / fork / copy id / close
 *   - [SessionToolsSheet]   · composer `+` — tabbed sidecars | capabilities
 */
@Composable
fun SessionTab(tab: TabState) {
    val controller = LocalBclawController.current
    val uiState by controller.uiState.collectAsState()
    val timelines by controller.timelines.collectAsState()
    val colors = BclawTheme.colors

    val runtime = uiState.tabRuntimes[tab.id]
    val streaming = runtime?.streamingTurnInFlight == true
    val historyLoading = runtime?.historyLoading == true
    val runningLabel = runtime?.runningStripLabel
    val agentPhase = uiState.agentConnections[tab.agentId]
    // `null` phase = never tried to connect yet (fresh tab / bootstrap); sendPrompt will
    // trigger the first connect, so we treat it as "allowed to try". Connected is the
    // normal green path. Everything else (Connecting / Reconnecting / Offline / Unavailable)
    // blocks composer — either a connect is in flight or we know it failed.
    val connected = agentPhase == null ||
        agentPhase == com.bclaw.app.service.AgentConnectionPhase.Connected
    val timeline = timelines[tab.id].orEmpty()

    // Per-tab composer text — survives config change via rememberSaveable keyed on tab id so
    // switching tabs shows each tab's own draft.
    var composerInput by rememberSaveable(tab.id.value) { mutableStateOf("") }

    // Overlay visibility (local UI state — doesn't belong on the controller).
    var actionsVisible by remember { mutableStateOf(false) }
    var toolsVisible by remember { mutableStateOf(false) }
    var toolsInitialTab by remember { mutableStateOf(SessionToolsTab.Sidecars) }
    var sidecarStub by remember { mutableStateOf<Sidecar?>(null) }

    // Scroll position belongs to the tab identity so opening a different session doesn't
    // inherit stale layout info or "already primed" state from the previous one.
    val listState = rememberSaveable(tab.id.value, saver = LazyListState.Saver) {
        LazyListState()
    }
    val crumbVisible by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 &&
                listState.firstVisibleItemScrollOffset < 120
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.surfaceBase)
                // max(IME, nav-bar): composer sits just above the keyboard when open, just
                // above the gesture/nav area when closed. No double-counting because the
                // activity is edge-to-edge (window doesn't resize for IME).
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
        ) {
            AnimatedVisibility(
                visible = crumbVisible,
                enter = expandVertically(animationSpec = tween(180)) +
                    fadeIn(animationSpec = tween(180)),
                exit = shrinkVertically(animationSpec = tween(160)) +
                    fadeOut(animationSpec = tween(160)),
            ) {
                Column {
                    SessionCrumb(
                        tab = tab,
                        onOverflowClick = { actionsVisible = true },
                    )
                    HairRule()
                }
            }

            when {
                timeline.isEmpty() && historyLoading -> SessionHistoryLoading(
                    modifier = Modifier.weight(1f),
                )
                timeline.isEmpty() && !streaming -> EmptySessionStarters(
                    tab = tab,
                    onSeedComposer = { seed -> composerInput = seed },
                    modifier = Modifier.weight(1f),
                )
                else -> MessageList(
                    sessionKey = tab.id.value,
                    items = timeline,
                    listState = listState,
                    historyLoading = historyLoading,
                    modifier = Modifier.weight(1f),
                )
            }

            if (streaming) {
                RunningStrip(label = runningLabel)
            }

            HairRule()

            Composer(
                input = composerInput,
                onInputChange = { composerInput = it },
                streaming = streaming,
                connected = connected,
                onSend = { text ->
                    controller.onIntent(BclawV2Intent.SendPrompt(tab.id, text))
                    composerInput = ""
                },
                onCancel = {
                    controller.onIntent(BclawV2Intent.CancelPrompt(tab.id))
                },
                onOpenSidecars = {
                    toolsInitialTab = SessionToolsTab.Sidecars
                    toolsVisible = true
                },
            )
        }

        SessionActionsSheet(
            visible = actionsVisible,
            tab = tab,
            onDismissRequest = { actionsVisible = false },
            onCloseTab = { controller.onIntent(BclawV2Intent.CloseTab(tab.id)) },
        )

        SessionToolsSheet(
            visible = toolsVisible,
            initialTab = toolsInitialTab,
            agentId = tab.agentId,
            onDismissRequest = { toolsVisible = false },
            onSelectSidecar = { sidecarStub = it },
        )

        SidecarStubSheet(
            visible = sidecarStub != null,
            sidecar = sidecarStub,
            onDismissRequest = { sidecarStub = null },
        )
    }
}

@Composable
private fun SessionHistoryLoading(modifier: Modifier = Modifier) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        androidx.compose.material3.Text(
            text = "loading history…",
            style = type.bodySmall,
            color = colors.inkTertiary,
        )
    }
}

@Composable
private fun HairRule() {
    val colors = BclawTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(colors.borderSubtle),
    )
}
