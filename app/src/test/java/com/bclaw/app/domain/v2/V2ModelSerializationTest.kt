package com.bclaw.app.domain.v2

import com.bclaw.app.net.BclawJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V2ModelSerializationTest {
    @Test
    fun `legacy wsBaseUrl key decodes into hostApiBaseUrl`() {
        val raw = """
            {
              "id": "device-1",
              "displayName": "studio-mac",
              "wsBaseUrl": "http://127.0.0.1:8766",
              "token": "",
              "pairedAtEpochMs": 123
            }
        """.trimIndent()

        val device = BclawJson.decodeFromString<Device>(raw)

        assertEquals("http://127.0.0.1:8766", device.hostApiBaseUrl)
    }

    @Test
    fun `new device metadata encodes hostApiBaseUrl key`() {
        val device = Device(
            id = DeviceId("device-1"),
            displayName = "studio-mac",
            hostApiBaseUrl = "http://127.0.0.1:8766",
            token = "",
            pairedAtEpochMs = 123,
        )

        val raw = BclawJson.encodeToString(device)

        assertTrue(raw.contains("hostApiBaseUrl"))
        assertFalse(raw.contains("wsBaseUrl"))
    }
}
