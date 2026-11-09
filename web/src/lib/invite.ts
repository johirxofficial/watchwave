/**
 * Parses any link the Android host app can produce:
 *   https://host/room/ROOM?t=TOKEN
 *   http://192.168.1.2:8080/room/ROOM?t=TOKEN
 *   watchwave://room/ROOM?t=TOKEN
 *   ROOM  (bare room code)
 */

const ROOM_ID = /^[a-z0-9]{6,32}$/

export interface ParsedInvite {
  roomId: string
  inviteToken: string | null
}

export function parseInvite(raw: string): ParsedInvite | null {
  const value = raw.trim()
  if (!value) return null

  const marker = value.indexOf('/room/')
  if (marker >= 0) {
    const rest = value.slice(marker + '/room/'.length)
    const path = rest.split(/[?#]/, 1)[0].replace(/^\/+|\/+$/g, '')
    if (!ROOM_ID.test(path)) return null
    return { roomId: path, inviteToken: readToken(rest) }
  }

  if (ROOM_ID.test(value)) {
    return { roomId: value, inviteToken: null }
  }

  return null
}

export function readToken(value: string): string | null {
  const queryStart = value.indexOf('?')
  if (queryStart < 0) return null
  const hashStart = value.indexOf('#', queryStart)
  const query = value.slice(queryStart + 1, hashStart < 0 ? undefined : hashStart)
  const params = new URLSearchParams(query)
  const token = params.get('t')?.trim()
  return token ? token : null
}

export function buildRoomPath(roomId: string, inviteToken?: string | null): string {
  return `/room/${encodeURIComponent(roomId)}${inviteToken ? `?t=${encodeURIComponent(inviteToken)}` : ''}`
}

export function isValidRoomId(value: string): boolean {
  return ROOM_ID.test(value)
}
