package com.bclaw.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bclaw.app.domain.model.TimelineItemUi
import com.bclaw.app.ui.components.CornerRadiusDpKey
import com.bclaw.app.ui.components.ElevationDpKey
import com.bclaw.app.ui.components.FontFamilyTokenKey
import com.bclaw.app.ui.components.MetroActionButton
import com.bclaw.app.ui.components.StatusDot
import com.bclaw.app.ui.components.TimelineItemCard
import com.bclaw.app.ui.connection.WelcomeScreen
import com.bclaw.app.ui.theme.BclawTheme
import com.bclaw.app.domain.model.ConnectionPhase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiInvariantsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun terminal_metro_visual_invariants_hold() {
        composeRule.setContent {
            BclawTheme {
                Column {
                    StatusDot(
                        connectionPhase = ConnectionPhase.Connected,
                        modifier = Modifier.testTag("status_dot_sample"),
                    )
                    MetroActionButton(
                        text = "SEND",
                        onClick = {},
                        modifier = Modifier.testTag("send_button"),
                    )
                    TimelineItemCard(
                        TimelineItemUi.CommandExecution(
                            id = "cmd",
                            turnId = "turn-1",
                            command = "ls",
                            cwd = "/tmp",
                            status = "completed",
                            output = "file.txt",
                            exitCode = 0,
                            durationMs = 1000,
                        ),
                    )
                    Box(modifier = Modifier.height(320.dp)) {
                        WelcomeScreen(onContinue = {})
                    }
                }
            }
        }
        composeRule.onNodeWithTag("status_dot_sample").assertWidthIsEqualTo(8.dp)
        composeRule.onNodeWithTag("status_dot_sample").assertHeightIsEqualTo(8.dp)
        assertEquals(
            0f,
            composeRule.onNodeWithTag("status_dot_sample").fetchSemanticsNode().config[CornerRadiusDpKey],
        )
        assertEquals(
            0f,
            composeRule.onNodeWithTag("send_button").fetchSemanticsNode().config[CornerRadiusDpKey],
        )
        assertEquals(
            0f,
            composeRule.onNodeWithTag("command_execution_card_cmd").fetchSemanticsNode().config[ElevationDpKey],
        )
        assertEquals(
            "sans-serif-light",
            composeRule.onNodeWithTag("welcome_hero").fetchSemanticsNode().config[FontFamilyTokenKey],
        )
    }
}
