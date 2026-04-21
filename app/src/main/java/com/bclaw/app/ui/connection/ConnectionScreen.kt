package com.bclaw.app.ui.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.bclaw.app.data.BclawUrlParseResult
import com.bclaw.app.data.BclawUrlParser
import com.bclaw.app.data.ConnectionConfig
import com.bclaw.app.domain.model.ConnectionPhase
import com.bclaw.app.ui.components.Banner
import com.bclaw.app.ui.components.MetroActionButton
import com.bclaw.app.ui.components.MetroUnderlineTextField
import com.bclaw.app.ui.components.connection.ConnectionClipboardChip
import com.bclaw.app.ui.components.connection.ConnectionHelpLink
import com.bclaw.app.ui.components.connection.ConnectionInlineError
import com.bclaw.app.ui.components.connection.rememberBclawClipboardPayload
import com.bclaw.app.ui.components.connection.rememberBclawQrScanner
import com.bclaw.app.ui.theme.BclawSpacing
import com.bclaw.app.ui.theme.LocalBclawColors
import com.bclaw.app.ui.theme.LocalBclawTypography
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun ConnectionScreen(
    initialConfig: ConnectionConfig,
    connectionPhase: ConnectionPhase,
    statusMessage: String?,
    onConnect: (ConnectionConfig) -> Unit,
    onDismiss: (() -> Unit)? = null,
) {
    val colors = LocalBclawColors.current
    val typography = LocalBclawTypography.current
    val clipboardPayload = rememberBclawClipboardPayload()
    var connectionString by rememberSaveable(initialConfig.host, initialConfig.token, initialConfig.workspaces) {
        mutableStateOf(initialConfig.toEditableConnectionString())
    }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var scanError by rememberSaveable { mutableStateOf<String?>(null) }
    val startScan = rememberBclawQrScanner(
        onResult = { scannedUrl ->
            connectionString = scannedUrl
            scanError = null
        },
        onError = { error ->
            scanError = error
        },
    )

    val parseResult = remember(connectionString) {
        if (connectionString.isBlank()) {
            null
        } else {
            BclawUrlParser.parse(connectionString)
        }
    }
    val parsedConfig = (parseResult as? BclawUrlParseResult.Success)?.config
    val parseError = (parseResult as? BclawUrlParseResult.Error)?.reason
    val connectEnabled = parsedConfig != null && connectionPhase != ConnectionPhase.Connecting

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.terminalBlack)
            .verticalScroll(rememberScrollState())
            .padding(
                start = BclawSpacing.EdgeLeft,
                end = BclawSpacing.EdgeRight,
                top = 24.dp,
                bottom = 24.dp,
            )
            .testTag("connection_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Connect to your Mac",
                modifier = Modifier.weight(1f),
                style = typography.hero,
                color = colors.textPrimary,
            )
            if (onDismiss != null) {
                Text(
                    text = "CLOSE",
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable(onClick = onDismiss)
                        .testTag("connection_close"),
                    style = typography.body,
                    color = colors.accentCyan,
                )
            }
        }

        Banner(statusMessage)

        if (!clipboardPayload.isNullOrBlank()) {
            ConnectionClipboardChip(
                text = "paste from clipboard",
                onClick = { connectionString = clipboardPayload },
            )
        }

        ConnectionClipboardChip(
            text = "scan QR code",
            onClick = {
                scanError = null
                startScan()
            },
        )

        if (!scanError.isNullOrBlank()) {
            ConnectionInlineError(scanError!!)
        }

        MetroUnderlineTextField(
            value = connectionString,
            onValueChange = { connectionString = it },
            modifier = Modifier.fillMaxWidth(),
            fieldModifier = Modifier.testTag("connection_url_field"),
            placeholder = "paste connection string",
            singleLine = false,
            minLines = 2,
            maxLines = 4,
        )

        if (!parseError.isNullOrBlank() && connectionString.isNotBlank()) {
            ConnectionInlineError(parseError)
        }

        MetroActionButton(
            text = if (connectionPhase == ConnectionPhase.Connecting) "CONNECTING…" else "CONNECT",
            onClick = { parsedConfig?.let(onConnect) },
            enabled = connectEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("connect_button"),
        )

        ConnectionHelpLink(
            expanded = showHelp,
            onToggle = { showHelp = !showHelp },
        )
    }
}

private fun ConnectionConfig.toEditableConnectionString(): String {
    if (host.isBlank() || token.isBlank()) return ""
    val authority = host.removePrefix("ws://").removePrefix("wss://")
    if (authority.isBlank()) return ""
    val query = buildList {
        add("tok=${encodeQueryValue(token)}")
        workspaces.forEach { workspace ->
            add("cwd=${encodeQueryValue(workspace.cwd)}")
        }
    }.joinToString("&")
    return "bclaw1://$authority?$query"
}

private fun encodeQueryValue(raw: String): String {
    return URLEncoder.encode(raw, StandardCharsets.UTF_8).replace("+", "%20")
}
