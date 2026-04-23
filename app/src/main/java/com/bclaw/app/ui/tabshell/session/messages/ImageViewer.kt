package com.bclaw.app.ui.tabshell.session.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * CompositionLocal handing out a "open this image full-screen" callback. Any message
 * renderer deeper in the tree can call [LocalImageViewer.current] to pop a thumbnail
 * into the preview overlay without knowing where the Dialog itself lives.
 *
 * Default is a no-op so composables can be previewed in isolation without crashes.
 */
val LocalImageViewer = compositionLocalOf<(String) -> Unit> { {} }

/**
 * Wraps [content] with an image-preview launcher. A single full-screen Dialog is hosted
 * at this level; children get a callback that sets the target path, which triggers the
 * Dialog to show on next composition. Tapping the Dialog (or pressing back) dismisses.
 */
@Composable
fun ImageViewerHost(content: @Composable () -> Unit) {
    var previewPath by remember { mutableStateOf<String?>(null) }
    CompositionLocalProvider(
        LocalImageViewer provides { path -> previewPath = path },
    ) {
        content()
    }
    val target = previewPath
    if (target != null) {
        Dialog(
            onDismissRequest = { previewPath = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { previewPath = null },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                BridgeImage(
                    absPath = target,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
