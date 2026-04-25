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

    /**
     * True while the v2.1 design catalogue overlay is shown. Entry point lives on
     * [com.bclaw.app.ui.tabshell.home.HomeTab] (`view design v2.1 catalogue`) — used by
     * designers to review the full showcase without touching production navigation.
     */
    var showcaseOverlayVisible: Boolean by mutableStateOf(false)
        private set

    /** Root-level remote desktop overlay. Covers app chrome without using a platform Dialog. */
    var remoteOverlay: RemoteOverlayRequest? by mutableStateOf(null)
        private set

    fun requestPairOverlay() { pairOverlayVisible = true }
    fun dismissPairOverlay() { pairOverlayVisible = false }

    fun requestShowcaseOverlay() { showcaseOverlayVisible = true }
    fun dismissShowcaseOverlay() { showcaseOverlayVisible = false }

    fun requestRemoteOverlay(bridgeWsUrl: String?, deviceName: String) {
        remoteOverlay = RemoteOverlayRequest(
            bridgeWsUrl = bridgeWsUrl,
            deviceName = deviceName,
        )
    }

    fun dismissRemoteOverlay() { remoteOverlay = null }
}

data class RemoteOverlayRequest(
    val bridgeWsUrl: String?,
    val deviceName: String,
)

val LocalBclawNavigation = compositionLocalOf<BclawNavigation> {
    error("LocalBclawNavigation not provided. Wrap with BclawApp's CompositionLocalProvider.")
}
