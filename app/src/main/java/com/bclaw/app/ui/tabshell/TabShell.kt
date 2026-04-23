package com.bclaw.app.ui.tabshell

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bclaw.app.ui.LocalBclawController
import com.bclaw.app.ui.tabshell.home.HomeTab
import com.bclaw.app.ui.tabshell.session.SessionTab
import com.bclaw.app.ui.theme.BclawTheme

/**
 * Tab shell — the ambient chrome that hosts Home + N session tabs.
 *
 * Layout (UX_V2 §1, top to bottom):
 *   - [TabStrip] · 38dp · horizontally scrolling · leftmost pinned Home + session tabs + `+`
 *   - Divider · 1dp border-subtle
 *   - Active content · fills remaining viewport
 *
 * Content switches via [Crossfade] on `uiState.activeTabId`: null → [HomeTab], else the
 * matching [SessionTabPlaceholder]. TabStrip dispatches intents to the controller.
 */
@Composable
fun TabShell() {
    val controller = LocalBclawController.current
    val uiState by controller.uiState.collectAsState()
    val colors = BclawTheme.colors

    // Monotonic tick bumped whenever the user taps the Home pin while Home is already
    // active — HomeTab observes it to re-open the project switcher (so the pin doubles
    // as "jump to projects" once you're already home).
    var homeReclickTick by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceBase)
            .statusBarsPadding(),
    ) {
        TabStrip(onHomeReclick = { homeReclickTick++ })

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.borderSubtle),
        )

        // Single, always-visible device-level reachability indicator (auto-hides when
        // connected). SessionCrumb no longer repeats this info per-tab.
        ConnectionStatusBar()

        Crossfade(
            targetState = uiState.activeTabId,
            label = "tab-content-crossfade",
            modifier = Modifier.fillMaxSize(),
        ) { activeTabId ->
            if (activeTabId == null) {
                HomeTab(homeReclickTick = homeReclickTick)
            } else {
                val tab = uiState.activeTabBook?.tabs?.firstOrNull { it.id == activeTabId }
                if (tab == null) {
                    // Race between TabBook update and activeTabId transition → fall back to
                    // Home rather than flash an empty SessionTab. Controller reconciles next tick.
                    HomeTab(homeReclickTick = homeReclickTick)
                } else {
                    SessionTab(tab = tab)
                }
            }
        }
    }
}
