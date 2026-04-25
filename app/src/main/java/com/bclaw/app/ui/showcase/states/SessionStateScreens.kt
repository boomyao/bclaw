package com.bclaw.app.ui.showcase.states

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bclaw.app.ui.showcase.DefaultShowTabs
import com.bclaw.app.ui.showcase.ShowComposer
import com.bclaw.app.ui.showcase.ShowCrumb
import com.bclaw.app.ui.showcase.ShowGestureBar
import com.bclaw.app.ui.showcase.ShowStatusBar
import com.bclaw.app.ui.showcase.ShowTabStrip
import com.bclaw.app.ui.showcase.ShowcaseNeutrals
import com.bclaw.app.ui.theme.BclawTheme

/**
 * A.05 · No sessions yet — post-pair empty state. Mirrors `screens-states.jsx:297-337`.
 *
 * Dashed `+` glyph, headline + helper, new / resume CTAs.
 */
@Composable
fun ColumnScope.ShowEmptyNoSessions() {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    ShowStatusBar()
    ShowTabStrip(tabs = emptyList(), activeId = null)
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .background(colors.surfaceBase)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .drawDashedBorder(colors.borderStrong, strokeWidthDp = 1.5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                fontSize = 44.sp,
                fontWeight = FontWeight.Thin,
                color = colors.inkTertiary,
                style = type.display,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "no sessions yet",
            style = type.h2.copy(fontWeight = FontWeight.Medium, fontSize = 20.sp),
            color = colors.inkPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "tap + above to start one. your last agent & project will carry over once you've opened one.",
            style = type.body,
            color = colors.inkSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(280.dp),
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InvertedPill(text = "NEW SESSION")
            OutlinedPill(text = "RESUME RECENT")
        }
    }
    ShowGestureBar()
}

/**
 * A.06 · New session · awaiting first prompt — mirrors `screens-states.jsx:340-389`.
 *
 * Hero `what's on the table today?` + 4 starter cards (first one accented as continue),
 * composer with starter-centric placeholder.
 */
@Composable
fun ColumnScope.ShowEmptyNewSession() {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    ShowStatusBar()
    ShowTabStrip(tabs = DefaultShowTabs + UntitledTab, activeId = "untitled")
    ShowCrumb(device = "ember · mac", project = "foo-api", session = "untitled")
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .background(colors.surfaceBase)
            .padding(start = 20.dp, end = 20.dp, top = 40.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "●", style = type.meta, color = colors.accent)
            Spacer(Modifier.width(6.dp))
            Text(
                text = "CODEX · READY",
                style = type.meta,
                color = colors.accent,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "what's on",
            fontSize = 28.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = (-0.8).sp,
            color = colors.inkPrimary,
            style = type.hero,
        )
        Text(
            text = "the table today?",
            fontSize = 28.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = (-0.8).sp,
            color = colors.inkPrimary,
            style = type.hero,
        )
        Spacer(Modifier.height(14.dp))
        Row {
            Text(
                text = "you're in ",
                style = type.body,
                color = colors.inkSecondary,
            )
            Text(
                text = "~/code/foo-api",
                style = type.mono,
                color = colors.inkPrimary,
                fontSize = 13.sp,
            )
            Text(
                text = ". starters to get going:",
                style = type.body,
                color = colors.inkSecondary,
            )
        }
        Spacer(Modifier.height(20.dp))
        val starters = listOf(
            Starter("Continue fix-login", "resumed · 2h ago · 14 turns", accent = true),
            Starter("Tour this repo", "reads README + key files"),
            Starter("Run the test suite", "npm test — watches & reports"),
            Starter("What changed since main?", "git diff + commit summary"),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            starters.forEach { StarterCard(it) }
        }
    }
    ShowComposer(placeholder = "type or pick a starter above…")
    ShowGestureBar()
}

/**
 * A.07 · Loading skeleton — mirrors `screens-states.jsx:392-428`.
 *
 * 5 rows of pulsing skeleton slabs + bottom status strip with blinking dot.
 */
@Composable
fun ColumnScope.ShowLoading() {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    ShowStatusBar()
    ShowTabStrip(tabs = DefaultShowTabs, activeId = "fix-login")
    ShowCrumb(device = "ember · mac", project = "foo-api", session = "fix-login")
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .background(colors.surfaceBase)
            .padding(16.dp),
    ) {
        val widths = listOf(0.65f, 0.85f, 0.55f, 0.75f, 0.40f)
        widths.forEachIndexed { i, w ->
            Box(
                Modifier
                    .fillMaxWidth(w)
                    .height(10.dp)
                    .background(colors.surfaceDeep),
            )
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth((w - 0.2f).coerceAtLeast(0.1f))
                    .height(10.dp)
                    .background(colors.surfaceDeep),
            )
            Spacer(Modifier.height(14.dp))
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.borderSubtle)
            .background(colors.surfaceRaised)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(8.dp).background(colors.accent))
        Text(
            text = "opening session…",
            style = type.mono,
            color = colors.inkTertiary,
        )
    }
    ShowGestureBar()
}

/**
 * A.08 · Tailnet dropped mid-turn — mirrors `screens-states.jsx:431-487`.
 *
 * Amber warning banner · dimmed last turn · queued-input strip at bottom.
 */
@Composable
fun ColumnScope.ShowDisconnected() {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    ShowStatusBar()
    ShowTabStrip(tabs = DefaultShowTabs, activeId = "fix-login")
    ShowCrumb(device = "ember · mac", project = "foo-api", session = "fix-login")
    // Warning banner
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.roleWarn)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text = "⚠", fontSize = 14.sp, color = ShowcaseNeutrals.N950)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "TAILNET OFFLINE",
                style = type.meta.copy(fontWeight = FontWeight.SemiBold),
                color = ShowcaseNeutrals.N950,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "session paused at turn 14. your input is queued locally.",
                style = type.mono,
                color = ShowcaseNeutrals.N900,
            )
        }
        Box(
            modifier = Modifier
                .border(1.dp, ShowcaseNeutrals.N950)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = "RETRY",
                style = type.micro,
                color = ShowcaseNeutrals.N950,
            )
        }
    }
    // Dimmed last turn
    Column(
        modifier = Modifier
            .weight(1f)
            .background(colors.surfaceBase)
            .alpha(0.6f)
            .padding(16.dp),
    ) {
        Text(
            text = "ok — let me run the tests and see what's red.",
            style = type.bodyLarge,
            color = colors.inkPrimary,
        )
        Spacer(Modifier.height(14.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.borderSubtle)
                .background(ShowcaseNeutrals.N950),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ShowcaseNeutrals.N800)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "◌ SUSPENDED · $ NPM TEST",
                    style = type.micro,
                    color = ShowcaseNeutrals.N700,
                )
            }
            Text(
                text = "reconnect to resume…",
                style = type.mono,
                color = ShowcaseNeutrals.N700,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
    // Queued input strip
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.borderSubtle)
            .background(colors.surfaceRaised)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "◌",
            style = type.mono,
            color = colors.roleWarn,
        )
        Text(
            text = "queued · \"add a test for the null case\"",
            style = type.mono,
            color = colors.inkSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "will send on reconnect",
            style = type.mono,
            color = colors.inkMuted,
        )
    }
    ShowGestureBar()
}

/**
 * A.09 · Agent crashed — mirrors `screens-states.jsx:490-548`.
 *
 * Red-bordered panic card with exit code, title, body, stack-trace preformat + two CTAs.
 */
@Composable
fun ColumnScope.ShowErrorCrashed() {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    ShowStatusBar()
    ShowTabStrip(tabs = DefaultShowTabs, activeId = "fix-login")
    ShowCrumb(device = "ember · mac", project = "foo-api", session = "fix-login")
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .background(colors.surfaceBase)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, colors.roleError)
                .background(colors.surfaceOverlay)
                .padding(14.dp),
        ) {
            Text(
                text = "× AGENT CRASHED · EXIT 139",
                style = type.meta,
                color = colors.roleError,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "codex stopped responding",
                style = type.h3.copy(fontSize = 18.sp, fontWeight = FontWeight.Medium),
                color = colors.inkPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "the process on ember died during a long tool call. your transcript is safe — nothing is lost.",
                style = type.body,
                color = colors.inkSecondary,
            )
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceDeep)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                listOf(
                    "thread 'acp-tool' panicked at:",
                    "  write(pipe): broken pipe",
                    "  /Users/you/.codex/bin/codex:847",
                    "note: run with `RUST_BACKTRACE=1`",
                ).forEach { line ->
                    Text(
                        text = line,
                        style = type.mono,
                        color = colors.inkSecondary,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(colors.inkPrimary)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "RESTART SESSION",
                        style = type.body.copy(
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.5.sp,
                            fontSize = 12.sp,
                        ),
                        color = colors.inkOnInverse,
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, colors.borderStrong)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "FORK TO CLAUDE",
                        style = type.body.copy(
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.5.sp,
                            fontSize = 12.sp,
                        ),
                        color = colors.inkPrimary,
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.borderSubtle)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = "send diagnostics to bclaw team? ",
                style = type.mono,
                color = colors.inkTertiary,
            )
            Text(
                text = "send →",
                style = type.mono,
                color = colors.accent,
            )
        }
    }
    ShowGestureBar()
}

// ── helpers ──────────────────────────────────────────────────────────────

private val UntitledTab = com.bclaw.app.ui.showcase.ShowTab(
    id = "untitled",
    label = "untitled",
    color = com.bclaw.app.ui.showcase.MetroPalette.Cyan,
)

private data class Starter(
    val heading: String,
    val sub: String,
    val accent: Boolean = false,
)

@Composable
private fun StarterCard(s: Starter) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (s.accent) colors.accentSoft else colors.surfaceOverlay)
            .border(
                width = if (s.accent) 1.5.dp else 1.dp,
                color = if (s.accent) colors.accent else colors.borderSubtle,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = s.heading,
            style = type.body.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp),
            color = colors.inkPrimary,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = s.sub,
            style = type.monoSmall,
            color = colors.inkTertiary,
        )
    }
}

@Composable
private fun InvertedPill(text: String) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    Box(
        modifier = Modifier
            .background(colors.inkPrimary)
            .border(1.dp, colors.inkPrimary)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = text,
            style = type.body.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.5.sp,
                fontSize = 12.sp,
            ),
            color = colors.inkOnInverse,
        )
    }
}

@Composable
private fun OutlinedPill(text: String) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    Box(
        modifier = Modifier
            .border(1.dp, colors.borderStrong)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = text,
            style = type.body.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.5.sp,
                fontSize = 12.sp,
            ),
            color = colors.inkPrimary,
        )
    }
}

/** Dashed rectangular border — Compose doesn't ship one, so draw with a path effect. */
private fun Modifier.drawDashedBorder(
    color: Color,
    strokeWidthDp: androidx.compose.ui.unit.Dp,
): Modifier = this.drawBehind {
    val stroke = strokeWidthDp.toPx()
    drawRect(
        color = color,
        style = Stroke(
            width = stroke,
            pathEffect = PathEffect.dashPathEffect(
                intervals = floatArrayOf(8f, 6f),
                phase = 0f,
            ),
        ),
    )
}
