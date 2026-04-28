package com.bclaw.app.data

import com.bclaw.app.domain.v2.BclawV2UrlParseResult
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Parses remote-desktop pairing URLs:
 *
 *   `bclaw2://<host>:<port>?tok=<token>`
 *
 * Unknown query keys are ignored for forward compatibility.
 */
object BclawV2UrlParser {
    fun parse(rawValue: String): BclawV2UrlParseResult {
        val raw = rawValue.trim()
        if (raw.isBlank()) return BclawV2UrlParseResult.Error.Blank

        val uri = try {
            URI(raw)
        } catch (_: URISyntaxException) {
            return BclawV2UrlParseResult.Error.Malformed
        }

        val scheme = uri.scheme?.lowercase()
            ?: return BclawV2UrlParseResult.Error.Malformed

        when (scheme) {
            "bclaw2" -> Unit
            "bclaw1" -> return BclawV2UrlParseResult.Error.LegacyV1
            else -> return BclawV2UrlParseResult.Error.WrongScheme(scheme)
        }

        val host = uri.host?.takeIf { it.isNotBlank() }
            ?: return BclawV2UrlParseResult.Error.MissingHost

        val port = uri.port
        if (port <= 0) return BclawV2UrlParseResult.Error.MissingPort

        val token = parseQuery(uri.rawQuery)["tok"]?.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return BclawV2UrlParseResult.Error.MissingToken

        return BclawV2UrlParseResult.Success(
            hostApiBaseUrl = "http://$host:$port",
            token = token,
        )
    }

    private fun parseQuery(rawQuery: String?): Map<String, List<String>> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split("&")
            .filter { it.isNotBlank() }
            .groupBy(
                keySelector = { segment -> decode(segment.substringBefore("=")) },
                valueTransform = { segment -> decode(segment.substringAfter("=", "")) },
            )
    }

    private fun decode(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8)
}
