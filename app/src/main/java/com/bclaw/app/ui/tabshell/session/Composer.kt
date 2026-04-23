package com.bclaw.app.ui.tabshell.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bclaw.app.ui.theme.BclawTheme

/**
 * Session composer — bottom input bar per UX_V2 §2.3.
 *
 * Controlled component: [input] + [onInputChange] let callers seed the text from starter
 * chips or the `/` palette. Send clears the input by dispatching [onSend] with the current
 * value — callers are expected to emit an empty string via [onInputChange] in response.
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
    onSend: (String) -> Unit,
    onCancel: () -> Unit,
    onOpenSidecars: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    val canSend = input.isNotBlank() && !streaming && connected

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceOverlay)
            .border(1.dp, colors.borderSubtle)
            .heightIn(min = sp.composerHeight, max = sp.composerMaxHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ComposerIconSlot(glyph = "+", enabled = !streaming && connected, onClick = onOpenSidecars)

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
