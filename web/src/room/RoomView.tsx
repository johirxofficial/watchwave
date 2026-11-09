import { useMemo, useState } from 'react'
import { Brand } from '../components/Brand'
import { ConnectionBadge } from '../components/ConnectionBadge'
import {
  CrownIcon,
  LockIcon,
  LogOutIcon,
  RefreshIcon,
  ShieldIcon,
  UsersIcon,
  WaveIcon,
} from '../components/Icons'
import { ParticipantList } from '../components/ParticipantList'
import { ShareButton } from '../components/ShareButton'
import { Banner, Button, LiveDot, Panel, Pill } from '../components/ui'
import { formatClock } from '../lib/format'
import { roomWithDefaults } from '../lib/protocol'
import type { UseWatchPartyResult } from '../hooks/useWatchParty'
import { WatchPlayer } from '../player/WatchPlayer'

interface RoomViewProps {
  roomId: string
  party: UseWatchPartyResult
  onLeave: () => void
}

export function RoomView({ roomId, party, onLeave }: RoomViewProps) {
  const { state } = party
  const room = state.room ? roomWithDefaults(state.room) : party.preview ? roomWithDefaults(party.preview) : null
  const mediaTitle = state.playback?.mediaTitle ?? room?.media?.title ?? 'WatchWave'
  const duration = state.playback?.durationMs ?? room?.media?.durationMs ?? 0
  const [refreshing, setRefreshing] = useState(false)

  const shareUrl = useMemo(() => {
    const url = new URL(`/room/${encodeURIComponent(roomId)}`, window.location.origin)
    if (party.inviteToken) url.searchParams.set('t', party.inviteToken)
    return url.toString()
  }, [party.inviteToken, roomId])

  const host = state.participants.find((participant) => participant.isHost)
  const controlLabel = state.controlsLocked
    ? 'Only the host can control playback'
    : state.canControl
      ? 'Everyone can control playback'
      : 'Viewer controls are locked'

  return (
    <div className="min-h-screen pb-10">
      <header className="sticky top-0 z-40 border-b border-white/[0.06] bg-ink-950/85 backdrop-blur-xl">
        <div className="mx-auto flex w-full max-w-[1500px] items-center gap-3 px-3 py-3 sm:px-6">
          <Brand compact />
          <span className="hidden h-6 w-px bg-white/10 sm:block" />
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium text-white">{room?.roomName ?? 'Watch party'}</p>
            <p className="truncate text-[11px] text-wave-muted">
              Hosted by {room?.hostName ?? host?.displayName ?? 'the host'}
            </p>
          </div>
          <ConnectionBadge phase={party.connection} detail={party.connectionDetail} hostOffline={state.hostOffline} />
          <div className="hidden sm:block">
            <ShareButton url={shareUrl} compact />
          </div>
          <Button
            variant="ghost"
            size="sm"
            onClick={onLeave}
            disabled={party.leaving}
            aria-label="Leave the room"
            title="Leave the room"
            className="px-2.5"
          >
            <LogOutIcon size={17} />
            <span className="hidden md:inline">Leave</span>
          </Button>
        </div>
      </header>

      <main className="mx-auto mt-4 grid w-full max-w-[1500px] items-start gap-4 px-3 sm:px-6 lg:grid-cols-[minmax(0,1fr)_360px] lg:gap-5">
        <section className="min-w-0 space-y-3">
          <WatchPlayer
            mediaUrl={party.mediaUrl}
            mediaTitle={mediaTitle}
            playback={state.playback}
            canControl={state.canControl}
            connection={party.connection}
            connectionDetail={party.connectionDetail}
            serverNow={party.serverNow}
            onPlay={party.play}
            onPause={party.pause}
            onSeek={party.seek}
            onSpeed={party.setSpeed}
            onEnded={party.ended}
            onRefreshStream={() => {
              void party.refreshStream()
            }}
          />

          <div className="space-y-2">
            {party.connection === 'failed' ? (
              <Banner
                kind="error"
                title="Disconnected from the host"
                action={
                  <Button size="sm" variant="secondary" onClick={party.retryConnection} className="shrink-0">
                    <RefreshIcon size={15} /> Retry
                  </Button>
                }
              >
                {party.connectionDetail ?? 'Playback will resume after the socket reconnects.'}
              </Banner>
            ) : state.hostOffline ? (
              <Banner kind="warn" title="The host went offline">
                Playback is paused at the current position. The room reconnects automatically when the host returns.
              </Banner>
            ) : state.notice ? (
              <Banner
                kind={state.notice.kind === 'error' ? 'error' : state.notice.kind === 'warn' ? 'warn' : 'info'}
                action={
                  <button
                    type="button"
                    onClick={party.dismissNotice}
                    className="shrink-0 rounded-md px-2 py-1 text-[11px] text-inherit/80 transition hover:bg-white/10"
                  >
                    Dismiss
                  </button>
                }
              >
                {state.notice.message}
              </Banner>
            ) : party.connection === 'reconnecting' ? (
              <Banner kind="warn">
                {party.connectionDetail ?? 'Reconnecting to the host. The player keeps its position.'}
              </Banner>
            ) : null}
          </div>

          <Panel className="flex flex-wrap items-center gap-x-5 gap-y-2 px-4 py-3">
            <span className="min-w-0 flex-1">
              <span className="block truncate text-sm font-medium text-white">{mediaTitle}</span>
              <span className="text-[11px] text-wave-muted">
                {formatClock(duration)} · streamed from the host's storage · original quality
              </span>
            </span>
            <span className="inline-flex items-center gap-1.5 text-[12px] text-wave-muted">
              <UsersIcon size={14} /> {state.participants.length} watching
            </span>
            <Pill tone={state.controlsLocked ? 'amber' : 'cyan'}>
              {state.controlsLocked ? <LockIcon size={12} /> : <WaveIcon size={12} />}
              {state.controlsLocked ? 'Host controls' : 'Shared controls'}
            </Pill>
          </Panel>
        </section>

        <aside className="space-y-3">
          <Panel className="p-4">
            <div className="flex items-center justify-between">
              <h2 className="text-[12px] font-semibold uppercase tracking-[0.14em] text-wave-muted">Connection</h2>
              <ConnectionBadge
                phase={party.connection}
                detail={party.connectionDetail}
                hostOffline={state.hostOffline}
              />
            </div>
            <dl className="mt-3 space-y-2 text-[12px]">
              <Row label="Transport">
                <span className="font-mono">{window.location.protocol === 'https:' ? 'WSS' : 'WS'} · host relay</span>
              </Row>
              <Row label="Clock offset">
                <span className="font-mono">
                  {party.serverOffsetMs >= 0 ? '+' : ''}
                  {Math.round(party.serverOffsetMs)} ms
                </span>
              </Row>
              <Row label="Controls">
                <span className={state.controlsLocked ? 'text-wave-amber' : 'text-wave-green'}>{controlLabel}</span>
              </Row>
              <Row label="Access">
                <span className="inline-flex items-center gap-1 text-wave-muted">
                  <ShieldIcon size={13} /> Token-scoped
                </span>
              </Row>
            </dl>
            <button
              type="button"
              disabled={refreshing}
              onClick={async () => {
                setRefreshing(true)
                await party.refreshStream()
                setRefreshing(false)
              }}
              className="mt-3 inline-flex items-center gap-1.5 rounded-lg px-2 py-1 text-[11px] text-wave-muted outline-none transition hover:bg-white/[0.06] hover:text-white focus-visible:ring-2 focus-visible:ring-wave-cyan/60 disabled:opacity-50"
            >
              <RefreshIcon size={13} className={refreshing ? 'animate-spin' : undefined} />
              Refresh video access
            </button>
          </Panel>

          <Panel className="pb-2">
            <ParticipantList
              participants={state.participants}
              selfId={state.self?.id}
              maxParticipants={room?.maxParticipants ?? 20}
            />
            <div className="mx-4 mb-3 flex items-center gap-2 rounded-lg border border-white/[0.06] bg-black/20 px-3 py-2 text-[11px] text-wave-muted">
              {state.isHost ? <CrownIcon size={13} className="text-wave-amber" /> : <LockIcon size={13} />}
              {state.isHost
                ? 'You are the host. Other viewers can control playback when shared controls are on.'
                : state.controlsLocked
                  ? 'The host locked shared controls. You can still adjust volume locally.'
                  : 'Shared controls are on. Your play, pause and seek reach everyone.'}
            </div>
          </Panel>

          <Panel className="p-4">
            <div className="flex items-center justify-between gap-3">
              <div>
                <h2 className="text-[12px] font-semibold uppercase tracking-[0.14em] text-wave-muted">Invite</h2>
                <p className="mt-1 text-[12px] leading-relaxed text-wave-muted/80">
                  Anyone with this link can join while the invite is valid.
                </p>
              </div>
              <ShareButton url={shareUrl} compact label="Copy" />
            </div>
            <code className="mt-3 block truncate rounded-lg border border-white/[0.07] bg-black/30 px-3 py-2 font-mono text-[11px] text-slate-300">
              {shareUrl}
            </code>
            <p className="mt-2 text-[11px] leading-relaxed text-wave-muted/70">
              The host can rotate or revoke this link from the Android app. Ask for a fresh one if it stops working.
            </p>
          </Panel>

          <Panel className="flex items-center gap-2 px-4 py-3 text-[11px] text-wave-muted">
            <LiveDot tone="cyan" />
            Synced through the host's authoritative playback clock.
          </Panel>
        </aside>
      </main>
    </div>
  )
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <dt className="text-wave-muted/80">{label}</dt>
      <dd className="text-right text-slate-300">{children}</dd>
    </div>
  )
}
