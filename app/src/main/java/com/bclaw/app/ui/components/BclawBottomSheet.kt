package com.bclaw.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bclaw.app.ui.theme.BclawTheme

/**
 * Sharp-rectangle bottom sheet with a 40% black scrim.
 *
 * Mirrors UX_V2 §1.2: "swipes up from bottom on agent-chip tap. 320ms emphasis ease."
 * Implementation uses [slideInVertically] (normal 200ms) + scrim fade — close enough to the
 * wireframe for v2.0; the emphasis-bounce curve is deferred with `t-motion` tokens in v2.1.
 *
 * Zero radius everywhere (UX_V2 §7.4). Scrim tap or drag-down closes; use back-press on the
 * host Activity if you want hardware-Back dismissal (not wired here — each caller decides).
 */
@Composable
fun BclawBottomSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
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
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(
                    animationSpec = tween(motion.durNormal),
                    initialOffsetY = { it },
                ),
                exit = slideOutVertically(
                    animationSpec = tween(motion.durFast),
                    targetOffsetY = { it },
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .background(colors.surfaceOverlay)
                        .border(1.dp, colors.borderSubtle)
                        .clickable(
                            // Consume clicks on the panel so they don't bubble to the scrim.
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
