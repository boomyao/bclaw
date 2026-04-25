package com.bclaw.app.ui.showcase.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bclaw.app.ui.showcase.DefaultShowTabs
import com.bclaw.app.ui.showcase.ShowComposer
import com.bclaw.app.ui.showcase.ShowCrumb
import com.bclaw.app.ui.showcase.ShowGestureBar
import com.bclaw.app.ui.showcase.ShowStatusBar
import com.bclaw.app.ui.showcase.ShowTabStrip
import com.bclaw.app.ui.theme.BclawTheme

/**
 * C · Session-rich demo screens — stitch the [MessagePrimitives] into the 4 JSX demo
 * layouts from `bclaw v2.1.html:192-303`. Scrollable so the long C.01 transcript fits.
 */

@Composable
private fun ColumnScope.SessionRichShell(
    showComposer: Boolean = true,
    activeTab: String = "fix-login",
    content: @Composable ColumnScope.() -> Unit,
) {
    ShowStatusBar()
    ShowTabStrip(tabs = DefaultShowTabs, activeId = activeTab)
    ShowCrumb(
        device = "ember · mac",
        project = "foo-api",
        session = "fix-login · src/auth/session.ts",
        running = true,
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .background(BclawTheme.colors.surfaceBase)
            .verticalScroll(rememberScrollState()),
    ) {
        content()
    }
    if (showComposer) ShowComposer()
    ShowGestureBar()
}

/**
 * C.01 — long session: every message type in sequence. This is the "visual rhythm"
 * demo showing user→reasoning→plan→diff→approval→command→agent all in one scroll.
 */
@Composable
fun ColumnScope.ShowSessionAllTypes() {
    SessionRichShell {
        ShowUserMsg(
            text = "add a test for the null-token case and fix whatever breaks.",
            time = "01:03",
        )
        ShowReasoning(summary = "reading test file · deciding approach")
        ShowAgentText("I'll add a failing test first so we see the bug, then make it pass.")
        ShowPlanCard(
            title = "fix null-token handling",
            items = listOf(
                PlanItem(PlanItemState.Done, "read src/auth/session.ts"),
                PlanItem(PlanItemState.Doing, "add test · null token should throw AuthError"),
                PlanItem(PlanItemState.Pending, "patch verify() to throw on null"),
                PlanItem(PlanItemState.Pending, "run full suite"),
            ),
        )
        ShowAgentText("creating the test file now.")
        ShowDiffBlock(
            path = "src/auth/session.test.ts",
            added = 14,
            removed = 0,
            lines = listOf(
                DiffLine(12, ' ', "  });"),
                DiffLine(13, ' ', ""),
                DiffLine(14, '+', "  it('rejects null token', () => {"),
                DiffLine(15, '+', "    expect(() => verify(null))"),
                DiffLine(16, '+', "      .toThrow(AuthError);"),
                DiffLine(17, '+', "  });"),
            ),
        )
        ShowApprovalCard(
            title = "run tests in ~/code/foo-api?",
            subtitle = "first time running shell in this project. approve once, or always.",
            detail = "$ npm test --silent",
        )
        ShowCommandBlock(
            cmd = "npm test --silent",
            exitCode = 1,
            output = """FAIL  src/auth/session.test.ts
  ● rejects null token
    expected: AuthError
    received: undefined""",
        )
        ShowAgentText("failed as expected. patching now.")
        ShowDiffBlock(
            path = "src/auth/session.ts",
            added = 3,
            removed = 1,
            lines = listOf(
                DiffLine(8, ' ', "export function verify(token) {"),
                DiffLine(9, '-', "  if (!token) return null;"),
                DiffLine(10, '+', "  if (!token)"),
                DiffLine(11, '+', "    throw new AuthError(\"no token\");"),
                DiffLine(12, '+', ""),
            ),
        )
        ShowCommandBlock(
            cmd = "npm test",
            exitCode = 0,
            output = """PASS  src/auth/session.test.ts
PASS  src/auth/index.test.ts
Tests: 47 passed, 47 total
Time:  3.2s""",
        )
        ShowAgentText("green. pushing to origin/fix-login.", streaming = true)
    }
}

/**
 * C.02 — destructive approval (red 2dp + "Proceed" CTA). Also shows EventBar below the
 * card while waiting for a decision.
 */
@Composable
fun ColumnScope.ShowSessionDestructive() {
    SessionRichShell(showComposer = false) {
        ShowUserMsg(text = "clean the dist folder before rebuild.", time = "01:05")
        ShowAgentText("this will remove build artifacts. need your go-ahead.")
        ShowApprovalCard(
            title = "delete 1,247 files in dist/?",
            subtitle = "this cannot be undone from inside this session. the files are not in git.",
            detail = """$ rm -rf dist/
  dist/index.js
  dist/index.js.map
  dist/assets/ (241 files)
  …and 1,003 more""",
            danger = true,
        )
        ShowEventBar(
            icon = "◌",
            label = "awaiting your decision…",
            color = BclawTheme.colors.roleWarn,
        )
    }
}

/**
 * C.03 — image inline with plan side (collapse to stacked in portrait phone).
 */
@Composable
fun ColumnScope.ShowSessionImagePlan() {
    SessionRichShell(showComposer = false) {
        ShowUserMsg(
            text = "the login button is the wrong color on dark mode. here.",
            time = "01:06",
        )
        ShowImagePreview(caption = "screenshot.png · 300×180 · 28 kb")
        ShowReasoning(summary = "comparing hex · checking design tokens · 3s")
        ShowAgentText {
            val type = BclawTheme.typography
            val colors = BclawTheme.colors
            // JSX mixes inline `<code>` runs into the sentence — we drop to a single
            // flowing Text since Compose doesn't render span-level backgrounds inside
            // Text without AnnotatedString. Readable loss, same information.
            Text(
                text = "I see — the button is using --brand-600 but in dark mode it should fall back to --brand-300. three ways to fix:",
                style = type.bodyLarge,
                color = colors.inkPrimary,
            )
        }
        ShowPlanCard(
            title = "fix options",
            items = listOf(
                PlanItem(PlanItemState.Pending, "add a token override in dark.css (scoped)"),
                PlanItem(PlanItemState.Pending, "change the button component to use a semantic token"),
                PlanItem(PlanItemState.Pending, "just patch this one instance with a media query"),
            ),
        )
        ShowAgentText("I'd lean option 2 — want me to go?")
    }
}

/**
 * C.04 — long output fold. Single command whose transcript is too long to inline.
 */
@Composable
fun ColumnScope.ShowSessionLongFold() {
    SessionRichShell(showComposer = false) {
        ShowUserMsg(text = "why is the build slow?", time = "01:10")
        ShowAgentText("let me check what webpack is actually doing.")
        ShowLongOutputFold(
            lines = listOf(
                "$ npm run build -- --stats verbose",
                "  [0] ./src/index.tsx 2.4 kb {main} [built]",
                "  [1] ./src/app.tsx 8.1 kb {main} [built]",
                "  [2] ./node_modules/react/index.js 312 b",
            ),
            shown = 4,
            total = 847,
        )
        ShowReasoning(summary = "analyzed 847 lines · found pattern")
        ShowAgentText {
            val type = BclawTheme.typography
            val colors = BclawTheme.colors
            Text(
                text = "the problem is the @sentry/webpack-plugin — it's uploading sourcemaps on every dev build. want me to gate it behind NODE_ENV=production?",
                style = type.bodyLarge,
                color = colors.inkPrimary,
            )
        }
    }
}
