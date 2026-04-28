package com.bclaw.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.bclaw.app.ui.devicelist.DeviceListScreen
import com.bclaw.app.ui.pair.PairScreen
import com.bclaw.app.ui.remote.LiveRemoteOverlay
import com.bclaw.app.ui.theme.BclawTheme

/**
 * v2 root composable.
 *
 * Cross-fades between the two app-level surfaces:
 *   - [PairScreen] when no device is active (first run, or after removing the last device)
 *   - [DeviceListScreen] when one or more devices are paired
 *
 * Also hosts the "add another device" pair overlay — a [PairScreen] rendered ON TOP of the
 * device list when [BclawNavigation.pairOverlayVisible] is true. Auto-dismisses when a new
 * device lands in the book.
 */
@Composable
fun BclawApp() {
    val controller = LocalBclawController.current
    val uiState by controller.uiState.collectAsState()
    val colors = BclawTheme.colors
    val motion = BclawTheme.motion
    val context = LocalContext.current

    val navigation = remember { BclawNavigation() }
    val deviceCount = uiState.deviceBook.devices.size
    val remoteOverlay = navigation.remoteOverlay

    // Auto-dismiss the overlay once a new device has been written to the book.
    LaunchedEffect(deviceCount) {
        if (navigation.pairOverlayVisible) navigation.dismissPairOverlay()
    }

    DisposableEffect(context, remoteOverlay != null) {
        val window = context.findActivity()?.window
        if (window != null && remoteOverlay != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            onDispose {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        } else {
            onDispose { }
        }
    }

    CompositionLocalProvider(LocalBclawNavigation provides navigation) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.surfaceBase),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars),
            ) {
                Crossfade(
                    targetState = uiState.hasActiveDevice,
                    label = "bclaw-v2-root-crossfade",
                ) { hasDevice ->
                    if (hasDevice) {
                        DeviceListScreen()
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

            AnimatedVisibility(
                visible = remoteOverlay != null,
                enter = fadeIn(animationSpec = tween(motion.durNormal)),
                exit = fadeOut(animationSpec = tween(motion.durFast)),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                ) {
                    remoteOverlay?.let { overlay ->
                        LiveRemoteOverlay(
                            hostApiBaseUrl = overlay.hostApiBaseUrl,
                            hostAgentToken = overlay.hostAgentToken,
                            deviceName = overlay.deviceName,
                            onDismiss = { navigation.dismissRemoteOverlay() },
                        )
                    }
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
