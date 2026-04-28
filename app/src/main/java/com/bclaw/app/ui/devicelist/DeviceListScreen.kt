package com.bclaw.app.ui.devicelist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bclaw.app.domain.v2.Device
import com.bclaw.app.service.BclawV2Intent
import com.bclaw.app.ui.LocalBclawController
import com.bclaw.app.ui.LocalBclawNavigation
import com.bclaw.app.ui.components.MetroButton
import com.bclaw.app.ui.components.MetroButtonSize
import com.bclaw.app.ui.components.MetroButtonVariant
import com.bclaw.app.ui.components.MetroTextField

@Composable
fun DeviceListScreen() {
    val controller = LocalBclawController.current
    val navigation = LocalBclawNavigation.current
    val uiState by controller.uiState.collectAsState()
    val devices = uiState.deviceBook.devices
    var renameTarget by remember { mutableStateOf<Device?>(null) }
    var removeTarget by remember { mutableStateOf<Device?>(null) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Devices",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (devices.isEmpty()) {
                    Text(
                        text = "No devices yet",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(devices, key = { it.id.value }) { device ->
                            DeviceRow(
                                device = device,
                                onClick = {
                                    navigation.requestRemoteOverlay(
                                        hostApiBaseUrl = device.hostApiBaseUrl,
                                        hostAgentToken = device.token,
                                        deviceName = device.displayName,
                                    )
                                },
                                onRename = { renameTarget = device },
                                onRemove = { removeTarget = device },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { navigation.requestPairOverlay() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Pair another device")
                }
            }
        }
    }

    renameTarget?.let { device ->
        RenameDeviceDialog(
            device = device,
            onDismiss = { renameTarget = null },
            onSave = { displayName ->
                controller.onIntent(BclawV2Intent.RenameDevice(device.id, displayName))
                renameTarget = null
            },
        )
    }

    removeTarget?.let { device ->
        RemoveDeviceDialog(
            device = device,
            onDismiss = { removeTarget = null },
            onConfirm = {
                controller.onIntent(BclawV2Intent.RemoveDevice(device.id))
                removeTarget = null
            },
        )
    }
}

@Composable
private fun DeviceRow(
    device: Device,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = device.displayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = device.hostApiBaseUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetroButton(
                label = "Rename",
                onClick = onRename,
                variant = MetroButtonVariant.Ghost,
                size = MetroButtonSize.Sm,
            )
            MetroButton(
                label = "Remove",
                onClick = onRemove,
                variant = MetroButtonVariant.Danger,
                size = MetroButtonSize.Sm,
            )
        }
    }
}

@Composable
private fun RenameDeviceDialog(
    device: Device,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var displayName by remember(device.id.value) { mutableStateOf(device.displayName) }
    val canSave = displayName.trim().isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename device") },
        text = {
            MetroTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = "device name",
                placeholder = "Studio Mac",
                singleLine = true,
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (canSave) onSave(displayName)
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(displayName) },
                enabled = canSave,
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun RemoveDeviceDialog(
    device: Device,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove device") },
        text = {
            Text(
                text = "Remove ${device.displayName} from this phone? You can pair it again with a new bclaw2 URL.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Remove")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
