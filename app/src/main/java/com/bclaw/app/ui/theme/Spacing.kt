package com.bclaw.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * bclaw v2 spacing tokens — mirror of `design/tokens.json` → `spacing` + chrome heights.
 *
 * 4dp base unit; 8dp preferred step. Components compose from 8/16/24 in 80% of cases per
 * `design/project/design/ds-spacing.html` §A. Odd values (6, 10, 14) are forbidden outside tokens.
 */
data class BclawSpacing(
    // Base ramp
    val sp1: Dp = 4.dp,
    val sp2: Dp = 8.dp,
    val sp3: Dp = 12.dp,
    val sp4: Dp = 16.dp,
    val sp5: Dp = 20.dp,
    val sp6: Dp = 24.dp,
    val sp8: Dp = 32.dp,
    val sp10: Dp = 40.dp,
    val sp12: Dp = 48.dp,
    val sp16: Dp = 64.dp,

    // Canonical chrome heights (§ds-spacing B)
    val statusBarHeight: Dp = 28.dp,
    val tabStripHeight: Dp = 38.dp,
    val crumbHeight: Dp = 52.dp,
    val composerHeight: Dp = 56.dp,
    val composerMaxHeight: Dp = 160.dp,   // 5-line multiline cap
    val gestureBandHeight: Dp = 20.dp,    // OS-reserved; do not touch

    // Page gutters
    val pageGutter: Dp = 16.dp,
    val edgeLeft: Dp = 24.dp,             // Lumia asymmetric left margin (hero + crumb hang off this)
    val edgeRight: Dp = 16.dp,

    // Composition gaps
    val messageGap: Dp = 20.dp,           // between sibling content items
    val sectionGap: Dp = 32.dp,           // between thematic sections
    val rowGap: Dp = 12.dp,
    val groupGap: Dp = 24.dp,
    val insideCard: Dp = 16.dp,
    val inlineGap: Dp = 8.dp,
    val dotToLabel: Dp = 8.dp,

    // Accessibility
    val hitTargetMin: Dp = 44.dp,         // minimum hit region
    val hitTargetPreferred: Dp = 48.dp,
)

val DefaultBclawSpacing = BclawSpacing()

val LocalBclawSpacing = staticCompositionLocalOf { DefaultBclawSpacing }
