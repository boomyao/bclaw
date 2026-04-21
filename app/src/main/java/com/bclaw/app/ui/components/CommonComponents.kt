package com.bclaw.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.bclaw.app.domain.model.ConnectionPhase
import com.bclaw.app.ui.theme.BclawShape
import com.bclaw.app.ui.theme.BclawSpacing
import com.bclaw.app.ui.theme.LocalBclawColors
import com.bclaw.app.ui.theme.LocalBclawTypography

@Composable
fun StatusDot(
    connectionPhase: ConnectionPhase,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBclawColors.current
    val pulse by metroPulseAlpha(
        pulsing = connectionPhase == ConnectionPhase.Connecting || connectionPhase == ConnectionPhase.Reconnecting,
    )
    val color = when (connectionPhase) {
        ConnectionPhase.Connected,
        ConnectionPhase.Connecting,
        -> colors.accentCyan

        ConnectionPhase.Reconnecting -> colors.warningAmber
        ConnectionPhase.Offline,
        ConnectionPhase.AuthFailed,
        ConnectionPhase.Error,
        -> colors.dangerRed

        ConnectionPhase.Idle -> colors.textDim
    }
    Box(
        modifier = modifier
            .size(8.dp)
            .semantics { bclawCornerRadiusDp = 0f }
            .drawBehind {
                drawRect(color = color.copy(alpha = pulse))
            },
    )
}

@Composable
fun ConnectionChip(
    connectionPhase: ConnectionPhase,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBclawColors.current
    val typography = LocalBclawTypography.current
    val label = when (connectionPhase) {
        ConnectionPhase.Connected -> "connected"
        ConnectionPhase.Connecting -> "connecting…"
        ConnectionPhase.Reconnecting -> "reconnecting"
        ConnectionPhase.Offline,
        ConnectionPhase.Error,
        ConnectionPhase.Idle,
        -> "offline"

        ConnectionPhase.AuthFailed -> "auth failed"
    }
    val labelColor = when (connectionPhase) {
        ConnectionPhase.Connected -> colors.textPrimary
        ConnectionPhase.Connecting -> colors.accentCyan
        ConnectionPhase.Reconnecting -> colors.warningAmber
        ConnectionPhase.Offline,
        ConnectionPhase.AuthFailed,
        ConnectionPhase.Error,
        ConnectionPhase.Idle,
        -> colors.dangerRed
    }
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp)
            .testTag("connection_chip"),
        horizontalArrangement = Arrangement.spacedBy(BclawSpacing.DotToLabel),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(connectionPhase = connectionPhase)
        Text(
            text = label,
            style = typography.meta,
            color = labelColor,
        )
    }
}

@Composable
fun Banner(text: String?) {
    if (text.isNullOrBlank()) return
    val colors = LocalBclawColors.current
    val typography = LocalBclawTypography.current
    val accent = if (
        text.contains("reconnect", ignoreCase = true) ||
        text.contains("interrupted", ignoreCase = true)
    ) {
        colors.warningAmber
    } else {
        colors.dangerRed
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = BclawSpacing.EdgeLeft,
                end = BclawSpacing.EdgeRight,
                top = 8.dp,
                bottom = 8.dp,
            )
            .drawLeftStripe(accent, 2.dp)
            .background(colors.surfaceElevated, BclawShape.Sharp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(BclawSpacing.InsideCard),
            style = typography.body,
            color = colors.textPrimary,
        )
    }
}

@Composable
fun MetroUnderlineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    textStyle: TextStyle = LocalBclawTypography.current.body,
) {
    val colors = LocalBclawColors.current
    val typography = LocalBclawTypography.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                style = typography.meta,
                color = colors.textMeta,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = fieldModifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            textStyle = textStyle.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accentCyan),
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.terminalBlack, BclawShape.Sharp)
                        .drawBehind {
                            drawRect(
                                color = if (focused) {
                                    colors.accentCyan
                                } else {
                                    colors.textPrimary.copy(alpha = 0.4f)
                                },
                                topLeft = Offset(0f, size.height - 1.dp.toPx()),
                                size = Size(size.width, 1.dp.toPx()),
                            )
                        }
                        .padding(vertical = 12.dp),
                ) {
                    if (value.isBlank() && !placeholder.isNullOrBlank()) {
                        Text(
                            text = placeholder,
                            style = textStyle,
                            color = colors.textDim,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
fun MetroActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fillColor: Color = LocalBclawColors.current.accentCyan,
    textColor: Color = LocalBclawColors.current.terminalBlack,
    borderColor: Color? = null,
) {
    val colors = LocalBclawColors.current
    val typography = LocalBclawTypography.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val background = when {
        !enabled -> colors.textDim.copy(alpha = 0.3f)
        pressed -> colors.surfaceElevated
        else -> fillColor
    }
    Box(
        modifier = modifier
            .height(48.dp)
            .semantics { bclawCornerRadiusDp = 0f }
            .background(background, BclawShape.Sharp)
            .then(
                if (borderColor != null) {
                    Modifier.border(1.dp, borderColor, BclawShape.Sharp)
                } else {
                    Modifier
                },
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = typography.body,
            color = if (enabled) textColor else colors.textDim,
        )
    }
}

@Composable
fun RunningStrip(
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBclawColors.current
    val typography = LocalBclawTypography.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("running_turn_strip"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FiveDotMetroProgressBar()
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = BclawSpacing.EdgeLeft),
            style = typography.meta,
            color = colors.textMeta,
        )
    }
}

@Composable
fun TwoDotPulseIndicator(
    modifier: Modifier = Modifier,
) {
    val colors = LocalBclawColors.current
    val transition = rememberInfiniteTransition(label = "workspace-run-pulse")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "workspace-run-progress",
    )
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(2) { index ->
            val alpha = phasedPulseAlpha(progress = progress, phaseOffset = index * 0.5f)
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(colors.accentCyan.copy(alpha = alpha), BclawShape.Sharp),
            )
        }
    }
}

@Composable
internal fun StreamingInlineCodeText(
    text: String,
    modifier: Modifier = Modifier,
    bodyStyle: TextStyle,
    codeStyle: TextStyle,
    textColor: Color,
    codeBackground: Color,
) {
    var previousText by remember { mutableStateOf(text) }
    var appendedStart by remember { mutableStateOf<Int?>(null) }
    val appendedAlpha = remember { Animatable(1f) }

    LaunchedEffect(text) {
        if (text.startsWith(previousText) && text.length > previousText.length) {
            appendedStart = previousText.length
            appendedAlpha.snapTo(0f)
            appendedAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 150, easing = LinearEasing),
            )
        } else {
            appendedStart = null
            appendedAlpha.snapTo(1f)
        }
        previousText = text
    }

    Text(
        text = buildAnnotatedInlineText(
            text = text,
            bodyStyle = bodyStyle,
            codeStyle = codeStyle,
            textColor = textColor,
            codeBackground = codeBackground,
            appendedStart = appendedStart,
            appendedAlpha = appendedAlpha.value,
        ),
        modifier = modifier,
        style = bodyStyle,
        color = textColor,
    )
}

@Composable
private fun FiveDotMetroProgressBar() {
    val colors = LocalBclawColors.current
    val transition = rememberInfiniteTransition(label = "running-strip")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "running-strip-progress",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .drawBehind {
                val dotSize = 4.dp.toPx()
                repeat(5) { index ->
                    val x = ((progress + index * 0.2f) % 1f) * (size.width + dotSize) - dotSize
                    drawRect(
                        color = colors.accentCyan,
                        topLeft = Offset(x, 0f),
                        size = Size(dotSize, size.height),
                    )
                }
            },
    )
}

@Composable
private fun metroPulseAlpha(pulsing: Boolean): androidx.compose.runtime.State<Float> {
    if (!pulsing) return remember { mutableStateOf(1f) }
    val transition = rememberInfiniteTransition(label = "status-dot")
    return transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "status-dot-alpha",
    )
}

private fun phasedPulseAlpha(progress: Float, phaseOffset: Float): Float {
    val normalized = ((progress + phaseOffset) % 1f)
    return if (normalized < 0.5f) {
        0.3f + (normalized / 0.5f) * 0.7f
    } else {
        1f - ((normalized - 0.5f) / 0.5f) * 0.7f
    }
}

private fun buildAnnotatedInlineText(
    text: String,
    bodyStyle: TextStyle,
    codeStyle: TextStyle,
    textColor: Color,
    codeBackground: Color,
    appendedStart: Int?,
    appendedAlpha: Float,
): AnnotatedString {
    return buildAnnotatedString {
        var inCode = false
        var outputIndex = 0
        text.forEach { char ->
            if (char == '`') {
                inCode = !inCode
            } else {
                val alpha = if (appendedStart != null && outputIndex >= appendedStart) {
                    appendedAlpha
                } else {
                    1f
                }
                pushStyle(
                    SpanStyle(
                        color = textColor.copy(alpha = alpha),
                        fontFamily = if (inCode) {
                            codeStyle.fontFamily ?: FontFamily.Monospace
                        } else {
                            bodyStyle.fontFamily
                        },
                        fontSize = if (inCode) codeStyle.fontSize else bodyStyle.fontSize,
                        background = if (inCode) codeBackground else Color.Transparent,
                    ),
                )
                append(char)
                pop()
                outputIndex += 1
            }
        }
    }
}


