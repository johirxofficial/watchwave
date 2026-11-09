import { cn } from '../lib/cn'
import type { Participant } from '../lib/protocol'
import { Avatar } from './ui'
import { CrownIcon } from './Icons'

export function ParticipantList({
  participants,
  selfId,
  maxParticipants,
}: {
  participants: Participant[]
  selfId?: string
  maxParticipants: number
}) {
  const sorted = [...participants].sort((a, b) => {
    const hostDelta = Number(b.isHost ?? false) - Number(a.isHost ?? false)
    if (hostDelta !== 0) return hostDelta
    if (a.id === selfId) return -1
    if (b.id === selfId) return 1
    return (a.joinedAt ?? 0) - (b.joinedAt ?? 0)
  })

  return (
    <section aria-label="Room participants" className="flex flex-col">
      <div className="flex items-center justify-between px-4 pt-4">
        <div className="flex items-center gap-2">
          <h2 className="text-[11px] font-semibold uppercase tracking-[0.16em] text-wave-muted">
            In the room
          </h2>
          <span className="inline-flex size-4 items-center justify-center rounded-full bg-wave-green/15 text-[9px] font-semibold text-wave-green">
            {participants.filter((participant) => participant.connected !== false).length}
          </span>
        </div>
        <span className="font-mono text-[10px] text-wave-muted/70">
          {participants.length}/{Math.max(maxParticipants, participants.length)}
        </span>
      </div>

      <ul className="mt-2 max-h-64 space-y-0.5 overflow-y-auto px-2 pb-2">
        {sorted.map((participant) => {
          const isSelf = participant.id === selfId
          const online = participant.connected ?? true
          return (
            <li
              key={participant.id}
              className={cn(
                'flex items-center gap-2.5 rounded-lg px-2 py-2 text-sm',
                isSelf && 'bg-white/[0.045]',
              )}
            >
              <Avatar name={participant.displayName} host={participant.isHost} size="sm" />
              <span className="min-w-0 flex-1 truncate text-slate-200">
                {participant.displayName}
                {isSelf ? <span className="ml-1.5 text-[11px] text-wave-muted">you</span> : null}
              </span>
              {participant.isHost ? (
                <span title="Host" className="text-wave-amber">
                  <CrownIcon size={13} />
                </span>
              ) : null}
              <span
                title={online ? 'Connected' : 'Reconnecting'}
                className={cn(
                  'size-1.5 rounded-full',
                  online ? 'bg-wave-green' : 'bg-wave-muted/50',
                )}
              />
            </li>
          )
        })}
      </ul>

      {participants.length <= 1 ? (
        <p className="px-4 pb-3 text-[12px] leading-relaxed text-wave-muted/80">
          Waiting for more viewers. Share the invite link to bring someone in.
        </p>
      ) : null}
    </section>
  )
}
