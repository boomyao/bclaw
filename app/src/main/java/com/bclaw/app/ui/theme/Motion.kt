package com.bclaw.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * bclaw v2 motion tokens — mirror of `design/tokens.json` → `motion`
 * plus the rules in `design/project/design/ds-motion.html`.
 *
 * Three durations, two easings, no bounce outside sheet entries.
 * If a transition exceeds 320ms it is either decorative (cut it) or
 * compositional (split into sequenced sub-steps at 200ms each). Never stretch.
 */
data class BclawMotion(
    // Durations (ms)
    val durFast: Int = 120,       // hover/press tints, icon flips, small value commits
    val durNormal: Int = 200,     // tab slides, message cards entering, chips opening
    val durSlow: Int = 320,       // sheets, pickers, device switcher

    // Easings
    val easeStandard: Easing = StandardEase,   // fast out, slow in — default for all UI
    val easeEmphasis: Easing = EmphasisEase,   // slight overshoot — sheets & reveals only

    // Pattern-specific
    val staggerStep: Int = 40,           // ms between list items · cap at 6
    val listStaggerCap: Int = 6,
    val livePulseLoop: Int = 1600,       // ms · status chip alpha pulse
    val loadingDotsLoop: Int = 1200,     // ms · 3-dot loading cycle
    val loadingDotsStagger: Int = 150,   // ms between each dot in loading cycle
    val runningStripLoop: Int = 1800,    // ms · 5-dot metro progress bar
    val runningStripStagger: Int = 360,  // ms between each dot in running strip
)

// Keep these as `val` at file scope so they can also be referenced from data-class defaults.
val StandardEase: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
val EmphasisEase: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1.4f)

val DefaultBclawMotion = BclawMotion()

val LocalBclawMotion = staticCompositionLocalOf { DefaultBclawMotion }
