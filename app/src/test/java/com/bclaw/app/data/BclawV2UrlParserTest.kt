package com.bclaw.app.data

import com.bclaw.app.domain.v2.BclawV2UrlParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BclawV2UrlParserTest {

    @Test fun `blank input returns Blank`() {
        assertEquals(BclawV2UrlParseResult.Error.Blank, BclawV2UrlParser.parse(""))
        assertEquals(BclawV2UrlParseResult.Error.Blank, BclawV2UrlParser.parse("   "))
    }

    @Test fun `random garbage returns Malformed`() {
        val result = BclawV2UrlParser.parse("not a url at all")
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
        val result = BclawV2UrlParser.parse("bclaw1://1.2.3.4:8765?tok=abc")
        assertEquals(BclawV2UrlParseResult.Error.LegacyV1, result)
    }

    @Test fun `missing host returns MissingHost`() {
        val result = BclawV2UrlParser.parse("bclaw2:///path?tok=abc")
        assertEquals(BclawV2UrlParseResult.Error.MissingHost, result)
    }

    @Test fun `missing port returns MissingPort`() {
        val result = BclawV2UrlParser.parse("bclaw2://1.2.3.4?tok=abc")
        assertEquals(BclawV2UrlParseResult.Error.MissingPort, result)
    }

    @Test fun `missing tok query returns MissingToken`() {
        val result = BclawV2UrlParser.parse("bclaw2://1.2.3.4:8766")
        assertEquals(BclawV2UrlParseResult.Error.MissingToken, result)
    }

    @Test fun `blank tok value returns MissingToken`() {
        val result = BclawV2UrlParser.parse("bclaw2://1.2.3.4:8766?tok=")
        assertEquals(BclawV2UrlParseResult.Error.MissingToken, result)
    }

    @Test fun `minimal valid url`() {
        val result = BclawV2UrlParser.parse("bclaw2://1.2.3.4:8766?tok=deadbeef")
        val success = assertSuccess(result)
        assertEquals("http://1.2.3.4:8766", success.hostApiBaseUrl)
        assertEquals("deadbeef", success.token)
    }

    @Test fun `unknown query params do not reject`() {
        val result = BclawV2UrlParser.parse(
            "bclaw2://host:8766?tok=t&futureKey=somevalue&unused=%2Fignored",
        )
        val success = assertSuccess(result)
        assertEquals("http://host:8766", success.hostApiBaseUrl)
        assertEquals("t", success.token)
    }

    @Test fun `ipv4 and port carried through verbatim`() {
        val result = BclawV2UrlParser.parse("bclaw2://10.0.0.1:9090?tok=t")
        val success = assertSuccess(result)
        assertEquals("http://10.0.0.1:9090", success.hostApiBaseUrl)
    }

    @Test fun `leading and trailing whitespace tolerated`() {
        val result = BclawV2UrlParser.parse("  bclaw2://host:8766?tok=abc  ")
        assertSuccess(result)
    }

    private fun assertSuccess(result: BclawV2UrlParseResult): BclawV2UrlParseResult.Success {
        if (result !is BclawV2UrlParseResult.Success) {
            fail("expected Success, got $result")
            error("unreachable")
        }
        return result
    }
}
