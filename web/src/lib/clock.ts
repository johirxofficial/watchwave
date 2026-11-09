/**
 * Estimates the host's wall clock from state frames and ping/pong samples.
 *
 * `offset = serverTime + roundTrip / 2 - clientReceiveTime`, exponentially smoothed
 * so a single slow frame cannot make the player jump.
 */
export function estimateOffset(serverTimeMs: number, clientReceivedMs: number, roundTripMs = 0): number {
  return serverTimeMs + Math.floor(Math.max(0, roundTripMs) / 2) - clientReceivedMs
}

export function smoothOffset(previousMs: number, sampleMs: number, alpha = 0.25): number {
  const clamped = Math.min(1, Math.max(0, alpha))
  return Math.round(previousMs + (sampleMs - previousMs) * clamped)
}

export class ServerClock {
  private offsetMs = 0
  private sampled = false
  private samples = 0

  sample(serverTimeMs: number, roundTripMs = 0, receivedAtMs = Date.now()): void {
    if (!Number.isFinite(serverTimeMs) || serverTimeMs <= 0) return
    const raw = estimateOffset(serverTimeMs, receivedAtMs, roundTripMs)
    this.offsetMs = this.sampled ? smoothOffset(this.offsetMs, raw) : raw
    this.sampled = true
    this.samples += 1
  }

  /** Host wall-clock time, projected to now. */
  now(nowMs = Date.now()): number {
    return nowMs + this.offsetMs
  }

  get offset(): number {
    return this.offsetMs
  }

  get sampleCount(): number {
    return this.samples
  }

  reset(): void {
    this.offsetMs = 0
    this.sampled = false
    this.samples = 0
  }
}
