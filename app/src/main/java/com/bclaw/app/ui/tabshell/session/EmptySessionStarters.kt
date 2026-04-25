package com.bclaw.app.ui.tabshell.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.bclaw.app.domain.v2.TabState
import com.bclaw.app.ui.theme.BclawTheme

/**
 * Empty session starters — renders in place of [MessageList] when a tab has no timeline
 * items. UX_V2 §2.4: hero + starter chips.
 *
 * v2.0 ships with static command starters (`/plan · /review · /explain`). The UX spec calls
 * for git-recents + agent-commands + user-pinned starters; all three need new bridge surfaces
 * and are deferred. Tapping a chip seeds the composer input via [onSeedComposer] so the user
 * can edit before sending — matches the `/` palette behavior in §2.8.
 */
@Composable
fun EmptySessionStarters(
    tab: TabState,
    onSeedComposer: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    val agentName = tab.agentId.value.lowercase()
    val cwd = tab.projectCwd.value.trimEnd('/')

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = sp.pageGutter,
                end = sp.pageGutter,
                top = sp.sp10,
                bottom = sp.sp4,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "●", style = type.meta, color = colors.accent)
            Spacer(Modifier.padding(start = 6.dp))
            Text(
                text = "${agentName.uppercase()} · READY",
                style = type.meta,
                color = colors.accent,
            )
        }
        Spacer(Modifier.height(sp.sp2))
        Text(
            text = "what's on",
            fontSize = 28.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = (-0.8).sp,
            color = colors.inkPrimary,
            style = type.hero,
        )
        Text(
            text = "the table today?",
            fontSize = 28.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = (-0.8).sp,
            color = colors.inkPrimary,
            style = type.hero,
        )
        Spacer(Modifier.height(sp.sp3))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "you're in ",
                style = type.body,
                color = colors.inkSecondary,
            )
            Text(
                text = cwd.ifBlank { "~" },
                style = type.mono,
                color = colors.inkPrimary,
            )
            Text(
                text = ". starters to get going:",
                style = type.body,
                color = colors.inkSecondary,
            )
        }
        Spacer(Modifier.height(sp.sp5))
        // First starter is the palette-open hint — matches the accent-first-card pattern
        // from A.06. Remaining starters are secondary surface.
        defaultStarters.forEachIndexed { i, (heading, sub, seed) ->
            StarterCard(
                heading = heading,
                sub = sub,
                accent = i == 0,
                onClick = { onSeedComposer(seed) },
            )
            Spacer(Modifier.height(sp.sp2))
        }
        Spacer(Modifier.height(sp.sp5))
        Text(
            text = "tip: type `/` to browse commands · `@` to invoke a skill.",
            style = type.bodySmall,
            color = colors.inkTertiary,
        )
    }
}

/**
 * Starters drive the composer prefill. v2.0 shipped raw `/` shortcuts; v2.1 dresses them
 * as titled cards with a hint subtitle. The first entry carries the accent per A.06 so a
 * glance shows what the session can do immediately. All three stay as pre-bridge static
 * starters — SPEC_V2 still tracks git-recents / agent-commands / user-pinned as follow-ups.
 */
private val defaultStarters = listOf(
    Triple("Tour this repo", "reads README + key files · /review .", "/review "),
    Triple("Make a plan", "breaks the next task into steps · /plan ", "/plan "),
    Triple("Explain a file", "walks key files · /explain <path>", "/explain "),
)

@Composable
private fun StarterCard(
    heading: String,
    sub: String,
    accent: Boolean,
    onClick: () -> Unit,
) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (accent) colors.accentSoft else colors.surfaceRaised)
            .border(
                width = if (accent) 1.5.dp else 1.dp,
                color = if (accent) colors.accent else colors.borderSubtle,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = sp.sp4, vertical = sp.sp3),
    ) {
        Text(
            text = heading,
            style = type.body.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp),
            color = colors.inkPrimary,
        )
        Spacer(Modifier.padding(top = 2.dp))
        Text(
            text = sub,
            style = type.monoSmall,
            color = colors.inkTertiary,
        )
    }
}
