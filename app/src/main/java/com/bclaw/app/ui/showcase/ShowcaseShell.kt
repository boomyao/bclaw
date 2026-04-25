package com.bclaw.app.ui.showcase

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bclaw.app.ui.theme.BclawTheme
import com.bclaw.app.ui.theme.MetroCyan
import com.bclaw.app.ui.theme.MetroLime
import com.bclaw.app.ui.theme.MetroMagenta
import com.bclaw.app.ui.theme.MetroOrange
import com.bclaw.app.ui.theme.MetroRed
import com.bclaw.app.ui.theme.MetroViolet

/**
 * v2.1 design showcase — pixel-accurate Compose mirrors of the v21 JSX files
 * (shell, screens-states, screens-session, screens-terminal, screens-remote).
 *
 * These composables live outside the production tab shell; they render in a dedicated
 * showcase overlay so designers can review every screen without touching live nav.
 * Primitives here deliberately mirror the JSX exactly (paddings, borders, colors) so
 * the catalogue is a ground-truth reference — don't refactor them into "idiomatic" Compose
 * without a matching update to the design source.
 */

// ── Neutral ramp mirror (tokens.css — full range exposed for showcase fidelity) ──
//
// The production Color.kt exposes semantic surfaces (surfaceBase/surfaceRaised/…) that map
// to specific steps per theme. Showcase screens sometimes reference specific raw steps
// (e.g. a terminal always renders on true-black regardless of the outer theme). We expose
// the full 0..1000 ramp here so those screens can stay true to the design source without
// polluting the semantic palette.
internal object ShowcaseNeutrals {
    val N0 = Color(0xFF000000)
    val N50 = Color(0xFF0A0A0A)
    val N100 = Color(0xFF141410)
    val N150 = Color(0xFF1C1C16)
    val N200 = Color(0xFF26261C)
    val N300 = Color(0xFF3A3A2E)
    val N400 = Color(0xFF55554A)
    val N500 = Color(0xFF76746A)
    val N600 = Color(0xFF9A9788)
    val N700 = Color(0xFFBAB6A3)
    val N800 = Color(0xFFD8D2BE)
    val N850 = Color(0xFFE4DEC8)
    val N900 = Color(0xFFECEAD9)
    val N950 = Color(0xFFF4F0E3)
    val N1000 = Color(0xFFFFFFFF)
}

internal object MetroPalette {
    val Cyan = MetroCyan
    val Magenta = MetroMagenta
    val Lime = MetroLime
    val Orange = MetroOrange
    val Red = MetroRed
    val Violet = MetroViolet
}

/** One tab entry in the showcase tab strip — mirrors JSX `TABS` array. */
internal data class ShowTab(
    val id: String,
    val label: String,
    val color: Color,
    val running: Boolean = false,
)

internal val DefaultShowTabs = listOf(
    ShowTab("fix-login", "foo-api/fix", MetroPalette.Cyan, running = true),
    ShowTab("hero", "landing/hero", MetroPalette.Magenta),
    ShowTab("migration", "foo-api/mgr", MetroPalette.Cyan),
    ShowTab("zsh", "dotfiles/zsh", MetroPalette.Magenta),
)

// ── Phone frame · 412×915, 32dp radius, 8dp bezel ────────────────────────
/**
 * Physical phone frame matching `shell.jsx:175-217`. Renders children at 412dp × 915dp
 * regardless of host constraints — callers are expected to either wrap in a scroller or
 * scale externally. `dark` swaps the inner background for true-black (terminal / remote).
 */
@Composable
internal fun ShowPhone(
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .size(412.dp, 915.dp)
            .clip(RectangleShape)
            .background(Color(0xFF111111))
            .padding(8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (dark) ShowcaseNeutrals.N0
                    else BclawTheme.colors.surfaceBase
                ),
        ) {
            content()
        }
    }
}

// ── Android-chrome mocks (28dp status + 20dp gesture bar) ────────────────
@Composable
internal fun ShowStatusBar(
    dark: Boolean = false,
    time: String = "01:07",
) {
    // Status-bar ink reads dark-on-light and light-on-dark so it stays legible across
    // both terminal-skin screens (dark=true, N950 ink) and standard paper (N50 ink).
    val barInk = if (dark) ShowcaseNeutrals.N950 else ShowcaseNeutrals.N50
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .padding(start = 18.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = time,
            color = barInk,
            style = BclawTheme.typography.mono,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Signal bars — four rising rectangles.
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                listOf(3.dp, 5.dp, 7.dp, 9.dp).forEach { h ->
                    Box(Modifier.size(width = 2.dp, height = h).background(barInk))
                }
            }
            Spacer(Modifier.width(4.dp))
            // Wifi — three concentric arcs rendered as stacked rounded rectangles.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(width = 10.dp, height = 2.dp).background(barInk))
                Spacer(Modifier.height(1.dp))
                Box(Modifier.size(width = 7.dp, height = 2.dp).background(barInk))
                Spacer(Modifier.height(1.dp))
                Box(Modifier.size(width = 3.dp, height = 2.dp).background(barInk))
            }
            Spacer(Modifier.width(4.dp))
            // Battery
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(width = 18.dp, height = 10.dp)
                        .border(1.dp, barInk)
                        .padding(1.dp),
                ) {
                    Box(Modifier.fillMaxSize().background(barInk))
                }
                Box(Modifier.size(width = 2.dp, height = 4.dp).background(barInk))
            }
        }
    }
}

@Composable
internal fun ShowGestureBar(dark: Boolean = false) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 108.dp, height = 4.dp)
                .background(
                    if (dark) ShowcaseNeutrals.N50 else ShowcaseNeutrals.N300,
                ),
        )
    }
}

// ── Tab strip mirror ─────────────────────────────────────────────────────
/**
 * Compact tab strip (38dp) matching `shell.jsx:43-113`. Leading stop-square, tabs with
 * 3dp agent-color bar, running dot, underline on active, trailing `+`.
 */
@Composable
internal fun ShowTabStrip(
    tabs: List<ShowTab>,
    activeId: String?,
    stop: Boolean = false,
) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(colors.surfaceBase)
            .border(width = 1.dp, color = colors.borderSubtle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Stop / menu square
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(if (stop) colors.roleError else Color.Transparent)
                .border(
                    width = 1.dp,
                    color = colors.borderSubtle,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (stop) {
                Box(Modifier.size(10.dp).background(Color.White))
            } else {
                Column(
                    modifier = Modifier.size(14.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                ) {
                    repeat(3) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.5.dp)
                                .background(colors.inkTertiary),
                        )
                    }
                }
            }
        }
        // Tabs
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { t ->
                val active = t.id == activeId
                Box(
                    modifier = Modifier
                        .height(38.dp)
                        .background(if (active) colors.surfaceOverlay else Color.Transparent)
                        .border(width = 1.dp, color = colors.borderSubtle),
                ) {
                    Row(
                        modifier = Modifier
                            .padding(start = 12.dp, end = 14.dp)
                            .fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            Modifier
                                .size(width = 3.dp, height = 16.dp)
                                .background(t.color),
                        )
                        Text(
                            text = t.label,
                            style = type.bodySmall,
                            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                            color = if (active) colors.inkPrimary else colors.inkTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (t.running) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .background(colors.roleLive),
                            )
                        }
                    }
                    if (active) {
                        Box(
                            Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(t.color),
                        )
                    }
                }
            }
            // New-tab `+` button
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .border(1.dp, colors.borderSubtle),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+",
                    style = type.h3,
                    fontWeight = FontWeight.Light,
                    color = colors.inkTertiary,
                )
            }
        }
    }
}

// ── Breadcrumb (device / project · session) ──────────────────────────────
/**
 * 52dp breadcrumb matching `shell.jsx:118-170` — meta device/project eyebrow + session
 * title, optional running pill, overflow dots.
 */
@Composable
internal fun ShowCrumb(
    device: String,
    project: String,
    session: String,
    running: Boolean = false,
) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(colors.surfaceBase)
            .border(1.dp, colors.borderSubtle)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = device.uppercase(),
                    style = type.meta,
                    color = colors.inkTertiary,
                )
                Text(
                    text = "/",
                    style = type.meta,
                    color = colors.inkTertiary.copy(alpha = 0.4f),
                )
                Text(
                    text = project.uppercase(),
                    style = type.meta,
                    color = colors.inkTertiary,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = session,
                style = type.h3,
                color = colors.inkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (running) {
            Row(
                modifier = Modifier
                    .border(1.dp, colors.roleLive)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(6.dp).background(colors.roleLive))
                Text(
                    text = "RUNNING",
                    style = type.micro,
                    color = colors.roleLive,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) {
                Box(Modifier.size(3.dp).background(colors.inkTertiary))
            }
        }
    }
}

// ── Composer preview ─────────────────────────────────────────────────────
/**
 * Composer preview row matching `shell.jsx:222-276` — `+` attach, placeholder/input field,
 * trailing mic OR send (send shown when `value` is non-empty).
 */
@Composable
internal fun ShowComposer(
    placeholder: String = "message…",
    value: String = "",
    disabled: Boolean = false,
) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceBase)
            .border(1.dp, colors.borderSubtle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(colors.surfaceOverlay)
                .border(1.dp, colors.borderSubtle),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                fontSize = 18.sp,
                fontWeight = FontWeight.Light,
                color = colors.inkPrimary,
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .background(colors.surfaceOverlay)
                .border(1.dp, colors.borderSubtle)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = value.ifBlank { placeholder },
                style = type.body,
                color = if (value.isBlank() || disabled) colors.inkMuted else colors.inkPrimary,
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(if (value.isBlank()) colors.surfaceOverlay else colors.accent)
                .border(1.dp, if (value.isBlank()) colors.borderSubtle else colors.accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (value.isBlank()) "🎤" else "➤",
                fontSize = 14.sp,
                color = if (value.isBlank()) colors.inkTertiary else colors.accentInk,
            )
        }
    }
}

