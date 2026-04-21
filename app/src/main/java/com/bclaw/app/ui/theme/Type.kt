package com.bclaw.app.ui.theme

import android.graphics.Typeface
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

private val SansSerifLight = FontFamily(Typeface.create("sans-serif-light", Typeface.NORMAL))

data class BclawTypography(
    val hero: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val meta: TextStyle,
    val code: TextStyle,
)

val DefaultBclawTypography = BclawTypography(
    hero = TextStyle(
        fontFamily = SansSerifLight,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
    ),
    title = TextStyle(
        fontFamily = SansSerifLight,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    meta = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    code = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
)

val LocalBclawTypography = staticCompositionLocalOf { DefaultBclawTypography }
