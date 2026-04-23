package com.bclaw.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bclaw.app.ui.theme.BclawTheme

/**
 * Metro button — sharp rectangle, zero radius, 5 variants × 4 sizes + optional mono style.
 *
 * Mirrors `design/project/design/ds-buttons.html` §A Variants and §B Sizes.
 *
 * Rules (UX_V2 §9 + ds-buttons "Don't"s):
 *   - Max one [MetroButtonVariant.Primary] per screen (the decisive commit).
 *   - [MetroButtonVariant.Accent] reserved for brand-forward moments (pair, approve, join).
 *   - [MetroButtonVariant.Ghost] is the low-weight action in rows/headers.
 *   - [mono] = true ⇒ UPPER-caps + 0.15em-spaced mono label ("SEND", "SCAN", "RETRY").
 */
enum class MetroButtonVariant { Primary, Accent, Secondary, Ghost, Danger }

enum class MetroButtonSize(val height: Dp, val horizontalPadding: Dp, val fontSizeSp: Int) {
    Xs(24.dp, 8.dp, 11),
    Sm(32.dp, 12.dp, 12),
    Md(40.dp, 16.dp, 13),
    Lg(48.dp, 20.dp, 15),
}

@Composable
fun MetroButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: MetroButtonVariant = MetroButtonVariant.Secondary,
    size: MetroButtonSize = MetroButtonSize.Md,
    mono: Boolean = false,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    val palette = paletteFor(variant, colors)

    val textStyle: TextStyle = if (mono) {
        type.body.copy(
            fontFamily = type.mono.fontFamily,
            fontSize = size.fontSizeSp.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.15.em,
        )
    } else {
        type.body.copy(fontSize = size.fontSizeSp.sp, fontWeight = FontWeight.Medium)
    }

    val labelText = if (mono) label.uppercase() else label
    val alpha = if (enabled) 1f else 0.4f

    Row(
        modifier = modifier
            .defaultMinSize(minWidth = size.height, minHeight = size.height)
            .height(size.height)
            .background(palette.background)
            .border(BorderStroke(1.dp, palette.border))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = size.horizontalPadding)
            .alpha(alpha),
        horizontalArrangement = Arrangement.spacedBy(sp.sp2, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) Box { leadingIcon() }
        Text(
            text = labelText,
            style = textStyle,
            color = palette.foreground,
            textAlign = TextAlign.Center,
        )
    }
}

// ── palette resolver ────────────────────────────────────────────────────

private data class ButtonPalette(val background: Color, val foreground: Color, val border: Color)

private fun paletteFor(
    variant: MetroButtonVariant,
    colors: com.bclaw.app.ui.theme.BclawColors,
): ButtonPalette = when (variant) {
    MetroButtonVariant.Primary -> ButtonPalette(
        colors.inkPrimary, colors.inkOnInverse, colors.inkPrimary,
    )
    MetroButtonVariant.Accent -> ButtonPalette(
        colors.accent, colors.accentInk, colors.accent,
    )
    MetroButtonVariant.Secondary -> ButtonPalette(
        Color.Transparent, colors.inkPrimary, colors.borderSubtle,
    )
    MetroButtonVariant.Ghost -> ButtonPalette(
        Color.Transparent, colors.inkSecondary, Color.Transparent,
    )
    MetroButtonVariant.Danger -> ButtonPalette(
        Color.Transparent, colors.roleError, colors.roleError,
    )
}
