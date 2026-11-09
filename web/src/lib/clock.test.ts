import { describe, expect, it } from 'vitest'
import { estimateOffset, ServerClock, smoothOffset } from './clock'

describe('clock synchronization', () => {
  it('accounts for half the round trip when estimating the host offset', () => {
    expect(estimateOffset(10_000, 9_900, 200)).toBe(200)
  })

  it('smooths samples instead of jumping', () => {
    expect(smoothOffset(100, 200, 0.25)).toBe(125)
    expect(smoothOffset(0, 400, 0.5)).toBe(200)
    expect(smoothOffset(50, -50, 1)).toBe(-50)
  })

  it('projects host time from the first noisy sample', () => {
    const clock = new ServerClock()
    clock.sample(5_000, 100, 4_900)
    expect(clock.offset).toBe(150)
    expect(clock.now(10_000)).toBe(10_150)
    expect(clock.sampleCount).toBe(1)
  })

  it('converges across repeated samples', () => {
    const clock = new ServerClock()
    clock.sample(1_000, 0, 1_000)
    clock.sample(2_000, 0, 1_900)
    expect(clock.offset).toBe(25)
    clock.reset()
    expect(clock.offset).toBe(0)
    expect(clock.sampleCount).toBe(0)
  })

  it('ignores invalid server timestamps', () => {
    const clock = new ServerClock()
    clock.sample(0)
    clock.sample(Number.NaN)
    expect(clock.sampleCount).toBe(0)
    expect(clock.now(123)).toBe(123)
  })
})
