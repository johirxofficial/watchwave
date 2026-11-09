import { useEffect } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { Brand } from '../components/Brand'
import { AlertTriangleIcon, LockIcon, LogOutIcon, WaveIcon } from '../components/Icons'
import { Banner, Button, Panel, Spinner } from '../components/ui'
import { useWatchParty, type UseWatchPartyResult } from '../hooks/useWatchParty'
import { isValidRoomId } from '../lib/invite'
import { JoinPanel } from '../room/JoinPanel'
import { RoomView } from '../room/RoomView'
import { NotFoundPage } from './NotFoundPage'

export function RoomPage() {
  const { roomId } = useParams<{ roomId: string }>()
  if (!roomId || !isValidRoomId(roomId)) return <NotFoundPage />
  return <RoomSession key={roomId} roomId={roomId} />
}

function RoomSession({ roomId }: { roomId: string }) {
  const [params] = useSearchParams()
  const inviteToken = params.get('t')
  const party = useWatchParty(roomId, inviteToken)
  const navigate = useNavigate()

  useEffect(() => {
    const name = party.state.room?.roomName ?? party.preview?.roomName
    document.title = name ? `${name} · WatchWave` : 'WatchWave'
    return () => {
      document.title = 'WatchWave — watch together, in sync'
    }
  }, [party.preview?.roomName, party.state.room?.roomName])

  if (party.boot === 'loading') {
    return <LoadingScreen roomId={roomId} />
  }

  if (party.boot === 'unavailable') {
    return (
      <MessageScreen
        title="Can't open this room"
        message={party.bootError ?? 'The invite is invalid, or the host is offline.'}
        onHome={() => navigate('/', { replace: true })}
        onRetry={() => window.location.reload()}
      />
    )
  }

  if (party.boot === 'join') {
    return (
      <JoinPanel
        roomId={roomId}
        preview={party.preview}
        suggestedName={party.suggestedName}
        joining={party.joining}
        error={party.joinError}
        hasInvite={Boolean(inviteToken)}
        onJoin={(name) => {
          void party.join(name)
        }}
      />
    )
  }

  if (party.state.phase === 'closed') {
    return (
      <ClosedScreen
        message={party.state.closedMessage ?? 'The host closed this room.'}
        onHome={() => navigate('/', { replace: true })}
      />
    )
  }

  return (
    <RoomView
      roomId={roomId}
      party={party}
      onLeave={() => {
        void leaveAndGoHome(party, navigate)
      }}
    />
  )
}

async function leaveAndGoHome(
  party: UseWatchPartyResult,
  navigate: ReturnType<typeof useNavigate>,
): Promise<void> {
  await party.leave()
  navigate('/', { replace: true })
}

function LoadingScreen({ roomId }: { roomId: string }) {
  return (
    <div className="relative flex min-h-screen flex-col">
      <header className="mx-auto w-full max-w-6xl px-5 py-5 sm:px-8">
        <Brand compact />
      </header>
      <main className="grid flex-1 place-items-center px-5 pb-24">
        <Panel accent className="w-full max-w-md p-8 text-center">
          <span className="mx-auto grid size-12 place-items-center rounded-full border border-wave-cyan/25 bg-wave-cyan/[0.08] text-wave-cyan">
            <WaveIcon size={24} className="animate-pulse-soft" />
          </span>
          <h1 className="mt-4 font-display text-xl font-semibold text-white">Connecting to the host</h1>
          <p className="mt-2 text-sm text-wave-muted">
            Verifying room <span className="font-mono text-slate-300">{roomId}</span> and restoring your session.
          </p>
          <div className="mt-5 flex justify-center">
            <Spinner label="Opening the room…" />
          </div>
        </Panel>
      </main>
    </div>
  )
}

function MessageScreen({
  title,
  message,
  onHome,
  onRetry,
}: {
  title: string
  message: string
  onHome: () => void
  onRetry: () => void
}) {
  return (
    <div className="relative flex min-h-screen flex-col">
      <header className="mx-auto w-full max-w-6xl px-5 py-5 sm:px-8">
        <Brand compact />
      </header>
      <main className="grid flex-1 place-items-center px-5 pb-24">
        <Panel className="w-full max-w-lg p-7 sm:p-8">
          <span className="grid size-11 place-items-center rounded-xl border border-wave-amber/25 bg-wave-amber/[0.08] text-wave-amber">
            <AlertTriangleIcon size={21} />
          </span>
          <h1 className="mt-4 font-display text-2xl font-semibold text-white">{title}</h1>
          <p className="mt-2 text-sm leading-relaxed text-wave-muted">{message}</p>
          <div className="mt-6 flex flex-wrap gap-2">
            <Button onClick={onHome}>Back home</Button>
            <Button variant="secondary" onClick={onRetry}>
              Try again
            </Button>
          </div>
          <p className="mt-6 flex items-start gap-2 text-[11px] leading-relaxed text-wave-muted/70">
            <LockIcon size={13} className="mt-0.5 shrink-0" />
            If the host rotated the invite, the old link stops working even if the room is still live.
          </p>
        </Panel>
      </main>
    </div>
  )
}

function ClosedScreen({ message, onHome }: { message: string; onHome: () => void }) {
  return (
    <div className="relative flex min-h-screen flex-col">
      <header className="mx-auto w-full max-w-6xl px-5 py-5 sm:px-8">
        <Brand compact />
      </header>
      <main className="grid flex-1 place-items-center px-5 pb-24">
        <Panel className="w-full max-w-lg p-7 text-center sm:p-8">
          <span className="mx-auto grid size-12 place-items-center rounded-full border border-white/10 bg-white/[0.04] text-wave-muted">
            <LogOutIcon size={22} />
          </span>
          <h1 className="mt-4 font-display text-2xl font-semibold text-white">This room is closed</h1>
          <p className="mt-2 text-sm leading-relaxed text-wave-muted">{message}</p>
          <div className="mt-5">
            <Banner kind="info">Your session was cleared. Ask the host for a new invite to watch again.</Banner>
          </div>
          <Button className="mt-5" onClick={onHome}>
            Back to home
          </Button>
        </Panel>
      </main>
    </div>
  )
}
