import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  buildMediaUrl,
  buildWebSocketUrl,
  getRoom,
  joinRoom,
  refreshMediaToken,
} from './api'
import { ApiError } from './errors'

const fetchMock = vi.fn()

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('WatchWave API client', () => {
  beforeEach(() => {
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('requests a room preview with an encoded invite token', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({ roomId: 'abc123', roomName: 'Film', hostName: 'Host', status: 'ACTIVE' }),
    )
    await getRoom('abc123', 'a b+c')
    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/v1/rooms/abc123?t=a%20b%2Bc')
    expect(init.credentials).toBe('omit')
    expect(init.cache).toBe('no-store')
  })

  it('turns a structured error into an ApiError', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({ error: 'INVITE_INVALID', message: 'Invite is invalid.' }, 403),
    )
    const error = await getRoom('abc123', 'bad').catch((value: unknown) => value)
    expect(error).toMatchObject({
      name: 'ApiError',
      status: 403,
      code: 'INVITE_INVALID',
      message: 'Invite is invalid.',
    })
    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).isAuthFailure).toBe(true)
  })

  it('reports an unreachable host without leaking the URL', async () => {
    fetchMock.mockRejectedValue(new TypeError('network down'))
    const error = await getRoom('abc123', 'token').catch((value: unknown) => value)
    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).code).toBe('NETWORK_ERROR')
    expect((error as ApiError).message).not.toContain('token')
  })

  it('normalizes join responses whose optional defaults were omitted', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({
        room: { roomId: 'abc123', roomName: 'Film', hostName: 'Host', status: 'ACTIVE' },
        participant: { id: 'p_1', displayName: 'Maya', controlsAllowed: true },
        sessionToken: 'session',
        mediaToken: 'media',
        mediaUrlPath: '/api/v1/rooms/abc123/video',
        wsPath: '/api/v1/rooms/abc123/sync',
      }),
    )
    const joined = await joinRoom('abc123', ' Maya ', 'invite')
    expect(joined.canControl).toBe(true)
    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(JSON.parse(String(init.body))).toEqual({
      displayName: 'Maya',
      inviteToken: 'invite',
    })
  })

  it('refreshes media tokens with the session bearer token', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ mediaToken: 'fresh', expiresAt: 123_456 }))
    const result = await refreshMediaToken('abc123', 'session-token')
    expect(result.mediaToken).toBe('fresh')
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/v1/rooms/abc123/media-token')
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer session-token')
  })

  it('rejects a successful but malformed response', async () => {
    fetchMock.mockResolvedValue(new Response('', { status: 200 }))
    await expect(getRoom('abc123', 't')).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
  })

  it('builds media URLs with an encoded token', () => {
    expect(buildMediaUrl('/api/v1/rooms/abc123/video', 'a b+c')).toBe(
      '/api/v1/rooms/abc123/video?t=a%20b%2Bc',
    )
    expect(buildMediaUrl('/video?x=1', 'tok')).toBe('/video?x=1&t=tok')
  })

  it('builds a ws URL on the current origin', () => {
    const url = buildWebSocketUrl('/api/v1/rooms/abc123/sync', 'tok en')
    expect(url).toBe('ws://localhost:3000/api/v1/rooms/abc123/sync?token=tok%20en')
  })
})
