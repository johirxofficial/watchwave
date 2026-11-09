# WebSocket protocol

## Endpoint

```text
wss://<host>/api/v1/rooms/{roomId}/sync?token=<session token>
```

`/api` is kept as a compatibility alias. The **session token** is the one returned by
`POST /join` or `POST /reconnect`; it is not the invitation or media token.

The server validates the token before accepting the upgrade. Invalid, expired, revoked
or removed sessions are closed immediately with WebSocket close code `1008` and no
application payload.

The same endpoint exists for the host. The Android host connects to its own server at
`ws://127.0.0.1:<port>/api/v1/rooms/{roomId}/sync` using the host session token.

## Framing

Every application frame is one UTF-8 JSON text frame using the `SyncMessage` envelope.
Binary frames are ignored. Unknown fields are ignored, so new optional fields can be
added without a protocol bump. The version field `v` is currently `1`.

```json
{
  "type": "PLAY",
  "v": 1,
  "roomId": "k3f9xz2m7q1a",
  "commandId": "1b4e28ba-2fa1-11d2-883f-0016d3cca427",
  "positionMs": 120000,
  "clientTimestamp": 1762684920000,
  "expectedSequence": 7
}
```

Because `encodeDefaults = false`, the server omits fields that are not relevant to a
frame. Clients must tolerate missing fields and use `null`/`undefined` checks.

## Envelope fields

| Field | Type | Meaning |
| --- | --- | --- |
| `type` | string | Required message type |
| `v` | number | Protocol version (default 1) |
| `roomId` | string | Room the frame belongs to |
| `participantId` | string | Server-assigned participant id |
| `displayName` | string | Display name, used in join/participant events |
| `isHost` | boolean | Whether the participant is the host |
| `commandId` | string | Client-generated unique command id (1–128 chars) |
| `sequence` | number | Authoritative state sequence |
| `positionMs` | number | Anchor playback position |
| `playbackSpeed` | number | Playback rate, 0.5–2.0 |
| `durationMs` | number | Media duration |
| `isPlaying` | boolean | Playing/paused |
| `serverTimestamp` | number | Server wall clock at the state anchor (epoch ms) |
| `serverTime` | number | Server wall clock for clock samples (epoch ms) |
| `mediaId` | string | Media identifier |
| `mediaTitle` | string | Media title |
| `participants` | array | Participant list (see below) |
| `canControl` | boolean | Whether this participant may currently issue commands |
| `controlsLocked` | boolean | Whether viewer controls are locked |
| `errorCode` | string | Machine-readable error code |
| `errorMessage` | string | Human-readable error |
| `clientTimestamp` | number | Client time when a command was created |
| `expectedSequence` | number | Sequence the client believes is current |
| `retryAfterMs` | number | Suggested retry delay |
| `text` | string | Text for notices, errors, chat sends and ping correlation |
| `chatMessages` | array | Chat messages (send, broadcast or history) |

Participant objects:

```json
{
  "id": "p_xy12ab34cd56ef",
  "displayName": "Rahim",
  "isHost": false,
  "connected": true,
  "joinedAt": 1762684860000,
  "controlsAllowed": true
}
```

## Message types

| Type | Direction | Purpose |
| --- | --- | --- |
| `JOIN_ACK` | server → client | Identity, host flag, initial participants, control policy |
| `STATE_UPDATE` | server → client | Authoritative playback state |
| `PARTICIPANTS` | server → client | Full participant snapshot and control policy |
| `PARTICIPANT_JOINED` | server → client | A participant connected |
| `PARTICIPANT_LEFT` | server → client | A participant disconnected or left |
| `CONTROL_POLICY` | server → client | Viewer controls enabled/locked |
| `MEDIA_CHANGED` | server → client | Host switched the video |
| `ROOM_CLOSED` | server → client | Room closed by the host or expired |
| `HOST_OFFLINE` | server → client | The host's WebSocket disconnected |
| `ERROR` | server → client | Rejected command or protocol error |
| `SYNC_PING` | both | Clock/heartbeat ping |
| `SYNC_PONG` | both | Reply to a ping |
| `PLAY` | client → server | Start playback, optionally at a position |
| `PAUSE` | client → server | Pause at the current authoritative position |
| `SEEK` | client → server | Seek to `positionMs` |
| `SET_SPEED` | client → server | Set `playbackSpeed` |
| `ENDED` | client → server | Mark playback ended |
| `LEAVE` | client → server | Leave and close the socket cleanly |
| `CHAT_SEND` | client → server | Send a chat message (`text`, 1–500 characters) |
| `CHAT_MESSAGE` | server → client | One authoritative chat message broadcast to the room |
| `CHAT_HISTORY` | server → client | Last up to 100 messages, sent after a successful join |
| `JOIN` | reserved | Not accepted; identity comes from the URL token |

`JOIN` exists for forward compatibility only. Sending it returns `INVALID_ACTION`.

## Chat

Chat uses the same authenticated room socket and is available to every participant, even
when viewer playback controls are locked.

Client send:

```json
{
  "type": "CHAT_SEND",
  "roomId": "k3f9xz2m7q1a",
  "text": "That scene was wild",
  "clientTimestamp": 1762684920000
}
```

Server broadcast:

```json
{
  "type": "CHAT_MESSAGE",
  "roomId": "k3f9xz2m7q1a",
  "serverTime": 1762684920123,
  "chatMessages": [
    {
      "messageId": "8a2d0f3e-...",
      "participantId": "p_xy12ab34cd56ef",
      "displayName": "Rahim",
      "text": "That scene was wild",
      "sentAt": 1762684920123,
      "isHost": false
    }
  ]
}
```

Rules:

- The server trims text, rejects empty messages with `CHAT_EMPTY`, and rejects text longer
  than 500 characters with `CHAT_TOO_LONG`.
- The server always overwrites `participantId`, `displayName`, `isHost`, `messageId` and
  `sentAt` from the authenticated session and its own clock. Clients cannot spoof another
  sender.
- Each connection has a separate chat limiter of 10 messages per 10 seconds. Exceeding it
  returns `RATE_LIMITED` with `retryAfterMs`.
- History is in memory only, capped at the last 100 messages per room, and cleared when the
  room closes or the host process stops. It is not persisted to disk.
- On join the server sends `JOIN_ACK`, `STATE_UPDATE`, then `CHAT_HISTORY` if history exists,
  then the participant broadcasts. Clients should clear local chat on a new handshake and
  rebuild it from `CHAT_HISTORY` plus subsequent `CHAT_MESSAGE` frames.
- A `CHAT_MESSAGE` frame contains one message in normal operation; clients must still
  tolerate an array and de-duplicate by `messageId`.

## Connection lifecycle

1. Client obtains a session token through `POST /join` or `POST /reconnect`.
2. Client opens the WebSocket with `?token=<sessionToken>`.
3. Server validates the token, registers the participant and marks it connected.
4. Server sends `JOIN_ACK`, then `STATE_UPDATE`, then `CHAT_HISTORY` when history exists,
   then `PARTICIPANTS` and `PARTICIPANT_JOINED` broadcasts.
5. Every 15 seconds the server sends `SYNC_PING`. Clients answer `SYNC_PONG`; the server
   accepts the pong silently. Clients may also send their own `SYNC_PING`, which the
   server answers with `SYNC_PONG` and the original `text` correlation value.
6. Clients apply `STATE_UPDATE`/`MEDIA_CHANGED` frames with a sequence greater than or
   equal to the last applied sequence.
7. On network loss the client reconnects with backoff and the same session token, then
   requests/uses the state from the new socket.
8. On `LEAVE` or a clean client close, the server marks the participant disconnected and
   broadcasts `PARTICIPANT_LEFT`/`PARTICIPANTS`.

If a participant reconnects while an old socket is still registered, the server replaces
the old connection and closes it with code `1000` and the reason "Replaced by reconnect".

If the host socket disconnects, viewers receive `HOST_OFFLINE` and retain the last known
state. The host app reconnects automatically when auto-reconnect is enabled.

## Commands

Every command must include a unique `commandId` of 1–128 characters. The server
deduplicates `(participantId, commandId)` pairs with a 512-entry cache, so retrying a
command after an ambiguous network failure is safe.

Clients should include `expectedSequence` from the last state they applied. If the
server has advanced beyond that, the command is rejected with `STALE_SEQUENCE` and a
fresh state frame; the client should apply the state and retry if appropriate.

Validation order:

1. Participant exists and is connected (or is the host).
2. Viewer controls are enabled, unless the participant is the host.
3. `commandId` is present and within length limits.
4. The command is not a duplicate.
5. `expectedSequence` is not ahead of the server.
6. Per-participant command rate limit (12 commands per 2 seconds).
7. Action-specific payload:
   - `SEEK` requires `positionMs` in `[0, durationMs]`.
   - `SET_SPEED` requires `playbackSpeed` in `[0.5, 2.0]`.
   - `PLAY` may omit `positionMs`, in which case the current position is used.
8. Accepted commands increment `sequence` and broadcast `STATE_UPDATE`.

`PAUSE` and `ENDED` do not accept a position; they use the current authoritative
position.

## Playback state and clock projection

A `STATE_UPDATE` looks like:

```json
{
  "type": "STATE_UPDATE",
  "v": 1,
  "roomId": "k3f9xz2m7q1a",
  "mediaId": "content://media/external/video/media/42",
  "mediaTitle": "Example.mp4",
  "sequence": 8,
  "positionMs": 120340,
  "playbackSpeed": 1.0,
  "durationMs": 5400000,
  "isPlaying": true,
  "serverTimestamp": 1762684920340,
  "serverTime": 1762684920340
}
```

Clients project the position between frames:

```text
expected = positionMs + (serverNow - serverTimestamp) * playbackSpeed
```

where `serverNow` is the local monotonic time plus the estimated server offset.
`ClockSync` estimates the offset from `serverTime` samples using round-trip time and an
exponential smoothing factor of 0.25. The browser samples on connect and periodically;
Android does the same in `RoomClient`.

## Drift correction

The same policy is implemented in `:shared` and `web/src/lib/drift.ts`:

- small drift: no change,
- medium drift: temporary rate adjustment,
- large drift: seek with a cooldown,
- paused: seek to the exact frame when drift exceeds the small threshold.

See [ARCHITECTURE.md](ARCHITECTURE.md#drift-correction) for thresholds.

## Error and close semantics

Application errors are `ERROR` frames:

```json
{
  "type": "ERROR",
  "v": 1,
  "roomId": "k3f9xz2m7q1a",
  "participantId": "p_xy12ab34cd56ef",
  "errorCode": "CONTROLS_LOCKED",
  "errorMessage": "The host has temporarily locked viewer controls."
}
```

After a rejected command the server also sends a fresh `STATE_UPDATE`, so the client can
resynchronize without another REST call.

Close codes:

| Code | Meaning | Client action |
| --- | --- | --- |
| `1000` | Normal close / room ended / replaced by reconnect / client left | Do not reconnect blindly; show the reason |
| `1003` | Unsupported data | Treat as fatal |
| `1008` | Policy violation (invalid token, room mismatch) | Treat as fatal; ask for a new invite |
| `1001` | Going away (server stopped) | Reconnect if the host returns |
| Other | Transport/network failure | Reconnect with exponential backoff |

The browser treats `1008` and `1003` as fatal and shows a fresh-invite message. Other
codes use bounded exponential backoff with jitter. Android follows the same policy in
`RoomClient`.

## Example session

```text
C: GET /api/v1/rooms/k3f9xz2m7q1a?t=<invite>        -> room preview
C: POST /api/v1/rooms/k3f9xz2m7q1a/join              -> session + media token
C: WS  /api/v1/rooms/k3f9xz2m7q1a/sync?token=<session>

S: {"type":"JOIN_ACK","roomId":"k3f9xz2m7q1a","participantId":"p_...","displayName":"Rahim","isHost":false,"participants":[...],"canControl":true,"controlsLocked":false,"serverTime":1762684861000}
S: {"type":"STATE_UPDATE",...}
S: {"type":"PARTICIPANTS",...}
S: {"type":"PARTICIPANT_JOINED",...}

C: {"type":"PLAY","roomId":"k3f9xz2m7q1a","commandId":"c1","positionMs":120000,"expectedSequence":0}
S: {"type":"STATE_UPDATE","roomId":"k3f9xz2m7q1a","sequence":1,"positionMs":120000,"isPlaying":true,...}

S: {"type":"SYNC_PING","roomId":"k3f9xz2m7q1a","serverTime":...}
C: {"type":"SYNC_PONG","roomId":"k3f9xz2m7q1a","serverTime":...}

C: {"type":"SEEK","roomId":"k3f9xz2m7q1a","commandId":"c2","positionMs":600000,"expectedSequence":1}
S: {"type":"STATE_UPDATE","roomId":"k3f9xz2m7q1a","sequence":2,"positionMs":600000,...}

C: {"type":"LEAVE","roomId":"k3f9xz2m7q1a"}
S: close 1000 "Client left"
```

## Limits

- Maximum frame size: 256 KiB.
- Server WebSocket ping period: 20 seconds; timeout: 30 seconds (Ktor transport level).
- Application heartbeat: 15 seconds.
- Events per connection: 60 per 10 seconds.
- Commands per participant: 12 per 2 seconds.
- Clock samples are smoothed; no unbounded history is kept.
