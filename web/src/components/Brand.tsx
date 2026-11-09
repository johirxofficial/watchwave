import { Link } from 'react-router-dom'
import { cn } from '../lib/cn'

/**
 * Wordmark and app icon. The mark combines a play triangle with the two-wave
 * motif, so it still reads at 32 px in a browser tab or on a TV.
 */
export function Brand({ to = '/', compact = false, showTagline = false }: { to?: string; compact?: boolean; showTagline?: boolean }) {
  return (
    <Link
      to={to}
      className={cn(
        'group inline-flex items-center gap-2.5 rounded-xl outline-none transition focus-visible:ring-2 focus-visible:ring-wave-cyan/70',
        compact ? 'text-base' : 'text-lg',
      )}
      aria-label="WatchWave home"
    >
      <BrandMark size={compact ? 'sm' : 'md'} />
      <span className="min-w-0">
        <span
          className={cn(
            'block font-display font-semibold leading-none tracking-tight text-white',
            compact ? 'text-[15px]' : 'text-[17px]',
          )}
        >
          Watch<span className="text-wave-cyan">Wave</span>
        </span>
        {showTagline ? (
          <span className="mt-1 block text-[9px] font-medium uppercase tracking-[0.2em] text-wave-muted">
            watch together
          </span>
        ) : null}
      </span>
    </Link>
  )
}

export function BrandMark({ size = 'md' }: { size?: 'sm' | 'md' | 'lg' }) {
  const box = size === 'sm' ? 'size-8 rounded-[10px]' : size === 'lg' ? 'size-14 rounded-2xl' : 'size-9 rounded-xl'
  const icon = size === 'sm' ? 18 : size === 'lg' ? 30 : 22
  return (
    <span
      className={cn(
        'relative grid shrink-0 place-items-center overflow-hidden border border-white/10 bg-ink-800 shadow-[inset_0_1px_0_rgba(255,255,255,.09)]',
        box,
      )}
      aria-hidden="true"
    >
      <span className="absolute inset-0 bg-[radial-gradient(circle_at_28%_18%,rgb(var(--wv-accent)/.4),transparent_68%)]" />
      <span className="absolute inset-x-0 bottom-0 h-1/2 bg-[linear-gradient(to_top,rgb(var(--wv-accent-2)/.16),transparent)]" />
      <svg width={icon} height={icon} viewBox="0 0 32 32" fill="none" className="relative">
        <path
          d="M2.5 19c3-5 6-5 9 0s6 5 9 0 6-5 9 0"
          stroke="rgb(var(--wv-accent-2))"
          strokeWidth="2.4"
          strokeLinecap="round"
          opacity=".9"
        />
        <path d="M12 8.8v14.4a.9.9 0 0 0 1.37.77l11.3-7.2a.9.9 0 0 0 0-1.54l-11.3-7.2A.9.9 0 0 0 12 8.8Z" fill="#ffffff" />
      </svg>
    </span>
  )
}
