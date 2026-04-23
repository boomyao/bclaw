# bclaw — UX Design (v0)

> Companion to `SPEC.md`. SPEC defines the technical contract (protocol, data model, architecture); this file defines the **product shape** — interaction model, screen states, visual tone, the moments that make or break the feel. Where the two disagree, UX wins for anything user-facing, SPEC wins for anything wire-level.

---

## 0. Design principles

These are the rules every downstream decision must pass. When a choice feels ambiguous, come back here.

1. **The conversation is the product.** Chat is the root of navigation, not a destination at the end of a drill-down. Everything else — workspace switching, settings, connection health — wraps the conversation, it does not stack in front of it.

2. **Make the remote machine present.** The user is driving a machine in another room. The UI's single most important job on mobile is *awareness*: connection health, agent state, mid-turn progress, last line of output from a turn running in a workspace the user isn't currently looking at. bclaw fails if the phone feels like a dead terminal.

3. **One thumb, one screen.** Every critical action must be reachable without stretching or leaving the current screen. Double-hand interactions are allowed only for long-form composition.

4. **Banners, not blockers.** When things go wrong (network, auth, approval leak, error), the UI surfaces what happened in a banner or inline affordance — never a modal that blocks forward motion. When things go right, the UI stays out of the way.

5. **No feature without a phone-specific reason.** Every element must justify its existence against "is this better than the desktop `claude` / `codex` command?" If the answer is "same," cut it. The phone is not a worse desktop — it's a different device.

---

## 1. Navigation model

**One root screen: Chat.**

Chat is not a destination — it is the home. Opening the app returns you to the last thread you were in. First launch shows a welcome state that merges into connection setup; every launch after that skips straight to chat.

Everything else is an **overlay** on chat:

- **Drawer** (swipe from left edge, or hamburger tap): workspace list with nested thread lists. This is where you switch context.
- **Status sheet** (swipe down from the top bar, or tap the connection chip): connection detail, quick toggles.
- **Connection settings** (opened from drawer overflow): full-screen modal, dismissible.

**There is no backstack navigation between primary screens.** The system Back button does not change your workspace or thread — those are one gesture away from wherever you are. Back closes overlays (drawer / sheet / modal), then exits the app.

```
┌───────────────────────────┐
│ [☰] WorkspaceA / thr-abc ●│  ← top bar: drawer btn, workspace/thread label, connection chip
├───────────────────────────┤
│                           │
│    chat message list      │
│    (items per SPEC §4.3)  │
│                           │
│ ─────────────────────     │
│ ⟲ agent working…          │  ← running-turn strip (only when a turn is in progress)
├───────────────────────────┤
│ [ type a prompt...    ] ▶ │  ← input row: composer, send / stop
└───────────────────────────┘
```

---

## 2. Screen inventory

1. **Welcome / first-run** flow
2. **Chat** (permanent home)
3. **Drawer** overlay (workspace + threads)
4. **Status sheet** (top pull-down)
5. **Connection settings** (modal)

There is **no dedicated workspace-list screen** and **no dedicated thread-list screen**. Both are subsumed into the drawer.

### 2.1 Welcome flow

First launch. **One field, one tap.**

```
┌────────────────────────────────┐
│                                │
│   Connect to your Mac          │
│                                │
│   ┌──────────────────────────┐ │
│   │ paste connection string  │ │  ← single text field
│   └──────────────────────────┘ │
│                                │
│          [  Connect  ]         │
│                                │
│   How do I get this? ↗         │  ← expands a short "run bclaw-handoff" help
│                                │
└────────────────────────────────┘
```

The user gets a `bclaw://` URL from their Mac (see [SPEC.md §3.1](SPEC.md) for the URL format and the `bclaw-handoff` helper). They paste it into the single field — system paste from a long-press, or a "paste from clipboard" chip that appears above the field when the clipboard already contains a `bclaw1://` payload. Tap Connect. The app parses the URL, populates host / token / workspaces silently, runs the handshake, and cross-fades into Chat.

- **Parse failure:** inline red text under the field with a one-line reason (e.g. "expected `bclaw1://…`").
- **Handshake failure:** stay on the welcome screen and surface the error per §9 (offline strip, auth failed red text, etc. — same handlers the connected app uses).

**No QR in v0, and not as a placeholder either.** QR is a v1 feature — same `bclaw://` URL, just delivered by camera instead of clipboard. The parser, auth, handshake, and success paths are all identical; the only v1 add-on is a camera capture surface that decodes a QR into the same string the v0 paste field already accepts. The "paste a URL" primitive is the stable one; QR is one possible transport layered on top. This is why the paste field is **not** a stopgap — it's the actual abstraction, and QR is a transport optimization we'll add when there's real dog-food pressure for it.

---

## 3. Connection state indicator

A single always-visible chip in the top bar (right side). Five states. Colors are from §15.1 Terminal Metro tokens — no green, because §15 locked the palette with a single `AccentCyan` accent. "Connected" is the quiet state: the dot is cyan but the label is plain white so it recedes against agent output. Non-connected states surface their color through the label so the chip looks alive whenever the user needs to notice it.

| State | Dot token | Animation | Label color | Label text |
|---|---|---|---|---|
| Connected | `AccentCyan` | steady | `TextPrimary` | `connected` |
| Connecting | `AccentCyan` | alpha pulse (1200ms, §15.5) | `AccentCyan` | `connecting…` |
| Reconnecting | `WarningAmber` | alpha pulse (1200ms) | `WarningAmber` | `reconnecting` |
| Offline / Idle / Error | `DangerRed` | steady | `DangerRed` | `offline` |
| Auth failed | `DangerRed` | steady | `DangerRed` | `auth failed` |

Tapping the chip pulls down the **status sheet** (§6). The chip is **never hidden**, even during a running turn. It is the continuous "remote machine is present" signal and it is never silenced.

---

## 4. Chat screen

The home of the product. Everything else wraps around it.

### 4.1 Layout

- **Top bar** (48dp): drawer button (left), workspace name and thread name stacked (center-left, two lines of small type), connection chip (right).
- **Message list** (flex): renders items in order per §7 below. Scrolls.
- **Agent-running strip** (variable, shown only during a turn): a thin horizontal strip between the message list and the input row showing the current item being worked on — "thinking…", "running `pnpm build`", "editing 3 files". Disappears when `turn/completed` arrives.
- **Input row** (min 56dp, expands to 5 lines of type): composer + send/stop.

### 4.2 States

| State | What the user sees |
|---|---|
| **Empty** (brand-new thread, no history, no turn) | Quiet centered prompt: "Tell the agent what to work on." A faint example row beneath: "e.g. Run the tests and summarize failures." |
| **Loading** (`thread/read` in flight) | 3-bubble skeleton shimmer. Top bar still shows names and connection chip — the shell never blinks. |
| **Idle** (history loaded, no running turn) | Full item list; send button enabled. |
| **Running turn** | Send button morphs into stop button. Agent-running strip appears above the input. User messages and optimistic bubbles keep flowing into the list as items arrive. |
| **Turn completed** | Strip disappears with a subtle scale-out. Light haptic tick. `item/completed` items replace their streaming precursors atomically. |
| **Turn failed** | An inline red-bordered bubble below the last item: "turn failed — <one-line reason>" with a small "retry" action that resends the same user input. |
| **Turn interrupted** | Same inline bubble, neutral-colored: "turn interrupted" with no retry affordance. |
| **Offline** | A persistent non-modal strip above the input row: "offline — your next message will send when reconnected." The input stays enabled; messages queue. |

### 4.3 Input row

- **Default height:** 1 line. Grows to a max of 5 lines as the user types, then scrolls internally.
- **Send button:** primary color when the field has content, disabled gray when empty. Tapping sends — **no confirmation dialog**. Friction here kills the product; the user already decided when they typed.
- **Stop button:** replaces send the moment `turn/started` fires. Tapping sends `turn/interrupt`. Lingers ~500ms after the turn ends, then flips back, to avoid a jarring flicker on fast turns.
- **Voice input: there is none, deliberately.** No mic icon, no "voice coming soon" toast, no in-app voice affordance. Users who want to dictate tap the mic on their system keyboard (豆包, Typeless, GBoard voice) — it's already excellent and bclaw has no reason to reinvent it. Reserving UI real estate for a voice feature that isn't on the roadmap would be fake differentiation and is explicitly rejected.

### 4.4 Scroll behavior

- **Auto-scroll to bottom** on new items **only if** the user is within 100dp of the bottom. If the user has scrolled up to read, respect that.
- When new items arrive while the user is scrolled up, a small floating chip appears in the bottom-right: **"↓ 3 new"**. Tap to jump to bottom.
- **No pull-to-refresh.** SPEC §4.1 says fetch fresh on entry; there is nothing new to pull. Pull-to-refresh on chat implies reconnecting, which is what the status sheet is for.

---

## 5. Drawer

Opens on left-edge swipe or hamburger tap. Overlays chat — chat stays partially visible behind a 40% dim to keep the user anchored. Width: ~75% of screen.

### 5.1 Contents (top to bottom)

```
bclaw                            [⚙︎]    ← header + settings shortcut
──────────────────────────────────────
● WorkspaceA       · connected  · ⟲     ← dot = conn state, spinner = turn running
  ├ thr-abc  · just now · 💬             ← unread chip
  ├ thr-def  · 2h ago
  │    "compiling pnpm build..."        ← last output line of running turn
  └ [+ new thread]
──────────────────────────────────────
○ WorkspaceB       · offline
  └ thr-ghi · yesterday
──────────────────────────────────────
● WorkspaceC       · connected
  └ thr-jkl · 3d ago
──────────────────────────────────────
[+ add workspace]
```

### 5.2 Signals per workspace row

- **Leading dot:** this workspace's connection state (green / amber / red). Distinct from the top-bar chip (which is global socket health) because a workspace could be unreachable due to cwd misconfig even when the socket is fine.
- **Trailing spinner ⟲:** a turn is running in **any** thread within this workspace right now, including threads the user isn't currently viewing. This is the core "remote machine is present" affordance.
- The spinner **must animate** — stillness here kills the signal.

### 5.3 Signals per thread row

- **Name** (`thread.name` → `thread.preview` → id prefix per SPEC §5.2).
- **Last activity timestamp** (relative: "just now", "2h ago", "yesterday", ">7d").
- **Unread chip** 💬 if a turn completed in this thread since the user last viewed it.
- **Last output line preview** (directly under the row, indented, muted): only for threads with a running turn. Updates live as `item/commandExecution/outputDelta` and `item/agentMessage/delta` arrive. Truncate to ~40 chars. This is the single most important detail for the "I can feel it working" feeling — it transforms a silent spinner into a live pulse.
- Tap → closes drawer, chat cross-fades to that thread (~200ms).

### 5.4 Long-press

- **Long-press a thread** → bottom sheet: Archive, Rename, Copy id.
- **Long-press a workspace** → bottom sheet: Rename, Remove (with confirm), Disconnect.

Long-press is the power-user surface — it doesn't need to be discoverable in v0.

---

## 6. Status sheet

Pulled down from the top bar or from tapping the connection chip. A **small** overlay, not full-screen, attached to the top. Contains:

- Host + port + tailnet latency (from the last WebSocket ping round-trip, if available)
- Last handshake time
- Quick buttons: **Disconnect** (cleanly tears down the session), **Reconnect now** (manual retry)
- Link to Connection settings

Nothing else. This is not a mini-dashboard; it's a status panel.

---

## 7. Item rendering

Per SPEC §4.3, these item types must render in v0. Each has a specific visual treatment:

### 7.1 `userMessage`

- Right-aligned.
- **Subtle** bubble — surface-variant color, not the primary accent. Loud user bubbles compete with agent output; this product is about what the agent says, not what the user said.
- Max width 85% of content area.
- Timestamp in tiny muted type below the bubble.

### 7.2 `agentMessage`

- Left-aligned, **no bubble**. Bubbles would box long content awkwardly. Use type color / size to distinguish from user messages.
- **Markdown rendering** (MVP scope):
  - **Code blocks:** system mono, muted surface, copy-on-long-press.
  - **Inline code:** system mono, no background.
  - **Lists, headings, bold/italic:** standard.
  - **Links:** colored; tapping opens a confirm: "open <host> in browser?" No auto-navigation.
- **Streaming:** characters appear as `item/agentMessage/delta` arrives. **Do not batch** — the streaming is part of the "the machine is present" feel. Drop a frame budget of 60fps for delta application.

### 7.3 `commandExecution`

A **compact card** in the flow, not a bubble.

```
┌──────────────────────────────────┐
│ ▶ pnpm build                     │
│   /Users/you/projects/foo        │
│   ⟲ running 12s                  │
│                                  │
│   └ compiling 147/520 files…     │  ← last 3 lines, tailing
└──────────────────────────────────┘
```

- Compact by default, tap to expand full stdout/stderr in a scrollable body.
- Status badge on the card header: `⟲` in-progress, `✓` completed, `✕` failed, `⏸` declined.
- During the run: tail the last 3 lines of output below the command — live feed without taking over the screen.
- On completion: the live tail collapses, replaced by a one-line summary ("exit 0 in 24s" / "exit 1 in 3s").

### 7.4 `fileChange`

A card with summary header + expandable diff.

```
┌──────────────────────────────────┐
│ ✎ 3 files changed                │
│   src/app.ts        +12 -3       │
│   src/lib/main.ts   +1  -1       │
│   README.md         +8  -0       │
└──────────────────────────────────┘
```

- Tap to expand into the unified diff (plain monochrome — **no syntax highlighting in MVP**).
- Diff source is `turn/diff/updated` per SPEC §5.7.
- Status badge same as commandExecution.
- Per the user's own insight ("after AI, reading diffs is infrequent"), this is a secondary surface — make it compact and collapsed by default. Don't over-engineer.

### 7.5 `reasoning`

Collapsed by default as a small muted row: **"💭 thinking…"**. Tap to expand the streamed summary. Most users will not expand — that's fine. Reasoning is a power-user affordance and should not compete for attention with actual output.

### 7.6 Unknown items

Small gray row: `— unsupported item (<kind>) —`. Never crash. Codex adds new item types over time; fail open.

---

## 8. Key moments

Walk through these end-to-end when reviewing any implementation.

### 8.1 First launch

1. Splash → Welcome headline "Connect to your Mac" + single paste field
2. User pastes a `bclaw://` URL (long-press → paste, or taps the "paste from clipboard" chip if one is offered)
3. Tap Connect → parse → handshake
4. Success → cross-fade to Chat empty state: "Tell the agent what to work on."
5. First user input → chat comes alive

### 8.2 Sending a message

1. User types
2. Taps send — user bubble appears instantly, **no spinner** (the optimistic render is the acknowledgment)
3. Agent-running strip appears: "agent working…"
4. First `item/agentMessage/delta` arrives — strip updates to "responding…" and the left-aligned message begins streaming character by character
5. If a `commandExecution` starts mid-turn — strip updates: "running `pnpm build`"
6. `turn/completed` — strip disappears, subtle haptic tick, final items land

### 8.3 Leaving and returning mid-turn

1. User sends a long task, locks phone, puts it in pocket
2. Unlocks later
3. Chat opens on the current thread, agent-running strip is **already present** with the current state (the foreground service kept the WebSocket alive per SPEC §7.2)
4. If the turn completed during the lock — chat shows the final state immediately; a subtle top-sticky chip says **"completed while you were away"** for ~4s then dismisses

### 8.4 The drawer glance

1. User is mid-conversation in WorkspaceA
2. Swipes from the left edge
3. Drawer slides out — **WorkspaceC has a spinner** and shows its last output line, "compiling pnpm build..."
4. User gets instant peace of mind: C is working. Closes drawer. Keeps typing in A.

**This is the core moment that direction-3 (activity feed) wanted to deliver.** Direction 2 earns it without a separate feed screen. If §8.4 doesn't feel magical in the real implementation, the design is broken.

### 8.5 Switching threads

1. User is in thr-abc of WorkspaceA, mid-read of an old conversation
2. Swipes from the left edge
3. Drawer slides out
4. User taps thr-def in the same workspace
5. Drawer slides closed, chat cross-fades (~200ms) to thr-def
6. Chat loads thr-def via `thread/resume` + `thread/read`; skeleton for ~100ms; content lands

No backstack entry is pushed. The system Back button still just closes overlays and exits — it does not walk a history of thread visits.

---

## 9. Error and recovery UX

Restating principle 4: **banners, not blockers.** Every failure surfaces inline, never as a modal.

| Failure | Surface | User action |
|---|---|---|
| Socket disconnect | Amber chip in top bar + offline strip above input row | "offline — your next message will send when reconnected"; input stays enabled, new messages queue |
| Auth rejected | Red chip + red strip "auth failed — tap to reconfigure" | Tap → opens Connection settings modal |
| Turn failed (`CodexErrorInfo::*`) | Inline bubble below last item, red left border, red text with one-line reason | Tap "retry" to resend the same user input |
| Turn interrupted (user-initiated) | Inline bubble, neutral color, no retry | (none) |
| Approval leaked (despite §5.3 config) | Transient top toast: "approval auto-accepted — see logs" | Informational only |
| Unknown item kind | Inline gray row "`— unsupported item —`" | None |
| Sending while offline | User bubble appears with a tiny `⌛` in the corner — "queued" state | Long-press → "cancel queued message" |
| Usage limit exceeded | Inline bubble: "usage limit exceeded" (no retry — retry would fail) | Informational only |
| Server overloaded (`-32001`) | Transient top toast: "server busy, retrying in Ns" | None — handled in backoff layer |

---

## 10. Typography & spatial system

Light touch for MVP — define the system, don't over-specify.

- **Headlines:** system sans, 18sp semibold. Used for: welcome title, drawer header, modal titles.
- **Body:** system sans, 15sp regular. Chat messages, drawer rows, button labels.
- **Meta:** system sans, 12sp regular muted. Timestamps, subtitles, chip text.
- **Code:** system mono, 14sp. Command execution body, file change body, inline code inside agentMessage.
- **Padding:** 16dp horizontal on all scrollable surfaces, 12dp between adjacent elements, 20dp between logical sections, 8dp inside compact cards.
- **Color:** Material 3 dynamic color. Single primary accent; everything else is tone-shifted neutrals. **No secondary color in MVP.**
- **Dark mode:** Material 3 defaults only. Do not hand-pick a custom dark palette for v0; v1 can invest.
- **Elevation:** sparingly. Cards in chat are flat with subtle borders, not drop-shadowed. Drawer uses a single elevation level against chat.

---

## 11. Notifications

- **Foreground service notification** (required by Android to keep the WebSocket alive per SPEC §7.2): non-dismissible while the service is running. Title: "bclaw — connected to <host>". Text: dynamic, reflecting the most recent turn state, or "idle" when nothing is running.
- **No push / system notifications** in MVP. When a turn completes while the phone is locked, the user sees it the next time they open the app (see §8.3).
- v1 will add push-on-turn-complete once there's a relay to originate them from. Listed in SPEC §11 as out-of-scope.

---

## 12. Accessibility & input methods

- **Tap targets** ≥ 48dp.
- **Drawer gestures** have button equivalents (hamburger icon). Never gesture-only.
- **Dynamic type:** respect system font scale. Chat bubbles and cards must reflow, not truncate.
- **Content descriptions:** every icon-only button (hamburger, send, stop) has a descriptive content description for TalkBack.
- **Contrast:** use Material 3 surface/onSurface pairings; do not hand-pick hex values.
- **IME:** voice input is entirely the responsibility of the system input method (豆包, Typeless, GBoard voice). bclaw adds no custom voice UI and has no plan to add one. Users tap their keyboard's mic the same as in any other text field.

---

## 13. Deliberately out of scope

These are explicit cuts. If Codex wants to build them "while it's here," the answer is no — they don't ship in v0, and a half-built version is worse than none.

- Thread search
- Thread archive UI (SPEC mentions it; UX defers)
- Diff syntax highlighting
- File browser / file tree
- Rich Markdown tables (render as plain text)
- Image attachments (text-only `turn/start` input)
- Profile / avatar / user identity
- Team / multi-user concepts
- In-chat slash commands (`/compact`, `/review`, etc.)
- Push notifications on turn completion
- Tablet / landscape optimized layout
- Localization frameworks (mixed English + Chinese literals are fine)
- Home-screen widget / Quick Settings tile
- Watch companion
- Custom dark palette
- In-app QR scanner (v1 — same `bclaw://` URL format, camera path layered on top of the v0 paste primitive)
- Native / in-app voice input — users rely on system IME voice; not a roadmap item

---

## 14. Why direction 2, not direction 1 or 3

For future-me and any reviewer: a short note on the choice, since direction 2 isn't the obvious safe default and isn't the flashy experimental pick.

**Direction 1 (deep stack — Connection → WorkspaceList → Workspace → Chat)** was rejected because it's "safe" in a way that kills the product. Four levels of navigation on mobile is the mobile equivalent of a shell's `cd` chain — exactly the friction bclaw exists to eliminate. SPEC's original §6 defaulted to this; UX deliberately overrides it.

**Direction 3 (activity feed as home, zoom-in to chat)** was the most differentiated, but it has two problems for v0:

1. **It costs a round or two of Codex work that should go to acceptance items 4 (reconnect) and 5 (background survival)** — those are non-negotiable for the "带着手机出门" promise, and a pretty feed screen while reconnect is half-broken is a worse product than a plain drawer over a rock-solid session.
2. **It only pays off when the user has 3+ simultaneously active workspaces.** Early in the product's life, most users (including the developer dog-fooding v0) will have 1-2 workspaces. A feed of 1-2 cards is empty ceremony.

**Direction 2 captures ~80% of direction 3's value** through the drawer design in §5: the per-workspace spinner + last-output-line preview is a mini activity feed, collapsed into the switcher. The §8.4 "drawer glance" moment is direction 3's core win, delivered inside direction 2's structure.

**Evolution path to direction 3 later:** §5's drawer row data model is already a complete activity record (workspace status, running-turn flag, last-output-line, last-activity timestamp, unread flag). Lifting it out of the drawer into a standalone feed screen in v1 is a presentation-layer change, not an architecture change. Nothing here forecloses that future.

---

## 15. Terminal Metro — visual tokens

> Added after the baseline UI audit revealed the app was running on 100% Compose Material 3 defaults with **no theming layer whatsoever** (no `Theme.kt`, no `Color.kt`, no `Type.kt`, no `Shape.kt`). §10 earlier said "light touch for MVP" which Codex correctly read as an escape hatch to do nothing. This section locks down concrete hex values, dp sizes, and composable replacement specs. **Codex treats numbers in this section as hard constraints, not suggestions.**

### 15.0 Name and inspiration

The design language is internally called **Terminal Metro** — Metro / MDL aesthetic applied to a terminal-adjacent content type. Inspired by Nokia Lumia's Windows Phone era (Metro / Modern UI). Three Metro principles map directly to bclaw's goals:

1. **"Authentically digital"** — flat, no skeuomorphism, no fake depth. Mirrors §0 principle 2 ("make the remote machine present"): nothing pretends to be a physical object; the UI is a pure signal surface.
2. **"Content over chrome"** — typography is the hierarchy; UI chrome is minimized. Mirrors §0 principle 1 ("the conversation is the product").
3. **True-black + single saturated accent + flat rectangles** — engineering payoff: true black on OLED is pixel-off, extending the battery life that the foreground service needs.

Visual reference: **Nokia Lumia 800 / 920 era Windows Phone**, plus modern descendants like **Warp**, **Ghostty**, early **Linear**. Do not look at modern Microsoft Teams, Windows 11, or Fluent — those are the later evolution that lost Metro's rigor.

### 15.1 Palette (dark-first; light mode is v1)

All values are locked. No "system dynamic color." No Material 3 tonal derivation. These hex values go directly into `ui/theme/Color.kt` as `val` constants and are referenced via a custom `BclawColors` data class, **not** via `MaterialTheme.colorScheme`.

| Token | Hex | Use |
|---|---|---|
| `TerminalBlack` | `#000000` | App background. Literal pixel-off black on OLED. |
| `SurfaceNear` | `#0A0A0A` | Drawer panel, inline cards. A whisper above black to hint at layering without breaking the true-black aesthetic. |
| `SurfaceElevated` | `#141414` | Status sheet, dialogs, pressed / hover states. One more notch up. Use sparingly. |
| `Divider` | `#262626` | 1dp horizontal rules where absolutely needed (rarely — Metro prefers whitespace over lines). |
| `TextPrimary` | `#FFFFFF` | All body and headline text. |
| `TextMeta` | `#9A9A9A` | Timestamps, subtitles, card metadata. |
| `TextDim` | `#5A5A5A` | Placeholder text, disabled labels, path breadcrumbs. |
| **`AccentCyan`** | **`#00BCF2`** | **The single accent.** Status dots, selected state, focus state, link color, SEND button fill, running strip fill, drawer selection stripe. This is Lumia Cyan, locked. |
| `DangerRed` | `#E51400` | Error banners, failed turn inline text, offline status. Lumia Red. |
| `WarningAmber` | `#F0A30A` | Reconnecting indicator, transient warning toasts, `-32001` backoff banner. Lumia Orange. |

**Success state has no color.** Per Metro, absence of error IS success. No green checkmark, no "done!" toast. A completed turn lands silently with a subtle haptic tick.

### 15.2 Typography

All sizes are `sp`, all line heights are `sp`. Android system font families only — no custom font ship.

| Token | Family | Size | Line height | Tracking | Use |
|---|---|---|---|---|---|
| `HeroLight` | `sans-serif-light` | 34sp | 40sp | -0.5sp | Welcome headline, drawer workspace name |
| `TitleLight` | `sans-serif-light` | 20sp | 26sp | 0 | Top bar, card header, status sheet title |
| `Body` | `sans-serif` | 15sp | 22sp | 0 | Chat message text, form labels, button labels |
| `Meta` | `sans-serif` | 12sp | 16sp | 0 | Timestamps, subtitles, chip text |
| `Code` | `sans-serif-monospace` | 14sp | 20sp | 0 | Command execution body, file change diff, inline code inside agentMessage |

**Discipline rules:**

- `HeroLight` and `TitleLight` MUST use `sans-serif-light`. This is the Metro signature. Do not substitute regular-weight. Codex has forgotten weight discipline before.
- `Body` and `Meta` stay `sans-serif` (regular). No bold. Bold is used only inside markdown-rendered text in agent messages, never in UI chrome.
- Delete all `MaterialTheme.typography.*` references during migration; replace with `BclawTypography.*`.

### 15.3 Shape

**Zero corner radius. Everywhere. No exceptions that matter.**

| Element | Radius |
|---|---|
| Cards (workspace rows, tool cards, status sheet, any card) | `0.dp` |
| Buttons (SEND, STOP, drawer items, text buttons) | `0.dp` |
| Input fields | `0.dp` |
| Badges | `0.dp` |
| Drawer panel edges | `0.dp` |
| Status dots | **8×8 dp square, `0.dp`** — this is the Live Tile status glyph, not a circle |

**Elevation: zero. Everywhere. No exceptions.** Compose M3 Card adds elevation by default — override with `CardDefaults.cardElevation(defaultElevation = 0.dp)`. No `Modifier.shadow(...)`. No `surfaceTint`.

Layer separation is done through color alone (`TerminalBlack` vs `SurfaceNear` vs `SurfaceElevated`), never through shadow or elevation.

### 15.4 Spacing

8dp base grid. Every margin, padding, gap is a multiple of 8 unless specified otherwise.

| Token | Value | Use |
|---|---|---|
| `EdgeLeft` | **24dp** | The famous Metro asymmetric left margin. Headlines and first-level content hang from this gutter. |
| `EdgeRight` | 16dp | Right margin. Intentionally less than left, per Metro asymmetry. |
| `MessageGap` | 20dp | Space between consecutive message items in the chat timeline. |
| `SectionGap` | 32dp | Space between logical sections (workspace sections in drawer; turn boundaries in chat if rendered). |
| `InsideCard` | 16dp | Padding inside cards and sheet containers. |
| `InlineGap` | 8dp | Between inline elements (icon + label, dot + text, etc.). |
| `DotToLabel` | 8dp | Between a status dot and its label specifically. |

### 15.5 Motion

Motion is restrained but present. Metro's rule: motion is part of the interface, not decoration, but it should never draw attention away from content.

- **Status dot pulse** (Connecting / Reconnecting): alpha `0.3 → 1.0 → 0.3` over **1200ms**, ease-in-out, infinite. Color is `AccentCyan` (Connecting) or `WarningAmber` (Reconnecting).
- **Running strip**: 4dp-tall horizontal bar at the bottom of the top bar area, `AccentCyan` fill with a Metro-style "5 dots sliding right" animation — five 4dp squares traveling left-to-right, 1800ms cycle, each dot offset 360ms from the next. Use `drawBehind` on a simple Box; do not pull in a library.
- **Streaming text append**: each `item/agentMessage/delta` appends immediately; fade the newly-appended region in over **150ms**. No slide, no scale.
- **Drawer open / close**: 200ms ease-out horizontal slide. A customized `ModalNavigationDrawer` is acceptable if shaped and colored per §15.1–15.3. **No** Metro 3D turnstile page transitions — they were cool in 2011 but too heavy for a content-dense chat app.
- **Haptic tick on `turn/completed`**: `HapticFeedbackConstants.CONTEXT_CLICK` — one subtle tick, not a long-press thud.

### 15.6 Composables to replace (hard list for the polish round)

All of these currently exist in their Material 3 default form and must be replaced or deeply customized. Each entry states what exists, what to build, and what test tag to preserve for regression.

1. **`StatusDot`** (currently `CommonComponents.kt`, 12dp rounded square with Material Design 2 hex colors) → 8×8 dp sharp square, `AccentCyan` / `DangerRed` / `WarningAmber` / `TextDim` colors from the new palette, alpha-pulse animation on Connecting / Reconnecting.

2. **`ConnectionChip`** (currently `FilterChip` wrapper) → delete the `FilterChip`. Replace with `Row { StatusDot + 8dp gap + Text(label, Meta typography) }`, clickable, no background, no border, no selection state. Label color: `TextPrimary` when Connected, `WarningAmber` when Reconnecting, `DangerRed` when Offline. Preserve `testTag("connection_chip")`.

3. **`MessageBubble`** (currently `Card` containers for BOTH user and agent, the latter violating UX.md §7.2) → **delete entirely.** Replace with:
   - **`UserMessageLine`**: right-aligned `Text` in `AccentCyan` color, `Body` typography, max 85% width, horizontal padding `EdgeLeft` / `EdgeRight`. No card, no background, no border. Timestamp below in `Meta` typography, `TextMeta` color.
   - **`AgentMessageBlock`**: full-width `Text` in `TextPrimary` color, `Body` typography, horizontal padding `EdgeLeft` / `EdgeRight`. No card, no background, no border. Markdown rendering deferred to post-polish (compose-markdown is a heavy dep); for polish round, do inline-code detection only: segments wrapped in backticks render with `Code` typography and `SurfaceNear` background inline.
   - Preserve existing chat timeline test tags.

4. **`TimelineItemCard` / `CollapsibleToolCard`** (currently one generic component covering `commandExecution`, `fileChange`, `reasoning`, `error`, and unsupported) → split into dedicated composables:
   - **`CommandExecutionCard`**: sharp rectangle, `SurfaceNear` fill, **2dp `AccentCyan` left border**, `EdgeLeft` / `EdgeRight` horizontal padding, `InsideCard` vertical padding. Header row: `▶` glyph as text + command in `Code` typography + status badge on the right (`⟲` / `✓` / `✕` / `⏸`). Sub-header: `cwd` in `Meta` / `TextDim`. Body during run: live tail of last 3 lines in `Code` typography. On completion: collapses to `"exit 0 · 24s"` one-liner; tap to expand full output.
   - **`FileChangeCard`**: sharp rectangle, `SurfaceNear` fill, 2dp `AccentCyan` left border, same padding. Header: `✎ N files changed` in `Body`. Body: table of `path` in `Code` + `+N / -M` in `Meta`. Tap to expand into the unified diff in `Code` typography (plain monochrome, no syntax highlighting).
   - **`ReasoningRow`**: just a single row `"💭 thinking…"` in `Meta` / `TextMeta`. Tap to expand into the streamed summary in `Body`.
   - **`UnsupportedItemRow`**: `"— unsupported item (<kind>) —"` in `Meta` / `TextDim`. Never a card.

5. **`InputRow`** (currently `OutlinedTextField` + `Button` in a `Row`) → replace with a custom text field: `BasicTextField` wrapped in a `Box` with a 1dp bottom border (white at 40% alpha unfocused, `AccentCyan` at 100% focused), `Body` typography, `TerminalBlack` fill. Right side: a sharp rectangular button labeled `SEND` (uppercase, `Body` typography), `AccentCyan` fill, `TerminalBlack` text. When `turn/started` fires, the same button **morphs** in place (content, color, and onClick change — not side-by-side) into `STOP` with `DangerRed` fill and `TerminalBlack` text. The button lingers 500ms after `turn/completed` before flipping back (§4.3). No `OutlinedTextField`, no Material `Button`.

6. **`RunningStrip`** (currently a plain `Text` row with fallback to "agent working…") → replace with a `Column`:
   - Row 1: the "5 dots sliding right" progress animation (§15.5) in a 4dp-tall strip spanning full width.
   - Row 2: item-type-aware status label in `Meta` typography — `thinking…` when the in-flight item is a `reasoning`, `running <command>` when it's a `commandExecution`, `editing N files` when it's a `fileChange`, `responding…` when it's a streaming `agentMessage`. Use the most-recently-started item to decide.

7. **`WorkspaceToneDot`** (currently 10dp `clip(shapes.small)` rounded rectangle with Material Design 2 colors) → same 8×8 dp sharp square as `StatusDot`. These should become the same composable. Colors read from `BclawColors`, not hardcoded hex.

8. **`CircularProgressIndicator`** in `WorkspacePresenceSummary` (currently Material 3 spinning circle for "turn running in this workspace") → replace with a **2-dot horizontal pulse**: two 4×4 dp `AccentCyan` squares that alpha-pulse out of phase over 1200ms. Preserve `testTag("workspace_running_<id>")`.

9. **`WorkspaceDrawerSection`** (currently a `Card` with `surfaceVariant` fill for selected state) → replace with: a `Column` with no card wrapper, a 3dp `AccentCyan` left stripe when `currentWorkspaceId == workspace.id` (via `Modifier.drawBehind`), `SurfaceNear` fill, `0.dp` radius. Selected workspace name text uses `AccentCyan`; otherwise `TextPrimary`.

10. **`BclawStatusSheet`** (currently a rounded `Card` with default elevation mounted at the top) → replace with a sharp-rectangle `Box` dropping from the top, `SurfaceElevated` fill, `0.dp` radius, `0.dp` elevation. Action buttons (Reconnect / Disconnect / Settings) become sharp `AccentCyan`-bordered rectangles with `AccentCyan` text. No `TextButton` defaults.

11. **`WelcomeScreen`** (currently centered `Column` with `headlineSmall` + generic button) → rewrite:
    - Top: a `HeroLight` headline "Connect to your Mac" **left-aligned** from `EdgeLeft`, positioned ~30% down the screen. No centering.
    - Below: a `Body` paragraph in `TextMeta` explaining what happens next, max 70% width, same `EdgeLeft` alignment.
    - Below: a sharp-rectangle `AccentCyan` button labeled `CONTINUE` (uppercase), full width minus `EdgeLeft` / `EdgeRight` padding.
    - Background `TerminalBlack`. This screen is the first impression — it must feel nothing like a Compose sample.

### 15.7 Theme file structure (must be created in the polish round)

The polish round creates the standard Compose theming package at `app/src/main/java/com/bclaw/app/ui/theme/`:

- **`Color.kt`**: all hex values from §15.1 as `val` constants plus a `BclawColors` data class and a singleton `DefaultBclawColors` instance. A `LocalBclawColors` `CompositionLocal` exposes them to composables. **Delete all `MaterialTheme.colorScheme.*` references across the app** and replace with `LocalBclawColors.current.*`.
- **`Type.kt`**: `BclawTypography(hero: TextStyle, title: TextStyle, body: TextStyle, meta: TextStyle, code: TextStyle)` with values from §15.2. Use `FontFamily(Typeface.create("sans-serif-light", Typeface.NORMAL))` for the light-weight family. A `LocalBclawTypography` exposes them. **Delete all `MaterialTheme.typography.*` references** and replace.
- **`Shape.kt`**: a `BclawShape` object with `RoundedCornerShape(0.dp)` constants for any composable that needs a shape parameter.
- **`Theme.kt`**: a composable `BclawTheme(content: @Composable () -> Unit)` that provides `LocalBclawColors` + `LocalBclawTypography` + wraps content in a `MaterialTheme(colorScheme = darkColorScheme(...))` derived from the Terminal Metro palette (so leftover Material composables still render on the right background). Wrap `BclawApp` with this theme at the `MainActivity` level.
- **`themes.xml`**: drop `DayNight`, drop `Theme.Material3.DayNight.NoActionBar` → `Theme.Material3.NoActionBar`. Add `<item name="android:windowBackground">@android:color/black</item>` so the Android shell renders on true black even before the first Compose frame (no white flash on cold start).

### 15.8 Light mode: deferred to v1

v0 is dark-only. Do not ship a `LightBclawColors`. When v1 arrives, add a parallel palette + a theme selector. Light mode is a real deliverable but not a v0 one.

### 15.9 Regression guard

After the polish round, all 5 existing E2E tests must continue to pass unchanged. The polish round may update test selectors that depended on Material 3 component class names (`FilterChip`, `OutlinedTextField`), but the *scenarios* must stay identical.

Additionally, the polish round adds a lightweight **visual invariants test** (`UiInvariantsTest.kt` in `androidTest`) that verifies a few key constants from §15.1–15.3 are actually applied to the rendered tree:

- `StatusDot` is 8.dp × 8.dp with 0 corner radius
- `SEND` button has 0 corner radius
- `Card` composables (where still used) have 0 elevation
- A hero text uses `sans-serif-light`

This is a cheap guard against future rounds reverting to Material defaults.
