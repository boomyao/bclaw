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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bclaw.app.domain.v2.TimelineItem
import com.bclaw.app.domain.v2.ToolStatus
import com.bclaw.app.ui.showcase.messages.DiffLine
import com.bclaw.app.ui.showcase.messages.ShowCommandBlock
import com.bclaw.app.ui.showcase.messages.ShowDiffBlock
import com.bclaw.app.ui.showcase.messages.ShowReasoning
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
        is TimelineItem.AgentImages -> AgentImagesRow(item)
        is TimelineItem.Unsupported -> UnsupportedRow(item)
    }
}

// ── User message ────────────────────────────────────────────────────────

@Composable
private fun UserMessageRow(item: TimelineItem.UserMessage) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    // Codex Desktop's "paste image" UX wraps the attached file(s) + real question into
    // a markdown preamble like "# Files mentioned by the user:\n## name: /abs/path\n...
    // ## My request for Codex:\n<question>". When we replay such a rollout we parse it
    // client-side so the image shows as a thumbnail and the text is just the question.
    val parsed = remember(item.text) { parseFilesMentionedPreamble(item.text) }
    val extractedImagePaths = parsed?.first.orEmpty()
    val displayText = parsed?.second ?: item.text

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = sp.pageGutter),
        horizontalArrangement = Arrangement.End,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 320.dp),
            horizontalAlignment = Alignment.End,
        ) {
            if (item.imageAttachments.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(sp.sp1)) {
                    item.imageAttachments.forEach { attachment ->
                        AsyncImage(
                            model = attachment.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(96.dp)
                                .background(colors.surfaceDeep),
                        )
                    }
                }
                if (displayText.isNotBlank() ||
                    item.fileAttachments.isNotEmpty() ||
                    extractedImagePaths.isNotEmpty()
                ) {
                    Spacer(Modifier.height(sp.sp1))
                }
            }
            if (extractedImagePaths.isNotEmpty()) {
                val launchViewer = LocalImageViewer.current
                Row(horizontalArrangement = Arrangement.spacedBy(sp.sp1)) {
                    extractedImagePaths.forEach { absPath ->
                        BridgeImage(
                            absPath = absPath,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(96.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { launchViewer(absPath) },
                                ),
                        )
                    }
                }
                if (displayText.isNotBlank() || item.fileAttachments.isNotEmpty()) {
                    Spacer(Modifier.height(sp.sp1))
                }
            }
            if (item.fileAttachments.isNotEmpty()) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(sp.sp1),
                ) {
                    item.fileAttachments.forEach { file ->
                        Row(
                            modifier = Modifier
                                .background(colors.surfaceRaised)
                                .padding(horizontal = sp.sp2, vertical = sp.sp1),
                            horizontalArrangement = Arrangement.spacedBy(sp.sp1),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("📄", style = type.bodySmall, color = colors.inkSecondary)
                            Text(
                                text = file.rel,
                                style = type.mono,
                                color = colors.inkSecondary,
                            )
                        }
                    }
                }
                if (displayText.isNotBlank()) Spacer(Modifier.height(sp.sp1))
            }
            if (displayText.isNotBlank()) {
                Text(
                    text = displayText,
                    style = type.bodyLarge,
                    color = colors.accent,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

/**
 * Parses the "# Files mentioned by the user:" preamble that codex Desktop emits
 * when the user attaches a pasted image to their prompt. Returns (imageAbsPaths,
 * cleanedQuestion) if the preamble is present, null otherwise.
 *
 * Only image-typed attachments are extracted into the first list — non-image
 * entries collapse into the cleaned question unchanged. Good enough for the Desktop
 * paste-image flow; iterate later if codex expands the format.
 */
private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "heic", "heif", "bmp")

private fun parseFilesMentionedPreamble(raw: String): Pair<List<String>, String>? {
    val text = raw.trimStart()
    if (!text.startsWith("# Files mentioned by the user:")) return null
    val lines = text.lines()
    val paths = mutableListOf<String>()
    var questionStart = -1
    for ((idx, line) in lines.withIndex()) {
        val trimmed = line.trim()
        if (trimmed.startsWith("## My request for Codex:")) {
            questionStart = idx + 1
            break
        }
        if (trimmed.startsWith("## ") && trimmed.contains(": ")) {
            val path = trimmed.substringAfter(": ").trim()
            val ext = path.substringAfterLast('.', "").lowercase()
            if (path.startsWith("/") && ext in IMAGE_EXTENSIONS) paths.add(path)
        }
    }
    val question = if (questionStart >= 0) {
        lines.drop(questionStart).joinToString("\n").trim()
    } else {
        // No explicit question marker — collapse to empty so the preamble doesn't
        // bleed through as text.
        ""
    }
    return paths to question
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

/**
 * Renders an ACP command execution as the v2.1 paper-terminal card (`ShowCommandBlock`).
 * The card packs status · cmd header · output tail in a warm-paper strip regardless of the
 * host theme — a command block should always look like a terminal, not like paper.
 *
 * Output is clipped at ~maxLines; long outputs still surface in the tool-run group fold or
 * future `LongOutputFold` integration. Running commands show a blinking cursor via
 * `ShowCommandBlock(running=true)`. Cancelled / Failed keep the non-zero exit code visible.
 */
@Composable
private fun CommandExecutionRow(item: TimelineItem.CommandExecution) {
    val running = item.status == ToolStatus.Pending || item.status == ToolStatus.Running
    val effectiveExit = when (item.status) {
        ToolStatus.Completed -> item.exitCode ?: 0
        ToolStatus.Cancelled -> item.exitCode ?: 130
        ToolStatus.Failed -> item.exitCode ?: 1
        else -> null
    }
    val outputText = if (item.outputTail.isNotBlank()) {
        item.outputTail
    } else if (running) {
        item.cwd?.let { "cwd: $it" } ?: "…"
    } else {
        "(no output)"
    }
    ShowCommandBlock(
        cmd = item.command.lines().firstOrNull().orEmpty(),
        output = outputText,
        exitCode = effectiveExit,
        running = running,
        maxLines = 6,
    )
}

// ── File change ─────────────────────────────────────────────────────────

/**
 * Renders a file-change tool call:
 *   - If the agent shipped a unified diff, parse it into per-file [DiffLine]s and use the
 *     v2.1 `ShowDiffBlock` (tri-column gutter + marker + code with add/rem tinting).
 *   - Otherwise, fall back to the compact `✎ N files · +X −Y` row — this matches pre-commit
 *     tool calls where the edit is in flight and the diff body isn't known yet.
 *
 * Diff parsing is deliberately forgiving: a missing/ill-formed hunk header just drops us back
 * to the compact row. Callers never see a crash on a malformed diff.
 */
@Composable
private fun FileChangeRow(item: TimelineItem.FileChange) {
    val diff = item.unifiedDiff
    if (diff.isNullOrBlank()) {
        CompactFileChangeRow(item)
        return
    }
    val parsed = remember(item.id, diff) { parseUnifiedDiff(diff) }
    if (parsed.isEmpty()) {
        CompactFileChangeRow(item)
        return
    }
    parsed.forEach { (path, lines) ->
        ShowDiffBlock(
            path = path.ifBlank { item.paths.firstOrNull().orEmpty() },
            added = lines.count { it.t == '+' },
            removed = lines.count { it.t == '-' },
            lines = lines,
        )
    }
}

@Composable
private fun CompactFileChangeRow(item: TimelineItem.FileChange) {
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

/**
 * Minimal unified-diff parser. Splits on `diff --git` or `--- a/…` headers, reads the first
 * `+++` line as the path, then walks hunks (`@@ -l,c +l,c @@`) accumulating `+`/`-`/` ` lines
 * with their new-file line numbers. Returns `[]` if the shape doesn't look like a diff —
 * callers fall back to the compact summary row in that case.
 */
private fun parseUnifiedDiff(diff: String): List<Pair<String, List<DiffLine>>> {
    if ("@@" !in diff) return emptyList()
    val files = mutableListOf<Pair<String, MutableList<DiffLine>>>()
    var currentPath = ""
    var currentLines = mutableListOf<DiffLine>()
    var newLineNo = 0
    fun flush() {
        if (currentPath.isNotBlank() && currentLines.isNotEmpty()) {
            files += currentPath to currentLines
        }
        currentLines = mutableListOf()
    }
    for (raw in diff.lines()) {
        when {
            raw.startsWith("diff --git") || raw.startsWith("--- ") -> {
                flush()
                currentPath = ""
            }
            raw.startsWith("+++ ") -> {
                currentPath = raw.removePrefix("+++ ").removePrefix("b/").trim()
            }
            raw.startsWith("@@") -> {
                // Hunk header: `@@ -l,c +l,c @@ …`. We only need the leading `+` side.
                val plus = raw.substringAfter("+").substringBefore(" ")
                newLineNo = plus.substringBefore(",").toIntOrNull() ?: 0
            }
            raw.startsWith("+") && !raw.startsWith("+++") -> {
                currentLines += DiffLine(n = newLineNo, t = '+', code = raw.drop(1))
                newLineNo++
            }
            raw.startsWith("-") && !raw.startsWith("---") -> {
                currentLines += DiffLine(n = newLineNo, t = '-', code = raw.drop(1))
            }
            raw.startsWith(" ") -> {
                currentLines += DiffLine(n = newLineNo, t = ' ', code = raw.drop(1))
                newLineNo++
            }
            // Skip "index …", "\ No newline at end of file", etc.
        }
    }
    flush()
    return files
}

// ── Reasoning ───────────────────────────────────────────────────────────

/**
 * Reasoning uses the v2.1 `ShowReasoning` gutter — `THINKING — <summary>` on a 2dp
 * border-subtle left rail. Tap toggles the expanded body; empty summaries stay collapsed
 * with a "thinking…" placeholder (the composable itself handles it).
 */
@Composable
private fun ReasoningRow(item: TimelineItem.Reasoning) {
    var expanded by remember(item.id) { mutableStateOf(false) }
    val summary = item.summary.lines().firstOrNull().orEmpty().ifBlank {
        if (item.streaming) "thinking…" else "(no summary)"
    }.take(120)
    Column(
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { if (item.summary.isNotBlank()) expanded = !expanded },
        ),
    ) {
        ShowReasoning(
            summary = summary,
            expanded = expanded && item.summary.isNotBlank(),
            body = item.summary.takeIf { expanded && it.isNotBlank() },
        )
    }
}

// ── Agent images (view_image tool) ──────────────────────────────────────

@Composable
private fun AgentImagesRow(item: TimelineItem.AgentImages) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = sp.pageGutter),
    ) {
        val caption = buildString {
            append(item.title)
            if (!item.sourcePath.isNullOrBlank()) {
                append(" · ")
                append(item.sourcePath.substringAfterLast('/'))
            }
        }
        Text(
            text = caption.uppercase(),
            style = type.meta,
            color = colors.inkTertiary,
        )
        when {
            item.imageUrls.isNotEmpty() -> {
                Spacer(Modifier.height(sp.sp2))
                item.imageUrls.forEach { url ->
                    coil.compose.AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceDeep),
                    )
                    Spacer(Modifier.height(sp.sp2))
                }
            }
            !item.sourcePath.isNullOrBlank() -> {
                // Thumbnail mode: 240dp square in the timeline; tap opens the
                // full-screen viewer. Bytes are bridged once + cached to app storage
                // (see BridgeImage), so both the thumbnail and the full-size view
                // read from the same local file.
                Spacer(Modifier.height(sp.sp2))
                val launchViewer = LocalImageViewer.current
                val path = item.sourcePath
                BridgeImage(
                    absPath = path,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(240.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { launchViewer(path) },
                        ),
                )
            }
            else -> {
                Spacer(Modifier.height(sp.sp1))
                Text(
                    text = "(loading image…)",
                    style = type.bodySmall,
                    color = colors.inkTertiary,
                )
            }
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
