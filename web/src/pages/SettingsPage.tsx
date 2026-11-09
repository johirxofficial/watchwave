import { useMemo, useState } from 'react'
import { AppShell } from '../components/AppShell'
import {
  CheckIcon,
  CopyIcon,
  ExternalLinkIcon,
  GaugeIcon,
  GithubIcon,
  KeyboardIcon,
  MonitorSmartphoneIcon,
  PaletteIcon,
  PlayIcon,
  RefreshIcon,
  ServerIcon,
  ShieldIcon,
  SlidersIcon,
  TrashIcon,
  WaveIcon,
} from '../components/Icons'
import { Banner, Button, Card, KeyCap, Pill, Segmented, SettingsRow, Toggle } from '../components/ui'
import { usePreferences } from '../lib/preferences-context'
import type { Accent, Density, SkipSeconds } from '../lib/preferences'
import { clearAllSessions } from '../lib/storage'
import { cn } from '../lib/cn'

const REPO_URL = 'https://github.com/johirxofficial/watchwave'

const SECTIONS = [
  { id: 'playback', label: 'Playback', icon: PlayIcon },
  { id: 'interface', label: 'Interface', icon: PaletteIcon },
  { id: 'connection', label: 'Connection', icon: ServerIcon },
  { id: 'advanced', label: 'Advanced', icon: SlidersIcon },
  { id: 'about', label: 'About', icon: WaveIcon },
]

export function SettingsPage() {
  const { preferences, setPreference, resetPreferences } = usePreferences()
  const [toast, setToast] = useState<string | null>(null)
  const [sessionsCleared, setSessionsCleared] = useState(0)

  const diagnostics = useMemo(
    () =>
      [
        `WatchWave viewer`,
        `URL: ${window.location.href}`,
        `Protocol: ${window.location.protocol === 'https:' ? 'HTTPS/WSS' : 'HTTP/WS'}`,
        `Secure context: ${window.isSecureContext ? 'yes' : 'no'}`,
        `User agent: ${navigator.userAgent}`,
        `Languages: ${navigator.languages?.join(', ') || navigator.language}`,
        `Screen: ${window.screen.width}×${window.screen.height} @ ${window.devicePixelRatio}x`,
        `Viewport: ${window.innerWidth}×${window.innerHeight}`,
        `Reduced motion (OS): ${window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'yes' : 'no'}`,
        `PiP supported: ${'pictureInPictureEnabled' in document ? document.pictureInPictureEnabled : false}`,
        `Preferences: ${JSON.stringify(preferences)}`,
      ].join('\n'),
    [preferences],
  )

  const flash = (message: string) => {
    setToast(message)
    window.setTimeout(() => setToast(null), 2400)
  }

  const copyDiagnostics = async () => {
    try {
      await navigator.clipboard.writeText(diagnostics)
      flash('Diagnostics copied to the clipboard')
    } catch {
      flash('Clipboard is unavailable in this browser')
    }
  }

  const resetLocalData = () => {
    const cleared = clearAllSessions()
    setSessionsCleared(cleared)
    resetPreferences()
    try {
      window.localStorage.removeItem('watchwave.displayName')
      window.localStorage.removeItem('watchwave.volume')
    } catch {
      // Ignore.
    }
    flash(`Local data cleared${cleared ? ` · ${cleared} session${cleared === 1 ? '' : 's'} removed` : ''}`)
  }

  return (
    <AppShell active="settings">
      <div className="relative">
        <div className="ambient pointer-events-none absolute inset-x-0 top-0 h-80 bg-[radial-gradient(ellipse_at_top,rgb(var(--wv-accent)/.09),transparent_65%)]" />

        <div className="relative mx-auto w-full max-w-[1180px] px-4 pb-16 pt-6 sm:px-6 lg:px-10 lg:pt-10">
          <header className="flex flex-col gap-5 border-b border-white/[0.06] pb-6 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <p className="section-eyebrow">Preferences</p>
              <h1 className="mt-2 font-display text-3xl font-semibold tracking-tight text-white sm:text-4xl">
                Settings
              </h1>
              <p className="mt-2 max-w-xl text-[13px] leading-relaxed text-wave-muted">
                Saved on this device only. Nothing here is sent to the host.
              </p>
            </div>
            <div className="flex items-center gap-2">
              <Pill tone="green">
                <CheckIcon size={12} /> Auto-saved
              </Pill>
              <Button variant="ghost" size="sm" onClick={() => resetPreferences()}>
                <RefreshIcon size={14} /> Reset defaults
              </Button>
            </div>
          </header>

          <div className="mt-6 grid gap-8 lg:grid-cols-[190px_minmax(0,1fr)] lg:gap-10">
            <nav className="hidden lg:block" aria-label="Settings sections">
              <div className="sticky top-6 space-y-1">
                {SECTIONS.map((section) => (
                  <a
                    key={section.id}
                    href={`#${section.id}`}
                    className="nav-item"
                    onClick={(event) => {
                      event.preventDefault()
                      document.getElementById(section.id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
                    }}
                  >
                    <section.icon size={17} />
                    {section.label}
                  </a>
                ))}
                <div className="mt-4 rounded-xl border border-white/[0.06] bg-white/[0.02] p-3">
                  <p className="text-[10px] font-semibold uppercase tracking-[0.16em] text-wave-muted/60">Tip</p>
                  <p className="mt-1.5 text-[11px] leading-relaxed text-wave-muted">
                    Press <KeyCap>?</KeyCap> in a room to see every keyboard shortcut.
                  </p>
                </div>
              </div>
            </nav>

            <div className="min-w-0 space-y-10">
              <Section
                id="playback"
                icon={<PlayIcon size={18} />}
                eyebrow="Playback"
                title="How the player behaves"
                description="These options affect this browser. The host still owns the room timeline."
              >
                <SettingsCard>
                  <SettingsRow
                    title="Skip distance"
                    description="Used by the skip buttons and the left/right arrow keys."
                  >
                    <Segmented<SkipSeconds>
                      label="Skip distance"
                      value={preferences.skipSeconds}
                      onChange={(value) => setPreference('skipSeconds', value)}
                      options={[5, 10, 15, 30].map((seconds) => ({
                        value: seconds as SkipSeconds,
                        label: `${seconds}s`,
                      }))}
                    />
                  </SettingsRow>
                  <div className="surface-divider" />
                  <Toggle
                    label="Show live sync drift"
                    description="Displays the clock difference between this browser and the host."
                    checked={preferences.showDrift}
                    onChange={(value) => setPreference('showDrift', value)}
                  />
                  <div className="surface-divider" />
                  <Toggle
                    label="Start muted"
                    description="Useful on a phone in public. Volume is remembered separately."
                    checked={preferences.startMuted}
                    onChange={(value) => setPreference('startMuted', value)}
                  />
                  <div className="surface-divider" />
                  <Toggle
                    label="Cinema mode"
                    description="Hide the controls automatically while a video is playing. Move the pointer or tap to reveal them."
                    checked={preferences.cinemaMode}
                    onChange={(value) => setPreference('cinemaMode', value)}
                  />
                  <div className="surface-divider" />
                  <Toggle
                    label="Keyboard shortcuts"
                    description="Space/K play, ←/→ skip, M mute, F fullscreen, ? help."
                    checked={preferences.keyboardShortcuts}
                    onChange={(value) => setPreference('keyboardShortcuts', value)}
                  />
                </SettingsCard>

                <div className="mt-4 grid gap-3 sm:grid-cols-3">
                  {[
                    { key: 'Space / K', action: 'Play or pause' },
                    { key: '← / →', action: `Skip ${preferences.skipSeconds}s` },
                    { key: 'M', action: 'Mute' },
                    { key: 'F', action: 'Fullscreen' },
                    { key: 'P', action: 'Picture-in-picture' },
                    { key: '?', action: 'Shortcut help' },
                  ].map((item) => (
                    <div key={item.key} className="rounded-xl border border-white/[0.06] bg-white/[0.02] px-3 py-2.5">
                      <KeyCap>{item.key}</KeyCap>
                      <p className="mt-1.5 text-[11px] text-wave-muted">{item.action}</p>
                    </div>
                  ))}
                </div>
              </Section>

              <Section
                id="interface"
                icon={<PaletteIcon size={18} />}
                eyebrow="Interface"
                title="Make it feel like yours"
                description="Accent, density and motion. All visual — nothing changes how media is streamed."
              >
                <SettingsCard>
                  <div className="py-3.5">
                    <p className="text-sm font-medium text-white">Accent palette</p>
                    <p className="mt-0.5 text-[12px] text-wave-muted">A small change that follows you across rooms.</p>
                    <div className="mt-4 grid grid-cols-2 gap-2 sm:grid-cols-4">
                      {ACCENTS.map((accent) => (
                        <button
                          key={accent.value}
                          type="button"
                          onClick={() => setPreference('accent', accent.value)}
                          className={cn(
                            'flex items-center gap-2.5 rounded-xl border px-3 py-2.5 text-left outline-none transition focus-visible:ring-2 focus-visible:ring-wave-cyan/60',
                            preferences.accent === accent.value
                              ? 'border-wave-cyan/40 bg-wave-cyan/[0.08]'
                              : 'border-white/[0.08] bg-white/[0.02] hover:bg-white/[0.05]',
                          )}
                        >
                          <span
                            className="size-6 shrink-0 rounded-full border border-white/20"
                            style={{ background: accent.swatch }}
                          />
                          <span className="min-w-0">
                            <span className="block text-[12px] font-medium text-white">{accent.label}</span>
                            <span className="block text-[10px] text-wave-muted">{accent.hint}</span>
                          </span>
                        </button>
                      ))}
                    </div>
                  </div>
                  <div className="surface-divider" />
                  <SettingsRow title="Density" description="Comfortable spacing, or a tighter list for small screens.">
                    <Segmented<Density>
                      label="Density"
                      value={preferences.density}
                      onChange={(value) => setPreference('density', value)}
                      options={[
                        { value: 'comfortable', label: 'Comfortable' },
                        { value: 'compact', label: 'Compact' },
                      ]}
                    />
                  </SettingsRow>
                  <div className="surface-divider" />
                  <Toggle
                    label="Compact chat"
                    description="Smaller avatars and tighter message spacing in rooms."
                    checked={preferences.compactChat}
                    onChange={(value) => setPreference('compactChat', value)}
                  />
                  <div className="surface-divider" />
                  <Toggle
                    label="Reduce motion"
                    description="Disable decorative animation and smooth scrolling. Your OS setting is still respected."
                    checked={preferences.reduceMotion}
                    onChange={(value) => setPreference('reduceMotion', value)}
                  />
                  <div className="surface-divider" />
                  <Toggle
                    label="Data saver"
                    description="Remove ambient gradients, noise and backdrop blur. Controls and video are untouched."
                    checked={preferences.dataSaver}
                    onChange={(value) => setPreference('dataSaver', value)}
                  />
                </SettingsCard>
              </Section>

              <Section
                id="connection"
                icon={<ServerIcon size={18} />}
                eyebrow="Connection"
                title="Room transport"
                description="WatchWave reconnects with the same session token and refreshes media access before it expires."
              >
                <SettingsCard>
                  <div className="flex items-start gap-3 py-3.5">
                    <span className="grid size-9 shrink-0 place-items-center rounded-xl border border-wave-green/20 bg-wave-green/[0.08] text-wave-green">
                      <ShieldIcon size={17} />
                    </span>
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <p className="text-sm font-medium text-white">Automatic reconnect</p>
                        <Pill tone="green">
                          <CheckIcon size={11} /> Always on
                        </Pill>
                      </div>
                      <p className="mt-1 text-[12px] leading-relaxed text-wave-muted">
                        Exponential backoff with session-token reuse. The player keeps its position while
                        the socket recovers.
                      </p>
                    </div>
                  </div>
                  <div className="surface-divider" />
                  <div className="flex items-start gap-3 py-3.5">
                    <span className="grid size-9 shrink-0 place-items-center rounded-xl border border-wave-cyan/20 bg-wave-cyan/[0.08] text-wave-cyan">
                      <GaugeIcon size={17} />
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="text-sm font-medium text-white">Media token refresh</p>
                      <p className="mt-1 text-[12px] leading-relaxed text-wave-muted">
                        Access tokens rotate every 45 minutes. Playback is preserved across a refresh.
                      </p>
                    </div>
                    <span className="font-mono text-[11px] text-wave-muted">45 min</span>
                  </div>
                  <div className="surface-divider" />
                  <Toggle
                    label="Show technical diagnostics in rooms"
                    description="Adds transport, clock offset, token state and protocol details to the room sidebar."
                    checked={preferences.showDiagnostics}
                    onChange={(value) => setPreference('showDiagnostics', value)}
                  />
                </SettingsCard>
              </Section>

              <Section
                id="advanced"
                icon={<SlidersIcon size={18} />}
                eyebrow="Advanced"
                title="The control room"
                description="All the technical, privacy and reset tools live here — separated from everyday playback settings."
              >
                <div className="grid gap-3 lg:grid-cols-2">
                  <Card className="p-5">
                    <div className="flex items-center justify-between">
                      <span className="text-wave-cyan">
                        <KeyboardIcon size={19} />
                      </span>
                      <Pill tone="neutral">Keyboard</Pill>
                    </div>
                    <h3 className="mt-3 text-sm font-semibold text-white">Shortcut reference</h3>
                    <p className="mt-1 text-[12px] leading-relaxed text-wave-muted">
                      Shortcuts never fire while you are typing in chat or a form field.
                    </p>
                    <div className="mt-3 grid grid-cols-2 gap-2 text-[11px] text-wave-muted">
                      {[
                        ['Space / K', 'Play · pause'],
                        ['← / →', 'Skip back · forward'],
                        ['M', 'Mute'],
                        ['F', 'Fullscreen'],
                        ['P', 'Picture-in-picture'],
                        ['?', 'Shortcut overlay'],
                      ].map(([key, action]) => (
                        <div key={key} className="flex items-center gap-2">
                          <KeyCap>{key}</KeyCap>
                          <span>{action}</span>
                        </div>
                      ))}
                    </div>
                  </Card>

                  <Card className="p-5">
                    <div className="flex items-center justify-between">
                      <span className="text-wave-cyan">
                        <MonitorSmartphoneIcon size={19} />
                      </span>
                      <Pill tone="neutral">Client</Pill>
                    </div>
                    <h3 className="mt-3 text-sm font-semibold text-white">Diagnostics snapshot</h3>
                    <p className="mt-1 text-[12px] leading-relaxed text-wave-muted">
                      Copy a local report for a bug report. It contains no session or invite token.
                    </p>
                    <div className="mt-3 flex flex-wrap gap-2">
                      <Button size="sm" variant="soft" onClick={() => void copyDiagnostics()}>
                        <CopyIcon size={14} /> Copy report
                      </Button>
                    </div>
                    <p className="mt-3 break-all font-mono text-[10px] leading-relaxed text-wave-muted/50">
                      {diagnostics.split('\n').slice(1, 4).join(' · ')}
                    </p>
                  </Card>
                </div>

                <Card className="mt-3 border-wave-red/20 p-5">
                  <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <TrashIcon size={16} className="text-wave-red" />
                        <h3 className="text-sm font-semibold text-white">Reset local data</h3>
                      </div>
                      <p className="mt-1 text-[12px] leading-relaxed text-wave-muted">
                        Removes this tab’s room sessions, display name, volume, preferences and
                        recent-room metadata. Nothing is deleted on the host.
                      </p>
                    </div>
                    <Button variant="danger" size="sm" onClick={resetLocalData} className="shrink-0">
                      Reset this browser
                    </Button>
                  </div>
                  {sessionsCleared > 0 ? (
                    <div className="mt-3">
                      <Banner kind="success">
                        Cleared {sessionsCleared} room session{sessionsCleared === 1 ? '' : 's'} from this tab.
                      </Banner>
                    </div>
                  ) : null}
                </Card>
              </Section>

              <Section
                id="about"
                icon={<WaveIcon size={18} />}
                eyebrow="About"
                title="Open source, on purpose"
                description="WatchWave is MIT licensed. Run the Android host yourself, deploy the browser client, or build something better."
              >
                <Card className="overflow-hidden">
                  <div className="grid gap-5 p-5 sm:grid-cols-[1.2fr_0.8fr] sm:p-6">
                    <div>
                      <div className="flex items-center gap-2">
                        <Pill tone="cyan">v1.1</Pill>
                        <Pill tone="neutral">MIT</Pill>
                        <Pill tone="green">
                          <CheckIcon size={11} /> no trackers
                        </Pill>
                      </div>
                      <p className="mt-3 text-[13px] leading-relaxed text-wave-muted">
                        One Android host streams the original file. Browser and Android viewers stay in
                        sync through an authoritative room clock. Chat is server-scoped and rate-limited.
                      </p>
                      <div className="mt-4 flex flex-wrap gap-2">
                        <a
                          href={REPO_URL}
                          target="_blank"
                          rel="noreferrer"
                          className="inline-flex h-9 items-center gap-1.5 rounded-lg border border-white/12 bg-white/[0.06] px-3 text-[12px] font-medium text-white transition hover:bg-white/[0.1]"
                        >
                          <GithubIcon size={15} /> Repository
                        </a>
                        <a
                          href={`${REPO_URL}/blob/main/docs/SECURITY.md`}
                          target="_blank"
                          rel="noreferrer"
                          className="inline-flex h-9 items-center gap-1.5 rounded-lg px-3 text-[12px] text-wave-muted transition hover:bg-white/[0.05] hover:text-white"
                        >
                          Security model <ExternalLinkIcon size={13} />
                        </a>
                      </div>
                    </div>
                    <div className="space-y-2 rounded-xl border border-white/[0.06] bg-black/20 p-4 text-[11px]">
                      <Row label="Protocol" value="v1 · WebSocket" />
                      <Row label="Transport" value={window.location.protocol === 'https:' ? 'HTTPS + WSS' : 'HTTP + WS'} />
                      <Row label="Storage" value="session + local" />
                      <Row label="Accounts" value="none" />
                      <Row label="Analytics" value="none" />
                      <Row label="License" value="MIT" />
                    </div>
                  </div>
                  <div className="border-t border-white/[0.06] px-5 py-3 text-[11px] text-wave-muted/60 sm:px-6">
                    Built with React, TypeScript and an honest amount of CSS. Android host uses Kotlin,
                    Compose, Ktor and Media3.
                  </div>
                </Card>
                <p className="mt-4 text-center text-[10px] text-wave-muted/45">
                  WatchWave 1.1 · © {new Date().getFullYear()} contributors · No cloud library, ever.
                </p>
              </Section>
            </div>
          </div>
        </div>

        {toast ? (
          <div className="fixed inset-x-4 bottom-24 z-50 mx-auto max-w-sm rounded-xl border border-white/10 bg-ink-800 px-4 py-3 text-center text-[13px] text-white shadow-float lg:bottom-6">
            {toast}
          </div>
        ) : null}
      </div>
    </AppShell>
  )
}

const ACCENTS: Array<{ value: Accent; label: string; hint: string; swatch: string }> = [
  { value: 'ice', label: 'Ice', hint: 'electric blue', swatch: 'linear-gradient(135deg,#5b9dff,#5eead4)' },
  { value: 'ember', label: 'Ember', hint: 'warm coral', swatch: 'linear-gradient(135deg,#ff7a59,#ffb74d)' },
  { value: 'mint', label: 'Mint', hint: 'fresh green', swatch: 'linear-gradient(135deg,#4ade80,#2dd4bf)' },
  { value: 'mono', label: 'Mono', hint: 'quiet silver', swatch: 'linear-gradient(135deg,#e2e8f0,#94a3b8)' },
]

function Section({
  id,
  icon,
  eyebrow,
  title,
  description,
  children,
}: {
  id: string
  icon: React.ReactNode
  eyebrow: string
  title: string
  description: string
  children: React.ReactNode
}) {
  return (
    <section id={id} className="scroll-mt-24">
      <div className="flex items-start gap-3">
        <span className="mt-0.5 grid size-10 shrink-0 place-items-center rounded-xl border border-white/[0.08] bg-white/[0.04] text-wave-cyan">
          {icon}
        </span>
        <div className="min-w-0">
          <p className="section-eyebrow">{eyebrow}</p>
          <h2 className="mt-1 font-display text-xl font-semibold tracking-tight text-white">{title}</h2>
          <p className="mt-1 max-w-2xl text-[12px] leading-relaxed text-wave-muted">{description}</p>
        </div>
      </div>
      <div className="mt-4">{children}</div>
    </section>
  )
}

function SettingsCard({ children }: { children: React.ReactNode }) {
  return <Card className="px-5 py-1.5 sm:px-6">{children}</Card>
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <span className="text-wave-muted">{label}</span>
      <span className="font-mono text-slate-300">{value}</span>
    </div>
  )
}
