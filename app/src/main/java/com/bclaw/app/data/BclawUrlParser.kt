package com.bclaw.app.data

import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

sealed class BclawUrlParseResult {
    data class Success(val config: ConnectionConfig) : BclawUrlParseResult()
    data class Error(val reason: String) : BclawUrlParseResult()
}

object BclawUrlParser {
    fun parse(rawValue: String): BclawUrlParseResult {
        val raw = rawValue.trim()
        if (raw.isBlank()) {
            return BclawUrlParseResult.Error("expected bclaw1://…")
        }

        val uri = runCatching { URI(raw) }
            .getOrElse {
                return BclawUrlParseResult.Error("invalid connection string")
            }

        if (uri.scheme != "bclaw1") {
            return BclawUrlParseResult.Error("expected bclaw1://…")
        }

        val host = uri.host?.takeIf { it.isNotBlank() }
            ?: return BclawUrlParseResult.Error("missing host")
        val port = uri.port
        if (port <= 0) {
            return BclawUrlParseResult.Error("missing port")
        }

        val queryParams = parseQuery(uri.rawQuery)
        val token = queryParams["tok"]?.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return BclawUrlParseResult.Error("missing tok query parameter")

        return BclawUrlParseResult.Success(
            ConnectionConfig(
                host = "ws://$host:$port",
                token = token,
            ),
        )
    }

    private fun parseQuery(rawQuery: String?): Map<String, List<String>> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split("&")
            .filter { it.isNotBlank() }
            .groupBy(
                keySelector = { segment ->
                    decodeQueryComponent(segment.substringBefore("="))
                },
                valueTransform = { segment ->
                    decodeQueryComponent(segment.substringAfter("=", ""))
                },
            )
    }

    private fun decodeQueryComponent(value: String): String {
        return URLDecoder.decode(value, StandardCharsets.UTF_8)
    }
}
