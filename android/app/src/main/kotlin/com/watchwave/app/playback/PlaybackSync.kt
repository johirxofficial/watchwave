package com.watchwave.app.playback

import android.os.SystemClock
import com.watchwave.app.net.AuthoritativePlayback
import com.watchwave.app.net.ConnectionPhase
import com.watchwave.app.net.LocalPlaybackState
import com.watchwave.app.net.RoomClient
import com.watchwave.shared.sync.DriftAction
import com.watchwave.shared.sync.DriftConfig
import com.watchwave.shared.sync.DriftController
import com.watchwave.shared.sync.expectedPositionMs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max

/**
 * Bridges the authoritative room clock and the local ExoPlayer.
 *
 * Small drift is corrected by nudging playback speed, medium drift by a stronger nudge
 * and large drift by a rate-limited seek. Paused playback converges by seeking, because
 * every viewer must sit on the same frame.
 */
class PlaybackSync(
    private val client: RoomClient,
    private val playback: PlaybackController,
    private val scope: CoroutineScope,
    private val driftConfig: () -> DriftConfig,
    private val logger: (String) -> Unit = {},
) {
    private val drift = DriftController(driftConfig())
    private val _state = MutableStateFlow(LocalPlaybackState())
    val state: StateFlow<LocalPlaybackState> = _state.asStateFlow()

    private var appliedMediaId: String? = null
    private var lastAuthoritativeSpeed = 1f
    private val endedSent = AtomicBoolean(false)
    private var jobs: List<Job> = emptyList()

    init {
        playback.mediaTokenProvider = { client.mediaToken }
        // ExoPlayer must only be touched on its application thread (the main thread).
        // The shared app scope runs on Dispatchers.Default, so pin both coroutines to
        // Main instead of relying on whichever thread happened to start the engine.
        jobs = listOf(
            scope.launch(Dispatchers.Main.immediate) {
                client.playback.collect { authoritative ->
                    if (authoritative != null) applyAuthoritative(authoritative)
                }
            },
            scope.launch(Dispatchers.Main.immediate) {
                while (isActive) {
                    delay(TICK_MS)
                    tick()
                }
            },
        )
    }

    fun play() {
        if (!canControl()) return
        playback.play()
        client.play(playback.positionMs, client.playback.value?.playbackSpeed ?: 1f)
        publish()
    }

    fun pause() {
        if (!canControl()) return
        playback.pause()
        client.pause(playback.positionMs, client.playback.value?.playbackSpeed ?: 1f)
        publish()
    }

    fun toggle() {
        if (playback.playWhenReady) pause() else play()
    }

    fun seekTo(positionMs: Long) {
        if (!canControl()) return
        val duration = client.playback.value?.durationMs ?: playback.durationMs
        val clamped = positionMs.coerceIn(0L, max(duration, 0L))
        playback.seekTo(clamped)
        client.seek(clamped, client.playback.value?.playbackSpeed ?: 1f)
        publish()
    }

    fun setSpeed(speed: Float) {
        if (!canControl()) return
        val clamped = speed.coerceIn(PlaybackController.MIN_SPEED, PlaybackController.MAX_SPEED)
        playback.setSpeed(clamped)
        client.setSpeed(clamped, playback.positionMs)
        publish()
    }

    fun canControl(): Boolean =
        client.connection.value.phase == ConnectionPhase.LIVE && client.connection.value.canControl

    fun release() {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
        playback.mediaTokenProvider = null
    }

    private fun applyAuthoritative(authoritative: AuthoritativePlayback) {
        val mediaChanged = authoritative.mediaId != appliedMediaId
        if (mediaChanged || !playback.hasMedia) {
            val expected = expectedPositionMs(
                anchorPositionMs = authoritative.positionMs,
                anchorServerTimestampMs = authoritative.anchorServerMs,
                playbackSpeed = authoritative.playbackSpeed,
                isPlaying = authoritative.isPlaying,
                nowServerMs = client.serverNow(),
                durationMs = authoritative.durationMs,
            )
            playback.prepare(
                url = client.mediaUrl,
                positionMs = expected,
                playWhenReady = authoritative.isPlaying,
                playbackSpeed = authoritative.playbackSpeed,
                title = authoritative.mediaTitle,
            )
            appliedMediaId = authoritative.mediaId
            lastAuthoritativeSpeed = authoritative.playbackSpeed
            drift.reset()
            endedSent.set(false)
            publish()
            return
        }

        if (playback.playWhenReady != authoritative.isPlaying) {
            playback.setPlayWhenReady(authoritative.isPlaying)
        }
        if (abs(lastAuthoritativeSpeed - authoritative.playbackSpeed) > 0.001f) {
            playback.setSpeed(authoritative.playbackSpeed)
            lastAuthoritativeSpeed = authoritative.playbackSpeed
        }
        if (!authoritative.isPlaying) {
            val expected = expectedPositionMs(
                anchorPositionMs = authoritative.positionMs,
                anchorServerTimestampMs = authoritative.anchorServerMs,
                playbackSpeed = authoritative.playbackSpeed,
                isPlaying = false,
                nowServerMs = client.serverNow(),
                durationMs = authoritative.durationMs,
            )
            if (abs(expected - playback.positionMs) > drift.config.smallThresholdMs) {
                playback.seekTo(expected)
            }
        }
        publish()
    }

    private fun tick() {
        val authoritative = client.playback.value
        if (!playback.hasMedia) {
            publish()
            return
        }
        if (authoritative == null) {
            publish()
            return
        }

        val duration = max(authoritative.durationMs, playback.durationMs)
        val expected = expectedPositionMs(
            anchorPositionMs = authoritative.positionMs,
            anchorServerTimestampMs = authoritative.anchorServerMs,
            playbackSpeed = authoritative.playbackSpeed,
            isPlaying = authoritative.isPlaying,
            nowServerMs = client.serverNow(),
            durationMs = duration,
        )

        if (!playback.isBuffering) {
            val action = drift.evaluate(
                expectedPositionMs = expected,
                localPositionMs = playback.positionMs,
                isPlaying = authoritative.isPlaying,
                nowMonoMs = SystemClock.elapsedRealtime(),
            )
            when (action) {
                is DriftAction.Seek -> {
                    if (abs(expected - playback.positionMs) > 150L) {
                        playback.seekTo(expected)
                    }
                }

                is DriftAction.AdjustSpeed -> {
                    playback.setSpeed(authoritative.playbackSpeed * action.rate.toFloat())
                }

                DriftAction.None -> {
                    if (abs(playback.speed - authoritative.playbackSpeed) > 0.001f) {
                        playback.setSpeed(authoritative.playbackSpeed)
                    }
                }
            }
        }

        if (authoritative.isPlaying && playback.hasEnded && endedSent.compareAndSet(false, true)) {
            client.ended(duration)
        }

        publish()
    }

    private fun publish() {
        val authoritative = client.playback.value
        _state.value = LocalPlaybackState(
            positionMs = playback.positionMs,
            durationMs = if (authoritative != null && authoritative.durationMs > 0L) {
                authoritative.durationMs
            } else {
                playback.durationMs
            },
            bufferedMs = playback.bufferedMs,
            isPlaying = playback.playWhenReady,
            speed = playback.speed,
            mediaTitle = authoritative?.mediaTitle ?: _state.value.mediaTitle,
            hasMedia = playback.hasMedia,
            ended = playback.hasEnded,
        )
    }

    companion object {
        private const val TICK_MS = 500L
    }
}
