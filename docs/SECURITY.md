# Security

WatchWave is designed for a trusted host and invited viewers. It is not a public
streaming platform, a DRM system, or a replacement for an authenticated conferencing
service. This document describes what is protected, how, and where the remaining risks
are.

## Threat model

The primary threats considered are:

- Someone without an invitation finding or guessing a room.
- A former viewer reusing an old link or token.
- A viewer controlling playback when the host has locked controls.
- A malicious HTTP request reading arbitrary files from the phone.
- Token leakage through logs, backups, share links or API responses.
- A public tunnel URL that does not actually reach the host.
- Resource exhaustion through joins, messages or concurrent streams.

Out of scope: a compromised host device, a malicious OS/provider, a viewer who is
authorised and records the video, and large-scale denial-of-service beyond the
in-process limits.

## Credentials and lifetimes

| Secret | Entropy | Storage | Lifetime |
| --- | --- | --- | --- |
| Invite token | 256-bit random | Host memory; appears in share link | Until room ends or invite is rotated |
| Session token | 256-bit random | Host stores only SHA-256 hash; client memory | Until leave/close/process end |
| Host token | 256-bit random | Host app memory only | Until room ends/process end |
| Media token | HMAC-SHA256 over room/participant/expiry | Host memory; viewer memory/URL | 1 hour |
| Cloudflare tunnel token | User-supplied | AES-GCM encrypted with Android Keystore | Until removed |
| Admin token | Operator-supplied `ServerConfig` | Not persisted by the app | Process/config lifetime |

Room ids are short, unambiguous random strings for usability, but knowing a room id is
not enough: the invite or session token is required for room metadata and joining.

Session tokens are compared by SHA-256 hash with `MessageDigest.isEqual`, which avoids
timing leaks. Media tokens are signed with HMAC-SHA256 and validated in constant time.
All tokens are URL-safe base64 so they survive query strings and deep links.

## What is never stored or returned

- The host token, host session token, invite token and room id are **not** written to the
  Room database, DataStore, browser `localStorage` or logs.
- `RoomSummaryDto.shareUrl` is `null` in API responses; share links are constructed by
  the host app, which knows the current public origin and invite.
- The tunnel status API returns state, provider, public URL, verification and messages,
  but never the cloudflared token or executable path.
- The API never accepts a filesystem path. Media is identified by a host-created
  `mediaId` that maps to an in-process `MediaSource`.
- Error responses use stable codes and generic messages; they do not echo secrets.

## Access control

- `POST /join` requires a valid invite token and respects join policy and capacity.
- Room preview requires either the invite token or a valid session.
- Room state and video require a session or media token scoped to that room.
- Host actions require the host token; a viewer session cannot invoke them.
- The host participant cannot be removed.
- When controls are locked, viewer `PLAY`/`PAUSE`/`SEEK`/`SET_SPEED`/`ENDED` commands
  are rejected with `CONTROLS_LOCKED`; the host can always control playback.
- Rotating the invite invalidates old share links immediately. Existing sessions remain
  valid until they leave, are removed, or the room closes.
- Closing a room marks every participant disconnected and broadcasts `ROOM_CLOSED`.

## Transport

- Remote viewing should use an HTTPS/WSS tunnel. TLS protects the session token, media
  token and video bytes in transit.
- LAN viewing uses plain HTTP because a local IP cannot present a publicly trusted
  certificate. Anyone on the same network who can sniff traffic could observe the
  invitation/session/media tokens. Use a trusted LAN or an HTTPS tunnel.
- `android:usesCleartextTraffic="true"` is required for the LAN use case. This does not
  disable TLS; it permits HTTP to private addresses.
- The app does not implement certificate pinning. It relies on the Android/browser trust
  store and on the tunnel provider's TLS.

## Tunnel verification

A tunnel URL is only published when all of the following hold:

1. The provider reports a public URL.
2. `GET <url>/api/v1/health` succeeds over HTTP(S).
3. The response body contains the `WatchWave` marker.
4. The status state is `CONNECTED` and `verified` is true.

`TunnelStatus.activeUrl` is null otherwise. This prevents a typo, a dead tunnel or an
unrelated service from being shown as a working share link. The app does not fabricate
`trycloudflare.com` URLs.

## Input validation

- Display names and room/video titles are stripped of ISO control characters, trimmed,
  whitespace-collapsed and length-limited.
- Room ids must match the generated alphabet and length (6–32 characters).
- Command ids are limited to 128 characters; positions must be within the media duration;
  speed is limited to 0.5–2.0.
- The WebSocket rejects a `roomId` field that does not match the authenticated room.
- The Range parser accepts exactly one `bytes=` range, clamps the end to the file size and
  rejects malformed or multi-range requests with `416`.
- Static file serving rejects `..` and backslashes and resolves only files from the APK's
  `assets/web` directory. There is no arbitrary filesystem route.

## Media access

- The user selects a document through the Storage Access Framework. WatchWave takes a
  persistable read grant for that document (and a selected tree when importing a folder).
- The host stores only metadata (URI, title, size, MIME, duration, playback position).
- Each Range request opens a fresh `ParcelFileDescriptor` at the requested offset. If the
  provider cannot seek, the request fails instead of serving wrong bytes.
- If the grant is revoked, the server returns `MEDIA_PERMISSION_REVOKED`; it does not fall
  back to another file.
- Folder import is capped at a fixed file count and only imports recognised video MIME
  types/extensions.

## Resource limits

| Control | Default |
| --- | --- |
| Join attempts per IP | 20 / 60 s |
| WebSocket events per connection | 60 / 10 s |
| Playback commands per participant | 12 / 2 s |
| Deduplication cache | 512 commands |
| Concurrent video streams | 24 |
| Participant limit | 20 (2–50) |
| Room TTL | 6 h (5 min–24 h) |
| WebSocket frame size | 256 KiB |
| Stale participant grace | 2 min |

The join limiter map is bounded to 2,048 IP entries before it is cleared. The server
does not queue unbounded frames: sends have a 5-second timeout and a failing connection
is removed.

## Browser-specific considerations

- Session and media tokens are kept in `sessionStorage`, scoped to the tab and cleared
  when it closes. They are never written to `localStorage` or cookies.
- Only display name and volume live in `localStorage`.
- The browser does not use `dangerouslySetInnerHTML`; React escapes text.
- A media token must be in the video URL because `<video>` cannot send an Authorization
  header. This means it can appear in browser history, devtools or proxy logs. It
  expires after one hour and is scoped to one room/participant.
- The invite token is part of the share URL and therefore browser history. Rotate the
  invite if a link leaks. Static responses set `Referrer-Policy: same-origin` so the
  token is not sent to third-party origins.
- Service worker caching is limited to static shell assets; API and media responses are
  not cached by the PWA.
- Browsers may require a user gesture before unmuted autoplay. The client shows a
  tap-to-join overlay instead of bypassing that policy.

## Logging

- Android logs use short messages (startup, room id, error class); they do not print
  tokens, headers or full media URLs.
- Ktor request failures are logged by exception class, not by request body or token.
- The browser logger receives connection phases and errors, not credentials.

If you add logging, do not log `Authorization`, `X-WatchWave-Host-Token`,
`X-WatchWave-Admin`, query parameter `t`, or `token`.

## Known limitations

- A viewer with legitimate access can always capture the decrypted video and audio.
- The host process holds room secrets in memory; a rooted/compromised device can read
  them.
- The invite token in a URL may be exposed by browser extensions, screenshots or a
  shared clipboard. Rotate invites and use HTTPS.
- There is no account system, audit trail, per-user revocation beyond kicking a
  participant, or persistent ban list.
- The tunnel provider is trusted to terminate TLS and forward traffic.
- Do not expose the embedded server directly to the public internet without a TLS
  reverse proxy/tunnel; use the LAN or an HTTPS tunnel.

## Reporting a vulnerability

Do not open a public issue containing a live invite link, session token, media token,
tunnel token or personal video. Reproduce with synthetic data, redact secrets, and
contact the repository owner privately.
