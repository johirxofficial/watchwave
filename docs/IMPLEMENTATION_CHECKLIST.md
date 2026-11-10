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
| Browser viewer | `web/`, React + TypeScript + Vite | ✅ 61 tests, typecheck and production build; ✅ live browser viewer against the Android host |
| Scan-to-join / share link | `ShareLinks`, QR/copy UI | ✅ LAN and HTTPS share links generated; QR scanning not exercised |
| Original quality only | `ContentUriMediaSource`, raw Range bytes | ✅ no transcoding path |
| Host-device storage | SAF document URI + persistable read grant | ✅ real 34.6 s / 5.1 MB MP4 imported through SAF |
| Real HTTP Range streaming | `RangeParser`, `WatchWaveServer`, `MediaSource` | ✅ `206` with `Content-Range` from the real host; live browser stream reached `readyState=4` |
| Multiple viewers | `RoomManager`, WebSocket hub | ✅ room/sync tests; ✅ three participants watched the live room; 🟡 only one browser viewer played media at a time |
| One host per room | `RoomManager` host flag, host-token actions | ✅ room tests |
| Shared play/pause/seek/speed | `SyncEngine`, commands, `CONTROL_POLICY` | ✅ `PLAY`, `PAUSE`, `SEEK`, `SET_SPEED` verified through the real WebSocket |
| Controls can be locked | host controls endpoint + hub broadcast | ✅ policy tests; 🟡 device UI flow not exhaustively exercised |
| WebSocket playback sync | `WebSocketHub`, heartbeat, correlated `STATE_REQUEST` resync, stale-clock pause | ✅ integration tests; ✅ real host↔browser sync badge and actual frame rendering |
| Clock/drift correction | `DriftController`, `expectedPositionMs`, browser port | ✅ shared + web tests; ✅ 12 s stale watchdog on Android and browser |
| Room chat | `CHAT_SEND`/`CHAT_MESSAGE`/`CHAT_HISTORY`, Android and browser panels | ✅ 10-message/10 s limiter, 500-char limit, 100-message history, server-owned identity; ✅ live room showed 11 messages to a joining browser |
| Tunnel abstraction | `TunnelManager`, companion + bundled cloudflared providers | ✅ real Quick Tunnel through Windows companion; ✅ on-device Quick Tunnel with the same binary and CA bundle |
| One-tap public link | bundled arm64 `cloudflared` + Mozilla CA in the APK | ✅ live: **PUBLIC LINK · TUNNEL VERIFIED**, public `/api/v1/health` returned `200` |
| HTTPS share link | verified `TunnelStatus.activeUrl`, `ShareLinks` | ✅ new one-tap UI produced a working HTTPS/WSS room link |
| Unique access tokens | 256-bit invite/session/host tokens, HMAC media token | ✅ security tests |
| Versioned API | `/api/v1` + `/api` compatibility alias | ✅ server tests |
| Documented WS protocol | `docs/WEBSOCKET_PROTOCOL.md` | ✅ chat, history, state-request and identity rules documented |
| Bounded concurrency | 24 streams, 20 joins/min/IP, 60 WS events/10 s, 12 commands/2 s, 10 chat/10 s | ✅ limiter/sync tests; ⛔ no large load test |
| Background service | Media3 `MediaSessionService` + foreground notification | 🟡 code; ⛔ screen-off device test |
| Room migrations | Room v2 with `MIGRATION_1_2` | 🟡 code; ⛔ migration test on a v1 database |
| No arbitrary filesystem access | SAF grants, asset path validation, no path in API | ✅ only the selected document is opened |
| Tokens/credentials never exposed | memory-only room tokens, hashed sessions, redacted tunnel errors | ✅ design + token tests; 🟡 no full device log audit |
| Configurable app id/base domain | `watchwave.applicationId`, `watchwave.baseDomain` | ✅ Gradle config |
| Docs and open-source files | `README.md`, `docs/*`, `LICENSE`, contribution/security templates | ✅ MIT license and community files added |
| GitHub Actions | `.github/workflows/ci.yml`, `release.yml` | ✅ prior CI run `42589858867` passed; ✅ 1.1.1 run `42760442433` passed; ✅ 1.1.2 CI run `42767657360` and release run `42768208907` passed |
| Release/R8 build | `:app:assembleRelease`, release workflow | ✅ builds an unsigned APK in CI; ⛔ signing secrets not configured |

## Automated verification

Environment: GitHub Actions Ubuntu, JDK 17, Node 20, Android platform 34.

| Command | Result |
| --- | --- |
| `:shared:test` | 38 tests passed |
| `:streaming:testDebugUnitTest` | 30 tests passed (including chat, viewer-command and state-request integration tests) |
| `:tunnel:testDebugUnitTest` | 3 tests passed (quick/named cloudflared command and transport flags) |
| `:app:compileDebugKotlin` | `BUILD SUCCESSFUL` after the 1.1.2 tunnel reliability changes |
| `:app:assembleDebug` | `BUILD SUCCESSFUL` in CI; APK includes `lib/arm64-v8a/libcloudflared.so` and `assets/tunnel/cacert.pem` |
| Web `npm run typecheck` | passed |
| Web `npm test` | 61 tests across 8 files passed |
| Web `npm run build` | passed |
| CI run `42589858867` | prior web + Android jobs passed; debug APK artifact uploaded |
| CI run for 1.1.1 | ✅ run `42760442433` passed: browser typecheck/tests/build, 68 Android tests, debug APK artifact |
| GitHub release `v1.1.1` | ✅ published with the CI debug APK (`45,186,733` bytes; SHA-256 `5304EDE464FDDDC89C45A23B447E121FCE62494CFD2BC47630E4C1B11D94CDE7`) |
| CI run for 1.1.2 | ✅ run `42767657360` passed: browser typecheck/tests/build, 71 Android tests (including 3 tunnel tests), debug APK artifact |
| Release workflow `v1.1.2` | ✅ run `42768208907` passed; unsigned release APK artifact uploaded (signing secrets still not configured) |
| GitHub release `v1.1.2` | ✅ published with the CI debug APK (`45,195,521` bytes; SHA-256 `7FD8C0C0D4F8B58ADEB5F00C135D759CC037B54AD01D67A2C21932C93923AF42`) |
| Local `v1.1.2` debug APK | ✅ built locally (`45,228,094` bytes; SHA-256 `C9F96765E3061C978A8E8DF9ACC42D22CFD6FB2C17C9101D6E5AF791F5C075A3`); CI artifact hash will differ |
| Debug APK (v0.1.0) | 45,140,165 bytes; SHA-256 `2B9012BB329F14474F2549DE6A1409EED3E8A39B9A652B63F85292C5C3222AEC`; installed on the SM-A217F |
| Bundled cloudflared | version `2026.10.7`; SHA-256 `3DF43AEDC9FF291D1735B5CB576CB97E0B157488796DB2E32B28AECABB445775`; `run-as com.watchwave.app .../lib/arm64/libcloudflared.so --version` succeeds |
| Bundled CA bundle | Mozilla `cacert.pem`, 200,116 bytes; SHA-256 `427C064216761A1E72A8B30F0D954D08CB6E4435A82091FB865AFD20C8497285` |

## Hardware and tunnel results

- Real SAF import and host start succeeded with a 34.6-second, 5.1 MB MP4.
- `GET /api/v1/health` → `200` with the WatchWave marker.
- Room metadata and join → `200`.
- Video Range request → `206` with `Content-Range`; browser media reached `readyState=4`
  and duration `34.617`.
- Real WebSocket commands → `PLAY`, `PAUSE`, `SEEK`, `SET_SPEED` confirmed.
- Real browser viewer against the Android host → joined as “Browser Tester”, loaded room
  state, participant list (3 watching) and 11 pre-existing chat messages.
- Windows companion Cloudflare Quick Tunnel against the Android host → health `200`,
  index `200`, room metadata `200`; browser over HTTPS/WSS `readyState=4`, no media error,
  actual frame, **WSS · host relay** and **IN SYNC**.
- On-device `cloudflared-linux-arm64` v2026.10.7 ran on the SM-A217F with
  `SSL_CERT_FILE` pointing at the Mozilla bundle and produced a temporary
  `https://<random-words>.trycloudflare.com` URL. The in-app flow reached
  **PUBLIC LINK · TUNNEL VERIFIED**, and the public `/api/v1/health` endpoint returned `200`
  from the PC. The hostname was temporary and has been redacted.
- The browser loaded the streamed video through the on-device HTTPS tunnel at
  `readyState=4`, duration `34.617 s`, while room state and chat history rendered.

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
7. A missed state broadcast could leave a viewer playing a stale clock → added correlated
   `STATE_REQUEST` resync, command confirmation checks, and a 12-second stale-clock pause
   in both browser and Android viewers.
8. A dead WebSocket could stay registered after a failed broadcast → the host now closes
   that socket so the client reconnects instead of drifting silently.
9. Autoplay-blocked browsers showed a “Tap to join playback” button that sent `PAUSE` to the
   whole room when the host was playing → it now starts local playback only.
10. Theater mode duplicated chat/room panels, the bottom-nav Join item did not scroll while
    already on the home page, and the browser toggle knob overflowed its track → all fixed.
11. Android tunnel mode chips overflowed narrow phones, and advanced drift thresholds could
    be saved in the wrong order → chips reflow in two rows and ordering is validated.

## Not verified / known limits

- Only one live room was exercised end to end. Multi-room soak behavior, long sessions and
  repeated rapid reconnect cycles remain unmeasured.
- A second physical Android viewer was not available; the Android viewer path is covered by
  protocol tests but not a multi-device hardware run.
- On-device `cloudflared` logs SELinux `netlink_route_socket bind` denials, and Android may
  kill processes it classifies as phantom. The tunnel worked during verification, but
  long-run stability across OEM firmware is unproven.
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

1. Repeat the live flow with a second Android phone in the same room.
2. Test screen-off playback for at least 30 minutes.
3. Run a multi-viewer soak test (5–10 browsers, 1–2 hours) and record memory/CPU/network.
4. Configure release signing and publish a signed release APK.
