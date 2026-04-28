# bclaw-handoff v2

Mint a `bclaw2://` pairing URL for the Android client and hand it off via clipboard or QR.

## One-shot (recommended)

```bash
# one-time setup
mkdir -p ~/.bclaw && openssl rand -hex 32 > ~/.bclaw/host-agent.token && chmod 600 ~/.bclaw/host-agent.token

# every time you want to pair
scripts/bclaw-handoff --qr
```

With no flags, the script:

- auto-detects your Tailscale IP (falls back to LAN IP via macOS `ipconfig` or Linux `ip` / `hostname -I`),
- reads the token from `~/.bclaw/host-agent.token` (falls back to legacy `~/.bclaw/ws.token`),
- emits a remote-desktop pairing URL,
- copies the URL to the clipboard (macOS),
- prints a QR on stderr you can scan from the Pair screen on the phone.

Example output (URL part goes to stdout, everything else to stderr):

```
bclaw2://100.64.1.2:8766?tok=…
```

## All flags

| Flag | Default | Notes |
| --- | --- | --- |
| `--host <http://host:port>` | auto Tailscale / LAN | Host-agent URL including port; legacy `ws://` is accepted |
| `--port <n>` | `8766` | Host-agent port |
| `--token-file <path>` | `~/.bclaw/host-agent.token` | File whose trimmed contents become `tok=…` |
| `--qr` | off | Prints an ASCII QR on stderr. Requires `python3 -m pip install qrcode`. |

## Relation to SPEC_V2

This script is the canonical producer of the URLs specified in [`SPEC_V2.md §3`](../SPEC_V2.md). The phone parser (`BclawV2UrlParser`) rejects any other scheme and surfaces a specific error per missing field.

## Security note

The URL carries the capability token in its query string. Treat the minted URL like a password:

- don't paste it into public chat / shared docs,
- prefer QR scan over paste when possible,
- rotate on leak: `openssl rand -hex 32 > ~/.bclaw/host-agent.token`, then re-pair.
