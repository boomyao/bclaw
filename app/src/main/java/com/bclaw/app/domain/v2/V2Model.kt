package com.bclaw.app.domain.v2

import java.util.UUID
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * Remote-desktop-first domain model.
 *
 * The paired host is now the primary object. Chat session and tab state were part of the
 * older session-first product direction and intentionally do not live in this model.
 */

@JvmInline
@Serializable
value class DeviceId(val value: String) {
    companion object {
        fun generate(): DeviceId = DeviceId(UUID.randomUUID().toString())
    }
}

/**
 * A Mac or Linux host running the bclaw host agent.
 */
@Serializable
data class Device(
    val id: DeviceId,
    val displayName: String,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("wsBaseUrl")
    val hostApiBaseUrl: String,
    val token: String,
    val pairedAtEpochMs: Long,
)

/**
 * The user's paired-device book. Tokens are stripped before DataStore persistence and stored
 * separately by [com.bclaw.app.data.DeviceBookRepository].
 */
@Serializable
data class DeviceBook(
    val devices: List<Device> = emptyList(),
    val activeDeviceId: DeviceId? = null,
) {
    val activeDevice: Device?
        get() = devices.firstOrNull { it.id == activeDeviceId }
}

/**
 * Result of parsing a `bclaw2://` pairing URL.
 */
sealed class BclawV2UrlParseResult {
    data class Success(
        val hostApiBaseUrl: String,
        val token: String,
    ) : BclawV2UrlParseResult()

    sealed class Error(open val reason: String) : BclawV2UrlParseResult() {
        data object Blank : Error("enter a bclaw2://… url")
        data object Malformed : Error("not a valid url")
        data class WrongScheme(val scheme: String) : Error(
            "expected bclaw2://…, got $scheme://",
        )
        data object LegacyV1 : Error("bclaw1:// is from the old paste flow. re-run bclaw-handoff on your host.")
        data object MissingHost : Error("missing host")
        data object MissingPort : Error("missing port")
        data object MissingToken : Error("missing tok=… query parameter")
    }
}
