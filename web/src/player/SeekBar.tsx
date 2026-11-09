import { cn } from '../lib/cn'

interface SeekBarProps {
  positionMs: number
  durationMs: number
  bufferedMs: number
  disabled?: boolean
  onScrubStart: () => void
  onScrub: (positionMs: number) => void
  onCommit: (positionMs: number) => void
}

export function SeekBar({
  positionMs,
  durationMs,
  bufferedMs,
  disabled = false,
  onScrubStart,
  onScrub,
  onCommit,
}: SeekBarProps) {
  const duration = Math.max(1, durationMs)
  const played = clamp((positionMs / duration) * 100, 0, 100)
  const buffered = clamp((bufferedMs / duration) * 100, 0, 100)

  const commit = () => onCommit(clamp(positionMs, 0, duration))

  return (
    <div className={cn('relative flex h-7 w-full items-center', disabled && 'pointer-events-none opacity-50')}>
      <input
        type="range"
        min={0}
        max={Math.max(1, Math.round(duration))}
        step={100}
        value={Math.round(clamp(positionMs, 0, duration))}
        disabled={disabled}
        aria-label="Seek"
        className="peer absolute inset-0 z-10 h-full w-full cursor-pointer appearance-none bg-transparent opacity-0 disabled:cursor-not-allowed"
        onPointerDown={onScrubStart}
        onTouchStart={onScrubStart}
        onChange={(event) => onScrub(Number(event.currentTarget.value))}
        onPointerUp={commit}
        onTouchEnd={commit}
        onKeyUp={(event) => {
          if (['ArrowLeft', 'ArrowRight', 'Home', 'End', 'PageUp', 'PageDown'].includes(event.key)) commit()
        }}
      />
      <div className="pointer-events-none relative h-1.5 w-full overflow-visible rounded-full bg-white/15 transition peer-focus-visible:ring-2 peer-focus-visible:ring-wave-cyan/60 peer-focus-visible:ring-offset-2 peer-focus-visible:ring-offset-ink-900">
        <div className="absolute inset-y-0 left-0 rounded-full bg-white/20" style={{ width: `${buffered}%` }} />
        <div className="absolute inset-y-0 left-0 rounded-full bg-wave-cyan" style={{ width: `${played}%` }} />
        <span
          className="absolute top-1/2 size-3.5 -translate-y-1/2 rounded-full border border-white/40 bg-white shadow-[0_0_12px_rgba(34,211,238,.55)] transition-transform hover:scale-110"
          style={{ left: `calc(${played}% - 7px)` }}
        />
      </div>
    </div>
  )
}

function clamp(value: number, min: number, max: number): number {
  if (!Number.isFinite(value)) return min
  return Math.min(max, Math.max(min, value))
}
