package com.bclaw.app.ui.components.connection

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import com.bclaw.app.ui.theme.BclawSpacing
import com.bclaw.app.ui.theme.LocalBclawColors
import com.bclaw.app.ui.theme.LocalBclawTypography

@Composable
fun rememberBclawClipboardPayload(): String? {
    val context = LocalContext.current
    val clipboard = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    var payload by remember(clipboard) { mutableStateOf(clipboard.currentBclawPayload(context)) }

    DisposableEffect(clipboard) {
        val listener = ClipboardManager.OnPrimaryClipChangedListener {
            payload = clipboard.currentBclawPayload(context)
        }
        clipboard.addPrimaryClipChangedListener(listener)
        onDispose {
            clipboard.removePrimaryClipChangedListener(listener)
        }
    }

    return payload
}

@Composable
fun ConnectionClipboardChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBclawColors.current
    val typography = LocalBclawTypography.current
    Text(
        text = text,
        modifier = modifier
            .clickable(onClick = onClick)
            .testTag("connection_paste_chip"),
        style = typography.meta,
        color = colors.accentCyan,
    )
}

@Composable
fun ConnectionHelpLink(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBclawColors.current
    val typography = LocalBclawTypography.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "How do I get this? ↗",
            modifier = Modifier
                .clickable(onClick = onToggle)
                .testTag("connection_help_toggle"),
            style = typography.body,
            color = colors.textPrimary,
        )
        if (expanded) {
            Text(
                text = "On your Mac, run scripts/bclaw-handoff to print your connection string.",
                modifier = Modifier
                    .padding(end = BclawSpacing.EdgeRight)
                    .testTag("connection_help_text"),
                style = typography.meta,
                color = colors.textMeta,
            )
        }
    }
}

@Composable
fun ConnectionInlineError(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBclawColors.current
    val typography = LocalBclawTypography.current
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .testTag("connection_parse_error"),
        style = typography.meta,
        color = colors.dangerRed,
    )
}

fun setClipboardText(context: Context, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("bclaw", value))
}

private fun ClipboardManager.currentBclawPayload(context: Context): String? {
    val itemText = primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.coerceToText(context)
        ?.toString()
        ?.trim()
    return itemText?.takeIf { it.startsWith("bclaw1://") }
}
