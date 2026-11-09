# Implementation checklist and verification status

Legend:

- ✅ implemented and covered by automated tests
- 🟡 implemented and compiled/built, but not exercised on real hardware or a real tunnel
- ⛔ not verified or not available in this environment

## Functional requirements

| Requirement | Implementation | Status |
| --- | --- | --- |
| Android host app | `:app`, Compose UI, `HostEngine`, `HostStreamingService` | 🟡 compiles/assembles; no device run |
| Android viewer app | `:app`, `ViewerEngine`, `RoomClient`, `PlaybackSync` | 🟡 compiles/assembles; no device run |
| Browser viewer | `web/`, React 18 + TypeScript + Vite | ✅ 49 tests, typecheck and build pass; ⛔ no live-host browser E2E |
| Original quality only | `ContentUriMediaSource`, raw Range bytes | ✅ no transcoding code path |
| Host-device storage | SAF document URI + persistable read grant | 🟡 code complete; real SAF provider not exercised |
| Real HTTP Range streaming | `RangeParser`, `WatchWaveServer`, `MediaSource` | ✅ unit/integration tests; ⛔ no device/network throughput test |
| Multiple viewers | `RoomManager`, WebSocket hub | ✅ room/sync tests; 🟡 Android multi-device not exercised |
| One host per room | `RoomManager` host flag, host-token actions | ✅ room tests |
| Shared play/pause/seek/speed | `SyncEngine`, commands, `CONTROL_POLICY` | ✅ sync tests |
| Controls can be locked | host controls endpoint + hub broadcast | ✅ policy logic tested; 🟡 UI/device flow not |
| WebSocket playback sync | `WebSocketHub`, 15 s heartbeat, state broadcasts | ✅ 4 integration tests (including heartbeat pong) |
| Clock/drift correction | `DriftController`, `expectedPositionMs`, browser port | ✅ shared + web tests |
| Tunnel abstraction | `TunnelManager`, companion + cloudflared providers | ✅ real Cloudflare Quick Tunnel against server harness; 🟡 Android host not exercised |
| HTTPS share link | verified `TunnelStatus.activeUrl`, `ShareLinks` | ✅ HTTPS health/Range/WS verified in harness; 🟡 app UI onboarding not |
| Unique access tokens | 256-bit invite/session/host tokens, HMAC media token | ✅ security tests |
| Versioned API | `/api/v1` + `/api` compatibility alias | ✅ server tests |
| Documented WS protocol | `docs/WEBSOCKET_PROTOCOL.md` | ✅ docs added |
| Bounded concurrency | 24 streams, 20 joins/min/IP, 60 WS events/10 s, 12 commands/2 s | ✅ tests for limiters/sync; 🟡 no load test |
| Background service | Media3 `MediaSessionService` + foreground notification | 🟡 code; ⛔ screen-off device test |
| Room migrations | Room v2 with `MIGRATION_1_2` | 🟡 code; ⛔ migration test on a v1 database |
| No arbitrary filesystem access | SAF grants, asset path validation, no path in API | ✅ code review; ✅ static path validation tests not explicit |
| Tokens/credentials never exposed | memory-only room tokens, hashed sessions, redacted tunnel errors | ✅ design + tests for tokens; 🟡 no device log audit |
| Configurable app id/base domain | `watchwave.applicationId`, `watchwave.baseDomain` | ✅ Gradle config; 🟡 not tested in a device build |
| Docs | `README.md` + `docs/*` | ✅ written |
| GitHub Actions | `.github/workflows/ci.yml`, `release.yml` | ✅ CI and release runs passed on GitHub |
| Release/R8 build | `:app:assembleRelease`, release workflow | ✅ builds unsigned APK in CI; ⛔ not signed or hardware-tested |

## Automated verification performed locally and in CI

Environment: Windows, JDK 17.0.20.1, Android SDK with platform 34, Node 26, Gradle 8.7.

| Command | Result |
| --- | --- |
| `.\gradlew.bat :shared:test` | 38 tests passed |
| `.\gradlew.bat :streaming:testDebugUnitTest` | 26 tests passed in CI; local full-suite rerun OOM on the 8 GB host |
| `.\gradlew.bat :app:compileDebugKotlin` | passed |
| `.\gradlew.bat :app:assembleDebug` | `BUILD SUCCESSFUL`, 99 tasks |
| `.\gradlew.bat :app:assembleRelease` | local run not verified: Gradle daemon OOM on the 8 GB host during R8 |
| CI run `42577294247` | passed: web tests/build, 64 Android tests, debug APK artifact |
| Release run `42569281967` | passed on `ubuntu-latest`: R8 release APK built |
| Cloudflare Quick Tunnel | Windows `cloudflared` → Ktor harness: health 200, browser index 200, room metadata 200, join 200, Range `206` with `Content-Range: bytes 100-199/2097152`, WebSocket `JOIN_ACK` + `PLAY` state confirmed |
| Downloaded release APK | 5,504,083 bytes, SHA-256 `B7F2660A0DFC05849F4E193671EAC111DD88611C2F7C025D90F23C57E1DF8E92`; unsigned; `assets/web/*` and `classes.dex` present |
| Downloaded CI debug APK | 25,754,222 bytes, SHA-256 `F36EC5ACF442F733ADE4043BFB2D65AEB1D72E5A98B982EFCF954CD5CDDA99B4`; v2-signed with the Android Debug certificate; 991 entries; `assets/web/*` and `classes.dex` present |
| APK asset inspection | `assets/web/index.html`, hashed JS/CSS, manifest, SW, icons present |
| `npm run typecheck` | passed |
| `npm test` | 49 tests across 7 files passed, exit 0 |
| `npm run build` | passed; JS 245.15 kB (75.76 kB gzip), CSS 31.29 kB (6.72 kB gzip) |

APK path: `android/app/build/outputs/apk/debug/app-debug.apk` (about 25.7 MB).

The shared tests cover protocol serialization, token generation/hash/constant-time
comparison, media-token signing/expiry/room scoping, sliding-window rate limiting, room
lifecycle, join/reconnect/leave, invite rotation, control policy, sync sequencing,
deduplication, drift decisions and clock smoothing.

The streaming tests cover health/marker, room metadata auth, join/reconnect/leave,
media-token auth, Range responses (200/206/416/HEAD), invalid ranges, concurrent stream
limits, state/stats auth, static client fallback and WebSocket join/state/command/reject
paths.

## Bugs found and fixed during the final pass

1. **Tunnel verification used the unversioned path by default.** `TunnelConfig` and
   `TunnelManager` defaulted to `/api/health`, while the canonical route is
   `/api/v1/health`. The server has a `/api` compatibility alias, so verification often
   worked, but the default and UI instructions now use the versioned route consistently.
2. **Server heartbeat pongs were treated as protocol errors.** The server sends
   `SYNC_PING` every 15 seconds; the browser and Android viewer correctly reply
   `SYNC_PONG`. `WebSocketHub` did not handle `SYNC_PONG` and returned `INVALID_ACTION`.
   It now accepts pongs silently, with an integration test covering the behaviour.
3. **Compose positional-argument errors** in four screens were fixed; `:app` now
   compiles.
4. **Missing browser client in the APK** was fixed by building `web/dist` before
   `assembleDebug` and verifying the APK zip entries.
5. **HEAD `/video` returned two `Content-Length` headers** (`2097152` and `0`), so
   Cloudflare's edge rejected the response with `502 Bad Gateway` even though Range GET
   requests worked. The HEAD branch now responds with an `OutgoingContent.NoContent`
   carrying the real media length, and regression tests assert exactly one
   `Content-Length` for full and partial HEAD requests.

## Not verified (must be done before calling this production-ready)

- Install and run on at least one physical Android device (ideally two) with a real
  video and a real SAF provider.
- Exercise the full host → Android viewer and host → browser flows on a LAN.
- Repeat the Cloudflare Quick Tunnel from a companion machine that reaches an **Android
  host**, and verify the app's Settings → Tunnel onboarding plus `/api/v1/health`, the
  WebSocket upgrade and Range streaming through it.
- Recheck HEAD `/video` through a live Cloudflare tunnel after the duplicate
  `Content-Length` fix; unit regression tests pass, but the fixed binary has not been
  retested through the edge.
- Test screen-off/background playback on a real OEM build.
- Test a Room v1 → v2 migration on a device with an existing database.
- Test media permission revocation, port conflicts, Wi-Fi changes and network loss.
- Run a browser E2E against a live host (Playwright/Cypress or manual).
- Load/soak test concurrent streams, long sessions and reconnects.
- Sign the release APK (CI secrets or a local keystore) and install it on hardware.
- Add a license if the project will be distributed publicly.

## Next steps

1. Install the debug APK on hardware and complete the checklist in
   `docs/ANDROID_SETUP.md`.
2. Run `cloudflared tunnel --url http://<phone-LAN-IP>:8080` on a computer that can
   reach the phone, then verify the app's Settings → Tunnel flow as described in
   `docs/CLOUDFLARE_SETUP.md`. A Windows companion quick tunnel has already been
   exercised against the server harness.
3. Add instrumentation tests for SAF, Media3 and the foreground service.
4. Add a browser E2E suite with a mock host or a real Android host.
5. Configure release signing secrets and verify the signed, minified APK on hardware.
6. Add a LICENSE and a security review pass before public distribution.

## Honest conclusion

The core logic and network layer are exercised by automated tests. The debug APK, browser
client, CI pipeline and minified release/R8 build all succeed on GitHub Actions. A real
Cloudflare Quick Tunnel was exercised end to end against the WatchWave Ktor server
harness, including health, Range `206` and WebSocket playback sync. The release artifact
is still unsigned, and the Android host, SAF, background playback and app tunnel
onboarding paths are not proven on hardware. Treat it as a working foundation that still
needs hardware validation, not as a verified production deployment.
