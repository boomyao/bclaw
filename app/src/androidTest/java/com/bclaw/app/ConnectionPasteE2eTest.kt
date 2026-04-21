package com.bclaw.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bclaw.app.testing.ForegroundServiceTeardownRule
import com.bclaw.app.testing.NotificationPermissionRule
import com.bclaw.app.testing.ThreadListFilteringFixture
import com.bclaw.app.testing.ThreadListFixture
import com.bclaw.app.testing.openConnectionScreen
import com.bclaw.app.testing.openDrawer
import com.bclaw.app.testing.scrollOpenDrawerTo
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectionPasteE2eTest {
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

    @Before
    fun setUp() {
        server = MockWebServer()
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                ThreadListFilteringFixture(
                    threads = listOf(
                        ThreadListFixture(
                            id = "thr-alpha-1",
                            cwd = "/tmp/projects/alpha",
                            name = "Alpha thread one",
                            preview = "alpha preview 1",
                        ),
                    ),
                ),
            ),
        )
        server.start()
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
            .recoverCatching { server.close() }
    }

    @Test
    fun invalid_url_stays_inline_and_valid_clipboard_payload_connects_into_chat() {
        setClipboard("http://foo")

        composeRule.openConnectionScreen()
        composeRule.onNodeWithTag("connection_url_field").performTextInput("http://foo")
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("connection_parse_error").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("chat_root").fetchSemanticsNodes().isEmpty()
        }

        val validUrl = "bclaw1://${server.hostName}:${server.port}" +
            "?tok=test-token&cwd=%2Ftmp%2Fprojects%2Falpha"
        setClipboard(validUrl)

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("connection_paste_chip").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("connection_paste_chip").performClick()
        composeRule.onNodeWithTag("connect_button").performClick()

        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithTag("chat_root").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.openDrawer()
        composeRule.scrollOpenDrawerTo("workspace_row_workspace-0")
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("workspace_row_workspace-0").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("alpha").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun setClipboard(value: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("bclaw", value))
    }
}
