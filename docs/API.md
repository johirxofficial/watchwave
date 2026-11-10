# HTTP API

## Base URL and versioning

All application routes are under:

```text
/api/v1
```

`/api` is kept as a compatibility alias for older clients. New code should use
`/api/v1`. The protocol version is also returned by the health endpoint.

The same server serves the compiled browser client at `/` and any non-API path. API
routes always take precedence over the static-file fallback.

## Authentication

Different actions use different credentials. Tokens are opaque strings; no token can be
used outside its room.

| Credential | How to send it | Used for |
| --- | --- | --- |
| Invitation token | `?t=` on a room preview, or `inviteToken` in a join body | Join or preview a room |
| Session token | `Authorization: Bearer <token>` or `?token=` | Viewer/host session actions and WebSocket |
| Media token | `?t=<token>` on the video URL | `<video>`/ExoPlayer stream requests |
| Host token | `X-WatchWave-Host-Token: <token>` | Host-only room management |
| Admin token | `X-WatchWave-Admin: <token>` | `POST /api/v1/rooms` bootstrap only |

Notes:

- `POST /api/v1/rooms` is disabled unless a non-empty `ServerConfig.adminToken` is
  configured. The Android app creates rooms directly through `RoomManager`; it does not
  expose this admin route.
- A media token is an HMAC-SHA256 token containing the room id, participant id and
  expiry. It is valid for **one hour** and is signed by a per-process secret.
- HTML5 `<video>` cannot attach an `Authorization` header, which is why the video URL
  uses `?t=`. Do not log full media URLs.
- Never put a session or host token in a share link. Only the invitation token belongs
  there.

## Error format

Every API error uses the same JSON shape:

```json
{
  "error": "INVITE_INVALID",
  "message": "Invitation is invalid or was revoked.",
  "retryAfterMs": 5000
}
```

`retryAfterMs` is optional. When present, the server may also send a `Retry-After`
header in seconds.

Stable error codes include:

```text
INVALID_REQUEST       INVALID_TOKEN         INVITE_INVALID
UNAUTHORIZED          FORBIDDEN             ROOM_NOT_FOUND
ROOM_CLOSED           ROOM_EXPIRED          ROOM_FULL
JOIN_DISABLED         CONTROLS_LOCKED       RATE_LIMITED
STALE_SEQUENCE        DUPLICATE_COMMAND     INVALID_ACTION
INVALID_POSITION      INVALID_SPEED         MEDIA_UNAVAILABLE
MEDIA_PERMISSION_REVOKED                    RANGE_NOT_SATISFIABLE
TUNNEL_NOT_READY      HOST_OFFLINE          INTERNAL_ERROR
```

Typical HTTP status mapping:

| Status | Meaning |
| --- | --- |
| `200` | Success |
| `201` | Room created |
| `204` | CORS preflight |
| `206` | Partial video content |
| `400` | Malformed request or invalid field |
| `401` | Missing credential where one is required |
| `403` | Invalid/revoked credential or locked controls |
| `404` | Room/resource not found |
| `409` | Room full, join disabled, media unavailable or invalid state |
| `410` | Room closed or expired |
| `416` | Unsatisfiable byte range |
| `429` | Rate limited |
| `503` | Concurrent stream limit reached |
| `500` | Unexpected server error |

## Endpoint summary

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| `GET` | `/api/v1/health` | none | Liveness, app marker, protocol version, server time |
| `GET` | `/api/v1/tunnel/status` | none | Current tunnel state (never returns credentials) |
| `POST` | `/api/v1/rooms` | admin | Create a room (disabled by default) |
| `GET` | `/api/v1/rooms/{roomId}` | invite or session | Room preview/metadata |
| `POST` | `/api/v1/rooms/{roomId}/join` | invite | Join and receive session/media tokens |
| `POST` | `/api/v1/rooms/{roomId}/reconnect` | session token in body | Reconnect after a network drop |
| `POST` | `/api/v1/rooms/{roomId}/leave` | session token in body | Leave and release the participant |
| `POST` | `/api/v1/rooms/{roomId}/close` | host | Close the room for everyone |
| `GET` | `/api/v1/rooms/{roomId}/state` | session or media token | Current authoritative playback state |
| `GET` | `/api/v1/rooms/{roomId}/stats` | host | Upload/streaming statistics |
| `GET`/`HEAD` | `/api/v1/rooms/{roomId}/video` | session or media token (`?t=`) | Range video stream |
| `POST` | `/api/v1/rooms/{roomId}/media-token` | session | Refresh the one-hour media token |
| `POST` | `/api/v1/rooms/{roomId}/host/participants/{participantId}/remove` | host | Kick a viewer |
| `POST` | `/api/v1/rooms/{roomId}/host/revoke-invite` | host | Rotate the invite secret |
| `POST` | `/api/v1/rooms/{roomId}/host/controls` | host | Enable/lock viewer playback controls |
| `POST` | `/api/v1/rooms/{roomId}/host/join-policy` | host | Enable/disable joins and set the limit |
| `WS` | `/api/v1/rooms/{roomId}/sync?token=...` | session | Playback synchronization |

## Health

```http
GET /api/v1/health
```

```json
{
  "status": "ok",
  "app": "WatchWave",
  "version": "1.1.2",
  "protocolVersion": 1,
  "serverTime": 1762684800000
}
```

`serverTime` is Unix epoch milliseconds. Tunnel verification requires the body to
contain the `WatchWave` marker.

## Tunnel status

```http
GET /api/v1/tunnel/status
```

```json
{
  "state": "CONNECTED",
  "provider": "companion",
  "publicUrl": "https://wave.example.com",
  "verified": true,
  "message": null,
  "updatedAt": 1762684800000
}
```

Possible states: `NOT_CONFIGURED`, `NOT_INSTALLED`, `STARTING`, `CONNECTED`,
`RECONNECTING`, `FAILED`, `STOPPED`. The API never returns the cloudflared token or an
executable path.

## Create a room (admin only)

```http
POST /api/v1/rooms
X-WatchWave-Admin: <admin token>
Content-Type: application/json
```

```json
{
  "roomName": "Friday Movie",
  "hostName": "Amina",
  "mediaId": "content://media/external/video/media/42",
  "videoTitle": "Example.mp4",
  "durationMs": 5400000,
  "mimeType": "video/mp4",
  "sizeBytes": 1073741824,
  "maxParticipants": 10,
  "ttlMinutes": 360
}
```

Response `201`:

```json
{
  "room": {
    "roomId": "k3f9xz2m7q1a",
    "roomName": "Friday Movie",
    "hostName": "Amina",
    "status": "ACTIVE",
    "media": {
      "mediaId": "content://media/external/video/media/42",
      "title": "Example.mp4",
      "durationMs": 5400000,
      "mimeType": "video/mp4",
      "sizeBytes": 1073741824
    },
    "isPlaying": false,
    "positionMs": 0,
    "playbackSpeed": 1.0,
    "participantCount": 1,
    "maxParticipants": 10,
    "hostOnline": false,
    "createdAt": 1762684800000,
    "expiresAt": 1762706400000,
    "canJoin": true,
    "controlsLocked": false,
    "shareUrl": null
  },
  "hostToken": "<host secret>",
  "hostSessionToken": "<host session secret>",
  "hostParticipantId": "p_ab12cd34ef56gh",
  "inviteToken": "<invite secret>",
  "sharePath": "/room/k3f9xz2m7q1a?t=<invite secret>",
  "mediaUrlPath": "/api/v1/rooms/k3f9xz2m7q1a/video",
  "wsPath": "/api/v1/rooms/k3f9xz2m7q1a/sync"
}
```

The host token and host session token are returned only here. They are never included
in a share link or room summary.

## Room preview

```http
GET /api/v1/rooms/{roomId}?t=<invite token>
```

A valid session token (`Authorization: Bearer ...`) also works. On success the body is a
`RoomSummaryDto` (the `room` object shown above). Without a valid invite/session the
server returns `403 INVITE_INVALID`; it deliberately does not reveal room metadata.

## Join

```http
POST /api/v1/rooms/{roomId}/join
Content-Type: application/json
```

```json
{
  "displayName": "Rahim",
  "inviteToken": "<invite token>"
}
```

Response `200`:

```json
{
  "room": { "...": "RoomSummaryDto" },
  "participant": {
    "id": "p_xy12ab34cd56ef",
    "displayName": "Rahim",
    "isHost": false,
    "connected": true,
    "joinedAt": 1762684860000,
    "controlsAllowed": true
  },
  "sessionToken": "<session secret>",
  "mediaToken": "<media token>",
  "mediaUrlPath": "/api/v1/rooms/k3f9xz2m7q1a/video",
  "wsPath": "/api/v1/rooms/k3f9xz2m7q1a/sync",
  "canControl": true,
  "shareUrl": null
}
```

Joins are rate limited per client IP (20 attempts per 60 seconds by default). Room
capacity is 2–50 participants; the default is 20.

## Reconnect

```http
POST /api/v1/rooms/{roomId}/reconnect
Content-Type: application/json
```

```json
{ "sessionToken": "<session token>" }
```

Response shape is the same as join, with a fresh media token. Reconnect preserves the
participant id, display name and control permissions. If the room closed or expired, the
server returns `410`.

## Leave

```http
POST /api/v1/rooms/{roomId}/leave
Content-Type: application/json
```

```json
{ "sessionToken": "<session token>" }
```

Response:

```json
{ "ok": true, "message": "Left the room." }
```

Leaving removes the participant and closes their WebSocket. Session tokens cannot be
reused after leaving.

## Close room

```http
POST /api/v1/rooms/{roomId}/close
X-WatchWave-Host-Token: <host token>
```

Response:

```json
{ "ok": true, "message": "Room closed." }
```

All viewers receive `ROOM_CLOSED` over WebSocket and the room can no longer be joined.

## Playback state

```http
GET /api/v1/rooms/{roomId}/state
Authorization: Bearer <session token>
```

```json
{
  "roomId": "k3f9xz2m7q1a",
  "mediaId": "content://media/external/video/media/42",
  "isPlaying": true,
  "positionMs": 120340,
  "playbackSpeed": 1.0,
  "durationMs": 5400000,
  "sequence": 7,
  "serverTimestamp": 1762684920340,
  "serverTime": 1762684920340
}
```

Use `serverTimestamp` as the anchor, `positionMs` as the anchor position and
`playbackSpeed` to project forward. `sequence` is monotonic within the room.

A media token in `?t=` is also accepted for this endpoint. The complete state is also
sent as the `STATE_UPDATE` WebSocket frame.

## Streaming statistics (host)

```http
GET /api/v1/rooms/{roomId}/stats
X-WatchWave-Host-Token: <host token>
```

```json
{
  "bytesSent": 734003200,
  "activeStreams": 3,
  "totalStreamsStarted": 11,
  "approximateBitrateBps": 3820000
}
```

## Video stream

```http
GET  /api/v1/rooms/{roomId}/video?t=<media token>
HEAD /api/v1/rooms/{roomId}/video?t=<media token>
Range: bytes=0-65535
```

A session token (`Authorization: Bearer ...`) is also accepted. A browser/player should
use the media token query parameter.

The server always sends:

```text
Accept-Ranges: bytes
Cache-Control: private, no-store
X-Content-Type-Options: nosniff
```

A valid request returns:

- `200 OK` with the full body when no `Range` is present.
- `206 Partial Content` with `Content-Range: bytes start-end/size` for a single range.
- `416 Requested Range Not Satisfiable` with `Content-Range: bytes */size` for a
  malformed, multi-range or out-of-bounds request.
- `409 MEDIA_UNAVAILABLE` if the host has no readable media.
- `409 MEDIA_PERMISSION_REVOKED` if the SAF grant was lost.
- `503 RATE_LIMITED` with `Retry-After` if 24 streams are already active.

Multi-range requests are deliberately rejected rather than partially served. ExoPlayer
and HTML5 video use single ranges.

Example:

```bash
curl -H "Range: bytes=0-1023" \
  "https://wave.example.com/api/v1/rooms/k3f9xz2m7q1a/video?t=$MEDIA_TOKEN" \
  -o first-kib.bin
```

## Refresh media token

```http
POST /api/v1/rooms/{roomId}/media-token
Authorization: Bearer <session token>
```

```json
{
  "mediaToken": "<new media token>",
  "expiresAt": 1762688520000
}
```

The browser calls this every 45 minutes and swaps the video source to the new URL while
preserving position. Android clients refresh on reconnect or when a stream fails.

## Host controls

All host endpoints require `X-WatchWave-Host-Token`, unless the body also supplies
`hostToken`.

### Enable or lock viewer controls

```http
POST /api/v1/rooms/{roomId}/host/controls
Content-Type: application/json
```

```json
{ "hostToken": "<host token>", "enabled": true }
```

`enabled: true` lets viewers play/pause/seek/set speed; `false` locks viewer controls
(the host can always control playback). A `CONTROL_POLICY` frame and updated
participant list are broadcast.

### Enable/disable joins and set the participant limit

```http
POST /api/v1/rooms/{roomId}/host/join-policy
Content-Type: application/json
```

```json
{ "hostToken": "<host token>", "enabled": true, "maxParticipants": 12 }
```

`maxParticipants` is optional and clamped to 2–50. It cannot be set below the current
participant count.

### Remove a participant

```http
POST /api/v1/rooms/{roomId}/host/participants/{participantId}/remove
X-WatchWave-Host-Token: <host token>
```

The viewer receives an `ERROR` frame and is disconnected. The host cannot be removed.

### Rotate the invite

```http
POST /api/v1/rooms/{roomId}/host/revoke-invite
X-WatchWave-Host-Token: <host token>
```

```json
{
  "ok": true,
  "inviteToken": "<new invite token>",
  "sharePath": "/room/k3f9xz2m7q1a?t=<new invite token>"
}
```

Every previously shared link stops working immediately. Existing sessions remain valid.

## Rate limits and concurrency

| Limit | Default |
| --- | --- |
| Join attempts per IP | 20 per 60 s |
| WebSocket events per connection | 60 per 10 s |
| Playback commands per participant | 12 per 2 s |
| Command deduplication cache | 512 command ids |
| Concurrent video streams | 24 |
| Server cleanup interval | 30 s |
| Stale participant grace | 2 min |
| WebSocket heartbeat | 15 s |

Rate-limited responses use `429` or `503`, `RATE_LIMITED`, and a `retryAfterMs` field.

## CORS

Production browser traffic is same-origin: the APK serves both the client and the API.
Cross-origin headers are only emitted for the development origins:

```text
http://localhost:5173
http://127.0.0.1:5173
```

To point the Vite dev server at a phone, set `WATCHWAVE_DEV_HOST` (the proxy target) or
set `VITE_WATCHWAVE_ORIGIN` when deploying the static client to a different origin.
