package com.bclaw.app.ui.tabshell.session

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bclaw.app.domain.v2.TabState
import com.bclaw.app.ui.components.BclawBottomSheet
import com.bclaw.app.ui.theme.BclawTheme

/**
 * Session actions sheet — opened from the crumb `···` overflow.
 *
 * Operations scoped to the current session (not the agent): rename, fork, copy session id,
 * close tab. Capabilities live in the tools sheet (composer `+`), not here.
 */
@Composable
fun SessionActionsSheet(
    visible: Boolean,
    tab: TabState?,
    onDismissRequest: () -> Unit,
    onCloseTab: () -> Unit,
) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing
    val context = LocalContext.current

    BclawBottomSheet(visible = visible, onDismissRequest = onDismissRequest) {
        if (tab == null) return@BclawBottomSheet

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = sp.pageGutter, vertical = sp.sp4),
        ) {
            Text(
                text = tab.sessionName?.trim()?.takeIf { it.isNotEmpty() } ?: "untitled",
                style = type.h3,
                color = colors.inkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(sp.sp1))
            Text(
                text = "${tab.agentId.value} · ${tab.projectCwd.value}",
                style = type.monoSmall,
                color = colors.inkTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            tab.sessionId?.let { id ->
                Spacer(Modifier.height(sp.sp1))
                Text(
                    text = "id · ${id.value}",
                    style = type.monoSmall,
                    color = colors.inkTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(sp.sp4))

            ActionRow(
                label = "rename session",
                subtitle = "v2.1 — agent doesn't expose a rename rpc yet",
                enabled = false,
                onClick = {},
            )
            ActionRow(
                label = "fork to another agent",
                subtitle = "v2.1 — long-press the tab (coming)",
                enabled = false,
                onClick = {},
            )
            ActionRow(
                label = "copy session id",
                enabled = tab.sessionId != null,
                onClick = {
                    tab.sessionId?.value?.let { id -> copyToClipboard(context, id) }
                    onDismissRequest()
                },
            )

            Spacer(Modifier.height(sp.sp2))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.borderSubtle),
            )
            Spacer(Modifier.height(sp.sp2))

            ActionRow(
                label = "close tab",
                danger = true,
                onClick = {
                    onDismissRequest()
                    onCloseTab()
                },
            )
        }
    }
}

@Composable
private fun ActionRow(
    label: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
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
            .padding(vertical = sp.sp3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = type.body,
                color = when {
                    !enabled -> colors.inkMuted
                    danger -> colors.roleError
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

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("session id", text))
    Toast.makeText(context, "session id copied", Toast.LENGTH_SHORT).show()
}
