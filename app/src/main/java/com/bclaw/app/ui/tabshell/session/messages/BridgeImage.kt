package com.bclaw.app.ui.tabshell.session.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.bclaw.app.net.acp.BridgeFsClient
import com.bclaw.app.ui.LocalBclawController
import com.bclaw.app.ui.theme.BclawTheme

/**
 * Renders an image at [absPath] on the paired Mac by hitting the bridge's `/fs/raw`
 * endpoint, which streams the file as raw bytes. Caching is Coil's responsibility —
 * memory + disk cache keyed by URL, so repeated loads of the same path are free.
 *
 * Raw-streaming is the fast path: the earlier /fs/read approach base64-encoded 3 MB
 * PNGs into 4 MB JSON that the phone then had to decode before handing bytes to the
 * image loader, multiplying work both directions. With /fs/raw Coil decodes directly
 * from the HTTP byte stream.
 */
@Composable
fun BridgeImage(
    absPath: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val controller = LocalBclawController.current
    val bridgeWsUrl = controller.uiState.value.deviceBook.activeDevice?.wsBaseUrl

    if (bridgeWsUrl.isNullOrBlank()) {
        Box(
            modifier = modifier.background(colors.surfaceDeep),
            contentAlignment = Alignment.Center,
        ) {
            Text("·", style = type.mono, color = colors.inkTertiary)
        }
        return
    }

    AsyncImage(
        model = BridgeFsClient.rawUrl(bridgeWsUrl, absPath),
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier.background(colors.surfaceDeep),
    )
}
