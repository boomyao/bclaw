package com.bclaw.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.bclaw.app.ui.pair.PairScreen
import com.bclaw.app.ui.tabshell.TabShell
import com.bclaw.app.ui.theme.BclawTheme

/**
 * v2 root composable.
 *
 * Cross-fades between the two app-level surfaces:
 *   - [PairScreen] when no device is active (first run, or after removing the last device)
 *   - [TabShell] when a device is active (UX_V2 §2 inventory)
 *
 * Also hosts the "add another device" pair overlay — a [PairScreen] rendered ON TOP of the
 * tab shell when [BclawNavigation.pairOverlayVisible] is true. Shown via the device switcher
 * drawer's "+ pair device" row. Auto-dismisses when a new device lands in the book.
 */
@Composable
fun BclawApp() {
    val controller = LocalBclawController.current
    val uiState by controller.uiState.collectAsState()
    val colors = BclawTheme.colors
    val motion = BclawTheme.motion

    val navigation = remember { BclawNavigation() }
    val deviceCount = uiState.deviceBook.devices.size

    // Auto-dismiss the overlay once a new device has been written to the book.
    LaunchedEffect(deviceCount) {
        if (navigation.pairOverlayVisible) navigation.dismissPairOverlay()
    }

    CompositionLocalProvider(LocalBclawNavigation provides navigation) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.surfaceBase)
                .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            Crossfade(
                targetState = uiState.hasActiveDevice,
                label = "bclaw-v2-root-crossfade",
            ) { hasDevice ->
                if (hasDevice) {
                    TabShell()
                } else {
                    PairScreen()
                }
            }

            AnimatedVisibility(
                visible = navigation.pairOverlayVisible,
                enter = fadeIn(animationSpec = tween(motion.durNormal)),
                exit = fadeOut(animationSpec = tween(motion.durFast)),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.surfaceBase),
                ) {
                    PairScreen(onDismiss = { navigation.dismissPairOverlay() })
                }
            }
        }
    }
}
