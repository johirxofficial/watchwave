/**
 * Client-side convergence toward the host's authoritative position.
 * Ported from the Kotlin `DriftController` so Android and browser behave alike.
 */

export interface DriftConfig {
  smallThresholdMs: number
  mediumThresholdMs: number
  largeThresholdMs: number
  maxRateAdjustment: number
  mediumRateAdjustment: number
  seekCooldownMs: number
}

export const DEFAULT_DRIFT_CONFIG: DriftConfig = {
  smallThresholdMs: 300,
  mediumThresholdMs: 1_200,
  largeThresholdMs: 3_000,
  maxRateAdjustment: 0.06,
  mediumRateAdjustment: 0.12,
  seekCooldownMs: 5_000,
}

export type DriftAction =
  | { kind: 'none' }
  | { kind: 'speed'; rate: number }
  | { kind: 'seek'; positionMs: number }

const NONE: DriftAction = { kind: 'none' }

export class DriftController {
  readonly config: DriftConfig
  private lastSeekAtMs: number
  private adjusted = false

  constructor(config: DriftConfig = DEFAULT_DRIFT_CONFIG, nowMs = -1e15) {
    this.config = config
    this.lastSeekAtMs = nowMs
  }

  evaluate(expectedPositionMs: number, localPositionMs: number, isPlaying: boolean, nowMs: number): DriftAction {
    const drift = expectedPositionMs - localPositionMs
    const distance = Math.abs(drift)

    if (!isPlaying) {
      this.adjusted = false
      if (distance > this.config.smallThresholdMs) {
        this.lastSeekAtMs = nowMs
        return { kind: 'seek', positionMs: Math.max(0, Math.round(expectedPositionMs)) }
      }
      return NONE
    }

    if (distance <= this.config.smallThresholdMs) {
      if (this.adjusted) {
        this.adjusted = false
        return { kind: 'speed', rate: 1 }
      }
      return NONE
    }

    if (distance <= this.config.mediumThresholdMs) {
      this.adjusted = true
      const ratio = Math.min(1, distance / this.config.mediumThresholdMs)
      const magnitude = Math.min(
        this.config.maxRateAdjustment,
        0.02 + ratio * (this.config.maxRateAdjustment - 0.02),
      )
      return { kind: 'speed', rate: roundRate(1 + magnitude * Math.sign(drift)) }
    }

    if (distance <= this.config.largeThresholdMs) {
      this.adjusted = true
      return { kind: 'speed', rate: roundRate(1 + this.config.mediumRateAdjustment * Math.sign(drift)) }
    }

    if (nowMs - this.lastSeekAtMs >= this.config.seekCooldownMs) {
      this.lastSeekAtMs = nowMs
      this.adjusted = false
      return { kind: 'seek', positionMs: Math.max(0, Math.round(expectedPositionMs)) }
    }

    this.adjusted = true
    return { kind: 'speed', rate: roundRate(1 + this.config.mediumRateAdjustment * Math.sign(drift)) }
  }

  reset(nowMs = -1e15): void {
    this.adjusted = false
    this.lastSeekAtMs = nowMs
  }
}

export function projectPosition(
  anchorPositionMs: number,
  anchorServerTimestampMs: number,
  playbackSpeed: number,
  isPlaying: boolean,
  nowServerMs: number,
  durationMs: number,
): number {
  const duration = Math.max(0, durationMs)
  if (!isPlaying) return clamp(anchorPositionMs, 0, duration)
  const elapsed = Math.max(0, nowServerMs - anchorServerTimestampMs)
  return clamp(anchorPositionMs + Math.round(elapsed * playbackSpeed), 0, duration)
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value))
}

function roundRate(rate: number): number {
  return Math.round(rate * 1000) / 1000
}
