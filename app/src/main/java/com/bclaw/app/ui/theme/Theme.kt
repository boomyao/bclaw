package com.bclaw.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * bclaw v2 theme wrapper.
 *
 * Wraps content with:
 *   - [LocalBclawColors] — light or dark palette per [ThemeMode]
 *   - [LocalBclawTypography] — Space Grotesk + JetBrains Mono scale
 *   - [LocalBclawSpacing] — 4dp-base spacing tokens + chrome heights
 *   - [LocalBclawMotion] — durations + easings
 *   - [MaterialTheme] — so any leftover Material composables at least render on our palette
 *
 * Rule: feature code reads `BclawTheme.colors.*` / `BclawTheme.typography.*` etc.
 * It should almost never touch `MaterialTheme.colorScheme.*`. The Material layer
 * exists as a render fallback, not a source of values.
 */
enum class ThemeMode { System, Light, Dark }

@Composable
fun BclawTheme(
    mode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val isDark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    val colors = if (isDark) DarkBclawColors else LightBclawColors

    CompositionLocalProvider(
        LocalBclawColors provides colors,
        LocalBclawTypography provides DefaultBclawTypography,
        LocalBclawSpacing provides DefaultBclawSpacing,
        LocalBclawMotion provides DefaultBclawMotion,
    ) {
        MaterialTheme(
            colorScheme = if (isDark) materialDarkScheme(colors) else materialLightScheme(colors),
            typography = Typography(), // Material typography intentionally left at defaults; use BclawTheme.typography instead.
            shapes = Shapes(
                extraSmall = BclawShape.Sharp,
                small = BclawShape.Sharp,
                medium = BclawShape.Sharp,
                large = BclawShape.Sharp,
                extraLarge = BclawShape.Sharp,
            ),
            content = content,
        )
    }
}

private fun materialDarkScheme(c: BclawColors) = darkColorScheme(
    primary = c.accent,
    onPrimary = c.accentInk,
    secondary = c.roleSupport,
    onSecondary = c.surfaceBase,
    tertiary = c.inkTertiary,
    onTertiary = c.surfaceBase,
    background = c.surfaceBase,
    onBackground = c.inkPrimary,
    surface = c.surfaceRaised,
    onSurface = c.inkPrimary,
    surfaceVariant = c.surfaceDeep,
    onSurfaceVariant = c.inkSecondary,
    outline = c.borderStrong,
    outlineVariant = c.borderSubtle,
    error = c.roleError,
    onError = c.surfaceBase,
    errorContainer = c.surfaceDeep,
    onErrorContainer = c.inkPrimary,
)

private fun materialLightScheme(c: BclawColors) = lightColorScheme(
    primary = c.accent,
    onPrimary = c.accentInk,
    secondary = c.roleSupport,
    onSecondary = c.surfaceBase,
    tertiary = c.inkTertiary,
    onTertiary = c.surfaceBase,
    background = c.surfaceBase,
    onBackground = c.inkPrimary,
    surface = c.surfaceRaised,
    onSurface = c.inkPrimary,
    surfaceVariant = c.surfaceDeep,
    onSurfaceVariant = c.inkSecondary,
    outline = c.borderStrong,
    outlineVariant = c.borderSubtle,
    error = c.roleError,
    onError = c.surfaceOverlay,
    errorContainer = c.surfaceDeep,
    onErrorContainer = c.inkPrimary,
)
