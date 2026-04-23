# bclaw — v2 UX Design

> Companion to `SPEC_V2.md`. SPEC defines the technical contract; this file defines the product shape. Where the two disagree on anything user-facing, **UX_V2 wins**. v0's `UX_v0_archived.md` is retained for reference only.

---

## 0. Design principles (v2)

1. **The conversation is the product.** Chat is the root; everything else — terminal, remote, files, device switching, capabilities — wraps it as sidecars, tabs, or sheets. Never stacks in front of it as a backstack.

2. **Tabs are the session model.** 5-7 open sessions live as horizontal tabs at all times. Opening a session = tab. Closing = closing the tab. Switching = tap. Agents and projects never collapse into a shared timeline.

3. **One agent per session, locked.** A session is bound to the agent that created it. Switching agent = fork to a new tab. Contexts never silently merge.

4. **Devices are accounts.** A device (Mac / Linux / RPi) is one persona. Switching device = reload the app with that device's tabs. You are never on two devices at once.

5. **Make the remote machine present.** Agent activity on any open tab surfaces in the tab strip — a tab whose session is mid-turn gets a pulse. This is how a muted phone still feels alive.

6. **Banners, not blockers.** Same rule as v0. Offline, auth failed, adapter unreachable — all inline. No modal dialog stops forward motion.

7. **No phantom UI placeholders.** If a feature isn't on the committed v2.x roadmap (SPEC §1), it does not get pixels. Terminal + remote sidecars are on the roadmap and earn their UI; "AI voice mode" is not and does not.

---

## 1. Navigation model

**Tab shell.** The app is a horizontal strip of tabs at the top. The leftmost tab is pinned to "Home" and cannot be closed. Every other tab is a session.

```
┌──────────────────────────────────────────┐
│ 9:41                              5G · 96%│   ← status bar · 28dp
├──────────────────────────────────────────┤
│ [◉] [C] [⌘] [G] [K] [+]           [🔽]  │   ← tab strip · 38dp
├──────────────────────────────────────────┤
│ foo-api · fix-login               [···] │   ← crumb · 52dp
│ ────────────────────                     │
│                                          │
│       (chat message list)                │
│                                          │
│ ─────────────────────                    │
│ ⟲ agent · 3m · running pnpm build        │   ← running strip (turn active)
├──────────────────────────────────────────┤
│ [+] [ type... ]           [@][/]    [▶] │   ← composer · 56dp
├──────────────────────────────────────────┤
│ ▂▂▂ (gesture band · 20dp · OS-reserved) │
└──────────────────────────────────────────┘
```

### 1.1 Tab strip behavior

- **Home tab is always leftmost, pinned, cannot close.** It's a small round cyan dot with a "bclaw" glyph inside. Tapping it returns to the overview (device header + tab list + history).
- **Session tabs** show a 2-letter agent glyph + the first 2-3 chars of session name. Agent color = left 2dp bar on the tab (codex cyan, claude magenta, etc.).
- **Active tab** has a 2dp accent underline under the label.
- **`+`** opens a new blank session on `lastUsedAgent × lastUsedProject`.
- **Close tab** = long-press → sheet with `close` / `duplicate` / `rename`. No per-tab `x` button (not enough space on mobile).
- **Overflow** (>7 tabs): horizontal scroll. Oldest tabs scroll off-left. Never hide; scrollable.
- **Running indicator**: tab whose session is mid-turn gets a 1600ms alpha pulse on the agent color bar. See `design/tokens.css` `@keyframes pulse`.
- **Unread indicator**: tab whose session completed a turn since user last viewed gets a small cyan dot above-right of the label.

System Back does NOT navigate between tabs — it closes sheets / drawers / overlays then exits the app. Tab switching is always explicit.

### 1.2 Overlays (over the tab content)

- **Agent picker sheet** — swipes up from bottom on agent-chip tap. 320ms emphasis ease (`design/ds-motion.html` §C). Backdrop dims chat 40%.
- **Capabilities drawer** — slides in from the right on the `⌇` icon. Shows skills / mcp / commands for the current tab's agent.
- **Device switcher drawer** — slides in from left on the Home tab's device chip. Switching triggers a full app reload.
- **Composer palettes** (`/`, `@`) — appear as a floating card above the composer when triggered. Do not dim chat; keep everything readable.
- **Sidecars** (terminal / remote / files) — not overlays. They live in the main content area in split / peek / sheet modes. See §5.

---

## 2. Screen inventory

1. **Pair screen** (first run + "+ device" from switcher)
2. **Home tab** (always present)
3. **Session tab** (any open session)
4. **Empty session** (new tab, first touch)
5. **Agent picker sheet**
6. **Device switcher drawer**
7. **Capabilities drawer** (skills / mcp / commands)
8. **Composer `/` palette**
9. **Composer `@` palette**
10. **Sidecar · terminal** (stub v2.0)
11. **Sidecar · remote** (stub v2.0)
12. **Sidecar · file-picker** (real v2.0)
13. **Sidecar · files viewer** (real v2.0)
14. **Settings** (devices, theme, about)

### 2.1 Pair screen

First launch, or "+ device" from switcher. Full-screen.

```
┌──────────────────────────────────────────┐
│  Pair a device.                          │
│                                          │
│  Run bclaw-handoff --qr on your Mac,     │
│  then scan the code it displays.         │
│                                          │
│  ┌────────────────────────────────────┐  │
│  │                                    │  │
│  │        [ camera viewfinder ]       │  │
│  │        · cyan corner ticks ·       │  │
│  │        · scanning line ·           │  │
│  │                                    │  │
│  └────────────────────────────────────┘  │
│                                          │
│  Paste URL instead →                     │
│                                          │
└──────────────────────────────────────────┘
```

- Camera permission requested on first entry. Deny → falls back to paste field.
- Scanner decodes `bclaw2://...` → auto-connects. Legacy `bclaw1://` → inline error "Upgrade your Mac-side handoff to v2."
- Paste field is a `MetroUnderlineTextField` (same underline component as v0's `ConnectionScreen`).
- Successful pair → cross-fade to HomeTab.

### 2.2 Home tab

The pinned leftmost tab. No chat. Purpose: device context + open tab list + history.

```
┌──────────────────────────────────────────┐
│ 9:41                              5G · 96%│
├──────────────────────────────────────────┤
│ [◉] [C·fi] [⌘·ra] [+]              [🔽] │
├──────────────────────────────────────────┤
│ mac-mini · 100.64.1.2          [switch ▾]│   ← device chip
│ ─────                                     │
│ 2 agents · 3 projects                     │
│                                           │
│ OPEN                                      │
│ ├ codex · foo-api · fix-login  · ⟲ 3m    │
│ └ claude · sandbox · refactor  · 14m ago │
│                                           │
│ HISTORY                                   │
│ ├ codex · foo-api · earlier today         │
│ ├ codex · bar-web · yesterday             │
│ └ claude · foo-api · 3d ago               │
│                                           │
│ [+ new session]                           │
│                                           │
└──────────────────────────────────────────┘
```

- **Device chip** opens the device switcher drawer. Shows device name + tailnet IP (mono).
- **Open list** = open tabs in this device. Running turns get `⟲` + last-output-line preview under the row.
- **History list** = past sessions not currently in a tab. Tap → open in a new tab.
- **+ new session** = same as `+` in tab strip.

### 2.3 Session tab

The workhorse screen. Per-tab state.

**Header (crumb row, 52dp):**
- Project name + session name (`foo-api · fix-login`), display font, 17/medium.
- Right: `···` overflow → rename / close / move to history / capabilities drawer.
- **Agent chip** is NOT in the header — the header shows project+session. The agent is implied by the tab (color bar + glyph). To switch agent, use the `+` on tab strip.

Wait — correction. The wireframe shows the agent chip in the header. Let me align with the wireframe:

- **Locked agent chip** sits on the LEFT of the crumb row. Shows agent color glyph + name (e.g. `codex ▾`). **Tap = open agent picker = fork to new tab.** It does not swap the current session.
- Long-press the chip = inline help "session is locked to its agent. tap to fork a new tab."

**Message list** scrolls. Items per §3. Auto-scroll follows bottom with 100dp tolerance (inherited from v0 UX §4.4). Floating `↓ N new` chip on scroll-up while new items arrive.

**Running strip** appears above the composer when a turn is active. 4dp-tall 5-dot sliding animation + item-aware label ("thinking…" / "running pnpm build" / "editing 3 files" / "responding…"). Same spec as v0 UX §15.5.

**Composer (56dp collapsed, expands to 160dp multiline):**
- `+` button → sidecar dock (terminal / remote / file-picker / files)
- Text input (body 15/regular)
- `@` button (magenta accent) → skills palette
- `/` button (cyan accent) → commands palette
- `▶` send (cyan fill, morphs to `■` stop during turn)

### 2.4 Empty session (new tab, no messages)

```
┌──────────────────────────────────────────┐
│ codex ▾    foo-api · untitled    [···]  │
│                                          │
│                                          │
│     Say something to codex in foo-api.   │   ← hero 28/light
│                                          │
│     git recently: fix(composer): focus   │   ← starter · mono
│     git recently: feat(api): /threads    │   ← starter · mono
│                                          │
│     /plan   /review   /explain           │   ← command chips
│                                          │
├──────────────────────────────────────────┤
│ [+] [ type… ]              [@][/]    [▶]│
└──────────────────────────────────────────┘
```

Starter chips come from: recent git commits in `cwd` (via ACP `repo/recentCommits`), top-3 agent commands, user-pinned starters.

### 2.5 Agent picker sheet

Invoked from tapping the agent chip on a session tab. Bottom sheet, 320ms emphasis ease.

```
┌──────────────────────────────────────────┐
│ Pick an agent                        [✕] │
│ ─────                                     │
│ Forking foo-api. Current context isn't   │
│ carried.                                  │
│                                           │
│ ● codex · gpt-5.1        (open in tab 2) │   ← already has a tab for this project
│ ● claude · sonnet 3.5    → new tab       │
│ ○ gemini · pro 2.0   not connected       │   ← grayed
│ ○ kimi · k2          not connected       │   ← grayed
│                                           │
└──────────────────────────────────────────┘
```

- **Already open** = tap switches to that tab, no new tab.
- **Connected + not open** = tap creates a new tab in the same project with that agent, opens empty session.
- **Not connected** = disabled row with clear reason ("adapter not installed on mac-mini"). No spinner, no "coming soon" — the wireframe's own `design/WIREFRAME_CHAT.md` calls this out.

### 2.6 Device switcher drawer

Slides in from the left when device chip is tapped (on Home tab).

```
┌──────────────────────┐
│ mac-mini  · active   │
│ 100.64.1.2           │
│ ─────                │
│ ○ dev-linux          │
│ ○ rpi-lab            │
│ ─────                │
│ [+ pair device]      │
│ ─────                │
│ [⚙ settings]         │
└──────────────────────┘
```

**Switching** = shows a full-screen "reloading" state for ~400ms (hero "switching to dev-linux"), tears down active device's session, spins up new one. All tabs are replaced by the new device's persisted tabs. This is closer to account switching than overlay navigation — the user feels a clear context transition.

### 2.7 Capabilities drawer

Slides in from the right on session tab's `···` → Capabilities, or on a dedicated `⌇` icon (future). Three pivot tabs:

- **skills** — @-mentionable procedures. Toggle on/off. Referenced via `@` in composer.
- **mcp** — tool servers. Card list. Tap a server = expand its tools with args schema + enabled toggle.
- **commands** — built-in + project-custom slash commands (`.codex/commands/*.md`).

All three read-only in v2.0. Install / remove / rename is v2.1.

### 2.8 Composer `/` palette

Typing `/` anywhere in composer opens a floating card above. Fuzzy filter as you type. Tap inserts the command + its required args prefilled as `$1`, `$2` placeholders. Esc closes.

### 2.9 Composer `@` palette

Same mechanism, magenta accent. Tap inserts `@skill-name` as a non-editable chip in the input — the chip is part of the message when sent. Agent receives a stamped intent that this turn invokes that procedure.

---

## 3. Message rendering (12 types)

Authoritative visual reference: `design/tokens.css` + `design/project/design/ds-messages.html` (extract into Compose per the spec below).

For each type the following rules apply:
- **No bubbles on agent messages.** The left-aligned text IS the boundary.
- **Left-aligned** = agent. **Right-aligned** = user. **Full-width flat card** = tool-level content (command, diff, mcp, etc.).
- **Left-accent border** is the primary way to signal type:
  - codex cyan / claude magenta / gemini orange / kimi violet / lime for success / orange for warn / red for error.

### 3.1 User message
Right-aligned text in `--accent` color, `.t-body-lg` (15/regular). Max 85% width. Timestamp below in `.t-caption` (`--ink-tertiary`).

### 3.2 Agent message
Left-aligned full-width text in `--ink-primary`, `.t-body-lg`. Inline code detected (backticks) gets `.t-mono` + `--surface-raised` background. Links are accent-colored, tap opens confirm sheet "open example.com?" (no auto-navigation).

### 3.3 Code block
Multi-line fenced code. First 3 lines rendered in `.t-mono` on `--surface-raised` with a 2dp `--accent` left border. Long-press → copy + haptic. Tap → expand to full body inline.

### 3.4 Command execution (tool)
Flat card, `--surface-raised`, 2dp `--accent` left border.

```
▶  pnpm build
   /Users/you/projects/foo
   ⟲ running 12s
   └ compiling 147/520 files…
```

- Header `▶ <command>` in `.t-mono`.
- cwd in `.t-mono` + `--ink-tertiary`.
- Status: `⟲` running (animated alpha pulse) / `✓` done / `✕` failed / `⏸` declined.
- Live tail: last 3 lines, `.t-mono`. Collapses on completion to `"exit 0 · 24s"` one-liner. Tap = expand full output in scrollable sheet.

### 3.5 File diff (tool)
Flat card, 2dp `--accent` left border.

```
✎ 3 files changed
   src/app.ts        +12 -3
   src/lib/main.ts   +1  -1
   README.md         +8  -0
```

- Paths in `.t-mono`. +/- counts in `.t-caption` color-coded (`--diff-add` green / `--diff-rem` red).
- Tap = expand into unified diff, `.t-mono` monochrome. No syntax highlighting in v2.0.

### 3.6 Reasoning
Single-row `💭 thinking…` in `.t-caption` + `--ink-tertiary`. Tap = expand to streamed summary in `.t-body-sm`. Most users will never expand.

### 3.7 Image
Inline tile max 280dp wide, border 1px `--border-subtle`. Tap = full-screen viewer with zoom/pan.

### 3.8 Table
Monospace content. Sharp 1px borders. Horizontal scroll on overflow (no wrapping of cell content). Header row has `--surface-raised` background.

### 3.9 Web search
Card with `--role-agent-3` (orange) 2dp left border. Title (display 17/medium) + source URL (mono 11/tertiary) + 1-2 line snippet.

### 3.10 MCP tool call
Card with `--role-agent-2` (magenta) 2dp left border. Rows: `args.foo = "bar"` (mono), then a divider, then `returns: ...` (mono, syntax-aware where possible). Not a raw JSON dump — structured.

### 3.11 Approval request
Card with `--role-warn` (orange) 2dp left border. Shows what is being asked for ("allow bclaw to run `rm foo.txt`?"). Inline `[deny]` and `[allow]` buttons. In v2.0 we auto-accept and log a warning banner; the card still renders so the user sees what happened.

### 3.12 Todo / plan
Checklist card with `--role-live` (lime) 2dp left border. Each row `✓` done / `⟲` in-progress (animated) / `○` pending. Tap an item = show detail (if any).

### 3.13 Unknown kind
`— unsupported item (<kind>) —` in `.t-caption` + `--ink-tertiary`. Single row. Never a card.

---

## 4. Capabilities (§SPEC_V2 §4, UX layer)

Capabilities attach to **the agent, not the session**. Consequence:

- Skills / mcp / commands are shared across all tabs using the same agent.
- Toggling a skill off affects all tabs with that agent (global).
- "This session's capabilities" is not a concept; only "this agent's capabilities" exists.

This is a deliberate simplification the wireframe chat explicitly decided (`design/WIREFRAME_CHAT.md` line 336: "capability_scope: 全局: 装在 agent 上,所有 session 共享").

---

## 5. Sidecars

Chat stays primary. Sidecars appear in one of three modes chosen per-sidecar:

### 5.1 Split mode (terminal, future)
Divider bar drags between chat (top) and terminal (bottom). Default 50/50. Chat remains scrollable.

### 5.2 Peek mode (remote, future)
Floating card over chat, 2/3 screen height, at the bottom. Top toolbar with `⤢` expand / `✕` close. Chat scroll remains usable behind.

### 5.3 Sheet mode (file-picker, files-viewer — v2.0 real)
Bottom sheet, dragged up from composer's `+`. Multi-select, recents, search. Selected files become a chip row above the composer.

### 5.4 Agent-invoked sidecars
An agent message can include `"sidecar-intent"` metadata. If present, the phone shows a subtle row under the message: `open terminal · "i'll run the build"`. Tap = open that sidecar in the default mode. User can always decline — intent is a suggestion, not an action.

---

## 6. Error and recovery

Same discipline as v0: banners, not blockers. Full table:

| Failure | Surface | User action |
|---|---|---|
| Device ACP socket disconnect | Amber device chip + offline strip on Home | Auto-reconnect; queue outgoing |
| Active device unreachable on app open | Pair screen shows "reconnecting to mac-mini…" | Wait; or switch device |
| Agent adapter crashed | Agent grayed in picker + inline banner in tabs using it | Wait for adapter restart; or fork to another agent |
| Turn failed | Inline red-bordered item in chat | `retry` sends same input |
| Approval auto-accepted | Transient top toast | Informational |
| Sidecar terminal/remote in v2.0 | Clearly-labeled "v2.1" state | — (not an error) |
| Camera denied on Pair | Paste-URL field focused + help text | Enter URL manually |

---

## 7. Typography & theme (v2 tokens)

Locked per `design/tokens.json`. Implementation maps 1:1 to Compose in `ui/theme/`:

### 7.1 Palette (light + dark)

**Light (paper):**
- surface base `#f4f0e3` · raised `#ecead9` · deep `#e4dec8` · overlay `#ffffff`
- ink primary `#0a0a0a` · secondary `#55554a` · tertiary `#76746a` · muted `#9a9788`
- border subtle `#d8d2be` · strong `#76746a`

**Dark (true black):**
- surface base `#000000` · raised `#0a0a0a` · deep `#141410` · overlay `#1c1c16`
- ink primary `#f4f0e3` · secondary `#bab6a3` · tertiary `#9a9788` · muted `#76746a`
- border subtle `#26261c` · strong `#55554a`

**Accent (both themes):** `#00BCF2` (Lumia Cyan)
**Agent colors:** codex cyan / claude magenta `#E3008C` / gemini orange `#F0A30A` / kimi violet `#7B5CF5` / reserved lime `#A4C400`
**Role:** live lime / warn orange / error red `#E51400`

### 7.2 Typography

- **Display + body:** Space Grotesk (300, 400, 500, 600, 700) — shipped as app asset once licensing is confirmed; placeholder = `sans-serif` until then.
- **Mono:** JetBrains Mono (300-700 + italic) — same asset-shipping approach; placeholder = `monospace`.
- **Scale (sp):** display 36, hero 28, h1 24, h2 20, h3 17, body-lg 15, body 13, body-sm 12, caption 11, mono 11, meta 10, micro 9.
- **Weights:** display stays at 300 (light). Body stays at 400 (regular). Medium (500) only for h3 and chips.
- **No italic on display.** Mono italic allowed for agent names in running strip only.

### 7.3 Spacing

4dp base. Tokens: 4 / 8 / 12 / 16 / 20 / 24 / 32 / 40 / 48 / 64. Canonical heights: status 28, tab strip 38, crumb 52, composer 56, gesture 20.

### 7.4 Shape

**Zero radius. Everywhere. No exceptions.** Status dots are 8×8dp sharp squares. Cards are razor rectangles. Buttons are rectangles. Inputs are underlines, not boxes. This is the one rule that cannot drift.

### 7.5 Motion

Three durations: fast 120ms / normal 200ms / slow 320ms. Two easings: standard `cubic-bezier(0.2, 0, 0, 1)` / emphasis `cubic-bezier(0.2, 0, 0, 1.4)`. Stagger 40ms between list items, cap at 6. Live pulse 1600ms loop. Loading dots 1200ms loop with 150ms stagger. Respect `prefers-reduced-motion` — fade-only fallback.

### 7.6 Theme toggle

System-follow by default. Manual override in Settings (`light` / `dark` / `system`). Theme switch is **instantaneous** — no animated color transitions. Per `design/ds-motion.html` §E: "Don't animate colors."

---

## 8. Accessibility

- Tap targets ≥ 44dp (preferred 48dp). Status dots are 8dp but have a larger padded hit region.
- Every gesture has a button equivalent (QR + paste on Pair; tab switch by tap, no mandatory swipe).
- Dynamic type: respect system font scale. Chat messages reflow, never truncate. Tabs truncate to 2-3 chars + ellipsis at scale 1.5+.
- Content descriptions on every icon button.
- Contrast: light mode passes AA at body 13/regular; dark mode passes AAA on `--ink-primary` over `--surface-base`.
- Voice input: system IME only. No custom voice UI.

---

## 9. Out of scope (restated for discipline)

If a PR proposes any of these before the v2.0 acceptance criteria (SPEC §10) all pass, the answer is no:

- iOS
- Voice input
- Tablet layouts
- Multi-user / cloud sync
- In-app skills marketplace
- Thread search (per-tab yes, cross-tab no)
- Custom agent installation UI
- Landscape-optimized layouts
- Push notifications on turn completion
- Session recording / replay

---

## 10. Deliberately deferred within v2.x

These ARE on the roadmap and get UI-shell pixels in v2.0, real functionality in later rounds:

- Terminal sidecar wiring (v2.1)
- Remote desktop sidecar wiring (v2.1)
- Claude-code / Gemini / Kimi ACP adapters (v2.2+)
- Skills / MCP install + remove (v2.1)
- Tab overflow chevron menu (v2.1 — v2.0 horizontal scrolls)
- Composer `+` → attach image (v2.1 — v2.0 attaches files via file-picker only)
- Fork lineage indicator in tab strip (v2.2 — "this tab was forked from tab 2")

Everything in this list has the UX_V2 budget to exist and the SPEC_V2 budget to wire later. That's the distinction from §9 "out of scope."
