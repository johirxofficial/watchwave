import type { ReactNode } from 'react'
import { cn } from '../lib/cn'
import { formatClock, formatDrift } from '../lib/format'
import {
  GaugeIcon,
  LockIcon,
  MaximizeIcon,
  MinimizeIcon,
  PauseIcon,
  PipIcon,
  PlayIcon,
  SkipBackIcon,
  SkipForwardIcon,
  VolumeIcon,
  VolumeMuteIcon,
} from '../components/Icons'
import { SeekBar } from './SeekBar'

const SPEEDS = [0.5, 0.75, 1, 1.25, 1.5, 2]

interface PlayerControlsProps {
  playing: boolean
  canControl: boolean
  positionMs: number
  durationMs: number
  bufferedMs: number
  volume: number
  muted: boolean
  speed: number
  driftMs: number
  fullscreen: boolean
  pipSupported: boolean
  pipActive: boolean
  onTogglePlay: () => void
  onSkip: (deltaMs: number) => void
  onScrubStart: () => void
  onScrub: (positionMs: number) => void
  onSeekCommit: (positionMs: number) => void
  onVolumeChange: (volume: number) => void
  onToggleMute: () => void
  onSpeedChange: (speed: number) => void
  onToggleFullscreen: () => void
  onTogglePip: () => void
}

export function PlayerControls({
  playing,
  canControl,
  positionMs,
  durationMs,
  bufferedMs,
  volume,
  muted,
  speed,
  driftMs,
  fullscreen,
  pipSupported,
  pipActive,
  onTogglePlay,
  onSkip,
  onScrubStart,
  onScrub,
  onSeekCommit,
  onVolumeChange,
  onToggleMute,
  onSpeedChange,
  onToggleFullscreen,
  onTogglePip,
}: PlayerControlsProps) {
  const driftTone =
    Math.abs(driftMs) < 350
      ? 'text-wave-green'
      : Math.abs(driftMs) < 1_400
        ? 'text-wave-amber'
        : 'text-wave-red'

  return (
    <div className="pointer-events-auto bg-gradient-to-t from-black/90 via-black/55 to-transparent px-3 pb-3 pt-10 sm:px-5 sm:pb-4">
      <SeekBar
        positionMs={positionMs}
        durationMs={durationMs}
        bufferedMs={bufferedMs}
        disabled={!canControl}
        onScrubStart={onScrubStart}
        onScrub={onScrub}
        onCommit={onSeekCommit}
      />

      <div className="mt-1 flex items-center gap-1.5 sm:gap-2.5">
        <ControlsButton
          label={playing ? 'Pause for everyone' : 'Play for everyone'}
          onClick={onTogglePlay}
          disabled={!canControl}
          prominent
        >
          {playing ? <PauseIcon size={19} /> : <PlayIcon size={19} />}
        </ControlsButton>

        <ControlsButton label="Back 10 seconds" onClick={() => onSkip(-10_000)} disabled={!canControl} hideOnMobile>
          <SkipBackIcon size={18} />
        </ControlsButton>

        <ControlsButton label="Forward 10 seconds" onClick={() => onSkip(10_000)} disabled={!canControl} hideOnMobile>
          <SkipForwardIcon size={18} />
        </ControlsButton>

        <div className="ml-1 flex items-baseline gap-1 font-mono text-[12px] tabular-nums text-white/90 sm:text-[13px]">
          <span>{formatClock(positionMs)}</span>
          <span className="text-white/35">/</span>
          <span className="text-white/55">{formatClock(durationMs)}</span>
        </div>

        <div className="flex-1" />

        <span
          className={cn('hidden items-center gap-1.5 text-[11px] font-medium uppercase tracking-[0.12em] sm:flex', driftTone)}
          title={`Clock drift: ${formatDrift(driftMs)}`}
        >
          <span className={cn('size-1.5 rounded-full', Math.abs(driftMs) < 350 ? 'bg-wave-green' : 'bg-current')} />
          {Math.abs(driftMs) < 350 ? 'In sync' : `±${formatDrift(driftMs)}`}
        </span>

        <label className="relative hidden items-center text-white/80 md:flex" title="Playback speed">
          <GaugeIcon size={17} className="pointer-events-none absolute left-2" />
          <select
            value={speed}
            disabled={!canControl}
            aria-label="Playback speed"
            onChange={(event) => onSpeedChange(Number(event.currentTarget.value))}
            className="h-8 cursor-pointer appearance-none rounded-lg border border-white/10 bg-black/40 pl-7 pr-2 text-[12px] font-medium text-white outline-none transition hover:bg-black/60 focus-visible:ring-2 focus-visible:ring-wave-cyan/60 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {SPEEDS.map((option) => (
              <option key={option} value={option} className="bg-ink-900 text-white">
                {option}×
              </option>
            ))}
          </select>
        </label>

        <div className="group hidden items-center gap-1 sm:flex">
          <ControlsButton label={muted ? 'Unmute' : 'Mute'} onClick={onToggleMute}>
            {muted || volume === 0 ? <VolumeMuteIcon size={18} /> : <VolumeIcon size={18} />}
          </ControlsButton>
          <input
            type="range"
            min={0}
            max={1}
            step={0.05}
            value={muted ? 0 : volume}
            aria-label="Volume"
            onChange={(event) => onVolumeChange(Number(event.currentTarget.value))}
            className="h-1 w-16 cursor-pointer accent-wave-cyan"
          />
        </div>

        {pipSupported ? (
          <ControlsButton
            label={pipActive ? 'Exit picture-in-picture' : 'Picture-in-picture'}
            onClick={onTogglePip}
            hideOnMobile
          >
            <PipIcon size={18} />
          </ControlsButton>
        ) : null}

        <ControlsButton label={fullscreen ? 'Exit fullscreen' : 'Fullscreen'} onClick={onToggleFullscreen}>
          {fullscreen ? <MinimizeIcon size={18} /> : <MaximizeIcon size={18} />}
        </ControlsButton>
      </div>

      {!canControl ? (
        <p className="mt-2 flex items-center gap-1.5 text-[11px] text-wave-amber/90">
          <LockIcon size={13} /> The host locked viewer controls. You can still watch and adjust your volume.
        </p>
      ) : null}
    </div>
  )
}

function ControlsButton({
  children,
  label,
  onClick,
  disabled = false,
  prominent = false,
  hideOnMobile = false,
}: {
  children: ReactNode
  label: string
  onClick: () => void
  disabled?: boolean
  prominent?: boolean
  hideOnMobile?: boolean
}) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      onClick={onClick}
      disabled={disabled}
      className={cn(
        'grid place-items-center rounded-lg text-white/85 outline-none transition hover:bg-white/10 hover:text-white focus-visible:ring-2 focus-visible:ring-wave-cyan/70 disabled:cursor-not-allowed disabled:opacity-40',
        prominent ? 'size-9 bg-white/10 text-white sm:size-10' : 'size-8',
        hideOnMobile && 'hidden sm:grid',
      )}
    >
      {children}
    </button>
  )
}
