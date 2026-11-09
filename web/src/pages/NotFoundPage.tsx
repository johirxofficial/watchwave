import { Link } from 'react-router-dom'
import { Brand } from '../components/Brand'
import { ArrowLeftIcon, WaveIcon } from '../components/Icons'
import { Card } from '../components/ui'

export function NotFoundPage() {
  return (
    <div className="relative flex min-h-screen flex-col overflow-hidden">
      <div className="ambient pointer-events-none absolute inset-0">
        <div className="absolute -left-40 top-0 size-[32rem] rounded-full bg-wave-cyan/[0.07] blur-3xl" />
        <div className="absolute inset-0 bg-grid opacity-30" />
      </div>
      <header className="relative z-10 mx-auto w-full max-w-6xl px-5 py-5 sm:px-8">
        <Brand compact showTagline />
      </header>
      <main className="relative z-10 grid flex-1 place-items-center px-4 pb-24">
        <Card className="w-full max-w-lg p-8 text-center">
          <span className="mx-auto grid size-12 place-items-center rounded-2xl border border-wave-cyan/20 bg-wave-cyan/[0.07] text-wave-cyan">
            <WaveIcon size={24} />
          </span>
          <p className="mt-5 font-mono text-[10px] uppercase tracking-[0.2em] text-wave-cyan">404 · off course</p>
          <h1 className="mt-2 font-display text-2xl font-semibold text-white">That room link is not valid</h1>
          <p className="mt-2 text-sm leading-relaxed text-wave-muted">
            Room links look like <code className="font-mono text-slate-300">/room/abc123?t=…</code>. Ask the host
            to share the full invite again.
          </p>
          <div className="mt-6 flex flex-wrap justify-center gap-2">
            <Link
              to="/"
              className="inline-flex h-11 items-center gap-2 rounded-xl bg-wave-cyan px-5 text-sm font-medium text-ink-950 transition hover:brightness-110"
            >
              <ArrowLeftIcon size={16} /> Back to home
            </Link>
            <Link
              to="/settings"
              className="inline-flex h-11 items-center gap-2 rounded-xl border border-white/12 bg-white/[0.06] px-5 text-sm font-medium text-white transition hover:bg-white/[0.1]"
            >
              Settings
            </Link>
          </div>
        </Card>
      </main>
    </div>
  )
}
