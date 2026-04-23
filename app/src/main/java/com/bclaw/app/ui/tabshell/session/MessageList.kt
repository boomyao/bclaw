package com.bclaw.app.ui.tabshell.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.bclaw.app.domain.v2.TimelineItem
import com.bclaw.app.domain.v2.ToolStatus
import com.bclaw.app.ui.tabshell.session.messages.MessageItemRow
import com.bclaw.app.ui.tabshell.session.messages.ToolRunGroupRow
import com.bclaw.app.ui.theme.BclawTheme
import kotlinx.coroutines.flow.first

/**
 * Lazy message list for a session tab.
 *
 * Responsibilities:
 *   - Fold consecutive finalized non-body items (3+) into a single collapsed group row
 *   - Render each [TimelineItem] or group via [MessageItemRow] / [ToolRunGroupRow]
 *   - Auto-scroll to bottom when new items arrive IF the user is near the bottom;
 *     initial population jumps instantly to avoid animating through the whole history
 *   - Empty state: big muted "say something to the agent" hint
 *
 * Lazy rendering is handled by [LazyColumn] itself — only visible rows pay composition cost.
 * For long histories, message caching lives in the data layer so we don't refetch on reopen.
 */
@Composable
fun MessageList(
    sessionKey: String,
    items: List<TimelineItem>,
    listState: LazyListState,
    historyLoading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val sp = BclawTheme.spacing
    val type = BclawTheme.typography
    val colors = BclawTheme.colors

    if (items.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "say something to the agent.",
                style = type.bodyLarge,
                color = colors.inkTertiary,
                modifier = Modifier.padding(horizontal = sp.pageGutter),
            )
        }
        return
    }

    val rows = remember(items) { buildTimelineRows(items) }

    val isAtBottom by remember(listState) {
        derivedStateOf {
            listState.isNearBottom()
        }
    }

    // Separate the initial "open at the tail" snap from steady-state follow behavior. The
    // initial pass is session-scoped, while later updates only follow if the user was already
    // near the bottom before new rows arrived.
    var primed by remember(sessionKey) { mutableStateOf(false) }
    var previousRowCount by remember(sessionKey) { mutableIntStateOf(0) }
    LaunchedEffect(sessionKey, rows.size, historyLoading) {
        if (rows.isEmpty()) {
            primed = false
            previousRowCount = 0
            return@LaunchedEffect
        }

        // While history is still streaming in (`session/load` replay), don't try to snap —
        // every new row would re-trigger this effect, snap to the current last, and waste
        // the next snap. Defer until loading completes; then prime once with the true
        // bottom of the full history.
        if (historyLoading) {
            previousRowCount = rows.size
            return@LaunchedEffect
        }

        val shouldPrime = !primed
        val grew = rows.size > previousRowCount
        previousRowCount = rows.size
        if (!shouldPrime && !(grew && isAtBottom)) return@LaunchedEffect

        awaitMeasuredRows(listState, rows.size)
        snapToBottom(listState, rows.lastIndex)
        primed = true
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = sp.sp4,
            bottom = sp.sp4,
        ),
        verticalArrangement = Arrangement.spacedBy(sp.messageGap),
    ) {
        items(items = rows, key = { it.key }) { row ->
            when (row) {
                is TimelineRow.Single -> MessageItemRow(row.item)
                is TimelineRow.Group -> ToolRunGroupRow(row.items)
            }
        }
    }
}

private fun LazyListState.isNearBottom(): Boolean {
    val info = layoutInfo
    val last = info.visibleItemsInfo.lastOrNull()
    return last == null || last.index >= info.totalItemsCount - 2
}

private suspend fun awaitMeasuredRows(
    listState: LazyListState,
    expectedRowCount: Int,
) {
    snapshotFlow { listState.layoutInfo }
        .first { info ->
            info.totalItemsCount == expectedRowCount &&
                info.visibleItemsInfo.isNotEmpty() &&
                info.viewportEndOffset > info.viewportStartOffset
        }
}

private suspend fun snapToBottom(
    listState: LazyListState,
    lastIndex: Int,
) {
    repeat(2) {
        listState.scrollToItem(lastIndex)
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .first { it == lastIndex }
        withFrameNanos { }
        if (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index == lastIndex) {
            return
        }
    }
}

// ── Row folding ─────────────────────────────────────────────────────────

internal sealed class TimelineRow {
    abstract val key: String

    data class Single(val item: TimelineItem) : TimelineRow() {
        override val key: String get() = "s-${item.id}"
    }

    data class Group(val items: List<TimelineItem>) : TimelineRow() {
        override val key: String get() = "g-${items.first().id}-${items.size}"
    }
}

/**
 * Walk the timeline; fold runs of 3+ consecutive finalized non-body items into one
 * [TimelineRow.Group]. Runs that contain any non-finalized item stay expanded as
 * individual singles so live progress remains visible.
 */
internal fun buildTimelineRows(items: List<TimelineItem>): List<TimelineRow> {
    val rows = mutableListOf<TimelineRow>()
    val run = mutableListOf<TimelineItem>()

    fun flushRun() {
        if (run.isEmpty()) return
        if (run.size >= 3 && run.all { it.isFinalized() }) {
            rows += TimelineRow.Group(run.toList())
        } else {
            run.forEach { rows += TimelineRow.Single(it) }
        }
        run.clear()
    }

    for (item in items) {
        if (item.isBody()) {
            flushRun()
            rows += TimelineRow.Single(item)
        } else {
            run += item
        }
    }
    flushRun()
    return rows
}

private fun TimelineItem.isBody(): Boolean =
    this is TimelineItem.UserMessage || this is TimelineItem.AgentMessage

private fun TimelineItem.isFinalized(): Boolean = when (this) {
    is TimelineItem.UserMessage -> !streaming
    is TimelineItem.AgentMessage -> !streaming
    is TimelineItem.CommandExecution ->
        status == ToolStatus.Completed || status == ToolStatus.Failed || status == ToolStatus.Cancelled
    is TimelineItem.FileChange ->
        status == ToolStatus.Completed || status == ToolStatus.Failed || status == ToolStatus.Cancelled
    is TimelineItem.Reasoning -> !streaming
    is TimelineItem.Unsupported -> true
}
