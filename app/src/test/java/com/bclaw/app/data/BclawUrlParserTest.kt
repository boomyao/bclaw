package com.bclaw.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BclawUrlParserTest {
    @Test
    fun parses_valid_url_with_one_cwd() {
        val result = BclawUrlParser.parse(
            "bclaw1://100.101.102.103:8765?tok=abc123&cwd=%2FUsers%2Fyou%2Fprojects%2Ffoo",
        )

        val success = result as BclawUrlParseResult.Success
        assertEquals("ws://100.101.102.103:8765", success.config.host)
        assertEquals("abc123", success.config.token)
        assertEquals(1, success.config.workspaces.size)
        assertEquals("/Users/you/projects/foo", success.config.workspaces.single().cwd)
        assertEquals("foo", success.config.workspaces.single().displayName)
    }

    @Test
    fun parses_valid_url_with_multiple_cwds() {
        val result = BclawUrlParser.parse(
            "bclaw1://100.101.102.103:8765?tok=abc123" +
                "&cwd=%2FUsers%2Fyou%2Fprojects%2Ffoo" +
                "&cwd=%2FUsers%2Fyou%2Fprojects%2Fbar",
        )

        val success = result as BclawUrlParseResult.Success
        assertEquals(2, success.config.workspaces.size)
        assertEquals(
            listOf("/Users/you/projects/foo", "/Users/you/projects/bar"),
            success.config.workspaces.map { it.cwd },
        )
    }

    @Test
    fun parses_valid_url_with_zero_cwds() {
        val result = BclawUrlParser.parse(
            "bclaw1://100.101.102.103:8765?tok=abc123",
        )

        val success = result as BclawUrlParseResult.Success
        assertTrue(success.config.workspaces.isEmpty())
        assertEquals("ws://100.101.102.103:8765", success.config.host)
        assertEquals("abc123", success.config.token)
    }

    @Test
    fun rejects_plain_httpish_urls() {
        listOf("http://foo", "ws://foo", "https://foo", "wss://foo").forEach { raw ->
            val result = BclawUrlParser.parse(raw)
            assertTrue(result is BclawUrlParseResult.Error)
        }
    }

    @Test
    fun rejects_missing_scheme_version() {
        val result = BclawUrlParser.parse("bclaw://100.101.102.103:8765?tok=abc123")
        assertTrue(result is BclawUrlParseResult.Error)
    }

    @Test
    fun rejects_missing_tok() {
        val result = BclawUrlParser.parse(
            "bclaw1://100.101.102.103:8765?cwd=%2FUsers%2Fyou%2Fprojects%2Ffoo",
        )
        assertTrue(result is BclawUrlParseResult.Error)
    }

    @Test
    fun accepts_unknown_query_params() {
        val result = BclawUrlParser.parse(
            "bclaw1://100.101.102.103:8765?tok=abc123&cwd=%2Ftmp%2Falpha&future=1",
        )

        val success = result as BclawUrlParseResult.Success
        assertEquals("/tmp/alpha", success.config.workspaces.single().cwd)
    }

    @Test
    fun decodes_url_encoded_cwd() {
        val result = BclawUrlParser.parse(
            "bclaw1://100.101.102.103:8765?tok=abc123&cwd=%2FUsers%2Fyou%2Fprojects%2Ffoo%20bar",
        )

        val success = result as BclawUrlParseResult.Success
        assertEquals("/Users/you/projects/foo bar", success.config.workspaces.single().cwd)
        assertEquals("foo bar", success.config.workspaces.single().displayName)
    }
}
