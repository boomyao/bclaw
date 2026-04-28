package com.bclaw.app.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Full-screen remote desktop overlay. */
@Composable
fun LiveRemoteOverlay(
    hostApiBaseUrl: String?,
    hostAgentToken: String?,
    deviceName: String,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        RemoteDesktopContent(
            hostApiBaseUrl = hostApiBaseUrl,
            hostAgentToken = hostAgentToken,
            deviceName = deviceName,
            onDismiss = onDismiss,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
