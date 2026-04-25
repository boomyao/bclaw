package com.bclaw.app.ui.showcase.states

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bclaw.app.ui.showcase.MetroPalette
import com.bclaw.app.ui.showcase.ShowGestureBar
import com.bclaw.app.ui.showcase.ShowStatusBar
import com.bclaw.app.ui.showcase.ShowcaseNeutrals
import com.bclaw.app.ui.theme.BclawTheme

/**
 * A.01 · Welcome screen — mirrors `screens-states.jsx:4-54`.
 *
 * Hero headline with accent last line, four agent tiles, pair CTA, fallback paste hint.
 * CSS `em`-letter-spacing is approximated with `sp` since Space Grotesk's advance table
 * keeps the visual match inside 1px at display sizes.
 */
@Composable
fun ColumnScope.ShowOnboarding1() {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    ShowStatusBar()
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .background(colors.surfaceBase)
            .padding(start = 28.dp, end = 28.dp, top = 48.dp, bottom = 28.dp),
    ) {
        Text(
            text = "BCLAW · V2.1",
            style = type.meta,
            color = colors.inkTertiary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "your dev",
            fontSize = 44.sp,
            lineHeight = 42.sp,
            letterSpacing = (-1.5).sp,
            fontWeight = FontWeight.Light,
            color = colors.inkPrimary,
            style = type.display,
        )
        Text(
            text = "box, in",
            fontSize = 44.sp,
            lineHeight = 42.sp,
            letterSpacing = (-1.5).sp,
            fontWeight = FontWeight.Light,
            color = colors.inkPrimary,
            style = type.display,
        )
        Text(
            text = "your pocket.",
            fontSize = 44.sp,
            lineHeight = 42.sp,
            letterSpacing = (-1.5).sp,
            fontWeight = FontWeight.Light,
            color = colors.accent,
            style = type.display,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "pair any mac / linux box on your tailnet. drive codex · claude · gemini from here.",
            style = type.bodyLarge,
            color = colors.inkSecondary,
        )
        Spacer(Modifier.weight(1f))
        val agents = listOf(
            "codex" to MetroPalette.Cyan,
            "claude" to MetroPalette.Magenta,
            "gemini" to MetroPalette.Orange,
            "kimi" to MetroPalette.Lime,
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                agents.take(2).forEach { (name, accent) ->
                    AgentTile(name = name, accent = accent, modifier = Modifier.weight(1f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                agents.drop(2).forEach { (name, accent) ->
                    AgentTile(name = name, accent = accent, modifier = Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        PrimaryCta(text = "PAIR A DEVICE →")
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "or paste ",
                style = type.mono,
                color = colors.inkTertiary,
            )
            Text(
                text = "bclaw://",
                style = type.mono,
                color = colors.accent,
            )
            Text(
                text = " link",
                style = type.mono,
                color = colors.inkTertiary,
            )
        }
    }
    ShowGestureBar()
}

@Composable
private fun AgentTile(
    name: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    Row(
        modifier = modifier
            .background(colors.surfaceOverlay)
            .border(1.dp, colors.borderSubtle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 16.dp)
                .background(accent),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = name.uppercase(),
            color = colors.inkPrimary,
            style = type.mono,
            letterSpacing = 1.sp,
        )
    }
}

/**
 * A.02 · Pair QR · step 1 — mirrors `screens-states.jsx:57-130`.
 *
 * CLI command card · divider · checker-pattern QR · waiting-for-handshake status line.
 */
@Composable
fun ColumnScope.ShowPairQR() {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    ShowStatusBar()
    PairStepHeader(step = "STEP 1 / 3", title = "pair a device")
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .background(colors.surfaceBase)
            .padding(24.dp),
    ) {
        Text(
            text = "on your mac, open a terminal and run:",
            style = type.body,
            color = colors.inkSecondary,
        )
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ShowcaseNeutrals.N950)
                .border(1.dp, ShowcaseNeutrals.N800)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "$",
                style = type.mono,
                color = MetroPalette.Lime,
                fontSize = 13.sp,
            )
            Text(
                text = "brew install bclaw && bclaw pair",
                style = type.mono,
                color = ShowcaseNeutrals.N50,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .border(1.dp, ShowcaseNeutrals.N500)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = "COPY",
                    style = type.micro,
                    color = ShowcaseNeutrals.N400,
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(colors.borderSubtle),
            )
            Text(
                text = "  OR SCAN  ",
                style = type.meta,
                color = colors.inkTertiary,
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(colors.borderSubtle),
            )
        }
        Spacer(Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(220.dp)
                .border(8.dp, colors.inkPrimary)
                .background(colors.surfaceBase),
            contentAlignment = Alignment.Center,
        ) {
            // 7×7 QR-style cell grid; the pattern is illustrative (matches the JSX index set).
            val active = setOf(0, 1, 2, 3, 6, 7, 8, 11, 12, 13, 14, 16, 18, 19, 22, 25, 27, 29, 30, 32, 35, 37, 38, 39, 41, 44, 46)
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                userScrollEnabled = false,
            ) {
                items((0 until 49).toList()) { i ->
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .background(if (i in active) colors.inkPrimary else Color.Transparent),
                    )
                }
            }
            // Logo chip in the middle
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White)
                    .border(2.dp, colors.inkPrimary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "bclaw",
                    color = colors.inkPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    style = type.body,
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = "waiting for handshake…",
            style = type.mono,
            color = colors.inkTertiary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        // Indeterminate progress bar — single accent-colored 30% slab at the leading edge.
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(colors.borderSubtle),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.3f)
                    .height(2.dp)
                    .background(colors.accent),
            )
        }
    }
    ShowGestureBar()
}

/**
 * A.03 · Pair confirm · step 2 — mirrors `screens-states.jsx:133-220`.
 *
 * Device card (name · os · 6-digit verify code) + permissions list + cancel/pair CTA row.
 */
@Composable
fun ColumnScope.ShowPairFound() {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    ShowStatusBar()
    PairStepHeader(step = "STEP 2 / 3", title = "confirm this is you")
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .background(colors.surfaceBase),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(20.dp),
        ) {
            // Device detection card — accent 2dp border on accent-soft fill.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, colors.accent)
                    .background(colors.accentSoft)
                    .padding(horizontal = 16.dp, vertical = 20.dp),
            ) {
                Text(
                    text = "DEVICE DETECTED",
                    style = type.meta,
                    color = colors.accent,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "ember",
                    style = type.h1.copy(fontWeight = FontWeight.Medium, fontSize = 22.sp),
                    color = colors.inkPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "macbook pro · darwin 24.2.0",
                    style = type.mono,
                    color = colors.inkSecondary,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceOverlay)
                        .border(1.dp, colors.borderSubtle)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "47-2K-9M",
                        style = type.mono,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 4.sp,
                        color = colors.inkPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "VERIFY CODE",
                        style = type.micro,
                        color = colors.inkTertiary,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "this code should match what's shown on your mac's terminal. if it doesn't, cancel.",
                    style = type.bodySmall,
                    color = colors.inkSecondary,
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "permissions this device will grant:",
                style = type.h3,
                color = colors.inkPrimary,
            )
            Spacer(Modifier.height(8.dp))
            val perms = listOf(
                "read & write files in chosen project roots" to "~/code, ~/docs (you pick)",
                "run shell commands as you" to "with per-command approval for new ones",
                "capture screen for remote desktop" to "only when you open the remote tool",
            )
            perms.forEach { (title, sub) ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = title,
                        style = type.body.copy(fontWeight = FontWeight.Medium),
                        color = colors.inkPrimary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = sub,
                        style = type.monoSmall,
                        color = colors.inkTertiary,
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.borderSubtle),
                )
            }
        }
        // CTA row — cancel / pair as two side-by-side full-bleed buttons.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.borderSubtle),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "CANCEL",
                    style = type.body.copy(
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp,
                    ),
                    color = colors.inkSecondary,
                )
            }
            Box(
                modifier = Modifier
                    .weight(2f)
                    .background(colors.inkPrimary)
                    .padding(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "CODES MATCH · PAIR",
                    style = type.body.copy(
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 2.sp,
                    ),
                    color = colors.inkOnInverse,
                )
            }
        }
    }
    ShowGestureBar()
}

/**
 * A.04 · Pair done · step 3 — mirrors `screens-states.jsx:223-294`.
 *
 * Success hero + detected-agents badge card + project-root picker.
 */
@Composable
fun ColumnScope.ShowPairDone() {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    ShowStatusBar()
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .background(colors.surfaceBase)
            .padding(start = 28.dp, end = 28.dp, top = 48.dp, bottom = 28.dp),
    ) {
        Text(
            text = "PAIRED · STEP 3 / 3",
            style = type.meta,
            color = colors.roleLive,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "ember is now",
            fontSize = 36.sp,
            fontWeight = FontWeight.Light,
            lineHeight = 38.sp,
            letterSpacing = (-1).sp,
            color = colors.inkPrimary,
            style = type.display,
        )
        Text(
            text = "in your pocket.",
            fontSize = 36.sp,
            fontWeight = FontWeight.Light,
            lineHeight = 38.sp,
            letterSpacing = (-1).sp,
            color = colors.inkPrimary,
            style = type.display,
        )
        Spacer(Modifier.height(28.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.borderSubtle)
                .drawLeftRail(colors.roleLive, 3.dp)
                .background(colors.surfaceOverlay)
                .padding(14.dp),
        ) {
            Text(
                text = "4 AGENTS DETECTED",
                style = type.meta,
                color = colors.inkTertiary,
            )
            Spacer(Modifier.height(8.dp))
            val badges = listOf(
                "codex" to MetroPalette.Cyan,
                "claude" to MetroPalette.Magenta,
                "gemini" to MetroPalette.Orange,
                "kimi" to MetroPalette.Lime,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                badges.forEach { (name, accent) ->
                    Row(
                        modifier = Modifier
                            .border(1.dp, accent)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(Modifier.size(6.dp).background(accent))
                        Text(
                            text = name,
                            style = type.mono,
                            color = accent,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.borderSubtle)
                .background(colors.surfaceOverlay)
                .padding(14.dp),
        ) {
            Text(
                text = "PICK YOUR FIRST PROJECT ROOT",
                style = type.meta,
                color = colors.inkTertiary,
            )
            val roots = listOf("~/code/foo-api", "~/code/landing", "~/dotfiles")
            roots.forEachIndexed { i, path ->
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceBase)
                        .border(
                            width = if (i == 0) 1.5.dp else 1.dp,
                            color = if (i == 0) colors.accent else colors.borderSubtle,
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .border(1.5.dp, colors.inkPrimary)
                            .background(if (i == 0) colors.accent else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (i == 0) Text(text = "✓", fontSize = 10.sp, color = colors.accentInk)
                    }
                    Text(
                        text = path,
                        style = type.mono,
                        color = colors.inkPrimary,
                        fontSize = 13.sp,
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        PrimaryCta(text = "OPEN FIRST SESSION →")
    }
    ShowGestureBar()
}

/** Shared 3-step pair header — back arrow, step eyebrow, title. */
@Composable
private fun PairStepHeader(step: String, title: String) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.borderSubtle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "←",
            fontSize = 18.sp,
            color = colors.inkTertiary,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = step, style = type.meta, color = colors.inkTertiary)
            Spacer(Modifier.height(2.dp))
            Text(
                text = title,
                style = type.h3,
                color = colors.inkPrimary,
            )
        }
    }
}

/** Full-bleed inverted primary CTA — shared between A.01 / A.04. */
@Composable
internal fun PrimaryCta(text: String) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.inkPrimary)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = colors.inkOnInverse,
            style = type.body.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp,
                fontSize = 14.sp,
            ),
        )
    }
}

// ── shared modifiers ─────────────────────────────────────────────────────

/** Draws a left-hand accent rail at [width]. Mirrors the JSX `borderLeft` pattern. */
internal fun Modifier.drawLeftRail(color: Color, width: Dp = 3.dp): Modifier =
    this.drawBehind {
        drawRect(
            color = color,
            topLeft = Offset(0f, 0f),
            size = Size(width.toPx(), size.height),
        )
    }
