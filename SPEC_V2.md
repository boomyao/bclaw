# bclaw v2 Spec

> Status: remote-desktop-first beta architecture. Earlier chat/session/agent-first specs are archived in `SPEC_v0_archived.md`; they are not the active product direction.

## 0. One-line definition

An Android remote desktop client for paired personal hosts. The phone pairs to a host agent, asks it to prepare/manage Sunshine, then connects directly to Sunshine's Moonlight-compatible stream.

## 1. Scope

### In scope for beta

- Android app in Kotlin + Jetpack Compose.
- Pairing through `bclaw2://<host>:<port>?tok=<token>` QR or paste.
- Paired-device list with one active host at a time.
- Full-screen remote desktop overlay.
- Sunshine lifecycle checks, launch, pairing, display selection, session start, close, Wake-on-LAN metadata, and macOS pinch input.
- One-command macOS setup through `scripts/install-macos-host-agent`, including Sunshine install and launchd registration.
- One-command Ubuntu setup through `scripts/install-linux-host-agent` / `scripts/install-ubuntu-host-agent`, including Sunshine install and systemd user service registration.

### Beta boundary

- The beta runtime is one managed `host-agent` service. Node.js is still the implementation runtime; it is installed by the host setup scripts.
- Replacing Node with a compiled single binary is a later packaging/runtime project, not required for this beta architecture.
- Native stream protocol hardening beyond the current Sunshine RTSP/control path.
- Settings surfaces for token rotation and host-agent diagnostics.

### Out of scope

- ACP, Codex, Claude, Gemini, Kimi, chat sessions, tabs, timeline, command palettes, file browser, and agent capability surfaces.
- Cloud relay or multi-user sync.
- iOS.

## 2. Architecture

```text
┌──────────────────────┐        HTTP control         ┌─────────────────────────────┐
│ Android app          │ ───────────────────────────► │ host-agent                  │
│ Pair + device list   │                             │ macOS beta runtime          │
│ Remote overlay       │ ◄─────────────────────────── │ Sunshine control facade     │
└──────────┬───────────┘        JSON status          └──────────────┬──────────────┘
           │                                                        │
           │        Moonlight-compatible stream/control             │
           └───────────────────────────────────────────────────────►│
                                                                    │
                                                           ┌────────▼────────┐
                                                           │ Sunshine        │
                                                           │ RTSP / UDP      │
                                                           └─────────────────┘
```

The host agent is the boundary. Android never shells into the host and never manages Sunshine files directly. Sunshine remains the required streaming backend for this beta.

## 3. Host Agent

Current implementation lives in `host-agent/`.

- Runtime: Node.js 18+ for the current beta cut.
- macOS setup: `scripts/install-macos-host-agent`.
- Ubuntu/Linux setup: `scripts/install-linux-host-agent`; `scripts/install-ubuntu-host-agent` is a named wrapper.
- Legacy setup entry: `scripts/install-macos-bridge`, kept as a compatibility wrapper.
- Launchd label: `dev.bclaw.host-agent`.
- systemd user service: `dev.bclaw.host-agent`.
- Token file: `~/.bclaw/host-agent.token`, with fallback migration from `~/.bclaw/ws.token`.

The host agent owns:

- Sunshine install detection.
- Sunshine process launch.
- Sunshine admin API calls.
- GameStream client certificate/pairing setup.
- Display selection.
- Session launch metadata.
- Wake-on-LAN target discovery.
- macOS input helper compilation/execution.

## 4. API Contract

Base URL is the paired host API base, for example `http://100.64.1.2:8766`.

Primary endpoints:

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/v1/host/status` | Host summary, enabled capabilities, remote status |
| `GET` | `/v1/host/capabilities` | Capability flags |
| `GET` | `/v1/remote/status` | Sunshine-only status shortcut |
| `GET` | `/v1/sunshine/status` | Sunshine installation/process/API status |
| `GET` | `/v1/sunshine/start` | Start Sunshine |
| `GET` | `/v1/sunshine/apps` | Proxy Sunshine apps |
| `GET` | `/v1/sunshine/catalog` | Normalized apps/displays/wake catalog for Android UI |
| `GET` | `/v1/sunshine/displays` | Available displays |
| `POST` | `/v1/sunshine/display/select` | Select Sunshine `output_name` |
| `POST` | `/v1/sunshine/session/start` | Pair if needed, configure display, launch stream session |
| `GET` | `/v1/sunshine/client/pair` | Auto-pair bclaw native client with Sunshine |
| `POST` | `/v1/sunshine/pair` | Proxy Sunshine PIN pairing |
| `GET` | `/v1/sunshine/prepare` | Optional user-provided prepare hook |
| `GET` | `/v1/sunshine/close` | Close current Sunshine app and run optional close hook |
| `POST` | `/v1/input/macos/pinch` | macOS pinch helper |

Legacy `/status`, `/host/*`, and `/remote/*` endpoints remain as compatibility aliases, but new clients should use `/v1`.

## 5. Pairing

Pairing payload:

```text
bclaw2://<host>:<port>?tok=<token>
```

Parser requirements:

- Accept only scheme `bclaw2`.
- Reject `bclaw1`, `http`, `https`, `ws`, `wss`, and malformed inputs.
- Require host, port, and non-empty `tok`.
- Produce `hostApiBaseUrl = http://<host>:<port>`.
- Preserve the token in encrypted app storage, not DataStore JSON.

`scripts/bclaw-handoff --qr` is the canonical producer. It accepts `--host http://host:port` or bare `host:port`; legacy `ws://host:port` input is accepted only to ease migration.

## 6. Android Model

```kotlin
DeviceBook {
  devices: List<Device>
  activeDeviceId: DeviceId?
}

Device {
  id: DeviceId
  displayName: String
  hostApiBaseUrl: String
  token: String
  pairedAtEpochMs: Long
}
```

Stored tokens live in encrypted preferences. DataStore persists token-stripped device metadata.

## 7. Android Structure

```text
com.bclaw.app
├── data
│   ├── BclawV2UrlParser
│   └── DeviceBookRepository
├── domain/v2
│   └── Device / DeviceBook / parser result types
├── net
│   └── BclawJson / NetworkMonitor
├── remote
│   └── Sunshine HTTP, RTSP, video, and control clients
├── service
│   └── BclawForegroundService / BclawV2Controller
├── ui
│   ├── pair
│   ├── devicelist
│   ├── remote
│   ├── components
│   └── theme
└── MainActivity
```

No `net/acp`, `tabshell`, `session`, `showcase`, timeline, command palette, or agent discovery packages belong in the beta remote-desktop-first architecture.

## 8. Connection Lifecycle

1. User installs the host agent with `scripts/install-macos-host-agent` on macOS or `scripts/install-linux-host-agent` on Ubuntu.
2. User generates a QR with `scripts/bclaw-handoff --qr`.
3. Android parses the QR, stores the device, and shows the device list.
4. User opens a device; Android calls `/v1/sunshine/catalog`.
5. If Sunshine is not running, Android calls `/v1/sunshine/start`.
6. If not paired, Android calls `/v1/sunshine/client/pair`.
7. Android calls `/v1/sunshine/session/start`.
8. Android starts RTSP/control/video handling against the returned Sunshine stream metadata.
9. Closing the overlay calls `/v1/sunshine/close` and tears down local stream state.

## 9. Failure Modes

- Host agent unreachable: show a remote connection error and allow retry.
- Sunshine missing: installation should be handled by the host-agent installer; UI reports the missing dependency if the agent still cannot find it.
- Sunshine API unreachable: allow start/retry and show raw status detail.
- Pairing failure: expose the PIN/admin API failure and allow retry.
- Stream setup failure: stop local stream state, keep host paired, allow reconnect.
- Wake-on-LAN failure: non-blocking; stream connection remains the primary path.

## 10. Beta Acceptance

- Fresh macOS host can run one install command: `scripts/install-macos-host-agent`.
- Fresh Ubuntu host can run one install command: `scripts/install-linux-host-agent`.
- The install command installs/validates Node.js, Sunshine, token file, npm dependencies, and the platform service manager.
- `scripts/bclaw-handoff --qr` emits a valid `bclaw2://` URL.
- Android parses the URL into an HTTP `hostApiBaseUrl`.
- Android can list paired devices and open the remote overlay.
- Android uses `/v1` host-agent endpoints for Sunshine catalog, start, pair, display selection, session start, close, and macOS pinch.
- Old `/remote/*` host-agent endpoints still answer as aliases during migration.
- Unit tests pass and host-agent syntax/smoke checks pass.
