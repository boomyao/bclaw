package com.bclaw.app.ui.components.connection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions

@Composable
fun rememberBclawQrScanner(
    onResult: (String) -> Unit,
    onError: (String) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val scanner = remember {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        GmsBarcodeScanning.getClient(context, options)
    }

    return remember(scanner) {
        {
            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    val rawValue = barcode.rawValue
                    if (rawValue != null && rawValue.startsWith("bclaw1://")) {
                        onResult(rawValue)
                    } else {
                        onError("Not a bclaw connection QR code")
                    }
                }
                .addOnCanceledListener {
                    // User cancelled — do nothing
                }
                .addOnFailureListener { e ->
                    onError(e.localizedMessage ?: "Scan failed")
                }
        }
    }
}
