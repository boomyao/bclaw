package com.bclaw.app.net

import kotlinx.serialization.json.Json

/** Shared kotlinx.serialization JSON config for persisted bclaw data. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
val BclawJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    encodeDefaults = true
}
