import { Link } from 'react-router-dom'
import { Brand } from '../components/Brand'
import { ArrowLeftIcon, WaveIcon } from '../components/Icons'
import { Panel } from '../components/ui'

export function NotFoundPage() {
  return (
    <div className="relative flex min-h-screen flex-col">
      <header className="mx-auto w-full max-w-6xl px-5 py-5 sm:px-8">
        <Brand compact />
      </header>
      <main className="grid flex-1 place-items-center px-5 pb-24">
        <Panel className="w-full max-w-lg p-8 text-center">
          <span className="mx-auto grid size-12 place-items-center rounded-full border border-wave-cyan/20 bg-wave-cyan/[0.07] text-wave-cyan">
            <WaveIcon size={24} />
          </span>
          <p className="mt-5 font-mono text-[11px] uppercase tracking-[0.2em] text-wave-cyan">404 · off course</p>
          <h1 className="mt-2 font-display text-2xl font-semibold text-white">That room link is not valid</h1>
          <p className="mt-2 text-sm leading-relaxed text-wave-muted">
            Room links look like <code className="font-mono text-slate-300">/room/abc123?t=…</code>. Ask the host
            to share the full invite again.
          </p>
          <Link
            to="/"
            className="mt-6 inline-flex h-11 items-center gap-2 rounded-xl bg-wave-cyan px-5 text-sm font-medium text-ink-950 transition hover:bg-cyan-300"
          >
            <ArrowLeftIcon size={16} /> Back to home
          </Link>
        </Panel>
      </main>
    </div>
  )
}
