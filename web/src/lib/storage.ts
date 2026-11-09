/**
 * Session credentials live in sessionStorage: scoped to this tab, cleared on close,
 * and never written to localStorage, cookies, logs or the URL.
 */

const SESSION_PREFIX = 'watchwave.session.'
const DISPLAY_NAME_KEY = 'watchwave.displayName'
const VOLUME_KEY = 'watchwave.volume'

export interface StoredSession {
  v: 1
  sessionToken: string
  mediaToken: string
  inviteToken: string | null
  displayName: string
  savedAt: number
}

function safeSession(): Storage | null {
  try {
    return window.sessionStorage
  } catch {
    return null
  }
}

function safeLocal(): Storage | null {
  try {
    return window.localStorage
  } catch {
    return null
  }
}

export function loadSession(roomId: string): StoredSession | null {
  const store = safeSession()
  if (!store) return null
  try {
    const raw = store.getItem(SESSION_PREFIX + roomId)
    if (!raw) return null
    const parsed: unknown = JSON.parse(raw)
    if (typeof parsed !== 'object' || parsed === null) return null
    const candidate = parsed as Partial<StoredSession>
    if (candidate.v !== 1) return null
    if (typeof candidate.sessionToken !== 'string' || !candidate.sessionToken) return null
    if (typeof candidate.mediaToken !== 'string' || !candidate.mediaToken) return null
    if (typeof candidate.displayName !== 'string') return null
    return {
      v: 1,
      sessionToken: candidate.sessionToken,
      mediaToken: candidate.mediaToken,
      inviteToken: typeof candidate.inviteToken === 'string' ? candidate.inviteToken : null,
      displayName: candidate.displayName,
      savedAt: typeof candidate.savedAt === 'number' ? candidate.savedAt : 0,
    }
  } catch {
    return null
  }
}

export function saveSession(roomId: string, session: Omit<StoredSession, 'v' | 'savedAt'>): void {
  const store = safeSession()
  if (!store) return
  try {
    const payload: StoredSession = { v: 1, ...session, savedAt: Date.now() }
    store.setItem(SESSION_PREFIX + roomId, JSON.stringify(payload))
  } catch {
    // Storage can be unavailable in hardened browsers; the live tab still works.
  }
}

export function clearSession(roomId: string): void {
  const store = safeSession()
  if (!store) return
  try {
    store.removeItem(SESSION_PREFIX + roomId)
  } catch {
    // Ignore.
  }
}

export function loadDisplayName(): string {
  const store = safeLocal()
  if (!store) return ''
  try {
    return store.getItem(DISPLAY_NAME_KEY)?.slice(0, 32) ?? ''
  } catch {
    return ''
  }
}

export function saveDisplayName(name: string): void {
  const store = safeLocal()
  if (!store) return
  try {
    store.setItem(DISPLAY_NAME_KEY, name.trim().slice(0, 32))
  } catch {
    // Ignore.
  }
}

export function loadVolume(): number {
  const store = safeLocal()
  if (!store) return 1
  try {
    const raw = Number.parseFloat(store.getItem(VOLUME_KEY) ?? '1')
    return Number.isFinite(raw) ? Math.min(1, Math.max(0, raw)) : 1
  } catch {
    return 1
  }
}

export function saveVolume(volume: number): void {
  const store = safeLocal()
  if (!store) return
  try {
    store.setItem(VOLUME_KEY, String(Math.min(1, Math.max(0, volume))))
  } catch {
    // Ignore.
  }
}
