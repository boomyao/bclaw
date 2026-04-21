package com.bclaw.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bclaw.app.domain.model.TimelineItemUi
import com.bclaw.app.ui.theme.BclawShape
import com.bclaw.app.ui.theme.BclawSpacing
import com.bclaw.app.ui.theme.LocalBclawColors
import com.bclaw.app.ui.theme.LocalBclawTypography
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val messageTimeFormatter = SimpleDateFormat("HH:mm", Locale.US)

@Composable
fun TimelineItemCard(item: TimelineItemUi) {
    when (item) {
        is TimelineItemUi.UserMessage -> UserMessageLine(item)
        is TimelineItemUi.AgentMessage -> AgentMessageBlock(item)
        is TimelineItemUi.CommandExecution -> CommandExecutionCard(item)
        is TimelineItemUi.FileChange -> FileChangeCard(item)
        is TimelineItemUi.Reasoning -> ReasoningRow(item)
        is TimelineItemUi.McpToolCall -> McpToolCallCard(item)
        is TimelineItemUi.WebSearch -> WebSearchRow(item)
        is TimelineItemUi.Plan -> PlanCard(item)
        is TimelineItemUi.ContextCompaction -> ContextCompactionSeparator(item)
        is TimelineItemUi.DynamicToolCall -> DynamicToolCallCard(item)
        is TimelineItemUi.SubAgent -> SubAgentCard(item)
        is TimelineItemUi.Unsupported -> UnsupportedItemRow(item)
        is TimelineItemUi.Error -> ErrorRow(item)
    }
}

@Composable
private fun UserMessageLine(
    item: TimelineItemUi.UserMessage,
) {
    val colors = LocalBclawColors.current
    val typography = LocalBclawTypography.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(
                    start = BclawSpacing.EdgeLeft,
                    end = BclawSpacing.EdgeRight,
                    top = BclawSpacing.MessageGap / 2,
                    bottom = BclawSpacing.MessageGap / 2,
                ),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = item.text,
                style = typography.body,
                color = colors.accentCyan,
            )
            val meta = buildList {
                item.timestampEpochMillis?.let { add(messageTimeFormatter.format(Date(it))) }
                if (item.optimistic) add("sending...")
            }.joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = typography.meta,
                    color = colors.textMeta,
                )
            }
        }
    }
}

@Composable
private fun AgentMessageBlock(
    item: TimelineItemUi.AgentMessage,
) {
    MarkdownText(
        text = item.text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = BclawSpacing.EdgeLeft,
                end = BclawSpacing.EdgeRight,
                top = BclawSpacing.MessageGap / 2,
                bottom = BclawSpacing.MessageGap / 2,
            )
            .testTag("agent_message_${item.id}"),
    )
}

@Composable
private fun CommandExecutionCard(
    item: TimelineItemUi.CommandExecution,
) {
    val colors = LocalBclawColors.current
    val typography = LocalBclawTypography.current
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    val running = item.status.isRunningStatus()
    val body = when {
        expanded -> item.output.ifBlank { "No output" }
        running -> item.output.tailLines(3).ifBlank { "waiting for output..." }
        else -> item.commandSummary()
    }
    val statusColor = if (item.exitCode != null && item.exitCode != 0) {
        colors.dangerRed
    } else {
        colors.textMeta
    }
    val durationSuffix = item.durationMs?.let { " ${formatDuration(it)}" }.orEmpty()
    val statusText = "${item.status.statusGlyph()} ${item.status.statusLabel()}$durationSuffix"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = BclawSpacing.EdgeLeft,
                end = BclawSpacing.EdgeRight,
                top = 4.dp,
                bottom = 4.dp,
            )
            .clickable(onClick = { expanded = !expanded })
            .testTag("command_execution_card_${item.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (expanded) "▾" else "▸",
            style = typography.meta,
            color = colors.accentCyan,
            modifier = Modifier.padding(end = 6.dp),
        )
        Text(
            text = item.command.ifBlank { "command" },
            modifier = Modifier.weight(1f),
            style = typography.code,
            color = colors.textMeta,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = statusText,
            style = typography.meta,
            color = statusColor,
        )
    }
    if (expanded || running) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = BclawSpacing.EdgeLeft,
                    end = BclawSpacing.EdgeRight,
                    bottom = BclawSpacing.MessageGap / 2,
                ),
            shape = BclawShape.Sharp,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceNear),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawLeftStripe(colors.accentCyan, 2.dp)
                    .padding(BclawSpacing.InsideCard),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (item.cwd.isNotBlank()) {
                    Text(text = item.cwd, style = typography.meta, color = colors.textDim)
                }
                Text(text = body, style = typography.code, color = colors.textPrimary)
            }
        }
    }
}

@Composable
private fun FileChangeCard(
    item: TimelineItemUi.FileChange,
) {
    val colors = LocalBclawColors.current
    val typography = LocalBclawTypography.current
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = BclawSpacing.EdgeLeft,
                end = BclawSpacing.EdgeRight,
                top = 4.dp,
                bottom = 4.dp,
            )
            .clickable(onClick = { expanded = !expanded })
            .testTag("file_change_card_${item.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (expanded) "▾" else "▸",
            style = typography.meta,
            color = colors.accentCyan,
            modifier = Modifier.padding(end = 6.dp),
        )
        Text(
            text = "${item.paths.size} files changed",
            modifier = Modifier.weight(1f),
            style = typography.body,
            color = colors.textMeta,
        )
        Text(
            text = "${item.status.statusGlyph()} ${item.status.statusLabel()}",
            style = typography.meta,
            color = colors.textMeta,
        )
    }
    if (expanded) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = BclawSpacing.EdgeLeft,
                    end = BclawSpacing.EdgeRight,
                    bottom = 4.dp,
                ),
            shape = BclawShape.Sharp,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceNear),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawLeftStripe(colors.accentCyan, 2.dp)
                    .padding(BclawSpacing.InsideCard),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item.changes.ifEmpty {
                    item.paths.map { path ->
                        TimelineItemUi.FileChangeEntry(path = path, additions = 0, deletions = 0, diff = "")
                    }
                }.forEach { change ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(BclawSpacing.InlineGap),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = change.path, modifier = Modifier.weight(1f), style = typography.code, color = colors.textPrimary)
                        Text(text = "+${change.additions} / -${change.deletions}", style = typography.meta, color = colors.textMeta)
                    }
                }
                if (item.diff.isNotBlank()) {
                    Text(text = item.diff, style = typography.code, color = colors.textPrimary)
                }
            }
        }
    }
}

@Composable
private fun ReasoningRow(
    item: TimelineItemUi.Reasoning,
) {
    val colors = LocalBclawColors.current
    val typography = LocalBclawTypography.current
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    val isStreaming = item.summary.isBlank()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = BclawSpacing.EdgeLeft,
                end = BclawSpacing.EdgeRight,
                top = 4.dp,
                bottom = 4.dp,
            )
            .clickable(enabled = !isStreaming, onClick = { expanded = !expanded }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (isStreaming) "THINKING ..." else if (expanded) "▾ THINKING" else "▸ THINKING",
            style = typography.meta,
            color = colors.accentCyan,
        )
    }
    if (expanded && !isStreaming) {
        Text(
            text = item.summary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = BclawSpacing.EdgeLeft,
                    end = BclawSpacing.EdgeRight,
                    bottom = 4.dp,
                ),
            style = typography.body,
            color = colors.textMeta,
        )
    }
}

@Composable
private fun McpToolCallCard(
    item: TimelineItemUi.McpToolCall,
) {
    val colors = LocalBclawColors.current
    val typography = LocalBclawTypography.current
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    val durationText = item.durationMs?.let { " ${formatDuration(it)}" }.orEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = BclawSpacing.EdgeLeft,
                end = BclawSpacing.EdgeRight,
                top = 4.dp,
                bottom = 4.dp,
            )
            .clickable(onClick = { expanded = !expanded }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (expanded) "▾" else "▸",
            style = typography.meta,
            color = colors.accentCyan,
            modifier = Modifier.padding(end = 6.dp),
        )
        Text(
            text = "${item.server}/${item.tool}",
            modifier = Modifier.weight(1f),
            style = typography.code,
            color = colors.textMeta,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${item.status.statusGlyph()}$durationText",
            style = typography.meta,
            color = colors.textMeta,
        )
    }
    if (expanded) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = BclawSpacing.EdgeLeft,
                    end = BclawSpacing.EdgeRight,
                    bottom = 4.dp,
                ),
            shape = BclawShape.Sharp,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceNear),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawLeftStripe(colors.accentCyan, 2.dp)
                    .padding(BclawSpacing.InsideCard),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (item.arguments.isNotBlank()) {
                    Text(text = item.arguments, style = typography.code, color = colors.textMeta)
                }
                if (item.error.isNotBlank()) {
                    Text(text = item.error, style = typography.code, color = colors.dangerRed)
                } else if (item.result.isNotBlank()) {
                    Text(text = item.result, style = typography.code, color = colors.textPrimary)
                }
            }
        }
    }
}

@Composable
private fun WebSearchRow(
    item: TimelineItemUi.WebSearch,
) {
    val colors = LocalBclawColors.current
    val typography = LocalBclawTypography.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = BclawSpacing.EdgeLeft,
                end = BclawSpacing.EdgeRight,
                top = BclawSpacing.MessageGap / 2,
                bottom = BclawSpacing.MessageGap / 2,
            ),
        horizontalArrangement = Arrangement.spacedBy(BclawSpacing.InlineGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "SEARCH",
            style = typography.meta,
            color = colors.accentCyan,
        )
        Text(
            text = item.query,
            style = typography.body,
            color = colors.textPrimary,
            maxLines = 1,
        )
    }
}

@Composable
private fun PlanCard(
    item: TimelineItemUi.Plan,
) {
    val colors = LocalBclawColors.current
    val typography = LocalBclawTypography.current
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = BclawSpacing.EdgeLeft,
                end = BclawSpacing.EdgeRight,
                top = 4.dp,
                bottom = 4.dp,
            )
            .clickable(onClick = { expanded = !expanded }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (expanded) "▾ PLAN" else "▸ PLAN",
            style = typography.meta,
            color = colors.accentCyan,
        )
    }
    if (expanded) {
        Text(
            text = item.text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = BclawSpacing.EdgeLeft,
                    end = BclawSpacing.EdgeRight,
                    bottom = 4.dp,
                ),
            style = typography.body,
            color = colors.textMeta,
        )
    }
}

@Composable
private fun ContextCompactionSeparator(
    @Suppress("UNUSED_PARAMETER") item: TimelineItemUi.ContextCompaction,
) {
    val colors = LocalBclawColors.current
    val typography = LocalBclawTypography.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = BclawSpacing.EdgeLeft,
                end = BclawSpacing.EdgeRight,
                top = BclawSpacing.MessageGap / 2,
                bottom = BclawSpacing.MessageGap / 2,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HorizontalDivider(color = colors.divider, thickness = 1.dp)
        Text(
            text = "-- context compacted --",
            style = typography.meta,
            color = colors.textDim,
        )
        HorizontalDivider(color = colors.divider, thickness = 1.dp)
    }
}

@Composable
private fun DynamicToolCallCard(
    item: TimelineItemUi.DynamicToolCall,
) {
    val colors = LocalBclawColors.current
    val typography = LocalBclawTypography.current
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    val durationText = item.durationMs?.let { " ${formatDuration(it)}" }.orEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = BclawSpacing.EdgeLeft,
                end = BclawSpacing.EdgeRight,
                top = 4.dp,
                bottom = 4.dp,
            )
            .clickable(onClick = { expanded = !expanded }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (expanded) "▾" else "▸",
            style = typography.meta,
            color = colors.accentCyan,
            modifier = Modifier.padding(end = 6.dp),
        )
        Text(
            text = item.tool,
            modifier = Modifier.weight(1f),
            style = typography.code,
            color = colors.textMeta,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${item.status.statusGlyph()}$durationText",
            style = typography.meta,
            color = colors.textMeta,
        )
    }
    if (expanded) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = BclawSpacing.EdgeLeft, end = BclawSpacing.EdgeRight, bottom = 4.dp),
            shape = BclawShape.Sharp,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceNear),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().drawLeftStripe(colors.accentCyan, 2.dp).padding(BclawSpacing.InsideCard),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (item.arguments.isNotBlank()) {
                    Text(text = item.arguments, style = typography.code, color = colors.textMeta)
                }
            }
        }
    }
}

@Composable
private fun SubAgentCard(
    item: TimelineItemUi.SubAgent,
) {
    val colors = LocalBclawColors.current
    val typography = LocalBclawTypography.current
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = BclawSpacing.EdgeLeft,
                end = BclawSpacing.EdgeRight,
                top = 4.dp,
                bottom = 4.dp,
            )
            .clickable(onClick = { expanded = !expanded }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (expanded) "▾" else "▸",
            style = typography.meta,
            color = colors.accentCyan,
            modifier = Modifier.padding(end = 6.dp),
        )
        Text(
            text = "SUB-AGENT ${item.tool}",
            modifier = Modifier.weight(1f),
            style = typography.code,
            color = colors.textMeta,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item.status.statusGlyph(),
            style = typography.meta,
            color = colors.textMeta,
        )
    }
    if (expanded) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = BclawSpacing.EdgeLeft, end = BclawSpacing.EdgeRight, bottom = 4.dp),
            shape = BclawShape.Sharp,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceNear),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().drawLeftStripe(colors.accentCyan, 2.dp).padding(BclawSpacing.InsideCard),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = item.prompt, style = typography.body, color = colors.textPrimary)
                if (item.model.isNotBlank()) {
                    Text(text = item.model, style = typography.meta, color = colors.textDim)
                }
            }
        }
    }
}

@Composable
private fun UnsupportedItemRow(
    item: TimelineItemUi.Unsupported,
) {
    val colors = LocalBclawColors.current
    val typography = LocalBclawTypography.current
    Text(
        text = "-- unsupported item (${item.kind}) --",
        modifier = Modifier.padding(
            start = BclawSpacing.EdgeLeft,
            end = BclawSpacing.EdgeRight,
            top = BclawSpacing.MessageGap / 2,
            bottom = BclawSpacing.MessageGap / 2,
        ),
        style = typography.meta,
        color = colors.textDim,
    )
}

@Composable
private fun ErrorRow(
    item: TimelineItemUi.Error,
) {
    val colors = LocalBclawColors.current
    val typography = LocalBclawTypography.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = BclawSpacing.EdgeLeft,
                end = BclawSpacing.EdgeRight,
                top = BclawSpacing.MessageGap / 2,
                bottom = BclawSpacing.MessageGap / 2,
            )
            .drawLeftStripe(colors.dangerRed, 2.dp)
            .background(colors.surfaceElevated, BclawShape.Sharp),
    ) {
        Text(
            text = item.message,
            modifier = Modifier.padding(BclawSpacing.InsideCard),
            style = typography.body,
            color = colors.textPrimary,
        )
    }
}

internal fun Modifier.drawLeftStripe(
    color: Color,
    width: Dp,
): Modifier {
    return drawBehind {
        drawRect(
            color = color,
            topLeft = Offset.Zero,
            size = Size(width.toPx(), size.height),
        )
    }
}

internal fun String.statusGlyph(): String {
    return when (this) {
        "inProgress",
        "running",
        -> ">"

        "completed",
        "success",
        "succeeded",
        -> "ok"

        "failed" -> "x"
        "declined",
        "interrupted",
        -> "||"

        else -> "."
    }
}

internal fun String.statusLabel(): String {
    return when (this) {
        "inProgress" -> "running"
        "completed" -> "completed"
        "failed" -> "failed"
        "interrupted" -> "interrupted"
        "declined" -> "declined"
        else -> if (isBlank()) "pending" else this
    }
}

internal fun TimelineItemUi.CommandExecution.commandSummary(): String {
    val exitPart = exitCode?.let { "exit $it" } ?: status.statusLabel()
    val durationPart = durationMs?.let { formatDuration(it) }
    return listOfNotNull(exitPart, durationPart).joinToString(" · ")
}

private fun String.tailLines(count: Int): String {
    return lineSequence()
        .filter { it.isNotBlank() }
        .toList()
        .takeLast(count)
        .joinToString("\n")
}

internal fun String.isRunningStatus(): Boolean {
    return this == "inProgress" || this == "running"
}

private fun String.truncate(maxLength: Int): String {
    return if (length <= maxLength) this else take(maxLength) + "..."
}

private fun formatDuration(ms: Long): String {
    return when {
        ms < 1000 -> "${ms}ms"
        ms < 60_000 -> "%.1fs".format(ms / 1000.0)
        else -> "${ms / 60_000}m ${(ms % 60_000) / 1000}s"
    }
}
