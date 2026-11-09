import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { cn } from '../lib/cn'
import { projectPosition, DriftController } from '../lib/drift'
import { formatClock } from '../lib/format'
import { loadVolume, saveVolume } from '../lib/storage'
import type { PlaybackAnchor } from '../room/state'
import type { ConnectionPhase } from '../services/sync-client'
import { CloseIcon, LockIcon, PlayIcon, RefreshIcon, WifiOffIcon } from '../components/Icons'
import { KeyCap, LiveDot, Pill, Spinner } from '../components/ui'
import { PlayerControls } from './PlayerControls'

export interface WatchPlayerProps {
  mediaUrl: string
  mediaTitle: string
  playback: PlaybackAnchor | null
  canControl: boolean
  connection: ConnectionPhase
  connectionDetail: string | null
  serverNow: () => number
  skipSeconds: number
  startMuted: boolean
  showDrift: boolean
  cinemaMode: boolean
  keyboardShortcuts: boolean
  onPlay: (positionMs: number) => void
  onPause: (positionMs: number) => void
  onSeek: (positionMs: number) => void
  onSpeed: (speed: number) => void
  onEnded: (positionMs: number) => void
  onRefreshStream: () => void
}

/**
 * HTML5 player driven by the host's authoritative clock.
 *
 * User intent is sent to the host; the resulting STATE_UPDATE is applied here.
 * A 250 ms loop nudges playback rate for small drift and seeks for large drift.
 */
export function WatchPlayer({
  mediaUrl,
  mediaTitle,
  playback,
  canControl,
  connection,
  connectionDetail,
  serverNow,
  skipSeconds,
  startMuted,
  showDrift,
  cinemaMode,
  keyboardShortcuts,
  onPlay,
  onPause,
  onSeek,
  onSpeed,
  onEnded,
  onRefreshStream,
}: WatchPlayerProps) {
  const shellRef = useRef<HTMLDivElement>(null)
  const videoRef = useRef<HTMLVideoElement>(null)
  const driftRef = useRef(new DriftController())
  const playbackRef = useRef(playback)
  const serverNowRef = useRef(serverNow)
  const canControlRef = useRef(canControl)
  const scrubbingRef = useRef(false)
  const needsGestureRef = useRef(false)
  const bufferingRef = useRef(false)
  const pendingSeekRef = useRef<{ positionMs: number; play: boolean } | null>(null)
  const loadedMediaRef = useRef<string | null>(null)
  const previousUrlRef = useRef(mediaUrl)
  const lastKnownPositionRef = useRef(0)
  const endedSentRef = useRef(false)
  const hideTimerRef = useRef<number | null>(null)

  const [positionMs, setPositionMs] = useState(0)
  const [durationMs, setDurationMs] = useState(0)
  const [bufferedMs, setBufferedMs] = useState(0)
  const [playing, setPlaying] = useState(false)
  const [buffering, setBuffering] = useState(true)
  const [needsGesture, setNeedsGesture] = useState(false)
  const [streamError, setStreamError] = useState(false)
  const [volume, setVolume] = useState(1)
  const [muted, setMuted] = useState(false)
  const [fullscreen, setFullscreen] = useState(false)
  const [pipActive, setPipActive] = useState(false)
  const [scrubMs, setScrubMs] = useState<number | null>(null)
  const [driftMs, setDriftMs] = useState(0)
  const [controlsVisible, setControlsVisible] = useState(true)
  const [helpOpen, setHelpOpen] = useState(false)
  const [skipFlash, setSkipFlash] = useState<{ direction: 'back' | 'forward'; id: number } | null>(null)

  playbackRef.current = playback
  serverNowRef.current = serverNow
  canControlRef.current = canControl
  needsGestureRef.current = needsGesture
  bufferingRef.current = buffering

  const safePlay = useCallback(() => {
    const video = videoRef.current
    if (!video) return
    const promise = video.play()
    if (!promise) return
    promise
      .then(() => {
        needsGestureRef.current = false
        setNeedsGesture(false)
      })
      .catch((error: DOMException) => {
        if (error?.name === 'NotAllowedError') {
          needsGestureRef.current = true
          setNeedsGesture(true)
        }
      })
  }, [])

  // Initial volume from local preferences.
  useEffect(() => {
    const video = videoRef.current
    if (!video) return
    const stored = loadVolume()
    video.volume = stored
    video.muted = startMuted
    setVolume(stored)
    setMuted(startMuted)
  }, [startMuted])

  // A different media id on the same stream path needs a hard reload.
  useEffect(() => {
    const video = videoRef.current
    if (!video || !playback?.mediaId) return
    if (loadedMediaRef.current === playback.mediaId) return
    loadedMediaRef.current = playback.mediaId
    endedSentRef.current = false
    driftRef.current.reset(performance.now())
    const expected = projectPosition(
      playback.positionMs,
      playback.anchorServerMs,
      playback.playbackSpeed,
      playback.isPlaying,
      serverNowRef.current(),
      playback.durationMs,
    )
    pendingSeekRef.current = { positionMs: expected, play: playback.isPlaying }
    video.load()
  }, [playback?.mediaId, playback?.positionMs, playback?.anchorServerMs, playback?.playbackSpeed, playback?.isPlaying, playback?.durationMs])

  // Media-token rotation replaces the URL; preserve the viewer's position.
  useEffect(() => {
    if (previousUrlRef.current === mediaUrl) return
    previousUrlRef.current = mediaUrl
    const video = videoRef.current
    if (!video || !mediaUrl) return
    const wasPlaying = !video.paused && !video.ended
    pendingSeekRef.current = {
      positionMs: lastKnownPositionRef.current,
      play: wasPlaying || (playbackRef.current?.isPlaying ?? false),
    }
    video.load()
  }, [mediaUrl])

  // React to each authoritative state change.
  useEffect(() => {
    const video = videoRef.current
    if (!video || !playback) return
    const expected = projectPosition(
      playback.positionMs,
      playback.anchorServerMs,
      playback.playbackSpeed,
      playback.isPlaying,
      serverNowRef.current(),
      playback.durationMs,
    )
    video.playbackRate = clamp(video.playbackRate, 0.25, 4)
    if (playback.isPlaying) {
      endedSentRef.current = false
      if (video.paused && !needsGestureRef.current) safePlay()
    } else {
      if (!video.paused) video.pause()
      if (Math.abs(video.currentTime * 1000 - expected) > 300) {
        video.currentTime = expected / 1000
        lastKnownPositionRef.current = expected
      }
    }
    if (playback.durationMs > 0) setDurationMs(playback.durationMs)
  }, [playback, safePlay])

  // Drift correction + UI clock.
  useEffect(() => {
    const timer = window.setInterval(() => {
      const video = videoRef.current
      if (!video) return

      const localMs = video.currentTime * 1000
      lastKnownPositionRef.current = localMs
      setPositionMs(localMs)
      if (Number.isFinite(video.duration) && video.duration > 0) {
        setDurationMs(video.duration * 1000)
      }
      if (video.buffered.length > 0) {
        setBufferedMs(video.buffered.end(video.buffered.length - 1) * 1000)
      }
      setPlaying(!video.paused && !video.ended)

      const current = playbackRef.current
      if (!current) return
      const expected = projectPosition(
        current.positionMs,
        current.anchorServerMs,
        current.playbackSpeed,
        current.isPlaying,
        serverNowRef.current(),
        current.durationMs || (Number.isFinite(video.duration) ? video.duration * 1000 : 0),
      )
      setDriftMs(expected - localMs)

      if (scrubbingRef.current || video.seeking || video.readyState < 2 || bufferingRef.current) {
        return
      }

      const action = driftRef.current.evaluate(expected, localMs, current.isPlaying, performance.now())
      if (action.kind === 'seek') {
        if (Math.abs(expected - localMs) > 150) video.currentTime = action.positionMs / 1000
      } else {
        const multiplier = action.kind === 'speed' ? action.rate : 1
        video.playbackRate = clamp(current.playbackSpeed * multiplier, 0.25, 4)
      }

      if (current.isPlaying && video.paused && !needsGestureRef.current) {
        safePlay()
      }
    }, 250)
    return () => window.clearInterval(timer)
  }, [safePlay])

  useEffect(() => {
    const onChange = () => setFullscreen(Boolean(document.fullscreenElement))
    const onPipEnter = () => setPipActive(true)
    const onPipLeave = () => setPipActive(false)
    document.addEventListener('fullscreenchange', onChange)
    document.addEventListener('enterpictureinpicture', onPipEnter)
    document.addEventListener('leavepictureinpicture', onPipLeave)
    return () => {
      document.removeEventListener('fullscreenchange', onChange)
      document.removeEventListener('enterpictureinpicture', onPipEnter)
      document.removeEventListener('leavepictureinpicture', onPipLeave)
    }
  }, [])

  const revealControls = useCallback(() => {
    setControlsVisible(true)
    if (hideTimerRef.current !== null) window.clearTimeout(hideTimerRef.current)
    const video = videoRef.current
    if (video && !video.paused) {
      hideTimerRef.current = window.setTimeout(() => setControlsVisible(false), cinemaMode ? 2_200 : 4_500)
    }
  }, [cinemaMode])

  useEffect(() => {
    if (playing) {
      revealControls()
    } else {
      setControlsVisible(true)
      if (hideTimerRef.current !== null) window.clearTimeout(hideTimerRef.current)
    }
  }, [playing, revealControls])

  useEffect(() => () => {
    if (hideTimerRef.current !== null) window.clearTimeout(hideTimerRef.current)
  }, [])

  const togglePlay = useCallback(() => {
    const video = videoRef.current
    if (!video || !canControlRef.current) return
    if (video.paused) {
      needsGestureRef.current = false
      setNeedsGesture(false)
      safePlay()
      onPlay(video.currentTime * 1000)
    } else {
      video.pause()
      onPause(video.currentTime * 1000)
    }
    revealControls()
  }, [onPause, onPlay, revealControls, safePlay])

  const seekBy = useCallback(
    (deltaMs: number) => {
      const video = videoRef.current
      if (!video || !canControlRef.current) return
      const duration = playbackRef.current?.durationMs || video.duration * 1000 || 0
      const target = clamp(video.currentTime * 1000 + deltaMs, 0, Math.max(0, duration))
      video.currentTime = target / 1000
      lastKnownPositionRef.current = target
      onSeek(target)
      setSkipFlash({ direction: deltaMs < 0 ? 'back' : 'forward', id: Date.now() })
    },
    [onSeek],
  )

  useEffect(() => {
    if (!skipFlash) return
    const timer = window.setTimeout(() => setSkipFlash(null), 750)
    return () => window.clearTimeout(timer)
  }, [skipFlash])

  const commitSeek = useCallback(
    (valueMs: number) => {
      const video = videoRef.current
      setScrubMs(null)
      scrubbingRef.current = false
      if (!video || !canControlRef.current) return
      const duration = playbackRef.current?.durationMs || video.duration * 1000 || 0
      const target = clamp(valueMs, 0, Math.max(0, duration))
      video.currentTime = target / 1000
      lastKnownPositionRef.current = target
      driftRef.current.reset(performance.now())
      onSeek(target)
    },
    [onSeek],
  )

  const changeSpeed = useCallback(
    (speed: number) => {
      const video = videoRef.current
      if (!video || !canControlRef.current) return
      video.playbackRate = speed
      onSpeed(speed)
    },
    [onSpeed],
  )

  const changeVolume = useCallback((next: number) => {
    const video = videoRef.current
    if (!video) return
    const value = clamp(next, 0, 1)
    video.volume = value
    video.muted = value === 0
    setVolume(value)
    setMuted(value === 0)
    saveVolume(value)
  }, [])

  const toggleMute = useCallback(() => {
    const video = videoRef.current
    if (!video) return
    const next = !video.muted
    video.muted = next
    if (!next && video.volume === 0) {
      video.volume = 0.6
      setVolume(0.6)
      saveVolume(0.6)
    }
    setMuted(next)
  }, [])

  const toggleFullscreen = useCallback(() => {
    const shell = shellRef.current
    if (!shell) return
    if (document.fullscreenElement) {
      void document.exitFullscreen().catch(() => undefined)
    } else {
      void shell.requestFullscreen().catch(() => undefined)
    }
  }, [])

  const togglePip = useCallback(() => {
    const video = videoRef.current
    if (!video) return
    if (document.pictureInPictureElement) {
      void document.exitPictureInPicture().catch(() => undefined)
    } else if (document.pictureInPictureEnabled) {
      void video.requestPictureInPicture().catch(() => undefined)
    }
  }, [])

  useEffect(() => {
    if (!keyboardShortcuts) return
    const onKeyDown = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null
      if (target?.closest('input, textarea, select, button, [contenteditable="true"]')) return
      if (event.metaKey || event.ctrlKey || event.altKey) return
      const skipMs = skipSeconds * 1_000
      switch (event.key) {
        case ' ':
        case 'k':
          event.preventDefault()
          togglePlay()
          break
        case 'ArrowLeft':
          event.preventDefault()
          seekBy(-skipMs)
          break
        case 'ArrowRight':
          event.preventDefault()
          seekBy(skipMs)
          break
        case 'm':
          event.preventDefault()
          toggleMute()
          break
        case 'f':
          event.preventDefault()
          toggleFullscreen()
          break
        case 'p':
          event.preventDefault()
          togglePip()
          break
        case '?':
          event.preventDefault()
          setHelpOpen((value) => !value)
          break
        case 'Escape':
          setHelpOpen(false)
          break
        default:
          break
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [keyboardShortcuts, seekBy, skipSeconds, toggleFullscreen, toggleMute, togglePip, togglePlay])

  const handleLoadedMetadata = () => {
    const video = videoRef.current
    if (!video) return
    if (Number.isFinite(video.duration) && video.duration > 0) setDurationMs(video.duration * 1000)
    const pending = pendingSeekRef.current
    if (!pending) return
    pendingSeekRef.current = null
    const max = Number.isFinite(video.duration) && video.duration > 0 ? video.duration * 1000 : pending.positionMs
    const target = clamp(pending.positionMs, 0, Math.max(0, max))
    try {
      video.currentTime = target / 1000
    } catch {
      // Some browsers reject seeks before the first frame; the sync loop retries.
    }
    lastKnownPositionRef.current = target
    if (pending.play) safePlay()
  }

  const displayDuration = Math.max(durationMs, playback?.durationMs ?? 0)
  const displayPosition = scrubMs ?? positionMs
  const canPip = typeof document !== 'undefined' && document.pictureInPictureEnabled
  const showCenterPlay = !playing && !buffering && !streamError
  const waitingForHost = !playback && (connection === 'connecting' || connection === 'reconnecting')

  const handleVideoDoubleClick = (event: React.MouseEvent<HTMLVideoElement>) => {
    const rect = event.currentTarget.getBoundingClientRect()
    const ratio = rect.width > 0 ? (event.clientX - rect.left) / rect.width : 0.5
    if (ratio < 0.34) {
      seekBy(-skipSeconds * 1_000)
    } else if (ratio > 0.66) {
      seekBy(skipSeconds * 1_000)
    } else {
      toggleFullscreen()
    }
  }

  return (
    <div
      ref={shellRef}
      className={cn(
        'player-shell group/player relative aspect-video w-full overflow-hidden rounded-2xl border border-white/[0.09] bg-black shadow-glow',
        fullscreen && 'rounded-none border-0 shadow-none',
      )}
      onMouseMove={revealControls}
      onMouseLeave={() => {
        if (playing) setControlsVisible(false)
      }}
      onTouchStart={revealControls}
    >
      <video
        ref={videoRef}
        src={mediaUrl || undefined}
        className="h-full w-full object-contain"
        playsInline
        preload="auto"
        aria-label={mediaTitle ? `Video: ${mediaTitle}` : 'WatchWave video'}
        onClick={togglePlay}
        onDoubleClick={handleVideoDoubleClick}
        onLoadedMetadata={handleLoadedMetadata}
        onTimeUpdate={(event) => {
          lastKnownPositionRef.current = event.currentTarget.currentTime * 1000
        }}
        onPlay={() => {
          setPlaying(true)
          setNeedsGesture(false)
          needsGestureRef.current = false
          if (eventSafeDuration(videoRef.current) > 0) endedSentRef.current = false
        }}
        onPause={() => {
          setPlaying(false)
          setControlsVisible(true)
        }}
        onWaiting={() => setBuffering(true)}
        onPlaying={() => setBuffering(false)}
        onCanPlay={() => setBuffering(false)}
        onError={() => setStreamError(true)}
        onEnded={() => {
          const video = videoRef.current
          const duration = video ? video.duration * 1000 : displayDuration
          setPlaying(false)
          if (!endedSentRef.current && canControlRef.current) {
            endedSentRef.current = true
            onEnded(duration)
          }
        }}
      />

      {skipFlash ? (
        <div
          key={skipFlash.id}
          className={cn(
            'pointer-events-none absolute inset-y-0 z-20 flex w-1/3 items-center justify-center bg-white/[0.06] animate-fade-in',
            skipFlash.direction === 'back' ? 'left-0 rounded-r-full' : 'right-0 rounded-l-full',
          )}
        >
          <span className="flex flex-col items-center gap-1 rounded-2xl bg-black/55 px-4 py-3 text-white backdrop-blur-sm">
            <span className="font-mono text-lg font-semibold">{skipFlash.direction === 'back' ? '−' : '+'}{skipSeconds}s</span>
            <span className="text-[10px] uppercase tracking-[0.14em] text-white/60">{skipFlash.direction === 'back' ? 'rewind' : 'forward'}</span>
          </span>
        </div>
      ) : null}

      <div
        className={cn(
          'pointer-events-none absolute inset-0 z-30 flex flex-col justify-between transition-opacity duration-200',
          controlsVisible || !playing ? 'opacity-100' : 'opacity-0',
        )}
      >
        <div className="flex items-start justify-between gap-3 bg-gradient-to-b from-black/75 to-transparent px-3.5 pb-6 pt-2.5 sm:px-4 sm:pb-12 sm:pt-3">
          <div className="min-w-0">
            <p className="truncate text-sm font-medium text-white/90">{mediaTitle || 'WatchWave room'}</p>
            <div className="mt-1 flex flex-wrap items-center gap-1.5">
              {connection === 'live' ? (
                <Pill tone="green">
                  <LiveDot /> Live
                </Pill>
              ) : connection === 'failed' || connection === 'closed' ? (
                <Pill tone="red">
                  <WifiOffIcon size={12} /> Offline
                </Pill>
              ) : (
                <Pill tone="amber">
                  <LiveDot tone="amber" /> {connection === 'reconnecting' ? 'Reconnecting' : 'Connecting'}
                </Pill>
              )}
              {!canControl ? (
                <Pill tone="amber">
                  <LockIcon size={12} /> Locked
                </Pill>
              ) : null}
            </div>
          </div>
          <p className="hidden shrink-0 font-mono text-[11px] text-white/50 sm:block">
            {formatClock(displayPosition)} / {formatClock(displayDuration)}
          </p>
        </div>

        <PlayerControls
          playing={playing}
          canControl={canControl}
          positionMs={displayPosition}
          durationMs={displayDuration}
          bufferedMs={bufferedMs}
          volume={volume}
          muted={muted}
          speed={playback?.playbackSpeed ?? 1}
          driftMs={driftMs}
          skipSeconds={skipSeconds}
          showDrift={showDrift}
          fullscreen={fullscreen}
          pipSupported={canPip}
          pipActive={pipActive}
          onTogglePlay={togglePlay}
          onSkip={seekBy}
          onScrubStart={() => {
            scrubbingRef.current = true
            setScrubMs(lastKnownPositionRef.current)
          }}
          onScrub={(value) => setScrubMs(value)}
          onSeekCommit={commitSeek}
          onVolumeChange={changeVolume}
          onToggleMute={toggleMute}
          onSpeedChange={changeSpeed}
          onToggleFullscreen={toggleFullscreen}
          onTogglePip={togglePip}
          onShowHelp={() => setHelpOpen(true)}
        />
      </div>

      {helpOpen ? <ShortcutsHelp onClose={() => setHelpOpen(false)} skipSeconds={skipSeconds} /> : null}

      {streamError ? (
        <CentrePanel>
          <WifiOffIcon size={30} className="text-wave-red" />
          <p className="text-sm font-medium text-white">The video stream stopped</p>
          <p className="max-w-xs text-center text-xs text-wave-muted">
            The connection may have moved to a new access token. Refresh the stream to keep watching.
          </p>
          <button
            type="button"
            className="mt-1 inline-flex h-10 items-center gap-2 rounded-xl border border-white/15 bg-white/10 px-4 text-sm font-medium text-white transition hover:bg-white/15"
            onClick={() => {
              setStreamError(false)
              onRefreshStream()
            }}
          >
            <RefreshIcon size={16} /> Refresh stream
          </button>
        </CentrePanel>
      ) : waitingForHost && !playback ? (
        <CentrePanel>
          <Spinner label={connectionDetail ?? 'Waiting for the host to start…'} />
        </CentrePanel>
      ) : needsGesture ? (
        <CentrePanel>
          <button
            type="button"
            onClick={togglePlay}
            className="grid size-16 place-items-center rounded-full border border-white/20 bg-white/10 text-white shadow-glow backdrop-blur transition hover:scale-105 hover:bg-white/15"
            aria-label="Start synchronized playback"
          >
            <PlayIcon size={30} />
          </button>
          <p className="text-sm font-medium text-white">Tap to join playback</p>
          <p className="max-w-xs text-center text-xs text-wave-muted">
            Your browser blocked autoplay. One tap starts the room at the host's exact position.
          </p>
        </CentrePanel>
      ) : showCenterPlay ? (
        <CentrePanel>
          <button
            type="button"
            onClick={togglePlay}
            disabled={!canControl}
            className="grid size-16 place-items-center rounded-full border border-white/20 bg-white/10 text-white shadow-glow backdrop-blur transition hover:scale-105 hover:bg-white/15 disabled:cursor-not-allowed disabled:opacity-40"
            aria-label={canControl ? 'Play for everyone' : 'Controls locked by the host'}
          >
            {canControl ? <PlayIcon size={30} /> : <LockIcon size={26} />}
          </button>
          <p className="text-sm font-medium text-white">
            {canControl ? 'Paused' : 'Paused by the host'}
          </p>
          <p className="max-w-xs text-center text-xs text-wave-muted">
            {canControl ? 'Press play to continue for everyone.' : 'Viewer controls are locked.'}
          </p>
        </CentrePanel>
      ) : buffering ? (
        <CentrePanel>
          <Spinner label="Buffering from the host…" />
        </CentrePanel>
      ) : null}
    </div>
  )
}

function CentrePanel({ children }: { children: ReactNode }) {
  return (
    <div className="pointer-events-none absolute inset-0 grid place-items-center">
      <div className="pointer-events-auto flex flex-col items-center gap-2 rounded-2xl bg-black/45 px-6 py-5 backdrop-blur-sm">
        {children}
      </div>
    </div>
  )
}

function eventSafeDuration(video: HTMLVideoElement | null): number {
  return video && Number.isFinite(video.duration) ? video.duration : 0
}

function ShortcutsHelp({ onClose, skipSeconds }: { onClose: () => void; skipSeconds: number }) {
  const shortcuts: Array<[string, string]> = [
    ['Space / K', 'Play or pause'],
    ['← / →', `Skip ${skipSeconds} seconds`],
    ['M', 'Mute or unmute'],
    ['F', 'Fullscreen'],
    ['P', 'Picture-in-picture'],
    ['?', 'Toggle this help'],
    ['Esc', 'Close overlays'],
  ]
  return (
    <div
      className="absolute inset-0 z-50 grid place-items-center bg-black/70 p-4 backdrop-blur-sm"
      role="dialog"
      aria-modal="true"
      aria-label="Keyboard shortcuts"
      onClick={onClose}
    >
      <div
        className="w-full max-w-sm rounded-2xl border border-white/12 bg-ink-900/95 p-5 shadow-float"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-4">
          <div>
            <p className="text-[10px] font-semibold uppercase tracking-[0.18em] text-wave-cyan">Keyboard</p>
            <h2 className="mt-1 font-display text-lg font-semibold text-white">Player shortcuts</h2>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="grid size-8 place-items-center rounded-lg text-wave-muted transition hover:bg-white/10 hover:text-white"
            aria-label="Close shortcut help"
          >
            <CloseIcon size={17} />
          </button>
        </div>
        <dl className="mt-4 space-y-2.5">
          {shortcuts.map(([key, action]) => (
            <div key={key} className="flex items-center justify-between gap-4">
              <dt>
                <KeyCap>{key}</KeyCap>
              </dt>
              <dd className="text-[12px] text-wave-muted">{action}</dd>
            </div>
          ))}
        </dl>
        <p className="mt-4 border-t border-white/[0.06] pt-3 text-[10px] leading-relaxed text-wave-muted/60">
          Shortcuts are ignored while you are typing in chat or a form. You can disable them in Settings → Playback.
        </p>
      </div>
    </div>
  )
}

function clamp(value: number, min: number, max: number): number {
  if (!Number.isFinite(value)) return min
  return Math.min(max, Math.max(min, value))
}
