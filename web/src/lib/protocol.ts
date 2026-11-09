/**
 * Wire types shared with the Kotlin host.
 *
 * The server serializes with `encodeDefaults = false`, so any Kotlin field that has
 * a default may be absent on the wire. Treat those fields as optional and apply the
 * documented default at the edge.
 */

export const PROTOCOL_VERSION = 1
export const API_PREFIX = '/api/v1'

export type MessageType =
  | 'JOIN'
  | 'JOIN_ACK'
  | 'LEAVE'
  | 'PLAY'
  | 'PAUSE'
  | 'SEEK'
  | 'SET_SPEED'
  | 'ENDED'
  | 'STATE_UPDATE'
  | 'PARTICIPANT_JOINED'
  | 'PARTICIPANT_LEFT'
  | 'PARTICIPANTS'
  | 'ROOM_CLOSED'
  | 'HOST_OFFLINE'
  | 'SYNC_PING'
  | 'SYNC_PONG'
  | 'ERROR'
  | 'MEDIA_CHANGED'
  | 'CONTROL_POLICY'
  | 'ROOM_INFO'
  | 'CHAT_SEND'
  | 'CHAT_MESSAGE'
  | 'CHAT_HISTORY'

export const CHAT_MAX_LENGTH = 500
export const CHAT_HISTORY_LIMIT = 100

export interface ChatMessage {
  messageId: string
  participantId: string
  displayName: string
  text: string
  sentAt: number
  isHost?: boolean
}

export type RoomStatus = 'ACTIVE' | 'CLOSED' | 'EXPIRED'
export type CommandAction = 'PLAY' | 'PAUSE' | 'SEEK' | 'SET_SPEED' | 'ENDED'

export interface Participant {
  id: string
  displayName: string
  isHost?: boolean
  connected?: boolean
  joinedAt?: number
  controlsAllowed?: boolean
}

export interface MediaDescriptor {
  mediaId: string
  title: string
  durationMs: number
  mimeType?: string | null
  sizeBytes?: number | null
}

export interface PlaybackStateDto {
  roomId: string
  mediaId: string
  isPlaying: boolean
  positionMs: number
  playbackSpeed: number
  durationMs: number
  sequence: number
  serverTimestamp: number
  serverTime?: number
}

export interface RoomSummary {
  roomId: string
  roomName: string
  hostName: string
  status: RoomStatus
  media?: MediaDescriptor | null
  isPlaying?: boolean
  positionMs?: number
  playbackSpeed?: number
  participantCount?: number
  maxParticipants?: number
  hostOnline?: boolean
  createdAt?: number
  expiresAt?: number
  canJoin?: boolean
  controlsLocked?: boolean
  shareUrl?: string | null
}

export interface JoinResponse {
  room: RoomSummary
  participant: Participant
  sessionToken: string
  mediaToken: string
  mediaUrlPath: string
  wsPath: string
  canControl?: boolean
  shareUrl?: string | null
}

export interface ReconnectResponse extends JoinResponse {}

export interface MediaTokenResponse {
  mediaToken: string
  expiresAt: number
}

export interface ErrorResponse {
  error: string
  message: string
  retryAfterMs?: number | null
}

export interface HealthResponse {
  status?: string
  app?: string
  version?: string
  protocolVersion?: number
  serverTime: number
}

export interface TunnelStatus {
  state: string
  provider: string
  publicUrl?: string | null
  verified?: boolean
  message?: string | null
  updatedAt?: number
}

export interface ActionResponse {
  ok?: boolean
  message?: string | null
}

/** One envelope for every WebSocket event. Unused fields are absent. */
export interface SyncMessage {
  type: MessageType
  v?: number
  roomId?: string
  participantId?: string
  displayName?: string
  isHost?: boolean
  commandId?: string
  sequence?: number
  positionMs?: number
  playbackSpeed?: number
  durationMs?: number
  isPlaying?: boolean
  serverTimestamp?: number
  serverTime?: number
  mediaId?: string
  mediaTitle?: string
  participants?: Participant[]
  canControl?: boolean
  controlsLocked?: boolean
  errorCode?: string
  errorMessage?: string
  clientTimestamp?: number
  expectedSequence?: number
  retryAfterMs?: number
  text?: string
  chatMessages?: ChatMessage[]
}

export function participantWithDefaults(participant: Participant): Required<Participant> {
  return {
    id: participant.id,
    displayName: participant.displayName,
    isHost: participant.isHost ?? false,
    connected: participant.connected ?? true,
    joinedAt: participant.joinedAt ?? 0,
    controlsAllowed: participant.controlsAllowed ?? true,
  }
}

export function roomWithDefaults(room: RoomSummary): Required<RoomSummary> {
  return {
    roomId: room.roomId,
    roomName: room.roomName,
    hostName: room.hostName,
    status: room.status,
    media: room.media ?? null,
    isPlaying: room.isPlaying ?? false,
    positionMs: room.positionMs ?? 0,
    playbackSpeed: room.playbackSpeed ?? 1,
    participantCount: room.participantCount ?? 0,
    maxParticipants: room.maxParticipants ?? 20,
    hostOnline: room.hostOnline ?? false,
    createdAt: room.createdAt ?? 0,
    expiresAt: room.expiresAt ?? 0,
    canJoin: room.canJoin ?? true,
    controlsLocked: room.controlsLocked ?? false,
    shareUrl: room.shareUrl ?? null,
  }
}
