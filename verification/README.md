# macOS Single-Binary Host — Verification

Acceptance criteria for replacing the current macOS host (Sunshine + Node `host-agent` + osascript + `macos_input_helper`) with a single signed bclaw binary that holds all required TCC permissions itself.

This file is the contract for "done." Each criterion is paired with a concrete verification mechanism. Items blocked on the headless Moonlight test client (Milestone 0) are marked **[BLOCKED-M0]**.

## Goals (the user-visible outcome)

1. End user grants permissions only to **bclaw** — no Sunshine, no node, no osascript entries in System Settings → Privacy & Security.
2. Single binary deliverable — no Homebrew formulas, no Node runtime, no separate helper processes.
3. No regression in functional or performance behavior versus the current Sunshine + Node setup.

## What today's setup actually requires (frozen here so we know what we're removing)

- Homebrew (installed by `scripts/install-macos-host-agent` if missing)
- Node.js 18+
- Sunshine (`brew install sunshine` from LizardByte tap), runs on `127.0.0.1:47990`
- `host-agent/server.js` (~3k LOC, depends on `ws`)
- `macos_input_helper` (compiled C, used for Ctrl+scroll pinch events via CGEvent)
- `osascript` (JXA) — single call site, returns AI input context (`appName`, `windowTitle`, `focusedElement`)

TCC permissions currently demanded:

| Binary | Screen Recording | Accessibility |
|---|---|---|
| Sunshine | required | required |
| osascript | — | required |
| node (host-agent) | — | indirect (via helpers) |
| macos_input_helper | — | required |

Target after rewrite: **one** entry in each of Screen Recording and Accessibility, both labeled `bclaw`.

---

## Acceptance criteria

### 1. Permission surface

| # | Criterion | Verification |
|---|---|---|
| 1.1 | TCC database contains only `bclaw` entries for Screen Recording and Accessibility | `scripts/verify-tcc-clean` (queries user-level TCC.db; requires Full Disk Access on the test runner) |
| 1.2 | Clean macOS first-run shows exactly two TCC dialogs, both labeled "bclaw" | **Manual + screen recording.** No way to suppress or intercept TCC dialogs programmatically. Procedure in `verification/cold-start-runbook.md` (TBD). |
| 1.3 | After uninstall, no `com.bclaw.*` or stale Sunshine/osascript entries linger | Re-run `scripts/verify-tcc-clean` post-uninstall, expect empty result for our app group |

### 2. Dependency footprint

| # | Criterion | Verification |
|---|---|---|
| 2.1 | Single Mach-O binary (or single .app bundle with one executable inside `Contents/MacOS/`) | `find bclaw.app -type f -perm +111 \| wc -l` == 1; `otool -L` shows only system frameworks |
| 2.2 | `install-macos-host-agent` does not install Homebrew, Node, npm, or Sunshine | `grep -E '(brew install\|nvm\|npm\|sunshine)' scripts/install-macos-host-agent` returns no install commands (other than removing the legacy ones) |
| 2.3 | At runtime, only one bclaw process exists; no Sunshine subprocess | `pgrep -af bclaw \| wc -l` == 1; `pgrep -i sunshine` empty |
| 2.4 | No `node_modules/`, no Sunshine cert dir, no host-agent.token shipped with binary | `find bclaw.app -name node_modules -o -name '.sunshine-client'` empty |

### 3. Functional parity (capabilities currently in `server.js`)

Verified by **headless Moonlight test client** (`verification/moonlight-probe`, **[BLOCKED-M0]**) plus host-side fixtures.

| # | Capability | Verification |
|---|---|---|
| 3.1 | Pairing | Probe completes Moonlight pair handshake against bclaw |
| 3.2 | Video stream | Probe receives ≥ 100 decoded frames at requested resolution |
| 3.3 | Audio stream | Probe receives Opus packets continuously for 10s |
| 3.4 | Input injection (mouse / keyboard / scroll) | Probe sends events; host-side fixture app logs received events; assert 1:1 mapping |
| 3.5 | Pinch / Ctrl+scroll (the `macos_input_helper` use case) | Probe sends pinch sequence; host fixture app reads `kCGScrollWheelEventScrollPhase` flags |
| 3.6 | AI input context (`appName`, `bundleId`, `windowTitle`, `focusedElement`) | Open fixture app → call host's `/v1/ai/input-context` (or successor RPC) → assert all four fields populated and stable across 100 polls |
| 3.7 | Multi-display detection | With 2 displays connected, probe enumerates both; display ids match `system_profiler SPDisplaysDataType` |
| 3.8 | All `server.js` HTTP/WS endpoints (`/v1/host/capabilities`, etc.) respond at parity | Run a captured request fixture against both old and new host; assert response shapes match |

### 4. Performance non-regression

All comparisons are versus a Sunshine baseline captured **before** rewrite work begins (`baselines/sunshine-{idle,streaming}-macos-*.json`).

| # | Metric | Verification | Threshold |
|---|---|---|---|
| 4.1 | End-to-end input → display latency p50 | Photo-diode-equivalent: probe injects timestamped input → captures frame containing the rendered timestamp → computes delta | new ≤ 1.10 × baseline |
| 4.2 | E2E latency p95 | same harness | new ≤ 1.10 × baseline |
| 4.3 | Encode quality at fixed bitrate | Stream a 60s reference clip; recover client-side; compute VMAF via `ffmpeg -lavfi libvmaf` | new ≥ baseline − 2.0 VMAF |
| 4.4 | Host CPU under streaming | `top -l 0 -s 1 -pid $PID` sampled 5 min while streaming a reference workload; aggregate p50/p95 | new ≤ baseline |
| 4.5 | Host RSS under streaming | same sample | new ≤ baseline |

4.1–4.3 are **[BLOCKED-M0]** until the test client exists. 4.4–4.5 are doable today against a phone client.

### 5. Cold-start UX (the real product judge)

| # | Criterion | Verification |
|---|---|---|
| 5.1 | A fresh macOS user account, never run bclaw or Sunshine, can go from "double-click installer" to "phone shows desktop and input responds" | Stopwatch on a clean macOS VM (Tart / UTM snapshot); operator runs the documented install path; record screen |
| 5.2 | ≤ 5 user actions and ≤ 3 minutes wall clock from start to first frame | Counted from screen recording |
| 5.3 | All TCC dialogs in the recording display only "bclaw" | Reviewed from screen recording |

This criterion **must not be self-tested by the implementer** (too easy to retrace your own optimal path). Use a fresh user account at minimum, or a non-author tester ideally.

### 6. Linux / Windows non-regression

| # | Criterion | Verification |
|---|---|---|
| 6.1 | Existing Linux install path (`scripts/install-linux-host-agent`, `install-ubuntu-host-agent`) continues to work with Sunshine + thin shell | Existing CI matrix (or manual smoke test on Ubuntu 24.10) passes |
| 6.2 | Windows path unaffected (no Windows install path exists today; goal is just "don't break it later") | Out of scope until Windows host work begins |

### 7. Code signing & notarization

Without these, even a perfect single-binary will produce a janky TCC experience.

| # | Criterion | Verification |
|---|---|---|
| 7.1 | Binary is signed with hardened runtime | `codesign -dv --verbose=4 bclaw.app` shows `flags=0x10000(runtime)` |
| 7.2 | App passes Gatekeeper assessment | `spctl -a -t exec -vv bclaw.app` returns `accepted` |
| 7.3 | Notarization ticket stapled | `xcrun stapler validate bclaw.app` returns `The validate action worked!` |

---

## Baseline capture (do this before writing any new code)

The "non-regression" criteria are meaningless without a Sunshine baseline. Run **once** on the primary dev mac:

```bash
scripts/baseline-macos.sh
```

This captures everything measurable without the test client (TCC entries, dependency footprint, process tree, idle CPU/mem, install footprint). Output goes to `baselines/<target>-<mode>-macos-<hostname>-<YYYY-MM-DD>.json`.

For streaming-load CPU/RSS (criteria 4.4 / 4.5), pair an Android client manually, start streaming a reference workload (e.g., the BBB 1080p60 clip), then run:

```bash
scripts/baseline-macos.sh --mode=streaming --duration=300
```

Latency (4.1/4.2) and VMAF (4.3) baselines are deferred to Milestone 0 (test client).

## Re-verification (after rewrite)

```bash
scripts/baseline-macos.sh --target=bclaw-rewrite           # idle
scripts/baseline-macos.sh --target=bclaw-rewrite --mode=streaming --duration=300
scripts/verify-tcc-clean
codesign -dv --verbose=4 dist/bclaw.app
spctl -a -t exec -vv dist/bclaw.app
xcrun stapler validate dist/bclaw.app
# moonlight-probe ...    [BLOCKED-M0]
```

Comparison reports are written to `verification/reports/<run-id>.md`.

## Roadmap

- **Milestone 0** — Headless Moonlight test client (`verification/moonlight-probe`). Unblocks 3.x and 4.1–4.3. Build before host rewrite, not after.
- **Milestone 1** — Native macOS capture + encode + Moonlight host (replaces Sunshine on macOS only).
- **Milestone 2** — Native AX-based AI input context (replaces osascript JXA).
- **Milestone 3** — Native input injection (subsumes `macos_input_helper`).
- **Milestone 4** — Single-binary distribution + signing/notarization pipeline.
- **Milestone 5** — Remove Node `host-agent` from macOS install path; cut over `install-macos-host-agent`.
