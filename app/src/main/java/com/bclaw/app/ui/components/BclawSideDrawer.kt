package com.bclaw.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bclaw.app.ui.theme.BclawTheme

enum class DrawerEdge { Left, Right }

/**
 * Sharp side drawer anchored to [edge] (left or right).
 *
 * UX_V2 §1.2: device switcher from left, capabilities from right. 40% scrim.
 *
 * Width defaults to 280dp; cap at 320dp when you pass a larger value — drawers are sidecars,
 * not full-screens (UX_V2 §5).
 */
@Composable
fun BclawSideDrawer(
    visible: Boolean,
    edge: DrawerEdge,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 280.dp,
    content: @Composable () -> Unit,
) {
    val colors = BclawTheme.colors
    val motion = BclawTheme.motion

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(motion.durNormal)),
        exit = fadeOut(animationSpec = tween(motion.durFast)),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.40f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest,
                ),
            contentAlignment = if (edge == DrawerEdge.Left) Alignment.CenterStart else Alignment.CenterEnd,
        ) {
            val fromSign = if (edge == DrawerEdge.Left) -1 else 1
            AnimatedVisibility(
                visible = visible,
                enter = slideInHorizontally(
                    animationSpec = tween(motion.durNormal),
                    initialOffsetX = { it * fromSign },
                ),
                exit = slideOutHorizontally(
                    animationSpec = tween(motion.durFast),
                    targetOffsetX = { it * fromSign },
                ),
            ) {
                Column(
                    modifier = Modifier
                        .width(width)
                        .fillMaxHeight()
                        .background(colors.surfaceOverlay)
                        .border(
                            width = 1.dp,
                            color = colors.borderSubtle,
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                ) {
                    content()
                }
            }
        }
    }
}
