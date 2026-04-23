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
    val project = tab.projectCwd.value.trimEnd('/').substringAfterLast('/')
        .ifBlank { "this workspace" }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = sp.edgeLeft, vertical = sp.sp6),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "say something to $agentName",
            style = type.hero,
            color = colors.inkPrimary,
        )
        Text(
            text = "in $project.",
            style = type.hero,
            color = colors.inkSecondary,
        )
        Spacer(Modifier.height(sp.sp8))

        Text(
            text = "STARTERS",
            style = type.meta,
            color = colors.inkTertiary,
        )
        Spacer(Modifier.height(sp.sp3))

        Column(verticalArrangement = Arrangement.spacedBy(sp.sp2)) {
            defaultStarters.forEach { starter ->
                StarterChip(label = starter, onClick = { onSeedComposer(starter) })
            }
        }

        Spacer(Modifier.height(sp.sp6))

        Text(
            text = "tip: type `/` to browse commands · `@` to invoke a skill.",
            style = type.bodySmall,
            color = colors.inkTertiary,
        )
    }
}

private val defaultStarters = listOf(
    "/plan ",
    "/review ",
    "/explain ",
)

@Composable
private fun StarterChip(label: String, onClick: () -> Unit) {
    val colors = BclawTheme.colors
    val type = BclawTheme.typography
    val sp = BclawTheme.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceRaised)
            .border(1.dp, colors.borderSubtle)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = sp.sp3, vertical = sp.sp3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .height(20.dp)
                .background(colors.accent)
                .padding(horizontal = 1.dp),
        ) {
            Spacer(Modifier.padding(horizontal = 1.dp))
        }
        Spacer(Modifier.padding(start = sp.sp2))
        Text(
            text = label.trim(),
            style = type.mono,
            color = colors.inkPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "↵",
            style = type.bodySmall,
            color = colors.inkTertiary,
        )
    }
}
