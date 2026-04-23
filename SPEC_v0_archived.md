# bclaw — MVP Spec

> Android-first mobile client that turns a remote Codex `app-server` into a
> session-first chat experience, so you can leave the laptop at home and
> still drive real development work from your phone.

---

## 0. One-line definition

**A session-first Android app that lets a single developer remote-drive `codex app-server` on their Mac over Tailscale, carrying multi-workspace / multi-thread work without losing state when the phone sleeps, switches networks, or is put in a pocket.**

## 1. Scope lock

### In scope (v0, target: ~3 days of agent-assisted build time)

- Android app (Kotlin + Jetpack Compose, single-module to start).
- Manual connection config: host `ws://…`, bearer token, list of workspace `cwd`s.
- Multi-workspace UI (list of configured `cwd`s; each is a folder of threads).
- Per-workspace thread list (new / resume / archive).
- Per-thread chat screen with streaming item rendering, input box, interrupt button.
- Foreground `Service` + notification to keep the WebSocket alive while the app is backgrounded or the screen is locked.
- Automatic reconnect + thread re-subscribe on network change / transient disconnect.
- Persistence of: connection config (host, token, workspaces) and the id of the "current" thread per workspace. **No local mirror of thread history** — always fetch fresh from the server on resume.

### Out of scope (v0)

- iOS.
- In-app QR scanner. v0 uses the `bclaw://` connection string (see §3.1) delivered via clipboard. QR is a v1 feature that layers a camera path on top of the same URL format — no redesign needed when it lands.
- Approval UI — we configure the turn to run without interactive approvals (see §5.3).
- Voice input (use the system IME).
- Fancy diff rendering (plain text + basic code-font monospace).
- File tree / `fs/*` calls.
- Multi-device handoff on the same thread (works incidentally, not a feature).
- Plugin/adapter layer — v0 is hard-coded to Codex. Protocol types are isolated in one module so a future adapter layer is cheap to slot in, but we don't build it now.
- Skills, marketplace, plugins, realtime audio, Windows sandbox, MCP elicitation dialogs, review mode.
- Local notifications for turn completion (phone delivers the chat message; OS push is a v1 feature).
- User accounts, multi-user, cloud sync.

## 2. Architecture

```
┌────────────────────┐        Tailscale (WireGuard)        ┌─────────────────────────┐
│   Android phone    │ ◄──────────────────────────────────► │        Mac              │
│                    │     ws://100.x.y.z:PORT              │                         │
│  bclaw app         │     JSON-RPC 2.0 over WebSocket      │  codex app-server       │
│  (foreground svc)  │     Authorization: Bearer <token>    │  --listen ws://…        │
└────────────────────┘                                      └─────────────────────────┘
```

There is **no custom desktop-side daemon** in v0. The "server" is literally `codex app-server` launched by the user (see §3). The mobile client speaks Codex's wire protocol directly. This is a deliberate bet: stay on rails with upstream Codex so the moving parts are only the Android app + the Codex team's own protocol.

Everything the client needs (session persistence, agent background execution, sandbox, approvals, streaming events) is already implemented in `codex app-server`. Our job is to not get in the way.

## 3. Mac setup (prerequisites — documented, not automated)

These are prerequisites the user performs once on the Mac. Ship a short `MAC_SETUP.md` alongside the app. No installer.

1. Install `codex` CLI (latest).
2. Install Tailscale on Mac and phone, make sure both are in the same tailnet, note the Mac's tailnet IP (e.g. `100.101.102.103`).
3. Generate a capability token file:
   - `openssl rand -hex 32 > ~/.codex/ws.token && chmod 600 ~/.codex/ws.token`
4. Launch the server (manual for v0; can wrap in a `launchd` plist later):
   ```
   codex app-server \
     --listen ws://100.101.102.103:8765 \
     --ws-auth capability-token \
     --ws-token-file ~/.codex/ws.token
   ```
5. Run the `bclaw-handoff` helper to mint a connection string for the phone:
   ```
   $ bclaw-handoff \
       --host ws://100.101.102.103:8765 \
       --token-file ~/.codex/ws.token \
       --cwd /Users/you/projects/foo \
       --cwd /Users/you/projects/bar

   bclaw1://100.101.102.103:8765?tok=4e8f1d2a…&cwd=%2FUsers%2Fyou%2Fprojects%2Ffoo&cwd=%2FUsers%2Fyou%2Fprojects%2Fbar
   (also copied to clipboard on macOS via pbcopy)
   ```
   Ship the URL to the phone via whatever channel is handy (AirDrop, iMessage, 微信 文件传输, email-to-self — treat it like a password, not like a shareable link).

6. On the phone, paste that URL into the single field on the Welcome screen and tap **Connect**. The phone parses the URL, populates host / token / workspaces, runs the handshake, and jumps into Chat. See [`UX.md §2.1`](UX.md) for the full first-run flow.

**Security note:** the Tailscale-internal IP means the socket is only reachable from tailnet peers. Tailscale is the primary perimeter; the capability token is belt-and-suspenders. Do **not** document binding to `0.0.0.0`. The `bclaw://` URL carries the token in its query string — treat it like a password. Don't paste it into public channels, and rotate (`openssl rand -hex 32 > ~/.codex/ws.token && bclaw-handoff ...`) if it ever leaks.

### 3.1 The `bclaw://` connection string

One URL, one paste, one source of truth for pairing. This is the stable primitive; `bclaw-handoff` and (future v1) QR scan are just different transports for this same string.

```
bclaw1://<host>:<port>?tok=<hex-token>&cwd=<url-encoded-absolute-path>[&cwd=...]
```

- **Scheme:** `bclaw1`. Versioned from day one — a future `bclaw2://` can introduce a new payload shape without ambiguity.
- **Authority:** `<host>:<port>` — the Tailscale IP and the `codex app-server` WebSocket port. The phone prefixes `ws://` (eventually `wss://`) when building the real WebSocket URL. Leaving the transport scheme out of the authority keeps the URL shorter and makes `bclaw1` itself the only marker that this is a bclaw pairing payload.
- **`tok` query param:** capability token, hex-encoded. Exactly one value.
- **`cwd` query param:** one absolute workspace path, URL-encoded. **Repeatable.** Each repetition adds one workspace. The phone uses `basename(cwd)` as the default display name; the user can rename from long-press on the workspace row (`UX.md §5.4`).

**Parser contract on the phone:**

- MUST accept zero or more `cwd` params. Zero means "connect now, add workspaces later via the in-app Add Workspace flow."
- MUST reject any URL whose scheme is not exactly `bclaw1` — show an inline parse error, do not silently fall back to plain WebSocket URLs.
- MUST reject `http://`, `https://`, `ws://`, and `wss://` URLs at the top level. Those are wrong-shape inputs and accepting them would mask real user errors.
- MUST preserve unknown query params (forward-compatible for a future `bclaw2` or additional fields) rather than choking on them.

**`bclaw-handoff` itself is un-shipped for the current rounds.** Its first implementation is a ~50-line shell (or Node) script that reads a token file, takes `--host` and one-or-more `--cwd` flags, writes the URL to stdout, and calls `pbcopy` on macOS. It is scheduled into a connection-polish round tracked in §13 (Known debt). Until it exists, the user can hand-construct the URL by reading `~/.codex/ws.token` and composing the query string manually — ugly but unblocking.

## 4. Data model

All Codex-side concepts are authoritative. Our client layer names things after Codex to avoid translation drift.

| bclaw UI term | Codex concept       | Notes |
|---------------|---------------------|-------|
| Workspace     | a `cwd` value       | Purely client-side grouping. `thread/list { cwd }` to populate. |
| Thread        | `Thread`            | A conversation. Lives on the server. |
| Turn          | `Turn`              | One user-input → agent cycle inside a thread. |
| Item          | `ThreadItem`        | Individual renderables (messages, commands, file changes…). |

### 4.1 Client-persisted state (Android local DB, e.g. DataStore or Room)

```
ConnectionConfig {
  host: String            // "ws://100.101.102.103:8765"
  token: String           // capability token, stored via EncryptedSharedPreferences
  workspaces: List<Workspace>
}

Workspace {
  id: UUID                // local only
  displayName: String     // user-picked, defaults to basename of cwd
  cwd: String             // absolute path on Mac
  lastOpenedThreadId: String?  // cache, can be stale
}
```

No thread or item content is persisted. On app launch we always re-fetch from the server. This is a deliberate choice: it keeps the mobile-side storage trivial and makes "source of truth lives on the desktop" a structural invariant.

### 4.2 In-memory runtime state (per connection)

```
Session {
  socket: WebSocket
  nextRpcId: Long
  pendingRequests: Map<Long, CompletableDeferred<JsonRpcResponse>>
  subscribedThreadIds: Set<String>
  currentTurnByThread: Map<String, TurnId?>
}
```

### 4.3 Item types the v0 UI must render

From the Codex protocol README §Events:

- **Always render:**
  - `userMessage` — plain bubble right-aligned.
  - `agentMessage` — plain bubble left-aligned; live-updated from `item/agentMessage/delta` deltas, finalized on `item/completed`.
- **Render as collapsible "tool" cards:**
  - `commandExecution` — show `command`, `cwd`, status badge; expand to show aggregated stdout/stderr. Live stream via `item/commandExecution/outputDelta`.
  - `fileChange` — show list of changed paths with `+N/-M` badges; expand to show unified diff from `turn/diff/updated` (turn-level aggregate). Status badge from the item's own `status`.
  - `reasoning` — collapsed by default, renders `summary` when present. Optional in v0, but cheap to ship. Delta via `item/reasoning/summaryTextDelta`.
- **Ignore in v0** (don't crash, just skip):
  - `plan`, `mcpToolCall`, `collabToolCall`, `webSearch`, `imageView`, `enteredReviewMode`, `exitedReviewMode`, `contextCompaction`, `compacted`.

Unknown item kinds must degrade gracefully to a small gray "unsupported item" placeholder — **never crash on a kind you don't recognize**. Codex adds new item types over time.

## 5. Protocol mapping

All RPC calls are JSON-RPC 2.0 over WebSocket. See the Codex app-server README (linked in §14) for exact parameter shapes. This section documents the *client's* usage — which calls to make, in what order, for each user action.

### 5.1 Connection handshake

On every socket open:

1. Upgrade WebSocket with `Authorization: Bearer <token>` header.
2. Send `initialize` with:
   ```json
   {
     "clientInfo": {"name": "bclaw", "title": "bclaw", "version": "0.1.0"},
     "capabilities": {
       "optOutNotificationMethods": [
         "item/reasoning/textDelta",
         "thread/realtime/outputAudio/delta",
         "fuzzyFileSearch/sessionUpdated",
         "fuzzyFileSearch/sessionCompleted"
       ]
     }
   }
   ```
3. Send `initialized` notification.
4. Any pending subscriptions (current thread per workspace) are re-established by calling `thread/resume` for each.

On backpressure errors (JSON-RPC `-32001` "Server overloaded"): exponential backoff + jitter, retry up to N times, then surface a transient banner.

### 5.2 Opening a workspace

User taps a workspace tile → navigate to Threads list → call:

- `thread/list` with `{ cwd: workspace.cwd, sourceKinds: ["cli", "vscode", "appServer"], archived: false, limit: 50 }`, support cursor pagination on scroll.
- **`sourceKinds` is not optional in practice.** If you omit it (or pass an empty list), the server defaults to interactive-terminal-origin threads only, and threads that bclaw itself created via `thread/start` through the app-server will be **filtered out** of the response. This is real protocol behavior, not a Codex bug. Discovered during Day 1 implementation.

Each returned `thread` has an `id`, a nullable `name`, a non-null `preview`, and a `status`; display the list. Tapping a thread → navigate to Chat screen with that `threadId`.

Thread display name fallback order (for any row in the list): `thread.name` → `thread.preview` → `thread.id.take(12)`.

### 5.3 Starting a new thread

"New thread" button in the thread list:

- `thread/start` with `{ cwd: workspace.cwd, sandbox: "workspace-write", approvalPolicy: "never" }`.
- **Important:** `thread/start` uses the **flat-string** `sandbox` field (not `sandboxPolicy`). Wire values: `"read-only" | "workspace-write" | "danger-full-access"`. `approvalPolicy` wire values: `"untrusted" | "on-failure" | "on-request" | "never"`. The upstream README example showing `"unlessTrusted"` is **stale** — always prefer schema/source over README for enum wire values.
- The goal of this configuration is: the agent can read/write inside `cwd` without ever sending approval requests to the client. As a belt-and-suspenders measure, the client auto-responds `{"decision": "accept"}` to any `item/*/requestApproval` request that sneaks through, and logs a warning; the intent is that the warning never fires in practice.

After `thread/start`, the server auto-subscribes this connection to `turn/*` and `item/*` notifications for that thread. Record the returned `thread.id` in `Workspace.lastOpenedThreadId`.

### 5.4 Resuming an existing thread

Entering the chat screen with a pre-existing `threadId`:

- `thread/resume { threadId }`. Hydrates the thread into memory if it wasn't loaded. The connection is auto-subscribed to its events.
- Fetch history via `thread/read { threadId, includeTurns: true }` to populate the chat backlog. Render items in order.

### 5.5 Sending a user message

User hits send in the chat input:

- `turn/start { threadId, input: [{type: "text", text: "..."}] }`.
- Optimistically render a `userMessage` bubble locally with the text the user typed; reconcile when the server emits the real `userMessage` item.
- From this moment, stream in `turn/started` → zero or more `item/*` → `turn/completed`.

Input includes images later; v0 is text-only.

**Per-turn policy override (documented for future use; v0 does not use it):** `turn/start` uses a **different shape** than `thread/start`. It takes a **tagged-union** `sandboxPolicy` (not the flat `sandbox` string):

- `{"type":"workspaceWrite", "writableRoots":[...], "networkAccess":false, ...}`
- `{"type":"readOnly", ...}`
- `{"type":"externalSandbox", ...}`
- `{"type":"dangerFullAccess"}`

`approvalPolicy` wire values are the same set as in §5.3. **v0 does not override per turn** — thread-level defaults from §5.3 apply to every turn in the thread. This is documented here only so the next round that needs per-turn override does not conflate the two shapes.

### 5.6 Interrupting a running turn

User hits the stop button:

- `turn/interrupt { threadId, turnId }` where `turnId` is the currently-active turn tracked in `Session.currentTurnByThread`.
- Button is only visible while a turn is in progress (track via `turn/started` / `turn/completed`).

The turn ends with a `turn/completed` notification carrying a `turn.status` wire value from the following enum (confirmed against schema during the interrupt round):

- `"completed"` — turn finished naturally
- `"interrupted"` — client-initiated `turn/interrupt` took effect
- `"failed"` — turn errored (see `codexErrorInfo` for specifics)
- `"inProgress"` — only seen on `turn/started`; never appears in terminal `turn/completed`

UI behavior: when `status == "interrupted"`, render the inline "turn interrupted" treatment per [UX.md §9](UX.md). The stop button transitions back to send within ~500ms of the terminal notification, per UX.md §4.3.

### 5.7 Notifications the client must handle

Minimum set:

- `thread/started`, `thread/closed`
- `turn/started`, `turn/completed`, `turn/diff/updated`
- `item/started`, `item/completed`
- `item/agentMessage/delta`
- `item/commandExecution/outputDelta`
- `item/reasoning/summaryTextDelta` (if reasoning rendering is shipped)
- `error` (see Codex README §Errors for `codexErrorInfo` enum values)
- `serverRequest/resolved` (for any approval round-trips we accidentally hit)

Approval-flow notifications (`item/commandExecution/requestApproval`, `item/fileChange/requestApproval`, `mcpServer/elicitation/request`, `item/tool/requestUserInput`, `item/permissions/requestApproval`): see §5.3. Auto-accept with a warning in v0.

## 6. Screens & UX

**For the authoritative product/interaction design, see [`UX.md`](UX.md).** That document defines the real navigation model, screen states, interaction patterns, visual tone, and the key moments that make the product feel mobile-native. Where `UX.md` and this section disagree about anything user-facing, **`UX.md` wins**. This section is kept as a minimal technical summary of the screens Codex needs to implement — UX.md is what drives what they actually look and feel like.

Four screens total. Each should be a top-level composable function.

### 6.1 ConnectionScreen
First-run or "not configured" state. Three fields: host, token, workspaces (add/remove rows of `displayName` + `cwd`). "Connect" button does the handshake end-to-end and, on success, navigates to WorkspaceListScreen.

### 6.2 WorkspaceListScreen
List of configured workspaces. Each row shows displayName, cwd (monospace), and a tiny status dot (connected / reconnecting / offline). Tap to open WorkspaceScreen. Overflow menu → connection settings.

### 6.3 WorkspaceScreen (per cwd)
Thread list for that `cwd`. Infinite scroll via `thread/list` pagination. FAB: "New thread". Each row: thread display name (or id prefix if unnamed), last turn time, a small badge if a turn is currently in progress. Tap → ChatScreen.

### 6.4 ChatScreen (per thread)
- Top bar: workspace name, thread name, connection status indicator.
- Message list: renders items in order per §4.3. Auto-scroll to bottom on new items unless user has scrolled up.
- Input row: text field (multi-line, system IME), send button, stop button (visible only when a turn is running).
- On scroll to top, no pagination in v0 — we always `thread/read` the full thread on entry. If that hits performance issues on long threads, add pagination post-MVP.

## 7. Android app structure

Single Gradle module, packages:

```
com.bclaw.app
├── data              // ConnectionConfig, Workspace DataStore, EncryptedPrefs for token
├── net
│   ├── WebSocketClient          // OkHttp WebSocket wrapper, reconnect logic
│   ├── JsonRpcSession           // request/response correlation, notification dispatch
│   └── codex                    // Codex protocol types (generated if possible, hand-written otherwise)
├── service           // BclawForegroundService hosts the Session across Activity lifecycle
├── domain            // Use-case layer: StartTurn, InterruptTurn, LoadThread, ListThreads…
├── ui
│   ├── connection
│   ├── workspaces
│   ├── threads
│   ├── chat
│   └── components
└── MainActivity
```

**Dependencies to pin early** (let the build agent pick latest compatible versions):
- Kotlin coroutines + Flow
- Jetpack Compose
- OkHttp 4 (for WebSocket)
- kotlinx.serialization (JSON; Codex uses camelCase)
- androidx.datastore (ConnectionConfig persistence)
- androidx.security:crypto (EncryptedSharedPreferences for the token)
- (optional) Ktor-client-websocket if the team prefers, but OkHttp works fine

### 7.1 Codex protocol types

Best path: on first build, run `codex app-server generate-json-schema --out <tmp>` on the developer's Mac, commit the resulting JSON Schema into `net/codex/schema/`, and generate Kotlin data classes from it (e.g. via `json-kotlin-schema-codegen` or by hand). Do not scrape the Rust sources. Hand-writing a minimal subset is also acceptable for v0 — only the method params and items in §4.3 / §5 are required.

### 7.2 Lifecycle ownership

The `JsonRpcSession` and its `WebSocket` live inside the foreground `Service`, not in a ViewModel. Activities bind to the service on create and unbind on destroy. This is the only place that gets the background connection survival we need. Do not put the WebSocket in a ViewModel — configuration changes and Activity finish would kill it.

## 8. Connection lifecycle & state machine

```
  ┌────────┐   start foreground service
  │  Idle  │ ─────────────────────────────┐
  └────────┘                              ▼
                                  ┌───────────────┐
                                  │  Connecting   │
                                  └───────────────┘
                                    │         │
                    initialize OK   │         │ socket error / timeout
                                    ▼         ▼
                            ┌───────────┐   ┌──────────┐
                            │ Connected │   │ Backoff  │
                            └───────────┘   └──────────┘
                                │  ▲                 │
                       socket   │  │ timer elapsed   │
                       closed   ▼  │                 │
                            ┌───────────┐            │
                            │ Reconnect │ ◄──────────┘
                            └───────────┘
```

Rules:
- Reconnect on: socket close, IOException, network change (listen via `ConnectivityManager.NetworkCallback`), returning from Doze.
- Backoff: start at 500ms, double up to 30s, jitter ±20%. Reset on successful `initialize`.
- On each successful reconnect, automatically:
  - Re-`initialize` + `initialized`.
  - Re-`thread/resume` the currently open thread (if any).
  - Do NOT automatically replay the user's last un-ACKed input. If `turn/start` was in flight when the socket dropped, the server may or may not have accepted it — show a "message may not have been sent, retry?" inline affordance instead of silently replaying.
- `-32001` server-overloaded responses: exponential backoff for retryable requests (see Codex README §Protocol).

The client never tears the connection down on its own while the foreground service is alive. Only explicit "Disconnect" (via settings) or the service being stopped releases the socket.

## 9. Failure modes & recovery

| Failure | Detection | Behavior |
|---|---|---|
| Network drop mid-turn | Socket close / read error | Move to `Reconnect`. When back, `thread/resume`; server will continue to stream items for the still-running turn — render whatever we missed. |
| Phone backgrounded / screen off | Android lifecycle | Socket stays alive via foreground service; nothing to do. |
| App killed by OS | Process death | On next launch, reconnect + `thread/resume` of last-opened thread. Accept that delta-frames during the downtime are lost — the server's final `item/completed` is authoritative and will overwrite partial state. |
| Token revoked | `initialize` fails with auth error | Return to ConnectionScreen with a visible error. |
| codex app-server unreachable | WebSocket connect fails | Stay in `Reconnect`, show offline banner. Do not crash. |
| Turn `failed` with `CodexErrorInfo::UsageLimitExceeded` | `turn/completed` with `turn.status = failed` | Show the error message in the chat, do not auto-retry. |
| Turn `failed` with `HttpConnectionFailed` | Same | Show error, offer "retry" button which sends a fresh `turn/start` with the same input. |
| Approval request fires despite §5.3 policy | `item/*/requestApproval` request arrives | Log warning, auto-respond `{"decision": "accept"}`, continue. This is a spec violation but v0 recovery behavior. |
| Unknown item type | Kotlin deserializer falls through | Render a small gray "unsupported item (kind=…)" placeholder. Do not crash. |
| `-32001` server overloaded | JSON-RPC error code | Exponential backoff on the specific request, up to 5 retries, then surface to UI. |

## 10. Acceptance criteria

The MVP is done when **all** of these are true:

1. **Cold start**: open app with no prior config → go through ConnectionScreen → see your workspaces → open a workspace → see existing threads → open a thread → history loads → send a message → see `userMessage` + `agentMessage` streaming → agent completes a turn. End to end, no dev-only affordances.

2. **Multi-workspace parallelism**: two workspaces, each with an active thread running a turn simultaneously. Switching between them shows live-updating progress for both without leaking events across threads.

3. **Interrupt works**: start a long-running turn (e.g. `codex` running a multi-step task), hit stop, turn ends with `status: "interrupted"` within ~2s, UI reflects it.

4. **Network resilience**: put the phone in airplane mode for 30 seconds mid-turn, turn it off. The connection reconnects, the currently-open thread re-subscribes, and any items the server emitted during the gap are reconciled on the next `thread/read` or remaining stream.

5. **Background survival**: send a message that kicks off a ~2-minute agent task. Lock the phone and leave it for the full duration. Unlock. The final `turn/completed` is visible in the chat and the final state matches what the desktop would show.

6. **Workspace-scoped thread filtering**: `thread/list` with `cwd` filter returns only threads created with that `cwd`. The UI never shows threads from a different workspace.

7. **You (the dog-fooder) carry only the phone for one full workday** and complete at least one real code change end-to-end via bclaw, without needing to touch the Mac. This is the ultimate subjective acceptance criterion and it's the one that matters.

## 11. Explicitly out of scope (restated, because it's the MVP's discipline)

If an agent or contributor proposes any of these during build, the answer is no unless 1–10 above already pass:

- In-app QR scanner. v0 uses `bclaw://` connection string paste (see §3.1). QR is a v1 camera transport layered on the same URL format.
- mDNS / Bonjour / tailnet peer auto-discovery.
- Approval UI (tap-to-approve commands or diffs).
- iOS.
- Voice input.
- Rich diff viewer with syntax highlighting.
- File browser (`fs/*`).
- Image input in turns.
- Thread search.
- Notification-triggered push on turn completion.
- Tablet layouts.
- Localization (English + Chinese hardcoded strings are fine, but no resource extraction yet).
- Dark mode polish beyond Compose defaults.

## 12. Resolved decisions (originally open questions, closed during Day 1)

All 6 original open questions were resolved against the real Codex schema and Android docs during the Day 1 build. This section is now the authoritative answer log — not an outstanding work list.

1. **`sandbox` / `sandboxPolicy` / `approvalPolicy` wire values** — resolved. The answer differs by method:
   - `thread/start` → `{ sandbox: "workspace-write", approvalPolicy: "never" }` (flat strings). See §5.3.
   - `turn/start` → `{ sandboxPolicy: {type: "workspaceWrite", ...}, approvalPolicy: "never" }` (tagged union). See §5.5.
   - Upstream README shows a stale `"unlessTrusted"` example; real wire value is `"untrusted"`. Always prefer schema over README.
2. **Thread display name** — resolved. `thread.name` (nullable) and `thread.preview` (non-null) both ride on `thread/list` responses. UI fallback order: `thread.name` → `thread.preview` → `thread.id.take(12)`. See §5.2.
3. **Token usage rendering** — decided. `thread/tokenUsage/updated` is subscribed but **ignored in v0 UI** (logged only). Revisit post-MVP if users want a visible budget meter.
4. **`thread/read` pagination** — resolved. The method has no cursor/page params; it always returns the full thread (with `turns` populated when `includeTurns=true`). v0's "fetch fresh on entry" design is consistent with the real protocol.
5. **Android 13+ notification permission** — resolved. `POST_NOTIFICATIONS` is a runtime permission on API 33+; it does **not** block starting a foreground service, but the app still must post an FGS notification. v0 declares the manifest permission and implements the FGS + channel. Runtime prompt flow is **not yet implemented** and is tracked as debt in §13. Denying the permission does not block connection establishment.
6. **Doze / vendor battery restrictions** — decided. v0 targets stock Android / Pixel behavior. OEM-specific battery-whitelist UX is out of scope for MVP. No code change required.

### Protocol gotchas discovered during Day 1 (not in original question list)

Now baked into §5.2 and §5.3, but logged here for auditability:

- **`thread/list` requires explicit `sourceKinds: ["cli","vscode","appServer"]`** — omitting it filters out threads created via the app-server path, which is exactly the path bclaw uses. Would have made the workspace screen look empty despite threads existing. See §5.2.
- **`thread/start.sandbox` vs `turn/start.sandboxPolicy`** — different field names, different shapes (flat string vs tagged union). They are not interchangeable. See §5.3 and §5.5.

## 13. Known debt (live list)

Debt, open items, and resolved items across all rounds so far. Open items carry forward; resolved items are kept for audit but struck through.

### Open

- **Post-dogfood visual polish batch (queued).** A batch of visual drift items discovered after the UI polish round — each individually fixable but best bundled into a single follow-up round that also absorbs whatever the user finds during acceptance 7 dog-food. Items so far:
  - **`ConnectionScreen` headline is clipped by the system status bar.** `themes.xml` declares `android:statusBarColor = transparent` for edge-to-edge drawing, but `ConnectionScreen.kt`'s root container does not apply `Modifier.statusBarsPadding()` (or equivalent `windowInsetsPadding(WindowInsets.systemBars)`), so the "Connect to your Mac" hero headline draws under the system clock and notification icons — the "C" of "Connect" is eclipsed by the `10:00` clock display. Found via post-polish emulator screenshot audit. One-line fix. `WelcomeScreen` is not affected (its headline sits ~30% down the screen, clear of the status bar); screens with `TopAppBar` are not affected (the app bar provides its own inset). Note: `UiInvariantsTest` does not catch this because it only asserts corner radius, elevation, and font family tokens — not window inset handling. If this batch adds more visual items, consider extending the invariants test to include a "root composable respects `WindowInsets.systemBars`" assertion.
- **`CommonComponents.kt` is now 877 lines.** Way over the 300-line soft cap from SPEC §7. The UI polish round dumped every new Terminal Metro composable (StatusDot, ConnectionChip, Banner, FiveDotMetroProgressBar, TwoDotPulseIndicator, UserMessageLine, AgentMessageBlock, CommandExecutionCard, FileChangeCard, ReasoningRow, UnsupportedItemRow, MetroUnderlineTextField, MetroActionButton, StreamingInlineCodeText, TimelineItemCard dispatcher) into one file. Should be split into logical groupings — suggested layout: `ui/components/status/` (dot, chip, banner), `ui/components/progress/` (FiveDot, TwoDotPulse, RunningStrip), `ui/components/message/` (UserMessageLine, AgentMessageBlock, StreamingInlineCodeText), `ui/components/tool/` (CommandExecutionCard, FileChangeCard, Reasoning / Unsupported rows), `ui/components/input/` (MetroUnderlineTextField, MetroActionButton). Cosmetic cleanup, no functional impact, not urgent.
- **`BclawSessionController` is at 375 lines** — past the 300-line soft cap, approaching the 400-line "split again" threshold from SPEC §7. Grew 297 → 321 → 375 across the interrupt/reconnect/nav rounds, driven first by reconnect-intent routing and then by nav-state orchestration. **If the next round pushes it over 400, block and split before continuing.**
- **`ForegroundServiceTeardownRule` uses `stopService + sleep(300ms)`** for inter-test cleanup. Works with the current 7-test suite but is fragile under growth. If the instrumented suite expands significantly, replace with an observable wait on service-idle / runtime-cleared.
- **Approval fallback for `mcpServer/elicitation/request` and `item/tool/requestUserInput` is best-effort.** Not production-grade. Acceptable while the spec intent is "no approval path should ever fire," but revisit if that assumption breaks.
- **Failure-mode coverage is main-path only.** §9's table is not fully exercised; specifically, `UsageLimitExceeded`, `HttpConnectionFailed` retry UI, and `-32001` backoff-to-banner paths are stubs.
- **SPEC §10 acceptance item 7 is still open.** It is the subjective "carry the phone for a full workday and ship a real change through bclaw" criterion, which can only be validated by the user, not by any agent round. The connection polish round below is the last production-code round before that handoff.

### Resolved

- ~~`BclawSessionController` monolith (~1,150 lines).~~ Reduced to 297 lines during the interrupt + session-split round. Orchestration moved to `domain/usecase/*`; shared state moved to `domain/state/*`. Controller is now a thin lifecycle owner + event router. (Has since crept to 375 lines across the reconnect and nav rounds — see open debt above, still under the 400 "split again" threshold.)
- ~~SPEC §10 acceptance item 1 (cold-start end-to-end).~~ Landed in the cold-start build round; covered by `ColdStartE2eTest`.
- ~~SPEC §10 acceptance item 2 (multi-workspace parallelism).~~ Landed in the nav + multi-workspace round; covered by `MultiWorkspaceE2eTest` (two workspaces, overlapping streaming, independent presence + chat routing, no event cross-contamination). The round also collapsed the old `WorkspaceListScreen → WorkspaceScreen → ChatScreen` backstack into the drawer + chat-primary model specified by `UX.md §1` (both old screens deleted, replaced by `BclawNavState` state holder + `BclawDrawerContent`).
- ~~SPEC §10 acceptance item 3 (interrupt).~~ Landed in the interrupt round; covered by `InterruptE2eTest`.
- ~~SPEC §10 acceptance item 4 (reconnect / network resilience).~~ Landed in the reconnect round; covered by `ReconnectE2eTest` (both idle-disconnect and mid-turn-disconnect paths). Exposed and fixed two real concurrency bugs along the way: late `onClosed()` from a stale socket stepping on the new connection (fixed by current-socket gating in `JsonRpcSession`), and `onTransportReady()` prematurely cancelling the reconnect coroutine before `restoreSubscriptions()` could run (fixed by reordering cleanup after subscription restore).
- ~~UX.md §1 navigation model drift (`WorkspaceListScreen → WorkspaceScreen → ChatScreen` backstack).~~ Resolved in the nav + multi-workspace round. NavHost abandoned in favor of a `BclawNavState` state holder + `BclawDrawerContent` overlay model. Both old screens deleted; Back key only closes drawer / status sheet / connection modal, never walks a thread history. Matches `UX.md §1` end-to-end.
- ~~Reconnect ownership split between `BclawSessionController` and `ConnectionScreen`.~~ Consolidated in the reconnect round: `BclawSessionController` is now the single authoritative owner. `ConnectionScreen` only emits an intent via `BclawForegroundService.reconnectNow()`, and the service routes to `controller.requestReconnectNow()`. In-code comment forbids any future UI from holding its own reconnect state machine.
- ~~`WorkspaceScreen` receives `connectionPhase` but doesn't render it.~~ Resolved in the reconnect round: `connectionPhase` now feeds a new `WorkspacePresenceUi` projection that drives the per-workspace status dot, state label, active-turn spinner, and last-output-line preview (per `UX.md` §5.2).
- ~~Instrumented test teardown requires explicit foreground-service shutdown.~~ Resolved in the reconnect round via `ForegroundServiceTeardownRule` (a shared JUnit `TestRule`).
- ~~Baseline UI was 100% Compose Material 3 defaults with no theming layer (no `Theme.kt`, no `Color.kt`, no `Type.kt`, no `Shape.kt`).~~ Resolved in the UI polish round. Full Terminal Metro token system landed at `ui/theme/` (4 files, 159 lines total) per `UX.md §15`. All 11 composables listed in `UX.md §15.6` replaced with custom `drawBehind`-based sharp-rectangle Compose primitives. `MaterialTheme.colorScheme.*` and `MaterialTheme.typography.*` **completely removed** from app code (verified via grep — only 2 references remain, both inside `Theme.kt` as the intentional bridge for any leftover Material composables). Zero hardcoded hex colors in app UI code. `UiInvariantsTest` added as a visual regression guard asserting 8dp square StatusDot, 0dp corner radius on SEND button, 0dp elevation on cards, and `sans-serif-light` font family on hero text.
- ~~SPEC §10 acceptance item 5 (background survival).~~ Landed in the background survival round. `BackgroundSurvivalE2eTest` (458 lines) runs a 96-second mock-backed long-running turn, backgrounds the activity via `scenario.moveToState(Lifecycle.State.CREATED)`, **polls `BclawRuntime.controller.value?.uiState` while backgrounded to prove delta consumption continues in-memory**, resumes the activity, and asserts the rendered agent text equals a character-level concat of all 90 streamed deltas. Also audited and confirmed: FGS type `dataSync` (required for Android 14+), `setOngoing(true)`, notification title/body dynamically reflect connection phase + active-turn preview via a `combine(controller.uiState, configFlow)` flow.
- ~~Runtime `POST_NOTIFICATIONS` prompt is not implemented.~~ Resolved in the background survival round. `rememberLauncherForActivityResult(RequestPermission())` landed in a dedicated `ui/permissions/NotificationPermissionUi.kt` (131 lines), backed by a `NotificationPermissionRepository` (40 lines) that persists the prompted flag via DataStore. Prompt timing: gated on `config.isConfigured && !connectionSettingsOpen`, so it fires after the first successful connect + Chat entry — not on Welcome. Denial does not block connection per SPEC §12 Q5; instead, a low-emphasis meta line surfaces in `BclawStatusSheet` with a tap-through to system settings. A `NotificationPermissionRule` (50 lines) handles pre-grant/revoke for instrumented tests.
- ~~`BclawForegroundService.ACTION_CONNECT` semantically dead code.~~ Removed in the background survival round as incidental cleanup. Only `BOOTSTRAP` and `RECONNECT_NOW` actions remain.
- ~~SPEC §10 acceptance item 6 (workspace filtering).~~ Landed in the workspace filtering round as a focused verification test. The production implementation has been correct since Day 1 (`thread/list` was always passing `sourceKinds: ["cli", "vscode", "appServer"]` per §5.2), but there was no test proving the behavior. `WorkspaceFilteringE2eTest` now locks it in: three threads in the mock backend across two `cwd`s, client configured with only the alpha workspace, drawer shows exactly the two alpha threads, the beta thread is absent, and the captured `thread/list` request carries both `cwd=/tmp/projects/alpha` and the required three `sourceKinds`. **No production code changed in this round** — the 80-line `ThreadListFilteringFixture.kt` mock extension lives entirely under `androidTest/testing/`.
- ~~`ConnectionScreen` 3-field form (host / token / workspaces) misaligned with `UX.md §2.1` single-paste design.~~ Refactored in the connection polish round: `ConnectionScreen.kt` reduced from 261 → 156 lines, 3 fields removed, replaced with one `MetroUnderlineTextField` backed by `BclawUrlParser`. New supporting UI (`ConnectionClipboardChip`, `ConnectionHelpLink`, `ConnectionInlineError`) lives in a new `ui/components/connection/ConnectionScreenComponents.kt` file — `CommonComponents.kt` was explicitly kept out of the blast radius. `BclawApp` now uses `Crossfade` to transition to Chat only after `connectionPhase == Connected`, so handshake failures keep the user on `ConnectionScreen` instead of half-navigating away. As a bonus, a `ConnectionConfig → bclaw1://...` serializer lets the settings-edit flow show the current config as an editable paste field.
- ~~`bclaw-handoff` Mac helper not yet shipped.~~ Shipped in the connection polish round at `scripts/bclaw-handoff` (executable POSIX shell, 1748 bytes, zero new runtime deps). Supports `--host`, `--token-file` (default `~/.codex/ws.token`), and repeatable `--cwd`; strips the `ws://` prefix, URL-encodes each `cwd`, emits the `bclaw1://` URL to stdout without a trailing newline, and copies to the clipboard via `pbcopy` on macOS (gracefully skips on Linux). A `scripts/bclaw-handoff.md` doc sits next to it with a ready-to-copy token generation + helper invocation example.
- ~~`BclawUrlParser` is not yet shipped.~~ Landed in the connection polish round at `app/src/main/java/com/bclaw/app/data/BclawUrlParser.kt`. Returns a sealed `BclawUrlParseResult.Success(ConnectionConfig) / Error(reason)`. Validated by an 8-case JVM unit test suite in `app/src/test/java/com/bclaw/app/data/BclawUrlParserTest.kt` covering: valid URL with 1 / N / 0 `cwd`; reject `http://`, `ws://`, `https://`, `wss://`; reject unversioned `bclaw://`; reject missing `tok`; accept unknown query params (forward-compat); correct URL decoding of `%2F`-encoded cwd. `./gradlew test` green.

## 14. Reference

- Codex app-server README (protocol is authoritative here):
  `https://github.com/openai/codex/blob/main/codex-rs/app-server/README.md`
- Codex protocol source crates:
  - `codex-rs/app-server-protocol` — JSON-RPC types
  - `codex-rs/app-server` — server implementation
- JSON Schema generation: `codex app-server generate-json-schema --out <dir>`
- TypeScript schema generation: `codex app-server generate-ts --out <dir>`
- Tailscale: https://tailscale.com/download
