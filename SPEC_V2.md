# bclaw — v2 Spec

> **Status:** v2 greenfield rewrite. v0 is archived in `SPEC_v0_archived.md` / `UX_v0_archived.md`. v0 was a single-Mac, single-codex, drawer-based Compose app that reached MVP 1-6 and entered dog-food acceptance 7 on 2026-04-15. v2 abandons that product shape entirely in favor of the design-handoff wireframe at `design/WIREFRAME_README.md` + `design/WIREFRAME_CHAT.md`.
>
> Where v0 and v2 disagree, **v2 wins**. Where v2 and `UX_V2.md` disagree about anything user-facing, **UX_V2.md wins**. SPEC_V2 defines the wire protocol, data model, and architecture; UX_V2 defines the product shape.

---

## 0. One-line definition

**A tab-based Android client that lets one developer drive multiple agents (codex / claude / gemini / kimi) across multiple paired devices (Macs / Linux boxes / RPi) over Tailscale, with chat as the primary surface and terminal / remote-desktop / file-picker as summonable sidecars.**

## 1. Scope lock

### In scope (v2, target: rebuild over ~3 rounds of agent-assisted work)

- Android app (Kotlin + Jetpack Compose, single-module).
- **Multi-device pairing** via QR scan (primary) + `bclaw://` URL paste (fallback). One paired device is the active "account-like" context at a time.
- **Multi-agent model** via ACP (Agent Client Protocol). v2 ships one real adapter (codex app-server) behind the ACP abstraction; additional agents (claude code, gemini-cli, kimi) are scaffolded in the data model and UI but marked "not connected" until their adapters land.
- **Tab shell**: Home is a pinned left tab; each open session is a tab. 5-7 tabs typical, horizontal scroll on overflow. Tabs persist across app restarts.
- **Zero-step new session**: `+` opens a blank session on the last-used agent + last-used project. Naming is automatic — the agent sets the session name after first turn.
- **Session ↔ agent 1:1 binding**: once a session is created, its agent is locked. "Switch agent" = open a new tab with the same project context under a different agent (fork via the picker).
- **Agent capabilities** (skills / mcp / commands) surfaced on mobile:
  - Skills: `@`-mention palette in composer
  - Commands: `/`-palette in composer
  - MCP: visual card/list replacing `/mcp` CLI — tap to expand tools, toggle on/off
- **Sidecar tools**: terminal, remote-desktop, file-picker, files-viewer summonable from the composer's `+` or the agent's own `"let me check"` intent. Chat stays primary; sidecars ride alongside (split / peek / sheet modes).
- **12 message types** rendered in chat (see §4.3).
- **Foreground service** keeps the ACP connection alive across lock / background / network change.

### Deliberately deferred to later v2.x rounds

- claude code / gemini-cli / kimi ACP adapters (UI shows them as "not connected"; picker grays them out).
- Terminal, remote-desktop, file-picker **implementations**. v2.0 ships the sidecar UI shells + dock + peek chrome, but the actual shells/VNC connections are stubs with a "coming in v2.x" inline state. This is NOT phantom placeholder — these features are on the committed roadmap and each ships a real adapter in a later round. See §13 "Sidecar staging."
- Session recording / replay for terminal (wireframe §07c shows it; v2.0 scaffolds the record button but records nothing).
- Magnifier / d-pad precision mode for remote desktop (wireframe §08c).
- Landscape-optimized layouts.
- Skills marketplace / MCP install flow (v2 only lists what the agent reports; add / remove is v2.1).

### Explicitly out of scope (v2, probably forever)

- iOS. Android single-platform per the design brief.
- Voice input. Users rely on system IME voice.
- Multi-user / team / cloud sync.
- Tablet layouts.
- Push notifications on turn completion (v2.x if relay infra exists).

## 2. Architecture

```
┌────────────────────┐   Tailscale (WireGuard)     ┌─────────────────────────────────────┐
│  Android phone     │ ◄─────────────────────────► │   Device (Mac / Linux / RPi)        │
│                    │   ws://100.x.y.z:8766/<id>  │                                     │
│  bclaw v2 app      │   ACP over WebSocket        │  bridge/server.js  (Node · WS)      │
│  (foreground svc)  │   one agent per connection  │    spawns one agent per path:       │
│                    │                             │    ├─ /claude → claude-code-acp     │
│                    │                             │    ├─ /codex  → codex-acp           │
│                    │                             │    ├─ /gemini → gemini-cli --acp    │
│                    │                             │    └─ /<kimi> → (future)            │
└────────────────────┘                             └─────────────────────────────────────┘
```

### 2.1 ACP (Agent Client Protocol) · the real protocol

The phone speaks **ACP** — Zed's open Agent Client Protocol, the same protocol driven by `claude-code-acp`, `codex-acp`, and `gemini-cli --experimental-acp`. bclaw did not invent this; we are a client of an ecosystem standard. Upstream: https://agentclientprotocol.com.

ACP method surface used by bclaw v2:

| Method | Direction | Use |
|---|---|---|
| `initialize` | client → agent | handshake + capability exchange (`protocolVersion`, `clientCapabilities`, `clientInfo`). Response carries `agentCapabilities` |
| `session/new` | client → agent | create a session for one `cwd` |
| `session/load` | client → agent | hydrate an existing session by id |
| `session/list` | client → agent | enumerate sessions (optional, gated by `agentCapabilities.sessionCapabilities.list`) |
| `session/prompt` | client → agent | send a user turn; streams `session/update` notifications until `stopReason` returns |
| `session/cancel` | client → agent | interrupt in-flight prompt |
| `session/update` | agent → client (notification) | tagged union on `sessionUpdate`: `user_message_chunk` / `agent_message_chunk` / `agent_thought_chunk` / `tool_call` / `tool_call_update` / `plan` / `session_info_update` / `available_commands_update` / `current_mode_update` |
| `session/request_permission` | agent → client (request) | approval prompt for a tool call; client returns an `outcome` |
| `fs/read_text_file`, `fs/write_text_file` | agent → client (request) | ACP client fs capability; v2 exposes these when the file-picker sidecar is open |

v2's 12 message types (UX_V2 §3) are **projections of `session/update` payloads**. The mapping lives in `net/acp/AcpUpdateMapper.kt` (carried forward from v0, unchanged in structure — rebound to v2's type set during Batch 1c).

**"Capabilities" per agent** = `initialize.agentCapabilities` response + the agent's runtime `available_commands_update` notifications. There is no separate `agent/capabilities` RPC; an earlier draft of this SPEC invented one before I re-confirmed the real ACP surface. UX_V2's Capabilities drawer reads the two real sources above.

### 2.2 Bridge (Mac side)

The Mac-side piece is **`bridge/` in this repository** — a ~200-line Node WebSocket server (already committed since v0). It:

1. Accepts `ws://host:8766/<agent-name>` connections.
2. Reads `bridge/agents.json` to resolve which CLI to spawn (`claude-code-acp`, `codex-acp`, `gemini-cli --experimental-acp`, future `kimi`).
3. Spawns the agent CLI as a child process.
4. Proxies each WebSocket text frame → one `\n`-delimited JSON-RPC line into the agent's stdin, and each agent stdout line → one WebSocket text frame.
5. Each WebSocket connection owns its own child agent process; closing the WebSocket kills the agent.

Consequence: **one agent = one WebSocket connection**. Multiple open sessions within the same agent share the same WebSocket (ACP `session/new` returns a `sessionId`; incoming notifications are scoped by that id). Switching agent requires opening a separate WebSocket to `/<other-agent>`.

This reshapes parts of the original §2.1 sketch:

- The phone does not call a per-device `agent/list` RPC. The agent set is **authored in `bridge/agents.json`** and surfaced via two routes:
  - **Static:** the `bclaw2://` pairing URL carries `agent=<name>` query params (authored by `bclaw-handoff v2`).
  - **Dynamic:** the bridge also serves `GET http://host:8766/agents` returning `{ agents: [...] }` — `AcpAgentDiscovery.kt` (v0, carried forward) hits this to refresh after pairing.
- One "device" in bclaw terms = one bridge instance.
- Switching device = close every open agent WebSocket, then reconnect against a different bridge.

### 2.3 Protocol types (phone side)

Lives in `app/src/main/java/com/bclaw/app/net/acp/`:

- `AcpModels.kt` — `initialize` / `session/*` / `session/prompt` / `session/request_permission` shapes. Carried forward from v0 unchanged.
- `AcpUpdateMapper.kt` — `session/update` payloads → UI timeline items. Carried forward; rebound to v2's 12-type model in Batch 1c.
- `AcpAgentDiscovery.kt` — REST probe on the bridge's `/agents` endpoint. Carried forward.

**`net/codex/` is deleted in Batch 1a.** That package held types for codex's native JSON-RPC, used back when the phone connected directly to `codex app-server`. Since bridge + ACP replaced that path, those types have no callers in v2 and the directory is dead weight.

## 3. Device pairing (`bclaw://` → QR transport)

**Primary flow (v2):** user opens bclaw, taps "scan to pair," camera opens, scans a QR the device emits. QR payload IS a `bclaw2://` URL — same primitive as v0's `bclaw1://`, bumped to v2 for ACP + multi-agent:

```
bclaw2://<host>:<port>?tok=<hex-token>&agent=<id>[&agent=...]&cwd=<abs-path>[&cwd=...]
```

- **Scheme:** `bclaw2`. Pairing payloads from `bclaw1://` are **not accepted** by v2 — they pre-date ACP. If a user has a v0 URL, we show an error telling them to re-run `bclaw-handoff --v2` on their Mac.
- **`agent` query param:** one or more agent ids available on this device. Repeatable. Example: `agent=codex&agent=claude-code`.
- **`cwd` query param:** one or more absolute project paths. Repeatable. Each becomes a project selectable when starting sessions.
- **`tok` query param:** shared capability token for the device's ACP gateway.

**Fallback flow:** on the QR scanner screen there is a "paste URL instead" affordance (single input). Same parser, same handshake.

**The Mac-side helper (`bclaw-handoff v2`)** is the primary transport for minting these URLs. v0's script at `scripts/bclaw-handoff` is reworked in Batch 0 to emit `bclaw2://` + include agent discovery.

**Parser contract on the phone:**

- MUST accept zero or more `agent` and `cwd` params (zero = "paired but no agents known yet; fetch via `agent/list` on connect").
- MUST reject `bclaw1://` URLs with an inline error.
- MUST preserve unknown query params.

## 4. Data model

### 4.1 Client-persisted state (DataStore + EncryptedPrefs)

```kotlin
DeviceBook {
  devices: List<Device>
  activeDeviceId: DeviceId
}

Device {
  id: DeviceId                  // local uuid
  displayName: String           // user-rename
  host: String                  // "ws://100.x.y.z:PORT"
  token: String                 // EncryptedPrefs
  knownAgents: List<AgentId>    // last-seen via agent/list
  knownProjects: List<CwdPath>  // last-seen via agent/capabilities or bclaw:// url
  pairedAt: Instant
}

TabBook {                       // per active device
  tabs: List<TabState>          // excludes pinned home tab (always present)
  activeTabId: TabId?
}

TabState {
  id: TabId
  sessionId: SessionId?         // null until first turn (empty session)
  agentId: AgentId              // locked at creation
  projectCwd: CwdPath           // locked at creation
  sessionName: String?          // agent-auto-named after first turn
  unread: Boolean
  lastActivityAt: Instant
}
```

### 4.2 Runtime state

```kotlin
DeviceSession {
  device: Device
  socket: WebSocket
  nextRpcId: Long
  openSessions: Map<SessionId, SessionRuntime>
  openSidecars: Map<SidecarId, SidecarRuntime>
  agentCaps: Map<AgentId, AgentCapabilities>   // cached from agent/capabilities
}
```

**Only the active device** has an open `DeviceSession` at a time. Switching device = tear down current session, spin up new one (UX §2.6: "a device is like an account; switching = reload").

### 4.3 Message types the v2 UI must render

From the wireframe's `ds-messages.html` + composer legend. All 12 must render correctly on a mock stream; the codex adapter only emits 5 of them (userMessage, agentMessage, commandExecution, fileChange, reasoning). The other 7 light up when their adapters arrive.

| Type | Wire name | v2.0 render | Adapter emitting (v2.0) |
|---|---|---|---|
| User message | `userMessage` | Right-aligned, accent color | codex |
| Agent message | `agentMessage` | Left-aligned, no bubble | codex |
| Code block | `codeBlock` | 3-line preview; long-press copy | codex (inside agentMessage) |
| Tool: command | `commandExecution` | Live tail (3 lines) → collapsed summary | codex |
| Tool: file diff | `fileChange` | `+N/-M` header; tap for unified diff | codex |
| Reasoning | `reasoning` | Collapsed `💭 thinking…`; tap expands | codex |
| Image | `imageView` | Inline tile + tap full | — (future) |
| Table | `table` | Sharp borders; mono; horizontal scroll | — (future) |
| Web search | `webSearch` | Orange accent border | — (future) |
| MCP tool call | `mcpToolCall` | Magenta border; structured args/returns card | — (future) |
| Approval req | `approvalRequest` | Orange border; inline deny/allow | — (future, v2 auto-accepts) |
| Todo / plan | `plan` | `✓ ⟲ ○` checklist; lime accent | — (future) |

Unknown kinds render as `— unsupported item (<kind>) —` in muted text. **Never crash.**

### 4.4 Sidecar kinds

| Sidecar | Purpose | v2.0 shipping state |
|---|---|---|
| `terminal` | Full shell in a session's cwd | UI shell + "not connected" inline banner |
| `remote-desktop` | Trackpad / viewport onto the device's display | UI shell + "not connected" inline banner |
| `file-picker` | Select files from cwd to attach to chat | **Real** (via ACP `fs/*` calls) |
| `files-viewer` | Read-only file preview | **Real** |

File-picker is real because chat-level file attachment is on v2.0's critical path. Terminal + remote are visible in the dock / composer + a real sidecar opens when tapped, but the content area shows a clearly-labeled "connecting adapter in v2.1" state. This is on the roadmap per §1 "deliberately deferred" — **not** phantom.

## 5. Protocol mapping (ACP wire)

### 5.1 Connection handshake

On every socket open:

1. Upgrade WebSocket with `Authorization: Bearer <token>`.
2. Send `initialize` with `{ clientInfo, capabilities: { acpVersion: "1", supportedSidecars: ["terminal","remote","file-picker","files-viewer"] }}`.
3. Send `initialized` notification.
4. Immediately call `agent/list` → hydrate `Device.knownAgents`.
5. For each agent the phone has open tabs for, call `agent/capabilities { agentId }` in parallel (cached with 60s TTL).
6. Re-subscribe via `session/resume` for each open session.

### 5.2 Agent discovery

- `agent/list` returns `[{ id, displayName, iconKind, version, capabilitiesVersion }]`.
- `agent/capabilities { agentId }` returns `{ skills: [...], mcpServers: [...], commands: [...] }`.
- Capabilities are **per agent, not per session** — v2's UX_V2 §4 makes this explicit.

### 5.3 Starting a session

Zero-step new-tab flow:

1. User taps `+` on tab strip.
2. Phone creates a local `TabState` with `agentId = lastUsedAgent`, `projectCwd = lastUsedCwd`, no `sessionId` yet.
3. Tab opens in "empty session" UI (hero prompt + quick starters).
4. First `turn/start` triggers a lazy `session/start { agentId, cwd, sandbox: "workspace-write", approvalPolicy: "never" }`.
5. Server returns `sessionId` + auto-subscribes this connection.
6. Server emits `session/named { sessionId, name }` shortly after the first agent turn finalizes — UI updates the tab label.

### 5.4 Switching agents (fork)

1. User taps the agent chip in session header.
2. Agent picker sheet opens (§UX_V2 §5.3).
3. User taps another agent.
4. If an existing tab for that agent already exists → switch to it.
5. Otherwise → open a new tab with `agentId = <picked>`, `projectCwd = <current-tab.cwd>`, empty session.
6. Original tab stays open in the strip; user can return to it.

No in-session agent swap. Ever.

### 5.5 Sidecars

- `sidecar/open { kind, sessionId? }` → returns `{ sidecarId, streamEndpoint? }`.
- `sidecar/close { sidecarId }`.
- Sidecar-specific notifications: `sidecar/terminal/outputDelta`, `sidecar/remote/frame`, `sidecar/files/list`, etc.
- Chat's composer `+` menu and the dock both dispatch the same `sidecar/open` call.

## 6. Screens

Authoritative product design lives in **`UX_V2.md`**. SPEC_V2 lists the screens Kotlin needs to implement:

1. **PairScreen** (QR scan + paste fallback)
2. **HomeTab** (left-pinned; device header, tab overview, history list)
3. **SessionTab** (chat with locked agent chip, composer, sidecar dock)
4. **AgentPickerSheet** (invoked from agent chip)
5. **DeviceSwitcherDrawer** (invoked from device chip; switching = reload)
6. **CapabilitiesDrawer** (skills / mcp / commands for current agent)
7. **ComposerSlashPalette** (invoked by typing `/`)
8. **ComposerAtPalette** (invoked by typing `@`)
9. **SidecarTerminal** (stub until v2.1)
10. **SidecarRemote** (stub until v2.1)
11. **SidecarFilePicker** (real)
12. **SidecarFilesViewer** (real)
13. **SettingsScreen** (device list, token rotation, theme toggle)

## 7. Android app structure

```
com.bclaw.app
├── data
│   ├── DeviceBook              // paired devices + active-device state
│   ├── TabBook                 // per-device tab persistence
│   ├── BclawV2UrlParser        // bclaw2:// parser; rejects bclaw1://
│   └── crypto                  // EncryptedPrefs for tokens
├── net
│   └── acp                     // ACP types, WebSocket client, session runtime
│       ├── AcpSession          // per-device connection lifecycle
│       ├── JsonRpcSession      // correlation + notification dispatch (copied from v0)
│       └── types/              // generated-or-hand-written ACP Kotlin types
├── service
│   └── BclawForegroundService  // one socket at a time (active device)
├── domain
│   ├── usecase/                // StartTurn, InterruptTurn, OpenSidecar, ForkAgent, …
│   ├── state/                  // SessionRuntime, SidecarRuntime, TabRuntime
│   └── model/                  // DeviceId, AgentId, SessionId, TabId, CwdPath…
├── ui
│   ├── pair                    // PairScreen (QR + paste)
│   ├── tabshell                // TabStrip, HomeTab, SessionTab frame
│   ├── session                 // chat list, composer, agent chip
│   ├── message                 // 12 message type composables
│   ├── sidecar                 // dock + each kind (terminal/remote/files/picker)
│   ├── capabilities            // drawer + palettes
│   ├── agent                   // AgentPickerSheet
│   ├── device                  // DeviceSwitcherDrawer
│   ├── settings                // SettingsScreen
│   ├── components              // StatusDot, BclawChip, MetroInput, MetroButton, …
│   └── theme                   // Color.kt / Type.kt / Shape.kt / Theme.kt / Spacing.kt / Motion.kt
└── MainActivity
```

### 7.1 What carries over from v0

- `net/` WebSocket + JSON-RPC correlation logic — generalize from codex-specific to ACP-generic.
- `service/BclawForegroundService` — keep lifecycle; rename and re-point to ACP session.
- `scripts/bclaw-handoff` — rework to emit `bclaw2://`.
- `data/crypto` — EncryptedPrefs reuse.
- `ui/theme/*` — **rewrite** to match wireframe tokens (light + dark + 5 agent colors + Space Grotesk / JetBrains Mono).

### 7.2 What gets deleted from v0

- `ui/connection`, `ui/chat`, `ui/workspaces`, `ui/threads`, `ui/components/connection` — obsolete IA.
- `BclawNavState` — drawer-based navigation is gone; tab shell replaces it.
- `BclawApp` — simpler shell needed for tabs + sidecars.
- `net/codex/*` — replaced by `net/acp/*`; the codex native protocol shim moves device-side.
- All E2E tests under `app/src/androidTest/` that exercised v0 screen paths. Retain the `JsonRpcSession` unit tests as a baseline for ACP's JSON-RPC correlation layer.

## 8. Connection lifecycle

Same state machine as v0 (`Idle → Connecting → Connected → Backoff → Reconnect`), but scoped to **active device only**. Switching devices fully tears down the old `DeviceSession` and spins up a new one. The foreground service notification reflects which device is active.

## 9. Failure modes

Delegated to `UX_V2.md §6` (banners-not-blockers discipline unchanged from v0). Protocol-level failures (`-32001`, auth rejection, adapter not reachable) surface in the status sheet + inline banners.

## 10. Acceptance criteria for v2.0

The v2.0 MVP is done when **all** of these hold:

1. **Pair via QR**: open app → scan a device's QR (from `bclaw-handoff --qr` or a `bclaw2://` URL) → land on HomeTab with that device active.
2. **Start a session on codex**: tap `+`, type a prompt, agent responds, session gets named, tab label updates.
3. **Fork to another agent (mocked)**: tap agent chip → picker shows all 4 agents; codex is enabled, the other 3 are grayed "not connected" with a clear label — **no phantom ping-spinner**; picking codex opens a second tab.
4. **Capabilities drawer**: open drawer from session → see skills / mcp / commands pivots with real data from `agent/capabilities` for codex; other agents' pivots show "not connected."
5. **Composer `/` and `@`**: typing `/` opens the commands palette; `@` opens skills palette; both filter as you type; tapping inserts.
6. **Sidecars**: tapping the `+` in composer opens a file-picker that actually selects files and attaches them to the next turn. Tapping terminal or remote in the dock opens the sidecar frame with a clearly-labeled "v2.1" state — not a fake terminal.
7. **Switch device**: settings → switch → confirm → app reloads with the new device's tabs. Old tabs hidden until switched back.
8. **Message types**: all 12 renderers work against a canned mock stream (unit test); the 5 codex emits show correctly in real runs.
9. **Dark & light themes**: system-follow works; manual override in settings; tab switch between themes is instant (no animated color).
10. **Carry the phone one full workday** and ship at least one real code change via bclaw v2 + codex (inherited dog-food criterion from v0).

## 11. Known debt (live list)

### Open (v2 greenfield — expected to grow quickly)

- Batch 0 (this document) written; Batch 1 (tokens port + delete v0 UI) in flight.
- Font loading: `Space Grotesk` and `JetBrains Mono` need to ship as app assets (not a Google Fonts runtime dependency — opens privacy + offline questions). Batch 0 ships placeholder mappings to `sans-serif-light` / `monospace` until the real fonts land.
- ACP protocol spec is currently **defined only in this SPEC**. A versioned JSON schema at `net/acp/schema/` needs to exist before Batch 2 wires the real wire layer. Batch 1 can proceed against mock flows.
- Permissions: `CAMERA` for QR scan is new in v2; runtime prompt landing in Batch 1.
- `bclaw-handoff v2` script update pending.

### Retired (v0 acceptance + polish items — no longer tracked here)

See `SPEC_v0_archived.md §13` for the v0 debt ledger. None carries forward; v2 is a clean slate.

## 12. References

- Wireframe handoff: `design/WIREFRAME_README.md` + `design/WIREFRAME_CHAT.md`
- Token source of truth: `design/tokens.json` (machine-readable) + `design/tokens.css` (CSS reference)
- ACP: spec pending; interim wire shape defined in §5 above.
- Codex app-server (for gateway reference): `https://github.com/openai/codex/blob/main/codex-rs/app-server/README.md`

---

## Appendix · v0 → v2 mental model delta

Kept here so future rounds don't reflexively re-implement v0 concepts.

| v0 concept | v2 replacement |
|---|---|
| ConnectionScreen (paste `bclaw1://`) | PairScreen (scan QR; paste fallback) |
| WorkspaceListScreen, WorkspaceScreen (deleted in v0's nav round) | HomeTab (leftmost pinned tab) |
| Drawer with workspaces + threads | Tab strip + tab overflow behavior |
| Single chat root | Multiple chat tabs, one per session |
| `thread`, `thread/list`, `thread/start` | `session`, per-agent `session/start` |
| Single workspace `cwd` | Project = `cwd` scoped under an agent |
| Codex as the only agent, hard-coded | 4 agents via ACP; codex is just one of them |
| Status chip in top bar | Status chip in device header (home tab), status bar on session tab shows agent chip |
| Connection sheet | Device switcher + device detail |
| No sidecars | Terminal / remote / file-picker / files as sidecar frames |
| Slash-commands out of scope | `/` + `@` palettes in composer are a Batch 2 deliverable |
| Skills / MCP out of scope | Capabilities drawer in Batch 2 |
