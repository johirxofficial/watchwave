import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { Brand } from '../components/Brand'
import { ChatPanel } from '../components/ChatPanel'
import { ConnectionBadge } from '../components/ConnectionBadge'
import {
  ChatIcon,
  ChevronRightIcon,
  CrownIcon,
  InfoIcon,
  LockIcon,
  LogOutIcon,
  MonitorSmartphoneIcon,
  RefreshIcon,
  SettingsIcon,
  ShieldIcon,
  UsersIcon,
  WaveIcon,
} from '../components/Icons'
import { ParticipantList } from '../components/ParticipantList'
import { ShareButton } from '../components/ShareButton'
import { Banner, Button, Card, LiveDot, Panel, Pill } from '../components/ui'
import { cn } from '../lib/cn'
import { formatClock } from '../lib/format'
import { usePreferences } from '../lib/preferences-context'
import { roomWithDefaults } from '../lib/protocol'
import type { UseWatchPartyResult } from '../hooks/useWatchParty'
import { WatchPlayer } from '../player/WatchPlayer'

interface RoomViewProps {
  roomId: string
  party: UseWatchPartyResult
  onLeave: () => void
}

type RoomTab = 'chat' | 'people' | 'room'

export function RoomView({ roomId, party, onLeave }: RoomViewProps) {
  const { state } = party
  const { preferences } = usePreferences()
  const room = state.room ? roomWithDefaults(state.room) : party.preview ? roomWithDefaults(party.preview) : null
  const mediaTitle = state.playback?.mediaTitle ?? room?.media?.title ?? 'WatchWave'
  const duration = state.playback?.durationMs ?? room?.media?.durationMs ?? 0
  const [refreshing, setRefreshing] = useState(false)
  const [tab, setTab] = useState<RoomTab>('chat')
  const [theater, setTheater] = useState(false)

  const shareUrl = useMemo(() => {
    const url = new URL(`/room/${encodeURIComponent(roomId)}`, window.location.origin)
    if (party.inviteToken) url.searchParams.set('t', party.inviteToken)
    return url.toString()
  }, [party.inviteToken, roomId])

  const host = state.participants.find((participant) => participant.isHost)
  const hostName = room?.hostName ?? host?.displayName ?? 'the host'
  const controlLabel = state.controlsLocked
    ? 'Only the host can control playback'
    : state.canControl
      ? 'Everyone can control playback'
      : 'Viewer controls are locked'

  return (
    <div className="min-h-screen bg-ink-950 pb-10">
      <header className="sticky top-0 z-40 border-b border-white/[0.06] bg-ink-950/85 backdrop-blur-xl">
        <div className="mx-auto flex w-full max-w-[1680px] items-center gap-2.5 px-3 py-2.5 sm:px-4 lg:px-6">
          <Brand compact />
          <span className="hidden h-6 w-px bg-white/10 sm:block" />
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2">
              <p className="truncate text-[13px] font-semibold text-white sm:text-sm">{room?.roomName ?? 'Watch party'}</p>
              {state.isHost ? <Pill tone="amber" className="hidden sm:inline-flex">host</Pill> : null}
            </div>
            <p className="truncate text-[10px] text-wave-muted sm:text-[11px]">
              {room ? `Hosted by ${hostName}` : 'Connecting to the host'}
              {duration > 0 ? ` · ${formatClock(duration)}` : ''}
            </p>
          </div>
          <ConnectionBadge phase={party.connection} detail={party.connectionDetail} hostOffline={state.hostOffline} />
          <div className="hidden items-center gap-1 sm:flex">
            <Button
              variant="ghost"
              size="icon"
              onClick={() => setTheater((value) => !value)}
              aria-label={theater ? 'Exit theater mode' : 'Theater mode'}
              title={theater ? 'Exit theater mode' : 'Theater mode'}
            >
              <MonitorSmartphoneIcon size={18} />
            </Button>
            <Link
              to="/settings"
              aria-label="Player settings"
              title="Player settings"
              className="grid size-10 place-items-center rounded-xl text-slate-300 outline-none transition hover:bg-white/[0.07] hover:text-white focus-visible:ring-2 focus-visible:ring-wave-cyan/60"
            >
              <SettingsIcon size={18} />
            </Link>
          </div>
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

      <main
        className={cn(
          'mx-auto mt-3 grid w-full max-w-[1680px] items-start gap-3 px-3 sm:px-4 lg:mt-4 lg:gap-4 lg:px-6',
          theater ? 'grid-cols-1' : 'lg:grid-cols-[minmax(0,1fr)_360px] xl:grid-cols-[minmax(0,1fr)_390px]',
        )}
      >
        <section className="min-w-0 space-y-3">
          <WatchPlayer
            mediaUrl={party.mediaUrl}
            mediaTitle={mediaTitle}
            playback={state.playback}
            canControl={state.canControl}
            connection={party.connection}
            connectionDetail={party.connectionDetail}
            serverNow={party.serverNow}
            skipSeconds={preferences.skipSeconds}
            startMuted={preferences.startMuted}
            showDrift={preferences.showDrift}
            cinemaMode={preferences.cinemaMode}
            keyboardShortcuts={preferences.keyboardShortcuts}
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

          <Card className="density-card flex flex-col gap-2 px-4 py-3 sm:flex-row sm:items-center sm:gap-5">
            <span className="min-w-0 flex-1">
              <span className="flex items-center gap-2">
                <span className="truncate text-sm font-semibold text-white">{mediaTitle}</span>
                {state.playback?.isPlaying ? <LiveDot tone="green" /> : null}
              </span>
              <span className="mt-0.5 block truncate text-[11px] text-wave-muted">
                {formatClock(duration)} · original quality · streamed from {hostName}
              </span>
            </span>
            <span className="flex items-center gap-3">
              <span className="inline-flex items-center gap-1.5 text-[12px] text-wave-muted">
                <UsersIcon size={14} /> {state.participants.length} watching
              </span>
              <Pill tone={state.controlsLocked ? 'amber' : 'cyan'}>
                {state.controlsLocked ? <LockIcon size={12} /> : <WaveIcon size={12} />}
                {state.controlsLocked ? 'Host controls' : 'Shared controls'}
              </Pill>
            </span>
          </Card>

          <div className="grid gap-3 sm:grid-cols-2 lg:hidden">
            <MobileCard
              icon={<UsersIcon size={17} />}
              title="People"
              detail={`${state.participants.length} in the room`}
              onClick={() => setTab('people')}
            />
            <MobileCard
              icon={<InfoIcon size={17} />}
              title="Room details"
              detail={state.controlsLocked ? 'Host controls' : 'Shared controls'}
              onClick={() => setTab('room')}
            />
          </div>
        </section>

        {!theater ? (
          <aside className="hidden min-w-0 space-y-3 lg:block">
            <ChatPanel
              messages={state.chat}
              selfId={state.self?.id}
              connected={party.connection === 'live'}
              compact={preferences.compactChat}
              participants={state.participants}
              onSend={party.sendChat}
            />

            <Panel className="pb-2">
              <ParticipantList
                participants={state.participants}
                selfId={state.self?.id}
                maxParticipants={room?.maxParticipants ?? 20}
              />
              <div className="mx-4 mb-3 flex items-start gap-2 rounded-xl border border-white/[0.06] bg-black/20 px-3 py-2.5 text-[11px] leading-relaxed text-wave-muted">
                {state.isHost ? <CrownIcon size={13} className="mt-0.5 shrink-0 text-wave-amber" /> : <LockIcon size={13} className="mt-0.5 shrink-0" />}
                {state.isHost
                  ? 'You are the host. Other viewers can control playback when shared controls are on.'
                  : state.controlsLocked
                    ? 'The host locked shared controls. You can still adjust volume locally and chat.'
                    : 'Shared controls are on. Your play, pause and seek reach everyone.'}
              </div>
            </Panel>

            <RoomInfoPanel
              party={party}
              roomId={roomId}
              shareUrl={shareUrl}
              controlLabel={controlLabel}
              refreshing={refreshing}
              setRefreshing={setRefreshing}
            />
          </aside>
        ) : null}
      </main>

      <div className="mx-auto mt-3 w-full max-w-[1680px] px-3 sm:px-4 lg:hidden">
        <div className="rounded-2xl border border-white/[0.07] bg-ink-850/70">
          <div className="flex items-center gap-1 border-b border-white/[0.06] p-1.5">
            {(
              [
                { key: 'chat', label: 'Chat', icon: <ChatIcon size={15} /> },
                { key: 'people', label: 'People', icon: <UsersIcon size={15} /> },
                { key: 'room', label: 'Room', icon: <InfoIcon size={15} /> },
              ] as const
            ).map((item) => (
              <button
                key={item.key}
                type="button"
                onClick={() => setTab(item.key)}
                className={cn(
                  'inline-flex flex-1 items-center justify-center gap-1.5 rounded-xl px-3 py-2.5 text-[12px] font-medium transition',
                  tab === item.key ? 'bg-white/[0.09] text-white' : 'text-wave-muted hover:text-slate-200',
                )}
              >
                {item.icon}
                {item.label}
                {item.key === 'people' ? (
                  <span className="rounded-full bg-white/10 px-1.5 text-[10px] text-wave-muted">{state.participants.length}</span>
                ) : null}
              </button>
            ))}
          </div>
          <div className="p-2">
            {tab === 'chat' ? (
              <ChatPanel
                messages={state.chat}
                selfId={state.self?.id}
                connected={party.connection === 'live'}
                compact={preferences.compactChat}
                participants={state.participants}
                onSend={party.sendChat}
                embedded
              />
            ) : tab === 'people' ? (
              <div className="pb-1">
                <ParticipantList
                  participants={state.participants}
                  selfId={state.self?.id}
                  maxParticipants={room?.maxParticipants ?? 20}
                />
              </div>
            ) : (
              <RoomInfoPanel
                party={party}
                roomId={roomId}
                shareUrl={shareUrl}
                controlLabel={controlLabel}
                refreshing={refreshing}
                setRefreshing={setRefreshing}
                embedded
              />
            )}
          </div>
        </div>
      </div>

      {theater ? (
        <div className="mx-auto mt-4 hidden w-full max-w-[1680px] px-3 sm:px-4 lg:block lg:px-6">
          <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_360px]">
            <div className="hidden lg:block">
              <ChatPanel
                messages={state.chat}
                selfId={state.self?.id}
                connected={party.connection === 'live'}
                compact={preferences.compactChat}
                participants={state.participants}
                onSend={party.sendChat}
              />
            </div>
            <RoomInfoPanel
              party={party}
              roomId={roomId}
              shareUrl={shareUrl}
              controlLabel={controlLabel}
              refreshing={refreshing}
              setRefreshing={setRefreshing}
            />
          </div>
        </div>
      ) : null}

      <footer className="mx-auto mt-6 hidden w-full max-w-[1680px] items-center justify-between px-6 text-[10px] text-wave-muted/45 lg:flex">
        <span className="inline-flex items-center gap-1.5">
          <ShieldIcon size={12} /> Session token stays in this tab · invite can be revoked by the host
        </span>
        <Link to="/settings" className="inline-flex items-center gap-1.5 transition hover:text-wave-muted">
          Player settings <ChevronRightIcon size={12} />
        </Link>
      </footer>
    </div>
  )
}

function RoomInfoPanel({
  party,
  roomId,
  shareUrl,
  controlLabel,
  refreshing,
  setRefreshing,
  embedded = false,
}: {
  party: UseWatchPartyResult
  roomId: string
  shareUrl: string
  controlLabel: string
  refreshing: boolean
  setRefreshing: (value: boolean) => void
  embedded?: boolean
}) {
  const { preferences } = usePreferences()
  const state = party.state

  const inner = (
    <>
      <div className={cn('p-4', embedded && 'p-3')}>
        <div className="flex items-center justify-between gap-3">
          <h2 className="text-[11px] font-semibold uppercase tracking-[0.16em] text-wave-muted">Invite people</h2>
          <ShareButton url={shareUrl} compact label="Copy" />
        </div>
        <code className="mt-3 block truncate rounded-xl border border-white/[0.07] bg-black/30 px-3 py-2 font-mono text-[10px] text-slate-300">
          {shareUrl}
        </code>
        <p className="mt-2 text-[11px] leading-relaxed text-wave-muted/70">
          Anyone with this link can join while the invite is valid. The host can rotate it from the Android app.
        </p>
      </div>

      {preferences.showDiagnostics ? (
        <div className="surface-divider p-4">
          <div className="flex items-center justify-between">
            <h2 className="text-[11px] font-semibold uppercase tracking-[0.16em] text-wave-muted">Diagnostics</h2>
            <ConnectionBadge phase={party.connection} detail={party.connectionDetail} hostOffline={state.hostOffline} />
          </div>
          <dl className="mt-3 space-y-2 text-[11px]">
            <Row label="Transport">
              <span className="font-mono">{window.location.protocol === 'https:' ? 'WSS' : 'WS'} · host relay</span>
            </Row>
            <Row label="Clock offset">
              <span className={cn('font-mono', Math.abs(party.serverOffsetMs) > 120 && 'text-wave-amber')}>
                {party.serverOffsetMs >= 0 ? '+' : ''}
                {Math.round(party.serverOffsetMs)} ms
              </span>
            </Row>
            <Row label="Controls">
              <span className={state.controlsLocked ? 'text-wave-amber' : 'text-wave-green'}>{controlLabel}</span>
            </Row>
            <Row label="Room">
              <span className="font-mono">{roomId}</span>
            </Row>
            <Row label="Access">
              <span className="inline-flex items-center gap-1 text-wave-muted">
                <ShieldIcon size={12} /> Token-scoped
              </span>
            </Row>
            <Row label="Protocol">
              <span className="font-mono">v1</span>
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
        </div>
      ) : (
        <div className="surface-divider flex items-center justify-between gap-3 p-4 text-[11px] text-wave-muted">
          <span className="inline-flex items-center gap-1.5">
            <LiveDot tone="cyan" /> Synced through the host clock
          </span>
          <Link to="/settings" className="inline-flex items-center gap-1 text-wave-muted transition hover:text-white">
            Settings <ChevronRightIcon size={12} />
          </Link>
        </div>
      )}
    </>
  )

  if (embedded) return <div>{inner}</div>
  return <Panel className="overflow-hidden">{inner}</Panel>
}

function MobileCard({
  icon,
  title,
  detail,
  onClick,
}: {
  icon: React.ReactNode
  title: string
  detail: string
  onClick: () => void
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="app-card flex items-center gap-3 p-3.5 text-left transition hover:bg-ink-800/90"
    >
      <span className="grid size-9 place-items-center rounded-xl border border-white/[0.08] bg-white/[0.04] text-wave-cyan">
        {icon}
      </span>
      <span className="min-w-0 flex-1">
        <span className="block text-[12px] font-semibold text-white">{title}</span>
        <span className="block truncate text-[10px] text-wave-muted">{detail}</span>
      </span>
      <ChevronRightIcon size={15} className="text-wave-muted" />
    </button>
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
