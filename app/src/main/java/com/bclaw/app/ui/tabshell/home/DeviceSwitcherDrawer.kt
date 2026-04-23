package com.bclaw.app.ui.tabshell.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bclaw.app.domain.v2.Device
import com.bclaw.app.service.BclawV2Intent
import com.bclaw.app.ui.LocalBclawController
import com.bclaw.app.ui.LocalBclawNavigation
import com.bclaw.app.ui.components.BclawSideDrawer
import com.bclaw.app.ui.components.DrawerEdge
import com.bclaw.app.ui.theme.BclawTheme

/**
 * Device switcher drawer — slides in from the left when the device chip on Home is tapped.
 *
 * UX_V2 §2.6: switching tears down the current device's transports and loads the new device's
 * persisted tabs. Visually handled via the Crossfade in BclawApp — uiState.hasActiveDevice
 * stays true, but tabRuntimes + transports empty and the new device's tabs populate.
 *
 * Settings row is a stub — Settings screen (UX_V2 §2 inventory #14) is deferred.
 */
@Composable
fun DeviceSwitcherDrawer(
    visible: Boolean,
    onDismissRequest: () -> Unit,
) {
    val controller = LocalBclawController.current
    val nav = LocalBclawNavigation.current
    val uiState by controller.uiState.collectAsState()
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    val devices = uiState.deviceBook.devices
    val activeId = uiState.deviceBook.activeDeviceId

    BclawSideDrawer(
        visible = visible,
        edge = DrawerEdge.Left,
        onDismissRequest = onDismissRequest,
        width = 300.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = sp.pageGutter, vertical = sp.sp6),
        ) {
            Text(
                text = "DEVICES",
                style = type.meta,
                color = colors.inkTertiary,
            )
            Spacer(Modifier.height(sp.sp3))

            Column(verticalArrangement = Arrangement.spacedBy(sp.sp1)) {
                devices.forEach { device ->
                    DeviceRow(
                        device = device,
                        isActive = device.id == activeId,
                        onClick = {
                            if (device.id != activeId) {
                                controller.onIntent(BclawV2Intent.SwitchDevice(device.id))
                            }
                            onDismissRequest()
                        },
                        onRemove = {
                            controller.onIntent(BclawV2Intent.RemoveDevice(device.id))
                        },
                    )
                }
            }

            Spacer(Modifier.height(sp.sp4))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.borderSubtle),
            )
            Spacer(Modifier.height(sp.sp3))

            DrawerActionRow(
                label = "+ pair device",
                accent = true,
                onClick = {
                    nav.requestPairOverlay()
                    onDismissRequest()
                },
            )
            DrawerActionRow(
                label = "⚙ settings",
                accent = false,
                enabled = false,
                subtitle = "lands in v2.1",
                onClick = { /* deferred */ },
            )
        }
    }
}

@Composable
private fun DeviceRow(
    device: Device,
    isActive: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isActive) colors.surfaceRaised else colors.surfaceOverlay)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = sp.sp3, vertical = sp.sp3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = device.displayName,
                    style = type.h3,
                    color = colors.inkPrimary,
                )
                if (isActive) {
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = " · active",
                        style = type.bodySmall,
                        color = colors.inkSecondary,
                    )
                }
            }
            Text(
                text = device.wsBaseUrl.removePrefix("ws://"),
                style = type.mono,
                color = colors.inkTertiary,
            )
        }
        if (!isActive) {
            Text(
                text = "✕",
                style = type.bodySmall,
                color = colors.inkMuted,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onRemove,
                    )
                    .padding(sp.sp2),
            )
        }
    }
}

@Composable
private fun DrawerActionRow(
    label: String,
    accent: Boolean,
    enabled: Boolean = true,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = sp.sp3, vertical = sp.sp3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = type.h3,
                color = when {
                    !enabled -> colors.inkMuted
                    accent -> colors.accent
                    else -> colors.inkPrimary
                },
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = type.bodySmall,
                    color = colors.inkTertiary,
                )
            }
        }
    }
}
