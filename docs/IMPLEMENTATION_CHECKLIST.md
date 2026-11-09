# Implementation checklist and verification status

Legend:

- ✅ implemented and covered by automated tests, and/or reproduced on hardware
- 🟡 implemented and builds, but the specific end-to-end path still needs a pass
- ⛔ not verified in this environment

Last hardware pass: Samsung **SM-A217F** (Exynos 850, arm64-v8a, Android 12, 720×1600),
connected through `adb forward tcp:8080 tcp:8080`.

## Functional requirements

| Requirement | Implementation | Status |
| --- | --- | --- |
| Android host app | `:app`, Compose UI, `HostEngine`, `HostStreamingService` | ✅ installed and hosted on the SM-A217F |
| Android viewer app | `:app`, `ViewerEngine`, `RoomClient`, `PlaybackSync` | 🟡 protocol and UI implemented; a second physical device was not available |
| Browser viewer | `web/`, React + TypeScript + Vite | ✅ 52 tests, typecheck and production build; ✅ live browser viewer against the Android host |
| Scan-to-join / share link | `ShareLinks`, QR/copy UI | ✅ LAN and HTTPS share links generated; QR scanning not exercised |
| Original quality only | `ContentUriMediaSource`, raw Range bytes | ✅ no transcoding path |
| Host-device storage | SAF document URI + persistable read grant | ✅ real 15.6 s / 5.2 MB MP4 imported through SAF |
| Real HTTP Range streaming | `RangeParser`, `WatchWaveServer`, `MediaSource` | ✅ `206` with `Content-Range: bytes 100-199/5483269` from the real host |
| Multiple viewers | `RoomManager`, WebSocket hub | ✅ room/sync tests; 🟡 only one simultaneous viewer was exercised on hardware |
| One host per room | `RoomManager` host flag, host-token actions | ✅ room tests |
| Shared play/pause/seek/speed | `SyncEngine`, commands, `CONTROL_POLICY` | ✅ `PLAY`, `PAUSE`, `SEEK`, `SET_SPEED` verified through the real WebSocket |
| Controls can be locked | host controls endpoint + hub broadcast | ✅ policy tests; 🟡 device UI flow not exhaustively exercised |
| WebSocket playback sync | `WebSocketHub`, 15 s heartbeat, state broadcasts | ✅ integration tests; ✅ real host↔browser sync badge and actual frame rendering |
| Clock/drift correction | `DriftController`, `expectedPositionMs`, browser port | ✅ shared + web tests |
| Room chat | `CHAT_SEND`/`CHAT_MESSAGE`/`CHAT_HISTORY`, Android and browser panels | ✅ 10-message/10 s limiter, 500-char limit, 100-message history, server-owned identity; ✅ 2 integration tests, ✅ 52 web tests; 🟡 live room UI pass pending |
| Tunnel abstraction | `TunnelManager`, companion + bundled cloudflared providers | ✅ real Quick Tunnel through Windows companion; ✅ on-device Quick Tunnel with the same binary and CA bundle |
| One-tap public link | bundled arm64 `cloudflared` + Mozilla CA in the APK | 🟡 packaged and executable from the app UID; full in-app start/share flow awaits an unlocked-device pass |
| HTTPS share link | verified `TunnelStatus.activeUrl`, `ShareLinks` | ✅ HTTPS health/room/Range/WS verified through a real tunnel; 🟡 new one-tap UI flow |
| Unique access tokens | 256-bit invite/session/host tokens, HMAC media token | ✅ security tests |
| Versioned API | `/api/v1` + `/api` compatibility alias | ✅ server tests |
| Documented WS protocol | `docs/WEBSOCKET_PROTOCOL.md` | ✅ chat, history and identity rules documented |
| Bounded concurrency | 24 streams, 20 joins/min/IP, 60 WS events/10 s, 12 commands/2 s, 10 chat/10 s | ✅ limiter/sync tests; ⛔ no large load test |
| Background service | Media3 `MediaSessionService` + foreground notification | 🟡 code; ⛔ screen-off device test |
| Room migrations | Room v2 with `MIGRATION_1_2` | 🟡 code; ⛔ migration test on a v1 database |
| No arbitrary filesystem access | SAF grants, asset path validation, no path in API | ✅ only the selected document is opened |
| Tokens/credentials never exposed | memory-only room tokens, hashed sessions, redacted tunnel errors | ✅ design + token tests; 🟡 no full device log audit |
| Configurable app id/base domain | `watchwave.applicationId`, `watchwave.baseDomain` | ✅ Gradle config |
| Docs and open-source files | `README.md`, `docs/*`, `LICENSE`, contribution/security templates | ✅ MIT license and community files added |
| GitHub Actions | `.github/workflows/ci.yml`, `release.yml` | ✅ CI run `42589858867` passed |
| Release/R8 build | `:app:assembleRelease`, release workflow | ✅ builds an unsigned APK in CI; ⛔ signing secrets not configured |

## Automated verification

Environment: GitHub Actions Ubuntu, JDK 17, Node 20, Android platform 34.

| Command | Result |
| --- | --- |
| `:shared:test` | 38 tests passed |
| `:streaming:testDebugUnitTest` | 28 tests passed (including 2 chat integration tests) |
| `:app:assembleDebug` | `BUILD SUCCESSFUL`; APK includes `lib/arm64-v8a/libcloudflared.so` and `assets/tunnel/cacert.pem` |
| Web `npm run typecheck` | passed |
| Web `npm test` | 52 tests across 7 files passed |
| Web `npm run build` | passed |
| CI run `42589858867` | web + Android jobs passed; debug APK artifact uploaded |
| Debug APK | 45,140,165 bytes; SHA-256 `2B9012BB329F14474F2549DE6A1409EED3E8A39B9A652B63F85292C5C3222AEC`; installed on the SM-A217F |
| Bundled cloudflared | version `2026.10.7`; SHA-256 `3DF43AEDC9FF291D1735B5CB576CB97E0B157488796DB2E32B28AECABB445775`; `run-as com.watchwave.app .../lib/arm64/libcloudflared.so --version` succeeds |
| Bundled CA bundle | Mozilla `cacert.pem`, 200,116 bytes; SHA-256 `427C064216761A1E72A8B30F0D954D08CB6E4435A82091FB865AFD20C8497285` |

## Hardware and tunnel results

- Real SAF import and host start succeeded with a 15-second, 5.2 MB MP4.
- `GET /api/v1/health` → `200` with the WatchWave marker.
- Room metadata and join → `200`.
- Video Range request → `206` with `Content-Range: bytes 100-199/5483269`.
- Real WebSocket commands → `PLAY`, `PAUSE`, `SEEK`, `SET_SPEED` confirmed.
- Real browser viewer against the Android host → joined, HTML5 video `readyState=4`,
  duration `15.586771`, an actual frame rendered, play/pause worked.
- Windows companion Cloudflare Quick Tunnel against the Android host → health `200`,
  index `200`, room metadata `200`; browser over HTTPS/WSS `readyState=4`, no media error,
  actual frame, **WSS · host relay** and **IN SYNC**.
- On-device `cloudflared-linux-arm64` v2026.10.7 ran on the SM-A217F with
  `SSL_CERT_FILE` pointing at the Mozilla bundle and produced a temporary
  `https://<random-words>.trycloudflare.com` URL; health, index and room metadata returned
  `200` through that tunnel. The hostname was temporary and has been redacted.
- The new APK packages the same binary and CA bundle. The binary executes from the app's
  native-library directory under the app UID. The full in-app tap-to-start flow was not
  completed because the test device was locked with a PIN at the end of the pass.

## Bugs found and fixed

1. `Player is accessed on the wrong thread` on real hardware → moved `PlaybackSync`
   coroutines and `playback.stop()` to `Dispatchers.Main.immediate`.
2. HEAD `/video` emitted two `Content-Length` headers, causing Cloudflare `502` → fixed and
   covered by regression tests.
3. Tunnel verification used `/api/health` while the canonical route is `/api/v1/health` →
   defaults made consistent.
4. Server heartbeat pongs were treated as errors → `SYNC_PONG` accepted silently.
5. First per-viewer chat design did not exist → added an authenticated, rate-limited,
   server-authoritative room chat with history.
6. Companion-only tunnel onboarding was too much friction → bundled arm64 `cloudflared`
   and the Mozilla CA bundle, with `ON_DEVICE` as the default reachability mode.

## Not verified / known limits

- The updated one-tap tunnel and chat UI were not exercised in a live room on the locked
  test device. The underlying binary, CA bundle, network path, server, chat protocol and
  browser client are verified separately.
- A second physical Android viewer was not available; the Android viewer path is covered by
  protocol tests but not a multi-device hardware run.
- Screen-off playback, long-session/soak behavior, media-permission revocation, Wi-Fi
  changes, port conflicts and a Room v1→v2 migration remain unverified.
- The bundled tunnel is `arm64-v8a` only. Other ABIs must use LAN, a companion URL or a
  custom cloudflared binary.
- Quick Tunnels are free and account-less, but best-effort: no uptime guarantee and a new
  hostname on each start. Use a named tunnel for a permanent hostname.
- The release APK is unsigned/uninstallable as a release until signing secrets are set.
- No large-scale load test has been run.
- “Bug-free” cannot be guaranteed. The project is honest about what is verified and what
  still needs a device/network pass.

## Recommended next steps

1. Unlock the test device and run: add video → **Instant public** → share link → open in
   browser → send chat from both sides → pause/seek/speed → verify history on a second
   browser tab.
2. Repeat with a second Android phone on the same room.
3. Test screen-off playback for at least 30 minutes.
4. Configure release signing and publish a signed release APK.
5. Run a multi-viewer soak test (5–10 browsers, 1–2 hours) and record memory/CPU/network.
