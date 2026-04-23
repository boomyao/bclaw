package com.bclaw.app.ui.tabshell

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.dp
import com.bclaw.app.domain.v2.AgentDescriptor
import com.bclaw.app.domain.v2.AgentId
import com.bclaw.app.domain.v2.CwdPath
import com.bclaw.app.domain.v2.TabId
import com.bclaw.app.domain.v2.TabState
import com.bclaw.app.service.BclawV2Intent
import com.bclaw.app.ui.LocalBclawController
import com.bclaw.app.ui.components.BclawBottomSheet
import com.bclaw.app.ui.components.StatusDot
import com.bclaw.app.ui.theme.BclawTheme

/**
 * Horizontal tab strip.
 *
 * Mirrors UX_V2 §1.1:
 *   - 38dp tall (canonical tab-strip height from ds-spacing §B)
 *   - Leftmost = pinned Home tab (small cyan dot; cannot close)
 *   - Session tabs: 2dp agent-color left bar, short label, 2dp accent-color underline if active
 *   - `+` button at end of the session tabs list (before overflow)
 *   - Horizontally scrollable — no overflow chevron in v2.0 (per UX_V2 §10 "v2.1 adds overflow menu")
 */
@Composable
fun TabStrip(onHomeReclick: () -> Unit = {}) {
    val controller = LocalBclawController.current
    val uiState by controller.uiState.collectAsState()
    val colors = BclawTheme.colors
    val sp = BclawTheme.spacing

    val activeTabId = uiState.activeTabId
    val tabs = uiState.activeTabBook?.tabs.orEmpty()

    var menuForTabId by remember { mutableStateOf<TabId?>(null) }
    val menuTab = tabs.firstOrNull { it.id == menuForTabId }
    var pickerForCwd by remember { mutableStateOf<CwdPath?>(null) }

    // Auto-scroll to center the active tab when it changes (including opening a new tab or
    // resuming a session). Positions are captured via onGloballyPositioned as each tab lays
    // out; viewport width comes from the strip's onSizeChanged.
    val scrollState = rememberScrollState()
    val tabBounds = remember { mutableStateMapOf<TabId, Pair<Int, Int>>() } // (xInRow, widthPx)
    var viewportWidth by remember { mutableStateOf(0) }

    LaunchedEffect(activeTabId, viewportWidth) {
        if (activeTabId == null) {
            scrollState.animateScrollTo(0)
            return@LaunchedEffect
        }
        val (x, w) = tabBounds[activeTabId] ?: return@LaunchedEffect
        val target = (x + w / 2 - viewportWidth / 2)
            .coerceIn(0, scrollState.maxValue)
        scrollState.animateScrollTo(target)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(sp.tabStripHeight)
            .background(colors.surfaceBase)
            .onSizeChanged { viewportWidth = it.width }
            .horizontalScroll(scrollState),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Leftmost pinned Home tab. Second tap while already on Home opens the project
        // switcher — same effect as tapping the project chip on the Home screen.
        HomePinTab(
            selected = activeTabId == null,
            onClick = {
                if (activeTabId == null) {
                    onHomeReclick()
                } else {
                    controller.onIntent(BclawV2Intent.SelectHomeTab)
                }
            },
        )

        // Session tabs
        tabs.forEach { tab ->
            SessionTab(
                tab = tab,
                selected = tab.id == activeTabId,
                onClick = { controller.onIntent(BclawV2Intent.SwitchTab(tab.id)) },
                onLongClick = { menuForTabId = tab.id },
                modifier = Modifier.onGloballyPositioned { coords ->
                    tabBounds[tab.id] = coords.positionInParent().x.toInt() to coords.size.width
                },
            )
        }

        // + button: opens a new session in the active cwd. Single agent → open directly;
        // multiple → show picker sheet. No cwd/agents → disabled.
        val activeDevice = uiState.deviceBook.activeDevice
        val newTabCwd = activeDevice?.effectiveProjectCwd
        val supportedAgents = if (activeDevice != null && newTabCwd != null) {
            activeDevice.agentsFor(newTabCwd)
        } else emptyList()
        val plusEnabled = newTabCwd != null && supportedAgents.isNotEmpty()

        PlusTab(
            enabled = plusEnabled,
            onClick = {
                if (!plusEnabled || newTabCwd == null) return@PlusTab
                val single = supportedAgents.singleOrNull()
                if (single != null) {
                    controller.onIntent(
                        BclawV2Intent.OpenNewTab(agentId = single.id, cwd = newTabCwd),
                    )
                } else {
                    pickerForCwd = newTabCwd
                }
            },
        )

        Spacer(Modifier.width(sp.sp4))
    }

    TabActionsSheet(
        tab = menuTab,
        onDismissRequest = { menuForTabId = null },
        onClose = {
            val id = menuForTabId ?: return@TabActionsSheet
            menuForTabId = null
            controller.onIntent(BclawV2Intent.CloseTab(id))
        },
    )

    NewTabAgentPickerSheet(
        visibleForCwd = pickerForCwd,
        agents = pickerForCwd?.let {
            uiState.deviceBook.activeDevice?.agentsFor(it)
        }.orEmpty(),
        onDismissRequest = { pickerForCwd = null },
        onPick = { agent ->
            val cwd = pickerForCwd ?: return@NewTabAgentPickerSheet
            pickerForCwd = null
            controller.onIntent(BclawV2Intent.OpenNewTab(agentId = agent.id, cwd = cwd))
        },
    )
}

// ── Home pin tab ────────────────────────────────────────────────────────

@Composable
private fun HomePinTab(selected: Boolean, onClick: () -> Unit) {
    val colors = BclawTheme.colors

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .drawBehind {
                if (selected) {
                    val thickness = 2.dp.toPx()
                    drawLine(
                        color = colors.accent,
                        start = Offset(0f, size.height - thickness / 2),
                        end = Offset(size.width, size.height - thickness / 2),
                        strokeWidth = thickness,
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Label would crowd 48dp; the cyan dot IS the brand glyph for Home.
        // Accessibility: future contentDescription = "Home" via Modifier.semantics
        StatusDot(color = colors.accent, size = 10.dp)
    }
}

// ── Session tab ─────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionTab(
    tab: TabState,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    val agentColor = agentColor(tab.agentId, colors)

    Row(
        modifier = modifier
            .fillMaxHeight()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = sp.sp3)
            .drawBehind {
                val barWidth = 2.dp.toPx()
                drawLine(
                    color = agentColor,
                    start = Offset(barWidth / 2, 6.dp.toPx()),
                    end = Offset(barWidth / 2, size.height - 6.dp.toPx()),
                    strokeWidth = barWidth,
                )
                if (selected) {
                    val thickness = 2.dp.toPx()
                    drawLine(
                        color = colors.accent,
                        start = Offset(0f, size.height - thickness / 2),
                        end = Offset(size.width, size.height - thickness / 2),
                        strokeWidth = thickness,
                    )
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sp.sp2),
    ) {
        Spacer(Modifier.width(sp.sp2)) // breathing room after the left color bar
        Text(
            text = tabLabel(tab),
            style = type.body,
            color = if (selected) colors.inkPrimary else colors.inkSecondary,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 140.dp),
        )
        if (tab.unread && !selected) {
            Spacer(Modifier.size(sp.sp1))
            StatusDot(color = colors.accent, size = 6.dp)
        }
    }
}

/** Tab label = session title (truncated), or "new" when the tab has no session yet.
 *  Agent identity lives in the left color bar, not the text. */
private fun tabLabel(tab: TabState): String {
    val raw = tab.sessionName?.trim()?.takeIf { it.isNotEmpty() }
        ?: return "new"
    // Strip leading markdown headers & collapse whitespace for strip display.
    val cleaned = raw.trimStart('#', ' ', '\t').replace(Regex("""\s+"""), " ").trim()
    return if (cleaned.length <= 24) cleaned else cleaned.take(24).trimEnd() + "…"
}

private fun agentColor(
    agentId: AgentId,
    colors: com.bclaw.app.ui.theme.BclawColors,
): Color = when (agentId.value.lowercase()) {
    "codex" -> colors.roleAgentCodex
    "claude", "claude-code" -> colors.roleAgentClaude
    "gemini" -> colors.roleAgentGemini
    "kimi" -> colors.roleAgentKimi
    else -> colors.roleAgentReserved
}

// ── Tab long-press action sheet ─────────────────────────────────────────

@Composable
private fun TabActionsSheet(
    tab: TabState?,
    onDismissRequest: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    BclawBottomSheet(visible = tab != null, onDismissRequest = onDismissRequest) {
        if (tab == null) return@BclawBottomSheet
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = sp.pageGutter, vertical = sp.sp4),
        ) {
            Text(
                text = tabLabel(tab),
                style = type.h3,
                color = colors.inkPrimary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(sp.sp1))
            Text(
                text = "${tab.agentId.value} · ${tab.projectCwd.value}",
                style = type.monoSmall,
                color = colors.inkTertiary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(sp.sp4))
            TabActionRow(label = "close tab", danger = true, onClick = onClose)
        }
    }
}

@Composable
private fun TabActionRow(label: String, danger: Boolean, onClick: () -> Unit) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = sp.sp3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = type.body,
            color = if (danger) colors.roleError else colors.inkPrimary,
        )
    }
}

// ── + new tab: pick agent ───────────────────────────────────────────────

@Composable
private fun NewTabAgentPickerSheet(
    visibleForCwd: CwdPath?,
    agents: List<AgentDescriptor>,
    onDismissRequest: () -> Unit,
    onPick: (AgentDescriptor) -> Unit,
) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    BclawBottomSheet(
        visible = visibleForCwd != null,
        onDismissRequest = onDismissRequest,
    ) {
        if (visibleForCwd == null) return@BclawBottomSheet
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = sp.pageGutter, vertical = sp.sp5),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "start new session",
                    style = type.h2,
                    color = colors.inkPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "✕",
                    style = type.h2,
                    color = colors.inkTertiary,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismissRequest,
                        )
                        .padding(sp.sp2),
                )
            }
            Spacer(Modifier.height(sp.sp1))
            Text(
                text = visibleForCwd.value,
                style = type.monoSmall,
                color = colors.inkTertiary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(sp.sp4))

            agents.forEach { agent ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onPick(agent) },
                        )
                        .padding(vertical = sp.sp3),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(sp.sp3),
                ) {
                    StatusDot(color = agentColor(agent.id, colors), size = 10.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = agent.id.value.lowercase(),
                            style = type.h3,
                            color = colors.inkPrimary,
                        )
                        Text(
                            text = agent.displayName.ifBlank { "agent" },
                            style = type.bodySmall,
                            color = colors.inkTertiary,
                        )
                    }
                    Text(
                        text = "→ new tab",
                        style = type.mono,
                        color = colors.accent,
                    )
                }
            }
            Spacer(Modifier.height(sp.sp3))
        }
    }
}

// ── + button ────────────────────────────────────────────────────────────

@Composable
private fun PlusTab(enabled: Boolean, onClick: () -> Unit) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(40.dp)
            .padding(horizontal = sp.sp1)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "+",
            style = type.h2,
            color = if (enabled) colors.inkPrimary else colors.inkMuted,
        )
    }
}
