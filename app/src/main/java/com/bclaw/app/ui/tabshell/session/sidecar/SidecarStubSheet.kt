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
                Sidecar.Terminal -> StubCard(
                    heading = "split-mode terminal",
                    body = "drags a divider between chat + a pty connected to the agent's shell. wiring lands in v2.1 once the bridge exposes `terminal/open` over the same ws auth.",
                    meta = "roadmap · v2.1 · SPEC_V2 §10",
                )
                Sidecar.Remote -> StubCard(
                    heading = "peek-mode remote",
                    body = "floats a scrcpy-style gui preview over chat, half screen. depends on v2.1 WebRTC bridge work — not a phone-side block.",
                    meta = "roadmap · v2.1 · SPEC_V2 §10",
                )
                Sidecar.Files -> StubCard(
                    heading = "repo file picker",
                    body = "browse the paired device's cwd, multi-select, attach. pending bridge-side `fs/listTextFiles` + `fs/readTextFile` forwarding — live in the ACP spec, not yet wired on the mac side.",
                    meta = "bridge work · tracking",
                )
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
