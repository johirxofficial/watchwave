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
| Tunnel abstraction | `TunnelManager`, companion + cloudflared providers | 🟡 code + health verification; ⛔ no real Cloudflare run |
| HTTPS share link | verified `TunnelStatus.activeUrl`, `ShareLinks` | 🟡 only after real tunnel verification |
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
| GitHub Actions | `.github/workflows/ci.yml`, `release.yml` | 🟡 added; ⛔ not run on GitHub yet |

## Automated verification performed locally

Environment: Windows, JDK 17.0.20.1, Android SDK with platform 34, Node 26, Gradle 8.7.

| Command | Result |
| --- | --- |
| `.\gradlew.bat :shared:test` | 38 tests passed |
| `.\gradlew.bat :streaming:testDebugUnitTest` | 25 tests passed |
| `.\gradlew.bat :app:compileDebugKotlin` | passed |
| `.\gradlew.bat :app:assembleDebug` | `BUILD SUCCESSFUL`, 99 tasks |
| `.\gradlew.bat :app:assembleRelease` | not verified: Gradle daemon OOM on the 8 GB local host during R8; CI is configured for release |
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

## Not verified (must be done before calling this production-ready)

- Install and run on at least one physical Android device (ideally two) with a real
  video and a real SAF provider.
- Exercise the full host → Android viewer and host → browser flows on a LAN.
- Establish a real Cloudflare quick/named tunnel and verify `/api/v1/health`, the
  WebSocket upgrade and Range streaming through it.
- Test screen-off/background playback on a real OEM build.
- Test a Room v1 → v2 migration on a device with an existing database.
- Test media permission revocation, port conflicts, Wi-Fi changes and network loss.
- Run a browser E2E against a live host (Playwright/Cypress or manual).
- Load/soak test concurrent streams, long sessions and reconnects.
- Review the release build with R8 and a signed, installable APK.
- Add a license if the project will be distributed publicly.

## Next steps

1. Run the CI workflow on GitHub and fix any environment-specific issues.
2. Install the debug APK on hardware and complete the checklist in
   `docs/ANDROID_SETUP.md`.
3. Run a real Cloudflare tunnel using `docs/CLOUDFLARE_SETUP.md`.
4. Add instrumentation tests for SAF, Media3 and the foreground service.
5. Add a browser E2E suite with a mock host or a real Android host.
6. Add release signing (CI secrets + Gradle signing config) and verify the minified APK.
7. Add a LICENSE and a security review pass before public distribution.

## Honest conclusion

The core logic and network layer are exercised by automated tests, and the debug
Android and web builds succeed. The release/R8 build, physical-device and real-tunnel
paths are not proven in this environment. Treat it as a working foundation
that still needs hardware validation, not as a verified production deployment.
