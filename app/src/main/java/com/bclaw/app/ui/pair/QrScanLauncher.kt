package com.bclaw.app.ui.pair

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Google Play Services "Code Scanner" launcher.
 *
 * Why GMS instead of CameraX + ML Kit ourselves:
 *   - No CAMERA runtime permission to ask for — GMS handles the camera inside its own process
 *   - No camera preview composable to maintain
 *   - Scanner UI is a system-styled full-screen affordance; the user sees the Metro-branded
 *     Pair screen, taps "scan", sees the GMS viewfinder, and returns to our screen with a result
 *
 * Trade-off: first-run users need Play Services installed and may see a one-time scanner
 * module download. Acceptable on Pixel / OxygenOS / Xiaomi Android 16 per the dog-food target
 * in SPEC_V2 §1.
 */
class QrScanLauncher internal constructor(private val scanner: GmsBarcodeScanner) {

    /**
     * Launch the system scanner. [onResult] receives the decoded payload — the caller is
     * responsible for validating it (e.g. running it through BclawV2UrlParser).
     *
     * [onCancelled] fires when the user dismissed the scanner without a scan; do not treat
     * this as an error state. [onError] fires on infra failures (no Play Services, module
     * failed to download, camera unavailable, etc.). Surface the throwable message to the
     * user in an inline banner — do not crash.
     */
    fun launch(
        onResult: (String) -> Unit,
        onCancelled: () -> Unit = {},
        onError: (Throwable) -> Unit = {},
    ) {
        scanner.startScan()
            .addOnSuccessListener { barcode: Barcode ->
                val raw = barcode.rawValue
                if (raw.isNullOrBlank()) {
                    onError(IllegalStateException("scanner returned empty payload"))
                } else {
                    onResult(raw)
                }
            }
            .addOnCanceledListener { onCancelled() }
            .addOnFailureListener { throwable -> onError(throwable) }
    }
}

/**
 * Compose helper: remember a [QrScanLauncher] tied to the current [Context].
 *
 * The scanner client is cheap to construct but keyed by Context — using `remember(context)`
 * means config changes (rotation) recreate the launcher, which is fine because the active
 * Task is held by GMS, not us.
 */
@Composable
fun rememberQrScanLauncher(): QrScanLauncher {
    val context = LocalContext.current
    return remember(context) { createQrScanLauncher(context) }
}

private fun createQrScanLauncher(context: Context): QrScanLauncher {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()
    val scanner = GmsBarcodeScanning.getClient(context, options)
    return QrScanLauncher(scanner)
}
