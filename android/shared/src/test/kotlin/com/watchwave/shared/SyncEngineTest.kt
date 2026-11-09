package com.watchwave.shared

import com.watchwave.shared.protocol.CommandAction
import com.watchwave.shared.protocol.ErrorCodes
import com.watchwave.shared.protocol.MediaDescriptorDto
import com.watchwave.shared.protocol.ParticipantDto
import com.watchwave.shared.security.FakeClock
import com.watchwave.shared.sync.CommandOutcome
import com.watchwave.shared.sync.SyncConfig
import com.watchwave.shared.sync.SyncEngine
import com.watchwave.shared.sync.PlaybackCommand
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SyncEngineTest {

    private val clock = FakeClock()
    private val media = MediaDescriptorDto(
        mediaId = "m_1",
        title = "Test Movie",
        durationMs = 100_000L,
        mimeType = "video/mp4",
    )
    private val host = ParticipantDto(id = "p_host", displayName = "Host", isHost = true)
    private val viewer = ParticipantDto(id = "p_view", displayName = "Viewer", isHost = false)

    private fun engine(config: SyncConfig = SyncConfig()) = SyncEngine("room12345678", media, clock, config)

    private fun PlaybackCommand.next(id: String = "cmd-1") = copy(commandId = id)

    private fun cmd(
        action: CommandAction,
        id: String = "cmd-${System.nanoTime()}",
        positionMs: Long? = null,
        speed: Float? = null,
        expectedSequence: Long? = null,
    ) = PlaybackCommand(
        commandId = id,
        action = action,
        positionMs = positionMs,
        playbackSpeed = speed,
        expectedSequence = expectedSequence,
    )

    @Test
    fun `initial state is paused at zero`() {
        val state = engine().state()
        assertEquals(0L, state.positionMs)
        assertEquals(0L, state.sequence)
        assertFalse(state.isPlaying)
        assertEquals(1f, state.playbackSpeed)
    }

    @Test
    fun `play advances position with the monotonic clock`() {
        val sync = engine()
        val played = sync.handleCommand(host, cmd(CommandAction.PLAY, positionMs = 0L))
        assertIs<CommandOutcome.Accepted>(played)
        assertEquals(1L, (played as CommandOutcome.Accepted).state.sequence)
        assertTrue(played.state.isPlaying)

        clock.advance(5_000L)
        val state = sync.state()
        assertTrue(state.positionMs in 4_999L..5_001L, "position was ${state.positionMs}")

        clock.advance(5_000L)
        assertEquals(10_000L, sync.currentPositionMs(), "position after two play seconds")
    }

    @Test
    fun `pause freezes the projected position`() {
        val sync = engine()
        sync.handleCommand(host, cmd(CommandAction.PLAY, id = "play"))
        clock.advance(3_000L)
        val paused = sync.handleCommand(viewer, cmd(CommandAction.PAUSE, id = "pause"))
        val state = (paused as CommandOutcome.Accepted).state
        assertFalse(state.isPlaying)
        assertTrue(abs(state.positionMs - 3_000L) <= 1L, "paused position was ${state.positionMs}")

        clock.advance(30_000L)
        assertEquals(3_000L, sync.currentPositionMs())
    }

    @Test
    fun `seek clamps and validates positions and keeps play state`() {
        val sync = engine()
        sync.handleCommand(host, cmd(CommandAction.PLAY, id = "p"))
        clock.advance(1_000L)

        val seek = sync.handleCommand(viewer, cmd(CommandAction.SEEK, id = "s", positionMs = 20_000L))
        val state = (seek as CommandOutcome.Accepted).state
        assertTrue(state.isPlaying)
        assertEquals(20_000L, state.positionMs)
        assertEquals(2L, state.sequence)

        val beyond = sync.handleCommand(host, cmd(CommandAction.SEEK, id = "s2", positionMs = 100_001L))
        assertEquals(ErrorCodes.INVALID_POSITION, (beyond as CommandOutcome.Rejected).code)

        val negative = sync.handleCommand(host, cmd(CommandAction.SEEK, id = "s3", positionMs = -1L))
        assertEquals(ErrorCodes.INVALID_POSITION, (negative as CommandOutcome.Rejected).code)

        val missing = sync.handleCommand(host, cmd(CommandAction.SEEK, id = "s4"))
        assertEquals(ErrorCodes.INVALID_POSITION, (missing as CommandOutcome.Rejected).code)
    }

    @Test
    fun `speed change preserves the current position and validates range`() {
        val sync = engine()
        sync.handleCommand(host, cmd(CommandAction.PLAY, id = "p"))
        clock.advance(4_000L)

        val speed = sync.handleCommand(viewer, cmd(CommandAction.SET_SPEED, id = "sp", speed = 2.0f))
        val state = (speed as CommandOutcome.Accepted).state
        assertEquals(2.0f, state.playbackSpeed)
        assertTrue(abs(state.positionMs - 4_000L) <= 1L, "speed-change position was ${state.positionMs}")

        clock.advance(2_000L)
        assertTrue(abs(sync.currentPositionMs() - 8_000L) <= 1L)

        val tooFast = sync.handleCommand(host, cmd(CommandAction.SET_SPEED, id = "sp2", speed = 4f))
        assertEquals(ErrorCodes.INVALID_SPEED, (tooFast as CommandOutcome.Rejected).code)
        val tooSlow = sync.handleCommand(host, cmd(CommandAction.SET_SPEED, id = "sp3", speed = 0.1f))
        assertEquals(ErrorCodes.INVALID_SPEED, (tooSlow as CommandOutcome.Rejected).code)
    }

    @Test
    fun `duplicate command ids are ignored and do not change sequence`() {
        val sync = engine()
        val command = cmd(CommandAction.PLAY, id = "same-id")
        assertIs<CommandOutcome.Accepted>(sync.handleCommand(host, command))
        val duplicate = sync.handleCommand(host, command)
        assertIs<CommandOutcome.Duplicate>(duplicate)
        assertEquals(1L, sync.sequence())
    }

    @Test
    fun `commands claiming future sequences are rejected`() {
        val sync = engine()
        val future = sync.handleCommand(
            host,
            cmd(CommandAction.PLAY, id = "future", expectedSequence = 5L),
        )
        assertEquals(ErrorCodes.STALE_SEQUENCE, (future as CommandOutcome.Rejected).code)
        assertEquals(0L, sync.sequence())

        val behind = sync.handleCommand(
            host,
            cmd(CommandAction.PLAY, id = "behind", expectedSequence = 0L),
        )
        assertIs<CommandOutcome.Accepted>(behind)
    }

    @Test
    fun `locked controls block viewers but never the host`() {
        val sync = engine()
        val lockedViewer = viewer.copy(controlsAllowed = false)

        val rejected = sync.handleCommand(lockedViewer, cmd(CommandAction.PAUSE, id = "v1"))
        assertEquals(ErrorCodes.CONTROLS_LOCKED, (rejected as CommandOutcome.Rejected).code)

        val hostAllowed = sync.handleCommand(host, cmd(CommandAction.PLAY, id = "h1"))
        assertIs<CommandOutcome.Accepted>(hostAllowed)
    }

    @Test
    fun `command flooding is rate limited`() {
        val sync = engine(SyncConfig(commandsPerWindow = 3, rateLimitWindowMs = 2_000L))
        repeat(3) { index ->
            val outcome = sync.handleCommand(host, cmd(CommandAction.PAUSE, id = "ok-$index"))
            assertIs<CommandOutcome.Accepted>(outcome)
        }
        val limited = sync.handleCommand(host, cmd(CommandAction.PLAY, id = "flood"))
        val rejected = limited as CommandOutcome.Rejected
        assertEquals(ErrorCodes.RATE_LIMITED, rejected.code)
        assertTrue((rejected.retryAfterMs ?: 0L) > 0L)

        clock.advance(2_001L)
        assertIs<CommandOutcome.Accepted>(
            sync.handleCommand(host, cmd(CommandAction.PLAY, id = "after-window")),
        )
    }

    @Test
    fun `ended parks the room at the duration`() {
        val sync = engine()
        sync.handleCommand(host, cmd(CommandAction.PLAY, id = "play"))
        val ended = sync.handleCommand(viewer, cmd(CommandAction.ENDED, id = "end"))
        val state = (ended as CommandOutcome.Accepted).state
        assertFalse(state.isPlaying)
        assertEquals(media.durationMs, state.positionMs)
    }

    @Test
    fun `media change resets playback and bumps sequence`() {
        val sync = engine()
        sync.handleCommand(host, cmd(CommandAction.PLAY, id = "play"))
        clock.advance(10_000L)
        val next = media.copy(mediaId = "m_2", title = "Second", durationMs = 50_000L)
        val state = sync.changeMedia(next)
        assertEquals("m_2", state.mediaId)
        assertEquals(0L, state.positionMs)
        assertFalse(state.isPlaying)
        assertEquals(2L, state.sequence)
        assertEquals(next, sync.currentMedia())
    }

    @Test
    fun `disconnected non-host participants cannot command playback`() {
        val sync = engine()
        val offline = viewer.copy(connected = false)
        val outcome = sync.handleCommand(offline, cmd(CommandAction.PLAY, id = "offline"))
        assertEquals(ErrorCodes.UNAUTHORIZED, (outcome as CommandOutcome.Rejected).code)
    }

    @Test
    fun `blank command ids are rejected`() {
        val sync = engine()
        val outcome = sync.handleCommand(host, PlaybackCommand("", CommandAction.PLAY))
        assertEquals(ErrorCodes.INVALID_REQUEST, (outcome as CommandOutcome.Rejected).code)
    }
}
