# WatchWave

WatchWave is a self-hosted watch-party system. One Android device hosts a video that is
already stored on the phone; other Android devices and ordinary browsers watch it in
sync. The host serves the original file directly over an embedded HTTP server using
HTTP Range requests and a room-scoped WebSocket. There is no media upload, no cloud
storage and no transcoding.

The browser client is compiled into the APK and served from the same origin as the API,
so a single HTTPS tunnel URL is enough to share a room.

## What is verified

| Area | Status |
| --- | --- |
| `:shared` protocol, tokens, rooms, sync clock, drift controller | 38 tests passing |
| `:streaming` REST, Range parsing, media auth, WebSocket integration | 25 tests passing |
| Android debug APK builds with the real browser client bundled | `BUILD SUCCESSFUL` |
| Browser client typecheck, unit/integration tests and production build | 49 tests passing, build succeeds |
| Real HTTP Range and WebSocket behaviour | Exercised by Ktor integration tests |
| GitHub Actions CI | Run `42569272812` passed: web tests/build, 63 Android tests and debug APK |
| Release/R8 build | Run `42569281967` passed on `ubuntu-latest`; unsigned APK produced with the browser client bundled |

## What is not yet verified

These are hard limitations of the development environment, not hidden successes:

- The APK has **not** been run on a physical Android device or emulator. No device, AVD
  or system image was available. Install and host/viewer flows on real hardware still
  need a manual pass.
- A real Cloudflare tunnel has **not** been established end to end. The tunnel code is
  implemented and guards against unverified URLs, but it requires an Android-compatible
  `cloudflared` binary or a companion process that you supply.
- The browser client has not been exercised against a running Android host in a real
  browser. Its protocol and state logic are covered by unit/integration tests.
- Background playback and the Media3 foreground service have not been tested with the
  screen off on hardware.
- The release/R8 build succeeds in CI, but the artifact is **unsigned** until signing
  secrets are configured. It has not been installed or exercised on hardware. Local R8
  builds still fail on the 8 GB Windows host, so use the GitHub runner or a machine with
  more memory.

Do not treat the "not yet verified" rows as working until you reproduce them on your own
device and network.

## Architecture at a glance

```text
┌──────────────────────────── Android host ────────────────────────────┐
│  SAF video  ──►  ContentUriMediaSource  ──►  Ktor HTTP server         │
│                                                │  Range / HEAD        │
│  ExoPlayer ◄── 127.0.0.1 media URL             │                      │
│      ▲                                         │                      │
│      │             RoomManager + SyncEngine    │                      │
│      └──────────── PlaybackSync ◄── RoomClient ┘                      │
│                              │ WebSocket                              │
│  TunnelManager ──► verified HTTPS URL ──► share link                  │
└──────────────────────────────┬───────────────────────────────────────┘
                               │ HTTPS + WSS
              ┌────────────────┴─────────────────┐
              │                                  │
     ┌────────▼────────┐                ┌────────▼─────────┐
     │ Android viewer  │                │ Browser viewer   │
     │ RoomClient +    │                │ React + HTML5    │
     │ PlaybackSync    │                │ video + sync WS  │
     └─────────────────┘                └──────────────────┘
```

The host application is itself a client of its own server. It connects to
`127.0.0.1:<port>` through the same `RoomClient` and `PlaybackSync` used by remote
viewers, so the authoritative path is exercised locally as well.

## Repository layout

```text
watchwave/
├── android/
│   ├── app/         Android host/viewer UI, engines, playback, tunnel UI
│   ├── data/        Room database, migrations, DataStore settings
│   ├── shared/      Protocol, tokens, room registry, sync engine, drift logic
│   ├── streaming/   Ktor HTTP Range server, WebSocket hub, static client serving
│   └── tunnel/      Tunnel providers and reachability verification
├── web/             React + TypeScript + Vite browser client
├── docs/            Architecture, API, protocol, security and setup guides
└── .github/         CI and release workflows
```

## Prerequisites

- JDK 17 (`JAVA_HOME` pointing at it)
- Android SDK with platform 34 and build-tools 34.0.0 (`ANDROID_HOME`)
- Node.js 20 or newer and npm
- A physical Android device with USB debugging, or an emulator with a system image
  (neither is bundled)

The project pins Gradle 8.7, AGP 8.6.1, Kotlin 2.0.20, Compose BOM 2024.09.00, Ktor
2.3.12, Media3 1.4.1 and Room 2.6.1.

## Build

Build the browser client first. The Android build copies `web/dist` into the APK
automatically.

```powershell
cd web
npm ci
npm run build

cd ..\android
$env:JAVA_HOME = "C:\path\to\jdk-17"
$env:ANDROID_HOME = "C:\path\to\android-sdk"
.\gradlew.bat :app:assembleDebug
```

The debug APK is written to:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

If `web/dist` is missing, the APK still builds but serves a built-in fallback page that
says the browser client was not bundled. Build the web client and rebuild to fix it.

### Configurable build values

Pass these as Gradle properties (`-P...`) or add them to `android/gradle.properties` /
your user-level Gradle properties:

| Property | Default | Purpose |
| --- | --- | --- |
| `watchwave.applicationId` | `com.watchwave.app` | Android application id |
| `watchwave.baseDomain` | empty | Public origin used to resolve `watchwave://` deep links and bare room codes |

Example:

```powershell
.\gradlew.bat :app:assembleDebug `
  -Pwatchwave.applicationId=com.example.watchwave `
  -Pwatchwave.baseDomain=wave.example.com
```

When `watchwave.baseDomain` is empty, the app only accepts full `http://` or `https://`
invitation links. It does not guess or fabricate a public URL.

## Run a room

1. Install the debug APK (`adb install -r android/app/build/outputs/apk/debug/app-debug.apk`).
2. Open **Settings**, set a display name, listen port (default 8080), participant limit
   and room lifetime.
3. Choose a video through the system file picker. Storage Access Framework grants access
   to that one document; the app never scans arbitrary directories.
4. Start hosting. On the same Wi-Fi, the app shows a LAN link such as
   `http://192.168.1.20:8080/room/<id>?t=<invite>`.
5. For remote viewers, configure a tunnel (see `docs/CLOUDFLARE_SETUP.md`). A public URL
   is only shown after `https://<url>/api/v1/health` returns the WatchWave marker.
6. Viewers paste the link into the Android app or open it in a browser. Multiple
   viewers can join one room; the host can lock viewer controls, disable joins, remove
   participants and rotate the invite link.

Only the original file is streamed. The phone must remain reachable and the app must
stay alive for the room to continue.

## Tests

```powershell
cd android
.\gradlew.bat :shared:test :streaming:testDebugUnitTest :app:assembleDebug
```

```powershell
cd web
npm ci
npm run typecheck
npm test
npm run build
```

Expected counts at the time of writing: **63 Android tests** (38 shared + 25 streaming)
and **49 browser tests**. The CI workflow runs these on Ubuntu with JDK 17 and Node 20.

## Security summary

- Invite tokens are 256-bit random values; session tokens are stored only as SHA-256
  hashes. Room, host and session secrets are memory-only and disappear when the host
  process stops.
- Media URLs use a separate HMAC-SHA256 token with a one-hour lifetime. The browser
  refreshes it every 45 minutes.
- Every HTTP and WebSocket request is scoped to one room and one participant.
- Tunnel credentials (Cloudflare token) are encrypted with an AES-GCM key in the Android
  Keystore. They are never written to logs or returned by the API.
- Range parsing rejects malformed and multi-range requests; the media handler never
  accepts a filesystem path from the network.
- The API enforces join, message and command rate limits and a bounded number of
  concurrent streams.
- LAN links are HTTP because a local IP cannot obtain a trusted certificate. Use an
  HTTPS tunnel for untrusted networks.

Read `docs/SECURITY.md` for the full threat model and limitations.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [HTTP API](docs/API.md)
- [WebSocket protocol](docs/WEBSOCKET_PROTOCOL.md)
- [Security](docs/SECURITY.md)
- [Android setup and device checklist](docs/ANDROID_SETUP.md)
- [Cloudflare tunnel setup](docs/CLOUDFLARE_SETUP.md)
- [Troubleshooting](docs/TROUBLESHOOTING.md)
- [Implementation checklist and verification status](docs/IMPLEMENTATION_CHECKLIST.md)

## License

No license file has been added. Add one before distributing the project publicly.
