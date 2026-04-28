package com.bclaw.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.bclaw.app.service.BclawForegroundService
import com.bclaw.app.ui.BclawApp
import com.bclaw.app.ui.LocalBclawController
import com.bclaw.app.ui.theme.BclawTheme
import com.bclaw.app.ui.theme.ThemeMode

/**
 * Host Activity for bclaw v2.
 *
 * Does not OWN the controller anymore — that lives on [BclawApplication] so a foreground
 * service can keep the process (and its coroutines) alive across Activity destruction.
 * Activity's only jobs:
 *   1. Publish [LocalBclawController] for composables
 *   2. Start / stop [BclawForegroundService] in lockstep with `uiState.hasActiveDevice`
 *
 * No `DisposableEffect { controller.shutdown() }` — the Application holds the reference
 * for the whole process; shutdown happens in Application.onTerminate (or when the OS kills
 * the process).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge so the remote desktop surface can own its keyboard and system-bar
        // layout without the Activity resizing underneath it.
        enableEdgeToEdge()
        setContent {
            val app = application as BclawApplication
            val controller = app.controller
            val context = LocalContext.current

            // Tie the foreground service to the presence of an active device. Flipping
            // false → true starts the service; true → false stops it. Safe across config
            // change because controller is Application-scoped.
            val uiState by controller.uiState.collectAsState()
            LaunchedEffect(uiState.hasActiveDevice) {
                if (uiState.hasActiveDevice) {
                    BclawForegroundService.start(context)
                } else {
                    BclawForegroundService.stop(context)
                }
            }

            BclawTheme(mode = ThemeMode.System) {
                CompositionLocalProvider(LocalBclawController provides controller) {
                    BclawApp()
                }
            }
        }
    }
}
