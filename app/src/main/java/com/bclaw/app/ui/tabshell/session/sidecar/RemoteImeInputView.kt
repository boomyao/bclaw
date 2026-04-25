package com.bclaw.app.ui.tabshell.session.sidecar

import android.content.Context
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import com.bclaw.app.remote.SunshineKey
import com.bclaw.app.remote.SunshineModifier
import com.bclaw.app.remote.SunshineVideoStream

internal class RemoteImeInputView(context: Context) : View(context) {
    var streamInput: SunshineVideoStream? = null
    var textCommitInterceptor: ((String) -> Unit)? = null

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO
    }

    fun showKeyboard() {
        requestFocus()
        post {
            val inputManager = context.getSystemService(InputMethodManager::class.java)
            inputManager.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_ACTION_NONE or
            EditorInfo.IME_FLAG_NO_EXTRACT_UI or
            EditorInfo.IME_FLAG_NO_FULLSCREEN
        return RemoteInputConnection(this)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        handleKeyEvent(event) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        handleKeyEvent(event) || super.onKeyUp(keyCode, event)

    private fun handleKeyEvent(event: KeyEvent): Boolean {
        val action = event.action
        if (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP) return false

        val mappedKey = event.keyCode.toSunshineKeyCode()
        if (mappedKey != null) {
            streamInput?.sendKeyboardKey(
                pressed = action == KeyEvent.ACTION_DOWN,
                keyCode = mappedKey,
                modifiers = event.sunshineModifiers(mappedKey),
            )
            return true
        }

        val unicodeChar = event.unicodeChar
        if (
            unicodeChar in 1..Character.MAX_CODE_POINT &&
            !event.isCtrlPressed &&
            !event.isAltPressed &&
            !event.isMetaPressed
        ) {
            if (action == KeyEvent.ACTION_DOWN) {
                sendText(String(Character.toChars(unicodeChar)))
            }
            return true
        }
        return false
    }

    private fun sendText(text: String) {
        if (text.isEmpty()) return
        val interceptor = textCommitInterceptor
        if (interceptor != null) {
            interceptor(text)
        } else {
            streamInput?.sendUtf8Text(text)
        }
    }

    private class RemoteInputConnection(
        private val target: RemoteImeInputView,
    ) : BaseInputConnection(target, false) {
        private var composingText = ""

        override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
            composingText = ""
            target.sendText(text.toString())
            return true
        }

        override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
            composingText = text.toString()
            return true
        }

        override fun finishComposingText(): Boolean {
            if (composingText.isNotEmpty()) {
                target.sendText(composingText)
                composingText = ""
            }
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            repeat(beforeLength.coerceIn(0, MAX_SYNTHETIC_DELETE_KEYS)) {
                target.sendKeyStroke(SunshineKey.BACKSPACE)
            }
            repeat(afterLength.coerceIn(0, MAX_SYNTHETIC_DELETE_KEYS)) {
                target.sendKeyStroke(SunshineKey.DELETE)
            }
            return true
        }

        override fun sendKeyEvent(event: KeyEvent): Boolean =
            target.handleKeyEvent(event) || super.sendKeyEvent(event)

        override fun performEditorAction(actionCode: Int): Boolean {
            target.sendKeyStroke(SunshineKey.ENTER)
            return true
        }

        override fun getTextBeforeCursor(length: Int, flags: Int): CharSequence = ""

        override fun getTextAfterCursor(length: Int, flags: Int): CharSequence = ""
    }

    private fun sendKeyStroke(keyCode: Int) {
        streamInput?.sendKeyboardKey(pressed = true, keyCode = keyCode)
        streamInput?.sendKeyboardKey(pressed = false, keyCode = keyCode)
    }
}

private fun KeyEvent.sunshineModifiers(mappedKey: Int): Int {
    if (mappedKey.isModifierKey()) return SunshineModifier.NONE
    var modifiers = SunshineModifier.NONE
    if (isShiftPressed) modifiers = modifiers or SunshineModifier.SHIFT
    if (isCtrlPressed) modifiers = modifiers or SunshineModifier.CTRL
    if (isAltPressed) modifiers = modifiers or SunshineModifier.ALT
    if (isMetaPressed) modifiers = modifiers or SunshineModifier.META
    return modifiers
}

private fun Int.isModifierKey(): Boolean =
    this == SunshineKey.LEFT_SHIFT ||
        this == SunshineKey.LEFT_CONTROL ||
        this == SunshineKey.RIGHT_CONTROL ||
        this == SunshineKey.LEFT_ALT ||
        this == SunshineKey.RIGHT_ALT ||
        this == SunshineKey.LEFT_META ||
        this == SunshineKey.RIGHT_META

private fun Int.toSunshineKeyCode(): Int? =
    when (this) {
        KeyEvent.KEYCODE_DEL -> SunshineKey.BACKSPACE
        KeyEvent.KEYCODE_FORWARD_DEL -> SunshineKey.DELETE
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> SunshineKey.ENTER
        KeyEvent.KEYCODE_TAB -> SunshineKey.TAB
        KeyEvent.KEYCODE_ESCAPE -> SunshineKey.ESCAPE
        KeyEvent.KEYCODE_SPACE -> SunshineKey.SPACE
        KeyEvent.KEYCODE_DPAD_UP -> SunshineKey.UP
        KeyEvent.KEYCODE_DPAD_DOWN -> SunshineKey.DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> SunshineKey.LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> SunshineKey.RIGHT
        KeyEvent.KEYCODE_MOVE_HOME -> SunshineKey.HOME
        KeyEvent.KEYCODE_MOVE_END -> SunshineKey.END
        KeyEvent.KEYCODE_PAGE_UP -> SunshineKey.PAGE_UP
        KeyEvent.KEYCODE_PAGE_DOWN -> SunshineKey.PAGE_DOWN
        KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> SunshineKey.LEFT_SHIFT
        KeyEvent.KEYCODE_CTRL_LEFT -> SunshineKey.LEFT_CONTROL
        KeyEvent.KEYCODE_CTRL_RIGHT -> SunshineKey.RIGHT_CONTROL
        KeyEvent.KEYCODE_ALT_LEFT -> SunshineKey.LEFT_ALT
        KeyEvent.KEYCODE_ALT_RIGHT -> SunshineKey.RIGHT_ALT
        KeyEvent.KEYCODE_META_LEFT -> SunshineKey.LEFT_META
        KeyEvent.KEYCODE_META_RIGHT -> SunshineKey.RIGHT_META
        in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> 0x41 + (this - KeyEvent.KEYCODE_A)
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> 0x30 + (this - KeyEvent.KEYCODE_0)
        else -> null
    }

private const val MAX_SYNTHETIC_DELETE_KEYS = 64
