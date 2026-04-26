package com.bclaw.app.ui.tabshell.session.sidecar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import android.view.HapticFeedbackConstants
import com.bclaw.app.remote.SunshineKey
import com.bclaw.app.remote.SunshineModifier
import com.bclaw.app.remote.SunshineVideoStream
import com.bclaw.app.ui.theme.BclawTheme
import com.bclaw.app.ui.theme.MetroCyan
import com.bclaw.app.ui.theme.MetroOrange

/**
 * Sticky modifier state on the accessory bar.
 *  - Off:    not pressed.
 *  - Locked: one-shot — applied to the next non-modifier key, then auto-released.
 *  - Held:   sticky-held — stays applied across multiple keys until tapped off.
 */
internal enum class StickyModifierState { Off, Locked, Held }

/**
 * Hoisted state for the accessory bar's four sticky modifier keys. Living above the bar
 * lets the soft-IME text-commit path (in RemoteStreamSurface) read the same state and
 * route a tapped letter as a `Ctrl+V` / `Cmd+Space` keystroke instead of UTF-8 text —
 * otherwise locking Ctrl on the bar would do nothing for keys typed on the system IME.
 */
@androidx.compose.runtime.Stable
internal class RemoteStickyModifiers {
    var ctrl by mutableStateOf(StickyModifierState.Off)
    var alt by mutableStateOf(StickyModifierState.Off)
    var meta by mutableStateOf(StickyModifierState.Off)
    var shift by mutableStateOf(StickyModifierState.Off)

    /** Sunshine modifier mask for whatever's currently lit (Locked or Held). */
    fun mask(): Int {
        var m = SunshineModifier.NONE
        if (ctrl != StickyModifierState.Off) m = m or SunshineModifier.CTRL
        if (alt != StickyModifierState.Off) m = m or SunshineModifier.ALT
        if (meta != StickyModifierState.Off) m = m or SunshineModifier.META
        if (shift != StickyModifierState.Off) m = m or SunshineModifier.SHIFT
        return m
    }

    /** Release every Locked modifier. Held modifiers stay engaged. */
    fun consumeOneShot() {
        if (ctrl == StickyModifierState.Locked) ctrl = StickyModifierState.Off
        if (alt == StickyModifierState.Locked) alt = StickyModifierState.Off
        if (meta == StickyModifierState.Locked) meta = StickyModifierState.Off
        if (shift == StickyModifierState.Locked) shift = StickyModifierState.Off
    }
}

/**
 * Maps a single character coming out of the soft IME to a Sunshine VK key code so we can
 * apply accessory-bar modifiers to it. Returns null if no clean mapping exists; callers
 * should fall back to the UTF-8 text path for those.
 */
internal fun remoteCharToSunshineKey(c: Char): Int? {
    val upper = c.uppercaseChar()
    return when (upper) {
        in 'A'..'Z' -> 0x41 + (upper - 'A')
        in '0'..'9' -> 0x30 + (upper - '0')
        ' ' -> SunshineKey.SPACE
        '\t' -> SunshineKey.TAB
        '\n', '\r' -> SunshineKey.ENTER
        else -> null
    }
}

internal fun remoteSunshineKeyIsModifier(keyCode: Int): Boolean =
    keyCode == SunshineKey.LEFT_SHIFT ||
        keyCode == SunshineKey.LEFT_CONTROL ||
        keyCode == SunshineKey.RIGHT_CONTROL ||
        keyCode == SunshineKey.LEFT_ALT ||
        keyCode == SunshineKey.RIGHT_ALT ||
        keyCode == SunshineKey.LEFT_META ||
        keyCode == SunshineKey.RIGHT_META

/**
 * Accessory bar that pins above the soft keyboard. Provides PC-keyboard keys that the
 * mobile IME doesn't expose:
 *
 *   [ AI ] │ [Ctrl][Alt][Cmd|Win][Shift] [Esc][Tab][←][↓][↑][→][Home][End] [fn ▾]
 *
 * Modifiers latch (tap = one-shot, long-press = sticky). The fn dropdown carries F1–F12,
 * Insert/Delete, PgUp/PgDn, and a Ctrl+Alt+Del shortcut. The dropdown uses a non-focusable
 * Popup so opening it doesn't steal focus from the soft IME (which would close it and
 * trigger a relayout that dismisses the menu before the user can tap an entry).
 */
@Composable
internal fun RemoteImeAccessoryBar(
    streamInput: SunshineVideoStream?,
    hostPlatform: String?,
    aiEnabled: Boolean,
    aiBusy: Boolean,
    onToggleAi: () -> Unit,
    stickyModifiers: RemoteStickyModifiers,
    modifier: Modifier = Modifier,
) {
    var fnOpen by remember { mutableStateOf(false) }

    val isMac = hostPlatform.equals("darwin", ignoreCase = true)
    val metaLabel = if (isMac) "Cmd" else "Win"

    fun sendKey(keyCode: Int, extraMods: Int = SunshineModifier.NONE) {
        val stream = streamInput ?: return
        val mods = stickyModifiers.mask() or extraMods
        stream.sendKeyboardChord(keyCode = keyCode, modifiers = mods)
        stickyModifiers.consumeOneShot()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .requiredHeight(34.dp)
            .background(Color(0xFF0A0A0A))
            .border(BorderStroke(1.dp, Color(0xFF26261C)))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        AccessoryAiButton(
            enabled = aiEnabled,
            busy = aiBusy,
            onToggle = onToggleAi,
        )
        AccessoryDivider()

        ModifierKey(label = "Ctrl", state = stickyModifiers.ctrl, onCycle = { stickyModifiers.ctrl = it })
        ModifierKey(label = "Alt", state = stickyModifiers.alt, onCycle = { stickyModifiers.alt = it })
        ModifierKey(label = metaLabel, state = stickyModifiers.meta, onCycle = { stickyModifiers.meta = it })
        ModifierKey(label = "Shift", state = stickyModifiers.shift, onCycle = { stickyModifiers.shift = it })
        AccessoryDivider()

        AccessoryKey(label = "Esc") { sendKey(SunshineKey.ESCAPE) }
        AccessoryKey(label = "Tab") { sendKey(SunshineKey.TAB) }
        AccessoryKey(label = "←") { sendKey(SunshineKey.LEFT) }
        AccessoryKey(label = "↓") { sendKey(SunshineKey.DOWN) }
        AccessoryKey(label = "↑") { sendKey(SunshineKey.UP) }
        AccessoryKey(label = "→") { sendKey(SunshineKey.RIGHT) }
        AccessoryKey(label = "Home") { sendKey(SunshineKey.HOME) }
        AccessoryKey(label = "End") { sendKey(SunshineKey.END) }
        AccessoryDivider()

        Box {
            AccessoryKey(label = "fn ▾", onClick = { fnOpen = !fnOpen })
            if (fnOpen) {
                AbovePopover(onDismiss = { fnOpen = false }) {
                    Column(
                        modifier = Modifier
                            .background(Color(0xFF0A0A0A))
                            .border(BorderStroke(1.dp, Color(0xFF26261C)))
                            // Cap height so the list scrolls instead of running off the top
                            // of the screen. ~6.5 entries are visible — enough to make the
                            // list feel browseable but signal there's more.
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        FnEntry("F1") { sendKey(SunshineKey.F1); fnOpen = false }
                        FnEntry("F2") { sendKey(SunshineKey.F2); fnOpen = false }
                        FnEntry("F3") { sendKey(SunshineKey.F3); fnOpen = false }
                        FnEntry("F4") { sendKey(SunshineKey.F4); fnOpen = false }
                        FnEntry("F5") { sendKey(SunshineKey.F5); fnOpen = false }
                        FnEntry("F6") { sendKey(SunshineKey.F6); fnOpen = false }
                        FnEntry("F7") { sendKey(SunshineKey.F7); fnOpen = false }
                        FnEntry("F8") { sendKey(SunshineKey.F8); fnOpen = false }
                        FnEntry("F9") { sendKey(SunshineKey.F9); fnOpen = false }
                        FnEntry("F10") { sendKey(SunshineKey.F10); fnOpen = false }
                        FnEntry("F11") { sendKey(SunshineKey.F11); fnOpen = false }
                        FnEntry("F12") { sendKey(SunshineKey.F12); fnOpen = false }
                        FnEntry("PgUp") { sendKey(SunshineKey.PAGE_UP); fnOpen = false }
                        FnEntry("PgDn") { sendKey(SunshineKey.PAGE_DOWN); fnOpen = false }
                        FnEntry("Insert") { sendKey(SunshineKey.INSERT); fnOpen = false }
                        FnEntry("Delete") { sendKey(SunshineKey.DELETE); fnOpen = false }
                        FnEntry("Ctrl+Alt+Del") {
                            val stream = streamInput
                            if (stream != null) {
                                val mods = SunshineModifier.CTRL or SunshineModifier.ALT
                                stream.sendKeyboardChord(keyCode = SunshineKey.DELETE, modifiers = mods)
                            }
                            fnOpen = false
                        }
                    }
                }
            }
        }
    }
}

/**
 * Popup that anchors *above* its enclosing element with a small gap, never steals focus
 * from the soft IME, and auto-dismisses on outside taps. Use this for menus/popovers that
 * appear on top of the keyboard accessory bar or floating overlays.
 */
@Composable
internal fun AbovePopover(
    onDismiss: () -> Unit,
    alignEnd: Boolean = false,
    gapDp: Int = 6,
    content: @Composable () -> Unit,
) {
    val gapPx = with(androidx.compose.ui.platform.LocalDensity.current) { gapDp.dp.toPx() }.toInt()
    val provider = remember(alignEnd, gapPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val rawX = if (alignEnd) {
                    anchorBounds.right - popupContentSize.width
                } else {
                    anchorBounds.left
                }
                val x = rawX
                    .coerceAtLeast(0)
                    .coerceAtMost((windowSize.width - popupContentSize.width).coerceAtLeast(0))
                val y = (anchorBounds.top - popupContentSize.height - gapPx).coerceAtLeast(0)
                return IntOffset(x, y)
            }
        }
    }
    Popup(
        popupPositionProvider = provider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = false,
            dismissOnClickOutside = true,
            dismissOnBackPress = true,
        ),
        content = content,
    )
}

@Composable
private fun AccessoryAiButton(
    enabled: Boolean,
    busy: Boolean,
    onToggle: () -> Unit,
) {
    val bg = when {
        busy -> MetroOrange
        enabled -> MetroCyan
        else -> Color(0xFF141410)
    }
    val fg = if (enabled || busy) Color.Black else Color(0xFFD8D2BE)
    val border = when {
        busy -> MetroOrange
        enabled -> MetroCyan
        else -> Color(0xFF26261C)
    }
    Box(
        modifier = Modifier
            .requiredSize(width = 36.dp, height = 26.dp)
            .background(bg)
            .border(BorderStroke(1.dp, border))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (busy) "..." else "AI",
            style = BclawTheme.typography.mono,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
private fun AccessoryDivider() {
    Box(
        modifier = Modifier
            .requiredWidth(1.dp)
            .height(16.dp)
            .background(Color(0xFF26261C)),
    )
}

@Composable
private fun ModifierKey(
    label: String,
    state: StickyModifierState,
    onCycle: (StickyModifierState) -> Unit,
) {
    val view = LocalView.current
    val (bg, fg, border) = when (state) {
        StickyModifierState.Off -> Triple(Color(0xFF141410), Color(0xFFD8D2BE), Color(0xFF26261C))
        StickyModifierState.Locked -> Triple(MetroCyan.copy(alpha = 0.25f), MetroCyan, MetroCyan)
        StickyModifierState.Held -> Triple(MetroCyan, Color.Black, MetroCyan)
    }
    Box(
        modifier = Modifier
            .requiredSize(width = 40.dp, height = 26.dp)
            .background(bg)
            .border(BorderStroke(1.dp, border))
            .pointerInput(state) {
                detectTapGestures(
                    onTap = {
                        val next = when (state) {
                            StickyModifierState.Off -> StickyModifierState.Locked
                            StickyModifierState.Locked -> StickyModifierState.Off
                            StickyModifierState.Held -> StickyModifierState.Off
                        }
                        onCycle(next)
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    },
                    onLongPress = {
                        if (state != StickyModifierState.Held) {
                            onCycle(StickyModifierState.Held)
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = BclawTheme.typography.mono,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
private fun AccessoryKey(
    label: String,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    Box(
        modifier = Modifier
            .requiredSize(width = 36.dp, height = 26.dp)
            .background(Color(0xFF141410))
            .border(BorderStroke(1.dp, Color(0xFF26261C)))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    onClick()
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = BclawTheme.typography.mono,
            color = Color(0xFFD8D2BE),
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
private fun FnEntry(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .requiredSize(width = 160.dp, height = 36.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            style = BclawTheme.typography.body,
            color = Color(0xFFD8D2BE),
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}
