import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { Brand } from '../components/Brand'
import {
  ArrowRightIcon,
  GaugeIcon,
  LinkIcon,
  PhoneIcon,
  PlayIcon,
  ShieldIcon,
  UsersIcon,
  WaveIcon,
  ZapIcon,
} from '../components/Icons'
import { Banner, Button, LiveDot, Panel, Pill, TextField } from '../components/ui'
import { getHealth } from '../lib/api'
import { buildRoomPath, parseInvite } from '../lib/invite'

export function LandingPage() {
  const navigate = useNavigate()
  const [value, setValue] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [hostVersion, setHostVersion] = useState<string | null>(null)

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

  return (
    <div className="relative flex min-h-screen flex-col overflow-hidden">
      <div className="pointer-events-none absolute inset-0">
        <div className="absolute -left-56 -top-48 size-[40rem] rounded-full bg-wave-cyan/[0.08] blur-3xl" />
        <div className="absolute -right-48 top-20 size-[36rem] rounded-full bg-teal-400/[0.05] blur-3xl" />
        <div className="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-white/10 to-transparent" />
        <div className="absolute inset-0 bg-grid opacity-40" />
      </div>

      <header className="relative z-20">
        <nav className="mx-auto flex w-full max-w-6xl items-center justify-between px-5 py-5 sm:px-8">
          <Brand />
          <div className="flex items-center gap-2">
            {hostVersion ? (
              <Pill tone="green" className="hidden sm:inline-flex">
                <LiveDot /> Host online
              </Pill>
            ) : null}
            <a
              href="#how-it-works"
              className="rounded-lg px-3 py-2 text-[13px] text-wave-muted transition hover:bg-white/[0.06] hover:text-white"
            >
              How it works
            </a>
          </div>
        </nav>
      </header>

      <main className="relative z-10 flex-1">
        <section className="mx-auto grid w-full max-w-6xl items-center gap-10 px-5 pb-16 pt-10 sm:px-8 lg:grid-cols-[1.1fr_0.9fr] lg:pb-24 lg:pt-16">
          <div className="animate-fade-up">
            <span className="inline-flex items-center gap-2 rounded-full border border-wave-cyan/25 bg-wave-cyan/[0.07] px-3 py-1 text-[11px] font-medium uppercase tracking-[0.16em] text-wave-cyan">
              <WaveIcon size={13} /> Original file. Zero uploads.
            </span>
            <h1 className="mt-5 max-w-2xl text-balance font-display text-5xl font-semibold leading-[1.04] tracking-tight text-white sm:text-6xl">
              Movie night, minus the
              <span className="text-wave-cyan"> “3, 2, 1, play”.</span>
            </h1>
            <p className="mt-5 max-w-xl text-pretty text-[15px] leading-relaxed text-wave-muted sm:text-base">
              WatchWave streams the video straight from the host's phone and keeps every viewer on the
              same frame. No re-encoding, no cloud library, no accounts.
            </p>

            <form onSubmit={submit} className="mt-7 max-w-xl">
              <div className="flex flex-col gap-2 sm:flex-row">
                <TextField
                  aria-label="Invite link"
                  value={value}
                  onChange={(event) => {
                    setValue(event.target.value)
                    if (error) setError(null)
                  }}
                  placeholder="Paste the invite link"
                  autoComplete="off"
                  spellCheck={false}
                  className="sm:flex-1"
                />
                <Button type="submit" size="lg" className="shrink-0">
                  Join room <ArrowRightIcon size={17} />
                </Button>
              </div>
              {error ? (
                <div className="mt-2">
                  <Banner kind="error">{error}</Banner>
                </div>
              ) : null}
              <p className="mt-2.5 flex items-center gap-1.5 text-[12px] text-wave-muted/70">
                <LinkIcon size={13} />
                Invite links look like <code className="font-mono text-slate-400">/room/abc123?t=…</code>
              </p>
            </form>

            <div className="mt-8 flex flex-wrap items-center gap-x-6 gap-y-2 font-mono text-[11px] uppercase tracking-[0.14em] text-wave-muted/60">
              <span>HTTP Range</span>
              <span className="text-wave-cyan/50">·</span>
              <span>WebSocket sync</span>
              <span className="text-wave-cyan/50">·</span>
              <span>No transcoding</span>
            </div>
          </div>

          <div className="animate-fade-up lg:pl-4" style={{ animationDelay: '80ms' }}>
            <Panel accent className="relative overflow-hidden p-5 sm:p-6">
              <div className="absolute inset-x-0 top-0 h-24 bg-gradient-to-b from-wave-cyan/[0.08] to-transparent" />
              <div className="relative">
                <div className="flex items-center justify-between">
                  <Pill tone="cyan">
                    <LiveDot /> Host ready
                  </Pill>
                  <span className="font-mono text-[11px] text-wave-muted/70">
                    {hostVersion ? `v${hostVersion}` : 'same-origin'}
                  </span>
                </div>

                <div className="mt-5 rounded-xl border border-white/[0.07] bg-black/30 p-4">
                  <p className="text-[11px] font-semibold uppercase tracking-[0.14em] text-wave-muted">
                    Playback clock
                  </p>
                  <div className="mt-3 flex items-end justify-between gap-3">
                    <div>
                      <p className="font-mono text-3xl font-semibold tabular-nums text-white">
                        01:24:38
                      </p>
                      <p className="mt-1 text-[11px] text-wave-muted">authoritative position</p>
                    </div>
                    <span className="mb-1 inline-flex items-center gap-1.5 rounded-full border border-wave-green/25 bg-wave-green/10 px-2.5 py-1 text-[10px] font-medium uppercase tracking-[0.12em] text-wave-green">
                      <span className="size-1.5 rounded-full bg-wave-green" /> ± 42 ms
                    </span>
                  </div>
                  <div className="mt-4 h-1.5 overflow-hidden rounded-full bg-white/10">
                    <div className="h-full w-[64%] rounded-full bg-gradient-to-r from-wave-cyan to-wave-teal" />
                  </div>
                  <div className="mt-3 flex items-center justify-between text-[11px] text-wave-muted">
                    <span className="inline-flex items-center gap-1.5">
                      <UsersIcon size={13} /> 4 viewers in sync
                    </span>
                    <span className="inline-flex items-center gap-1.5">
                      <ZapIcon size={13} className="text-wave-cyan" /> direct stream
                    </span>
                  </div>
                </div>

                <div className="mt-4 grid grid-cols-2 gap-2">
                  <MiniFeature icon={<GaugeIcon size={15} />} title="Drift aware" detail="Rate nudges, then seeks" />
                  <MiniFeature icon={<ShieldIcon size={15} />} title="Invite scoped" detail="Rotate or revoke" />
                </div>

                <p className="mt-4 flex items-start gap-2 text-[11px] leading-relaxed text-wave-muted/70">
                  <PhoneIcon size={14} className="mt-0.5 shrink-0" />
                  The host's Android app is the server. This page talks to it directly.
                </p>
              </div>
            </Panel>
          </div>
        </section>

        <section id="how-it-works" className="border-t border-white/[0.06] bg-ink-900/40 py-16 sm:py-20">
          <div className="mx-auto w-full max-w-6xl px-5 sm:px-8">
            <div className="max-w-2xl">
              <p className="text-[11px] font-semibold uppercase tracking-[0.2em] text-wave-cyan">How it works</p>
              <h2 className="mt-3 font-display text-3xl font-semibold tracking-tight text-white sm:text-4xl">
                The phone is the theatre.
              </h2>
              <p className="mt-3 text-[15px] leading-relaxed text-wave-muted">
                Nothing is uploaded. The host app serves byte ranges from the original file and owns the
                playback clock that every viewer follows.
              </p>
            </div>

            <ol className="mt-10 grid gap-4 md:grid-cols-3">
              {[
                {
                  icon: <PlayIcon size={18} />,
                  step: '01',
                  title: 'Pick a video',
                  body: 'Choose a file from the host phone. WatchWave stores only its metadata, never a copy.',
                },
                {
                  icon: <LinkIcon size={18} />,
                  step: '02',
                  title: 'Share the link',
                  body: 'The app verifies the tunnel, then shows an HTTPS invite with a private session token.',
                },
                {
                  icon: <UsersIcon size={18} />,
                  step: '03',
                  title: 'Watch together',
                  body: 'Everyone joins in a browser or the Android viewer and stays aligned to the host clock.',
                },
              ].map((item) => (
                <li key={item.step} className="rounded-2xl border border-white/[0.07] bg-ink-850/60 p-5">
                  <div className="flex items-center justify-between">
                    <span className="grid size-10 place-items-center rounded-xl border border-wave-cyan/20 bg-wave-cyan/[0.08] text-wave-cyan">
                      {item.icon}
                    </span>
                    <span className="font-mono text-[11px] text-wave-muted/50">{item.step}</span>
                  </div>
                  <h3 className="mt-4 font-display text-lg font-semibold text-white">{item.title}</h3>
                  <p className="mt-1.5 text-[13px] leading-relaxed text-wave-muted">{item.body}</p>
                </li>
              ))}
            </ol>

            <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <Feature
                icon={<ZapIcon size={17} />}
                title="Original quality"
                body="Byte-for-byte Range streaming. No transcode ladder, no waiting for a cloud import."
              />
              <Feature
                icon={<GaugeIcon size={17} />}
                title="Frame-aware sync"
                body="The host clock is authoritative. Viewers nudge playback rate and seek only when needed."
              />
              <Feature
                icon={<ShieldIcon size={17} />}
                title="Private by default"
                body="Invite tokens can be rotated or revoked. Session credentials stay in the browser tab."
              />
              <Feature
                icon={<UsersIcon size={17} />}
                title="Shared controls"
                body="When the host allows it, any viewer can play, pause, seek or change speed for the room."
              />
            </div>
          </div>
        </section>

        <section className="mx-auto w-full max-w-6xl px-5 py-14 sm:px-8">
          <Panel className="flex flex-col items-start justify-between gap-5 p-6 sm:flex-row sm:items-center sm:p-8">
            <div className="flex items-start gap-4">
              <span className="grid size-11 shrink-0 place-items-center rounded-xl border border-white/10 bg-white/[0.04] text-wave-cyan">
                <PhoneIcon size={20} />
              </span>
              <div>
                <h2 className="font-display text-xl font-semibold text-white">Hosting tonight?</h2>
                <p className="mt-1 max-w-xl text-[13px] leading-relaxed text-wave-muted">
                  Open WatchWave on Android, choose a video, and share the verified link. The app keeps the
                  server, tunnel and playback clock alive in a foreground service.
                </p>
              </div>
            </div>
            <Pill tone="cyan" className="shrink-0">
              <LiveDot /> Android host app
            </Pill>
          </Panel>
        </section>
      </main>

      <footer className="relative z-10 border-t border-white/[0.06]">
        <div className="mx-auto flex w-full max-w-6xl flex-col items-center justify-between gap-2 px-5 py-6 text-[12px] text-wave-muted/70 sm:flex-row sm:px-8">
          <span className="inline-flex items-center gap-2">
            <WaveIcon size={14} className="text-wave-cyan" />
            WatchWave · self-hosted watch parties
          </span>
          <span>No accounts. No uploads. The host's device serves the video.</span>
        </div>
      </footer>
    </div>
  )
}

function MiniFeature({ icon, title, detail }: { icon: React.ReactNode; title: string; detail: string }) {
  return (
    <div className="rounded-lg border border-white/[0.06] bg-white/[0.02] p-3">
      <span className="text-wave-cyan">{icon}</span>
      <p className="mt-2 text-[12px] font-medium text-white">{title}</p>
      <p className="text-[10px] text-wave-muted">{detail}</p>
    </div>
  )
}

function Feature({ icon, title, body }: { icon: React.ReactNode; title: string; body: string }) {
  return (
    <div className="rounded-xl border border-white/[0.06] bg-white/[0.02] p-4">
      <span className="text-wave-cyan">{icon}</span>
      <h3 className="mt-3 text-[13px] font-semibold text-white">{title}</h3>
      <p className="mt-1 text-[12px] leading-relaxed text-wave-muted">{body}</p>
    </div>
  )
}
