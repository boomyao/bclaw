package com.bclaw.app.ui.showcase.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bclaw.app.ui.showcase.MetroPalette
import com.bclaw.app.ui.showcase.ShowGestureBar
import com.bclaw.app.ui.showcase.ShowStatusBar
import com.bclaw.app.ui.showcase.ShowcaseNeutrals
import com.bclaw.app.ui.theme.BclawTheme

/**
 * E · Remote desktop sidecar — pixel mirrors of `screens-remote.jsx`.
 *
 * Three patterns (trackpad · magnifier · landscape PIP) that each keep the agent
 * reachable while showing the remote Mac. The desktop mock is an illustrative
 * stand-in — a navy gradient with a fake Finder window carries the design intent
 * without dragging in real capture bytes.
 */

private val FakeDesktopGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF2D4A6B), Color(0xFF1A2E4A)),
)

// ── E.01 · Trackpad mode ─────────────────────────────────────────────────
/**
 * Body-only trackpad sidecar — viewport preview + modifier keys + trackpad surface + click
 * CTAs. Live overlay embeds it inside its own chrome; showcase wrapper adds status/gesture
 * bars for the phone frame.
 */
@Composable
fun RemoteTrackpadContent(modifier: Modifier = Modifier) {
    Column(modifier = modifier.background(ShowcaseNeutrals.N0)) {
        RemoteHeader(label = "ember · 1512×982 @ 60", latency = "12 ms")
        // Viewport — top half
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(FakeDesktopGradient)
                .border(1.dp, ShowcaseNeutrals.N200),
        ) {
            FakeDesktopMenubar()
            FakeFinderWindow(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 40.dp, vertical = 50.dp),
            )
            FakeCursor(offsetX = 140.dp, offsetY = 110.dp)
        }
        // Trackpad — bottom half
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(ShowcaseNeutrals.N950),
        ) {
            // Modifier keys row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf("cmd", "⇧", "⌥", "⌃", "esc", "tab").forEach { k ->
                    ModKey(text = k)
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .border(1.dp, MetroPalette.Cyan)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "⊕ MAGNIFY",
                        style = BclawTheme.typography.micro,
                        color = MetroPalette.Cyan,
                        letterSpacing = 1.sp,
                    )
                }
            }
            // Trackpad surface
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .border(1.5.dp, ShowcaseNeutrals.N800)
                    .background(ShowcaseNeutrals.N900),
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp),
                ) {
                    val type = BclawTheme.typography
                    Text(text = "swipe — move cursor", style = type.monoSmall, color = ShowcaseNeutrals.N500)
                    Text(text = "tap — click · 2-finger — right", style = type.monoSmall, color = ShowcaseNeutrals.N500)
                    Text(text = "pinch — zoom viewport", style = type.monoSmall, color = ShowcaseNeutrals.N500)
                }
            }
            Spacer(Modifier.height(10.dp))
            // Click / right / scroll CTA row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ClickCta(text = "CLICK", accent = true, modifier = Modifier.weight(1f))
                ClickCta(text = "RIGHT", modifier = Modifier.weight(1f))
                ClickCta(text = "SCROLL", modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun ColumnScope.ShowRemoteTrackpad() {
    ShowStatusBar(dark = true)
    RemoteTrackpadContent(Modifier.weight(1f).fillMaxWidth())
    ShowGestureBar(dark = true)
}

// ── E.02 · Magnifier (precision · loupe + d-pad) ─────────────────────────
@Composable
fun RemoteMagnifierContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(ShowcaseNeutrals.N0),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(FakeDesktopGradient),
        )
        FakeFinderWindow(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 22.dp)
                .padding(bottom = 60.dp),
            showBodyText = false,
        )
        // Circular magnifier loupe
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-60).dp)
                .size(170.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(3.dp, MetroPalette.Cyan, shape = CircleShape),
        ) {
            // Faux magnified source: three code lines at 4× scale
            val type = BclawTheme.typography
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer(scaleX = 2.8f, scaleY = 2.8f)
                    .padding(12.dp),
            ) {
                Text(
                    text = "+ verify(token)",
                    style = type.mono.copy(fontSize = 9.sp),
                    color = Color(0xFF0A7F3F),
                )
                Text(
                    text = "  return null;",
                    style = type.mono.copy(fontSize = 9.sp),
                    color = Color(0xFF333333),
                )
                Row {
                    Box(
                        modifier = Modifier
                            .background(MetroPalette.Cyan)
                            .padding(horizontal = 2.dp),
                    ) {
                        Text(
                            text = "}",
                            style = type.mono.copy(fontSize = 9.sp),
                            color = Color(0xFF000000),
                        )
                    }
                }
            }
            // Crosshairs
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MetroPalette.Cyan.copy(alpha = 0.6f)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(MetroPalette.Cyan.copy(alpha = 0.6f)),
            )
        }
        // D-pad
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DPad()
            Spacer(Modifier.height(16.dp))
            Text(
                text = "PRECISION · D-PAD NUDGES 1PX",
                style = BclawTheme.typography.micro,
                color = ShowcaseNeutrals.N400,
                letterSpacing = 1.sp,
            )
        }
    }
}

@Composable
fun ColumnScope.ShowRemoteMagnifier() {
    ShowStatusBar(dark = true)
    RemoteMagnifierContent(Modifier.weight(1f).fillMaxWidth())
    ShowGestureBar(dark = true)
}

// ── E.03 · Landscape PIP ─────────────────────────────────────────────────
/**
 * Landscape mode with full viewport + agent PIP + floating tool column. In the JSX this
 * is rendered at 915×412 — outside the phone frame. We render it inline inside a fixed
 * landscape frame that the catalogue lays out horizontally-scrolling.
 */
@Composable
fun ShowRemotePipLandscape(modifier: Modifier = Modifier) {
    val type = BclawTheme.typography
    Box(
        modifier = modifier
            .size(width = 915.dp, height = 412.dp)
            .clip(RectangleShape)
            .background(Color(0xFF111111))
            .padding(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxSize().background(ShowcaseNeutrals.N0)) {
            // Viewport
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(FakeDesktopGradient),
            ) {
                FakeDesktopMenubar(appName = "Xcode")
                // Code editor window
                Column(
                    modifier = Modifier
                        .padding(start = 40.dp, top = 60.dp, end = 200.dp, bottom = 60.dp)
                        .fillMaxSize()
                        .background(Color(0xFF1E1E1E))
                        .padding(14.dp),
                ) {
                    Text(
                        text = "export function verify(token) {",
                        style = type.mono.copy(fontSize = 9.sp),
                        color = Color(0xFFCCCCCC),
                    )
                    Text(
                        text = "  if (!token) return null;",
                        style = type.mono.copy(fontSize = 9.sp),
                        color = Color(0xFFCCCCCC),
                    )
                    Text(
                        text = "  return jwt.verify(token, SECRET);",
                        style = type.mono.copy(fontSize = 9.sp),
                        color = Color(0xFFCCCCCC),
                    )
                    Text(
                        text = "}",
                        style = type.mono.copy(fontSize = 9.sp),
                        color = Color(0xFFCCCCCC),
                    )
                }
                FakeCursor(offsetX = 300.dp, offsetY = 120.dp)
                // Agent PIP (floating, top right)
                AgentPip(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 40.dp, end = 20.dp)
                        .width(170.dp),
                )
            }
            // Floating tool column (64dp wide on the right)
            Column(
                modifier = Modifier
                    .width(64.dp)
                    .fillMaxHeight()
                    .border(1.dp, ShowcaseNeutrals.N200)
                    .background(ShowcaseNeutrals.N950)
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(
                    "⌨" to "KEYS",
                    "⊕" to "ZOOM",
                    "◉" to "CLICK",
                    "≡" to "MENU",
                    "✕" to "CLOSE",
                ).forEach { (icon, label) ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ShowcaseNeutrals.N900)
                            .border(1.dp, ShowcaseNeutrals.N800)
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = icon,
                            fontSize = 16.sp,
                            color = ShowcaseNeutrals.N300,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = label,
                            style = type.micro,
                            fontSize = 8.sp,
                            color = ShowcaseNeutrals.N400,
                            letterSpacing = 0.5.sp,
                        )
                    }
                }
            }
        }
    }
}

// ── Internals ────────────────────────────────────────────────────────────

@Composable
private fun RemoteHeader(label: String, latency: String) {
    val type = BclawTheme.typography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .border(1.dp, ShowcaseNeutrals.N200)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label.uppercase(),
            style = type.micro,
            color = ShowcaseNeutrals.N400,
            letterSpacing = 1.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "● $latency",
            style = type.micro,
            color = MetroPalette.Lime,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun BoxScope.FakeDesktopMenubar(appName: String = "Finder") {
    Row(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(22.dp)
            .background(Color.White.copy(alpha = 0.85f))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = appName,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            color = Color.Black,
        )
        listOf("File", "Edit", "View", "Go").forEach { menu ->
            Text(text = menu, fontSize = 10.sp, color = Color.Black)
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = "01:08",
            style = BclawTheme.typography.mono,
            fontSize = 10.sp,
            color = Color.Black,
        )
    }
}

@Composable
private fun FakeFinderWindow(
    modifier: Modifier = Modifier,
    showBodyText: Boolean = true,
) {
    val type = BclawTheme.typography
    Column(
        modifier = modifier
            .background(Color.White)
            .border(1.dp, Color.Black.copy(alpha = 0.15f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(Color(0xFFECECEC))
                .border(1.dp, Color(0xFFCCCCCC))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFF5F57)))
            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFFBD2E)))
            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF27C93F)))
            Spacer(Modifier.width(2.dp))
            Text(
                text = "foo-api — src/auth",
                fontSize = 10.sp,
                color = Color(0xFF333333),
            )
        }
        if (showBodyText) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "export function verifySession(token) {",
                    style = type.mono,
                    fontSize = 9.sp,
                    color = Color(0xFF333333),
                )
                Text(
                    text = "  if (!token) return null;",
                    style = type.mono,
                    fontSize = 9.sp,
                    color = Color(0xFF333333),
                )
                Text(
                    text = "  return jwt.verify(token, SECRET);",
                    style = type.mono,
                    fontSize = 9.sp,
                    color = Color(0xFF333333),
                )
                Text(
                    text = "}",
                    style = type.mono,
                    fontSize = 9.sp,
                    color = Color(0xFF333333),
                )
            }
        }
    }
}

/**
 * Fake mac cursor — a tilted white arrow with a black border. Compose doesn't have a
 * Path mini-DSL so we approximate with a rotated rectangle + triangle silhouette.
 */
@Composable
private fun BoxScope.FakeCursor(offsetX: androidx.compose.ui.unit.Dp, offsetY: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset(x = offsetX, y = offsetY)
            .size(14.dp, 18.dp)
            .rotate(-8f)
            .background(Color.White)
            .border(1.dp, Color.Black),
    )
}

@Composable
private fun ModKey(text: String) {
    Box(
        modifier = Modifier
            .border(1.dp, ShowcaseNeutrals.N800)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = BclawTheme.typography.mono,
            color = ShowcaseNeutrals.N300,
        )
    }
}

@Composable
private fun ClickCta(text: String, accent: Boolean = false, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(if (accent) MetroPalette.Cyan else ShowcaseNeutrals.N900)
            .border(
                width = 1.dp,
                color = if (accent) MetroPalette.Cyan else ShowcaseNeutrals.N800,
            )
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = BclawTheme.typography.mono,
            color = if (accent) ShowcaseNeutrals.N0 else ShowcaseNeutrals.N300,
            letterSpacing = 1.sp,
        )
    }
}

/** 3×3 D-pad with center select. Matches `screens-remote.jsx:99-109`. */
@Composable
private fun DPad() {
    val cells = listOf(
        null, "▲", null,
        "◀", "●", "▶",
        null, "▼", null,
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        cells.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { k ->
                    DPadCell(text = k)
                }
            }
        }
    }
}

@Composable
private fun DPadCell(text: String?) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(
                when (text) {
                    null -> Color.Transparent
                    "●" -> MetroPalette.Cyan
                    else -> ShowcaseNeutrals.N900
                }
            )
            .border(
                width = if (text == null) 0.dp else 1.dp,
                color = if (text == null) Color.Transparent else ShowcaseNeutrals.N800,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (text != null) {
            Text(
                text = text,
                fontSize = 14.sp,
                color = if (text == "●") ShowcaseNeutrals.N0 else ShowcaseNeutrals.N300,
            )
        }
    }
}

@Composable
private fun AgentPip(modifier: Modifier = Modifier) {
    val type = BclawTheme.typography
    Column(
        modifier = modifier
            .background(Color(0xFF141410).copy(alpha = 0.95f))
            .border(1.dp, MetroPalette.Cyan)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(MetroPalette.Cyan))
            Text(
                text = "CODEX · WATCHING",
                style = type.micro,
                fontSize = 9.sp,
                color = MetroPalette.Cyan,
                letterSpacing = 1.sp,
            )
        }
        Text(
            text = "I see the cursor is on line 3 — want me to add the null-token test next to this file?",
            style = type.mono.copy(fontSize = 11.sp),
            color = ShowcaseNeutrals.N500,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(MetroPalette.Cyan)
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "YES",
                    style = type.micro,
                    fontSize = 9.sp,
                    color = ShowcaseNeutrals.N0,
                    letterSpacing = 1.sp,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, ShowcaseNeutrals.N500)
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "LATER",
                    style = type.micro,
                    fontSize = 9.sp,
                    color = ShowcaseNeutrals.N500,
                    letterSpacing = 1.sp,
                )
            }
        }
    }
}
