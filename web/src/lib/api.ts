import { ApiError, isErrorResponse } from './errors'
import {
  API_PREFIX,
  type ActionResponse,
  type HealthResponse,
  type JoinResponse,
  type MediaTokenResponse,
  type RoomSummary,
  type TunnelStatus,
} from './protocol'

const configuredOrigin = (import.meta.env.VITE_WATCHWAVE_ORIGIN ?? '').trim().replace(/\/+$/, '')

/** Empty means same-origin, which is how the APK serves the client. */
export function apiOrigin(): string {
  return configuredOrigin
}

export function apiUrl(path: string): string {
  const normalized = path.startsWith('/') ? path : `/${path}`
  return `${configuredOrigin}${normalized}`
}

export function absoluteUrl(path: string): string {
  if (/^https?:\/\//i.test(path)) return path
  const normalized = path.startsWith('/') ? path : `/${path}`
  return `${configuredOrigin}${normalized}`
}

interface RequestOptions {
  method?: 'GET' | 'POST'
  body?: unknown
  token?: string
  signal?: AbortSignal
  allowEmpty?: boolean
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  if (options.token) headers.Authorization = `Bearer ${options.token}`

  let response: Response
  try {
    response = await fetch(apiUrl(path), {
      method: options.method ?? 'GET',
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: options.signal,
      cache: 'no-store',
      credentials: 'omit',
      referrerPolicy: 'no-referrer',
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new ApiError(0, 'NETWORK_ERROR', 'The host could not be reached. Check the link and your connection.')
  }

  const text = await response.text()
  let parsed: unknown = null
  if (text) {
    try {
      parsed = JSON.parse(text)
    } catch {
      parsed = null
    }
  }

  if (!response.ok) {
    if (isErrorResponse(parsed)) {
      throw new ApiError(response.status, parsed.error, parsed.message, parsed.retryAfterMs ?? null)
    }
    throw new ApiError(
      response.status,
      `HTTP_${response.status}`,
      response.status === 429
        ? 'The host is receiving too many requests. Wait a moment and try again.'
        : 'The host rejected the request.',
    )
  }

  if (parsed === null && !options.allowEmpty) {
    throw new ApiError(response.status, 'INVALID_RESPONSE', 'The host returned an unexpected response.')
  }
  return parsed as T
}

export async function getHealth(signal?: AbortSignal): Promise<HealthResponse> {
  return request<HealthResponse>(`${API_PREFIX}/health`, { signal })
}

export async function getTunnelStatus(signal?: AbortSignal): Promise<TunnelStatus> {
  return request<TunnelStatus>(`${API_PREFIX}/tunnel/status`, { signal })
}

export async function getRoom(roomId: string, inviteToken?: string | null, signal?: AbortSignal): Promise<RoomSummary> {
  const query = inviteToken ? `?t=${encodeURIComponent(inviteToken)}` : ''
  return request<RoomSummary>(`${API_PREFIX}/rooms/${encodeURIComponent(roomId)}${query}`, { signal })
}

export async function joinRoom(
  roomId: string,
  displayName: string,
  inviteToken?: string | null,
): Promise<JoinResponse> {
  const response = await request<JoinResponse>(`${API_PREFIX}/rooms/${encodeURIComponent(roomId)}/join`, {
    method: 'POST',
    body: {
      displayName: displayName.trim().slice(0, 32),
      inviteToken: inviteToken ?? undefined,
    },
  })
  return normalizeJoin(response)
}

export async function reconnectRoom(roomId: string, sessionToken: string): Promise<JoinResponse> {
  const response = await request<JoinResponse>(`${API_PREFIX}/rooms/${encodeURIComponent(roomId)}/reconnect`, {
    method: 'POST',
    body: { sessionToken },
  })
  return normalizeJoin(response)
}

export async function leaveRoom(roomId: string, sessionToken: string): Promise<ActionResponse> {
  return request<ActionResponse>(`${API_PREFIX}/rooms/${encodeURIComponent(roomId)}/leave`, {
    method: 'POST',
    body: { sessionToken },
    token: sessionToken,
    allowEmpty: true,
  })
}

export async function refreshMediaToken(roomId: string, sessionToken: string): Promise<MediaTokenResponse> {
  return request<MediaTokenResponse>(`${API_PREFIX}/rooms/${encodeURIComponent(roomId)}/media-token`, {
    method: 'POST',
    body: {},
    token: sessionToken,
  })
}

function normalizeJoin(response: JoinResponse): JoinResponse {
  const isHost = response.participant.isHost ?? false
  const controlsAllowed = response.participant.controlsAllowed ?? true
  return {
    ...response,
    canControl: response.canControl ?? (isHost || controlsAllowed),
  }
}

/**
 * The host URL embedded in a video element can only carry a query token; browsers
 * cannot attach an Authorization header to media range requests.
 */
export function buildMediaUrl(mediaPath: string, mediaToken: string): string {
  const base = absoluteUrl(mediaPath)
  const separator = base.includes('?') ? '&' : '?'
  return `${base}${separator}t=${encodeURIComponent(mediaToken)}`
}

/** Builds a WebSocket URL on the API origin, preserving ws/wss for http/https. */
export function buildWebSocketUrl(wsPath: string, sessionToken: string): string {
  const path = wsPath.startsWith('/') ? wsPath : `/${wsPath}`
  const target = configuredOrigin ? new URL(configuredOrigin, window.location.href) : window.location
  const protocol = target.protocol === 'https:' ? 'wss:' : 'ws:'
  const host = target.host || window.location.host
  return `${protocol}//${host}${path}?token=${encodeURIComponent(sessionToken)}`
}
