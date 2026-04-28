# bclaw v2 UX

> Status: remote-desktop-first beta UX. Earlier chat/session UX is archived in `UX_v0_archived.md` and is no longer authoritative.

## 1. Product Principles

1. The remote machine is the product surface. Pairing, device list, and diagnostics exist to get the user into remote desktop quickly.
2. Pairing must be one visible action on the phone: scan QR or paste one URL.
3. The host setup should feel like a single install action. Any required dependency should be installed or clearly reported by the host-agent installer.
4. No phantom feature surfaces. If chat, agents, files, terminals, or command palettes are not in the current product direction, they do not get UI.
5. Failures should be recoverable from the remote screen: retry host status, start Sunshine, pair Sunshine, reconnect stream, wake host.

## 2. Screens

### 2.1 Pair Screen

Shown when no device is paired, or as an overlay from the device list.

Required controls:

- QR scan entry.
- Paste URL fallback.
- Parse error text for wrong scheme, missing host, missing port, or missing token.

Accepted URL shape:

```text
bclaw2://host:8766?tok=...
```

On success, store the device and move to the device list.

### 2.2 Device List

The normal app home after pairing.

Content:

- List of paired hosts.
- Host display name.
- Host API base URL.
- Pair another device button.

Tapping a device opens the remote desktop overlay.

### 2.3 Remote Desktop Overlay

Full-screen remote surface with a dark operational chrome. It is not a sidecar and does not sit next to chat.

Core states:

- Loading host/Sunshine catalog.
- Sunshine missing or not running.
- Sunshine running but not paired.
- Ready to connect.
- Connecting stream.
- Streaming.
- Stream failed.
- Host unreachable.

Core actions:

- Refresh.
- Start Sunshine.
- Pair Sunshine client.
- Select display.
- Connect.
- Disconnect/close.
- Wake.
- Toggle input mode.
- Show/hide keyboard accessory.

The video area is the primary visual weight. Controls should be compact and avoid obscuring the stream.

## 3. Remote Controls

Pointer modes:

- Click: direct mouse click/tap behavior.
- Trackpad: single-finger pointer movement, two-finger scroll or pinch.
- Scroll: drag maps to scroll wheel.
- Hold: latch primary mouse button for drag selection.

Keyboard:

- Use system IME.
- Provide compact modifier/accessory controls for remote shortcuts.

Stream controls:

- Low-profile stream fallback.
- H.264 fallback.
- Rotation/orientation affordance where needed.

## 4. Error Copy

Use direct operational messages:

- Host agent unreachable.
- Sunshine is not running.
- Sunshine is not paired.
- Display is unavailable.
- Stream failed to start.
- Wake packet sent.

Do not mention ACP, agents, Codex, sessions, tabs, or command transport in user-facing beta UI.

## 5. Install UX

The macOS host install path is:

```bash
scripts/install-macos-host-agent
scripts/bclaw-handoff --qr
```

The Ubuntu host install path is:

```bash
scripts/install-linux-host-agent
scripts/bclaw-handoff --qr
```

The installers should:

- Install or validate Homebrew on macOS, or apt base packages on Linux.
- Install or validate Node.js 18+.
- Install or validate Sunshine.
- Install host-agent npm dependencies.
- Create or migrate the pairing token.
- Register the platform service manager: launchd on macOS, systemd user service on Linux.
- Start Sunshine once for first-run permissions.
- Print the next pairing command.

macOS privacy permissions cannot be automated reliably. The UI and docs should treat Screen Recording, Microphone, and Accessibility as user-granted system permissions.

## 6. Visual Direction

- Dense, functional, remote-control oriented.
- The first screen after pairing is the paired host list, not a marketing page.
- The remote overlay should reserve maximum space for the video surface.
- Cards are only for repeated devices or contained status panels.
- Keep operational text short; do not explain features inside the app.

## 7. Beta Acceptance

- A new user can install the Mac or Ubuntu host agent with one command.
- A new user can scan one QR and see a paired host.
- A paired host opens a full-screen remote desktop surface.
- The app can guide the user through Sunshine start, Sunshine pair, display selection, stream connect, and stream close.
- There is no visible chat/session/agent UI in the beta product path.
