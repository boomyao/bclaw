package com.bclaw.app.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Ambient navigation state shared across root-level overlays.
 *
 * Intentionally NOT a full navigation controller — the app has no backstack. This holds the
 * transient overlay flags that multiple siblings need (pair-from-drawer, future settings
 * sheet, etc.) so we don't have to thread callbacks through every composable.
 *
 * Controller-level (persistent) state lives on BclawV2Controller. UI-only overlays live here.
 */
@Stable
class BclawNavigation {
    /** True while the pair screen should be shown on top of the device list. */
    var pairOverlayVisible: Boolean by mutableStateOf(false)
        private set

    /** Root-level remote desktop overlay. Covers app chrome without using a platform Dialog. */
    var remoteOverlay: RemoteOverlayRequest? by mutableStateOf(null)
        private set

    fun requestPairOverlay() { pairOverlayVisible = true }
    fun dismissPairOverlay() { pairOverlayVisible = false }

    fun requestRemoteOverlay(hostApiBaseUrl: String?, hostAgentToken: String?, deviceName: String) {
        remoteOverlay = RemoteOverlayRequest(
            hostApiBaseUrl = hostApiBaseUrl,
            hostAgentToken = hostAgentToken,
            deviceName = deviceName,
        )
    }

    fun dismissRemoteOverlay() { remoteOverlay = null }
}

data class RemoteOverlayRequest(
    val hostApiBaseUrl: String?,
    val hostAgentToken: String?,
    val deviceName: String,
)

val LocalBclawNavigation = compositionLocalOf<BclawNavigation> {
    error("LocalBclawNavigation not provided. Wrap with BclawApp's CompositionLocalProvider.")
}
