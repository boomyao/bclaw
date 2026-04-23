package com.bclaw.app.ui.tabshell.session

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.bclaw.app.domain.v2.TimelineItem
import com.bclaw.app.ui.theme.BclawTheme
import com.bclaw.app.ui.theme.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MessageListTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun hydrateFromEmptySnapsToBottom() {
        var items by mutableStateOf<List<TimelineItem>>(emptyList())
        lateinit var listState: LazyListState

        composeRule.setContent {
            BclawTheme(mode = ThemeMode.Light) {
                listState = rememberLazyListState()
                Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                    MessageList(
                        sessionKey = "session-a",
                        items = items,
                        listState = listState,
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            items = messageHistory(count = 48, prefix = "session-a")
        }

        waitUntilLastRowVisible(listState)

        composeRule.runOnIdle {
            assertTrue(
                "expected hydrated history to move away from the top of the list",
                listState.firstVisibleItemIndex > 0,
            )
        }
    }

    @Test
    fun newRowsFollowOnlyWhileUserStaysNearBottom() {
        var items by mutableStateOf(messageHistory(count = 48, prefix = "session-a"))
        lateinit var listState: LazyListState

        composeRule.setContent {
            BclawTheme(mode = ThemeMode.Light) {
                listState = rememberLazyListState()
                Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                    MessageList(
                        sessionKey = "session-a",
                        items = items,
                        listState = listState,
                    )
                }
            }
        }
        composeRule.waitForIdle()

        waitUntilLastRowVisible(listState)

        composeRule.runOnIdle {
            items = items + messageHistory(count = 1, prefix = "session-a-tail", startAt = items.size)
        }
        waitUntilLastRowVisible(listState)

        composeRule.runOnIdle {
            runBlocking { listState.scrollToItem(0) }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            listState.firstVisibleItemIndex == 0
        }

        composeRule.runOnIdle {
            items = items + messageHistory(count = 1, prefix = "session-a-top", startAt = items.size)
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            val info = listState.layoutInfo
            assertEquals(0, listState.firstVisibleItemIndex)
            assertTrue(
                "expected manual upward scroll to disable auto-follow",
                info.visibleItemsInfo.lastOrNull()!!.index < info.totalItemsCount - 1,
            )
        }
    }

    @Test
    fun switchingSessionsGetsFreshListStateAndReprimesBottomSnap() {
        var sessionKey by mutableStateOf("session-a")
        var items by mutableStateOf(messageHistory(count = 48, prefix = "session-a"))
        lateinit var listState: LazyListState

        composeRule.setContent {
            BclawTheme(mode = ThemeMode.Light) {
                listState = rememberSaveable(sessionKey, saver = LazyListState.Saver) {
                    LazyListState()
                }
                Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                    MessageList(
                        sessionKey = sessionKey,
                        items = items,
                        listState = listState,
                    )
                }
            }
        }
        composeRule.waitForIdle()

        waitUntilLastRowVisible(listState)

        composeRule.runOnIdle {
            runBlocking { listState.scrollToItem(0) }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            listState.firstVisibleItemIndex == 0
        }

        composeRule.runOnIdle {
            sessionKey = "session-b"
            items = messageHistory(count = 48, prefix = "session-b")
        }

        waitUntilLastRowVisible(listState)

        composeRule.runOnIdle {
            assertTrue(
                "expected a newly-opened session to reprime and snap to the tail",
                listState.firstVisibleItemIndex > 0,
            )
        }
    }

    private fun waitUntilLastRowVisible(listState: LazyListState) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            val info = listState.layoutInfo
            info.totalItemsCount > 0 &&
                info.visibleItemsInfo.lastOrNull()?.index == info.totalItemsCount - 1
        }
    }

    private fun messageHistory(
        count: Int,
        prefix: String,
        startAt: Int = 0,
    ): List<TimelineItem> = List(count) { offset ->
        val index = startAt + offset
        if (index % 2 == 0) {
            TimelineItem.UserMessage(
                id = "$prefix-user-$index",
                createdAtEpochMs = index.toLong(),
                text = "user message $index",
            )
        } else {
            TimelineItem.AgentMessage(
                id = "$prefix-agent-$index",
                createdAtEpochMs = index.toLong(),
                text = "agent message $index",
            )
        }
    }
}
