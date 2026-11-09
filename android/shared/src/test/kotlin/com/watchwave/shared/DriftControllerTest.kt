package com.watchwave.shared

import com.watchwave.shared.sync.ClockSync
import com.watchwave.shared.sync.DriftAction
import com.watchwave.shared.sync.DriftConfig
import com.watchwave.shared.sync.DriftController
import com.watchwave.shared.sync.expectedPositionMs
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DriftControllerTest {

    private val config = DriftConfig(
        smallThresholdMs = 300,
        mediumThresholdMs = 1_200,
        largeThresholdMs = 3_000,
        maxRateAdjustment = 0.06,
        mediumRateAdjustment = 0.12,
        seekCooldownMs = 5_000,
    )

    private fun controller() = DriftController(config)

    @Test
    fun `small drift is ignored`() {
        val action = controller().evaluate(expectedPositionMs = 10_000, localPositionMs = 9_800, isPlaying = true, nowMonoMs = 1_000)
        assertIs<DriftAction.None>(action)
    }

    @Test
    fun `behind client is sped up within the safe range`() {
        val action = controller().evaluate(10_000, 9_600, true, 1_000)
        val adjusted = assertIs<DriftAction.AdjustSpeed>(action)
        assertTrue(adjusted.rate > 1.0 && adjusted.rate <= 1.06, "rate was ${adjusted.rate}")
    }

    @Test
    fun `ahead client is slowed down`() {
        val action = controller().evaluate(10_000, 10_500, true, 1_000)
        val adjusted = assertIs<DriftAction.AdjustSpeed>(action)
        assertTrue(adjusted.rate < 1.0 && adjusted.rate >= 0.94, "rate was ${adjusted.rate}")
    }

    @Test
    fun `medium drift uses a stronger correction`() {
        val action = controller().evaluate(10_000, 8_100, true, 1_000)
        val adjusted = assertIs<DriftAction.AdjustSpeed>(action)
        assertTrue(adjusted.rate > 1.06, "rate was ${adjusted.rate}")
    }

    @Test
    fun `large drift seeks once and respects the cooldown`() {
        val drift = controller()
        val first = drift.evaluate(10_000, 2_000, true, 10_000)
        assertEquals(DriftAction.Seek(10_000), first)

        val duringCooldown = drift.evaluate(10_000, 2_000, true, 12_000)
        assertIs<DriftAction.AdjustSpeed>(duringCooldown)

        val afterCooldown = drift.evaluate(10_000, 2_000, true, 16_000)
        assertEquals(DriftAction.Seek(10_000), afterCooldown)
    }

    @Test
    fun `paused clients must match exactly`() {
        assertIs<DriftAction.None>(controller().evaluate(10_000, 9_850, false, 1_000))
        assertEquals(DriftAction.Seek(10_000), controller().evaluate(10_000, 9_000, false, 1_000))
    }

    @Test
    fun `speed is restored once drift is back inside the threshold`() {
        val drift = controller()
        assertIs<DriftAction.AdjustSpeed>(drift.evaluate(10_000, 9_500, true, 1_000))
        val restored = drift.evaluate(10_000, 9_950, true, 2_000)
        assertEquals(DriftAction.AdjustSpeed(1.0), restored)
    }

    @Test
    fun `expected position projection respects pause and duration`() {
        assertEquals(
            12_000L,
            expectedPositionMs(10_000, 1_000_000, 2f, true, 1_001_000, 100_000),
        )
        assertEquals(10_000L, expectedPositionMs(10_000, 1_000_000, 2f, false, 5_000_000, 100_000))
        assertEquals(100_000L, expectedPositionMs(99_000, 1_000_000, 2f, true, 5_000_000, 100_000))
    }

    @Test
    fun `clock sync estimates and smooths offsets`() {
        assertEquals(500L, ClockSync.estimateOffset(serverTimeMs = 10_500, clientReceivedMs = 10_000))
        assertEquals(400L, ClockSync.smooth(previousOffsetMs = 0L, sampleOffsetMs = 1_600L, alpha = 0.25))
        assertEquals(700L, ClockSync.smooth(previousOffsetMs = null, sampleOffsetMs = 700L))
    }
}
