package com.bclaw.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val TerminalBlack = Color(0xFF000000)
val SurfaceNear = Color(0xFF0A0A0A)
val SurfaceElevated = Color(0xFF141414)
val Divider = Color(0xFF262626)
val TextPrimary = Color(0xFFFFFFFF)
val TextMeta = Color(0xFF9A9A9A)
val TextDim = Color(0xFF5A5A5A)
val AccentCyan = Color(0xFF00BCF2)
val DangerRed = Color(0xFFE51400)
val WarningAmber = Color(0xFFF0A30A)

data class BclawColors(
    val terminalBlack: Color,
    val surfaceNear: Color,
    val surfaceElevated: Color,
    val divider: Color,
    val textPrimary: Color,
    val textMeta: Color,
    val textDim: Color,
    val accentCyan: Color,
    val dangerRed: Color,
    val warningAmber: Color,
)

val DefaultBclawColors = BclawColors(
    terminalBlack = TerminalBlack,
    surfaceNear = SurfaceNear,
    surfaceElevated = SurfaceElevated,
    divider = Divider,
    textPrimary = TextPrimary,
    textMeta = TextMeta,
    textDim = TextDim,
    accentCyan = AccentCyan,
    dangerRed = DangerRed,
    warningAmber = WarningAmber,
)

val LocalBclawColors = staticCompositionLocalOf { DefaultBclawColors }
