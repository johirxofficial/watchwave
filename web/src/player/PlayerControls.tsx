import type { ReactNode } from 'react'
import { cn } from '../lib/cn'
import { formatClock, formatDrift } from '../lib/format'
import {
  GaugeIcon,
  HelpIcon,
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

const SPEEDS = [0.5, 0.75, 1, 1.25, 1.5, 1.75, 2]

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
  skipSeconds: number
  showDrift: boolean
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
  onShowHelp: () => void
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
  skipSeconds,
  showDrift,
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
  onShowHelp,
}: PlayerControlsProps) {
  const driftTone =
    Math.abs(driftMs) < 350
      ? 'text-wave-green'
      : Math.abs(driftMs) < 1_400
        ? 'text-wave-amber'
        : 'text-wave-red'
  const skipMs = skipSeconds * 1_000

  return (
    <div className="pointer-events-auto bg-[linear-gradient(to_top,rgba(0,0,0,.94),rgba(0,0,0,.62)_55%,transparent)] px-2.5 pb-2 pt-6 sm:px-4 sm:pb-4 sm:pt-12">
      <SeekBar
        positionMs={positionMs}
        durationMs={durationMs}
        bufferedMs={bufferedMs}
        disabled={!canControl}
        onScrubStart={onScrubStart}
        onScrub={onScrub}
        onCommit={onSeekCommit}
      />

      <div className="mt-0.5 flex items-center gap-0.5 sm:gap-1.5">
        <button
          type="button"
          aria-label={playing ? 'Pause for everyone' : 'Play for everyone'}
          title={playing ? 'Pause for everyone' : 'Play for everyone'}
          onClick={onTogglePlay}
          disabled={!canControl}
          className="grid size-10 shrink-0 place-items-center rounded-full bg-white text-ink-950 outline-none transition hover:scale-105 hover:bg-white/90 focus-visible:ring-2 focus-visible:ring-wave-cyan/70 disabled:cursor-not-allowed disabled:opacity-40 sm:size-11"
        >
          {playing ? <PauseIcon size={18} /> : <PlayIcon size={18} />}
        </button>

        <SkipButton label={`Back ${skipSeconds} seconds`} onClick={() => onSkip(-skipMs)} disabled={!canControl} seconds={skipSeconds}>
          <SkipBackIcon size={18} />
        </SkipButton>

        <SkipButton label={`Forward ${skipSeconds} seconds`} onClick={() => onSkip(skipMs)} disabled={!canControl} seconds={skipSeconds}>
          <SkipForwardIcon size={18} />
        </SkipButton>

        <div className="ml-1 flex items-baseline gap-1 font-mono text-[11px] tabular-nums text-white/90 sm:text-[12px]">
          <span>{formatClock(positionMs)}</span>
          <span className="text-white/30">/</span>
          <span className="text-white/50">{formatClock(durationMs)}</span>
        </div>

        <div className="flex-1" />

        {showDrift ? (
          <span
            className={cn('hidden items-center gap-1.5 text-[10px] font-semibold uppercase tracking-[0.1em] sm:flex', driftTone)}
            title={`Clock drift: ${formatDrift(driftMs)}`}
          >
            <span className={cn('size-1.5 rounded-full', Math.abs(driftMs) < 350 ? 'bg-wave-green' : 'bg-current')} />
            {Math.abs(driftMs) < 350 ? 'In sync' : `±${formatDrift(driftMs)}`}
          </span>
        ) : null}

        <label className="relative hidden items-center text-white/80 sm:flex" title="Playback speed">
          <GaugeIcon size={16} className="pointer-events-none absolute left-2" />
          <select
            value={speed}
            disabled={!canControl}
            aria-label="Playback speed"
            onChange={(event) => onSpeedChange(Number(event.currentTarget.value))}
            className="h-8 cursor-pointer appearance-none rounded-lg border border-white/10 bg-black/45 pl-7 pr-2 text-[11px] font-medium text-white outline-none transition hover:bg-black/65 focus-visible:ring-2 focus-visible:ring-wave-cyan/60 disabled:cursor-not-allowed disabled:opacity-50"
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
            {muted || volume === 0 ? <VolumeMuteIcon size={17} /> : <VolumeIcon size={17} />}
          </ControlsButton>
          <input
            type="range"
            min={0}
            max={1}
            step={0.05}
            value={muted ? 0 : volume}
            aria-label="Volume"
            onChange={(event) => onVolumeChange(Number(event.currentTarget.value))}
            className="h-1 w-14 cursor-pointer accent-wave-cyan"
          />
        </div>

        <ControlsButton label="Keyboard shortcuts" onClick={onShowHelp} hideOnMobile>
          <HelpIcon size={17} />
        </ControlsButton>

        {pipSupported ? (
          <ControlsButton
            label={pipActive ? 'Exit picture-in-picture' : 'Picture-in-picture'}
            onClick={onTogglePip}
            hideOnMobile
          >
            <PipIcon size={17} />
          </ControlsButton>
        ) : null}

        <ControlsButton label={fullscreen ? 'Exit fullscreen' : 'Fullscreen'} onClick={onToggleFullscreen}>
          {fullscreen ? <MinimizeIcon size={17} /> : <MaximizeIcon size={17} />}
        </ControlsButton>
      </div>

      {!canControl ? (
        <p className="mt-1.5 flex items-center gap-1.5 text-[10px] text-wave-amber/90">
          <LockIcon size={12} /> Host locked viewer controls. You can still watch, chat and adjust your volume.
        </p>
      ) : null}
    </div>
  )
}

function SkipButton({
  children,
  label,
  onClick,
  disabled,
  seconds,
}: {
  children: ReactNode
  label: string
  onClick: () => void
  disabled: boolean
  seconds: number
}) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      onClick={onClick}
      disabled={disabled}
      className="relative hidden size-9 place-items-center rounded-lg text-white/85 outline-none transition hover:bg-white/10 hover:text-white focus-visible:ring-2 focus-visible:ring-wave-cyan/70 disabled:cursor-not-allowed disabled:opacity-40 sm:grid"
    >
      {children}
      <span className="absolute -bottom-0.5 text-[7px] font-bold tabular-nums">{seconds}</span>
    </button>
  )
}

function ControlsButton({
  children,
  label,
  onClick,
  disabled = false,
  hideOnMobile = false,
}: {
  children: ReactNode
  label: string
  onClick: () => void
  disabled?: boolean
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
        'grid size-8 shrink-0 place-items-center rounded-lg text-white/80 outline-none transition hover:bg-white/10 hover:text-white focus-visible:ring-2 focus-visible:ring-wave-cyan/70 disabled:cursor-not-allowed disabled:opacity-40',
        hideOnMobile && 'hidden sm:grid',
      )}
    >
      {children}
    </button>
  )
}
