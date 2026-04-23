package com.bclaw.app.ui.tabshell.session.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bclaw.app.domain.v2.TimelineItem
import com.bclaw.app.domain.v2.ToolStatus
import com.bclaw.app.ui.theme.BclawTheme

/**
 * Message renderer dispatcher — maps a [TimelineItem] to its composable.
 *
 * Body items (user/agent messages) render as full-width bubbles. Non-body items
 * (command, file change, reasoning, unsupported) render as a single tappable row
 * that expands inline to the full detail card. Consecutive runs of 3+ finalized
 * non-body items are folded into a `ToolRunGroupRow` at the list level — see
 * [buildTimelineRows] in MessageList.
 */
@Composable
fun MessageItemRow(item: TimelineItem) {
    when (item) {
        is TimelineItem.UserMessage -> UserMessageRow(item)
        is TimelineItem.AgentMessage -> AgentMessageBlock(item)
        is TimelineItem.CommandExecution -> CommandExecutionRow(item)
        is TimelineItem.FileChange -> FileChangeRow(item)
        is TimelineItem.Reasoning -> ReasoningRow(item)
        is TimelineItem.Unsupported -> UnsupportedRow(item)
    }
}

// ── User message ────────────────────────────────────────────────────────

@Composable
private fun UserMessageRow(item: TimelineItem.UserMessage) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = sp.pageGutter),
        horizontalArrangement = Arrangement.End,
    ) {
        Text(
            text = item.text,
            style = type.bodyLarge,
            color = colors.accent,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(max = 320.dp),
        )
    }
}

// ── Agent message ───────────────────────────────────────────────────────

@Composable
private fun AgentMessageBlock(item: TimelineItem.AgentMessage) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(start = sp.edgeLeft, end = sp.edgeRight),
    ) {
        Text(
            text = item.text,
            style = type.bodyLarge,
            color = colors.inkPrimary,
        )
        if (item.streaming) {
            Spacer(Modifier.height(sp.sp1))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(sp.sp1),
            ) {
                Text("•", style = type.monoSmall, color = colors.accent)
                Text("streaming", style = type.meta, color = colors.inkTertiary)
            }
        }
    }
}

// ── Command execution ───────────────────────────────────────────────────

@Composable
private fun CommandExecutionRow(item: TimelineItem.CommandExecution) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing
    // Running/pending auto-expanded so users see live tail; completed starts collapsed.
    var expanded by remember {
        mutableStateOf(item.status == ToolStatus.Running || item.status == ToolStatus.Pending)
    }

    val statusGlyph = when (item.status) {
        ToolStatus.Pending, ToolStatus.Running -> "⟲"
        ToolStatus.Completed -> "✓"
        ToolStatus.Failed -> "✕"
        ToolStatus.Cancelled -> "⏸"
    }
    val statusColor = when (item.status) {
        ToolStatus.Completed -> colors.roleLive
        ToolStatus.Failed -> colors.roleError
        ToolStatus.Cancelled -> colors.roleWarn
        else -> colors.accent
    }
    val trailing = buildString {
        append(statusGlyph)
        val duration = item.durationMs
        val exit = item.exitCode
        when {
            duration != null -> append(" ${(duration / 1000).coerceAtLeast(1)}s")
            exit != null -> append(" exit $exit")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { expanded = !expanded },
            )
            .padding(horizontal = sp.pageGutter),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(sp.sp2),
        ) {
            Text(
                text = "▶ ${item.command.lines().firstOrNull().orEmpty()}",
                style = type.mono,
                color = colors.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = trailing,
                style = type.monoSmall,
                color = statusColor,
            )
        }
        if (expanded) {
            Spacer(Modifier.height(sp.sp1))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceRaised)
                    .drawBehind {
                        drawRect(
                            color = colors.accent,
                            topLeft = Offset(0f, 0f),
                            size = Size(2.dp.toPx(), size.height),
                        )
                    }
                    .padding(horizontal = sp.insideCard, vertical = sp.sp3),
                verticalArrangement = Arrangement.spacedBy(sp.sp1),
            ) {
                item.cwd?.let { cwd ->
                    Text(text = cwd, style = type.monoSmall, color = colors.inkTertiary)
                }
                if (item.outputTail.isNotBlank()) {
                    item.outputTail.lines().forEach { line ->
                        Text(
                            text = "└ $line".take(160),
                            style = type.monoSmall,
                            color = colors.inkTertiary,
                        )
                    }
                }
            }
        }
    }
}

// ── File change ─────────────────────────────────────────────────────────

@Composable
private fun FileChangeRow(item: TimelineItem.FileChange) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing
    var expanded by remember { mutableStateOf(false) }

    val trailing = if (item.addedLines > 0 || item.removedLines > 0) {
        "+${item.addedLines} -${item.removedLines}"
    } else ""

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { expanded = !expanded },
            )
            .padding(horizontal = sp.pageGutter),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(sp.sp2),
        ) {
            Text(
                text = "✎ ${item.paths.size} file${if (item.paths.size == 1) "" else "s"}",
                style = type.mono,
                color = colors.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (trailing.isNotBlank()) {
                Text(
                    text = trailing,
                    style = type.monoSmall,
                    color = colors.inkTertiary,
                )
            }
        }
        if (expanded && item.paths.isNotEmpty()) {
            Spacer(Modifier.height(sp.sp1))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceRaised)
                    .drawBehind {
                        drawRect(
                            color = colors.accent,
                            topLeft = Offset(0f, 0f),
                            size = Size(2.dp.toPx(), size.height),
                        )
                    }
                    .padding(horizontal = sp.insideCard, vertical = sp.sp3),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item.paths.forEach { path ->
                    Text(
                        text = path,
                        style = type.mono,
                        color = colors.inkSecondary,
                    )
                }
            }
        }
    }
}

// ── Reasoning ───────────────────────────────────────────────────────────

@Composable
private fun ReasoningRow(item: TimelineItem.Reasoning) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing
    var expanded by remember { mutableStateOf(false) }

    val summary = if (item.summary.isNotBlank()) item.summary else "thinking…"
    val firstLine = summary.lines().firstOrNull().orEmpty().ifBlank { "thinking…" }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = sp.pageGutter)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (item.summary.isNotBlank()) expanded = !expanded },
            ),
        verticalArrangement = Arrangement.spacedBy(sp.sp1),
    ) {
        Text(
            text = "💭 ${firstLine.take(96)}",
            style = type.bodySmall.copy(fontStyle = FontStyle.Italic),
            color = colors.inkTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (expanded) {
            Text(
                text = item.summary,
                style = type.bodySmall,
                color = colors.inkSecondary,
            )
        }
    }
}

// ── Unsupported ─────────────────────────────────────────────────────────

@Composable
private fun UnsupportedRow(item: TimelineItem.Unsupported) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    Text(
        text = "— unsupported item · ${item.kind} —",
        style = type.monoSmall,
        color = colors.inkTertiary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = sp.pageGutter),
        textAlign = TextAlign.Center,
    )
}

// ── Tool-run group ──────────────────────────────────────────────────────

/**
 * Renders a run of 3+ consecutive finalized non-body items as one collapsed row
 * with total duration + step count. Tap to expand — children then render as their
 * own single-line rows (each individually expandable).
 */
@Composable
fun ToolRunGroupRow(items: List<TimelineItem>) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing
    var expanded by remember(items.first().id, items.size) { mutableStateOf(false) }

    val totalMs = items.sumOf { child ->
        when (child) {
            is TimelineItem.CommandExecution -> child.durationMs ?: 0L
            else -> 0L
        }
    }
    val durationPart = when {
        totalMs >= 1000 -> "${totalMs / 1000}s"
        totalMs > 0 -> "${totalMs}ms"
        else -> null
    }
    val headerText = buildString {
        append("◷")
        durationPart?.let { append(" $it") }
        append(" · ${items.size} steps")
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { expanded = !expanded },
                )
                .padding(horizontal = sp.pageGutter, vertical = sp.sp1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(sp.sp2),
        ) {
            Text(
                text = headerText,
                style = type.mono,
                color = colors.inkTertiary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (expanded) "▾" else "▸",
                style = type.bodySmall,
                color = colors.inkTertiary,
            )
        }

        if (expanded) {
            Spacer(Modifier.height(sp.sp2))
            Column(verticalArrangement = Arrangement.spacedBy(sp.sp3)) {
                items.forEach { child ->
                    MessageItemRow(child)
                }
            }
        }
    }
}
