# bclaw-handoff v2

Mint a `bclaw2://` pairing URL for the Android client and hand it off via clipboard or QR.

## One-shot (recommended)

```bash
# one-time setup
openssl rand -hex 32 > ~/.codex/ws.token && chmod 600 ~/.codex/ws.token

# every time you want to pair
scripts/bclaw-handoff --qr
```

With no flags, the script:

- auto-detects your Tailscale IP (falls back to the en0 LAN IP),
- reads the token from `~/.codex/ws.token`,
- reads the available agents from `bridge/agents.json` (currently `claude` · `codex` · `gemini`),
- copies the URL to the clipboard (macOS),
- prints a QR on stderr you can scan from the Pair screen on the phone.

Example output (URL part goes to stdout, everything else to stderr):

```
bclaw2://100.64.1.2:8766?tok=…&agent=claude&agent=codex&agent=gemini
```

## Passing project paths

v2 uses project paths (`cwd`s) to scope sessions to a folder. Zero cwds is valid — the phone defers project selection to the agent picker on first use. To pre-populate, repeat `--cwd`:

```bash
scripts/bclaw-handoff \
  --cwd ~/projects/foo \
  --cwd ~/projects/bar \
  --qr
```

Paths are URL-encoded so spaces and unicode are fine.

## All flags

| Flag | Default | Notes |
| --- | --- | --- |
| `--host <ws://host:port>` | auto Tailscale / LAN | Full ws URL including port |
| `--port <n>` | `8766` | Bridge WebSocket port |
| `--token-file <path>` | `~/.codex/ws.token` | File whose trimmed contents become `tok=…` |
| `--agent <id>` | all keys in `bridge/agents.json` | Repeatable |
| `--cwd <abs-path>` | none | Repeatable · URL-encoded · zero is valid |
| `--qr` | off | Prints an ASCII QR on stderr. Requires `python3 -m pip install qrcode`. |

## Relation to SPEC_V2

This script is the canonical producer of the URLs specified in [`SPEC_V2.md §3`](../SPEC_V2.md). The phone parser (`BclawV2UrlParser`) rejects any other scheme and surfaces a specific error per missing field.

## Security note

The URL carries the capability token in its query string. Treat the minted URL like a password:

- don't paste it into public chat / shared docs,
- prefer QR scan over paste when possible,
- rotate on leak: `openssl rand -hex 32 > ~/.codex/ws.token`, then re-pair.
