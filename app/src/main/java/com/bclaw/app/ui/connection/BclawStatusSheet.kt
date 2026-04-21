package com.bclaw.app.ui.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.bclaw.app.data.ConnectionConfig
import com.bclaw.app.ui.components.MetroActionButton
import com.bclaw.app.ui.theme.BclawSpacing
import com.bclaw.app.ui.theme.LocalBclawColors
import com.bclaw.app.ui.theme.LocalBclawTypography

@Composable
fun BclawStatusSheet(
    config: ConnectionConfig,
    showNotificationsDisabledHint: Boolean,
    onDismiss: () -> Unit,
    onDisconnect: () -> Unit,
    onReconnectNow: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    val colors = LocalBclawColors.current
    val typography = LocalBclawTypography.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(onClick = onDismiss)
            .testTag("status_sheet_scrim"),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp, start = BclawSpacing.EdgeLeft, end = BclawSpacing.EdgeRight)
                .fillMaxWidth()
                .background(colors.surfaceElevated)
                .padding(BclawSpacing.InsideCard)
                .testTag("status_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = config.host.ifBlank { "No host configured" },
                style = typography.title,
                color = colors.textPrimary,
            )
            Text(
                text = "Workspaces: ${config.workspaces.size}",
                style = typography.meta,
                color = colors.textMeta,
            )
            if (showNotificationsDisabledHint) {
                Text(
                    text = "notifications disabled — background survival may be less reliable",
                    modifier = Modifier
                        .clickable(onClick = onOpenNotificationSettings)
                        .testTag("notifications_disabled_hint"),
                    style = typography.meta,
                    color = colors.textDim,
                )
            }
            MetroActionButton(
                text = "RECONNECT NOW",
                onClick = onReconnectNow,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("status_reconnect"),
                fillColor = Color.Transparent,
                textColor = colors.accentCyan,
                borderColor = colors.accentCyan,
            )
            MetroActionButton(
                text = "DISCONNECT",
                onClick = onDisconnect,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("status_disconnect"),
                fillColor = Color.Transparent,
                textColor = colors.accentCyan,
                borderColor = colors.accentCyan,
            )
            MetroActionButton(
                text = "CONNECTION SETTINGS",
                onClick = onOpenSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("status_settings"),
                fillColor = Color.Transparent,
                textColor = colors.accentCyan,
                borderColor = colors.accentCyan,
            )
        }
    }
}
