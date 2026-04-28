# bclaw

bclaw is an Android remote-desktop client plus a small host agent for pairing and controlling Sunshine on your computer.

Status: `0.0.1` preview. The APK is debug-signed for early open-source testing.

The open-source install path is:

1. Install and start the host service with one command.
2. Download the Android APK.
3. Pair the phone with the host by scanning or pasting a `bclaw2://` URL.

## Quick Start

On the computer you want to control:

```bash
curl -fsSL https://raw.githubusercontent.com/boomyao/bclaw/main/install.sh | bash
```

The installer supports macOS and apt-based Linux hosts, with Ubuntu as the primary Linux target. It downloads bclaw into `~/.bclaw/bclaw`, installs Node.js dependencies for the host agent, installs Sunshine where supported, creates a per-user service, and starts the service.

Then install the preview Android APK on your phone:

[Download latest debug-signed APK](https://github.com/boomyao/bclaw/releases/latest/download/bclaw-android-debug.apk)

Pair the phone:

```bash
~/.bclaw/bclaw/scripts/bclaw-handoff --qr
```

Scan the QR code in the Android app. If QR rendering is not available on your machine, run the command without `--qr` and paste the printed `bclaw2://` URL into the app.

## Installer Options

Pass options to the bootstrapper with `bash -s --`:

```bash
curl -fsSL https://raw.githubusercontent.com/boomyao/bclaw/main/install.sh | bash -s -- --ref v0.0.1
curl -fsSL https://raw.githubusercontent.com/boomyao/bclaw/main/install.sh | bash -s -- -- --port 9000
```

Common environment overrides:

```bash
BCLAW_INSTALL_DIR=~/apps/bclaw \
BCLAW_REF=main \
curl -fsSL https://raw.githubusercontent.com/boomyao/bclaw/main/install.sh | bash
```

Host-agent options are documented in [host-agent/README.md](host-agent/README.md).

## Security Model

Pairing creates a local capability token in `~/.bclaw/host-agent.token`. The Android app sends that token as `Authorization: Bearer ...` on host-agent API calls. Treat pairing URLs like passwords.

Do not expose the host-agent port directly to the internet. Use it on a trusted LAN or private network.

## Repository Layout

- `app/`: Android app.
- `host-agent/`: Node.js host service used by the Android app.
- `scripts/`: installer, pairing, and release helper scripts.
- `design/`: design tokens and wireframes.
- `docs/`: contributor and release documentation.

## Build The APK Locally

```bash
./scripts/build-android-apk
```

The APK is written to `dist/bclaw-android-debug.apk`.

## Development

Android:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Host agent:

```bash
cd host-agent
npm install
node --check server.js
npm start
```

## Current Scope

The MVP path is remote desktop through Sunshine. The host agent manages host status, pairing metadata, Sunshine lifecycle, display selection, session start, and input helper endpoints. Video is streamed directly from Sunshine to the Android app.

## License

MIT. See [LICENSE](LICENSE).
