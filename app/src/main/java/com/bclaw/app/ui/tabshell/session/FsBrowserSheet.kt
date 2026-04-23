package com.bclaw.app.ui.tabshell.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bclaw.app.domain.v2.FileAttachment
import com.bclaw.app.net.acp.BridgeFsClient
import com.bclaw.app.net.acp.FsEntry
import com.bclaw.app.ui.LocalBclawController
import com.bclaw.app.ui.components.BclawBottomSheet
import com.bclaw.app.ui.theme.BclawTheme
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Navigator over the paired mac's cwd. Drills into directories; tapping a file adds it to
 * the composer as a [FileAttachment] ref and closes the sheet. Content isn't loaded here —
 * only at send time (by the controller, via [BridgeFsClient.read]) and embedded as an ACP
 * `resource` block. Keeping content off the picker-path keeps navigation snappy even in
 * repos with huge files.
 */
@Composable
fun FsBrowserSheet(
    visible: Boolean,
    bridgeWsUrl: String?,
    cwd: String?,
    onDismissRequest: () -> Unit,
    onAttach: (FileAttachment) -> Unit,
) {
    if (!visible || bridgeWsUrl == null || cwd == null) {
        BclawBottomSheet(visible = false, onDismissRequest = onDismissRequest) {}
        return
    }

    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    val controller = LocalBclawController.current
    val pinnedDirs by remember(cwd) { controller.pinnedDirsFor(cwd) }
        .collectAsState(initial = emptyList())

    // Track an absolute path rather than a (cwd, rel) pair so we can navigate above the
    // session's project root. The sandbox still clamps at `safeRoot` (reported by the
    // bridge, usually $HOME).
    var currentPath by remember(cwd) { mutableStateOf(cwd) }
    var safeRoot by remember(cwd) { mutableStateOf(cwd) }
    var loading by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf<List<FsEntry>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(visible, currentPath) {
        if (!visible) return@LaunchedEffect
        loading = true
        error = null
        val listing = BridgeFsClient.list(bridgeWsUrl, currentPath, ".")
        if (listing == null) {
            error = "could not reach bridge"
            entries = emptyList()
        } else {
            entries = listing.entries
            if (listing.safeRoot.isNotEmpty()) safeRoot = listing.safeRoot
            if (listing.absPath.isNotEmpty() && listing.absPath != currentPath) {
                // Bridge returned the realpath — sync the state so the next up-nav uses it.
                currentPath = listing.absPath
            }
        }
        loading = false
    }

    BclawBottomSheet(
        visible = visible,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = sp.pageGutter, vertical = sp.sp5),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "mac files",
                    style = type.h2,
                    color = colors.inkPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "✕",
                    style = type.h2,
                    color = colors.inkTertiary,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismissRequest,
                        )
                        .padding(sp.sp2),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = sp.sp2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = displayPath(currentPath, safeRoot),
                    style = type.mono,
                    color = colors.inkTertiary,
                    modifier = Modifier.weight(1f),
                )
                // Pinning stores the absolute path, so bouncing between sessions still
                // finds the same pin. Disabled at safeRoot (~/ home) — no point pinning it.
                val canPin = currentPath != safeRoot
                val currentlyPinned = currentPath in pinnedDirs
                Text(
                    text = if (currentlyPinned) "★ pinned" else "☆ pin",
                    style = type.meta,
                    color = when {
                        !canPin -> colors.inkMuted
                        currentlyPinned -> colors.accent
                        else -> colors.inkSecondary
                    },
                    modifier = Modifier
                        .clickable(
                            enabled = canPin,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { controller.togglePinnedDir(cwd, currentPath) },
                        )
                        .padding(horizontal = sp.sp2, vertical = sp.sp1),
                )
            }

            if (pinnedDirs.isNotEmpty()) {
                PinnedStrip(
                    pinned = pinnedDirs,
                    currentPath = currentPath,
                    onJump = { target -> currentPath = target },
                    onRemove = { target -> controller.togglePinnedDir(cwd, target) },
                )
                Spacer(Modifier.height(sp.sp2))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = sp.sp1),
            ) {
                when {
                    loading -> Text(
                        "loading…",
                        style = type.bodySmall,
                        color = colors.inkTertiary,
                    )
                    error != null -> Text(
                        error!!,
                        style = type.bodySmall,
                        color = colors.roleError,
                    )
                    entries.isEmpty() -> Text(
                        "empty",
                        style = type.bodySmall,
                        color = colors.inkTertiary,
                    )
                    else -> LazyColumn {
                        if (currentPath != safeRoot) {
                            item {
                                EntryRow(
                                    glyph = "↑",
                                    label = "..",
                                    meta = "",
                                    onClick = { currentPath = parentOfAbs(currentPath, safeRoot) },
                                )
                            }
                        }
                        items(entries, key = { it.name }) { entry ->
                            val entryAbsPath = joinAbs(currentPath, entry.name)
                            val entryMime = mimeTypeFor(entry.name)
                            val isImage = entry.kind == "file" &&
                                entryMime?.startsWith("image/") == true
                            val onRowClick: () -> Unit = {
                                if (entry.kind == "dir") {
                                    currentPath = entryAbsPath
                                } else if (entry.kind == "file") {
                                    onAttach(
                                        FileAttachment(
                                            // Caller uses (cwd + rel) against /fs/read.
                                            // The containing dir plays the role of cwd;
                                            // rel is just the filename.
                                            cwd = currentPath,
                                            rel = entry.name,
                                            sizeBytes = entry.size ?: 0,
                                            truncated = (entry.size ?: 0)
                                                > BridgeFsClient.DEFAULT_MAX_READ_BYTES,
                                            mimeType = entryMime,
                                        ),
                                    )
                                    onDismissRequest()
                                }
                            }
                            val meta = when {
                                entry.kind == "dir" -> ""
                                entry.size == null -> ""
                                else -> formatSize(entry.size)
                            }
                            if (isImage) {
                                ImageEntryRow(
                                    bridgeWsUrl = bridgeWsUrl,
                                    cwd = currentPath,
                                    rel = entry.name,
                                    label = entry.name,
                                    meta = meta,
                                    sizeBytes = entry.size ?: 0,
                                    onClick = onRowClick,
                                )
                            } else {
                                EntryRow(
                                    glyph = when (entry.kind) {
                                        "dir" -> "▸"
                                        else -> "·"
                                    },
                                    label = entry.name,
                                    meta = meta,
                                    onClick = onRowClick,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageEntryRow(
    bridgeWsUrl: String,
    cwd: String,
    rel: String,
    label: String,
    meta: String,
    sizeBytes: Long,
    onClick: () -> Unit,
) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    // Only fetch if the file fits in a single /fs/read response (256 KB cap). Larger
    // images fall back to a plain glyph to avoid pulling truncated bytes that won't
    // decode. The fetch itself happens per-row-composition; LazyColumn disposes offscreen
    // rows so scrolling past unloaded rows cancels their LaunchedEffect before the bytes
    // return.
    val shouldFetch = sizeBytes in 1..BridgeFsClient.DEFAULT_MAX_READ_BYTES.toLong()
    var thumbBytes by remember(rel) { mutableStateOf<ByteArray?>(null) }
    if (shouldFetch) {
        LaunchedEffect(rel) {
            val fetched = BridgeFsClient.read(bridgeWsUrl, cwd, rel) ?: return@LaunchedEffect
            thumbBytes = withContext(Dispatchers.Default) {
                runCatching { java.util.Base64.getDecoder().decode(fetched.data) }.getOrNull()
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = sp.sp2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sp.sp2),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(colors.surfaceDeep),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbBytes != null) {
                AsyncImage(
                    model = thumbBytes,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(28.dp),
                )
            } else {
                Text(text = "·", style = type.mono, color = colors.inkTertiary)
            }
        }
        Text(
            text = label,
            style = type.bodyLarge,
            color = colors.inkPrimary,
            modifier = Modifier.weight(1f),
        )
        if (meta.isNotEmpty()) {
            Text(text = meta, style = type.meta, color = colors.inkTertiary)
        }
    }
}

@Composable
private fun EntryRow(
    glyph: String,
    label: String,
    meta: String,
    onClick: () -> Unit,
) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = sp.sp2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sp.sp2),
    ) {
        Text(text = glyph, style = type.mono, color = colors.inkTertiary)
        Text(
            text = label,
            style = type.bodyLarge,
            color = colors.inkPrimary,
            modifier = Modifier.weight(1f),
        )
        if (meta.isNotEmpty()) {
            Text(text = meta, style = type.meta, color = colors.inkTertiary)
        }
    }
}

@Composable
private fun PinnedStrip(
    pinned: List<String>,
    currentPath: String,
    onJump: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing
    Column {
        Text(
            text = "PINNED",
            style = type.meta,
            color = colors.inkTertiary,
            modifier = Modifier.padding(top = sp.sp2, bottom = sp.sp1),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(sp.sp2),
        ) {
            pinned.forEach { path ->
                val active = path == currentPath
                Row(
                    modifier = Modifier
                        .background(if (active) colors.surfaceRaised else colors.surfaceDeep)
                        .border(1.dp, if (active) colors.accent else colors.borderSubtle)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onJump(path) },
                        )
                        .padding(horizontal = sp.sp2, vertical = sp.sp1),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(sp.sp1),
                ) {
                    Text("★", style = type.body, color = colors.accent)
                    Text(
                        text = path.substringAfterLast('/').ifEmpty { path },
                        style = type.mono,
                        color = colors.inkPrimary,
                    )
                    Text(
                        text = "✕",
                        style = type.meta,
                        color = colors.inkTertiary,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onRemove(path) },
                            )
                            .padding(start = sp.sp1),
                    )
                }
            }
        }
    }
}

private fun joinAbs(parent: String, child: String): String =
    "${parent.trimEnd('/')}/$child"

private fun parentOfAbs(path: String, safeRoot: String): String {
    if (path == safeRoot) return safeRoot
    val trimmed = path.trimEnd('/')
    val idx = trimmed.lastIndexOf('/')
    if (idx <= 0) return safeRoot
    val candidate = trimmed.substring(0, idx)
    return if (candidate.length < safeRoot.length) safeRoot else candidate
}

/**
 * Compact breadcrumb: strip the safeRoot prefix so `~/projects/bclaw/app` shows instead of
 * `/Users/boomyao/projects/bclaw/app`. Falls back to trailing path segments if the current
 * path is outside safeRoot (shouldn't happen with a working sandbox, but defensive).
 */
private fun displayPath(current: String, safeRoot: String): String {
    if (current == safeRoot) return "~"
    if (current.startsWith("$safeRoot/")) return "~" + current.removePrefix(safeRoot)
    val parts = current.trimEnd('/').split('/').filter { it.isNotEmpty() }
    return if (parts.size <= 3) current else "…/${parts.takeLast(3).joinToString("/")}"
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> "${bytes / 1024}K"
    else -> "${bytes / (1024 * 1024)}M"
}

private fun mimeTypeFor(name: String): String? = when (name.substringAfterLast('.', "").lowercase()) {
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "heic", "heif" -> "image/heic"
    "bmp" -> "image/bmp"
    "svg" -> "image/svg+xml"
    else -> null
}
