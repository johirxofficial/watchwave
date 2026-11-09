import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ServerClock } from '../lib/clock'
import type { SyncMessage } from '../lib/protocol'
import { SyncClient, type ConnectionPhase } from './sync-client'

class FakeWebSocket {
  static readonly CONNECTING = 0
  static readonly OPEN = 1
  static readonly CLOSING = 2
  static readonly CLOSED = 3
  static instances: FakeWebSocket[] = []

  readyState = FakeWebSocket.CONNECTING
  readonly sent: string[] = []
  onopen: ((event: Event) => void) | null = null
  onmessage: ((event: MessageEvent<string>) => void) | null = null
  onerror: ((event: Event) => void) | null = null
  onclose: ((event: CloseEvent) => void) | null = null

  constructor(readonly url: string) {
    FakeWebSocket.instances.push(this)
  }

  send(data: string): void {
    this.sent.push(data)
  }

  close(code = 1000, reason = ''): void {
    if (this.readyState === FakeWebSocket.CLOSED) return
    this.readyState = FakeWebSocket.CLOSED
    this.onclose?.({ code, reason } as CloseEvent)
  }

  open(): void {
    this.readyState = FakeWebSocket.OPEN
    this.onopen?.(new Event('open'))
  }

  receive(message: SyncMessage): void {
    this.onmessage?.({ data: JSON.stringify(message) } as MessageEvent<string>)
  }

  lastSent(): Record<string, unknown> {
    return JSON.parse(this.sent[this.sent.length - 1] ?? '{}') as Record<string, unknown>
  }
}

function buildClient(overrides: Partial<ConstructorParameters<typeof SyncClient>[0]> = {}) {
  const phases: ConnectionPhase[] = []
  const messages: SyncMessage[] = []
  const onFatal = vi.fn()
  const clock = new ServerClock()
  const client = new SyncClient({
    roomId: 'abc123',
    sessionToken: 'session token',
    wsPath: '/api/v1/rooms/abc123/sync',
    clock,
    onMessage: (message) => messages.push(message),
    onPhase: (phase) => phases.push(phase),
    onFatal,
    ...overrides,
  })
  return { client, phases, messages, onFatal, clock }
}

describe('SyncClient', () => {
  beforeEach(() => {
    FakeWebSocket.instances = []
    vi.stubGlobal('WebSocket', FakeWebSocket)
    Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true })
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('connects with the session token and reports live', () => {
    const { client, phases } = buildClient()
    client.connect()
    const socket = FakeWebSocket.instances[0]
    expect(socket.url).toBe('ws://localhost:3000/api/v1/rooms/abc123/sync?token=session%20token')
    expect(phases).toContain('connecting')
    socket.open()
    expect(phases.at(-1)).toBe('live')
    expect(client.canSend()).toBe(true)
    client.close()
  })

  it('sends commands with a unique id and the latest sequence', () => {
    const { client } = buildClient()
    client.connect()
    const socket = FakeWebSocket.instances[0]
    socket.open()
    socket.receive({
      type: 'STATE_UPDATE',
      mediaId: 'm_1',
      isPlaying: true,
      positionMs: 1_000,
      sequence: 7,
      serverTimestamp: Date.now(),
    })
    expect(client.sendCommand('SEEK', { positionMs: 1_234.6 })).toBe(true)
    expect(socket.lastSent()).toMatchObject({
      type: 'SEEK',
      roomId: 'abc123',
      positionMs: 1_235,
      expectedSequence: 7,
    })
    expect(typeof socket.lastSent().commandId).toBe('string')
    client.close()
  })

  it('sends trimmed chat messages and rejects empty or oversized text', () => {
    const { client } = buildClient()
    client.connect()
    const socket = FakeWebSocket.instances[0]
    socket.open()
    expect(client.sendChat('  hello room  ')).toBe(true)
    expect(socket.lastSent()).toMatchObject({ type: 'CHAT_SEND', roomId: 'abc123', text: 'hello room' })
    expect(client.sendChat('   ')).toBe(false)
    expect(client.sendChat('x'.repeat(501))).toBe(false)
    client.close()
    expect(client.sendChat('after close')).toBe(false)
  })

  it('answers server pings with the same correlation id', () => {
    const { client } = buildClient()
    client.connect()
    const socket = FakeWebSocket.instances[0]
    socket.open()
    socket.receive({ type: 'SYNC_PING', text: 'ping-1', serverTime: Date.now() })
    expect(socket.lastSent()).toMatchObject({ type: 'SYNC_PONG', text: 'ping-1' })
    client.close()
  })

  it('samples the host clock from state frames', () => {
    const { client, clock } = buildClient()
    client.connect()
    const socket = FakeWebSocket.instances[0]
    socket.open()
    socket.receive({ type: 'STATE_UPDATE', serverTime: Date.now() + 5_000, mediaId: 'm', sequence: 1 })
    expect(clock.sampleCount).toBeGreaterThan(0)
    expect(clock.offset).toBeGreaterThan(4_000)
    client.close()
  })

  it('treats policy close codes as fatal', () => {
    const { client, onFatal, phases } = buildClient()
    client.connect()
    const socket = FakeWebSocket.instances[0]
    socket.open()
    socket.close(1008, 'This session is expired')
    expect(onFatal).toHaveBeenCalledWith('This session is expired')
    expect(phases.at(-1)).toBe('failed')
    expect(client.canSend()).toBe(false)
  })

  it('reconnects with backoff after an unexpected close', () => {
    vi.useFakeTimers()
    const { client, phases } = buildClient()
    client.connect()
    FakeWebSocket.instances[0].open()
    FakeWebSocket.instances[0].close(1006, 'gone')
    expect(phases.at(-1)).toBe('reconnecting')
    vi.advanceTimersByTime(2_000)
    expect(FakeWebSocket.instances).toHaveLength(2)
    client.close()
  })

  it('does not reconnect after an intentional close', () => {
    vi.useFakeTimers()
    const { client, phases } = buildClient()
    client.connect()
    FakeWebSocket.instances[0].open()
    client.close()
    expect(phases.at(-1)).toBe('closed')
    vi.advanceTimersByTime(20_000)
    expect(FakeWebSocket.instances).toHaveLength(1)
  })

  it('can reopen manually after an unexpected close', () => {
    vi.useFakeTimers()
    const { client } = buildClient()
    client.connect()
    FakeWebSocket.instances[0].open()
    FakeWebSocket.instances[0].close(1006, 'gone')
    client.reconnectNow()
    expect(FakeWebSocket.instances).toHaveLength(2)
    client.close()
  })
})
