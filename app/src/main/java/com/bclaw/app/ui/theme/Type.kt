package com.bclaw.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * bclaw v2 typography — mirror of `design/tokens.json` → `fontSize` + `fontWeight`
 * plus the scale rules in `design/project/design/ds-type.html`.
 *
 * Families:
 *   - display / body  → Space Grotesk
 *   - mono            → JetBrains Mono
 *
 * v2.0 ships placeholders (`FontFamily.SansSerif` / `FontFamily.Monospace`) because
 * Space Grotesk and JetBrains Mono asset bundling is tracked as open debt in
 * SPEC_V2 §11. When the assets land, swap the values of [DisplayFamily] and
 * [MonoFamily] below — nothing else needs to change.
 */

// Swap these two lines when Space Grotesk + JetBrains Mono ship as app assets.
val DisplayFamily: FontFamily = FontFamily.SansSerif
val MonoFamily: FontFamily = FontFamily.Monospace
val BodyFamily: FontFamily = DisplayFamily  // body shares display family per tokens.json

private val TightLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/**
 * 12-step scale per ds-type.html §B. Line-heights baked in; letter-spacing uses `em` so
 * scaling-up with accessibility font scale stays proportional.
 */
data class BclawTypography(
    // Display · hero only, intentionally large
    val displayXL: TextStyle,     // fs-48 · marketing only, never in-app
    val display: TextStyle,       // fs-36 · .t-display
    val hero: TextStyle,          // fs-28 · .t-hero · screen intros

    // Headings
    val h1: TextStyle,            // fs-24 · section titles
    val h2: TextStyle,            // fs-20 · sheet titles / primary panels
    val h3: TextStyle,            // fs-17 · card titles / dense panel headings

    // Body
    val bodyLarge: TextStyle,     // fs-15 · prominent body text
    val body: TextStyle,          // fs-13 · default UI label
    val bodySmall: TextStyle,     // fs-12 · helper text, dense lists

    // Technical / mono
    val mono: TextStyle,          // fs-11 · paths, ids, tokens, counts
    val monoSmall: TextStyle,     // fs-10 · status-bar style mono

    // Meta (mono, caps, letter-spaced)
    val meta: TextStyle,          // fs-10 · section labels, eyebrows
    val micro: TextStyle,         // fs-9 · chip + status labels only
)

val DefaultBclawTypography = BclawTypography(
    displayXL = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Light,
        fontSize = 48.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.025).em,
        lineHeightStyle = TightLineHeight,
    ),
    display = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Light,
        fontSize = 36.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.02).em,
        lineHeightStyle = TightLineHeight,
    ),
    hero = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Light,
        fontSize = 28.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.015).em,
        lineHeightStyle = TightLineHeight,
    ),

    h1 = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.01).em,
    ),
    h2 = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 23.sp,
    ),
    h3 = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 20.sp,
    ),

    bodyLarge = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    body = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),

    mono = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    ),
    monoSmall = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 14.sp,
    ),

    meta = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.15.em,
    ),
    micro = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.15.em,
    ),
)

val LocalBclawTypography = staticCompositionLocalOf { DefaultBclawTypography }
