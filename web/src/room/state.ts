import {
  CHAT_HISTORY_LIMIT,
  type ChatMessage,
  type JoinResponse,
  type Participant,
  type RoomSummary,
  type SyncMessage,
  participantWithDefaults,
} from '../lib/protocol'

export interface PlaybackAnchor {
  mediaId: string
  mediaTitle: string | null
  isPlaying: boolean
  positionMs: number
  playbackSpeed: number
  durationMs: number
  sequence: number
  anchorServerMs: number
}

export type NoticeKind = 'info' | 'warn' | 'error'

export interface Notice {
  kind: NoticeKind
  message: string
  code?: string
}

export interface PartyState {
  phase: 'joining' | 'live' | 'closed'
  room: RoomSummary | null
  self: Participant | null
  participants: Participant[]
  isHost: boolean
  canControl: boolean
  controlsLocked: boolean
  hostOffline: boolean
  playback: PlaybackAnchor | null
  chat: ChatMessage[]
  notice: Notice | null
  closedMessage: string | null
}

export const initialPartyState: PartyState = {
  phase: 'joining',
  room: null,
  self: null,
  participants: [],
  isHost: false,
  canControl: true,
  controlsLocked: false,
  hostOffline: false,
  playback: null,
  chat: [],
  notice: null,
  closedMessage: null,
}

export type PartyAction =
  | { type: 'joined'; response: JoinResponse }
  | { type: 'sync'; message: SyncMessage; serverNow: number }
  | { type: 'notice'; notice: Notice | null }
  | { type: 'reset' }

export function partyReducer(state: PartyState, action: PartyAction): PartyState {
  switch (action.type) {
    case 'reset':
      return initialPartyState
    case 'notice':
      return { ...state, notice: action.notice }
    case 'joined': {
      const self = participantWithDefaults(action.response.participant)
      const locked = action.response.room.controlsLocked ?? false
      return {
        ...state,
        phase: 'joining',
        room: action.response.room,
        self,
        participants: [self],
        isHost: self.isHost,
        controlsLocked: locked,
        canControl: self.isHost || (!locked && (action.response.canControl ?? true)),
        chat: [],
      }
    }
    case 'sync':
      return applySyncMessage(state, action.message, action.serverNow)
    default:
      return state
  }
}

export function applySyncMessage(state: PartyState, message: SyncMessage, serverNow: number): PartyState {
  switch (message.type) {
    case 'JOIN_ACK': {
      const participants = (message.participants ?? []).map(participantWithDefaults)
      const selfId = message.participantId ?? state.self?.id ?? ''
      const self = participants.find((item) => item.id === selfId) ?? state.self
      const isHost = message.isHost ?? self?.isHost ?? false
      const locked = message.controlsLocked ?? state.controlsLocked
      return {
        ...state,
        phase: 'live',
        self,
        participants: participants.length > 0 ? participants : state.participants,
        isHost,
        controlsLocked: locked,
        canControl: isHost || (!locked && (message.canControl ?? true)),
        hostOffline: false,
        chat: [],
        notice: null,
      }
    }

    case 'STATE_UPDATE':
    case 'MEDIA_CHANGED': {
      const current = state.playback
      const mediaId = message.mediaId ?? current?.mediaId
      if (!mediaId) return state
      if (
        current &&
        current.mediaId === mediaId &&
        typeof message.sequence === 'number' &&
        message.sequence < current.sequence
      ) {
        return state
      }
      const anchorServerMs = message.serverTimestamp ?? message.serverTime ?? serverNow
      return {
        ...state,
        phase: state.phase === 'closed' ? 'closed' : 'live',
        playback: {
          mediaId,
          mediaTitle: message.mediaTitle ?? current?.mediaTitle ?? null,
          isPlaying: message.isPlaying ?? current?.isPlaying ?? false,
          positionMs: message.positionMs ?? current?.positionMs ?? 0,
          playbackSpeed: message.playbackSpeed ?? current?.playbackSpeed ?? 1,
          durationMs: message.durationMs ?? current?.durationMs ?? 0,
          sequence: message.sequence ?? current?.sequence ?? 0,
          anchorServerMs,
        },
      }
    }

    case 'PARTICIPANTS': {
      const participants = (message.participants ?? state.participants).map(participantWithDefaults)
      const selfId = state.self?.id
      const self = participants.find((item) => item.id === selfId) ?? state.self
      const isHost = self?.isHost ?? state.isHost
      const locked = message.controlsLocked ?? state.controlsLocked
      return {
        ...state,
        participants,
        self,
        isHost,
        controlsLocked: locked,
        canControl: isHost || !locked,
      }
    }

    case 'CONTROL_POLICY': {
      const locked = message.controlsLocked ?? false
      return {
        ...state,
        controlsLocked: locked,
        canControl: state.isHost || (!locked && (message.canControl ?? true)),
        notice: {
          kind: locked ? 'warn' : 'info',
          message: locked ? 'The host locked viewer controls.' : 'Shared controls are enabled again.',
        },
      }
    }

    case 'PARTICIPANT_JOINED': {
      const id = message.participantId
      if (!id) return state
      if (state.participants.some((participant) => participant.id === id)) return state
      const added = participantWithDefaults({
        id,
        displayName: message.displayName ?? 'Viewer',
        isHost: message.isHost ?? false,
        connected: true,
      })
      return { ...state, participants: [...state.participants, added] }
    }

    case 'PARTICIPANT_LEFT': {
      const id = message.participantId
      if (!id) return state
      return {
        ...state,
        participants: state.participants.filter((participant) => participant.id !== id),
      }
    }

    case 'ROOM_CLOSED': {
      const closedMessage =
        message.errorMessage ?? message.text ?? 'The host closed this room.'
      return {
        ...state,
        phase: 'closed',
        closedMessage,
        hostOffline: false,
        notice: null,
      }
    }

    case 'HOST_OFFLINE':
      return {
        ...state,
        hostOffline: true,
        notice: {
          kind: 'warn',
          message: 'The host disconnected. Playback stays where it is until they return.',
          code: message.errorCode,
        },
      }

    case 'CHAT_HISTORY': {
      return {
        ...state,
        chat: (message.chatMessages ?? []).slice(-CHAT_HISTORY_LIMIT),
      }
    }

    case 'CHAT_MESSAGE': {
      const incoming = message.chatMessages ?? []
      if (incoming.length === 0) return state
      const seen = new Set(state.chat.map((item) => item.messageId))
      const next = [...state.chat]
      for (const item of incoming) {
        if (!seen.has(item.messageId)) {
          seen.add(item.messageId)
          next.push(item)
        }
      }
      return { ...state, chat: next.slice(-CHAT_HISTORY_LIMIT) }
    }

    case 'ERROR': {
      const code = message.errorCode
      const locked = code === 'CONTROLS_LOCKED'
      return {
        ...state,
        controlsLocked: locked || state.controlsLocked,
        canControl: locked ? state.isHost : state.canControl,
        notice: {
          kind: 'error',
          message: message.errorMessage ?? message.text ?? 'The host rejected that request.',
          code,
        },
      }
    }

    default:
      return state
  }
}
