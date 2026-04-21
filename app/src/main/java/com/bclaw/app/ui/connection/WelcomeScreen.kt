package com.bclaw.app.ui.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import com.bclaw.app.ui.components.MetroActionButton
import com.bclaw.app.ui.components.bclawFontFamilyToken
import com.bclaw.app.ui.theme.BclawSpacing
import com.bclaw.app.ui.theme.LocalBclawColors
import com.bclaw.app.ui.theme.LocalBclawTypography

@Composable
fun WelcomeScreen(
    onContinue: () -> Unit,
) {
    val colors = LocalBclawColors.current
    val typography = LocalBclawTypography.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.terminalBlack)
            .testTag("welcome_screen"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = BclawSpacing.EdgeLeft,
                    end = BclawSpacing.EdgeRight,
                    top = maxHeight * 0.3f,
                ),
        ) {
            Text(
                text = "Connect to your Mac",
                modifier = Modifier
                    .semantics { bclawFontFamilyToken = "sans-serif-light" }
                    .testTag("welcome_hero"),
                style = typography.hero,
                color = colors.textPrimary,
                textAlign = TextAlign.Start,
            )
            Text(
                text = "Paste your bclaw connection details and jump back into the conversation.",
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .padding(top = 16.dp, bottom = 24.dp),
                style = typography.body,
                color = colors.textMeta,
            )
            MetroActionButton(
                text = "CONTINUE",
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("welcome_continue"),
            )
        }
    }
}
