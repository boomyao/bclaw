package com.bclaw.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * bclaw v2 color tokens — mirror of `design/tokens.css` (`:root` + `[data-theme=light/dark]`).
 *
 * Source of truth: `design/tokens.json` → `semantic.light` / `semantic.dark` / `accent` / `role`.
 * If tokens.json changes, this file changes. Don't introduce hex values here that aren't in tokens.json.
 */

// ── neutrals (shared ramp, referenced by both themes) ──────────────────────
private val N0     = Color(0xFF000000)
private val N50    = Color(0xFF0A0A0A)
private val N100   = Color(0xFF141410)
private val N150   = Color(0xFF1C1C16)
private val N200   = Color(0xFF26261C)
private val N300   = Color(0xFF3A3A2E)
private val N400   = Color(0xFF55554A)
private val N500   = Color(0xFF76746A)
private val N600   = Color(0xFF9A9788)
private val N700   = Color(0xFFBAB6A3)
private val N800   = Color(0xFFD8D2BE)
private val N850   = Color(0xFFE4DEC8)
private val N900   = Color(0xFFECEAD9)
private val N950   = Color(0xFFF4F0E3)
private val N1000  = Color(0xFFFFFFFF)

// ── Metro brand colors (theme-invariant) ───────────────────────────────────
val MetroCyan    = Color(0xFF00BCF2)
val MetroMagenta = Color(0xFFE3008C)
val MetroLime    = Color(0xFFA4C400)
val MetroOrange  = Color(0xFFF0A30A)
val MetroRed     = Color(0xFFE51400)

/**
 * Semantic color set for one theme. Instantiated twice (light + dark) and selected at runtime.
 *
 * Token naming mirrors CSS custom properties in `design/tokens.css`:
 *   --surface-base     → surfaceBase
 *   --ink-primary      → inkPrimary
 *   --role-support     → roleSupport
 *   etc.
 */
data class BclawColors(
    // Surfaces
    val surfaceBase: Color,
    val surfaceRaised: Color,
    val surfaceDeep: Color,
    val surfaceOverlay: Color,
    val surfaceInverse: Color,

    // Ink (text)
    val inkPrimary: Color,
    val inkSecondary: Color,
    val inkTertiary: Color,
    val inkMuted: Color,
    val inkOnInverse: Color,
    val inkOnAccent: Color,

    // Borders
    val borderSubtle: Color,
    val borderStrong: Color,
    val borderFocus: Color,

    // Accent (Lumia Cyan — same value light/dark)
    val accent: Color,
    val accentInk: Color,
    val accentSoft: Color,

    // Roles (status + support)
    val roleLive: Color,     // success / live
    val roleWarn: Color,     // warning / reconnecting
    val roleError: Color,    // error / offline
    val roleSupport: Color,  // secondary support accent

    // Diff
    val diffAdd: Color,
    val diffRem: Color,
    val diffAddBg: Color,
    val diffRemBg: Color,

    // Theme flag (for inverse-aware widgets; not a color)
    val isDark: Boolean,
)

val LightBclawColors = BclawColors(
    // Surfaces — warm paper
    surfaceBase = N950,
    surfaceRaised = N900,
    surfaceDeep = N850,
    surfaceOverlay = N1000,
    surfaceInverse = N100,

    // Ink
    inkPrimary = N50,
    inkSecondary = N400,
    inkTertiary = N500,
    inkMuted = N600,
    inkOnInverse = N950,
    inkOnAccent = N50,

    // Borders
    borderSubtle = N800,
    borderStrong = N500,
    borderFocus = MetroCyan,

    // Accent
    accent = MetroCyan,
    accentInk = N50,
    accentSoft = MetroCyan.copy(alpha = 0.08f),

    // Roles
    roleLive = MetroLime,
    roleWarn = MetroOrange,
    roleError = MetroRed,
    roleSupport = MetroMagenta,

    // Diff (light)
    diffAdd = Color(0xFF2A7A2A),
    diffRem = Color(0xFFB82020),
    diffAddBg = Color(0xFFDFF0D8),
    diffRemBg = Color(0xFFF8D7DA),

    isDark = false,
)

val DarkBclawColors = BclawColors(
    // Surfaces — true black + warm-ink cascade
    surfaceBase = N0,
    surfaceRaised = N50,
    surfaceDeep = N100,
    surfaceOverlay = N150,
    surfaceInverse = N950,

    // Ink
    inkPrimary = N950,
    inkSecondary = N700,
    inkTertiary = N600,
    inkMuted = N500,
    inkOnInverse = N50,
    inkOnAccent = N0,

    // Borders
    borderSubtle = N200,
    borderStrong = N400,
    borderFocus = MetroCyan,

    // Accent
    accent = MetroCyan,
    accentInk = N0,
    accentSoft = MetroCyan.copy(alpha = 0.08f),

    // Roles
    roleLive = MetroLime,
    roleWarn = MetroOrange,
    roleError = MetroRed,
    roleSupport = MetroMagenta,

    // Diff (dark) — soft-tinted backgrounds + brighter additions
    diffAdd = MetroLime,
    diffRem = Color(0xFFFF5252),
    diffAddBg = MetroLime.copy(alpha = 0.10f),
    diffRemBg = MetroRed.copy(alpha = 0.10f),

    isDark = true,
)

/**
 * Composition local — access via `BclawTheme.colors` (see Theme.kt).
 * Don't read `LocalBclawColors.current` directly in feature code.
 */
val LocalBclawColors = staticCompositionLocalOf { DarkBclawColors }

/**
 * Convenience accessor matching the shape of `MaterialTheme.colorScheme`.
 */
object BclawTheme {
    val colors: BclawColors
        @Composable
        @ReadOnlyComposable
        get() = LocalBclawColors.current

    val typography: BclawTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalBclawTypography.current

    val spacing: BclawSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalBclawSpacing.current

    val motion: BclawMotion
        @Composable
        @ReadOnlyComposable
        get() = LocalBclawMotion.current
}
