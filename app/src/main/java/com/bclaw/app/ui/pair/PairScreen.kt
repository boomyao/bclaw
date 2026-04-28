package com.bclaw.app.ui.pair

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.bclaw.app.domain.v2.BclawV2UrlParseResult
import com.bclaw.app.service.BclawV2Intent
import com.bclaw.app.ui.LocalBclawController
import com.bclaw.app.ui.components.MetroButton
import com.bclaw.app.ui.components.MetroButtonSize
import com.bclaw.app.ui.components.MetroButtonVariant
import com.bclaw.app.ui.components.MetroTextField
import com.bclaw.app.ui.components.StatusDot
import com.bclaw.app.ui.theme.BclawTheme

/**
 * Pair screen — the first-touch surface. Renders when no device is active (UX_V2 §2.1).
 *
 * Paths:
 *   - **Scan** · [QrScanLauncher] launches the Google Play Services code scanner. Result is
 *     piped straight into the same [BclawV2Intent.PairNewDevice] the paste flow uses.
 *   - **Paste** · Optional custom name + multi-line mono text field + Pair button as a
 *     fallback / power-user path.
 *
 * Errors are surfaced two ways:
 *   - URL parse errors → [BclawV2Controller.pairError] stream → inline red helper under the
 *     text field (reuses [BclawV2UrlParseResult.Error.reason]).
 *   - Scanner infrastructure errors → local composable state → inline row above the scan box.
 *
 * Layout hangs from `edgeLeft` (24dp) per ds-spacing §B — the Lumia asymmetric margin.
 */
@Composable
fun PairScreen(onDismiss: (() -> Unit)? = null) {
    val controller = LocalBclawController.current
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    val pairError by controller.pairError.collectAsState()
    var deviceNameInput by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("") }
    var scannerError by remember { mutableStateOf<String?>(null) }
    val qrLauncher = rememberQrScanLauncher()

    // Clear the controller-level error when the user starts editing again, so the banner
    // doesn't linger after a visible fix. Local re-validation still happens on Pair tap.
    LaunchedEffect(urlInput) {
        if (pairError != null) controller.clearPairError()
    }

    val canPair = urlInput.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = sp.edgeLeft, end = sp.edgeRight, top = sp.sp10, bottom = sp.sp8),
    ) {
        // Eyebrow meta + optional dismiss (when rendered as an overlay)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "BCLAW · V2 · PAIR",
                style = type.meta,
                color = colors.inkTertiary,
                modifier = Modifier.padding(end = sp.sp2),
            )
            Spacer(Modifier.weight(1f))
            if (onDismiss != null) {
                Text(
                    text = "✕",
                    style = type.h2,
                    color = colors.inkSecondary,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss,
                        )
                        .padding(sp.sp2),
                )
            }
        }
        Spacer(Modifier.height(sp.sp2))

        // Hero
        Text(
            text = "pair a device.",
            style = type.hero,
            color = colors.inkPrimary,
        )
        Spacer(Modifier.height(sp.sp4))

        // Body copy
        Text(
            text = "Run `scripts/bclaw-handoff --qr` on your Mac, then scan the code it shows — or paste the url below.",
            style = type.bodyLarge,
            color = colors.inkSecondary,
        )
        Spacer(Modifier.height(sp.sp8))

        // ── Scanner error banner ──
        scannerError?.let { message ->
            Text(
                text = message.uppercase(),
                style = type.meta,
                color = colors.roleError,
                modifier = Modifier.padding(bottom = sp.sp2),
            )
        }

        // ── QR Scan tap-target ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(colors.surfaceRaised)
                .border(1.dp, colors.borderSubtle)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        scannerError = null
                        qrLauncher.launch(
                            onResult = { scanned ->
                                // Keep the raw payload visible even though the scan path
                                // commits immediately; parse errors still surface below.
                                urlInput = scanned
                                controller.onIntent(
                                    BclawV2Intent.PairNewDevice(
                                        rawUrl = scanned,
                                        displayName = deviceNameInput,
                                    ),
                                )
                            },
                            onCancelled = { /* user backed out — no-op */ },
                            onError = { throwable ->
                                scannerError = throwable.message
                                    ?: "scanner unavailable on this device"
                            },
                        )
                    },
                )
                .drawBehind { drawCameraCorners(colors.accent) },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(sp.sp2),
                ) {
                    StatusDot(color = colors.accent, size = 8.dp)
                    Text(
                        text = "TAP TO SCAN",
                        style = type.micro,
                        color = colors.accent,
                    )
                }
                Spacer(Modifier.height(sp.sp3))
                Text(
                    text = "scan the qr from your mac",
                    style = type.h3,
                    color = colors.inkPrimary,
                )
                Spacer(Modifier.height(sp.sp1))
                Text(
                    text = "run scripts/bclaw-handoff --qr to print one",
                    style = type.bodySmall,
                    color = colors.inkTertiary,
                )
            }
        }

        Spacer(Modifier.height(sp.sp8))

        // ── Paste fallback ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(sp.sp5, 1.dp)
                    .background(colors.borderStrong),
            )
            Spacer(Modifier.size(sp.sp2))
            Text(
                text = "OR PASTE URL",
                style = type.meta,
                color = colors.inkTertiary,
            )
            Spacer(Modifier.size(sp.sp2))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.borderStrong),
            )
        }

        Spacer(Modifier.height(sp.sp5))

        MetroTextField(
            value = deviceNameInput,
            onValueChange = { deviceNameInput = it },
            label = "device name",
            placeholder = "optional, e.g. Studio Mac",
            helpMessage = "leave blank to use the host name",
            singleLine = true,
            imeAction = ImeAction.Next,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(sp.sp4))

        MetroTextField(
            value = urlInput,
            onValueChange = { urlInput = it },
            label = "bclaw2 url",
            placeholder = "bclaw2://100.64.1.2:8766?tok=…",
            helpMessage = null,
            errorMessage = pairError?.let { pairErrorMessage(it) },
            mono = true,
            singleLine = false,
            imeAction = ImeAction.Done,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(sp.sp6))

        MetroButton(
            label = "Pair",
            onClick = {
                controller.onIntent(
                    BclawV2Intent.PairNewDevice(
                        rawUrl = urlInput,
                        displayName = deviceNameInput,
                    ),
                )
            },
            variant = MetroButtonVariant.Accent,
            size = MetroButtonSize.Lg,
            enabled = canPair,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Draw four 16dp L-shaped corner ticks onto the camera-preview placeholder for the Metro
 * viewfinder aesthetic. Inset 12dp from the container edges.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCameraCorners(
    color: androidx.compose.ui.graphics.Color,
) {
    val inset = 12.dp.toPx()
    val tick = 16.dp.toPx()
    val stroke = 2.dp.toPx()
    val w = size.width
    val h = size.height

    // top-left
    drawLine(color, Offset(inset, inset), Offset(inset + tick, inset), stroke)
    drawLine(color, Offset(inset, inset), Offset(inset, inset + tick), stroke)
    // top-right
    drawLine(color, Offset(w - inset - tick, inset), Offset(w - inset, inset), stroke)
    drawLine(color, Offset(w - inset, inset), Offset(w - inset, inset + tick), stroke)
    // bottom-left
    drawLine(color, Offset(inset, h - inset), Offset(inset + tick, h - inset), stroke)
    drawLine(color, Offset(inset, h - inset - tick), Offset(inset, h - inset), stroke)
    // bottom-right
    drawLine(color, Offset(w - inset - tick, h - inset), Offset(w - inset, h - inset), stroke)
    drawLine(color, Offset(w - inset, h - inset - tick), Offset(w - inset, h - inset), stroke)
}

private fun pairErrorMessage(error: BclawV2UrlParseResult.Error): String = error.reason
