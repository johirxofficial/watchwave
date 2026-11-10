import { describe, expect, it } from 'vitest'
import type { JoinResponse, SyncMessage } from '../lib/protocol'
import { applySyncMessage, initialPartyState, partyReducer, type PartyState } from './state'

const joined: JoinResponse = {
  room: {
    roomId: 'abc123',
    roomName: 'Friday film',
    hostName: 'Johir',
    status: 'ACTIVE',
    controlsLocked: false,
  },
  participant: { id: 'p_viewer', displayName: 'Maya', isHost: false, controlsAllowed: true },
  sessionToken: 'session',
  mediaToken: 'media',
  mediaUrlPath: '/api/v1/rooms/abc123/video',
  wsPath: '/api/v1/rooms/abc123/sync',
  canControl: true,
}

function liveState(): PartyState {
  return partyReducer(initialPartyState, { type: 'joined', response: joined })
}

function sync(state: PartyState, message: SyncMessage, now = 10_000): PartyState {
  return applySyncMessage(state, message, now)
}

describe('party state', () => {
  it('marks the room live and seeds the participant list on JOIN_ACK', () => {
    const acked = sync(liveState(), {
      type: 'JOIN_ACK',
      participantId: 'p_viewer',
      displayName: 'Maya',
      isHost: false,
      participants: [
        { id: 'p_host', displayName: 'Johir', isHost: true },
        { id: 'p_viewer', displayName: 'Maya' },
      ],
      canControl: true,
      controlsLocked: false,
      serverTime: 10_000,
    })
    expect(acked.phase).toBe('live')
    expect(acked.participants).toHaveLength(2)
    expect(acked.self?.displayName).toBe('Maya')
    expect(acked.canControl).toBe(true)
  })

  it('builds a playback anchor from the authoritative state', () => {
    const updated = sync(liveState(), {
      type: 'STATE_UPDATE',
      mediaId: 'm_1',
      mediaTitle: 'Film',
      isPlaying: true,
      positionMs: 5_000,
      playbackSpeed: 1.5,
      durationMs: 60_000,
      sequence: 4,
      serverTimestamp: 9_500,
    })
    expect(updated.playback).toEqual({
      mediaId: 'm_1',
      mediaTitle: 'Film',
      isPlaying: true,
      positionMs: 5_000,
      playbackSpeed: 1.5,
      durationMs: 60_000,
      sequence: 4,
      anchorServerMs: 9_500,
      receivedAt: expect.any(Number),
    })
  })

  it('accepts a correlated state response even when its sequence is older', () => {
    const first = sync(liveState(), {
      type: 'STATE_UPDATE',
      mediaId: 'm_1',
      isPlaying: true,
      positionMs: 5_000,
      sequence: 9,
      serverTimestamp: 1_000,
    })
    const resynced = sync(first, {
      type: 'STATE_UPDATE',
      mediaId: 'm_1',
      isPlaying: false,
      positionMs: 1_673,
      sequence: 3,
      serverTimestamp: 3_000,
      requestId: 'snapshot-1',
    })
    expect(resynced.playback?.isPlaying).toBe(false)
    expect(resynced.playback?.positionMs).toBe(1_673)
    expect(resynced.playback?.sequence).toBe(3)
  })

  it('discards stale sequence updates for the same media', () => {
    const first = sync(liveState(), {
      type: 'STATE_UPDATE',
      mediaId: 'm_1',
      isPlaying: true,
      positionMs: 5_000,
      sequence: 6,
      serverTimestamp: 1_000,
    })
    const stale = sync(first, {
      type: 'STATE_UPDATE',
      mediaId: 'm_1',
      isPlaying: false,
      positionMs: 0,
      sequence: 5,
      serverTimestamp: 1_100,
    })
    expect(stale.playback?.isPlaying).toBe(true)
    expect(stale.playback?.sequence).toBe(6)
  })

  it('accepts a new media id even with a lower sequence', () => {
    const first = sync(liveState(), {
      type: 'STATE_UPDATE',
      mediaId: 'm_1',
      isPlaying: true,
      positionMs: 5_000,
      sequence: 9,
      serverTimestamp: 1_000,
    })
    const switched = sync(first, {
      type: 'MEDIA_CHANGED',
      mediaId: 'm_2',
      mediaTitle: 'Second film',
      isPlaying: false,
      positionMs: 0,
      durationMs: 90_000,
      sequence: 1,
      serverTimestamp: 2_000,
    })
    expect(switched.playback?.mediaId).toBe('m_2')
    expect(switched.playback?.sequence).toBe(1)
    expect(switched.playback?.durationMs).toBe(90_000)
  })

  it('locks viewer controls while leaving the host in control', () => {
    const acked = sync(liveState(), {
      type: 'JOIN_ACK',
      participantId: 'p_viewer',
      participants: [
        { id: 'p_host', displayName: 'Johir', isHost: true },
        { id: 'p_viewer', displayName: 'Maya' },
      ],
      isHost: false,
      canControl: true,
    })
    const locked = sync(acked, {
      type: 'CONTROL_POLICY',
      controlsLocked: true,
      canControl: false,
    })
    expect(locked.controlsLocked).toBe(true)
    expect(locked.canControl).toBe(false)
  })

  it('ignores control locks for the host', () => {
    const hostJoined = partyReducer(initialPartyState, {
      type: 'joined',
      response: { ...joined, participant: { id: 'p_host', displayName: 'Johir', isHost: true } },
    })
    const locked = sync(hostJoined, {
      type: 'CONTROL_POLICY',
      controlsLocked: true,
      canControl: false,
    })
    expect(locked.canControl).toBe(true)
  })

  it('adds and removes participants without duplicates', () => {
    const withOne = sync(liveState(), {
      type: 'PARTICIPANT_JOINED',
      participantId: 'p_two',
      displayName: 'Alex',
    })
    const again = sync(withOne, {
      type: 'PARTICIPANT_JOINED',
      participantId: 'p_two',
      displayName: 'Alex',
    })
    expect(again.participants.filter((item) => item.id === 'p_two')).toHaveLength(1)
    const left = sync(again, { type: 'PARTICIPANT_LEFT', participantId: 'p_two' })
    expect(left.participants.some((item) => item.id === 'p_two')).toBe(false)
  })

  it('tracks the host going offline and the room closing', () => {
    const offline = sync(liveState(), { type: 'HOST_OFFLINE' })
    expect(offline.hostOffline).toBe(true)
    expect(offline.notice?.kind).toBe('warn')

    const closed = sync(offline, { type: 'ROOM_CLOSED', errorMessage: 'Host closed it.' })
    expect(closed.phase).toBe('closed')
    expect(closed.closedMessage).toBe('Host closed it.')
    expect(closed.hostOffline).toBe(false)
  })

  it('seeds chat history and appends new messages without duplicates', () => {
    const history = sync(liveState(), {
      type: 'CHAT_HISTORY',
      chatMessages: [
        {
          messageId: 'c1',
          participantId: 'p_host',
          displayName: 'Johir',
          text: 'Welcome!',
          sentAt: 1_000,
          isHost: true,
        },
      ],
    })
    expect(history.chat).toHaveLength(1)
    expect(history.chat[0]?.text).toBe('Welcome!')

    const appended = sync(history, {
      type: 'CHAT_MESSAGE',
      chatMessages: [
        {
          messageId: 'c2',
          participantId: 'p_viewer',
          displayName: 'Maya',
          text: 'Great scene',
          sentAt: 2_000,
        },
        {
          messageId: 'c2',
          participantId: 'p_viewer',
          displayName: 'Maya',
          text: 'Great scene',
          sentAt: 2_000,
        },
      ],
    })
    expect(appended.chat.map((message) => message.messageId)).toEqual(['c1', 'c2'])
  })

  it('clears chat when a fresh join handshake completes', () => {
    const withChat = sync(liveState(), {
      type: 'CHAT_MESSAGE',
      chatMessages: [
        {
          messageId: 'c1',
          participantId: 'p_viewer',
          displayName: 'Maya',
          text: 'Hello',
          sentAt: 1_000,
        },
      ],
    })
    const acked = sync(withChat, {
      type: 'JOIN_ACK',
      participantId: 'p_viewer',
      participants: [{ id: 'p_viewer', displayName: 'Maya' }],
      isHost: false,
    })
    expect(acked.chat).toEqual([])
  })

  it('surfaces command errors from the host', () => {
    const rejected = sync(liveState(), {
      type: 'ERROR',
      errorCode: 'CONTROLS_LOCKED',
      errorMessage: 'Locked by the host.',
    })
    expect(rejected.notice).toMatchObject({ kind: 'error', code: 'CONTROLS_LOCKED' })
    expect(rejected.controlsLocked).toBe(true)
  })
})
