import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { AppShell } from '../components/AppShell'
import { BrandMark } from '../components/Brand'
import {
  ArrowRightIcon,
  ChatIcon,
  ChevronRightIcon,
  ExternalLinkIcon,
  GaugeIcon,
  GithubIcon,
  GlobeIcon,
  KeyboardIcon,
  LinkIcon,
  LockIcon,
  MonitorSmartphoneIcon,
  PaletteIcon,
  PhoneIcon,
  PlayIcon,
  SettingsIcon,
  ShieldIcon,
  SlidersIcon,
  SparklesIcon,
  UsersIcon,
  WaveIcon,
  ZapIcon,
} from '../components/Icons'
import { Avatar, Banner, Button, Card, LiveDot, Pill, TextField } from '../components/ui'
import { getHealth } from '../lib/api'
import { buildRoomPath, parseInvite } from '../lib/invite'
import { listSessions, type StoredSessionSummary } from '../lib/storage'
import { formatRelative } from '../lib/format'

const REPO_URL = 'https://github.com/johirxofficial/watchwave'

export function LandingPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const [value, setValue] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [hostVersion, setHostVersion] = useState<string | null>(null)
  const [sessions, setSessions] = useState<StoredSessionSummary[]>([])

  useEffect(() => {
    setSessions(listSessions())
  }, [])

  useEffect(() => {
    if (location.hash !== '#join') return
    const timer = window.setTimeout(() => {
      document.getElementById('join')?.scrollIntoView({ behavior: 'smooth', block: 'center' })
      document.getElementById('invite-input')?.focus()
    }, 80)
    return () => window.clearTimeout(timer)
  }, [location.hash, location.key])

  useEffect(() => {
    const controller = new AbortController()
    const timer = window.setTimeout(() => controller.abort(), 4_000)
    getHealth(controller.signal)
      .then((health) => setHostVersion(health.version ?? 'online'))
      .catch(() => setHostVersion(null))
      .finally(() => window.clearTimeout(timer))
    return () => {
      window.clearTimeout(timer)
      controller.abort()
    }
  }, [])

  const submit = (event: FormEvent) => {
    event.preventDefault()
    const parsed = parseInvite(value)
    if (!parsed) {
      setError('Paste the full invite link the host shared, or a complete /room/ URL.')
      return
    }
    if (!parsed.inviteToken) {
      setError('That link is missing its invite token. Ask the host for the full share link — it ends with ?t=…')
      return
    }
    setError(null)
    navigate(buildRoomPath(parsed.roomId, parsed.inviteToken))
  }

  const activeSessions = useMemo(() => sessions.slice(0, 3), [sessions])

  return (
    <AppShell>
      <div className="relative overflow-hidden">
        <div className="ambient pointer-events-none absolute inset-0">
          <div className="absolute -left-48 -top-40 size-[36rem] rounded-full bg-wave-cyan/[0.08] blur-3xl" />
          <div className="absolute -right-40 top-24 size-[30rem] rounded-full bg-wave-teal/[0.05] blur-3xl" />
          <div className="absolute inset-0 bg-grid opacity-50" />
        </div>

        <section className="relative mx-auto w-full max-w-[1380px] px-4 pb-10 pt-6 sm:px-6 sm:pt-10 lg:px-10 lg:pb-16 lg:pt-14">
          <div className="grid items-center gap-10 lg:grid-cols-[1.04fr_0.96fr] lg:gap-14">
            <div className="animate-fade-up">
              <div className="flex flex-wrap items-center gap-2">
                <Pill tone="cyan">
                  <WaveIcon size={12} /> Open source · MIT
                </Pill>
                <span className="inline-flex items-center gap-1.5 rounded-full border border-white/[0.07] bg-white/[0.03] px-2.5 py-1 text-[10px] font-medium uppercase tracking-[0.12em] text-wave-muted">
                  <LiveDot tone={hostVersion ? 'green' : 'amber'} />
                  {hostVersion ? `host v${hostVersion}` : 'viewer ready'}
                </span>
              </div>

              <h1 className="mt-5 max-w-2xl text-balance font-display text-[2.65rem] font-semibold leading-[0.98] tracking-[-0.04em] text-white sm:text-6xl lg:text-[4.35rem]">
                Press play.
                <br />
                <span className="bg-[linear-gradient(100deg,rgb(var(--wv-accent)),rgb(var(--wv-accent-2))_70%)] bg-clip-text text-transparent">
                  Everyone follows.
                </span>
              </h1>

              <p className="mt-5 max-w-xl text-pretty text-[15px] leading-relaxed text-wave-muted sm:text-[16px]">
                A watch party for the videos already on your phone. Add a file, share one link,
                and keep every screen on the same frame — with chat for the whole room.
                No accounts, no cloud uploads, no transcoding.
              </p>

              <form id="join" onSubmit={submit} className="mt-7 max-w-xl scroll-mt-28">
                <div className="rounded-2xl border border-white/[0.09] bg-ink-850/75 p-2 shadow-float backdrop-blur-xl sm:flex sm:items-center sm:gap-2">
                  <TextField
                    id="invite-input"
                    aria-label="Invite link"
                    value={value}
                    onChange={(event) => {
                      setValue(event.target.value)
                      if (error) setError(null)
                    }}
                    placeholder="Paste the invite link your host shared"
                    autoComplete="off"
                    spellCheck={false}
                    leading={<LinkIcon size={17} />}
                    className="border-transparent bg-transparent focus:bg-transparent"
                  />
                  <Button type="submit" size="lg" className="mt-2 w-full shrink-0 sm:mt-0 sm:w-auto">
                    Join room <ArrowRightIcon size={17} />
                  </Button>
                </div>
                {error ? (
                  <div className="mt-2">
                    <Banner kind="error">{error}</Banner>
                  </div>
                ) : null}
                <p className="mt-2.5 flex flex-wrap items-center gap-x-4 gap-y-1.5 text-[11px] text-wave-muted/65">
                  <span className="inline-flex items-center gap-1.5">
                    <LockIcon size={12} /> Invite-scoped session
                  </span>
                  <span className="inline-flex items-center gap-1.5">
                    <ZapIcon size={12} /> Original quality
                  </span>
                  <span className="inline-flex items-center gap-1.5">
                    <ChatIcon size={12} /> Room chat included
                  </span>
                </p>
              </form>

              <div className="mt-7 flex flex-wrap items-center gap-2">
                <Link
                  to="/settings"
                  className="inline-flex items-center gap-1.5 rounded-lg text-[12px] text-wave-muted transition hover:text-white"
                >
                  <SettingsIcon size={14} /> Customize the player
                </Link>
                <span className="text-white/15">·</span>
                <a
                  href="#devices"
                  className="inline-flex items-center gap-1.5 rounded-lg text-[12px] text-wave-muted transition hover:text-white"
                >
                  <MonitorSmartphoneIcon size={14} /> Works on every screen
                </a>
              </div>
            </div>

            <HeroPreview hostVersion={hostVersion} />
          </div>
        </section>
      </div>

      {activeSessions.length > 0 ? (
        <section className="mx-auto w-full max-w-[1380px] px-4 pb-4 sm:px-6 lg:px-10">
          <div className="mb-3 flex items-end justify-between gap-3">
            <div>
              <p className="section-eyebrow">Continue in this tab</p>
              <h2 className="mt-1 font-display text-xl font-semibold text-white">Pick up where you left off</h2>
            </div>
            <span className="hidden text-[11px] text-wave-muted sm:block">Sessions clear when this tab closes</span>
          </div>
          <div className="grid gap-3 md:grid-cols-3">
            {activeSessions.map((session) => (
              <Link
                key={session.roomId}
                to={buildRoomPath(session.roomId, session.inviteToken)}
                className="app-card-interactive flex items-center gap-3 p-3.5"
              >
                <span className="relative grid size-12 shrink-0 place-items-center overflow-hidden rounded-xl bg-[linear-gradient(135deg,rgb(var(--wv-accent)/.22),rgb(var(--wv-accent-2)/.08))] text-wave-cyan">
                  <PlayIcon size={19} />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-medium text-white">
                    {session.roomName ?? `Room ${session.roomId}`}
                  </span>
                  <span className="mt-0.5 block truncate text-[11px] text-wave-muted">
                    {session.mediaTitle ?? 'Watch party'} · {formatRelative(session.savedAt)}
                  </span>
                </span>
                <ChevronRightIcon size={17} className="shrink-0 text-wave-muted" />
              </Link>
            ))}
          </div>
        </section>
      ) : null}

      <section className="mx-auto w-full max-w-[1380px] px-4 py-12 sm:px-6 lg:px-10 lg:py-16">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {[
            {
              icon: <ZapIcon size={18} />,
              title: 'Zero uploads',
              body: 'The original file stays on the host phone and streams with HTTP Range requests.',
            },
            {
              icon: <GaugeIcon size={18} />,
              title: 'Frame-aware sync',
              body: 'The host clock is authoritative. Viewers nudge speed, then seek only when needed.',
            },
            {
              icon: <UsersIcon size={18} />,
              title: 'Chat for everyone',
              body: 'Server-assigned names and timestamps. Viewers can talk even with controls locked.',
            },
            {
              icon: <ShieldIcon size={18} />,
              title: 'Private by default',
              body: 'Revocable invite tokens, per-room sessions and no account database to leak.',
            },
          ].map((feature) => (
            <Card key={feature.title} className="p-5">
              <span className="grid size-10 place-items-center rounded-xl border border-wave-cyan/20 bg-wave-cyan/[0.08] text-wave-cyan">
                {feature.icon}
              </span>
              <h3 className="mt-4 font-display text-[15px] font-semibold text-white">{feature.title}</h3>
              <p className="mt-1.5 text-[12px] leading-relaxed text-wave-muted">{feature.body}</p>
            </Card>
          ))}
        </div>
      </section>

      <section className="border-y border-white/[0.06] bg-ink-900/45 py-14 sm:py-20">
        <div className="mx-auto w-full max-w-[1380px] px-4 sm:px-6 lg:px-10">
          <div className="grid gap-10 lg:grid-cols-[0.8fr_1.2fr] lg:gap-16">
            <div>
              <p className="section-eyebrow">How it works</p>
              <h2 className="section-title mt-2">The phone is the theatre.</h2>
              <p className="mt-3 max-w-md text-[14px] leading-relaxed text-wave-muted">
                Nothing is uploaded. The Android host serves byte ranges from the original file,
                verifies a public link, and owns the playback clock every viewer follows.
              </p>
              <a
                href={`${REPO_URL}#readme`}
                target="_blank"
                rel="noreferrer"
                className="mt-5 inline-flex items-center gap-1.5 text-[12px] font-medium text-wave-cyan hover:underline"
              >
                Read the architecture <ExternalLinkIcon size={13} />
              </a>
            </div>
            <ol className="grid gap-3 sm:grid-cols-3">
              {[
                {
                  step: '01',
                  title: 'Add a video',
                  body: 'Pick a file with Android’s picker. WatchWave stores metadata and a read grant — never a copy.',
                },
                {
                  step: '02',
                  title: 'Share one link',
                  body: 'The app starts the embedded server and a free Cloudflare Quick Tunnel, then verifies the URL.',
                },
                {
                  step: '03',
                  title: 'Watch + chat',
                  body: 'Friends join from any browser. Play, pause and seek stay aligned; chat is always open.',
                },
              ].map((item) => (
                <li key={item.step} className="app-card p-5">
                  <span className="font-mono text-[11px] text-wave-cyan">{item.step}</span>
                  <h3 className="mt-3 font-display text-[15px] font-semibold text-white">{item.title}</h3>
                  <p className="mt-1.5 text-[12px] leading-relaxed text-wave-muted">{item.body}</p>
                </li>
              ))}
            </ol>
          </div>
        </div>
      </section>

      <section id="advanced" className="mx-auto w-full max-w-[1380px] scroll-mt-24 px-4 py-14 sm:px-6 lg:px-10 lg:py-20">
        <div className="app-card overflow-hidden">
          <div className="grid lg:grid-cols-[1fr_0.95fr]">
            <div className="relative border-b border-white/[0.06] p-6 sm:p-9 lg:border-b-0 lg:border-r">
              <div className="ambient pointer-events-none absolute -left-20 -top-24 size-72 rounded-full bg-wave-cyan/[0.08] blur-3xl" />
              <Pill tone="cyan">
                <SlidersIcon size={12} /> Advanced controls
              </Pill>
              <h2 className="mt-4 max-w-md font-display text-3xl font-semibold tracking-tight text-white sm:text-4xl">
                Tune it once.
                <br />
                Enjoy it everywhere.
              </h2>
              <p className="mt-3 max-w-md text-[14px] leading-relaxed text-wave-muted">
                Basic use is one link and one tap. When you want more, every advanced option lives
                in its own section — away from the things you touch every day.
              </p>
              <Link
                to="/settings"
                className="mt-6 inline-flex h-11 items-center gap-2 rounded-xl bg-white px-5 text-sm font-semibold text-ink-950 transition hover:bg-slate-200"
              >
                Open advanced settings <ArrowRightIcon size={16} />
              </Link>
            </div>
            <div className="grid gap-px bg-white/[0.06] sm:grid-cols-2">
              {[
                {
                  icon: <GaugeIcon size={17} />,
                  title: 'Sync & drift',
                  body: 'Choose the skip distance and show the live drift readout.',
                },
                {
                  icon: <PaletteIcon size={17} />,
                  title: 'Look & feel',
                  body: 'Four accent palettes, compact density and cinema mode.',
                },
                {
                  icon: <KeyboardIcon size={17} />,
                  title: 'Keyboard first',
                  body: 'Space, K, arrows, M and F. Shortcuts can be disabled.',
                },
                {
                  icon: <MonitorSmartphoneIcon size={17} />,
                  title: 'Data saver',
                  body: 'Strip ambience and blur while keeping every control.',
                },
                {
                  icon: <ChatIcon size={17} />,
                  title: 'Chat density',
                  body: 'Comfortable bubbles or a tight, stream-friendly list.',
                },
                {
                  icon: <ServerBadge />,
                  title: 'Diagnostics',
                  body: 'Protocol, clock offset and transport details when you need them.',
                },
              ].map((item) => (
                <div key={item.title} className="bg-ink-850/90 p-5">
                  <span className="text-wave-cyan">{item.icon}</span>
                  <h3 className="mt-3 text-[13px] font-semibold text-white">{item.title}</h3>
                  <p className="mt-1 text-[11px] leading-relaxed text-wave-muted">{item.body}</p>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      <section id="devices" className="mx-auto w-full max-w-[1380px] scroll-mt-24 px-4 pb-14 sm:px-6 lg:px-10 lg:pb-20">
        <div className="mb-7 max-w-2xl">
          <p className="section-eyebrow">Every screen</p>
          <h2 className="section-title mt-2">Phone in your hand. Friends on the couch.</h2>
          <p className="mt-2 text-[14px] leading-relaxed text-wave-muted">
            The viewer is responsive from a 320 px phone to a 4K desktop. The Android host stays
            portable, and the browser client is bundled inside the APK — one link is enough.
          </p>
        </div>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {[
            { icon: <PhoneIcon size={20} />, title: 'Phone', body: 'Bottom navigation, thumb-sized controls and safe-area padding.' },
            { icon: <MonitorSmartphoneIcon size={20} />, title: 'Tablet', body: 'A two-column room that keeps chat beside the player.' },
            { icon: <GlobeIcon size={20} />, title: 'Laptop', body: 'Desktop rail, theater layout, keyboard shortcuts and PiP.' },
            { icon: <SparklesIcon size={20} />, title: 'TV browser', body: 'Large focus rings and a fullscreen-first player surface.' },
          ].map((device) => (
            <Card key={device.title} className="p-5">
              <div className="flex items-center justify-between">
                <span className="text-wave-cyan">{device.icon}</span>
                <span className="font-mono text-[10px] uppercase tracking-[0.14em] text-wave-muted/50">ready</span>
              </div>
              <h3 className="mt-4 font-display text-[15px] font-semibold text-white">{device.title}</h3>
              <p className="mt-1.5 text-[12px] leading-relaxed text-wave-muted">{device.body}</p>
            </Card>
          ))}
        </div>
      </section>

      <section className="mx-auto w-full max-w-[1380px] px-4 pb-16 sm:px-6 lg:px-10 lg:pb-24">
        <div className="relative overflow-hidden rounded-3xl border border-white/[0.08] bg-[linear-gradient(120deg,rgb(var(--wv-accent)/.12),transparent_42%,rgb(var(--wv-accent-2)/.08))] p-7 sm:p-10">
          <div className="ambient pointer-events-none absolute inset-0 bg-noise" />
          <div className="relative flex flex-col gap-6 lg:flex-row lg:items-center lg:justify-between">
            <div className="max-w-2xl">
              <div className="flex items-center gap-3">
                <BrandMark />
                <div>
                  <p className="font-display text-xl font-semibold text-white">WatchWave is open source.</p>
                  <p className="text-[12px] text-wave-muted">MIT licensed · built for self-hosters and friends</p>
                </div>
              </div>
              <p className="mt-4 text-[13px] leading-relaxed text-wave-muted">
                Read the code, run your own build, fix a bug, or add a feature. The protocol,
                security model and Android app are documented in the repository.
              </p>
            </div>
            <div className="flex flex-wrap gap-2">
              <a
                href={REPO_URL}
                target="_blank"
                rel="noreferrer"
                className="inline-flex h-11 items-center gap-2 rounded-xl bg-white px-5 text-sm font-semibold text-ink-950 transition hover:bg-slate-200"
              >
                <GithubIcon size={17} /> Star on GitHub
              </a>
              <a
                href={`${REPO_URL}/releases/latest`}
                target="_blank"
                rel="noreferrer"
                className="inline-flex h-11 items-center gap-2 rounded-xl border border-white/15 bg-white/[0.06] px-5 text-sm font-medium text-white transition hover:bg-white/[0.1]"
              >
                Download Android <ArrowRightIcon size={16} />
              </a>
            </div>
          </div>
        </div>
        <footer className="mt-8 flex flex-col gap-3 text-[11px] text-wave-muted/55 sm:flex-row sm:items-center sm:justify-between">
          <p>© {new Date().getFullYear()} WatchWave contributors · MIT</p>
          <div className="flex flex-wrap items-center gap-4">
            <a href={`${REPO_URL}/blob/main/docs/SECURITY.md`} target="_blank" rel="noreferrer" className="hover:text-wave-muted">
              Security
            </a>
            <a href={`${REPO_URL}/blob/main/docs/ARCHITECTURE.md`} target="_blank" rel="noreferrer" className="hover:text-wave-muted">
              Architecture
            </a>
            <a href={`${REPO_URL}/blob/main/LICENSE`} target="_blank" rel="noreferrer" className="hover:text-wave-muted">
              License
            </a>
            <span>No trackers · no analytics</span>
          </div>
        </footer>
      </section>
    </AppShell>
  )
}

function HeroPreview({ hostVersion }: { hostVersion: string | null }) {
  return (
    <div className="relative animate-fade-up" style={{ animationDelay: '90ms' }}>
      <div className="ambient pointer-events-none absolute -inset-6 -z-10 rounded-[2.5rem] bg-[radial-gradient(circle_at_55%_35%,rgb(var(--wv-accent)/.14),transparent_65%)] blur-2xl" />
      <div className="app-card overflow-hidden border-white/[0.1] shadow-glow">
        <div className="flex items-center justify-between gap-2 border-b border-white/[0.06] px-4 py-2.5">
          <div className="flex items-center gap-2">
            <span className="flex gap-1">
              <span className="size-2 rounded-full bg-white/15" />
              <span className="size-2 rounded-full bg-white/15" />
              <span className="size-2 rounded-full bg-white/15" />
            </span>
            <span className="ml-1 font-mono text-[10px] text-wave-muted/60">watchwave / room / friday-night</span>
          </div>
          <Pill tone="green">
            <LiveDot /> Live
          </Pill>
        </div>

        <div className="relative aspect-[16/10] overflow-hidden bg-[linear-gradient(140deg,#0b1220_0%,#111827_38%,#0a0e17_100%)]">
          <div className="ambient absolute inset-0 opacity-90">
            <div className="absolute left-[12%] top-[18%] size-48 rounded-full bg-wave-cyan/15 blur-3xl" />
            <div className="absolute bottom-[8%] right-[8%] size-56 rounded-full bg-wave-teal/10 blur-3xl" />
            <div className="absolute inset-0 bg-grid opacity-30" />
          </div>
          <div className="absolute left-5 top-5">
            <p className="text-[10px] font-semibold uppercase tracking-[0.2em] text-wave-cyan/90">Now playing</p>
            <p className="mt-1.5 font-display text-lg font-semibold text-white sm:text-xl">Friday movie night</p>
            <p className="mt-0.5 text-[11px] text-white/50">Hosted from Johir’s phone · 1080p original</p>
          </div>

          <div className="absolute inset-0 grid place-items-center">
            <div className="relative">
              <span className="absolute -inset-4 animate-pulse-soft rounded-full border border-white/10" />
              <span className="grid size-16 place-items-center rounded-full border border-white/20 bg-white/10 text-white shadow-glow backdrop-blur-md sm:size-20">
                <PlayIcon size={30} />
              </span>
            </div>
          </div>

          <div className="absolute inset-x-4 bottom-4">
            <div className="flex items-center gap-2.5">
              <span className="font-mono text-[10px] tabular-nums text-white/80">01:24:38</span>
              <div className="h-1 flex-1 overflow-hidden rounded-full bg-white/15">
                <div className="h-full w-[64%] rounded-full bg-[linear-gradient(90deg,rgb(var(--wv-accent)),rgb(var(--wv-accent-2)))]" />
              </div>
              <span className="font-mono text-[10px] tabular-nums text-white/45">02:11:20</span>
            </div>
            <div className="mt-3 flex items-center justify-between">
              <div className="flex -space-x-2">
                {['Johir', 'Maya', 'Rafi', 'Nadia'].map((name) => (
                  <span key={name} className="rounded-full ring-2 ring-ink-850">
                    <Avatar name={name} size="sm" />
                  </span>
                ))}
                <span className="grid size-7 place-items-center rounded-full border border-white/10 bg-white/[0.08] text-[9px] font-medium text-white/70 ring-2 ring-ink-850">
                  +4
                </span>
              </div>
              <span className="inline-flex items-center gap-1.5 text-[10px] font-medium text-wave-green">
                <span className="size-1.5 rounded-full bg-wave-green" /> in sync ±42 ms
              </span>
            </div>
          </div>
        </div>

        <div className="space-y-2 border-t border-white/[0.06] bg-ink-900/60 px-4 py-3">
          <div className="flex items-start gap-2.5">
            <Avatar name="Maya" size="xs" />
            <div className="rounded-xl rounded-tl-sm border border-white/[0.06] bg-white/[0.05] px-3 py-1.5 text-[11px] text-slate-200">
              this scene 😭
            </div>
          </div>
          <div className="flex items-start gap-2.5">
            <Avatar name="Rafi" size="xs" />
            <div className="rounded-xl rounded-tl-sm border border-white/[0.06] bg-white/[0.05] px-3 py-1.5 text-[11px] text-slate-200">
              wait rewind 10s — I missed it
            </div>
            <span className="mt-auto rounded-full border border-wave-amber/25 bg-wave-amber/10 px-2 py-0.5 text-[9px] font-medium text-wave-amber">
              host only
            </span>
          </div>
        </div>

        <div className="flex items-center justify-between border-t border-white/[0.06] px-4 py-2.5 text-[10px] text-wave-muted">
          <span className="inline-flex items-center gap-1.5">
            <LockIcon size={11} /> invite token scoped
          </span>
          <span className="inline-flex items-center gap-1.5">
            {hostVersion ? <LiveDot tone="green" /> : <LiveDot tone="amber" />}
            {hostVersion ? 'host reachable' : 'host preview'}
          </span>
        </div>
      </div>
    </div>
  )
}

function ServerBadge() {
  return <SlidersIcon size={17} />
}
