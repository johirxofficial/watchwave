import { useCallback, useEffect, useMemo, useReducer, useRef, useState } from 'react'
import {
  buildMediaUrl,
  getRoom,
  joinRoom,
  leaveRoom,
  reconnectRoom,
  refreshMediaToken as refreshMediaTokenRequest,
} from '../lib/api'
import { ServerClock } from '../lib/clock'
import { describeError, isApiError } from '../lib/errors'
import type { JoinResponse, RoomSummary } from '../lib/protocol'
import {
  clearSession,
  loadDisplayName,
  loadSession,
  saveDisplayName,
  saveSession,
  type StoredSession,
} from '../lib/storage'
import { initialPartyState, partyReducer, type PartyState } from '../room/state'
import { SyncClient, type ConnectionPhase } from '../services/sync-client'

const TOKEN_REFRESH_MS = 45 * 60 * 1000

export type BootPhase = 'loading' | 'join' | 'live' | 'unavailable'

export interface UseWatchPartyResult {
  boot: BootPhase
  state: PartyState
  preview: RoomSummary | null
  bootError: string | null
  joinError: string | null
  joining: boolean
  leaving: boolean
  connection: ConnectionPhase
  connectionDetail: string | null
  mediaUrl: string
  serverOffsetMs: number
  suggestedName: string
  inviteToken: string | null
  join: (displayName: string) => Promise<void>
  leave: () => Promise<void>
  retryConnection: () => void
  dismissNotice: () => void
  refreshStream: () => Promise<void>
  play: (positionMs: number) => void
  pause: (positionMs: number) => void
  seek: (positionMs: number) => void
  setSpeed: (speed: number) => void
  ended: (positionMs: number) => void
  sendChat: (text: string) => boolean
  serverNow: () => number
}

/**
 * Owns the full viewer lifecycle: preview, join/reconnect, socket, media token.
 * Credentials are held in refs and sessionStorage only.
 */
export function useWatchParty(roomId: string, inviteToken: string | null): UseWatchPartyResult {
  const [state, dispatch] = useReducer(partyReducer, initialPartyState)
  const [boot, setBoot] = useState<BootPhase>('loading')
  const [preview, setPreview] = useState<RoomSummary | null>(null)
  const [bootError, setBootError] = useState<string | null>(null)
  const [joinError, setJoinError] = useState<string | null>(null)
  const [joining, setJoining] = useState(false)
  const [leaving, setLeaving] = useState(false)
  const [connection, setConnection] = useState<ConnectionPhase>('connecting')
  const [connectionDetail, setConnectionDetail] = useState<string | null>(null)
  const [mediaToken, setMediaToken] = useState('')
  const [mediaPath, setMediaPath] = useState(`/api/v1/rooms/${encodeURIComponent(roomId)}/video`)
  const [serverOffsetMs, setServerOffsetMs] = useState(0)

  const clockRef = useRef(new ServerClock())
  const clientRef = useRef<SyncClient | null>(null)
  const sessionRef = useRef<StoredSession | null>(null)
  const pathsRef = useRef<{ mediaUrlPath: string; wsPath: string } | null>(null)
  const inviteRef = useRef<string | null>(inviteToken)
  const tokenTimerRef = useRef<number | null>(null)
  const refreshInFlight = useRef(false)
  const lastRefreshAt = useRef(0)
  const mountedRef = useRef(true)

  inviteRef.current = inviteToken

  const stopTokenTimer = useCallback(() => {
    if (tokenTimerRef.current !== null) {
      window.clearInterval(tokenTimerRef.current)
      tokenTimerRef.current = null
    }
  }, [])

  const teardownClient = useCallback(() => {
    stopTokenTimer()
    const client = clientRef.current
    clientRef.current = null
    client?.close()
  }, [stopTokenTimer])

  const serverNow = useCallback(() => clockRef.current.now(), [])

  const forceRejoin = useCallback(
    (message: string) => {
      clearSession(roomId)
      sessionRef.current = null
      teardownClient()
      setBoot(inviteRef.current ? 'join' : 'unavailable')
      setBootError(message)
    },
    [roomId, teardownClient],
  )

  const refreshTokenNow = useCallback(
    async (silent: boolean): Promise<boolean> => {
      const session = sessionRef.current
      if (!session || refreshInFlight.current) return false
      refreshInFlight.current = true
      try {
        const result = await refreshMediaTokenRequest(roomId, session.sessionToken)
        if (!mountedRef.current) return false
        const updated: StoredSession = { ...session, mediaToken: result.mediaToken }
        sessionRef.current = updated
        saveSession(roomId, updated)
        setMediaToken(result.mediaToken)
        lastRefreshAt.current = Date.now()
        return true
      } catch (error) {
        if (!mountedRef.current) return false
        if (isApiError(error) && error.isAuthFailure) {
          forceRejoin('Your session expired. Rejoin with the invite link to continue watching.')
          return false
        }
        if (!silent) {
          dispatch({
            type: 'notice',
            notice: {
              kind: 'warn',
              message: 'Could not refresh the video access token. Playback will retry on the next request.',
            },
          })
        }
        return false
      } finally {
        refreshInFlight.current = false
      }
    },
    [forceRejoin, roomId],
  )

  const beginSession = useCallback(
    (response: JoinResponse, stored: StoredSession) => {
      teardownClient()
      clockRef.current.reset()
      setServerOffsetMs(0)

      const session: StoredSession = {
        v: 1,
        sessionToken: response.sessionToken,
        mediaToken: response.mediaToken,
        inviteToken: inviteRef.current ?? stored.inviteToken,
        displayName: stored.displayName,
        savedAt: Date.now(),
      }
      sessionRef.current = session
      saveSession(roomId, session)
      pathsRef.current = {
        mediaUrlPath: response.mediaUrlPath || `/api/v1/rooms/${roomId}/video`,
        wsPath: response.wsPath || `/api/v1/rooms/${roomId}/sync`,
      }
      setMediaPath(response.mediaUrlPath || `/api/v1/rooms/${roomId}/video`)
      setMediaToken(response.mediaToken)
      setPreview(response.room)
      setConnection('connecting')
      setConnectionDetail(null)
      setBoot('live')
      dispatch({ type: 'joined', response })

      const wsPath = pathsRef.current.wsPath
      const client = new SyncClient({
        roomId,
        sessionToken: response.sessionToken,
        wsPath,
        clock: clockRef.current,
        autoReconnect: true,
        onPhase: (phase, detail) => {
          if (!mountedRef.current) return
          setConnection(phase)
          setConnectionDetail(detail ?? null)
        },
        onMessage: (message) => {
          if (!mountedRef.current) return
          setServerOffsetMs(clockRef.current.offset)
          dispatch({ type: 'sync', message, serverNow: clockRef.current.now() })
          if (message.type === 'ROOM_CLOSED') {
            stopTokenTimer()
            clearSession(roomId)
            sessionRef.current = null
          }
        },
        onFatal: (message) => {
          if (!mountedRef.current) return
          forceRejoin(message)
        },
        logger: (message) => {
          if (import.meta.env.DEV) console.debug(`[watchwave] ${message}`)
        },
      })
      clientRef.current = client
      client.connect()

      stopTokenTimer()
      tokenTimerRef.current = window.setInterval(() => {
        void refreshTokenNow(true)
      }, TOKEN_REFRESH_MS)
    },
    [forceRejoin, refreshTokenNow, roomId, stopTokenTimer, teardownClient],
  )

  useEffect(() => {
    mountedRef.current = true
    let cancelled = false

    setBoot('loading')
    setBootError(null)
    setJoinError(null)
    setPreview(null)
    setConnection('connecting')
    setConnectionDetail(null)
    dispatch({ type: 'reset' })

    const run = async () => {
      const stored = loadSession(roomId)

      if (stored) {
        try {
          const response = await reconnectRoom(roomId, stored.sessionToken)
          if (cancelled) return
          beginSession(response, stored)
          return
        } catch (error) {
          if (cancelled) return
          if (isApiError(error) && (error.isAuthFailure || error.isGone)) {
            clearSession(roomId)
          } else {
            setBoot('unavailable')
            setBootError(describeError(error))
            return
          }
        }
      }

      if (!inviteRef.current) {
        setBoot('unavailable')
        setBootError(
          'This room needs an invite link. Ask the host for the full share link, which includes a token after /room/.',
        )
        return
      }

      try {
        const room = await getRoom(roomId, inviteRef.current)
        if (cancelled) return
        setPreview(room)
        if ((room.status ?? 'ACTIVE') !== 'ACTIVE') {
          setBoot('unavailable')
          setBootError(
            room.status === 'EXPIRED'
              ? 'This room has expired. Ask the host to create a new one.'
              : 'This room is closed.',
          )
          return
        }
        if (room.canJoin === false) {
          setBoot('unavailable')
          setBootError('The host is not accepting new viewers right now.')
          return
        }
        setBoot('join')
      } catch (error) {
        if (cancelled) return
        setBoot('unavailable')
        if (isApiError(error) && error.isGone) {
          setBootError('This room is closed or has expired.')
        } else if (isApiError(error) && (error.code === 'INVITE_INVALID' || error.isAuthFailure)) {
          setBootError('This invite link is invalid or was rotated by the host. Ask for a fresh link.')
        } else {
          setBootError(describeError(error))
        }
      }
    }

    void run()

    return () => {
      cancelled = true
      mountedRef.current = false
      teardownClient()
      sessionRef.current = null
    }
  }, [roomId, inviteToken, beginSession, teardownClient])

  const join = useCallback(
    async (displayName: string) => {
      const name = displayName.trim()
      if (!name) {
        setJoinError('Enter a display name so the host knows who joined.')
        return
      }
      setJoining(true)
      setJoinError(null)
      try {
        const response = await joinRoom(roomId, name, inviteRef.current)
        saveDisplayName(name)
        beginSession(response, {
          v: 1,
          sessionToken: response.sessionToken,
          mediaToken: response.mediaToken,
          inviteToken: inviteRef.current,
          displayName: name,
          savedAt: Date.now(),
        })
      } catch (error) {
        setJoinError(describeError(error))
      } finally {
        setJoining(false)
      }
    },
    [beginSession, roomId],
  )

  const leave = useCallback(async () => {
    setLeaving(true)
    const session = sessionRef.current
    clearSession(roomId)
    sessionRef.current = null
    teardownClient()
    dispatch({ type: 'reset' })
    if (session) {
      void leaveRoom(roomId, session.sessionToken).catch(() => {
        // The host also expires disconnected viewers automatically.
      })
    }
    setLeaving(false)
  }, [roomId, teardownClient])

  const retryConnection = useCallback(() => {
    setConnection('connecting')
    setConnectionDetail(null)
    clientRef.current?.reconnectNow()
  }, [])

  const dismissNotice = useCallback(() => {
    dispatch({ type: 'notice', notice: null })
  }, [])

  const refreshStream = useCallback(async () => {
    await refreshTokenNow(false)
  }, [refreshTokenNow])

  const send = useCallback(
    (
      type: 'PLAY' | 'PAUSE' | 'SEEK' | 'SET_SPEED' | 'ENDED',
      payload: { positionMs?: number; playbackSpeed?: number },
    ) => {
      const ok = clientRef.current?.sendCommand(type, payload) ?? false
      if (!ok) {
        dispatch({
          type: 'notice',
          notice: {
            kind: 'warn',
            message: 'The host is not reachable right now. Reconnecting will restore controls.',
          },
        })
      }
    },
    [],
  )

  const sendChat = useCallback((text: string): boolean => {
    const ok = clientRef.current?.sendChat(text) ?? false
    if (!ok) {
      dispatch({
        type: 'notice',
        notice: {
          kind: 'warn',
          message: 'The chat is reconnecting. Your message was not sent; try again in a moment.',
        },
      })
    }
    return ok
  }, [])

  const play = useCallback((positionMs: number) => send('PLAY', { positionMs }), [send])
  const pause = useCallback((positionMs: number) => send('PAUSE', { positionMs }), [send])
  const seek = useCallback((positionMs: number) => send('SEEK', { positionMs }), [send])
  const setSpeed = useCallback((speed: number) => send('SET_SPEED', { playbackSpeed: speed }), [send])
  const ended = useCallback((positionMs: number) => send('ENDED', { positionMs }), [send])

  useEffect(() => {
    const onOnline = () => {
      if (boot !== 'live') return
      clientRef.current?.reconnectNow()
      if (Date.now() - lastRefreshAt.current > 5 * 60 * 1000) {
        void refreshTokenNow(true)
      }
    }
    const onVisible = () => {
      if (document.visibilityState !== 'visible' || boot !== 'live') return
      if (connection === 'failed' || connection === 'reconnecting' || connection === 'closed') {
        clientRef.current?.reconnectNow()
      }
      if (Date.now() - lastRefreshAt.current > 10 * 60 * 1000) {
        void refreshTokenNow(true)
      }
    }
    window.addEventListener('online', onOnline)
    document.addEventListener('visibilitychange', onVisible)
    return () => {
      window.removeEventListener('online', onOnline)
      document.removeEventListener('visibilitychange', onVisible)
    }
  }, [boot, connection, refreshTokenNow])

  const mediaUrl = useMemo(() => {
    if (!mediaToken) return ''
    return buildMediaUrl(mediaPath, mediaToken)
  }, [mediaPath, mediaToken])

  return {
    boot,
    state,
    preview,
    bootError,
    joinError,
    joining,
    leaving,
    connection,
    connectionDetail,
    mediaUrl,
    serverOffsetMs,
    suggestedName: loadDisplayName(),
    inviteToken,
    join,
    leave,
    retryConnection,
    dismissNotice,
    refreshStream,
    play,
    pause,
    seek,
    setSpeed,
    ended,
    sendChat,
    serverNow,
  }
}
