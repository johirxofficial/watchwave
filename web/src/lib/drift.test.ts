import { describe, expect, it } from 'vitest'
import { DriftController, projectPosition } from './drift'

describe('position projection', () => {
  it('adds elapsed host time at the current speed', () => {
    expect(projectPosition(10_000, 1_000, 1, true, 3_000, 60_000)).toBe(12_000)
    expect(projectPosition(10_000, 1_000, 1.5, true, 3_000, 60_000)).toBe(13_000)
  })

  it('freezes paused playback and clamps to the media', () => {
    expect(projectPosition(10_000, 1_000, 1, false, 99_000, 60_000)).toBe(10_000)
    expect(projectPosition(59_000, 1_000, 1, true, 99_000, 60_000)).toBe(60_000)
    expect(projectPosition(-5, 0, 1, false, 0, 60_000)).toBe(0)
  })
})

describe('drift controller', () => {
  it('does nothing when playback is effectively in sync', () => {
    const controller = new DriftController()
    expect(controller.evaluate(10_000, 9_900, true, 1_000)).toEqual({ kind: 'none' })
  })

  it('nudges rate for small playing drift', () => {
    const controller = new DriftController()
    const action = controller.evaluate(10_900, 10_000, true, 1_000)
    expect(action.kind).toBe('speed')
    if (action.kind === 'speed') {
      expect(action.rate).toBeGreaterThan(1)
      expect(action.rate).toBeLessThanOrEqual(1.06)
    }
  })

  it('nudges in the opposite direction when the viewer is ahead', () => {
    const controller = new DriftController()
    const action = controller.evaluate(9_200, 10_000, true, 1_000)
    expect(action.kind).toBe('speed')
    if (action.kind === 'speed') expect(action.rate).toBeLessThan(1)
  })

  it('seeks immediately when paused drift is visible', () => {
    const controller = new DriftController()
    expect(controller.evaluate(10_500, 10_000, false, 1_000)).toEqual({
      kind: 'seek',
      positionMs: 10_500,
    })
  })

  it('seeks large drift but rate-limits repeated seeks', () => {
    const controller = new DriftController()
    expect(controller.evaluate(20_000, 10_000, true, 10_000)).toEqual({
      kind: 'seek',
      positionMs: 20_000,
    })
    const second = controller.evaluate(20_000, 10_000, true, 11_000)
    expect(second.kind).toBe('speed')
  })

  it('restores 1x after converging from an adjustment', () => {
    const controller = new DriftController()
    controller.evaluate(10_900, 10_000, true, 1_000)
    expect(controller.evaluate(10_050, 10_000, true, 2_000)).toEqual({ kind: 'speed', rate: 1 })
    expect(controller.evaluate(10_050, 10_000, true, 3_000)).toEqual({ kind: 'none' })
  })
})
