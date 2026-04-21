package com.bclaw.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

object BclawSpacing {
    val EdgeLeft = 24.dp
    val EdgeRight = 16.dp
    val MessageGap = 20.dp
    val SectionGap = 32.dp
    val InsideCard = 16.dp
    val InlineGap = 8.dp
    val DotToLabel = 8.dp
}

private val MaterialColorScheme = darkColorScheme(
    primary = DefaultBclawColors.accentCyan,
    onPrimary = DefaultBclawColors.terminalBlack,
    secondary = DefaultBclawColors.warningAmber,
    onSecondary = DefaultBclawColors.terminalBlack,
    tertiary = DefaultBclawColors.textMeta,
    onTertiary = DefaultBclawColors.terminalBlack,
    background = DefaultBclawColors.terminalBlack,
    onBackground = DefaultBclawColors.textPrimary,
    surface = DefaultBclawColors.surfaceNear,
    onSurface = DefaultBclawColors.textPrimary,
    surfaceVariant = DefaultBclawColors.surfaceElevated,
    onSurfaceVariant = DefaultBclawColors.textMeta,
    outline = DefaultBclawColors.divider,
    error = DefaultBclawColors.dangerRed,
    onError = DefaultBclawColors.terminalBlack,
    errorContainer = DefaultBclawColors.surfaceElevated,
    onErrorContainer = DefaultBclawColors.textPrimary,
)

@Composable
fun BclawTheme(
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalBclawColors provides DefaultBclawColors,
        LocalBclawTypography provides DefaultBclawTypography,
    ) {
        MaterialTheme(
            colorScheme = MaterialColorScheme,
            typography = Typography(),
            shapes = Shapes(
                small = BclawShape.Sharp,
                medium = BclawShape.Sharp,
                large = BclawShape.Sharp,
            ),
            content = content,
        )
    }
}
