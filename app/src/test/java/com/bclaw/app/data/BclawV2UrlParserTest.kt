package com.bclaw.app.data

import com.bclaw.app.domain.v2.AgentId
import com.bclaw.app.domain.v2.BclawV2UrlParseResult
import com.bclaw.app.domain.v2.CwdPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Contract tests for [BclawV2UrlParser] per SPEC_V2.md §3.
 *
 * Organized by result variant: error cases first (one per [BclawV2UrlParseResult.Error] subtype),
 * then success cases (varying agent + cwd cardinality + URL-encoding).
 */
class BclawV2UrlParserTest {

    // ── Error cases ─────────────────────────────────────────────

    @Test fun `blank input returns Blank`() {
        assertEquals(BclawV2UrlParseResult.Error.Blank, BclawV2UrlParser.parse(""))
        assertEquals(BclawV2UrlParseResult.Error.Blank, BclawV2UrlParser.parse("   "))
    }

    @Test fun `random garbage returns Malformed`() {
        val result = BclawV2UrlParser.parse("not a url at all")
        // "not a url at all" → no scheme → Malformed
        assertTrue("expected Malformed, got $result", result is BclawV2UrlParseResult.Error.Malformed)
    }

    @Test fun `http scheme returns WrongScheme`() {
        val result = BclawV2UrlParser.parse("http://1.2.3.4:8766/?tok=abc")
        assertEquals(
            BclawV2UrlParseResult.Error.WrongScheme("http"),
            result,
        )
    }

    @Test fun `ws scheme returns WrongScheme`() {
        val result = BclawV2UrlParser.parse("ws://1.2.3.4:8766/?tok=abc")
        assertEquals(
            BclawV2UrlParseResult.Error.WrongScheme("ws"),
            result,
        )
    }

    @Test fun `bclaw1 scheme returns LegacyV1`() {
        val result = BclawV2UrlParser.parse("bclaw1://1.2.3.4:8765?tok=abc&cwd=%2Ftmp")
        assertEquals(BclawV2UrlParseResult.Error.LegacyV1, result)
    }

    @Test fun `missing host returns MissingHost`() {
        // `bclaw2:///?tok=abc` parses with empty host
        val result = BclawV2UrlParser.parse("bclaw2:///path?tok=abc")
        assertEquals(BclawV2UrlParseResult.Error.MissingHost, result)
    }

    @Test fun `missing port returns MissingPort`() {
        val result = BclawV2UrlParser.parse("bclaw2://1.2.3.4?tok=abc")
        assertEquals(BclawV2UrlParseResult.Error.MissingPort, result)
    }

    @Test fun `missing tok query returns MissingToken`() {
        val result = BclawV2UrlParser.parse("bclaw2://1.2.3.4:8766?agent=codex")
        assertEquals(BclawV2UrlParseResult.Error.MissingToken, result)
    }

    @Test fun `blank tok value returns MissingToken`() {
        val result = BclawV2UrlParser.parse("bclaw2://1.2.3.4:8766?tok=")
        assertEquals(BclawV2UrlParseResult.Error.MissingToken, result)
    }

    // ── Success cases ───────────────────────────────────────────

    @Test fun `minimal valid url with zero agents and zero cwds`() {
        val result = BclawV2UrlParser.parse("bclaw2://1.2.3.4:8766?tok=deadbeef")
        val success = assertSuccess(result)
        assertEquals("ws://1.2.3.4:8766", success.wsBaseUrl)
        assertEquals("deadbeef", success.token)
        assertTrue(success.agents.isEmpty())
        assertTrue(success.projects.isEmpty())
    }

    @Test fun `url with single agent and single cwd`() {
        val result = BclawV2UrlParser.parse(
            "bclaw2://100.64.1.2:8766?tok=abc123&agent=codex&cwd=%2FUsers%2Fyou%2Fprojects%2Ffoo"
        )
        val success = assertSuccess(result)
        assertEquals("ws://100.64.1.2:8766", success.wsBaseUrl)
        assertEquals("abc123", success.token)
        assertEquals(listOf(AgentId("codex")), success.agents.map { it.id })
        assertEquals("codex", success.agents.single().displayName)
        assertEquals(listOf(CwdPath("/Users/you/projects/foo")), success.projects)
    }

    @Test fun `url with three agents and two cwds preserves order`() {
        val result = BclawV2UrlParser.parse(
            "bclaw2://host:8766?tok=t&agent=codex&agent=claude&agent=gemini" +
                    "&cwd=%2Fa&cwd=%2Fb"
        )
        val success = assertSuccess(result)
        assertEquals(
            listOf(AgentId("codex"), AgentId("claude"), AgentId("gemini")),
            success.agents.map { it.id },
        )
        assertEquals(
            listOf(CwdPath("/a"), CwdPath("/b")),
            success.projects,
        )
    }

    @Test fun `unknown query params do not reject`() {
        val result = BclawV2UrlParser.parse(
            "bclaw2://host:8766?tok=t&futureKey=somevalue&agent=codex"
        )
        val success = assertSuccess(result)
        assertEquals(listOf(AgentId("codex")), success.agents.map { it.id })
    }

    @Test fun `ipv4 and port carried through verbatim`() {
        val result = BclawV2UrlParser.parse("bclaw2://10.0.0.1:9090?tok=t")
        val success = assertSuccess(result)
        assertEquals("ws://10.0.0.1:9090", success.wsBaseUrl)
    }

    @Test fun `leading and trailing whitespace tolerated`() {
        val result = BclawV2UrlParser.parse("  bclaw2://host:8766?tok=abc  ")
        assertSuccess(result)
    }

    @Test fun `blank agent and cwd values skipped`() {
        val result = BclawV2UrlParser.parse("bclaw2://host:8766?tok=t&agent=&cwd=")
        val success = assertSuccess(result)
        assertTrue(success.agents.isEmpty())
        assertTrue(success.projects.isEmpty())
    }

    // ── Helper ──────────────────────────────────────────────────

    private fun assertSuccess(result: BclawV2UrlParseResult): BclawV2UrlParseResult.Success {
        if (result !is BclawV2UrlParseResult.Success) {
            fail("expected Success, got $result")
            error("unreachable")
        }
        return result
    }
}
