package com.bclaw.app.data

import com.bclaw.app.domain.v2.AgentDescriptor
import com.bclaw.app.domain.v2.AgentId
import com.bclaw.app.domain.v2.BclawV2UrlParseResult
import com.bclaw.app.domain.v2.CwdPath
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Parses bclaw v2 pairing URLs of the form
 *   `bclaw2://<host>:<port>?tok=<hex>&agent=<id>*&cwd=<abs-path>*[&...]`
 *
 * Spec: SPEC_V2.md §3. Errors are granular (see [BclawV2UrlParseResult.Error]) so the
 * Pair screen can surface a specific inline reason rather than a generic "bad url".
 *
 * Inherits v0's intent: one URL, one paste/scan, one source of truth for pairing.
 * Repeated query keys (`agent`, `cwd`) are first-class — `java.net.URI.getQuery`
 * collapses duplicates by default, so we hand-roll the query decoder.
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

        val params = parseQuery(uri.rawQuery)

        val token = params["tok"]?.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return BclawV2UrlParseResult.Error.MissingToken

        val agents = params["agent"].orEmpty()
            .filter { it.isNotBlank() }
            .map { id ->
                AgentDescriptor(
                    id = AgentId(id),
                    displayName = id,
                )
            }

        val projects = params["cwd"].orEmpty()
            .filter { it.isNotBlank() }
            .map { CwdPath(it) }

        return BclawV2UrlParseResult.Success(
            wsBaseUrl = "ws://$host:$port",
            token = token,
            agents = agents,
            projects = projects,
        )
    }

    /**
     * Hand-rolled query parser that preserves repeated keys, URL-decoding each value.
     * Unknown keys are passed through — no rejection, per SPEC_V2 §3 forward-compat clause.
     */
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
