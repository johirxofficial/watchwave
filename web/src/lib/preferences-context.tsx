import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import {
  DEFAULT_PREFERENCES,
  applyPreferences,
  clearPreferences,
  loadPreferences,
  savePreferences,
  type Preferences,
} from './preferences'

interface PreferencesContextValue {
  preferences: Preferences
  setPreference: <K extends keyof Preferences>(key: K, value: Preferences[K]) => void
  resetPreferences: () => void
}

const PreferencesContext = createContext<PreferencesContextValue | null>(null)

export function PreferencesProvider({ children }: { children: ReactNode }) {
  const [preferences, setPreferences] = useState<Preferences>(() => loadPreferences())

  useEffect(() => {
    applyPreferences(preferences)
    savePreferences(preferences)
  }, [preferences])

  useEffect(() => {
    if (!preferences.reduceMotion) return
    // Add a class too: some older browsers do not support the attribute selector
    // used by the stylesheet, while the class is universal.
    document.documentElement.classList.add('reduce-motion')
    return () => document.documentElement.classList.remove('reduce-motion')
  }, [preferences.reduceMotion])

  const setPreference = useCallback(<K extends keyof Preferences>(key: K, value: Preferences[K]) => {
    setPreferences((current) => ({ ...current, [key]: value }))
  }, [])

  const resetPreferences = useCallback(() => {
    clearPreferences()
    setPreferences({ ...DEFAULT_PREFERENCES })
  }, [])

  const value = useMemo(
    () => ({ preferences, setPreference, resetPreferences }),
    [preferences, resetPreferences, setPreference],
  )

  return <PreferencesContext.Provider value={value}>{children}</PreferencesContext.Provider>
}

export function usePreferences(): PreferencesContextValue {
  const context = useContext(PreferencesContext)
  if (!context) throw new Error('usePreferences must be used inside PreferencesProvider')
  return context
}
