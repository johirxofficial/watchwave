/**
 * Viewer preferences are deliberately local-only. Nothing here is sent to the
 * host, and no account is required. Unknown or invalid values fall back to the
 * defaults so a stale localStorage entry can never break playback.
 */

export const PREFERENCES_KEY = 'watchwave.preferences.v1'

export type Accent = 'ice' | 'ember' | 'mint' | 'mono'
export type Density = 'comfortable' | 'compact'
export type SkipSeconds = 5 | 10 | 15 | 30

export interface Preferences {
  /** How far the skip buttons and arrow keys jump. */
  skipSeconds: SkipSeconds
  /** Show the live clock-drift readout in the player. */
  showDrift: boolean
  /** Hide the interface chrome while playback is running. */
  cinemaMode: boolean
  /** Denser message bubbles and participant rows. */
  compactChat: boolean
  /** Comfortable or compact spacing throughout the app. */
  density: Density
  /** Respect the user's OS motion setting unless this is explicitly disabled. */
  reduceMotion: boolean
  /** Remove decorative gradients and ambient animation. */
  dataSaver: boolean
  /** Start the player muted on this device. */
  startMuted: boolean
  /** Show protocol and connection diagnostics inside rooms. */
  showDiagnostics: boolean
  /** Enable single-key playback shortcuts outside form fields. */
  keyboardShortcuts: boolean
  /** Accent palette. Purely visual. */
  accent: Accent
}

export const DEFAULT_PREFERENCES: Preferences = {
  skipSeconds: 10,
  showDrift: true,
  cinemaMode: false,
  compactChat: false,
  density: 'comfortable',
  reduceMotion: false,
  dataSaver: false,
  startMuted: false,
  showDiagnostics: false,
  keyboardShortcuts: true,
  accent: 'ice',
}

const ACCENTS: readonly Accent[] = ['ice', 'ember', 'mint', 'mono']
const DENSITIES: readonly Density[] = ['comfortable', 'compact']
const SKIPS: readonly SkipSeconds[] = [5, 10, 15, 30]

function boolean(value: unknown, fallback: boolean): boolean {
  return typeof value === 'boolean' ? value : fallback
}

export function normalizePreferences(input: unknown): Preferences {
  if (typeof input !== 'object' || input === null) return { ...DEFAULT_PREFERENCES }
  const value = input as Record<string, unknown>
  return {
    skipSeconds: SKIPS.includes(value.skipSeconds as SkipSeconds)
      ? (value.skipSeconds as SkipSeconds)
      : DEFAULT_PREFERENCES.skipSeconds,
    showDrift: boolean(value.showDrift, DEFAULT_PREFERENCES.showDrift),
    cinemaMode: boolean(value.cinemaMode, DEFAULT_PREFERENCES.cinemaMode),
    compactChat: boolean(value.compactChat, DEFAULT_PREFERENCES.compactChat),
    density: DENSITIES.includes(value.density as Density)
      ? (value.density as Density)
      : DEFAULT_PREFERENCES.density,
    reduceMotion: boolean(value.reduceMotion, DEFAULT_PREFERENCES.reduceMotion),
    dataSaver: boolean(value.dataSaver, DEFAULT_PREFERENCES.dataSaver),
    startMuted: boolean(value.startMuted, DEFAULT_PREFERENCES.startMuted),
    showDiagnostics: boolean(value.showDiagnostics, DEFAULT_PREFERENCES.showDiagnostics),
    keyboardShortcuts: boolean(value.keyboardShortcuts, DEFAULT_PREFERENCES.keyboardShortcuts),
    accent: ACCENTS.includes(value.accent as Accent)
      ? (value.accent as Accent)
      : DEFAULT_PREFERENCES.accent,
  }
}

function store(): Storage | null {
  try {
    return window.localStorage
  } catch {
    return null
  }
}

export function loadPreferences(): Preferences {
  const storage = store()
  if (!storage) return { ...DEFAULT_PREFERENCES }
  try {
    const raw = storage.getItem(PREFERENCES_KEY)
    if (!raw) return { ...DEFAULT_PREFERENCES }
    return normalizePreferences(JSON.parse(raw))
  } catch {
    return { ...DEFAULT_PREFERENCES }
  }
}

export function savePreferences(preferences: Preferences): boolean {
  const storage = store()
  if (!storage) return false
  try {
    storage.setItem(PREFERENCES_KEY, JSON.stringify(normalizePreferences(preferences)))
    return true
  } catch {
    return false
  }
}

export function clearPreferences(): void {
  const storage = store()
  if (!storage) return
  try {
    storage.removeItem(PREFERENCES_KEY)
  } catch {
    // Ignore unavailable storage.
  }
}

/** Attributes applied to <html> so CSS can react without re-rendering the tree. */
export function preferenceAttributes(preferences: Preferences): Record<string, string> {
  return {
    'data-accent': preferences.accent,
    'data-density': preferences.density,
    'data-reduce-motion': String(preferences.reduceMotion),
    'data-data-saver': String(preferences.dataSaver),
  }
}

export function applyPreferences(preferences: Preferences, root: HTMLElement = document.documentElement): void {
  for (const [name, value] of Object.entries(preferenceAttributes(preferences))) {
    root.setAttribute(name, value)
  }
  root.style.colorScheme = 'dark'
}
