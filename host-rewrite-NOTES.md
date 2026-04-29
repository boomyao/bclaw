# bclaw-host Fork-Strip Notes

Status: **landed on 2026-04-29** — real Android client paired and streamed end-to-end via `bclaw-host` on standard Moonlight ports (47984/47989/47990/48010 + 8766 bclaw control). Old Sunshine + Node host-agent stack is no longer needed for the macOS host (kept on disk as rollback channel; see "Cutover plan" below). Latest passing protocol smokes: `verification/reports/20260429T031012Z-host-rewrite-smoke.json` (pair + RTSP via `.delegate/host_rewrite_smoke.js`).

Post-landing fixes applied as fork commits on top of the initial fork-strip:
- `54718055` catalog/status JSON shape: added `installed/stream/display/wake/pairStatus="1"` for Android client compat.
- `495a796a` session/start: real `nvhttp::launch_game_stream_app` call returning `riKey`/`riKeyId`/`sessionUrl0`.
- `feef3ee4` session/start: appid is a string, fix nlohmann type_error.302.

## Cutover plan (not yet executed)

- Old Node host-agent at `host-agent/` and installer `scripts/install-macos-host-agent` retained for rollback.
- `scripts/install-macos-host-native` is the new launchd-based install (uses bclaw-host directly, no brew/node/sunshine at runtime).
- Recommended sweep: after 1–2 weeks of dog-fooding, retire `host-agent/` and `install-macos-host-agent`; rerun §4 perf comparison vs `baselines/sunshine-{idle,streaming}-macos-*.json`.

## Retained Sunshine Modules

- macOS capture/audio/input platform files under `src/platform/macos/*`.
- Moonlight/GameStream HTTP pair and launch flow in `src/nvhttp.cpp`.
- RTSP setup and stream negotiation in `src/rtsp.cpp`.
- Control/video/audio streaming pipeline in `src/stream.cpp`, `src/video.cpp`, `src/audio.cpp`.
- Crypto/certificate persistence in `src/crypto.*`, `src/nvhttp.*`, and the existing `sunshine_state.json` state model.

## Removed/Disabled Modules

- Web UI npm build is disabled with `SUNSHINE_ENABLE_WEB_UI=OFF`.
- macOS build no longer compiles `src/nvenc/*.cpp`; `BCLAW_MACOS_ONLY` provides NVENC link stubs.
- Private FFmpeg CBS code is stubbed on macOS because Homebrew FFmpeg does not ship/export the private CBS API.
- Tray is disabled through `SUNSHINE_ENABLE_TRAY=OFF` and has no-op stubs when not compiled.
- Linux/Windows packaging and platform code remains in the tree but is not part of the macOS target.

## Pairing Entry

Target code entry: `src/nvhttp.cpp`, `pair<T>()`, specifically the `phrase=getservercert` branch.

- Reads token from `Authorization: Bearer <token>`, `X-Bclaw-Token: <token>`, or `?tok=<token>`.
- Compares against `BCLAW_TOKEN_FILE`, `~/.bclaw/host-agent.token`, then legacy `~/.bclaw/ws.token`.
- On success, immediately continues Sunshine's existing `getservercert -> clientchallenge -> serverchallengeresp -> clientpairingsecret` certificate flow, using the bclaw token as the pairing shared secret.
- Sunshine async PIN UI response path is bypassed for bclaw token pairing.

## Endpoint Map

Implemented in `src/bclaw_http.mm` on `BCLAW_HOST_HTTP_PORT` (default `8766`), with the old Node aliases and token shapes:

- `/v1/host/status` -> `/host/status`
- `/v1/host/capabilities` -> `/host/capabilities`
- `/v1/remote/status` -> `/remote/status`
- `/v1/sunshine/status` -> `/remote/sunshine/status`
- `/v1/sunshine/start` -> `/remote/sunshine/start`
- `/v1/sunshine/apps` -> `/remote/sunshine/apps`
- `/v1/sunshine/catalog` -> `/remote/sunshine/catalog`
- `/v1/sunshine/displays` -> `/remote/sunshine/displays`
- `/v1/sunshine/display/select` -> `/remote/sunshine/display/select`
- `/v1/sunshine/session/start` -> `/remote/sunshine/session/start`
- `/v1/sunshine/client/pair` -> `/remote/sunshine/client/pair`
- `/v1/sunshine/pair` -> `/remote/sunshine/pair`
- `/v1/sunshine/prepare` -> `/remote/sunshine/prepare`
- `/v1/sunshine/close` -> `/remote/sunshine/close`
- `/v1/input/macos/pinch` -> `/remote/input/macos/pinch`
- `/v1/input/ai/context` -> `/remote/input/ai/context`
- `/v1/input/ai/text` -> `/remote/input/ai/text`

Protected paths return `401 {"error":"unauthorized"}` on token mismatch. `/remote/input/ai/text` currently returns `501` because this run does not invoke paid/remote AI.

## Entitlements

The build script writes and signs with:

- `com.apple.security.device.audio-input`
- `com.apple.security.cs.allow-jit`
- `com.apple.security.cs.allow-unsigned-executable-memory`
- `com.apple.security.cs.disable-library-validation`

`Info.plist` includes `NSScreenCaptureUsageDescription`, `NSMicrophoneUsageDescription`, and `NSAudioCaptureUsageDescription`. Codesign output shows `flags=0x10002(adhoc,runtime)`.

## Files Changed In Fork

- `CMakeLists.txt`
- `cmake/compile_definitions/common.cmake`
- `cmake/compile_definitions/macos.cmake`
- `cmake/dependencies/Boost_Sunshine.cmake`
- `cmake/dependencies/ffmpeg.cmake`
- `cmake/prep/constants.cmake`
- `cmake/prep/options.cmake`
- `cmake/targets/common.cmake`
- `cmake/targets/macos.cmake`
- `src/bclaw_http.h`
- `src/bclaw_http.mm`
- `src/cbs.cpp`
- `src/config.cpp`
- `src/main.cpp`
- `src/network.cpp`
- `src/nvenc/nvenc_base_bclaw_stub.cpp`
- `src/nvhttp.cpp`
- `src/system_tray.cpp`
- `src_assets/macos/build/Info.plist.in`

## Build Output

- Built app: `dist/bclaw.app`
- Executable: `dist/bclaw.app/Contents/MacOS/bclaw-host`
- Size: `4,881,424` bytes
- `otool -L` lists system frameworks plus Homebrew build-time dylibs for miniupnpc, Boost, OpenSSL, and FFmpeg.
- Hardened runtime adhoc signing passes verification.

## Known Gaps

- True TCP pair + RTSP smoke did not complete because this sandbox denies TCP listeners (`EPERM`) even for `127.0.0.1:3000`.
- The binary is smaller than the requested 30MB+ because it dynamically links Homebrew FFmpeg/Boost/OpenSSL rather than bundling/static-linking them.
- Runtime is not yet Homebrew-independent; `/opt/homebrew/opt/...` dylibs appear in `otool -L`.
- `BCLAW_MACOS_ONLY` stubs private FFmpeg CBS and NVENC paths; full live video frame validation remains open.
- AI text generation endpoint intentionally returns `501`; AX context and pinch are native.

## Next Steps

1. Re-run the same smoke outside this Codex sandbox or in a sandbox mode that permits TCP listeners.
2. Bundle or relink Homebrew dylibs into `Contents/Frameworks` and rewrite install names for runtime independence.
3. Replace FFmpeg CBS stubs with a macOS-compatible static/prepared FFmpeg dependency if live frame SPS rewriting is required.
4. Re-enable platform initialization and run full stream smoke once Screen Recording permission is granted to the signed app/binary.

## Session Start Endpoint Round

- Replaced the native `/v1/sunshine/session/start` stub with launch-plan construction in `src/bclaw_http.mm`: optional JSON body parsing, app selection, display field propagation, 16-byte uppercase `riKey`, random `riKeyId`, old host-agent-style `stream`, `display`, and `gameStream` response fields.
- Added nvhttp internal helpers in `src/nvhttp.cpp`/`src/nvhttp.h` so the control endpoint can create the same GameStream launch session as `/launch`, raise RTSP session state, and return parsed launch XML including `gamesession` and `sessionUrl0`.
- Added an RTSP pending-launch clear helper so repeated `/session/start` calls cannot leave RTSP armed with an older `riKey` while returning a fresh one to Android.
- Added `.delegate/session_start_smoke.js`, which starts an isolated host, POSTs `/v1/sunshine/session/start`, checks `riKey`/`riKeyId`/`sessionUrl0`/`gamesession`, then attempts RTSP `OPTIONS`, `DESCRIBE`, `SETUP audio`, `SETUP video`, `SETUP control`, and `PLAY`.
- Rebuilt `dist/bclaw.app` successfully with `scripts/build-bclaw-host`; fork commit: `495a796a bclaw session start launches gamestream`.
- Verification is currently blocked by this Codex sandbox denying TCP listeners: both the new smoke and existing `.delegate/host_rewrite_smoke.js` fail at host startup with RTSP `bind(...): Operation not permitted`, and a standalone Node `listen(127.0.0.1:3000)` also returns `EPERM`. Latest session-start report: `verification/reports/20260429T035555Z-session-start-smoke.json`.
