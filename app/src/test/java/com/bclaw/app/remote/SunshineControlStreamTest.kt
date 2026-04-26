package com.bclaw.app.remote

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SunshineControlStreamTest {
    @Test
    fun rightMouseButtonUsesSunshineRightButtonId() {
        val packet = buildMouseButtonPacket(
            pressed = true,
            button = SunshineMouseButton.RIGHT,
        )

        assertEquals(9, packet.size)
        assertEquals(5, packet.readBe32(0))
        assertEquals(0x00000008, packet.readLe32(4))
        assertEquals(0x03, packet[8].toInt() and 0xff)
    }

    @Test
    fun touchPacketEncodesSunshineTouchExtension() {
        val packet = buildTouchPacket(
            eventType = SunshineTouchEvent.DOWN,
            pointerId = 7,
            x = 0.25f,
            y = 0.75f,
            pressureOrDistance = 0.5f,
            contactAreaMajor = 0.02f,
            contactAreaMinor = 0.03f,
        )

        assertEquals(36, packet.size)
        assertEquals(32, packet.readBe32(0))
        assertEquals(0x55000002, packet.readLe32(4))
        assertEquals(SunshineTouchEvent.DOWN, packet[8].toInt() and 0xff)
        assertEquals(7, packet.readLe32(12))
        assertEquals(0.25f, packet.readLeFloat(16), 0.0001f)
        assertEquals(0.75f, packet.readLeFloat(20), 0.0001f)
        assertEquals(0.5f, packet.readLeFloat(24), 0.0001f)
        assertEquals(0.02f, packet.readLeFloat(28), 0.0001f)
        assertEquals(0.03f, packet.readLeFloat(32), 0.0001f)
    }

    @Test
    fun utf8TextPacketEncodesMoonlightTextEvent() {
        val textBytes = "你".toByteArray(Charsets.UTF_8)
        val packet = buildUtf8TextPacket(textBytes)

        assertEquals(8 + textBytes.size, packet.size)
        assertEquals(4 + textBytes.size, packet.readBe32(0))
        assertEquals(0x00000017, packet.readLe32(4))
        assertArrayEquals(textBytes, packet.copyOfRange(8, packet.size))
    }

    @Test
    fun modifierMaskExpandsToExplicitModifierKeyCodes() {
        assertEquals(
            listOf(
                SunshineKey.LEFT_SHIFT,
                SunshineKey.LEFT_CONTROL,
                SunshineKey.LEFT_ALT,
                SunshineKey.LEFT_META,
            ),
            sunshineModifierKeyCodes(
                SunshineModifier.SHIFT or
                    SunshineModifier.CTRL or
                    SunshineModifier.ALT or
                    SunshineModifier.META,
            ),
        )
    }

    @Test
    fun sdpAttributeUIntParsesDecimalAndHexFeatureFlags() {
        assertEquals(
            3,
            parseSdpAttributeUInt(
                "v=0\r\na=x-ss-general.featureFlags:3 \r\n",
                "x-ss-general.featureFlags",
            ),
        )
        assertEquals(
            3,
            parseSdpAttributeUInt(
                "a=x-ss-general.featureFlags:0x3 \r\n",
                "x-ss-general.featureFlags",
            ),
        )
        assertNull(parseSdpAttributeUInt("v=0\r\n", "x-ss-general.featureFlags"))
    }

    private fun ByteArray.readBe32(offset: Int): Int =
        ((this[offset].toInt() and 0xff) shl 24) or
            ((this[offset + 1].toInt() and 0xff) shl 16) or
            ((this[offset + 2].toInt() and 0xff) shl 8) or
            (this[offset + 3].toInt() and 0xff)

    private fun ByteArray.readLe32(offset: Int): Int =
        (this[offset].toInt() and 0xff) or
            ((this[offset + 1].toInt() and 0xff) shl 8) or
            ((this[offset + 2].toInt() and 0xff) shl 16) or
            ((this[offset + 3].toInt() and 0xff) shl 24)

    private fun ByteArray.readLeFloat(offset: Int): Float =
        Float.fromBits(readLe32(offset))
}
