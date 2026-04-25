package com.bclaw.app.ui.showcase.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bclaw.app.ui.showcase.MetroPalette
import com.bclaw.app.ui.showcase.ShowCrumb
import com.bclaw.app.ui.showcase.ShowGestureBar
import com.bclaw.app.ui.showcase.ShowStatusBar
import com.bclaw.app.ui.showcase.ShowTab
import com.bclaw.app.ui.showcase.ShowTabStrip
import com.bclaw.app.ui.showcase.ShowcaseNeutrals
import com.bclaw.app.ui.theme.BclawTheme

/**
 * D · Terminal sidecar — pixel mirrors of `screens-terminal.jsx`.
 *
 * All three terminal variants render on a true-black skin regardless of app theme
 * (that's the shell convention: a terminal looks like a terminal). Agent-assisted rows
 * use the cyan accent; per-project favourite chips line up above the output pane; the
 * pinned-above-keyboard key-row sits at the bottom with symbolic commonly-used keys.
 */

// ── D.01 · Full terminal · chips + keyrow ────────────────────────────────
/**
 * Body-only variant — paints the terminal card without fake status/gesture bars so live
 * sidecars can embed it directly inside their own full-screen overlay. The showcase wrapper
 * below adds the mocked device chrome.
 */
@Composable
fun TerminalChipsContent(modifier: Modifier = Modifier) {
    Column(modifier = modifier.background(ShowcaseNeutrals.N0)) {
        TerminalTabRow(
            agent = "codex",
            agentColor = MetroPalette.Cyan,
            label = "ember: ~/code/foo-api",
            status = "zsh",
            statusLive = true,
        )
        ChipsRow(
            chips = listOf("git status", "npm test", "npm run dev", "git diff HEAD~1", "pnpm i", "pwd", "⋯"),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TerminalLine(text = "ember% git status", muted = true)
            TerminalLine(text = "On branch fix-login")
            TerminalLine(text = "Your branch is ahead of 'origin/fix-login' by 2 commits.")
            Spacer(Modifier.height(4.dp))
            TerminalLine(text = "Changes not staged for commit:")
            TerminalLine(
                text = "    modified:   src/auth/session.ts",
                color = MetroPalette.Lime,
            )
            TerminalLine(
                text = "    modified:   src/auth/index.ts",
                color = MetroPalette.Lime,
            )
            TerminalLine(
                text = "    deleted:    src/auth/legacy.ts",
                color = MetroPalette.Red,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TerminalLine(text = "ember% ", muted = true)
                TerminalLine(text = "npm t")
                // Block cursor highlighting the suffix
                Box(
                    modifier = Modifier
                        .background(ShowcaseNeutrals.N1000)
                        .padding(horizontal = 2.dp),
                ) {
                    TerminalLine(text = "est", color = ShowcaseNeutrals.N0)
                }
            }
        }
        // Agent-assisted suggestion row — dashed cyan divider.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MetroPalette.Cyan.copy(alpha = 0.08f))
                .drawDashedTopBorder(MetroPalette.Cyan, 1.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "CODEX",
                style = BclawTheme.typography.micro,
                color = MetroPalette.Cyan,
                letterSpacing = 1.sp,
            )
            Text(
                text = "want me to run this and fix failures?",
                style = BclawTheme.typography.mono,
                color = ShowcaseNeutrals.N400,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .background(MetroPalette.Cyan)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = "GO",
                    style = BclawTheme.typography.micro,
                    color = ShowcaseNeutrals.N0,
                    letterSpacing = 1.sp,
                )
            }
        }
        KeyRow(keys = listOf("esc", "tab", "^", "~", "/", "|", "-", "←", "→"))
    }
}

@Composable
fun ColumnScope.ShowTerminalChips() {
    ShowStatusBar(dark = true)
    TerminalChipsContent(Modifier.weight(1f).fillMaxWidth())
    ShowGestureBar(dark = true)
}

// ── D.02 · Split · chat above, shell below ───────────────────────────────
/**
 * Body-only split view — chat pane above, terminal below, with a draggable divider and
 * key row. Live sidecar embeds it inside its overlay chrome; showcase wrapper adds the
 * fake device chrome (tab strip / crumb) per the JSX source.
 */
@Composable
fun TerminalSplitContent(modifier: Modifier = Modifier) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    Column(modifier = modifier) {
    // Top chat pane
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .background(colors.surfaceBase)
            .padding(12.dp),
    ) {
        Text(
            text = "let me run the tests and see:",
            style = type.bodyLarge,
            color = colors.inkPrimary,
        )
    }
    // Draggable divider
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(colors.surfaceDeep)
            .border(1.dp, colors.borderSubtle)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(width = 24.dp, height = 2.dp).background(colors.inkTertiary))
        Text(
            text = "TERMINAL · DRAG TO RESIZE",
            style = type.micro,
            color = colors.inkTertiary,
            letterSpacing = 1.5.sp,
        )
        Spacer(Modifier.weight(1f))
        Text(text = "⤢", style = type.monoSmall, color = colors.inkTertiary)
        Text(text = "✕", style = type.monoSmall, color = colors.inkTertiary)
    }
    // Bottom terminal pane
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .background(ShowcaseNeutrals.N0)
            .padding(10.dp),
    ) {
        TerminalLine(text = "$ npm test", muted = true)
        Spacer(Modifier.height(6.dp))
        TerminalLine(text = "PASS  src/auth/index.test.ts")
        TerminalLine(text = "FAIL  src/auth/session.test.ts", color = MetroPalette.Red)
        Spacer(Modifier.height(4.dp))
        TerminalLine(
            text = "    ● session › expires on null token",
            muted = true,
        )
        Spacer(Modifier.height(2.dp))
        TerminalLine(text = "        expected: Error", color = ShowcaseNeutrals.N500)
        TerminalLine(text = "        received: undefined", color = ShowcaseNeutrals.N500)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TerminalLine(text = "$ ", muted = true)
            Box(
                modifier = Modifier
                    .background(ShowcaseNeutrals.N1000)
                    .padding(horizontal = 2.dp),
            ) {
                TerminalLine(text = "_", color = ShowcaseNeutrals.N0)
            }
        }
    }
    KeyRow(keys = listOf("esc", "tab", "^", "~", "/", "|", "-", "←", "→"), light = true)
    }  // end outer Column of TerminalSplitContent
}

@Composable
fun ColumnScope.ShowTerminalSplit() {
    ShowStatusBar()
    ShowTabStrip(
        tabs = listOf(ShowTab("fix-login", "foo-api/fix", MetroPalette.Cyan, running = true)),
        activeId = "fix-login",
    )
    ShowCrumb(device = "ember", project = "foo-api", session = "fix-login", running = true)
    TerminalSplitContent(Modifier.weight(1f).fillMaxWidth())
    ShowGestureBar()
}

// ── D.03 · Recording (rec + scrubber + share .cast) ──────────────────────
@Composable
fun TerminalRecordingContent(modifier: Modifier = Modifier) {
    val type = BclawTheme.typography
    Column(modifier = modifier.background(ShowcaseNeutrals.N0)) {
        // Header with REC pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .border(1.dp, ShowcaseNeutrals.N200)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "ember: ~/code/foo-api",
                style = type.mono,
                color = ShowcaseNeutrals.N400,
                modifier = Modifier.weight(1f),
            )
            Row(
                modifier = Modifier
                    .border(1.dp, MetroPalette.Red)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(8.dp).background(MetroPalette.Red))
                Text(
                    text = "REC 00:42",
                    style = type.micro,
                    color = MetroPalette.Red,
                    letterSpacing = 1.sp,
                )
            }
        }
        // Terminal output
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            TerminalLine(text = "$ docker compose up -d", muted = true)
            TerminalLine(text = "[+] Running 3/3")
            TerminalLine(text = " ✔ Container redis-1     Started", color = MetroPalette.Lime)
            TerminalLine(text = " ✔ Container postgres-1  Started", color = MetroPalette.Lime)
            TerminalLine(text = " ✔ Container foo-api-1   Started", color = MetroPalette.Lime)
            Spacer(Modifier.height(8.dp))
            TerminalLine(text = "$ curl localhost:3000/health", muted = true)
            TerminalLine(text = "{\"status\":\"ok\",\"version\":\"1.3.2\"}")
        }
        // Scrubber + CTAs
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ShowcaseNeutrals.N200)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = "00:42", style = type.monoSmall, color = ShowcaseNeutrals.N400)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .background(ShowcaseNeutrals.N200),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(4.dp)
                            .background(MetroPalette.Red),
                    )
                }
                Text(text = "01:00", style = type.monoSmall, color = ShowcaseNeutrals.N400)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TerminalCta(text = "STOP", modifier = Modifier.weight(1f))
                TerminalCta(text = "REPLAY", modifier = Modifier.weight(1f))
                TerminalCta(
                    text = "SHARE .CAST",
                    modifier = Modifier.weight(1f),
                    accent = MetroPalette.Cyan,
                )
            }
        }
    }
}

@Composable
fun ColumnScope.ShowTerminalRecording() {
    ShowStatusBar(dark = true)
    TerminalRecordingContent(Modifier.weight(1f).fillMaxWidth())
    ShowGestureBar(dark = true)
}

// ── Internals: repeated terminal building blocks ─────────────────────────

@Composable
private fun TerminalTabRow(
    agent: String,
    agentColor: Color,
    label: String,
    status: String,
    statusLive: Boolean,
) {
    val type = BclawTheme.typography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .border(1.dp, ShowcaseNeutrals.N200)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "▢", style = type.mono, color = agentColor)
        Text(
            text = label,
            style = type.mono,
            color = ShowcaseNeutrals.N400,
            modifier = Modifier.weight(1f),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (statusLive) {
                Box(Modifier.size(6.dp).background(MetroPalette.Lime))
            }
            Text(
                text = "● ${status.uppercase()}",
                style = type.micro,
                color = MetroPalette.Lime,
                letterSpacing = 1.sp,
            )
        }
    }
}

@Composable
private fun ChipsRow(chips: List<String>) {
    val type = BclawTheme.typography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ShowcaseNeutrals.N200)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        chips.forEachIndexed { i, c ->
            val isOverflow = i == chips.lastIndex && c == "⋯"
            Box(
                modifier = Modifier
                    .border(
                        width = 1.dp,
                        color = if (isOverflow) ShowcaseNeutrals.N400 else ShowcaseNeutrals.N300,
                    )
                    .background(if (isOverflow) Color.Transparent else ShowcaseNeutrals.N100)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    text = c,
                    style = type.mono,
                    color = if (isOverflow) ShowcaseNeutrals.N500 else ShowcaseNeutrals.N300,
                )
            }
        }
    }
}

/** 9-cell sticky key row (32dp tall keys). `light` uses paper palette for the split view. */
@Composable
private fun KeyRow(keys: List<String>, light: Boolean = false) {
    val type = BclawTheme.typography
    val bg = if (light) ShowcaseNeutrals.N950 else ShowcaseNeutrals.N0
    val keyBg = if (light) ShowcaseNeutrals.N900 else ShowcaseNeutrals.N100
    val keyBorder = if (light) ShowcaseNeutrals.N800 else ShowcaseNeutrals.N300
    val keyInk = if (light) ShowcaseNeutrals.N50 else ShowcaseNeutrals.N400
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .border(1.dp, if (light) ShowcaseNeutrals.N800 else ShowcaseNeutrals.N200)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        keys.forEach { k ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(keyBg)
                    .border(1.dp, keyBorder)
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = k, style = type.mono, color = keyInk)
            }
        }
    }
}

@Composable
private fun TerminalLine(
    text: String,
    muted: Boolean = false,
    color: Color = if (muted) ShowcaseNeutrals.N500 else ShowcaseNeutrals.N300,
) {
    Text(
        text = text,
        style = BclawTheme.typography.mono.copy(fontSize = 12.sp),
        color = color,
    )
}

@Composable
private fun TerminalCta(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color? = null,
) {
    Box(
        modifier = modifier
            .background(accent ?: ShowcaseNeutrals.N100)
            .border(
                width = 1.dp,
                color = if (accent != null) accent else ShowcaseNeutrals.N300,
            )
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = BclawTheme.typography.micro,
            color = if (accent != null) ShowcaseNeutrals.N0 else ShowcaseNeutrals.N300,
            letterSpacing = 1.sp,
        )
    }
}

private fun Modifier.drawDashedTopBorder(
    color: Color,
    strokeWidth: androidx.compose.ui.unit.Dp,
): Modifier = this.drawBehind {
    val stroke = strokeWidth.toPx()
    drawLine(
        color = color,
        start = androidx.compose.ui.geometry.Offset(0f, 0f),
        end = androidx.compose.ui.geometry.Offset(size.width, 0f),
        strokeWidth = stroke,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f),
    )
}
