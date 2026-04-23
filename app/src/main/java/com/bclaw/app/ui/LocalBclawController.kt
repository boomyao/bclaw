package com.bclaw.app.ui

import androidx.compose.runtime.compositionLocalOf
import com.bclaw.app.service.BclawV2Controller

/**
 * Composition-local reference to the Activity-scoped [BclawV2Controller].
 *
 * Provided by [com.bclaw.app.MainActivity]; composables consume it via
 * `LocalBclawController.current`. Designed to fail loudly if accessed outside the provider —
 * no orphan composables should read the controller.
 */
val LocalBclawController = compositionLocalOf<BclawV2Controller> {
    error("LocalBclawController not provided. Wrap with MainActivity's CompositionLocalProvider.")
}
