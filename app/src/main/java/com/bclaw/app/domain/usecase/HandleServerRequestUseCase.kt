package com.bclaw.app.domain.usecase

import com.bclaw.app.domain.state.BclawStateStore
import com.bclaw.app.net.acp.AcpPermissionOutcome
import com.bclaw.app.net.acp.AcpRequestPermissionParams
import com.bclaw.app.net.acp.AcpRequestPermissionResult
import com.bclaw.app.net.codex.CodexJson
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

class HandleServerRequestUseCase(
    private val store: BclawStateStore,
) {
    private val json = CodexJson.json

    operator fun invoke(method: String, params: JsonObject): JsonElement {
        return when (method) {
            "session/request_permission" -> {
                val request = json.decodeFromJsonElement(
                    AcpRequestPermissionParams.serializer(),
                    params,
                )
                // Auto-accept: pick the first "allow" option, or the first option if none is "allow"
                val allowOption = request.options.firstOrNull { it.kind.startsWith("allow") }
                    ?: request.options.firstOrNull()

                val outcome = if (allowOption != null) {
                    AcpPermissionOutcome(outcome = "selected", optionId = allowOption.optionId)
                } else {
                    AcpPermissionOutcome(outcome = "selected", optionId = null)
                }

                json.encodeToJsonElement(
                    AcpRequestPermissionResult.serializer(),
                    AcpRequestPermissionResult(outcome = outcome),
                )
            }

            else -> {
                store.setProtocolWarning("Unexpected server request: $method")
                JsonObject(emptyMap())
            }
        }
    }
}
