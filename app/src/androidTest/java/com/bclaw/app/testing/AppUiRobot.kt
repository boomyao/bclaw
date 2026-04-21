package com.bclaw.app.testing

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.bclaw.app.BclawApplication
import com.bclaw.app.data.ConnectionConfig
import com.bclaw.app.data.WorkspaceConfig
import com.bclaw.app.service.BclawRuntime
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import kotlinx.coroutines.runBlocking

fun AndroidComposeTestRule<*, *>.openConnectionScreen() {
    waitUntil(10_000) {
        onAllNodesWithTag("connection_url_field").fetchSemanticsNodes().isNotEmpty() ||
            onAllNodesWithTag("welcome_screen").fetchSemanticsNodes().isNotEmpty() ||
            onAllNodesWithTag("chat_root").fetchSemanticsNodes().isNotEmpty()
    }
    when {
        onAllNodesWithTag("connection_url_field").fetchSemanticsNodes().isNotEmpty() -> return
        onAllNodesWithTag("welcome_screen").fetchSemanticsNodes().isNotEmpty() -> {
            onNodeWithTag("welcome_continue").performClick()
        }
        else -> {
            openDrawer()
            onNodeWithTag("drawer_settings").performClick()
        }
    }
    waitUntil(10_000) {
        onAllNodesWithTag("connection_url_field").fetchSemanticsNodes().isNotEmpty()
    }
}

fun AndroidComposeTestRule<*, *>.connectToServer(
    host: String,
    token: String = "test-token",
    workspaceNamesAndCwds: List<Pair<String, String>>,
) {
    val application = ApplicationProvider.getApplicationContext<BclawApplication>()
    val config = ConnectionConfig(
        host = host,
        token = token,
        lastOpenedWorkspaceId = workspaceNamesAndCwds.firstOrNull()?.let { "workspace-0" },
        workspaces = workspaceNamesAndCwds.mapIndexed { index, (name, cwd) ->
            WorkspaceConfig(
                id = "workspace-$index",
                displayName = name,
                cwd = cwd,
            )
        },
    )
    waitUntil(10_000) {
        BclawRuntime.controller.value != null
    }
    runBlocking {
        application.configRepository.saveConfig(ConnectionConfig())
    }
    BclawRuntime.controller.value?.saveConfigAndConnect(config)
}

@OptIn(ExperimentalTestApi::class)
fun AndroidComposeTestRule<*, *>.openDrawer() {
    waitUntil(10_000) {
        onAllNodesWithTag("drawer_button").fetchSemanticsNodes().isNotEmpty()
    }
    onNodeWithTag("drawer_button").performClick()
    waitUntil(10_000) {
        onAllNodesWithTag("drawer_panel_open").fetchSemanticsNodes().isNotEmpty()
    }
}

@OptIn(ExperimentalTestApi::class)
fun AndroidComposeTestRule<*, *>.selectWorkspace(workspaceId: String) {
    openDrawer()
    scrollOpenDrawerTo("workspace_row_$workspaceId")
    waitUntil(10_000) {
        onAllNodesWithTag("workspace_row_$workspaceId").fetchSemanticsNodes().isNotEmpty()
    }
    onNodeWithTag("workspace_row_$workspaceId").performClick()
    waitUntil(10_000) {
        onAllNodesWithTag("drawer_panel_open").fetchSemanticsNodes().isEmpty()
    }
}

@OptIn(ExperimentalTestApi::class)
fun AndroidComposeTestRule<*, *>.selectThread(threadId: String) {
    openDrawer()
    scrollOpenDrawerTo("thread_row_$threadId")
    waitUntil(10_000) {
        onAllNodesWithTag("thread_row_$threadId").fetchSemanticsNodes().isNotEmpty()
    }
    onNodeWithTag("thread_row_$threadId").performClick()
    waitUntil(10_000) {
        onAllNodesWithTag("drawer_panel_open").fetchSemanticsNodes().isEmpty()
    }
}

@OptIn(ExperimentalTestApi::class)
fun AndroidComposeTestRule<*, *>.createThreadInWorkspace(workspaceId: String) {
    openDrawer()
    scrollOpenDrawerTo("new_thread_$workspaceId")
    waitUntil(10_000) {
        onAllNodesWithTag("new_thread_$workspaceId").fetchSemanticsNodes().isNotEmpty()
    }
    onNodeWithTag("new_thread_$workspaceId").performClick()
    waitUntil(10_000) {
        onAllNodesWithTag("drawer_panel_open").fetchSemanticsNodes().isEmpty()
    }
}

@OptIn(ExperimentalTestApi::class)
fun AndroidComposeTestRule<*, *>.scrollOpenDrawerTo(tag: String) {
    onNodeWithTag("drawer_panel_open").performScrollToNode(hasTestTag(tag))
}

@OptIn(ExperimentalTestApi::class)
fun AndroidComposeTestRule<*, *>.taggedNodeHasText(tag: String, expected: String): Boolean {
    return onAllNodesWithTag(tag, useUnmergedTree = true)
        .fetchSemanticsNodes()
        .any { node ->
            runCatching {
                node.config[androidx.compose.ui.semantics.SemanticsProperties.Text]
            }.getOrDefault(emptyList())
                .any { text -> text.text == expected }
        }
}

@OptIn(ExperimentalTestApi::class)
fun AndroidComposeTestRule<*, *>.taggedNodeContainsText(tag: String, expectedSubstring: String): Boolean {
    return onAllNodesWithTag(tag, useUnmergedTree = true)
        .fetchSemanticsNodes()
        .any { node ->
            runCatching {
                node.config[androidx.compose.ui.semantics.SemanticsProperties.Text]
            }.getOrDefault(emptyList())
                .any { text -> text.text.contains(expectedSubstring) }
        }
}

@OptIn(ExperimentalTestApi::class)
fun AndroidComposeTestRule<*, *>.containsTextAnywhere(expectedSubstring: String): Boolean {
    return onAllNodesWithText(
        text = expectedSubstring,
        substring = true,
        useUnmergedTree = true,
    ).fetchSemanticsNodes().isNotEmpty()
}

@OptIn(ExperimentalTestApi::class)
fun AndroidComposeTestRule<*, *>.countNodesContainingText(expectedSubstring: String): Int {
    return onAllNodesWithText(
        text = expectedSubstring,
        substring = true,
        useUnmergedTree = true,
    ).fetchSemanticsNodes().size
}

fun allowNotificationsIfPrompted(timeoutMs: Long = 5_000): Boolean {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val selectors = listOf(
        By.res("com.android.permissioncontroller", "permission_allow_button"),
        By.res("com.android.packageinstaller", "permission_allow_button"),
        By.text(Pattern.compile("(?i)allow|允许")),
    )
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        val button = selectors.firstNotNullOfOrNull { selector ->
            device.findObject(selector)
        }
        if (button != null) {
            button.click()
            device.waitForIdle()
            device.wait(Until.gone(By.pkg("com.android.permissioncontroller")), 2_000)
            return true
        }
        device.waitForIdle(200)
        Thread.sleep(100)
    }
    return false
}
