package com.bclaw.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bclaw.app.ui.theme.BclawTheme

/**
 * Metro bordered text field.
 *
 * Mirrors `design/project/design/ds-forms.html` §A Text fields:
 *   - 44dp height (single-line) / `min` 88dp (multi-line)
 *   - 1dp border-subtle, accent border on focus
 *   - surface-overlay fill
 *   - body 13/regular by default; mono 11 when [mono] = true
 *   - Label above in mono meta style (uppercase + 0.15em)
 *   - Help / error below (mono 10, tertiary or error color)
 *
 * `errorMessage` takes precedence over `helpMessage` when both supplied.
 */
@Composable
fun MetroTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    helpMessage: String? = null,
    errorMessage: String? = null,
    mono: Boolean = false,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    enabled: Boolean = true,
    readOnly: Boolean = false,
) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val hasError = errorMessage != null

    val borderColor = when {
        hasError -> colors.roleError
        focused -> colors.accent
        else -> colors.borderSubtle
    }

    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label.uppercase(),
                style = type.meta,
                color = colors.inkTertiary,
            )
            Spacer(Modifier.height(sp.sp1))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (singleLine) 44.dp else 88.dp)
                .background(if (readOnly) colors.surfaceDeep else colors.surfaceOverlay)
                .border(BorderStroke(1.dp, borderColor))
                .padding(horizontal = sp.sp3, vertical = if (singleLine) 0.dp else sp.sp3),
            contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
        ) {
            val textStyle = (if (mono) type.mono else type.body).copy(
                color = if (readOnly) colors.inkTertiary else colors.inkPrimary,
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = if (singleLine) 44.dp else 88.dp),
                enabled = enabled,
                readOnly = readOnly,
                singleLine = singleLine,
                interactionSource = interactionSource,
                textStyle = textStyle,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                keyboardActions = keyboardActions,
                cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.accent),
                decorationBox = { innerTextField ->
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            text = placeholder,
                            style = textStyle,
                            color = colors.inkMuted,
                        )
                    }
                    innerTextField()
                },
            )
        }

        if (errorMessage != null || helpMessage != null) {
            Spacer(Modifier.height(sp.sp1 + sp.sp1))
            Text(
                text = errorMessage ?: helpMessage!!,
                style = type.monoSmall,
                color = if (hasError) colors.roleError else colors.inkTertiary,
            )
        }
    }
}
