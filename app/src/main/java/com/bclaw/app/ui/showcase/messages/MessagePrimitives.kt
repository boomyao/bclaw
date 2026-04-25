package com.bclaw.app.ui.showcase.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bclaw.app.ui.showcase.MetroPalette
import com.bclaw.app.ui.showcase.ShowcaseNeutrals
import com.bclaw.app.ui.showcase.states.drawLeftRail
import com.bclaw.app.ui.theme.BclawTheme

/**
 * C · Session message types — pixel mirrors of `screens-session.jsx:5-359`.
 *
 * Each function is a reusable card/line renderer. They can be composed into any session
 * screen (live or showcase) — the session-rich demo screens in [SessionRichScreens.kt]
 * stitch them together in-order. Names and props match the JSX so cross-referencing the
 * design source is direct.
 */

// ── User message (right-aligned, accent-colored) ──────────────────────────
@Composable
fun ShowUserMsg(text: String, time: String? = null) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Box(modifier = Modifier.widthIn(max = 340.dp)) {
            Text(
                text = text,
                style = type.bodyLarge,
                color = colors.accent,
                textAlign = TextAlign.End,
            )
        }
        if (time != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = time,
                style = type.monoSmall,
                color = colors.inkMuted,
                letterSpacing = 1.sp,
            )
        }
    }
}

// ── Agent text block ─────────────────────────────────────────────────────
@Composable
fun ShowAgentText(
    streaming: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 14.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f, fill = false)) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides colors.inkPrimary,
            ) {
                // Caller can mix text + code spans (see SessionRich demos); we just lay the
                // composable content on the inkPrimary surface.
                Box { content() }
            }
        }
        if (streaming) {
            Spacer(Modifier.width(2.dp))
            Box(
                Modifier
                    .size(width = 8.dp, height = 14.dp)
                    .background(colors.accent),
            )
        }
    }
}

/** Single-text overload for the common case. */
@Composable
fun ShowAgentText(text: String, streaming: Boolean = false) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    ShowAgentText(streaming = streaming) {
        Text(text = text, style = type.bodyLarge, color = colors.inkPrimary)
    }
}

// ── EventBar (status bar inside the stream) ──────────────────────────────
@Composable
fun ShowEventBar(
    icon: String,
    label: String,
    color: Color,
    hint: String? = null,
) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceRaised)
            .border(1.dp, colors.borderSubtle)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = icon,
            style = type.mono,
            color = color,
            modifier = Modifier.width(14.dp),
            textAlign = TextAlign.Center,
        )
        Text(
            text = label,
            style = type.mono,
            color = colors.inkPrimary,
            modifier = Modifier.weight(1f),
        )
        if (hint != null) {
            Text(text = hint, style = type.mono, color = colors.inkTertiary)
        }
    }
}

// ── Reasoning (collapsed-by-default inline gutter) ────────────────────────
@Composable
fun ShowReasoning(summary: String, expanded: Boolean = false, body: String? = null) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawLeftRail(colors.borderSubtle, 2.dp)
                .padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = if (expanded) "v" else ">",
                style = type.mono,
                color = colors.inkTertiary,
            )
            Text(
                text = "THINKING",
                style = type.micro,
                color = colors.inkTertiary,
            )
            Text(
                text = "— $summary",
                style = type.mono,
                color = colors.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (expanded && body != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = body,
                style = type.bodySmall.copy(fontStyle = FontStyle.Italic),
                color = colors.inkSecondary,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

// ── Command / shell block ────────────────────────────────────────────────
/**
 * Terminal-style card — header strip (status · `$ cmd`) + N-line output pane.
 * JSX: `screens-session.jsx:68-107`. Skin is always the warm-paper terminal (N50→N950),
 * regardless of whether the host app runs in light or dark mode — the block is meant to
 * look like a literal terminal embedded in the transcript.
 */
@Composable
fun ShowCommandBlock(
    cmd: String,
    output: String,
    exitCode: Int? = null,
    running: Boolean = false,
    maxLines: Int = 3,
) {
    val type = BclawTheme.typography
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .border(1.dp, BclawTheme.colors.borderSubtle)
            .background(ShowcaseNeutrals.N950),
    ) {
        // Header strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ShowcaseNeutrals.N800)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val statusColor = if (running) MetroPalette.Lime
            else if (exitCode == 0) ShowcaseNeutrals.N400
            else MetroPalette.Red
            Text(
                text = when {
                    running -> "● RUNNING"
                    exitCode == 0 -> "✓ DONE"
                    else -> "✗ EXIT ${exitCode ?: "?"}"
                },
                style = type.micro,
                color = statusColor,
            )
            Text(
                text = "$ $cmd",
                style = type.mono,
                color = ShowcaseNeutrals.N50,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Output — fixed max-height so very long output doesn't dominate the card.
        Text(
            text = output,
            style = type.mono.copy(fontSize = 12.sp),
            color = ShowcaseNeutrals.N50,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = (maxLines * 18).dp)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
    Spacer(Modifier.height(10.dp))
}

// ── Approval card ────────────────────────────────────────────────────────
/**
 * JSX: `screens-session.jsx:110-180`. 2dp border, eyebrow strip, body block, optional
 * detail pre-formatted block, deny/allow CTA row, per-session persistence footer.
 * `danger=true` swaps everything to red + the CTA reads "Proceed" in a red fill.
 */
@Composable
fun ShowApprovalCard(
    title: String,
    subtitle: String? = null,
    detail: String? = null,
    danger: Boolean = false,
) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val stroke = if (danger) colors.roleError else colors.roleWarn
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .border(2.dp, stroke)
            .background(colors.surfaceOverlay),
    ) {
        // Eyebrow strip
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, stroke)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = if (danger) "⚠ DESTRUCTIVE" else "⚠ APPROVAL REQUIRED",
                style = type.meta,
                color = stroke,
                letterSpacing = 2.sp,
            )
        }
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = title,
                style = type.h3.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp),
                color = colors.inkPrimary,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = type.body,
                    color = colors.inkSecondary,
                )
            }
            if (detail != null) {
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceDeep)
                        .border(1.dp, colors.borderSubtle)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    detail.lines().forEach { ln ->
                        Text(
                            text = ln,
                            style = type.mono,
                            color = colors.inkPrimary,
                        )
                    }
                }
            }
        }
        // CTA row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.borderSubtle),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, colors.borderSubtle)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "DENY",
                    style = type.body.copy(
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp,
                        fontSize = 13.sp,
                    ),
                    color = colors.inkSecondary,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (danger) colors.roleError else colors.inkPrimary)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (danger) "PROCEED" else "ALLOW",
                    style = type.body.copy(
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp,
                        fontSize = 13.sp,
                    ),
                    color = if (danger) Color.White else colors.inkOnInverse,
                )
            }
        }
        // Per-session persistence footer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.borderSubtle)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Allow always for this session",
                style = type.monoSmall,
                color = colors.inkMuted,
            )
            Text(text = "›", style = type.monoSmall, color = colors.inkMuted)
        }
    }
    Spacer(Modifier.height(8.dp))
}

// ── Plan / Todo list ─────────────────────────────────────────────────────
/**
 * JSX: `screens-session.jsx:183-227`. Lime left rail, done/doing/pending states with
 * different glyphs + strikethrough on done.
 */
enum class PlanItemState { Done, Doing, Pending }

data class PlanItem(val state: PlanItemState, val text: String)

@Composable
fun ShowPlanCard(title: String, items: List<PlanItem>) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val lime = MetroPalette.Lime
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .drawLeftRail(lime, 3.dp)
            .border(1.dp, colors.borderSubtle)
            .background(colors.surfaceOverlay),
    ) {
        // Header strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.borderSubtle)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val done = items.count { it.state == PlanItemState.Done }
            Text(
                text = "PLAN · $done/${items.size}",
                style = type.meta,
                color = lime,
                letterSpacing = 2.sp,
            )
            Text(
                text = "tap step to open",
                style = type.micro,
                color = colors.inkMuted,
            )
        }
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val glyph = when (item.state) {
                        PlanItemState.Done -> "✓"
                        PlanItemState.Doing -> "◐"
                        PlanItemState.Pending -> "○"
                    }
                    val glyphColor = when (item.state) {
                        PlanItemState.Done -> lime
                        PlanItemState.Doing -> colors.accent
                        PlanItemState.Pending -> colors.inkMuted
                    }
                    Box(modifier = Modifier.width(16.dp).padding(top = 2.dp)) {
                        Text(text = glyph, style = type.mono, color = glyphColor, fontSize = 13.sp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.text,
                            style = type.body.copy(
                                fontSize = 14.sp,
                                textDecoration = if (item.state == PlanItemState.Done)
                                    androidx.compose.ui.text.style.TextDecoration.LineThrough
                                else null,
                            ),
                            color = if (item.state == PlanItemState.Done) colors.inkTertiary
                            else colors.inkPrimary,
                        )
                        if (item.state == PlanItemState.Doing) {
                            Text(
                                text = "IN PROGRESS…",
                                style = type.micro,
                                color = colors.accent,
                                letterSpacing = 1.sp,
                            )
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

// ── Image preview card ───────────────────────────────────────────────────
/**
 * JSX: `screens-session.jsx:230-257`. Solid gradient placeholder (we don't have real
 * image bytes here) + hint caption underneath.
 */
@Composable
fun ShowImagePreview(
    caption: String? = null,
    w: Int = 300,
    h: Int = 180,
    colors: List<Color> = listOf(MetroPalette.Violet, MetroPalette.Magenta, MetroPalette.Cyan),
) {
    val theme = BclawTheme.colors
    val type = BclawTheme.typography
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Box(
            modifier = Modifier
                .size(w.dp, h.dp)
                .border(1.dp, theme.borderSubtle)
                .background(
                    Brush.linearGradient(
                        colors = colors,
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end = androidx.compose.ui.geometry.Offset(w.toFloat(), h.toFloat()),
                    ),
                ),
        ) {
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "PNG · ${w}×${h}",
                    color = Color.White,
                    style = type.micro,
                    letterSpacing = 1.sp,
                    fontSize = 9.sp,
                )
            }
        }
        if (caption != null) {
            Spacer(Modifier.height(4.dp))
            Text(text = caption, style = type.mono, color = theme.inkTertiary)
        }
    }
    Spacer(Modifier.height(10.dp))
}

// ── Diff block ───────────────────────────────────────────────────────────
/**
 * JSX: `screens-session.jsx:260-293`. Header (path · +adds · −rems) + gutter+marker+code
 * tri-column rows, tinted per line type.
 */
data class DiffLine(val n: Int, val t: Char, val code: String)

@Composable
fun ShowDiffBlock(path: String, added: Int, removed: Int, lines: List<DiffLine>) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .border(1.dp, colors.borderSubtle)
            .background(colors.surfaceOverlay),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.borderSubtle)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = path,
                style = type.mono,
                color = colors.inkPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "+$added",
                style = type.mono,
                color = colors.roleLive,
            )
            Text(
                text = "−$removed",
                style = type.mono,
                color = colors.roleError,
            )
        }
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            lines.forEach { l ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            when (l.t) {
                                '+' -> colors.diffAddBg
                                '-' -> colors.diffRemBg
                                else -> Color.Transparent
                            }
                        )
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = l.n.toString().padStart(3),
                        style = type.mono,
                        color = colors.inkMuted,
                        modifier = Modifier.width(26.dp),
                        textAlign = TextAlign.End,
                    )
                    Text(
                        text = l.t.toString(),
                        style = type.mono,
                        color = when (l.t) {
                            '+' -> colors.roleLive
                            '-' -> colors.roleError
                            else -> colors.inkMuted
                        },
                        modifier = Modifier.width(16.dp),
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = l.code,
                        style = type.mono,
                        color = if (l.t == '-') colors.inkTertiary else colors.inkPrimary,
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}

// ── Long-output fold ─────────────────────────────────────────────────────
/**
 * JSX: `screens-session.jsx:325-359`. Preview N lines of a much longer output with a
 * gradient fade at the bottom; trailing "+ show X more lines" row.
 */
@Composable
fun ShowLongOutputFold(lines: List<String>, shown: Int = 3, total: Int) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.borderSubtle)
                .background(ShowcaseNeutrals.N950)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Column {
                lines.take(shown).forEach { line ->
                    Text(text = line, style = type.mono, color = ShowcaseNeutrals.N100)
                }
            }
            // Fade to paper — approximate the JSX linear gradient with a 20dp overlay.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(20.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, ShowcaseNeutrals.N950),
                        ),
                    ),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.borderSubtle)
                .background(colors.surfaceRaised)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "+ show ${total - shown} more lines",
                style = type.mono,
                color = colors.inkTertiary,
            )
            Text(
                text = "$total total",
                style = type.mono,
                color = colors.inkMuted,
            )
        }
    }
    Spacer(Modifier.height(10.dp))
}
