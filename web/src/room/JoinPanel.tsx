import { useState, type FormEvent } from 'react'
import { Brand } from '../components/Brand'
import { LockIcon, PlayIcon, ShieldIcon, UsersIcon, WaveIcon } from '../components/Icons'
import { Avatar, Banner, Button, Panel, Pill, TextField } from '../components/ui'
import { formatClock } from '../lib/format'
import { participantWithDefaults, roomWithDefaults, type RoomSummary } from '../lib/protocol'

interface JoinPanelProps {
  roomId: string
  preview: RoomSummary | null
  suggestedName: string
  joining: boolean
  error: string | null
  hasInvite: boolean
  onJoin: (displayName: string) => void
}

export function JoinPanel({
  roomId,
  preview,
  suggestedName,
  joining,
  error,
  hasInvite,
  onJoin,
}: JoinPanelProps) {
  const [name, setName] = useState(suggestedName)
  const room = preview ? roomWithDefaults(preview) : null
  const host = room ? participantWithDefaults({ id: 'host', displayName: room.hostName, isHost: true }) : null

  const submit = (event: FormEvent) => {
    event.preventDefault()
    onJoin(name)
  }

  return (
    <div className="relative flex min-h-screen flex-col">
      <div className="pointer-events-none absolute inset-0 overflow-hidden">
        <div className="absolute -left-40 top-0 size-[34rem] rounded-full bg-wave-cyan/[0.07] blur-3xl" />
        <div className="absolute -right-32 bottom-0 size-[30rem] rounded-full bg-teal-400/[0.05] blur-3xl" />
      </div>

      <header className="relative z-10 mx-auto flex w-full max-w-6xl items-center justify-between px-5 py-5 sm:px-8">
        <Brand compact />
        <Pill tone="cyan">
          <WaveIcon size={12} /> Invite only
        </Pill>
      </header>

      <main className="relative z-10 mx-auto grid w-full max-w-6xl flex-1 items-center gap-6 px-5 pb-16 pt-4 sm:px-8 lg:grid-cols-[1.08fr_0.92fr] lg:gap-10">
        <Panel accent className="animate-fade-up p-6 sm:p-8">
          <p className="text-[11px] font-semibold uppercase tracking-[0.2em] text-wave-cyan">You're invited</p>
          <h1 className="mt-3 font-display text-4xl font-semibold tracking-tight text-white sm:text-5xl">
            {room?.roomName || 'Watch party'}
          </h1>
          <p className="mt-2 text-sm text-wave-muted">
            Room <span className="font-mono text-slate-300">{roomId}</span>
          </p>

          <div className="mt-7 flex items-center gap-3 rounded-xl border border-white/[0.07] bg-black/20 p-3">
            <Avatar name={host?.displayName ?? 'Host'} host size="md" />
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium text-white">{host?.displayName ?? 'Host'}</p>
              <p className="text-xs text-wave-muted">
                {room?.hostOnline === false ? 'Host reconnecting…' : 'Hosting this room'}
              </p>
            </div>
            <Pill tone={room?.hostOnline === false ? 'amber' : 'green'}>
              {room?.hostOnline === false ? 'Away' : 'Online'}
            </Pill>
          </div>

          {room?.media ? (
            <div className="mt-4 flex items-center gap-3 rounded-xl border border-white/[0.07] bg-black/20 p-3">
              <span className="grid size-11 shrink-0 place-items-center rounded-lg bg-wave-cyan/10 text-wave-cyan">
                <PlayIcon size={18} />
              </span>
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium text-white">{room.media.title}</p>
                <p className="text-xs text-wave-muted">
                  {formatClock(room.media.durationMs)} · original quality
                  {room.media.mimeType ? ` · ${room.media.mimeType.split('/').pop()}` : ''}
                </p>
              </div>
            </div>
          ) : (
            <div className="mt-4 rounded-xl border border-dashed border-white/10 p-4 text-sm text-wave-muted">
              The host has not attached a video yet.
            </div>
          )}

          <div className="mt-5 flex flex-wrap items-center gap-x-5 gap-y-2 text-[12px] text-wave-muted">
            <span className="inline-flex items-center gap-1.5">
              <UsersIcon size={14} /> {room?.participantCount ?? 0} watching
              {room ? ` · up to ${room.maxParticipants}` : ''}
            </span>
            <span className="inline-flex items-center gap-1.5">
              <ShieldIcon size={14} /> Invite-scoped access
            </span>
          </div>
        </Panel>

        <Panel className="animate-fade-up p-6 sm:p-8" >
          <h2 className="font-display text-2xl font-semibold tracking-tight text-white">Join the room</h2>
          <p className="mt-1.5 text-sm leading-relaxed text-wave-muted">
            Choose the name other viewers will see. No account or upload is needed.
          </p>

          <form className="mt-6 space-y-4" onSubmit={submit}>
            <TextField
              label="Display name"
              value={name}
              onChange={(event) => setName(event.target.value.slice(0, 32))}
              placeholder="e.g. Johir"
              maxLength={32}
              autoComplete="nickname"
              autoCapitalize="words"
              disabled={joining}
            />

            {error ? <Banner kind="error">{error}</Banner> : null}
            {!hasInvite ? (
              <Banner kind="warn">
                This page is missing the invite token. Open the full link the host shared.
              </Banner>
            ) : null}

            <Button type="submit" size="lg" className="w-full" loading={joining} disabled={!hasInvite || name.trim().length === 0}>
              {joining ? 'Joining…' : 'Join watch party'}
            </Button>
          </form>

          <ol className="mt-7 space-y-3 border-t border-white/[0.07] pt-6 text-[13px] text-wave-muted">
            <li className="flex gap-3">
              <Step number="1" />
              <span>Your browser connects to the host and receives a private session.</span>
            </li>
            <li className="flex gap-3">
              <Step number="2" />
              <span>The video streams directly from the host device in its original quality.</span>
            </li>
            <li className="flex gap-3">
              <Step number="3" />
              <span>Play, pause and seek stay in sync for everyone — unless the host locks controls.</span>
            </li>
          </ol>

          <p className="mt-6 flex items-start gap-2 text-[11px] leading-relaxed text-wave-muted/70">
            <LockIcon size={13} className="mt-0.5 shrink-0" />
            Your session is stored only in this tab. The invite link can be revoked by the host at any time.
          </p>
        </Panel>
      </main>
    </div>
  )
}

function Step({ number }: { number: string }) {
  return (
    <span className="grid size-6 shrink-0 place-items-center rounded-full border border-white/10 bg-white/[0.04] font-mono text-[11px] text-slate-300">
      {number}
    </span>
  )
}
