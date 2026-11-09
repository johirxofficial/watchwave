import { useEffect, type ReactNode } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { cn } from '../lib/cn'
import { Brand } from './Brand'
import {
  GithubIcon,
  HomeIcon,
  LibraryIcon,
  LinkIcon,
  SettingsIcon,
  SparklesIcon,
} from './Icons'

const REPO_URL = 'https://github.com/johirxofficial/watchwave'

interface AppShellProps {
  children: ReactNode
  /** Overrides the location-derived active item. */
  active?: 'home' | 'settings' | 'join'
}

const NAV = [
  { key: 'home' as const, label: 'Home', to: '/', icon: HomeIcon },
  { key: 'join' as const, label: 'Join a room', to: '/#join', icon: LinkIcon },
  { key: 'settings' as const, label: 'Settings', to: '/settings', icon: SettingsIcon },
]

/**
 * App-style shell: a fixed rail on laptop/desktop and a thumb-friendly bottom
 * bar on phones. Rooms intentionally opt out and use the immersive layout.
 */
export function AppShell({ children, active }: AppShellProps) {
  const location = useLocation()
  const current = active ?? (location.pathname.startsWith('/settings') ? 'settings' : 'home')

  useEffect(() => {
    window.scrollTo({ top: 0, behavior: 'auto' })
  }, [location.pathname])

  return (
    <div className="min-h-screen lg:pl-[248px]">
      <DesktopRail current={current} />
      <MobileHeader />
      <main className="min-h-screen pb-24 lg:pb-0">{children}</main>
      <MobileBottomBar current={current} />
    </div>
  )
}

function DesktopRail({ current }: { current: string }) {
  return (
    <aside className="fixed inset-y-0 left-0 z-40 hidden w-[248px] flex-col border-r border-white/[0.06] bg-ink-900/70 px-4 py-5 backdrop-blur-xl lg:flex">
      <Brand showTagline />

      <nav className="mt-8 space-y-1" aria-label="Primary">
        {NAV.map((item) => (
          <Link
            key={item.key}
            to={item.to}
            className={cn('nav-item', current === item.key && 'nav-item-active')}
          >
            <item.icon size={18} />
            <span className="flex-1">{item.label}</span>
            {item.key === 'join' ? (
              <span className="rounded-md border border-white/10 px-1.5 py-0.5 font-mono text-[9px] uppercase tracking-wider text-wave-muted">
                link
              </span>
            ) : null}
          </Link>
        ))}
      </nav>

      <div className="mt-7">
        <p className="px-3 text-[10px] font-semibold uppercase tracking-[0.18em] text-wave-muted/60">Library</p>
        <div className="mt-2 space-y-1">
          <div className="nav-item cursor-default opacity-70">
            <LibraryIcon size={17} />
            <span className="flex-1">Local videos</span>
            <span className="text-[10px] uppercase tracking-wider text-wave-muted/60">app</span>
          </div>
          <a
            href="#advanced"
            className="nav-item"
            onClick={(event) => {
              if (window.location.pathname !== '/') return
              const target = document.getElementById('advanced')
              if (!target) return
              event.preventDefault()
              target.scrollIntoView({ behavior: 'smooth', block: 'start' })
            }}
          >
            <SparklesIcon size={17} />
            <span className="flex-1">Advanced</span>
          </a>
        </div>
      </div>

      <div className="mt-auto space-y-3">
        <a
          href={REPO_URL}
          target="_blank"
          rel="noreferrer"
          className="nav-item border border-white/[0.06] bg-white/[0.02]"
        >
          <GithubIcon size={17} />
          <span className="flex-1">Open source</span>
          <span className="text-[10px] text-wave-muted/60">MIT</span>
        </a>
        <div className="rounded-2xl border border-wave-cyan/20 bg-wave-cyan/[0.06] p-3.5">
          <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-wave-cyan">Host on Android</p>
          <p className="mt-1.5 text-[12px] leading-relaxed text-wave-muted">
            Add a video on your phone and share one link. No account, no upload.
          </p>
          <a
            href={`${REPO_URL}/releases/latest`}
            target="_blank"
            rel="noreferrer"
            className="mt-3 inline-flex text-[12px] font-medium text-white underline decoration-wave-cyan/50 underline-offset-4 hover:decoration-wave-cyan"
          >
            Get the APK
          </a>
        </div>
        <p className="px-1 text-[10px] leading-relaxed text-wave-muted/45">
          WatchWave 1.1 · MIT licensed
          <br />
          No accounts. No cloud library.
        </p>
      </div>
    </aside>
  )
}

function MobileHeader() {
  return (
    <header className="sticky top-0 z-30 border-b border-white/[0.06] bg-ink-950/80 backdrop-blur-xl lg:hidden">
      <div className="flex items-center justify-between px-4 py-3">
        <Brand compact />
        <div className="flex items-center gap-1.5">
          <a
            href={REPO_URL}
            target="_blank"
            rel="noreferrer"
            aria-label="WatchWave on GitHub"
            className="grid size-9 place-items-center rounded-xl text-wave-muted transition hover:bg-white/[0.06] hover:text-white"
          >
            <GithubIcon size={18} />
          </a>
          <Link
            to="/settings"
            aria-label="Settings"
            className="grid size-9 place-items-center rounded-xl text-wave-muted transition hover:bg-white/[0.06] hover:text-white"
          >
            <SettingsIcon size={18} />
          </Link>
        </div>
      </div>
    </header>
  )
}

function MobileBottomBar({ current }: { current: string }) {
  return (
    <nav
      className="fixed inset-x-0 bottom-0 z-40 border-t border-white/[0.07] bg-ink-950/90 px-2 pb-safe pt-1.5 backdrop-blur-xl lg:hidden"
      aria-label="Primary"
    >
      <div className="mx-auto grid max-w-md grid-cols-4">
        <MobileItem to="/" label="Home" active={current === 'home'} icon={<HomeIcon size={20} />} />
        <MobileItem to="/#join" label="Join" active={current === 'join'} icon={<LinkIcon size={20} />} />
        <MobileItem
          to="/settings"
          label="Settings"
          active={current === 'settings'}
          icon={<SettingsIcon size={20} />}
        />
        <a
          href={REPO_URL}
          target="_blank"
          rel="noreferrer"
          className="flex flex-col items-center gap-1 rounded-xl py-1.5 text-wave-muted transition hover:bg-white/[0.05] hover:text-white"
        >
          <GithubIcon size={20} />
          <span className="text-[10px] font-medium">Source</span>
        </a>
      </div>
    </nav>
  )
}

function MobileItem({
  to,
  label,
  active,
  icon,
}: {
  to: string
  label: string
  active: boolean
  icon: ReactNode
}) {
  return (
    <Link
      to={to}
      className={cn(
        'flex flex-col items-center gap-1 rounded-xl py-1.5 transition',
        active ? 'text-wave-cyan' : 'text-wave-muted hover:bg-white/[0.05] hover:text-white',
      )}
    >
      {icon}
      <span className="text-[10px] font-medium">{label}</span>
    </Link>
  )
}
