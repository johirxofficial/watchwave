import { useState, type FormEvent } from 'react'
import { Brand } from '../components/Brand'
import { ArrowRightIcon, LockIcon, PlayIcon, ShieldIcon, UsersIcon, WaveIcon } from '../components/Icons'
import { Avatar, Banner, Button, Card, Pill, TextField } from '../components/ui'
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
    <div className="relative flex min-h-screen flex-col overflow-hidden">
      <div className="ambient pointer-events-none absolute inset-0">
        <div className="absolute -left-40 top-0 size-[34rem] rounded-full bg-wave-cyan/[0.08] blur-3xl" />
        <div className="absolute -right-32 bottom-0 size-[30rem] rounded-full bg-wave-teal/[0.05] blur-3xl" />
        <div className="absolute inset-0 bg-grid opacity-30" />
      </div>

      <header className="relative z-10 mx-auto flex w-full max-w-6xl items-center justify-between px-5 py-5 sm:px-8">
        <Brand compact showTagline />
        <Pill tone="cyan">
          <WaveIcon size={12} /> Invite only
        </Pill>
      </header>

      <main className="relative z-10 mx-auto grid w-full max-w-5xl flex-1 items-center gap-5 px-4 pb-16 pt-3 sm:px-6 lg:grid-cols-[1.06fr_0.94fr] lg:gap-8">
        <Card accent className="animate-fade-up overflow-hidden">
          <div className="relative border-b border-white/[0.06] p-6 sm:p-8">
            <div className="ambient absolute -right-16 -top-20 size-56 rounded-full bg-wave-cyan/10 blur-3xl" />
            <p className="section-eyebrow">You&apos;re invited</p>
            <h1 className="mt-2 text-balance font-display text-3xl font-semibold tracking-tight text-white sm:text-4xl">
              {room?.roomName || 'Watch party'}
            </h1>
            <p className="mt-1.5 text-[12px] text-wave-muted">
              Room <span className="font-mono text-slate-300">{roomId}</span>
            </p>

            <div className="mt-6 flex items-center gap-3 rounded-xl border border-white/[0.07] bg-black/20 p-3">
              <Avatar name={host?.displayName ?? 'Host'} host size="md" />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium text-white">{host?.displayName ?? 'Host'}</p>
                <p className="text-[11px] text-wave-muted">
                  {room?.hostOnline === false ? 'Host reconnecting…' : 'Hosting this room'}
                </p>
              </div>
              <Pill tone={room?.hostOnline === false ? 'amber' : 'green'}>
                {room?.hostOnline === false ? 'Away' : 'Online'}
              </Pill>
            </div>

            {room?.media ? (
              <div className="mt-3 flex items-center gap-3 rounded-xl border border-white/[0.07] bg-black/20 p-3">
                <span className="relative grid size-12 shrink-0 place-items-center overflow-hidden rounded-xl bg-[linear-gradient(135deg,rgb(var(--wv-accent)/.22),rgb(var(--wv-accent-2)/.08))] text-wave-cyan">
                  <PlayIcon size={18} />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium text-white">{room.media.title}</p>
                  <p className="text-[11px] text-wave-muted">
                    {formatClock(room.media.durationMs)} · original quality
                    {room.media.mimeType ? ` · ${room.media.mimeType.split('/').pop()}` : ''}
                  </p>
                </div>
              </div>
            ) : (
              <div className="mt-3 rounded-xl border border-dashed border-white/10 p-4 text-sm text-wave-muted">
                The host has not attached a video yet.
              </div>
            )}

            <div className="mt-4 flex flex-wrap items-center gap-x-4 gap-y-2 text-[11px] text-wave-muted">
              <span className="inline-flex items-center gap-1.5">
                <UsersIcon size={13} /> {room?.participantCount ?? 0} watching
                {room ? ` · up to ${room.maxParticipants}` : ''}
              </span>
              <span className="inline-flex items-center gap-1.5">
                <ShieldIcon size={13} /> Invite-scoped access
              </span>
            </div>
          </div>
          <div className="grid gap-3 bg-white/[0.02] p-4 text-[11px] text-wave-muted sm:grid-cols-3">
            <Step number="01" title="No account" body="Just a display name." />
            <Step number="02" title="Direct stream" body="From the host's phone." />
            <Step number="03" title="Stay in sync" body="Playback follows the host." />
          </div>
        </Card>

        <Card className="animate-fade-up p-6 sm:p-8">
          <h2 className="font-display text-2xl font-semibold tracking-tight text-white">Join the room</h2>
          <p className="mt-1.5 text-[13px] leading-relaxed text-wave-muted">
            Choose the name other viewers will see. Your session stays in this tab.
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

            <Button
              type="submit"
              size="lg"
              className="w-full"
              loading={joining}
              disabled={!hasInvite || name.trim().length === 0}
            >
              {joining ? 'Joining…' : 'Join watch party'}
              {!joining ? <ArrowRightIcon size={17} /> : null}
            </Button>
          </form>

          <p className="mt-5 flex items-start gap-2 border-t border-white/[0.06] pt-4 text-[11px] leading-relaxed text-wave-muted/70">
            <LockIcon size={13} className="mt-0.5 shrink-0" />
            The invite can be revoked by the host at any time. Nothing is uploaded to a WatchWave
            server — the video comes straight from the host device.
          </p>
        </Card>
      </main>
    </div>
  )
}

function Step({ number, title, body }: { number: string; title: string; body: string }) {
  return (
    <div className="flex items-start gap-2.5">
      <span className="grid size-6 shrink-0 place-items-center rounded-full border border-white/10 bg-white/[0.04] font-mono text-[10px] text-wave-cyan">
        {number}
      </span>
      <span>
        <span className="block text-[11px] font-medium text-slate-200">{title}</span>
        <span className="block text-[10px] text-wave-muted">{body}</span>
      </span>
    </div>
  )
}
