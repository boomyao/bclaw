package com.bclaw.app.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Ambient navigation state shared across the tab shell.
 *
 * Intentionally NOT a full navigation controller — the app has no backstack. This holds the
 * transient overlay flags that multiple siblings need (pair-from-drawer, future settings
 * sheet, etc.) so we don't have to thread callbacks through every composable.
 *
 * Controller-level (persistent) state lives on BclawV2Controller. UI-only overlays live here.
 */
@Stable
class BclawNavigation {
    /** True while the pair screen should be shown ON TOP of the tab shell (add another device). */
    var pairOverlayVisible: Boolean by mutableStateOf(false)
        private set

    fun requestPairOverlay() { pairOverlayVisible = true }
    fun dismissPairOverlay() { pairOverlayVisible = false }
}

val LocalBclawNavigation = compositionLocalOf<BclawNavigation> {
    error("LocalBclawNavigation not provided. Wrap with BclawApp's CompositionLocalProvider.")
}
