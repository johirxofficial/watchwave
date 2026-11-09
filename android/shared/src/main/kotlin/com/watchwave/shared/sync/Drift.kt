package com.watchwave.shared.sync

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sign

/**
 * Configurable drift-correction thresholds.
 *
 * All values can be overridden from Settings. The defaults are tuned for typical
 * mobile playback where rate changes below ~8% are barely perceptible.
 */
data class DriftConfig(
    val smallThresholdMs: Long = 300,
    val mediumThresholdMs: Long = 1_200,
    val largeThresholdMs: Long = 3_000,
    val maxRateAdjustment: Double = 0.06,
    val mediumRateAdjustment: Double = 0.12,
    val seekCooldownMs: Long = 5_000,
)

sealed interface DriftAction {
    data object None : DriftAction
    data class AdjustSpeed(val rate: Double) : DriftAction
    data class Seek(val positionMs: Long) : DriftAction
}

/**
 * Decides how a client should converge on the authoritative position.
 *
 * - tiny drift: leave it alone (or restore 1.0x if previously adjusted)
 * - small/medium drift: nudge playback rate temporarily
 * - large drift: seek, but never more often than [DriftConfig.seekCooldownMs]
 * - paused: everyone must sit at the same frame, so seek once drift exceeds the
 *   small threshold
 */
class DriftController(
    val config: DriftConfig = DriftConfig(),
    private var lastSeekMonoMs: Long = Long.MIN_VALUE / 2,
) {
    private var currentlyAdjusted = false

    fun evaluate(
        expectedPositionMs: Long,
        localPositionMs: Long,
        isPlaying: Boolean,
        nowMonoMs: Long,
    ): DriftAction {
        val drift = expectedPositionMs - localPositionMs
        val distance = abs(drift)

        if (!isPlaying) {
            currentlyAdjusted = false
            if (distance > config.smallThresholdMs) {
                lastSeekMonoMs = nowMonoMs
                return DriftAction.Seek(expectedPositionMs)
            }
            return DriftAction.None
        }

        if (distance <= config.smallThresholdMs) {
            if (currentlyAdjusted) {
                currentlyAdjusted = false
                return DriftAction.AdjustSpeed(1.0)
            }
            return DriftAction.None
        }

        if (distance <= config.mediumThresholdMs) {
            currentlyAdjusted = true
            val ratio = min(1.0, distance.toDouble() / config.mediumThresholdMs.toDouble())
            val magnitude = min(config.maxRateAdjustment, 0.02 + ratio * (config.maxRateAdjustment - 0.02))
            return adjust(1.0 + magnitude * drift.sign)
        }

        if (distance <= config.largeThresholdMs) {
            currentlyAdjusted = true
            return adjust(1.0 + config.mediumRateAdjustment * drift.sign)
        }

        if (nowMonoMs - lastSeekMonoMs >= config.seekCooldownMs) {
            lastSeekMonoMs = nowMonoMs
            currentlyAdjusted = false
            return DriftAction.Seek(expectedPositionMs)
        }
        currentlyAdjusted = true
        return adjust(1.0 + config.mediumRateAdjustment * drift.sign)
    }

    fun reset() {
        currentlyAdjusted = false
        lastSeekMonoMs = Long.MIN_VALUE / 2
    }

    private fun adjust(rate: Double): DriftAction =
        DriftAction.AdjustSpeed(((rate * 1000.0).roundToLong() / 1000.0).coerceIn(0.5, 2.0))
}

/**
 * Projects the authoritative position forward using the server timestamp anchor.
 * Clients call this between STATE_UPDATE frames so they do not need to poll.
 */
fun expectedPositionMs(
    anchorPositionMs: Long,
    anchorServerTimestampMs: Long,
    playbackSpeed: Float,
    isPlaying: Boolean,
    nowServerMs: Long,
    durationMs: Long,
): Long {
    if (!isPlaying) return anchorPositionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
    val elapsed = (nowServerMs - anchorServerTimestampMs).coerceAtLeast(0L)
    val projected = anchorPositionMs + (elapsed * playbackSpeed).roundToLong()
    return projected.coerceIn(0L, durationMs.coerceAtLeast(0L))
}

/** Estimates server clock minus client clock from a ping/pong or state timestamp. */
object ClockSync {
    fun estimateOffset(serverTimeMs: Long, clientReceivedMs: Long, roundTripMs: Long = 0L): Long =
        serverTimeMs + roundTripMs / 2 - clientReceivedMs

    /** Exponential smoothing; avoids visible jumps when a sample is noisy. */
    fun smooth(previousOffsetMs: Long?, sampleOffsetMs: Long, alpha: Double = 0.25): Long {
        if (previousOffsetMs == null) return sampleOffsetMs
        return (previousOffsetMs + (sampleOffsetMs - previousOffsetMs) * alpha).roundToLong()
    }
}
