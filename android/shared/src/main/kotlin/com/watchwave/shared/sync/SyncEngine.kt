package com.watchwave.shared.sync

import com.watchwave.shared.protocol.CommandAction
import com.watchwave.shared.protocol.ErrorCodes
import com.watchwave.shared.protocol.MediaDescriptorDto
import com.watchwave.shared.protocol.ParticipantDto
import com.watchwave.shared.protocol.PlaybackStateDto
import com.watchwave.shared.security.Clock
import com.watchwave.shared.security.SlidingWindowRateLimiter
import com.watchwave.shared.security.SystemClock
import kotlin.math.roundToLong

data class SyncConfig(
    val minSpeed: Float = 0.5f,
    val maxSpeed: Float = 2.0f,
    val defaultSpeed: Float = 1.0f,
    val commandsPerWindow: Int = 12,
    val rateLimitWindowMs: Long = 2_000L,
    val dedupeCapacity: Int = 512,
    val maxFutureSequenceGap: Long = 0L,
)

/** A playback command as sent by any participant (host or viewer). */
data class PlaybackCommand(
    val commandId: String,
    val action: CommandAction,
    val positionMs: Long? = null,
    val playbackSpeed: Float? = null,
    val clientTimestamp: Long? = null,
    val expectedSequence: Long? = null,
)

sealed interface CommandOutcome {
    data class Accepted(val state: PlaybackStateDto, val action: CommandAction) : CommandOutcome
    data class Duplicate(val state: PlaybackStateDto) : CommandOutcome
    data class Rejected(
        val code: String,
        val message: String,
        val state: PlaybackStateDto? = null,
        val retryAfterMs: Long? = null,
    ) : CommandOutcome
}

/**
 * Server-side authoritative playback clock.
 *
 * Position is stored as an anchor (`positionAtAnchorMs` at `anchorMonoMs`) rather than
 * mutating it every tick. This keeps memory and CPU usage flat, uses a monotonic clock
 * immune to wall-clock changes, and lets snapshots be computed in O(1).
 *
 * Commands are applied with a last-accepted-command-wins policy. Duplicate command ids
 * are ignored, commands claiming a future sequence are rejected, and each accepted
 * command increments one monotonic sequence number that clients use to discard
 * out-of-order STATE_UPDATE frames.
 */
class SyncEngine(
    private val roomId: String,
    initialMedia: MediaDescriptorDto,
    private val clock: Clock = SystemClock,
    private val config: SyncConfig = SyncConfig(),
) {
    private val lock = Any()
    private val limiters = HashMap<String, SlidingWindowRateLimiter>()
    private val seenCommands = object : LinkedHashMap<String, Boolean>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean =
            size > config.dedupeCapacity
    }

    private var media: MediaDescriptorDto = initialMedia
    private var isPlaying: Boolean = false
    private var speed: Float = config.defaultSpeed
    private var positionAtAnchorMs: Long = 0L
    private var anchorMonoMs: Long = clock.monoMs()
    private var sequence: Long = 0L

    fun currentMedia(): MediaDescriptorDto = synchronized(lock) { media }

    fun currentPositionMs(nowMonoMs: Long = clock.monoMs()): Long = synchronized(lock) {
        positionLocked(nowMonoMs)
    }

    fun state(nowMonoMs: Long = clock.monoMs(), nowWallMs: Long = clock.wallMs()): PlaybackStateDto =
        synchronized(lock) { stateLocked(nowMonoMs, nowWallMs) }

    fun sequence(): Long = synchronized(lock) { sequence }

    /**
     * Applies a command after validating the participant, the room's control policy,
     * rate limits, the action payload and sequencing rules.
     */
    fun handleCommand(
        participant: ParticipantDto,
        command: PlaybackCommand,
        nowMonoMs: Long = clock.monoMs(),
        nowWallMs: Long = clock.wallMs(),
    ): CommandOutcome = synchronized(lock) {
        if (!participant.connected && !participant.isHost) {
            return CommandOutcome.Rejected(ErrorCodes.UNAUTHORIZED, "Participant is not connected.")
        }
        if (!participant.isHost && !participant.controlsAllowed) {
            return CommandOutcome.Rejected(
                ErrorCodes.CONTROLS_LOCKED,
                "The host has temporarily locked viewer controls.",
                stateLocked(nowMonoMs, nowWallMs),
            )
        }
        if (command.commandId.isBlank() || command.commandId.length > 128) {
            return CommandOutcome.Rejected(
                ErrorCodes.INVALID_REQUEST,
                "A command id of 1-128 characters is required.",
                stateLocked(nowMonoMs, nowWallMs),
            )
        }
        val dedupeKey = "${participant.id}:${command.commandId}"
        if (seenCommands.containsKey(dedupeKey)) {
            return CommandOutcome.Duplicate(stateLocked(nowMonoMs, nowWallMs))
        }
        val expected = command.expectedSequence
        if (expected != null && expected > sequence + config.maxFutureSequenceGap) {
            return CommandOutcome.Rejected(
                ErrorCodes.STALE_SEQUENCE,
                "Client sequence is ahead of the server; refresh room state.",
                stateLocked(nowMonoMs, nowWallMs),
            )
        }

        val limiter = limiters.getOrPut(participant.id) {
            SlidingWindowRateLimiter(config.commandsPerWindow, config.rateLimitWindowMs)
        }
        if (!limiter.tryAcquire(nowMonoMs)) {
            return CommandOutcome.Rejected(
                ErrorCodes.RATE_LIMITED,
                "Too many commands; slow down.",
                stateLocked(nowMonoMs, nowWallMs),
                retryAfterMs = limiter.retryAfterMs(nowMonoMs),
            )
        }

        when (command.action) {
            CommandAction.PLAY -> {
                val position = command.positionMs?.let { validatePosition(it, nowMonoMs, nowWallMs) }
                    ?: positionLocked(nowMonoMs)
                positionAtAnchorMs = position
                anchorMonoMs = nowMonoMs
                isPlaying = true
            }

            CommandAction.PAUSE -> {
                positionAtAnchorMs = positionLocked(nowMonoMs)
                anchorMonoMs = nowMonoMs
                isPlaying = false
            }

            CommandAction.SEEK -> {
                val requested = command.positionMs ?: return CommandOutcome.Rejected(
                    ErrorCodes.INVALID_POSITION,
                    "Seek requires positionMs.",
                    stateLocked(nowMonoMs, nowWallMs),
                )
                positionAtAnchorMs = validatePosition(requested, nowMonoMs, nowWallMs)
                    ?: return CommandOutcome.Rejected(
                        ErrorCodes.INVALID_POSITION,
                        "Seek position is outside the video.",
                        stateLocked(nowMonoMs, nowWallMs),
                    )
                anchorMonoMs = nowMonoMs
            }

            CommandAction.SET_SPEED -> {
                val requested = command.playbackSpeed
                    ?: return CommandOutcome.Rejected(
                        ErrorCodes.INVALID_SPEED,
                        "SET_SPEED requires playbackSpeed.",
                        stateLocked(nowMonoMs, nowWallMs),
                    )
                if (requested !in config.minSpeed..config.maxSpeed) {
                    return CommandOutcome.Rejected(
                        ErrorCodes.INVALID_SPEED,
                        "Playback speed must be between ${config.minSpeed}x and ${config.maxSpeed}x.",
                        stateLocked(nowMonoMs, nowWallMs),
                    )
                }
                positionAtAnchorMs = positionLocked(nowMonoMs)
                anchorMonoMs = nowMonoMs
                speed = requested
            }

            CommandAction.ENDED -> {
                positionAtAnchorMs = media.durationMs
                anchorMonoMs = nowMonoMs
                isPlaying = false
            }
        }

        seenCommands[dedupeKey] = true
        sequence += 1
        CommandOutcome.Accepted(stateLocked(nowMonoMs, nowWallMs), command.action)
    }

    /** Host-only media switch. Resets playback to the start of the new video. */
    fun changeMedia(
        newMedia: MediaDescriptorDto,
        nowMonoMs: Long = clock.monoMs(),
        nowWallMs: Long = clock.wallMs(),
    ): PlaybackStateDto = synchronized(lock) {
        media = newMedia
        isPlaying = false
        speed = config.defaultSpeed
        positionAtAnchorMs = 0L
        anchorMonoMs = nowMonoMs
        sequence += 1
        stateLocked(nowMonoMs, nowWallMs)
    }

    fun forgetParticipant(participantId: String) {
        synchronized(lock) { limiters.remove(participantId) }
    }

    fun resetForTests() = synchronized(lock) {
        limiters.clear()
        seenCommands.clear()
        sequence = 0
    }

    private fun validatePosition(positionMs: Long, nowMonoMs: Long, nowWallMs: Long): Long? {
        if (positionMs < 0 || positionMs > media.durationMs) return null
        return positionMs
    }

    private fun positionLocked(nowMonoMs: Long): Long {
        val raw = if (isPlaying) {
            positionAtAnchorMs + ((nowMonoMs - anchorMonoMs).coerceAtLeast(0L) * speed).roundToLong()
        } else {
            positionAtAnchorMs
        }
        return raw.coerceIn(0L, media.durationMs.coerceAtLeast(0L))
    }

    private fun stateLocked(nowMonoMs: Long, nowWallMs: Long): PlaybackStateDto = PlaybackStateDto(
        roomId = roomId,
        mediaId = media.mediaId,
        isPlaying = isPlaying,
        positionMs = positionLocked(nowMonoMs),
        playbackSpeed = speed,
        durationMs = media.durationMs,
        sequence = sequence,
        serverTimestamp = nowWallMs,
        serverTime = nowWallMs,
    )
}
