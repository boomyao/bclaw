package com.bclaw.app.ui.showcase

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bclaw.app.ui.showcase.messages.ShowSessionAllTypes
import com.bclaw.app.ui.showcase.messages.ShowSessionDestructive
import com.bclaw.app.ui.showcase.messages.ShowSessionImagePlan
import com.bclaw.app.ui.showcase.messages.ShowSessionLongFold
import com.bclaw.app.ui.showcase.remote.ShowRemoteMagnifier
import com.bclaw.app.ui.showcase.remote.ShowRemotePipLandscape
import com.bclaw.app.ui.showcase.remote.ShowRemoteTrackpad
import com.bclaw.app.ui.showcase.states.ShowDisconnected
import com.bclaw.app.ui.showcase.states.ShowEmptyNewSession
import com.bclaw.app.ui.showcase.states.ShowEmptyNoSessions
import com.bclaw.app.ui.showcase.states.ShowErrorCrashed
import com.bclaw.app.ui.showcase.states.ShowLoading
import com.bclaw.app.ui.showcase.states.ShowOnboarding1
import com.bclaw.app.ui.showcase.states.ShowPairDone
import com.bclaw.app.ui.showcase.states.ShowPairFound
import com.bclaw.app.ui.showcase.states.ShowPairQR
import com.bclaw.app.ui.showcase.terminal.ShowTerminalChips
import com.bclaw.app.ui.showcase.terminal.ShowTerminalRecording
import com.bclaw.app.ui.showcase.terminal.ShowTerminalSplit
import com.bclaw.app.ui.theme.BclawTheme

/**
 * Design catalogue root — renders every v2.1 showcase screen in one scrollable page so
 * designers / reviewers can sweep the whole book end-to-end. Sections A · C · D · E mirror
 * the `bclaw v2.1.html` masthead groupings; each screen is placed in its own 412dp phone
 * frame and captioned with the design ref (e.g. "A.05 — no sessions yet").
 *
 * Rendered by wrapping the design-source's index HTML inside the Android phone —
 * showcase screens are NOT wired into live navigation or the agent runtime. They are
 * for visual fidelity review only.
 */
@Composable
fun ShowcaseCatalogScreen(onDismiss: () -> Unit) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceBase),
    ) {
        // Header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceOverlay)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "BCLAW · V2.1 DESIGN · APRIL 2026",
                    style = type.meta,
                    color = colors.inkTertiary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "closing the gaps, opening the loop.",
                    style = type.h2,
                    color = colors.inkPrimary,
                )
            }
            Text(
                text = "✕",
                style = type.h2,
                color = colors.inkTertiary,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    )
                    .padding(8.dp),
            )
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(32.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = 24.dp,
                bottom = 48.dp,
                start = 0.dp,
                end = 0.dp,
            ),
        ) {
            items(CatalogSections) { section ->
                SectionBlock(section)
            }
        }
    }
}

// ── data ────────────────────────────────────────────────────────────────

private data class CatalogEntry(
    val ref: String,
    val label: String,
    val dark: Boolean = false,
    val content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
)

private data class CatalogSection(
    val id: String,
    val title: String,
    val subtitle: String,
    val entries: List<CatalogEntry>,
    val landscapeEntry: Pair<String, @Composable () -> Unit>? = null,
)

private val CatalogSections = listOf(
    CatalogSection(
        id = "A",
        title = "A · states & onboarding",
        subtitle = "every screen that isn't the happy path. first-run · pair flow · empty · loading · disconnected · error.",
        entries = listOf(
            CatalogEntry("A.01", "first run · no paired device") { ShowOnboarding1() },
            CatalogEntry("A.02", "pair step 1 · show qr + cli") { ShowPairQR() },
            CatalogEntry("A.03", "pair step 2 · verify code + permissions") { ShowPairFound() },
            CatalogEntry("A.04", "pair step 3 · pick project root") { ShowPairDone() },
            CatalogEntry("A.05", "no sessions yet (post-pair)") { ShowEmptyNoSessions() },
            CatalogEntry("A.06", "session opened · awaiting first prompt") { ShowEmptyNewSession() },
            CatalogEntry("A.07", "resuming · skeleton while history loads") { ShowLoading() },
            CatalogEntry("A.08", "tailnet dropped mid-turn · queue input") { ShowDisconnected() },
            CatalogEntry("A.09", "agent crashed · safe recovery") { ShowErrorCrashed() },
        ),
    ),
    CatalogSection(
        id = "C",
        title = "C · session message types",
        subtitle = "approval · plan · image · long output · reasoning · diff · command — all in one long session to show visual rhythm.",
        entries = listOf(
            CatalogEntry("C.01", "long session — every message type in sequence") { ShowSessionAllTypes() },
            CatalogEntry("C.02", "destructive approval — stronger visual weight") { ShowSessionDestructive() },
            CatalogEntry("C.03", "image inline — screenshot + plan side-by-side") { ShowSessionImagePlan() },
            CatalogEntry("C.04", "long output folding — agent ran something verbose") { ShowSessionLongFold() },
        ),
    ),
    CatalogSection(
        id = "D",
        title = "D · terminal sidecar",
        subtitle = "three modes for how terminal surfaces inside bclaw. each keeps chat reachable.",
        entries = listOf(
            CatalogEntry("D.01", "full terminal · chips + keyrow (fullscreen sidecar)", dark = true) { ShowTerminalChips() },
            CatalogEntry("D.02", "split view · chat above, shell below") { ShowTerminalSplit() },
            CatalogEntry("D.03", "recording · rec 00:42 · shareable .cast", dark = true) { ShowTerminalRecording() },
        ),
    ),
    CatalogSection(
        id = "E",
        title = "E · remote desktop sidecar",
        subtitle = "remote is the escape hatch, not a mac replacement. trackpad-first · precision for menubar items · agent stays visible.",
        entries = listOf(
            CatalogEntry("E.01", "trackpad · top viewport, bottom trackpad + modifier keys", dark = true) { ShowRemoteTrackpad() },
            CatalogEntry("E.02", "magnifier · circular loupe + d-pad for pixel nudges", dark = true) { ShowRemoteMagnifier() },
        ),
        landscapeEntry = "E.03 — landscape · full viewport, floating tool column, agent PIP" to {
            ShowRemotePipLandscape()
        },
    ),
)

// ── layout ──────────────────────────────────────────────────────────────

@Composable
private fun SectionBlock(section: CatalogSection) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        Text(
            text = section.title,
            style = type.h1.copy(fontWeight = FontWeight.Light),
            color = colors.inkPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = section.subtitle,
            style = type.body,
            color = colors.inkSecondary,
        )
        Spacer(Modifier.height(20.dp))
        // Horizontally scrolling row of phone frames (each 412dp wide).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            section.entries.forEach { e ->
                EntryBlock(entry = e)
            }
            if (section.landscapeEntry != null) {
                LandscapeEntryBlock(
                    label = section.landscapeEntry.first,
                    content = section.landscapeEntry.second,
                )
            }
        }
    }
}

@Composable
private fun EntryBlock(entry: CatalogEntry) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    Column(
        modifier = Modifier
            .widthIn(min = 412.dp)
            .wrapContentHeight(),
    ) {
        // Caption — `A.05  no sessions yet`
        Row(
            modifier = Modifier.padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = entry.ref,
                style = type.mono,
                color = colors.accent,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = entry.label,
                style = type.mono,
                color = colors.inkPrimary,
            )
        }
        // Phone frame (412×915) hosting the screen.
        ShowPhone(dark = entry.dark, content = entry.content)
    }
}

@Composable
private fun LandscapeEntryBlock(label: String, content: @Composable () -> Unit) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    Column {
        Text(
            text = label,
            style = type.mono,
            color = colors.inkTertiary,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        content()
    }
}
