import '@testing-library/jest-dom/vitest'
import { afterEach } from 'vitest'
import { cleanup } from '@testing-library/react'

/**
 * Some jsdom/Node combinations do not expose Web Storage on the default origin.
 * The app treats storage as optional, but tests need a deterministic backing store.
 */
function memoryStorage(): Storage {
  const values = new Map<string, string>()
  return {
    get length() {
      return values.size
    },
    clear() {
      values.clear()
    },
    getItem(key: string) {
      return values.has(key) ? (values.get(key) as string) : null
    },
    key(index: number) {
      return Array.from(values.keys())[index] ?? null
    },
    removeItem(key: string) {
      values.delete(key)
    },
    setItem(key: string, value: string) {
      values.set(key, String(value))
    },
  }
}

function ensureStorage(name: 'localStorage' | 'sessionStorage'): void {
  try {
    if (window[name]) return
  } catch {
    // Access can throw on opaque origins.
  }
  Object.defineProperty(window, name, { value: memoryStorage(), configurable: true })
}

ensureStorage('localStorage')
ensureStorage('sessionStorage')

afterEach(() => {
  cleanup()
  try {
    window.sessionStorage.clear()
  } catch {
    // Ignore.
  }
  try {
    window.localStorage.clear()
  } catch {
    // Ignore.
  }
})
