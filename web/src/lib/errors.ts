import type { ErrorResponse } from './protocol'

/** A structured failure returned by the WatchWave API. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly retryAfterMs: number | null

  constructor(status: number, code: string, message: string, retryAfterMs: number | null = null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.retryAfterMs = retryAfterMs
  }

  get isAuthFailure(): boolean {
    return this.status === 401 || this.status === 403 || this.code === 'UNAUTHORIZED' || this.code === 'INVALID_TOKEN'
  }

  get isGone(): boolean {
    return this.status === 410 || this.code === 'ROOM_CLOSED' || this.code === 'ROOM_EXPIRED'
  }

  get isRetryable(): boolean {
    return this.status === 429 || this.status >= 500 || this.code === 'RATE_LIMITED'
  }
}

export function isApiError(value: unknown): value is ApiError {
  return value instanceof ApiError
}

export function isErrorResponse(value: unknown): value is ErrorResponse {
  if (typeof value !== 'object' || value === null) return false
  const candidate = value as Record<string, unknown>
  return typeof candidate.error === 'string' && typeof candidate.message === 'string'
}

/** Human-readable message that never includes a token or request URL. */
export function describeError(error: unknown): string {
  if (isApiError(error)) return error.message
  if (error instanceof DOMException && error.name === 'AbortError') return 'The request was cancelled.'
  if (error instanceof TypeError) {
    return 'The host could not be reached. Check the link and your connection.'
  }
  if (error instanceof Error && error.message) return error.message
  return 'Something went wrong. Try again.'
}
