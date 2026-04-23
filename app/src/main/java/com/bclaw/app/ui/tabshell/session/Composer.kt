package com.bclaw.app.ui.tabshell.session

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bclaw.app.domain.v2.FileAttachment
import com.bclaw.app.ui.theme.BclawTheme

/**
 * Session composer — bottom input bar per UX_V2 §2.3.
 *
 * Controlled component: [input] + [onInputChange] let callers seed the text from starter
 * chips or the `/` palette. Send clears the input by dispatching [onSend] with the current
 * value — callers are expected to emit an empty string via [onInputChange] in response.
 *
 * Attachments (photos) are owned by the parent; the composer just renders the chip strip
 * + wires the `×` button. The actual picker lives behind Tools → files so there's a single,
 * named entry point for "add a file" instead of two parallel UI affordances.
 *
 * `+` (sidecar) is wired via [onOpenSidecars]. `@` / `/` palettes are UX §2.8/2.9 — wired
 * alongside the command palette in a follow-up.
 */
@Composable
fun Composer(
    input: String,
    onInputChange: (String) -> Unit,
    streaming: Boolean,
    connected: Boolean,
    attachments: List<Uri>,
    fileAttachments: List<FileAttachment>,
    onRemoveAttachment: (Uri) -> Unit,
    onRemoveFileAttachment: (FileAttachment) -> Unit,
    onSend: (String) -> Unit,
    onCancel: () -> Unit,
    onOpenSidecars: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    val canSend = (input.isNotBlank() || attachments.isNotEmpty() || fileAttachments.isNotEmpty()) &&
        !streaming && connected

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceOverlay)
            .border(1.dp, colors.borderSubtle),
    ) {
        if (fileAttachments.isNotEmpty()) {
            FileAttachmentStrip(
                attachments = fileAttachments,
                onRemove = onRemoveFileAttachment,
            )
        }
        if (attachments.isNotEmpty()) {
            AttachmentStrip(attachments = attachments, onRemove = onRemoveAttachment)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = sp.composerHeight, max = sp.composerMaxHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ComposerIconSlot(
                glyph = "+",
                enabled = !streaming && connected,
                onClick = onOpenSidecars,
            )

            Divider()

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = sp.sp3, vertical = sp.sp2),
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = type.bodyLarge.copy(color = colors.inkPrimary),
                    singleLine = false,
                    enabled = !streaming && connected,
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Default,
                    ),
                    keyboardActions = KeyboardActions.Default,
                    decorationBox = { inner ->
                        if (input.isEmpty()) {
                            Text(
                                text = when {
                                    streaming -> "…"
                                    !connected -> "offline · agent not connected"
                                    attachments.isNotEmpty() -> "add a message (optional)"
                                    else -> "type a message"
                                },
                                style = type.bodyLarge,
                                color = colors.inkMuted,
                            )
                        }
                        inner()
                    },
                )
            }

            // `@` / `/` palettes — deferred; keep honest disabled glyphs so their location is known.
            ComposerIconSlot(glyph = "@", enabled = false, glyphColor = colors.roleAgentClaude) { }
            ComposerIconSlot(glyph = "/", enabled = false, glyphColor = colors.accent) { }

            Divider()

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (streaming) colors.roleError
                        else if (canSend) colors.inkPrimary
                        else colors.surfaceDeep,
                    )
                    .clickable(
                        enabled = streaming || canSend,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (streaming) onCancel()
                            else onSend(input)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (streaming) "■" else "▶",
                    style = type.h3,
                    color = if (streaming || canSend) colors.inkOnInverse else colors.inkTertiary,
                )
            }
        }
    }
}

@Composable
private fun AttachmentStrip(
    attachments: List<Uri>,
    onRemove: (Uri) -> Unit,
) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = sp.sp3, vertical = sp.sp2),
        horizontalArrangement = Arrangement.spacedBy(sp.sp2),
    ) {
        attachments.forEach { uri ->
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(colors.surfaceDeep)
                    .border(1.dp, colors.borderSubtle),
            ) {
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp)
                        .background(colors.surfaceOverlay)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onRemove(uri) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✕", style = type.monoSmall, color = colors.inkPrimary)
                }
            }
        }
    }
}

@Composable
private fun FileAttachmentStrip(
    attachments: List<FileAttachment>,
    onRemove: (FileAttachment) -> Unit,
) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = sp.sp3, vertical = sp.sp2),
        horizontalArrangement = Arrangement.spacedBy(sp.sp2),
    ) {
        attachments.forEach { attachment ->
            Row(
                modifier = Modifier
                    .background(colors.surfaceDeep)
                    .border(1.dp, colors.borderSubtle)
                    .padding(horizontal = sp.sp2, vertical = sp.sp1),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(sp.sp1),
            ) {
                Text("📄", style = type.body, color = colors.inkSecondary)
                Text(
                    text = attachment.rel.substringAfterLast('/'),
                    style = type.mono,
                    color = colors.inkPrimary,
                )
                Text(
                    text = "✕",
                    style = type.meta,
                    color = colors.inkTertiary,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onRemove(attachment) },
                        )
                        .padding(sp.sp1),
                )
            }
        }
    }
}

@Composable
private fun ComposerIconSlot(
    glyph: String,
    enabled: Boolean,
    glyphColor: androidx.compose.ui.graphics.Color = BclawTheme.colors.inkTertiary,
    onClick: () -> Unit,
) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography

    Box(
        modifier = Modifier
            .size(40.dp)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = type.body,
            color = if (enabled) glyphColor else colors.inkMuted,
        )
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(40.dp)
            .background(BclawTheme.colors.borderSubtle),
    )
}
