package com.bclaw.app.ui.chat

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bclaw.app.data.WorkspaceConfig
import com.bclaw.app.domain.model.ChatThreadState
import com.bclaw.app.domain.model.ConnectionPhase
import com.bclaw.app.domain.model.TimelineItemUi
import com.bclaw.app.ui.components.Banner
import com.bclaw.app.ui.components.ConnectionChip
import com.bclaw.app.ui.components.MetroActionButton
import com.bclaw.app.ui.components.MetroUnderlineTextField
import com.bclaw.app.ui.components.RunningStrip
import com.bclaw.app.ui.components.TimelineItemCard
import com.bclaw.app.ui.theme.BclawSpacing
import com.bclaw.app.ui.theme.LocalBclawColors
import com.bclaw.app.ui.theme.LocalBclawTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    workspace: WorkspaceConfig?,
    threadId: String?,
    connectionPhase: ConnectionPhase,
    threadState: ChatThreadState?,
    statusMessage: String?,
    onOpenDrawer: () -> Unit,
    onOpenStatusSheet: () -> Unit,
    onOpenThread: (() -> Unit)?,
    onSend: (String) -> Unit,
    onInterrupt: () -> Unit,
) {
    val colors = LocalBclawColors.current
    val typography = LocalBclawTypography.current
    val density = LocalDensity.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    var draft by rememberSaveable(threadId) { mutableStateOf("") }
    val listState = rememberLazyListState()
    var pendingNewCount by remember(threadId) { mutableIntStateOf(0) }
    var previousItemCount by remember(threadId) { mutableIntStateOf(threadState?.items?.size ?: 0) }
    var presentStopButton by remember(threadId) { mutableStateOf(threadState?.activeTurnId != null) }
    var lastHapticTurnId by remember { mutableStateOf<String?>(null) }
    val thresholdPx = with(density) { 100.dp.roundToPx() }
    val isNearBottom by remember(listState, threadState?.items?.size) {
        derivedStateOf {
            val totalItems = threadState?.items?.size ?: 0
            listState.isWithinBottomThreshold(totalItems = totalItems, thresholdPx = thresholdPx)
        }
    }

    LaunchedEffect(threadId) {
        onOpenThread?.invoke()
        previousItemCount = threadState?.items?.size ?: 0
        pendingNewCount = 0
        val count = threadState?.items?.size ?: 0
        if (count > 0) {
            listState.scrollToItem(count - 1)
        }
    }

    LaunchedEffect(threadState?.items?.size, isNearBottom) {
        val count = threadState?.items?.size ?: 0
        if (count > previousItemCount) {
            if (isNearBottom) {
                listState.animateScrollToItem(count - 1)
                pendingNewCount = 0
            } else {
                pendingNewCount += count - previousItemCount
            }
        }
        previousItemCount = count
    }

    LaunchedEffect(isNearBottom) {
        if (isNearBottom) pendingNewCount = 0
    }

    LaunchedEffect(threadState?.activeTurnId) {
        if (threadState?.activeTurnId != null) {
            presentStopButton = true
        } else if (presentStopButton) {
            delay(500)
            presentStopButton = false
        }
    }

    LaunchedEffect(threadState?.latestTurnId, threadState?.latestTurnStatus) {
        if (
            threadState?.latestTurnId != null &&
            threadState.latestTurnId != lastHapticTurnId &&
            threadState.latestTurnStatus != null
        ) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            lastHapticTurnId = threadState.latestTurnId
        }
    }

    val canSend = threadId != null
    val runningLabel = threadState.runningStripLabel()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.terminalBlack)
            .testTag("chat_root"),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ChatTopBar(
                threadName = threadState?.thread?.name?.takeIf { it.isNotBlank() }
                    ?: threadId?.take(12)
                    ?: "No thread selected",
                workspaceName = workspace?.displayName ?: "Choose a workspace",
                connectionPhase = connectionPhase,
                runningLabel = runningLabel,
                onOpenDrawer = onOpenDrawer,
                onOpenStatusSheet = onOpenStatusSheet,
            )

            Box(modifier = Modifier.weight(1f)) {
                if (threadId == null) {
                    EmptyChatState(
                        statusMessage = statusMessage,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("chat_timeline"),
                        state = listState,
                    ) {
                        item {
                            Banner(statusMessage ?: threadState?.error)
                        }
                        if (!threadState?.latestTurnStatus.isNullOrBlank()) {
                            item {
                                Text(
                                    text = "Turn: ${threadState?.latestTurnStatus.toDisplayLabel()}",
                                    modifier = Modifier
                                        .padding(
                                            start = BclawSpacing.EdgeLeft,
                                            end = BclawSpacing.EdgeRight,
                                            top = 8.dp,
                                        )
                                        .testTag("turn_status"),
                                    style = typography.meta,
                                    color = colors.textMeta,
                                )
                            }
                        }
                        items(
                            count = threadState?.items?.size ?: 0,
                            key = { index -> threadState?.items?.get(index)?.id ?: "item_$index" },
                        ) { index ->
                            threadState?.items?.get(index)?.let { item ->
                                TimelineItemCard(item)
                            }
                        }
                    }
                }

                if (pendingNewCount > 0 && threadId != null) {
                    MetroActionButton(
                        text = "↓ $pendingNewCount new",
                        onClick = {
                            coroutineScope.launch {
                                val count = threadState?.items?.size ?: 0
                                if (count > 0) {
                                    listState.animateScrollToItem(count - 1)
                                }
                                pendingNewCount = 0
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = BclawSpacing.EdgeRight, bottom = 16.dp)
                            .testTag("new_messages_chip"),
                        fillColor = colors.surfaceElevated,
                        textColor = colors.accentCyan,
                        borderColor = colors.accentCyan,
                    )
                }
            }

            InputRow(
                draft = draft,
                onDraftChanged = { draft = it },
                canSend = canSend,
                showStop = presentStopButton,
                stopEnabled = threadState?.activeTurnId != null,
                onSend = {
                    val message = draft.trim()
                    if (message.isNotBlank() && threadId != null) {
                        onSend(message)
                        draft = ""
                    }
                },
                onInterrupt = onInterrupt,
            )
        }
    }
}

@Composable
private fun ChatTopBar(
    threadName: String,
    workspaceName: String,
    connectionPhase: ConnectionPhase,
    runningLabel: String?,
    onOpenDrawer: () -> Unit,
    onOpenStatusSheet: () -> Unit,
) {
    val colors = LocalBclawColors.current
    val typography = LocalBclawTypography.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(colors.terminalBlack),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 8.dp,
                    end = BclawSpacing.EdgeRight,
                    top = 8.dp,
                    bottom = 8.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BclawSpacing.InlineGap),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(onClick = onOpenDrawer)
                    .semantics { contentDescription = "Open drawer" }
                    .testTag("drawer_button"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = null,
                    tint = colors.textPrimary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = threadName,
                    style = typography.title,
                    color = colors.textPrimary,
                )
                Text(
                    text = workspaceName,
                    style = typography.meta,
                    color = colors.textMeta,
                )
            }
            ConnectionChip(
                connectionPhase = connectionPhase,
                onClick = onOpenStatusSheet,
            )
        }
        if (!runningLabel.isNullOrBlank()) {
            RunningStrip(label = runningLabel)
        }
    }
}

@Composable
private fun InputRow(
    draft: String,
    onDraftChanged: (String) -> Unit,
    canSend: Boolean,
    showStop: Boolean,
    stopEnabled: Boolean,
    onSend: () -> Unit,
    onInterrupt: () -> Unit,
) {
    val colors = LocalBclawColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.terminalBlack)
            .navigationBarsPadding()
            .padding(
                start = BclawSpacing.EdgeLeft,
                end = BclawSpacing.EdgeRight,
                top = 12.dp,
                bottom = 12.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(BclawSpacing.InlineGap),
        ) {
            MetroUnderlineTextField(
                value = draft,
                onValueChange = onDraftChanged,
                modifier = Modifier
                    .weight(1f),
                fieldModifier = Modifier.testTag("chat_input"),
                enabled = canSend && !showStop,
                placeholder = if (canSend) {
                    "Type a prompt…"
                } else {
                    "Choose a thread from the drawer"
                },
                minLines = 1,
                maxLines = 4,
            )
            MetroActionButton(
                text = if (showStop) "STOP" else "SEND",
                onClick = if (showStop) onInterrupt else onSend,
                modifier = Modifier
                    .testTag(if (showStop) "stop_button" else "send_button"),
                enabled = if (showStop) stopEnabled else canSend && draft.trim().isNotBlank(),
                fillColor = if (showStop) colors.dangerRed else colors.accentCyan,
                textColor = colors.terminalBlack,
            )
        }
    }
}

@Composable
private fun EmptyChatState(
    statusMessage: String?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBclawColors.current
    val typography = LocalBclawTypography.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = BclawSpacing.EdgeLeft,
                end = BclawSpacing.EdgeRight,
                top = 48.dp,
            )
            .testTag("empty_chat"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Banner(statusMessage)
        Text(
            text = "Tell the agent what to work on.",
            style = typography.title,
            color = colors.textPrimary,
            textAlign = TextAlign.Start,
        )
        Text(
            text = "Pick a thread from the drawer or create a new one in a workspace.",
            style = typography.body,
            color = colors.textMeta,
            textAlign = TextAlign.Start,
        )
    }
}

private fun String?.toDisplayLabel(): String {
    return when (this) {
        "inProgress" -> "In progress"
        "interrupted" -> "Interrupted"
        "completed" -> "Completed"
        "failed" -> "Failed"
        else -> this.orEmpty()
    }
}

private fun ChatThreadState?.runningStripLabel(): String? {
    val state = this ?: return null
    if (state.activeTurnId == null) return null
    val current = state.items.asReversed().firstOrNull { it.turnId == state.activeTurnId } ?: state.items.lastOrNull()
    return when (current) {
        is TimelineItemUi.Reasoning -> "thinking…"
        is TimelineItemUi.CommandExecution -> "running ${current.command.ifBlank { "command" }}"
        is TimelineItemUi.FileChange -> "editing ${current.paths.size} files"
        is TimelineItemUi.AgentMessage -> "responding…"
        else -> "working…"
    }
}

private fun androidx.compose.foundation.lazy.LazyListState.isWithinBottomThreshold(
    totalItems: Int,
    thresholdPx: Int,
): Boolean {
    if (totalItems == 0 || layoutInfo.visibleItemsInfo.isEmpty()) return true
    val lastVisible = layoutInfo.visibleItemsInfo.last()
    if (lastVisible.index < totalItems - 1) return false
    val distanceToBottom = lastVisible.offset + lastVisible.size - layoutInfo.viewportEndOffset
    return distanceToBottom <= thresholdPx
}
