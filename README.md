# WatchWave

**A watch party that feels like pressing play with friends.** Install the Android app, add a
video from your phone, start a room, share one link, and watch together with real chat.

No account. No cloud upload. No transcoding. The original file stays on the host phone and
is streamed with HTTP Range requests; playback and chat stay in sync over a room WebSocket.

```text
Install APK  →  Add video  →  Start party  →  Copy/share link  →  Watch + chat
```

The browser client is bundled inside the APK and served from the same origin as the API, so
one HTTPS link is enough. **Instant public** mode starts a free Cloudflare Quick Tunnel on
the phone itself; you do not need a PC, Termux, account or port forwarding in the normal
flow.

## What's new in 1.1

- **Midnight-cinema redesign** across Android and the browser client: an electric-blue
  accent, deeper surfaces, larger tap targets and a calmer, theater-first room layout.
- **Responsive everywhere.** The browser shell adapts from a phone bottom bar to a desktop
  rail, and Android content is width-capped so tablets and foldables stay readable.
- **Viewer preferences that stay local.** Theme accent, reduced motion, density, start-muted,
  autoplay, skip distance, chat timestamps and other player defaults live in **Settings**
  and never change the host's room.
- **Advanced settings have their own section.** Android keeps everyday host controls on the
  main Settings screen and moves drift thresholds, reconnect behaviour, build details and
  diagnostics into **Settings → Advanced**. The browser groups advanced options under a
  clearly separated **Advanced** section too.
- **MX Player-inspired transport.** Large play/pause, configurable skip, speed menu,
  keyboard shortcuts, double-click side skip, PiP, fullscreen and a live sync badge.

## Quick start (the intended flow)

1. **Install** the debug APK from the latest CI artifact or a GitHub release.
2. Open **Library** and add a video with Android's file picker. WatchWave keeps a read grant
   for that one document; it never scans your storage.
3. Tap **Create party**, choose the video, and leave reachability on **Instant public**.
4. Tap **Start**. WatchWave starts the embedded server and bundled tunnel, verifies the
   public health endpoint, and shows the share link only after it is reachable.
5. **Share the link.** Viewers open it in Chrome/Edge/Firefox/Safari, or paste it into the
   Android app's Join screen.
6. Watch in sync. Play/pause/seek/speed are host-authoritative; viewers can chat even when
   playback controls are locked.

The phone must stay alive while the room is active. The app runs a foreground media service
and keeps the screen on during playback.

> **Tunnel note:** Instant public uses Cloudflare Quick Tunnels. They are free and require no
> account, but they are best-effort, have no uptime guarantee, and usually get a new hostname
> each time. Use a named tunnel or companion URL when you need a permanent address.

## What works today

| Area | Status |
| --- | --- |
| Android host | Real device: Samsung SM-A217F, Android 12, arm64-v8a |
| SAF import + original-quality streaming | Real 15.6 s / 5.2 MB MP4; Range `206` with `Content-Range` |
| Real-time playback sync | Real WebSocket `PLAY`, `PAUSE`, `SEEK`, `SET_SPEED`; browser frame rendered and in sync |
| Browser viewer | Joined the real Android host, HTML5 `readyState=4`, play/pause verified |
| Cloudflare Quick Tunnel | Verified from a Windows companion **and** on-device with the bundled arm64 binary |
| Room chat | Server-authoritative identity/timestamps, 500-char limit, 10/10 s rate limit, 100-message in-memory history; Android + browser UI; protocol integration tests |
| One-tap in-app public link | Packaged and executable; final live-room UI pass blocked by a locked test device (see below) |
| Automated tests | **66 Android tests** (38 shared + 28 streaming) and **58 browser tests**; CI runs both suites |
| Open source | MIT license, contribution/security/conduct docs, issue and PR templates |

### Honest limits

These are not hidden “coming soon” features; they are the current boundary:

- The updated one-tap tunnel and chat UI were built and installed, but the final live-room
  pass on the test phone was blocked by its PIN lock. The bundled binary and CA bundle were
  verified under the app's UID, and the same tunnel/binary path was verified end to end
  from `/data/local/tmp`.
- Only one physical Android phone was available. The Android viewer is covered by protocol
  tests but not a second-device hardware run.
- Screen-off playback, long sessions, Wi-Fi changes, port conflicts, permission revocation,
  a Room v1→v2 migration and large-scale load have not been fully tested.
- Optimized for **arm64-v8a** phones for the bundled tunnel. Other ABIs fall back to LAN, a
  companion URL, or a custom cloudflared binary.
- The debug APK is signed with an ephemeral CI debug key. Installing a newer CI build
  usually requires uninstalling the old one. The release APK is unsigned until signing
  secrets are configured.
- “Bug-free” cannot be guaranteed. Treat this as a strong, honest foundation rather than a
  claim of perfection.

## Features

- **MX Player-inspired dark player UI**: large transport controls, 10-second skip, speed
  menu (0.5×–2×), live sync badge, host-control lock indicator.
- **One host, many viewers.** The host owns the room and the authoritative timeline.
- **Real chat for everyone.** Viewers can talk even when they cannot control playback.
  Sender names, host badges, ids and timestamps are assigned by the server, so nobody can
  spoof another participant.
- **Bounded and defensive.** 256-bit invite tokens, hashed session tokens, room-scoped HMAC
  media tokens, rate limits, bounded concurrent streams, and no filesystem paths in the API.
- **Same-origin browser client.** No separate web deployment, CORS setup or extra URL.
- **Configurable.** Display name, port, participant limit, room lifetime, drift thresholds,
  auto-reconnect, control policy, and reachability mode.

## Architecture

```text
┌──────────────────────────── Android host ────────────────────────────┐
│  SAF video  ──►  ContentUriMediaSource  ──►  Ktor HTTP server         │
│                                                │  Range / HEAD        │
│  ExoPlayer ◄── 127.0.0.1 media URL             │                      │
│      ▲                                         │                      │
│      │             RoomManager + SyncEngine    │                      │
│      └──────────── PlaybackSync ◄── RoomClient ┘                      │
│                              │ WebSocket (sync + chat)                │
│  TunnelManager ──► bundled cloudflared ──► verified HTTPS URL         │
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

The Android host is also a client of its own server (`127.0.0.1`), so the same sync and chat
paths are exercised locally.

## Repository layout

```text
watchwave/
├── android/
│   ├── app/         Compose host/viewer UI, engines, player, chat, tunnel UI
│   ├── data/        Room database, migrations, DataStore settings
│   ├── shared/      Protocol, tokens, room registry, sync engine, drift logic
│   ├── streaming/   Ktor HTTP Range server, WebSocket hub, static client serving
│   └── tunnel/      Tunnel providers and reachability verification
├── web/             React + TypeScript + Vite browser client
├── docs/            Architecture, API, protocol, security, setup, checklist
└── .github/         CI, release workflow, issue/PR templates
```

## Build from source

Prerequisites: JDK 17, Android SDK platform 34 + build-tools 34.0.0, Node 20+.

```powershell
cd web
npm ci
npm run typecheck
npm test
npm run build

cd ..\android
$env:JAVA_HOME = "C:\path\to\jdk-17"
$env:ANDROID_HOME = "C:\path\to\android-sdk"
.\gradlew.bat :shared:test :streaming:testDebugUnitTest :app:assembleDebug
```

APK output:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

If `web/dist` is missing, the APK still builds but serves a fallback page instead of the
browser client. Build the web client first.

The debug APK includes the arm64 tunnel binary and CA bundle. The repository tracks these
files so a normal `assembleDebug` produces the one-tap experience; third-party licenses and
checksums are in [`docs/THIRD_PARTY_NOTICES.md`](docs/THIRD_PARTY_NOTICES.md).

### Configurable build values

| Property | Default | Purpose |
| --- | --- | --- |
| `watchwave.applicationId` | `com.watchwave.app` | Android application id |
| `watchwave.baseDomain` | empty | Public origin for `watchwave://` deep links and bare room codes |

## Run a room

1. Install the debug APK (`adb install -r app-debug.apk`).
2. Open **Settings** and set a display name. Leave the listen port at `8080` unless it is
   busy.
3. Add a video in **Library**.
4. **Create party** → choose the video → keep **Instant public** (or choose LAN/companion).
5. Start. Wait for **PUBLIC LINK · TUNNEL VERIFIED**.
6. Share the link. Browser viewers need no install; Android viewers can paste the same link.
7. Chat from the room panel. The history is in memory and disappears when the room closes.

Only the selected original file is streamed. Nothing is uploaded to a WatchWave server.

## Tests

```powershell
cd android
.\gradlew.bat :shared:test :streaming:testDebugUnitTest :app:assembleDebug

cd ..\web
npm run typecheck
npm test
npm run build
```

Current counts: **66 Android tests** and **58 browser tests**. The Android suite covers the
chat limiter, empty/oversized chat rejection, history replay, socket joins/state, Range
responses, tokens, rooms and sync. The web suite covers chat state/reducers, sync-client
chat sends, drift, invite parsing, storage and clock behavior.

CI is authoritative: [`.github/workflows/ci.yml`](.github/workflows/ci.yml) builds the web
client, runs both test suites, assembles the debug APK and uploads the artifact. Local Gradle
builds can OOM on small Windows hosts; use CI or a machine with more memory.

## Security summary

- Invite tokens are 256-bit random values; session tokens are stored only as SHA-256 hashes.
  Room, host and session secrets are memory-only and vanish when the host process stops.
- Media URLs use a separate HMAC-SHA256 token with a one-hour lifetime.
- Every HTTP and WebSocket request is scoped to one room and one participant.
- Chat identity and timestamps come from the authenticated server session, never from the
  client payload.
- Tunnel credentials are encrypted with an AES-GCM key in the Android Keystore and are never
  returned by the API.
- Range parsing rejects malformed and multi-range requests; the media handler never accepts
  a filesystem path from the network.
- LAN links use HTTP because a local IP cannot obtain a trusted certificate. Use HTTPS for
  untrusted networks.
- The bundled CA bundle is the standard Mozilla trust store, used only so Go's TLS stack can
  verify Cloudflare's endpoints on Android.

Read [`docs/SECURITY.md`](docs/SECURITY.md) for the full threat model.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [HTTP API](docs/API.md)
- [WebSocket protocol and chat](docs/WEBSOCKET_PROTOCOL.md)
- [Security](docs/SECURITY.md)
- [Android setup and device checklist](docs/ANDROID_SETUP.md)
- [Cloudflare tunnel setup](docs/CLOUDFLARE_SETUP.md)
- [Troubleshooting](docs/TROUBLESHOOTING.md)
- [Implementation checklist and verification status](docs/IMPLEMENTATION_CHECKLIST.md)
- [Third-party notices](docs/THIRD_PARTY_NOTICES.md)

## Contributing

Contributions are welcome. Read [`CONTRIBUTING.md`](CONTRIBUTING.md) and
[`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md). For vulnerabilities, use
[`SECURITY.md`](SECURITY.md) and GitHub's private advisory flow.

## License

MIT. See [`LICENSE`](LICENSE). Third-party components and their licenses are listed in
[`docs/THIRD_PARTY_NOTICES.md`](docs/THIRD_PARTY_NOTICES.md).
