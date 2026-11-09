import { beforeEach, describe, expect, it } from 'vitest'
import {
  DEFAULT_PREFERENCES,
  PREFERENCES_KEY,
  applyPreferences,
  clearPreferences,
  loadPreferences,
  normalizePreferences,
  preferenceAttributes,
  savePreferences,
} from './preferences'

describe('preferences', () => {
  beforeEach(() => {
    window.localStorage.clear()
    document.documentElement.removeAttribute('data-accent')
    document.documentElement.removeAttribute('data-density')
    document.documentElement.removeAttribute('data-reduce-motion')
    document.documentElement.removeAttribute('data-data-saver')
  })

  it('returns defaults when nothing is stored', () => {
    expect(loadPreferences()).toEqual(DEFAULT_PREFERENCES)
  })

  it('normalizes invalid values instead of trusting storage', () => {
    const result = normalizePreferences({
      skipSeconds: 999,
      showDrift: 'yes',
      density: 'huge',
      accent: 'neon',
      compactChat: true,
    })
    expect(result.skipSeconds).toBe(DEFAULT_PREFERENCES.skipSeconds)
    expect(result.showDrift).toBe(DEFAULT_PREFERENCES.showDrift)
    expect(result.density).toBe(DEFAULT_PREFERENCES.density)
    expect(result.accent).toBe(DEFAULT_PREFERENCES.accent)
    expect(result.compactChat).toBe(true)
  })

  it('survives malformed JSON', () => {
    window.localStorage.setItem(PREFERENCES_KEY, '{not json')
    expect(loadPreferences()).toEqual(DEFAULT_PREFERENCES)
  })

  it('round-trips valid preferences', () => {
    const next = { ...DEFAULT_PREFERENCES, skipSeconds: 30 as const, accent: 'ember' as const, cinemaMode: true }
    expect(savePreferences(next)).toBe(true)
    expect(loadPreferences()).toEqual(next)
  })

  it('clears stored preferences', () => {
    savePreferences({ ...DEFAULT_PREFERENCES, compactChat: true })
    clearPreferences()
    expect(loadPreferences()).toEqual(DEFAULT_PREFERENCES)
  })

  it('maps preferences to document attributes', () => {
    const prefs = { ...DEFAULT_PREFERENCES, accent: 'mint' as const, density: 'compact' as const, reduceMotion: true }
    expect(preferenceAttributes(prefs)).toEqual({
      'data-accent': 'mint',
      'data-density': 'compact',
      'data-reduce-motion': 'true',
      'data-data-saver': 'false',
    })
    applyPreferences(prefs)
    expect(document.documentElement.getAttribute('data-accent')).toBe('mint')
    expect(document.documentElement.getAttribute('data-density')).toBe('compact')
  })
})
