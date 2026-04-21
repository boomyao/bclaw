package com.bclaw.app

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bclaw.app.testing.ForegroundServiceTeardownRule
import com.bclaw.app.testing.NotificationPermissionRule
import com.bclaw.app.testing.ThreadListFilteringFixture
import com.bclaw.app.testing.ThreadListFixture
import com.bclaw.app.testing.connectToServer
import com.bclaw.app.testing.openDrawer
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceFilteringE2eTest {
    private val foregroundServiceTeardown = ForegroundServiceTeardownRule()
    private val notificationPermissionRule = NotificationPermissionRule()
    private val composeRuleDelegate = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(foregroundServiceTeardown)
        .around(notificationPermissionRule)
        .around(composeRuleDelegate)

    private val composeRule
        get() = composeRuleDelegate

    private lateinit var server: MockWebServer
    private lateinit var fixture: ThreadListFilteringFixture

    @Before
    fun setUp() {
        fixture = ThreadListFilteringFixture(
            threads = listOf(
                ThreadListFixture(
                    id = "thr-alpha-1",
                    cwd = "/tmp/projects/alpha",
                    name = "Alpha thread one",
                    preview = "alpha preview 1",
                ),
                ThreadListFixture(
                    id = "thr-alpha-2",
                    cwd = "/tmp/projects/alpha",
                    name = "Alpha thread two",
                    preview = "alpha preview 2",
                ),
                ThreadListFixture(
                    id = "thr-beta-1",
                    cwd = "/tmp/projects/beta",
                    name = "Beta thread one",
                    preview = "beta preview 1",
                ),
            ),
        )
        server = MockWebServer()
        server.enqueue(MockResponse().withWebSocketUpgrade(fixture))
        server.start()
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
            .recoverCatching { server.close() }
    }

    @Test
    fun workspace_thread_list_is_filtered_by_cwd_and_requests_expected_source_kinds() {
        composeRule.connectToServer(
            host = server.url("/").toString().replaceFirst("http", "ws"),
            workspaceNamesAndCwds = listOf("Alpha Workspace" to "/tmp/projects/alpha"),
        )

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("chat_root").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.openDrawer()

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("thread_row_thr-alpha-1").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithTag("thread_row_thr-alpha-2").fetchSemanticsNodes().isNotEmpty()
        }

        assertEquals(2, drawerThreadCount())
        assertTrue(
            composeRule.onAllNodesWithTag("thread_row_thr-alpha-1").fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            composeRule.onAllNodesWithTag("thread_row_thr-alpha-2").fetchSemanticsNodes().isNotEmpty(),
        )
        assertFalse(
            composeRule.onAllNodesWithTag("thread_row_thr-beta-1").fetchSemanticsNodes().isNotEmpty(),
        )

        composeRule.waitUntil(10_000) {
            fixture.threadListRequests.isNotEmpty()
        }
        val params = fixture.threadListRequests.last()
        assertEquals("/tmp/projects/alpha", params.getString("cwd"))
        val sourceKinds = params.getJSONArray("sourceKinds").toStringSet()
        assertEquals(setOf("cli", "vscode", "appServer"), sourceKinds)
    }

    private fun drawerThreadCount(): Int {
        val knownThreadRows = hasTestTag("thread_row_thr-alpha-1")
            .or(hasTestTag("thread_row_thr-alpha-2"))
            .or(hasTestTag("thread_row_thr-beta-1"))
        return composeRule.onAllNodes(
            knownThreadRows,
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
            .size
    }
}

private fun org.json.JSONArray.toStringSet(): Set<String> {
    return buildSet {
        for (index in 0 until length()) {
            add(getString(index))
        }
    }
}
