import { Link } from 'react-router-dom'
import { cn } from '../lib/cn'

export function Brand({ to = '/', compact = false }: { to?: string; compact?: boolean }) {
  return (
    <Link
      to={to}
      className={cn(
        'group inline-flex items-center gap-2.5 rounded-xl outline-none transition focus-visible:ring-2 focus-visible:ring-wave-cyan/70',
        compact ? 'text-base' : 'text-lg',
      )}
      aria-label="WatchWave home"
    >
      <span className="relative grid size-9 place-items-center overflow-hidden rounded-xl border border-white/10 bg-ink-800 shadow-[inset_0_1px_0_rgba(255,255,255,.08)]">
        <span className="absolute inset-0 bg-[radial-gradient(circle_at_30%_20%,rgba(34,211,238,.28),transparent_65%)]" />
        <svg width="22" height="22" viewBox="0 0 32 32" fill="none" aria-hidden="true" className="relative">
          <path
            d="M2 18c3-5.2 6-5.2 9 0s6 5.2 9 0 6-5.2 9 0"
            stroke="#22d3ee"
            strokeWidth="2.8"
            strokeLinecap="round"
          />
          <path d="M11 9v14l11-7-11-7Z" fill="#f8fbff" />
        </svg>
      </span>
      <span className={cn('font-display font-semibold tracking-tight text-white', compact ? 'text-base' : 'text-lg')}>
        Watch<span className="text-wave-cyan">Wave</span>
      </span>
    </Link>
  )
}
