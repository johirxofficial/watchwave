import { buildWebSocketUrl } from '../lib/api'
import type { ServerClock } from '../lib/clock'
import { CHAT_MAX_LENGTH, type CommandAction, type SyncMessage } from '../lib/protocol'

export type ConnectionPhase = 'connecting' | 'live' | 'reconnecting' | 'closed' | 'failed'

export interface SyncClientOptions {
  roomId: string
  sessionToken: string
  wsPath: string
  clock: ServerClock
  onMessage: (message: SyncMessage) => void
  onPhase: (phase: ConnectionPhase, detail?: string) => void
  onFatal: (message: string) => void
  autoReconnect?: boolean
  logger?: (message: string) => void
}

const HEARTBEAT_MS = 15_000
const STALE_MS = 50_000
const MAX_RECONNECT_ATTEMPTS = 8

/**
 * One WebSocket per viewer. Owns reconnect/backoff, heartbeat, clock sampling and
 * command idempotency. The host is authoritative; this class never mutates state.
 */
export class SyncClient {
  private readonly options: SyncClientOptions
  private socket: WebSocket | null = null
  private closed = false
  private attempt = 0
  private heartbeatTimer: number | null = null
  private reconnectTimer: number | null = null
  private lastMessageAt = 0
  private pendingPings = new Map<string, number>()
  private latestSequence = 0
  private phase: ConnectionPhase = 'connecting'

  constructor(options: SyncClientOptions) {
    this.options = options
  }

  get connectionPhase(): ConnectionPhase {
    return this.phase
  }

  connect(): void {
    if (this.closed) return
    if (this.socket && (this.socket.readyState === WebSocket.OPEN || this.socket.readyState === WebSocket.CONNECTING)) {
      return
    }
    this.openSocket()
  }

  /** Manual retry from the UI after a failed connection. */
  reconnectNow(): void {
    if (this.closed) return
    this.clearReconnectTimer()
    this.attempt = 0
    this.openSocket()
  }

  close(): void {
    if (this.closed) return
    this.closed = true
    this.stopHeartbeat()
    this.clearReconnectTimer()
    const socket = this.socket
    this.socket = null
    if (socket) {
      try {
        if (socket.readyState === WebSocket.OPEN) {
          socket.send(JSON.stringify({ type: 'LEAVE', roomId: this.options.roomId }))
        }
        socket.close(1000, 'Client left')
      } catch {
        // Ignore shutdown errors.
      }
    }
    this.setPhase('closed')
  }

  canSend(): boolean {
    return this.socket?.readyState === WebSocket.OPEN && this.phase === 'live'
  }

  sendCommand(
    type: Extract<CommandAction, 'PLAY' | 'PAUSE' | 'SEEK' | 'SET_SPEED' | 'ENDED'>,
    payload: { positionMs?: number; playbackSpeed?: number } = {},
  ): boolean {
    if (!this.canSend()) return false
    const message: SyncMessage = {
      type,
      roomId: this.options.roomId,
      commandId: randomId(),
      clientTimestamp: Date.now(),
      expectedSequence: this.latestSequence,
      ...(payload.positionMs !== undefined ? { positionMs: Math.max(0, Math.round(payload.positionMs)) } : {}),
      ...(payload.playbackSpeed !== undefined ? { playbackSpeed: payload.playbackSpeed } : {}),
    }
    return this.send(message)
  }

  /** Chat is independent of playback control policy; every participant may send. */
  sendChat(text: string): boolean {
    if (!this.canSend()) return false
    const trimmed = text.trim()
    if (trimmed.length === 0 || trimmed.length > CHAT_MAX_LENGTH) return false
    return this.send({
      type: 'CHAT_SEND',
      roomId: this.options.roomId,
      text: trimmed,
      clientTimestamp: Date.now(),
    })
  }

  private openSocket(): void {
    if (this.closed) return
    this.stopHeartbeat()
    this.setPhase(this.attempt === 0 ? 'connecting' : 'reconnecting')

    let socket: WebSocket
    try {
      socket = new WebSocket(buildWebSocketUrl(this.options.wsPath, this.options.sessionToken))
    } catch (error) {
      this.options.logger?.(`WebSocket construction failed: ${String(error)}`)
      this.scheduleReconnect()
      return
    }

    this.socket = socket
    socket.onopen = () => {
      if (this.closed || this.socket !== socket) return
      this.attempt = 0
      this.lastMessageAt = Date.now()
      this.setPhase('live')
      this.startHeartbeat()
      this.sendClockPing()
    }

    socket.onmessage = (event: MessageEvent<string>) => {
      if (this.socket !== socket) return
      this.lastMessageAt = Date.now()
      const message = parseMessage(event.data)
      if (!message) return
      this.observeClock(message)
      if (message.type === 'SYNC_PING') {
        this.send({
          type: 'SYNC_PONG',
          roomId: this.options.roomId,
          text: message.text,
          serverTime: Date.now(),
        })
      }
      if ((message.type === 'STATE_UPDATE' || message.type === 'MEDIA_CHANGED') && typeof message.sequence === 'number') {
        this.latestSequence = Math.max(this.latestSequence, message.sequence)
      }
      this.options.onMessage(message)
    }

    socket.onerror = () => {
      // The close handler owns reconnect decisions and user-facing errors.
      this.options.logger?.('WebSocket transport error')
    }

    socket.onclose = (event: CloseEvent) => {
      if (this.socket !== socket) return
      this.socket = null
      this.stopHeartbeat()
      if (this.closed) return

      if (event.code === 1008 || event.code === 1003) {
        this.closed = true
        this.setPhase('failed', event.reason || 'The host rejected this session.')
        this.options.onFatal(
          event.reason?.trim() ||
            'This session is no longer valid. Ask the host for a fresh invite link.',
        )
        return
      }

      if (event.code === 1000) {
        this.closed = true
        this.setPhase('closed', event.reason || 'The room connection closed.')
        return
      }

      this.scheduleReconnect()
    }
  }

  private scheduleReconnect(): void {
    if (this.closed) return
    if (this.options.autoReconnect === false) {
      this.setPhase('failed', 'The connection to the host was lost.')
      return
    }
    if (document.visibilityState === 'hidden') {
      this.setPhase('reconnecting', 'Waiting for the tab to become active again.')
      return
    }
    this.attempt += 1
    if (this.attempt > MAX_RECONNECT_ATTEMPTS) {
      this.setPhase('failed', 'Could not reach the host. Check the link and your connection, then retry.')
      return
    }
    const backoff = Math.min(10_000, 750 * 2 ** (this.attempt - 1))
    const jitter = Math.round(Math.random() * 250)
    this.setPhase('reconnecting', `Reconnecting (attempt ${this.attempt})…`)
    this.clearReconnectTimer()
    this.reconnectTimer = window.setTimeout(() => {
      this.reconnectTimer = null
      this.openSocket()
    }, backoff + jitter)
  }

  private startHeartbeat(): void {
    this.stopHeartbeat()
    this.heartbeatTimer = window.setInterval(() => {
      if (this.closed) return
      if (document.visibilityState === 'hidden') return
      if (Date.now() - this.lastMessageAt > STALE_MS) {
        this.options.logger?.('WebSocket heartbeat timed out; reconnecting')
        const socket = this.socket
        this.socket = null
        this.stopHeartbeat()
        try {
          socket?.close(4000, 'Heartbeat timeout')
        } catch {
          // Ignore.
        }
        this.scheduleReconnect()
        return
      }
      this.sendClockPing()
    }, HEARTBEAT_MS)
  }

  private stopHeartbeat(): void {
    if (this.heartbeatTimer !== null) {
      window.clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
  }

  private clearReconnectTimer(): void {
    if (this.reconnectTimer !== null) {
      window.clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
  }

  private sendClockPing(): void {
    const id = randomId()
    this.pendingPings.set(id, Date.now())
    if (this.pendingPings.size > 32) {
      const cutoff = Date.now() - 120_000
      for (const [key, sentAt] of this.pendingPings) {
        if (sentAt < cutoff) this.pendingPings.delete(key)
      }
    }
    this.send({
      type: 'SYNC_PING',
      roomId: this.options.roomId,
      text: id,
      clientTimestamp: Date.now(),
    })
  }

  private observeClock(message: SyncMessage): void {
    if (message.type === 'SYNC_PONG' && message.text) {
      const sentAt = this.pendingPings.get(message.text)
      this.pendingPings.delete(message.text)
      if (sentAt && message.serverTime) {
        this.options.clock.sample(message.serverTime, Date.now() - sentAt, Date.now())
        return
      }
    }
    if (message.serverTime) {
      this.options.clock.sample(message.serverTime)
    }
  }

  private send(message: SyncMessage): boolean {
    const socket = this.socket
    if (!socket || socket.readyState !== WebSocket.OPEN) return false
    try {
      socket.send(JSON.stringify(message))
      return true
    } catch {
      return false
    }
  }

  private setPhase(phase: ConnectionPhase, detail?: string): void {
    this.phase = phase
    this.options.onPhase(phase, detail)
  }
}

function parseMessage(raw: unknown): SyncMessage | null {
  if (typeof raw !== 'string') return null
  try {
    const value: unknown = JSON.parse(raw)
    if (typeof value !== 'object' || value === null) return null
    const candidate = value as Record<string, unknown>
    if (typeof candidate.type !== 'string') return null
    return value as SyncMessage
  } catch {
    return null
  }
}

function randomId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `c_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 10)}`
}
