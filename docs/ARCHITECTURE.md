# Architecture

WatchWave is a single-host, multi-viewer watch-party system. The host is an Android app
that owns the only authoritative copy of the room state and streams the selected video
directly from the device. Viewers are ordinary HTTP/WebSocket clients; one of them can
be another Android app, another can be a browser.

## Design goals

- Stream the original file without transcoding or uploading it.
- Keep one authoritative playback clock on the host.
- Let every connected participant request play, pause, seek and speed changes while the
  host allows shared controls.
- Work on a LAN without a server, and over the internet with a verified HTTPS tunnel.
- Keep secrets out of disk, logs and URLs that are not required by the protocol.
- Make the network layer testable without an Android device.

Non-goals: DRM, adaptive bitrate, transcoding, multi-host rooms, cloud recording,
persistent rooms across host restarts, and anonymous public rooms.

## Modules

### `:shared` (pure Kotlin/JVM)

- `protocol/Protocol.kt` — every REST DTO, WebSocket envelope (`SyncMessage`), the
  protocol version (`1`), the canonical API prefix (`/api/v1`) and stable error codes.
- `security/Tokens.kt` — secure random tokens, room/participant/media ids, SHA-256
  hashing, constant-time comparison and HMAC media tokens.
- `security/RateLimiter.kt` — sliding-window limiter used by the server, WebSocket hub
  and sync engine.
- `security/ShareLinks.kt` — builds and parses `/room/{id}?t=...`, LAN URLs, HTTPS URLs
  and `watchwave://` deep links.
- `room/RoomManager.kt` — in-memory room and participant registry: lifecycle, joins,
  reconnects, invite rotation, join policy, control policy, participant limits and
  expiry.
- `sync/SyncEngine.kt` — authoritative playback clock, command validation, deduplication,
  sequence numbers and rate limits.
- `sync/Drift.kt` — shared drift-correction policy and clock-offset smoothing.

The module has no Android dependency, so its tests run on a normal JVM.

### `:streaming` (Android library)

- `WatchWaveServer.kt` — embedded Ktor CIO server:
  - REST API under `/api/v1` (plus a legacy `/api` alias),
  - authenticated HTTP Range streaming,
  - static browser client from APK assets,
  - one WebSocket endpoint per room.
- `WebSocketHub.kt` — connection registry, JOIN_ACK/state/participant broadcasts,
  heartbeat, command handling and close semantics.
- `RangeParser.kt` — strict single-range RFC 7233 parser.
- `StreamingStats.kt` — bytes sent, active streams and approximate bitrate.
- `MediaSource.kt` — abstraction over a readable, size-known media object.

The module only knows a `ServerRoomStore` interface (room manager, sync engines, media
sources). It never touches Android storage directly.

### `:tunnel` (Android library)

- `TunnelManager` — owns the active provider and exposes one status stream.
- `CompanionUrlProvider` — uses an externally run `cloudflared`/reverse proxy and
  verifies its HTTPS URL.
- `CloudflaredProcessProvider` — starts a cloudflared binary, parses its public URL and
  verifies it. Used for both the bundled arm64 Quick Tunnel and an advanced custom binary;
  the bundled path supplies `SSL_CERT_FILE` and a writable `HOME`/`TMPDIR`.
- `HttpReachabilityVerifier` — requires the WatchWave health marker before a URL is
  exposed to the UI or share sheet.

### `:data` (Android library)

- Room database for library metadata, recents, room history and configuration.
- DataStore for non-secret settings.
- Schema version 2 with a `room_history.notes` migration.
- Room/session/host tokens are intentionally **not** stored here.

### `:app` (Android application)

- `WatchWaveApplication` / `AppContainer` — process-wide dependency graph.
- `HostEngine` — one host session at a time: server, room, sync engine, media source,
  tunnel, local client, playback bridge and history.
- `ViewerEngine` — one viewer session at a time: invite parsing, room preview, join/
  reconnect, media token, WebSocket client and playback bridge.
- `RoomClient` — Ktor HTTP/WebSocket client shared by host and viewer; owns the chat
  `StateFlow` and `sendChat()`.
- `PlaybackSync` — connects the local Media3 player to the authoritative state.
- `ChatPanel` — shared Compose room-chat UI for host and viewer screens.
- `PlaybackController` — app-scoped ExoPlayer wrapper.
- `HostStreamingService` — Media3 `MediaSessionService` plus a foreground notification.
- `SecureStore` — Android Keystore AES-GCM encryption for connector credentials.
- Compose UI — home, library, create party, host, join, viewer, settings, tunnel,
  diagnostics and history screens.

The browser client in `web/` is a separate Vite build that is copied into
`app/src/main/assets/web` and served by the same Ktor server.

## Host startup sequence

`HostEngine.start()` performs this sequence:

1. Stop any active viewer session. Only one session is allowed at a time.
2. Find a free TCP port near the configured port (default 8080, scanning up to 25 ports).
3. Create `StreamingStats` and `WatchWaveServer`, then start it on `0.0.0.0`.
4. Create the room in `RoomManager` and capture the room id, host token, host session
   token, host participant id and invite token.
5. Create the room's `SyncEngine` and a `ContentUriMediaSource` for the selected SAF
   document.
6. Start the configured tunnel provider. The provider verifies `/api/v1/health` before
   publishing a public URL.
7. Create a `RoomClient` pointing at `http://127.0.0.1:<port>` and a `PlaybackSync`.
8. Publish LAN and verified tunnel share links.
9. Connect the local client, start the foreground service and record room history.

The host is therefore also a network client. If the embedded server or WebSocket path
is broken, host playback synchronization fails too, which avoids a "works only locally"
split-brain design.

## Playback state model

`SyncEngine` stores playback as an anchor rather than mutating a position on a timer:

```text
position(t) = clamp(anchorPosition + (t - anchorMono) * speed, 0, duration)
```

- The anchor uses a monotonic clock, so wall-clock changes do not move playback.
- Paused state stores a fixed position.
- Each accepted command increments a monotonic `sequence`.
- Clients ignore `STATE_UPDATE` frames older than the last sequence they applied.
- Duplicate `commandId` values are ignored per participant, which makes retries safe.
- A command claiming a sequence ahead of the server is rejected with `STALE_SEQUENCE`.
- Commands are validated for participant membership, control policy, rate limit, action
  payload and position/speed bounds before changing state.
- Speed is limited to 0.5x–2.0x.
- `ENDED` pins the position to the media duration and pauses.

The host and browser both project the anchor forward between updates using the
server-time offset estimated from state frames and ping/pong samples.

## Drift correction

`DriftController` is shared by Android and ported to TypeScript for the browser. Its
defaults are configurable in Settings:

| Drift | Action |
| --- | --- |
| ≤ 300 ms | none |
| 300–1200 ms | temporary playback-rate nudge, at most 6% |
| 1200–3000 ms | stronger rate nudge, at most 12% |
| > 3000 ms | seek, with a 5-second cooldown |
| paused and > 300 ms | seek to the exact frame |

Rate adjustments are restored to 1.0x once the client converges. The thresholds are
exposed as `driftSmallMs`, `driftMediumMs` and `driftLargeMs`.

## Streaming path

1. A viewer joins over REST and receives a media token plus `/video` and `/sync` paths.
2. The player requests the media URL with `?t=<media token>` because an HTML5 `<video>`
   element cannot attach an `Authorization` header.
3. The server validates the token, the room status and the participant's presence.
4. `RangeParser` turns `Range: bytes=...` into a full, partial or unsatisfiable result.
5. A semaphore limits concurrent streams (24 by default). Excess requests receive
   `503` with `Retry-After`.
6. The selected `MediaSource` opens a stream at the requested offset and copies it in
   64 KiB chunks with cancellation checks and backpressure from Ktor.
7. The server sends `Accept-Ranges`, `Content-Range`, `Content-Length`, `Cache-Control:
   private, no-store` and `X-Content-Type-Options: nosniff`.

A partial response is `206`; a full response is `200`; an invalid range is `416` with
`Content-Range: bytes */<size>`. `HEAD` returns the same headers without a body.

## Browser client

The browser client is a Vite + React 18 + TypeScript application:

- `lib/api.ts` — typed REST client and URL builders.
- `lib/invite.ts` — invitation parsing.
- `lib/clock.ts` — server clock offset estimation and smoothing.
- `lib/drift.ts` — TypeScript port of `DriftController`.
- `lib/storage.ts` — per-room `sessionStorage` for credentials; `localStorage` for
  display name and volume only.
- `services/sync-client.ts` — WebSocket reconnect/backoff, heartbeat, command ids,
  expected sequences and fatal-close handling.
- `hooks/useWatchParty.ts` — join, reconnect, token refresh, notices, commands and chat.
- `components/ChatPanel.tsx` — room chat with server-echoed messages, quick reactions and
  connection state.
- `player/WatchPlayer.tsx` — HTML5 video, token rotation, drift loop, autoplay overlay,
  fullscreen, PiP, volume, speed and keyboard shortcuts.
- `room/state.ts` — pure reducer for sync and chat frames, covered by Vitest.

The browser stores session credentials only in that room's `sessionStorage`. Media
tokens live for one hour; the client refreshes them every 45 minutes and swaps the
video source while preserving position and playback state.

## Chat path

1. A client sends `CHAT_SEND` with only text (plus an optional client timestamp) over its
   authenticated room WebSocket.
2. `WebSocketHub` applies a per-connection 10-message/10-second limiter, trims the text,
   checks the 500-character limit and resolves the participant from the session token.
3. The server creates a `ChatMessageDto` with its own message id, participant id, display
   name, host flag and wall-clock timestamp.
4. The message is appended to a per-room in-memory deque capped at 100 entries.
5. `CHAT_MESSAGE` is broadcast to every connection in the room.
6. A newly joined client receives `JOIN_ACK`, `STATE_UPDATE`, then `CHAT_HISTORY` when
   history exists.
7. Both clients de-duplicate by `messageId` and keep the last 100 entries.

Chat history is intentionally ephemeral: it is not written to Room or any file and is
cleared when the room closes or the host process stops.

## Tunnel lifecycle

The tunnel is an optimisation for remote viewers, not a prerequisite for LAN viewing.

- `NONE` — LAN only.
- `ON_DEVICE` — the default. The app runs the bundled arm64 `cloudflared` from its
  executable `nativeLibraryDir` as a free Quick Tunnel, with the Mozilla CA bundle supplied
  through `SSL_CERT_FILE`. No account or companion device is required.
- `COMPANION_URL` — the host supplies an HTTPS URL that forwards to its listen port.
  The app polls `/api/v1/health` and only publishes the link after the WatchWave marker
  is returned.
- `CLOUDFLARED_PROCESS` — the app starts a supplied Android-compatible binary, parses
  the public URL from its output and verifies it the same way.

`TunnelStatus.activeUrl` is non-null only when the state is `CONNECTED` **and**
`verified` is true. Failed or unverified URLs are never placed in the share sheet.

## Lifecycle and concurrency

- `AppContainer` is application-scoped. Engines outlive Activity recreation.
- One app-scoped ExoPlayer and `PlaybackController` serve either a host or viewer
  session, never both simultaneously.
- A foreground `MediaSessionService` keeps the host process alive during screen-off
  playback.
- `RoomManager` and `SyncEngine` use a single lock around mutations; the WebSocket hub
  uses a per-connection send mutex to serialize frames.
- Server cleanup runs every 30 seconds: stale participants are removed after a 2-minute
  grace period, expired rooms are marked, and closed-room state is forgotten.
- Room TTL defaults to 6 hours and is clamped to 5 minutes–24 hours.
- Participant limits default to 20 and are clamped to 2–50.

## Persistence boundaries

| Data | Storage | Lifetime |
| --- | --- | --- |
| Video metadata, recents, room history | Room database | Until user clears it |
| App settings | DataStore | Until user clears it |
| Cloudflare tunnel token | Keystore-encrypted SharedPreferences | Until removed |
| Room id, host token, session token, invite token | Host process memory | Until room ends/process dies |
| Media token | Host process memory + viewer query/session storage | 1 hour |
| Browser display name/volume | Browser `localStorage` | Until cleared |
| Browser credentials | Per-room `sessionStorage` | Tab session |

Restarting the host process always invalidates previous rooms and links. This is
intentional: there is no offline secret that can resurrect a room.

## Failure model

- REST errors use `{ "error": CODE, "message": "...", "retryAfterMs": ... }` with stable
  machine-readable codes.
- WebSocket protocol errors are sent as an `ERROR` frame and may be followed by a close.
  Close code `1008` (policy violation) and `1003` (unsupported data) are fatal; `1000`
  means intentional/closed; other codes are retried with backoff.
- If the host WebSocket disconnects, viewers receive `HOST_OFFLINE` and keep the last
  known state. The host reconnects to its own server with the session token.
- If the media document permission is revoked, streaming returns
  `MEDIA_PERMISSION_REVOKED` and the host must reselect the file.
- If the port is busy, the host scans nearby ports. If no port is free, startup fails
  with a visible error rather than pretending to be live.
- If the tunnel cannot be verified, the room stays on its LAN link and the UI shows a
  warning.

## Test strategy

- Pure logic is unit-tested on the JVM (`:shared`).
- The Ktor server is tested with `testApplication` for REST, Range, auth and WebSocket
  integration (`:streaming`).
- The browser has Vitest tests for protocol, API, invite parsing, storage, clock, drift,
  reducer and WebSocket lifecycle.
- Android UI, Media3 playback, SAF access, Keystore and the foreground service require
  instrumentation/device tests that were not available in this environment.
