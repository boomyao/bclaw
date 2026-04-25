package com.bclaw.app.ui.tabshell.session.sidecar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bclaw.app.ui.components.BclawBottomSheet
import com.bclaw.app.ui.theme.BclawTheme

/**
 * Sidecar detail sheet — render the selected [Sidecar]'s content.
 *
 * v2.0 ships three honest stubs:
 *   - Terminal  → "v2.1 — split mode wiring pending"
 *   - Remote    → "v2.1 — gui-over-tailnet pending"
 *   - Files     → "no files yet" (pending ACP `fs/readTextFile` via bridge)
 *
 * Each stub explains what the feature IS and what's blocking, not "coming soon". Per UX_V2 §0
 * principle 7 + §6 error/recovery table.
 */
@Composable
fun SidecarStubSheet(
    visible: Boolean,
    sidecar: Sidecar?,
    onDismissRequest: () -> Unit,
    imageAttachmentsSupported: Boolean = false,
    macFilesSupported: Boolean = false,
    onPickPhoneImages: () -> Unit = {},
    onBrowseMacFiles: () -> Unit = {},
) {
    if (sidecar == null) {
        BclawBottomSheet(visible = false, onDismissRequest = onDismissRequest) {}
        return
    }

    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    BclawBottomSheet(
        visible = visible,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = sp.pageGutter, vertical = sp.sp5),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = sidecar.title(),
                    style = type.h2,
                    color = colors.inkPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "✕",
                    style = type.h2,
                    color = colors.inkTertiary,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismissRequest,
                        )
                        .padding(sp.sp2),
                )
            }
            Spacer(Modifier.height(sp.sp3))

            when (sidecar) {
                // Terminal + Remote are routed to LiveTerminalSidecar / LiveRemoteSidecar in
                // SessionTab so this sheet never actually renders their case. Keeping the
                // branch defensive in case a stale entry-point opens the bottom sheet.
                Sidecar.Terminal, Sidecar.Remote -> Unit
                Sidecar.Files -> Column(verticalArrangement = Arrangement.spacedBy(sp.sp3)) {
                    ActionCard(
                        heading = "from phone",
                        body = if (imageAttachmentsSupported) {
                            "pick images from your gallery; they go on the next prompt"
                        } else {
                            "this agent doesn't accept image input (promptCapabilities.image = false)"
                        },
                        enabled = imageAttachmentsSupported,
                        onClick = {
                            onPickPhoneImages()
                            onDismissRequest()
                        },
                    )
                    ActionCard(
                        heading = "browse mac files",
                        body = if (macFilesSupported) {
                            "pick a file from the paired project; up to 256 KB embeds as context for the next prompt"
                        } else {
                            "this agent doesn't accept embedded file context (promptCapabilities.embeddedContext = false)"
                        },
                        enabled = macFilesSupported,
                        onClick = {
                            onBrowseMacFiles()
                            onDismissRequest()
                        },
                    )
                }
            }
        }
    }
}

private fun Sidecar.title(): String = when (this) {
    Sidecar.Terminal -> "terminal"
    Sidecar.Remote -> "remote"
    Sidecar.Files -> "files"
}

@Composable
private fun ActionCard(
    heading: String,
    body: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (enabled) colors.surfaceRaised else colors.surfaceDeep)
            .border(1.dp, colors.borderSubtle)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(sp.sp4),
        verticalArrangement = Arrangement.spacedBy(sp.sp2),
    ) {
        Box(
            modifier = Modifier
                .height(2.dp)
                .fillMaxWidth()
                .background(if (enabled) colors.accent else colors.borderSubtle),
        )
        Text(
            text = heading,
            style = type.h3,
            color = if (enabled) colors.inkPrimary else colors.inkMuted,
        )
        Text(
            text = body,
            style = type.bodyLarge,
            color = if (enabled) colors.inkSecondary else colors.inkTertiary,
        )
    }
}

@Composable
private fun StubCard(heading: String, body: String, meta: String) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceRaised)
            .border(1.dp, colors.borderSubtle)
            .padding(sp.sp4),
        verticalArrangement = Arrangement.spacedBy(sp.sp3),
    ) {
        Box(
            modifier = Modifier
                .height(2.dp)
                .fillMaxWidth()
                .background(colors.accent),
        )
        Text(text = heading, style = type.h3, color = colors.inkPrimary)
        Text(text = body, style = type.bodyLarge, color = colors.inkSecondary)
        Text(text = meta, style = type.meta, color = colors.inkTertiary)
    }
}
