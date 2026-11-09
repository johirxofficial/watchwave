import { beforeEach, describe, expect, it } from 'vitest'
import {
  clearSession,
  loadDisplayName,
  loadSession,
  loadVolume,
  saveDisplayName,
  saveSession,
  saveVolume,
} from './storage'

describe('session storage', () => {
  beforeEach(() => {
    window.sessionStorage.clear()
    window.localStorage.clear()
  })

  it('round-trips a per-room session', () => {
    saveSession('abc123', {
      sessionToken: 'session-secret',
      mediaToken: 'media-secret',
      inviteToken: 'invite-secret',
      displayName: 'Johir',
    })
    expect(loadSession('abc123')).toMatchObject({
      v: 1,
      sessionToken: 'session-secret',
      mediaToken: 'media-secret',
      inviteToken: 'invite-secret',
      displayName: 'Johir',
    })
    expect(loadSession('other123')).toBeNull()
  })

  it('clears one room without affecting another', () => {
    saveSession('abc123', {
      sessionToken: 'a',
      mediaToken: 'b',
      inviteToken: null,
      displayName: 'A',
    })
    saveSession('def456', {
      sessionToken: 'c',
      mediaToken: 'd',
      inviteToken: null,
      displayName: 'D',
    })
    clearSession('abc123')
    expect(loadSession('abc123')).toBeNull()
    expect(loadSession('def456')?.sessionToken).toBe('c')
  })

  it('ignores malformed or future-version payloads', () => {
    window.sessionStorage.setItem('watchwave.session.abc123', '{not json')
    expect(loadSession('abc123')).toBeNull()
    window.sessionStorage.setItem(
      'watchwave.session.abc123',
      JSON.stringify({ v: 99, sessionToken: 'x', mediaToken: 'y', displayName: 'z' }),
    )
    expect(loadSession('abc123')).toBeNull()
  })

  it('persists display name and clamps volume', () => {
    saveDisplayName('  Johir  ')
    expect(loadDisplayName()).toBe('Johir')
    saveVolume(0.35)
    expect(loadVolume()).toBeCloseTo(0.35)
    saveVolume(3)
    expect(loadVolume()).toBe(1)
  })
})
