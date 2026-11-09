import { cn } from '../lib/cn'
import type { ConnectionPhase } from '../services/sync-client'
import { LiveDot, Pill } from './ui'

export function ConnectionBadge({
  phase,
  detail,
  hostOffline = false,
}: {
  phase: ConnectionPhase
  detail?: string | null
  hostOffline?: boolean
}) {
  if (hostOffline) {
    return (
      <Pill tone="amber" className="whitespace-nowrap">
        <LiveDot tone="amber" /> Host offline
      </Pill>
    )
  }
  if (phase === 'live') {
    return (
      <Pill tone="green" className="whitespace-nowrap">
        <LiveDot /> Synced
      </Pill>
    )
  }
  if (phase === 'failed' || phase === 'closed') {
    return (
      <Pill tone="red" className="whitespace-nowrap" title={detail ?? undefined}>
        Disconnected
      </Pill>
    )
  }
  return (
    <Pill tone="amber" className={cn('whitespace-nowrap')} title={detail ?? undefined}>
      <LiveDot tone="amber" />
      {phase === 'reconnecting' ? 'Reconnecting' : 'Connecting'}
    </Pill>
  )
}
