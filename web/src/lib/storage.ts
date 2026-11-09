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
  /** Optional preview metadata. Never used for authentication. */
  roomName?: string
  hostName?: string
  mediaTitle?: string
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
      roomName: typeof candidate.roomName === 'string' ? candidate.roomName.slice(0, 64) : undefined,
      hostName: typeof candidate.hostName === 'string' ? candidate.hostName.slice(0, 32) : undefined,
      mediaTitle: typeof candidate.mediaTitle === 'string' ? candidate.mediaTitle.slice(0, 120) : undefined,
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

export interface StoredSessionSummary extends StoredSession {
  roomId: string
}

/**
 * Rooms joined in this tab. The invite token stays in sessionStorage, so the
 * list disappears when the tab closes and never leaks into a shared device.
 */
export function listSessions(): StoredSessionSummary[] {
  const store = safeSession()
  if (!store) return []
  const sessions: StoredSessionSummary[] = []
  try {
    for (let index = 0; index < store.length; index += 1) {
      const key = store.key(index)
      if (!key?.startsWith(SESSION_PREFIX)) continue
      const roomId = key.slice(SESSION_PREFIX.length)
      const session = loadSession(roomId)
      if (session) sessions.push({ ...session, roomId })
    }
  } catch {
    return []
  }
  return sessions.sort((a, b) => b.savedAt - a.savedAt)
}

export function clearAllSessions(): number {
  const store = safeSession()
  if (!store) return 0
  let removed = 0
  try {
    const keys: string[] = []
    for (let index = 0; index < store.length; index += 1) {
      const key = store.key(index)
      if (key?.startsWith(SESSION_PREFIX)) keys.push(key)
    }
    for (const key of keys) {
      store.removeItem(key)
      removed += 1
    }
  } catch {
    return removed
  }
  return removed
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
