package com.bclaw.app.ui.tabshell.session

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bclaw.app.domain.v2.FileAttachment
import com.bclaw.app.domain.v2.TabState
import com.bclaw.app.service.BclawV2Intent
import com.bclaw.app.ui.LocalBclawController
import com.bclaw.app.ui.LocalBclawNavigation
import com.bclaw.app.ui.tabshell.session.sidecar.LiveTerminalSidecar
import com.bclaw.app.ui.tabshell.session.sidecar.Sidecar
import com.bclaw.app.ui.tabshell.session.sidecar.SessionToolsSheet
import com.bclaw.app.ui.tabshell.session.sidecar.SessionToolsTab
import com.bclaw.app.ui.tabshell.session.sidecar.SidecarStubSheet
import com.bclaw.app.ui.theme.BclawTheme
import kotlinx.coroutines.launch

private const val MAX_PROMPT_ATTACHMENTS = 3

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
    val navigation = LocalBclawNavigation.current
    val uiState by controller.uiState.collectAsState()
    val timelines by controller.timelines.collectAsState()
    val colors = BclawTheme.colors

    val runtime = uiState.tabRuntimes[tab.id]
    val streaming = runtime?.streamingTurnInFlight == true
    val historyLoading = runtime?.historyLoading == true
    val runningLabel = runtime?.runningStripLabel
    val lastSendError = runtime?.lastSendError
    val agentPhase = uiState.agentConnections[tab.agentId]
    val agentInit by controller.agentInit.collectAsState()
    // `prompt.image == null` = we haven't handshaken with this agent yet. Default-allow
    // (agents that don't support images will either silently drop the block or reply with
    // an error; we surface that via the agent's reply rather than pre-emptively disabling).
    val imageAttachmentsSupported =
        agentInit[tab.agentId]?.agentCapabilities?.promptCapabilities?.image != false
    val macFilesSupported =
        agentInit[tab.agentId]?.agentCapabilities?.promptCapabilities?.embeddedContext != false
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
    // Split-terminal layout flag: message list takes the top half, terminal body the bottom,
    // composer is hidden (shell IS the input). Triggered from the fullscreen terminal's
    // "⇅ SPLIT" action; closed by the divider's ✕.
    var splitTerminalVisible by rememberSaveable(tab.id.value) { mutableStateOf(false) }

    // Attachments staged for the next prompt — owned here so both the composer chip strip
    // and the Tools→files entry write to the same list.
    var pendingAttachments by remember(tab.id.value) { mutableStateOf<List<Uri>>(emptyList()) }
    var pendingFileAttachments by remember(tab.id.value) {
        mutableStateOf<List<FileAttachment>>(emptyList())
    }
    var fsBrowserVisible by remember { mutableStateOf(false) }
    val attachScope = rememberCoroutineScope()
    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PROMPT_ATTACHMENTS),
    ) { picked ->
        if (picked.isNotEmpty()) {
            pendingAttachments =
                (pendingAttachments + picked).distinct().take(MAX_PROMPT_ATTACHMENTS)
        }
    }

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

    com.bclaw.app.ui.tabshell.session.messages.ImageViewerHost {
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

            // Top half (or full body when split is off): timeline / empty / loading.
            // In split mode we halve the weight; without split the body keeps its natural
            // full-height allocation.
            val bodyWeight = if (splitTerminalVisible) 1f else 1f
            val bodyModifier = Modifier.weight(bodyWeight)
            when {
                timeline.isEmpty() && historyLoading -> SessionHistoryLoading(
                    modifier = bodyModifier,
                )
                timeline.isEmpty() && !streaming -> EmptySessionStarters(
                    tab = tab,
                    onSeedComposer = { seed -> composerInput = seed },
                    modifier = bodyModifier,
                )
                else -> MessageList(
                    sessionKey = tab.id.value,
                    items = timeline,
                    listState = listState,
                    historyLoading = historyLoading,
                    modifier = bodyModifier,
                )
            }

            if (splitTerminalVisible) {
                // Divider row ~ JSX screens-terminal.jsx:72-89. 24dp strip, drag handle,
                // resize / close affordances. Close returns to normal chat + composer layout.
                SplitDivider(onClose = { splitTerminalVisible = false })
                com.bclaw.app.ui.showcase.terminal.TerminalChipsContent(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } else {
                if (streaming) {
                    RunningStrip(label = runningLabel)
                }
                if (lastSendError != null) {
                    SendErrorBar(
                        message = lastSendError,
                        onDismiss = { controller.onIntent(BclawV2Intent.DismissSendError(tab.id)) },
                    )
                }

                HairRule()

                Composer(
                    input = composerInput,
                    onInputChange = { composerInput = it },
                    streaming = streaming,
                    connected = connected,
                    attachments = pendingAttachments,
                    fileAttachments = pendingFileAttachments,
                    onRemoveAttachment = { uri -> pendingAttachments = pendingAttachments - uri },
                    onRemoveFileAttachment = { ref ->
                        pendingFileAttachments = pendingFileAttachments - ref
                    },
                    onSend = { text ->
                        controller.onIntent(
                            BclawV2Intent.SendPrompt(
                                tabId = tab.id,
                                text = text,
                                imageAttachmentUris = pendingAttachments.map { it.toString() },
                                fileAttachments = pendingFileAttachments,
                            ),
                        )
                        composerInput = ""
                        pendingAttachments = emptyList()
                        pendingFileAttachments = emptyList()
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
            onSelectSidecar = { sidecar ->
                if (sidecar == Sidecar.Remote) {
                    navigation.requestRemoteOverlay(
                        bridgeWsUrl = uiState.deviceBook.activeDevice?.wsBaseUrl,
                        deviceName = uiState.deviceBook.activeDevice?.displayName ?: "device",
                    )
                    sidecarStub = null
                } else {
                    sidecarStub = sidecar
                }
            },
        )

        // Sidecar routing:
        //   - Files → bottom sheet (it IS a picker, bottom sheet is right)
        //   - Terminal → full-screen design shell until PTY adapter lands
        //   - Remote → Sunshine-controlled remote sidecar
        when (sidecarStub) {
            Sidecar.Files -> SidecarStubSheet(
                visible = true,
                sidecar = sidecarStub,
                onDismissRequest = { sidecarStub = null },
                imageAttachmentsSupported = imageAttachmentsSupported,
                macFilesSupported = macFilesSupported,
                onPickPhoneImages = {
                    pickImages.launch(
                        PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly,
                        ),
                    )
                },
                onBrowseMacFiles = { fsBrowserVisible = true },
            )
            Sidecar.Terminal -> LiveTerminalSidecar(
                onDismiss = { sidecarStub = null },
                onRequestSplit = {
                    sidecarStub = null
                    splitTerminalVisible = true
                },
            )
            Sidecar.Remote -> Unit
            null -> {
                // No sidecar — keep a dismissed stub sheet around for transition animation parity.
                SidecarStubSheet(
                    visible = false,
                    sidecar = null,
                    onDismissRequest = { sidecarStub = null },
                )
            }
        }

        FsBrowserSheet(
            visible = fsBrowserVisible,
            bridgeWsUrl = uiState.deviceBook.activeDevice?.wsBaseUrl,
            cwd = tab.projectCwd.value,
            onDismissRequest = { fsBrowserVisible = false },
            onAttach = { ref ->
                val isImage = ref.mimeType?.startsWith("image/") == true
                val wsBaseUrl = uiState.deviceBook.activeDevice?.wsBaseUrl
                if (isImage && wsBaseUrl != null) {
                    // Fetch bytes now so the composer chip shows a real thumbnail instead
                    // of "📄 filename". Reuses the phone-picker image path end to end:
                    // persistedUri goes into pendingAttachments, sendPrompt encodes it
                    // like any gallery image.
                    attachScope.launch {
                        val persistedUri = controller.prepareMacImageAttachment(
                            deviceWsUrl = wsBaseUrl,
                            cwd = ref.cwd,
                            rel = ref.rel,
                            mimeType = ref.mimeType ?: "image/jpeg",
                        )
                        if (persistedUri != null) {
                            pendingAttachments =
                                (pendingAttachments + Uri.parse(persistedUri))
                                    .distinct()
                                    .take(MAX_PROMPT_ATTACHMENTS)
                        }
                    }
                } else {
                    pendingFileAttachments = (pendingFileAttachments + ref)
                        .distinctBy { it.rel }
                        .take(MAX_PROMPT_ATTACHMENTS)
                }
            },
        )
    }
    }
}

@Composable
private fun SendErrorBar(message: String, onDismiss: () -> Unit) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceDeep)
            .padding(horizontal = sp.pageGutter, vertical = sp.sp2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sp.sp2),
    ) {
        Text(text = "●", style = type.bodySmall, color = colors.roleError)
        Text(
            text = "send failed · $message",
            style = type.bodySmall,
            color = colors.inkPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "dismiss",
            style = type.meta,
            color = colors.inkTertiary,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                .padding(sp.sp1),
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

/**
 * Split-terminal divider — 24dp strip dividing the chat pane (above) from the terminal
 * pane (below). Label explains the drag affordance; the ✕ on the right dismisses split
 * and returns to the normal composer layout.
 */
@Composable
private fun SplitDivider(onClose: () -> Unit) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(colors.surfaceDeep)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .width(24.dp)
                .height(2.dp)
                .background(colors.inkTertiary),
        )
        Text(
            text = "TERMINAL · DRAG TO RESIZE",
            style = type.micro,
            color = colors.inkTertiary,
            letterSpacing = 1.5.sp,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "✕",
            style = type.monoSmall,
            color = colors.inkTertiary,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                )
                .padding(4.dp),
        )
    }
}
