package com.bclaw.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * bclaw v2 shapes — all sharp.
 *
 * UX_V2 §7.4: "Zero radius. Everywhere. No exceptions."
 * If you are tempted to add a non-zero radius shape here, you are wrong. Come back
 * and re-read the UX principle first.
 *
 * The [Sharp] object exists only to satisfy Material 3's `Shapes` slot in [BclawTheme].
 */
object BclawShape {
    val Sharp = RoundedCornerShape(0.dp)
}

/**
 * Stroke widths, mirroring `design/tokens.json` → `stroke`.
 */
object BclawStroke {
    /** 1dp — `--stroke-thin` · hair rules between list items, subtle cards. */
    const val Thin = 1f

    /** 1.5dp — `--stroke-rule` · section dividers, header underlines. */
    const val Rule = 1.5f

    /** 2dp — `--stroke-bold` · left-accent on tool cards, focused input underline. */
    const val Bold = 2f

    /** 3dp — `--stroke-selected` (extension) · selected-workspace drawer stripe. Not in tokens.json; kept for v0 parity. */
    const val Selected = 3f
}
