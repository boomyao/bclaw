package com.bclaw.app.remote

import java.io.Closeable
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class SunshineControlProbe(
    val connected: Boolean,
    val encrypted: Boolean,
    val error: String?,
)

internal class SunshineControlSession private constructor(
    private val handle: Long,
    private val key: ByteArray,
    private val encrypted: Boolean,
    private val hostFeatureFlags: Int,
) : Closeable {
    private val closed = AtomicBoolean(false)
    private val sequence = AtomicInteger(0)
    private val touchInputSupported = (hostFeatureFlags and SUNSHINE_FEATURE_PEN_TOUCH_EVENTS) != 0

    fun start() {
        if (encrypted) {
            sendEncrypted(type = 0x0302, payload = byteArrayOf(0, 0), reliable = true)
            sendEncrypted(type = 0x0307, payload = byteArrayOf(0), reliable = true)
        } else {
            sendPlain(type = 0x0305, payload = byteArrayOf(0, 0), reliable = true)
            sendPlain(type = 0x0307, payload = byteArrayOf(0), reliable = true)
        }
    }

    fun sendMousePosition(x: Int, y: Int, referenceWidth: Int, referenceHeight: Int, reliable: Boolean = false) {
        sendInputPacket(
            buildMousePositionPacket(x, y, referenceWidth, referenceHeight),
            reliable = reliable,
        )
    }

    fun sendMouseMove(deltaX: Int, deltaY: Int) {
        val packet = buildMouseMovePacket(deltaX, deltaY) ?: return
        sendInputPacket(packet, reliable = false)
    }

    fun sendMouseScroll(scrollAmount: Int) {
        val packet = buildMouseScrollPacket(scrollAmount) ?: return
        sendInputPacket(packet, reliable = true)
    }

    fun sendMouseHScroll(scrollAmount: Int) {
        val packet = buildMouseHScrollPacket(scrollAmount) ?: return
        sendInputPacket(packet, reliable = true)
    }

    fun sendMouseButton(pressed: Boolean, button: Int = SunshineMouseButton.LEFT) {
        sendInputPacket(buildMouseButtonPacket(pressed, button), reliable = true)
    }

    fun sendKeyboardKey(
        pressed: Boolean,
        keyCode: Int,
        modifiers: Int = SunshineModifier.NONE,
        flags: Int = 0,
    ) {
        sendInputPacket(
            buildKeyboardPacket(pressed, keyCode, modifiers, flags),
            reliable = true,
            channelId = CTRL_CHANNEL_KEYBOARD,
        )
    }

    fun sendKeyboardShortcut(modifierKeyCode: Int, keyCode: Int) {
        sendKeyboardKey(pressed = true, keyCode = modifierKeyCode)
        sendKeyboardKey(pressed = true, keyCode = keyCode)
        sendKeyboardKey(pressed = false, keyCode = keyCode)
        sendKeyboardKey(pressed = false, keyCode = modifierKeyCode)
    }

    fun sendUtf8Text(text: String) {
        text.forEachCodePointUtf8 { bytes ->
            sendInputPacket(
                buildUtf8TextPacket(bytes),
                reliable = true,
                channelId = CTRL_CHANNEL_UTF8,
            )
        }
    }

    fun sendTouchEvent(
        eventType: Int,
        pointerId: Int,
        x: Float,
        y: Float,
        pressureOrDistance: Float = 0f,
        contactAreaMajor: Float = 0f,
        contactAreaMinor: Float = 0f,
        rotation: Int = LI_ROT_UNKNOWN,
    ): Boolean {
        if (!touchInputSupported) return false
        sendInputPacket(
            buildTouchPacket(
                eventType = eventType,
                pointerId = pointerId,
                x = x,
                y = y,
                pressureOrDistance = pressureOrDistance,
                contactAreaMajor = contactAreaMajor,
                contactAreaMinor = contactAreaMinor,
                rotation = rotation,
            ),
            reliable = !isBatchableTouchEvent(eventType),
            channelId = CTRL_CHANNEL_TOUCH,
        )
        return true
    }

    fun sendPeriodicPing() {
        val payload = byteArrayOf(4, 0, 0, 0, 0, 0, 0, 0)
        if (encrypted) {
            sendEncrypted(type = 0x0200, payload = payload, reliable = true)
        } else {
            sendPlain(type = 0x0200, payload = payload, reliable = true)
        }
    }

    fun service(timeoutMs: Int): Int =
        if (closed.get()) 0 else NativeEnet.nativeService(handle, timeoutMs)

    private fun sendInputPacket(
        packet: ByteArray,
        reliable: Boolean,
        channelId: Int = CTRL_CHANNEL_MOUSE,
    ) {
        if (encrypted) {
            sendEncrypted(
                type = 0x0206,
                payload = packet,
                reliable = reliable,
                channelId = channelId,
            )
        } else {
            sendPlain(
                type = 0x0206,
                payload = packet,
                reliable = reliable,
                channelId = channelId,
            )
        }
    }

    private fun sendPlain(
        type: Int,
        payload: ByteArray,
        reliable: Boolean,
        channelId: Int = CTRL_CHANNEL_GENERIC,
    ) {
        val packet = ByteArray(2 + payload.size)
        packet.writeLe16(0, type)
        payload.copyInto(packet, 2)
        sendPacket(packet, channelId = channelId, reliable = reliable)
    }

    private fun sendEncrypted(
        type: Int,
        payload: ByteArray,
        reliable: Boolean,
        channelId: Int = CTRL_CHANNEL_GENERIC,
    ) {
        val plain = ByteArray(4 + payload.size)
        plain.writeLe16(0, type)
        plain.writeLe16(2, payload.size)
        payload.copyInto(plain, 4)

        val seq = sequence.getAndIncrement()
        val iv = ByteArray(12)
        iv.writeLe32(0, seq)
        iv[10] = 'C'.code.toByte()
        iv[11] = 'C'.code.toByte()

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val sealed = cipher.doFinal(plain)
        if (sealed.size < AES_GCM_TAG_LENGTH) {
            throw IOException("control encryption produced a runt packet")
        }

        val ciphertextLength = sealed.size - AES_GCM_TAG_LENGTH
        val packet = ByteArray(8 + AES_GCM_TAG_LENGTH + ciphertextLength)
        packet.writeLe16(0, 0x0001)
        packet.writeLe16(2, 4 + AES_GCM_TAG_LENGTH + plain.size)
        packet.writeLe32(4, seq)
        sealed.copyInto(
            destination = packet,
            destinationOffset = 8,
            startIndex = ciphertextLength,
            endIndex = sealed.size,
        )
        sealed.copyInto(
            destination = packet,
            destinationOffset = 8 + AES_GCM_TAG_LENGTH,
            startIndex = 0,
            endIndex = ciphertextLength,
        )
        sendPacket(packet, channelId = channelId, reliable = reliable)
    }

    private fun sendPacket(packet: ByteArray, channelId: Int, reliable: Boolean) {
        if (closed.get()) return
        if (!NativeEnet.nativeSend(handle, packet, channelId, reliable, unsequenced = false)) {
            throw IOException("failed to send ENet control packet")
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            NativeEnet.nativeClose(handle)
        }
    }

    companion object {
        fun connect(
            host: String,
            port: Int,
            connectData: String,
            riKey: String,
            encrypted: Boolean,
            hostFeatureFlags: Int = 0,
        ): SunshineControlSession {
            val key = riKey.hexToBytes()
            if (key.size != 16) {
                throw IOException("invalid riKey length")
            }
            val handle = NativeEnet.nativeCreate(
                host = host,
                port = port,
                connectData = connectData.parseUnsigned32(),
                timeoutMs = 4_000,
            )
            if (handle == 0L) {
                throw IOException("failed to connect ENet control stream")
            }
            return SunshineControlSession(handle, key, encrypted, hostFeatureFlags)
        }
    }
}

internal object SunshineMouseButton {
    const val LEFT = 0x01
    const val MIDDLE = 0x02
    const val RIGHT = 0x03
    const val X1 = 0x04
    const val X2 = 0x05
}

internal object SunshineKey {
    const val BACKSPACE = 0x08
    const val TAB = 0x09
    const val ENTER = 0x0D
    const val ESCAPE = 0x1B
    const val SPACE = 0x20
    const val PAGE_UP = 0x21
    const val PAGE_DOWN = 0x22
    const val END = 0x23
    const val HOME = 0x24
    const val LEFT = 0x25
    const val RIGHT = 0x27
    const val DELETE = 0x2E
    const val LEFT_SHIFT = 0xA0
    const val LEFT_CONTROL = 0xA2
    const val RIGHT_CONTROL = 0xA3
    const val LEFT_ALT = 0xA4
    const val RIGHT_ALT = 0xA5
    const val LEFT_META = 0x5B
    const val RIGHT_META = 0x5C
    const val OEM_PLUS = 0xBB
    const val OEM_MINUS = 0xBD
    const val UP = 0x26
    const val DOWN = 0x28
}

internal object SunshineModifier {
    const val NONE = 0x00
    const val SHIFT = 0x01
    const val CTRL = 0x02
    const val ALT = 0x04
    const val META = 0x08
}

internal object SunshineTouchEvent {
    const val HOVER = 0x00
    const val DOWN = 0x01
    const val UP = 0x02
    const val MOVE = 0x03
    const val CANCEL = 0x04
    const val BUTTON_ONLY = 0x05
    const val HOVER_LEAVE = 0x06
    const val CANCEL_ALL = 0x07
}

internal fun buildMousePositionPacket(
    x: Int,
    y: Int,
    referenceWidth: Int,
    referenceHeight: Int,
): ByteArray {
    val safeWidth = referenceWidth.coerceAtLeast(1)
    val safeHeight = referenceHeight.coerceAtLeast(1)
    val packet = ByteArray(18)
    packet.writeBe32(0, 14)
    packet.writeLe32(4, MOUSE_MOVE_ABS_MAGIC)
    packet.writeBe16(8, x.coerceIn(0, safeWidth - 1))
    packet.writeBe16(10, y.coerceIn(0, safeHeight - 1))
    packet.writeBe16(12, 0)
    packet.writeBe16(14, safeWidth - 1)
    packet.writeBe16(16, safeHeight - 1)
    return packet
}

internal fun buildMouseMovePacket(deltaX: Int, deltaY: Int): ByteArray? {
    val clampedX = deltaX.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
    val clampedY = deltaY.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
    if (clampedX == 0 && clampedY == 0) return null

    val packet = ByteArray(12)
    packet.writeBe32(0, 8)
    packet.writeLe32(4, MOUSE_MOVE_REL_MAGIC_GEN5)
    packet.writeBe16(8, clampedX)
    packet.writeBe16(10, clampedY)
    return packet
}

internal fun buildMouseScrollPacket(scrollAmount: Int): ByteArray? {
    val clamped = scrollAmount.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
    if (clamped == 0) return null

    val packet = ByteArray(14)
    packet.writeBe32(0, 10)
    packet.writeLe32(4, SCROLL_MAGIC_GEN5)
    packet.writeBe16(8, clamped)
    packet.writeBe16(10, clamped)
    packet.writeBe16(12, 0)
    return packet
}

internal fun buildMouseHScrollPacket(scrollAmount: Int): ByteArray? {
    val clamped = scrollAmount.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
    if (clamped == 0) return null

    val packet = ByteArray(10)
    packet.writeBe32(0, 6)
    packet.writeLe32(4, HSCROLL_MAGIC)
    packet.writeBe16(8, clamped)
    return packet
}

internal fun buildMouseButtonPacket(pressed: Boolean, button: Int): ByteArray {
    val packet = ByteArray(9)
    packet.writeBe32(0, 5)
    packet.writeLe32(4, if (pressed) MOUSE_BUTTON_DOWN_MAGIC_GEN5 else MOUSE_BUTTON_UP_MAGIC_GEN5)
    packet[8] = button.toByte()
    return packet
}

internal fun buildKeyboardPacket(
    pressed: Boolean,
    keyCode: Int,
    modifiers: Int = SunshineModifier.NONE,
    flags: Int = 0,
): ByteArray {
    val packet = ByteArray(14)
    packet.writeBe32(0, 10)
    packet.writeLe32(4, if (pressed) KEY_DOWN_EVENT_MAGIC else KEY_UP_EVENT_MAGIC)
    packet[8] = flags.toByte()
    packet.writeLe16(9, keyCode)
    packet[11] = modifiers.toByte()
    packet.writeLe16(12, 0)
    return packet
}

internal fun buildUtf8TextPacket(textBytes: ByteArray): ByteArray {
    val packet = ByteArray(8 + textBytes.size)
    packet.writeBe32(0, 4 + textBytes.size)
    packet.writeLe32(4, UTF8_TEXT_EVENT_MAGIC)
    textBytes.copyInto(packet, 8)
    return packet
}

internal fun buildTouchPacket(
    eventType: Int,
    pointerId: Int,
    x: Float,
    y: Float,
    pressureOrDistance: Float = 0f,
    contactAreaMajor: Float = 0f,
    contactAreaMinor: Float = 0f,
    rotation: Int = LI_ROT_UNKNOWN,
): ByteArray {
    val packet = ByteArray(36)
    packet.writeBe32(0, 32)
    packet.writeLe32(4, SS_TOUCH_MAGIC)
    packet[8] = eventType.toByte()
    packet[9] = 0
    packet.writeLe16(10, rotation)
    packet.writeLe32(12, pointerId)
    packet.writeLeFloat(16, x.normalized())
    packet.writeLeFloat(20, y.normalized())
    packet.writeLeFloat(24, pressureOrDistance.normalized())
    packet.writeLeFloat(28, contactAreaMajor.nonNegativeFinite())
    packet.writeLeFloat(32, contactAreaMinor.nonNegativeFinite())
    return packet
}

private fun isBatchableTouchEvent(eventType: Int): Boolean =
    eventType == SunshineTouchEvent.HOVER || eventType == SunshineTouchEvent.MOVE

internal fun runSunshineControlProbe(
    host: String,
    port: Int?,
    connectData: String?,
    riKey: String?,
    encrypted: Boolean,
    videoWork: () -> Int,
): Pair<SunshineControlProbe, Int> {
    if (port == null || connectData.isNullOrBlank() || riKey.isNullOrBlank()) {
        return SunshineControlProbe(false, encrypted, "missing control metadata") to videoWork()
    }

    val session = runCatching {
        SunshineControlSession.connect(host, port, connectData, riKey, encrypted).also { it.start() }
    }.getOrElse { error ->
        return SunshineControlProbe(false, encrypted, error.message ?: "control stream failed") to videoWork()
    }

    val running = AtomicBoolean(true)
    val serviceThread = Thread(
        {
            var nextPingAt = System.nanoTime()
            while (running.get()) {
                runCatching {
                    if (System.nanoTime() >= nextPingAt) {
                        session.sendPeriodicPing()
                        nextPingAt = System.nanoTime() + PING_INTERVAL_NANOS
                    }
                    session.service(10)
                }.onFailure {
                    running.set(false)
                }
            }
        },
        "BclawSunshineControl",
    )
    serviceThread.isDaemon = true
    serviceThread.start()

    val packetCount = try {
        videoWork()
    } finally {
        running.set(false)
        runCatching { serviceThread.join(250) }
        session.close()
    }
    return SunshineControlProbe(true, encrypted, null) to packetCount
}

internal fun isEncryptedControlAppVersion(version: String?): Boolean {
    val parts = version
        ?.split('.', '-')
        ?.mapNotNull { it.toIntOrNull() }
        .orEmpty()
    val major = parts.getOrNull(0) ?: return true
    val minor = parts.getOrNull(1) ?: 0
    val patch = parts.getOrNull(2) ?: 0
    return major > 7 || (major == 7 && (minor > 1 || (minor == 1 && patch >= 431)))
}

private fun ByteArray.writeLe16(offset: Int, value: Int) {
    this[offset] = (value and 0xff).toByte()
    this[offset + 1] = ((value ushr 8) and 0xff).toByte()
}

private fun ByteArray.writeLe32(offset: Int, value: Int) {
    this[offset] = (value and 0xff).toByte()
    this[offset + 1] = ((value ushr 8) and 0xff).toByte()
    this[offset + 2] = ((value ushr 16) and 0xff).toByte()
    this[offset + 3] = ((value ushr 24) and 0xff).toByte()
}

private fun ByteArray.writeLeFloat(offset: Int, value: Float) {
    writeLe32(offset, value.toBits())
}

private fun Float.normalized(): Float =
    if (isFinite()) coerceIn(0f, 1f) else 0f

private fun Float.nonNegativeFinite(): Float =
    if (isFinite()) coerceAtLeast(0f) else 0f

private fun ByteArray.writeBe16(offset: Int, value: Int) {
    this[offset] = ((value ushr 8) and 0xff).toByte()
    this[offset + 1] = (value and 0xff).toByte()
}

private fun ByteArray.writeBe32(offset: Int, value: Int) {
    this[offset] = ((value ushr 24) and 0xff).toByte()
    this[offset + 1] = ((value ushr 16) and 0xff).toByte()
    this[offset + 2] = ((value ushr 8) and 0xff).toByte()
    this[offset + 3] = (value and 0xff).toByte()
}

private fun String.hexToBytes(): ByteArray {
    val normalized = trim()
    if (normalized.length % 2 != 0) {
        throw IOException("invalid hex string")
    }
    return ByteArray(normalized.length / 2) { index ->
        normalized.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}

private fun String.parseUnsigned32(): Long {
    val value = trim()
    if (value.isBlank()) throw IOException("missing connect data")
    val parsed = if (value.startsWith("0x", ignoreCase = true)) {
        value.substring(2).toLong(16)
    } else {
        value.toLong()
    }
    return parsed and 0xffff_ffffL
}

private inline fun String.forEachCodePointUtf8(block: (ByteArray) -> Unit) {
    var offset = 0
    while (offset < length) {
        val codePoint = codePointAt(offset)
        block(String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8))
        offset += Character.charCount(codePoint)
    }
}

private const val AES_GCM_TAG_LENGTH = 16
private const val CTRL_CHANNEL_GENERIC = 0
private const val CTRL_CHANNEL_KEYBOARD = 2
private const val CTRL_CHANNEL_MOUSE = 3
private const val CTRL_CHANNEL_TOUCH = 5
private const val CTRL_CHANNEL_UTF8 = 6
private const val PING_INTERVAL_NANOS = 100_000_000L
private const val SUNSHINE_FEATURE_PEN_TOUCH_EVENTS = 0x01
private const val MOUSE_MOVE_ABS_MAGIC = 0x00000005
private const val MOUSE_MOVE_REL_MAGIC_GEN5 = 0x00000007
private const val SCROLL_MAGIC_GEN5 = 0x0000000A
private const val HSCROLL_MAGIC = 0x55000001
private const val MOUSE_BUTTON_DOWN_MAGIC_GEN5 = 0x00000008
private const val MOUSE_BUTTON_UP_MAGIC_GEN5 = 0x00000009
private const val KEY_DOWN_EVENT_MAGIC = 0x00000003
private const val KEY_UP_EVENT_MAGIC = 0x00000004
private const val UTF8_TEXT_EVENT_MAGIC = 0x00000017
private const val SS_TOUCH_MAGIC = 0x55000002
private const val LI_ROT_UNKNOWN = 0xFFFF
