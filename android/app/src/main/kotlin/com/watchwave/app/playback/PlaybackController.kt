package com.watchwave.app.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

/**
 * Single application-scoped ExoPlayer. Media URLs are signed lazily at request time,
 * so a refreshed media token applies to the very next range request without reloading
 * the player or interrupting playback.
 */
class PlaybackController(context: Context) {

    private val appContext = context.applicationContext

    @Volatile
    var mediaTokenProvider: (() -> String?)? = null

    private val dataSourceFactory: DataSource.Factory =
        ResolvingDataSource.Factory(
            DefaultDataSource.Factory(appContext),
            ResolvingDataSource.Resolver { dataSpec ->
                val token = mediaTokenProvider?.invoke()
                val needsSignature = dataSpec.uri.path?.contains("/video") == true
                if (!needsSignature || token.isNullOrBlank()) {
                    dataSpec
                } else {
                    val signed = dataSpec.uri.buildUpon()
                        .clearQuery()
                        .appendQueryParameter("t", token)
                        .build()
                    dataSpec.buildUpon().setUri(signed).build()
                }
            },
        )

    val player: ExoPlayer = ExoPlayer.Builder(appContext)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .build()
        .apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            setHandleAudioBecomingNoisy(true)
        }

    private var loadedPath: String? = null

    val hasMedia: Boolean get() = player.mediaItemCount > 0
    val positionMs: Long get() = player.currentPosition.coerceAtLeast(0L)
    val durationMs: Long get() = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
    val bufferedMs: Long get() = player.bufferedPosition.coerceAtLeast(0L)
    val playWhenReady: Boolean get() = player.playWhenReady
    val isPlaying: Boolean get() = player.isPlaying
    val hasEnded: Boolean get() = player.playbackState == Player.STATE_ENDED
    val isBuffering: Boolean get() = player.playbackState == Player.STATE_BUFFERING
    val speed: Float get() = player.playbackParameters.speed.takeIf { it > 0f } ?: 1f

    /**
     * Loads [url] at [positionMs] only when the underlying media path changed. A token
     * rotation changes the query string but not the path, so playback continues.
     */
    fun prepare(
        url: String,
        positionMs: Long,
        playWhenReady: Boolean,
        playbackSpeed: Float,
        title: String?,
    ) {
        val path = runCatching { Uri.parse(url).path }.getOrNull() ?: url
        val item = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title ?: "WatchWave")
                    .build(),
            )
            .build()

        if (loadedPath == path && player.mediaItemCount > 0) {
            player.seekTo(positionMs.coerceAtLeast(0L))
            player.setPlaybackSpeed(playbackSpeed.coerceIn(MIN_SPEED, MAX_SPEED))
            player.playWhenReady = playWhenReady
            return
        }

        loadedPath = path
        player.setMediaItem(item)
        player.prepare()
        player.seekTo(positionMs.coerceAtLeast(0L))
        player.setPlaybackSpeed(playbackSpeed.coerceIn(MIN_SPEED, MAX_SPEED))
        player.playWhenReady = playWhenReady
    }

    fun play() {
        if (hasEnded) player.seekTo(0L)
        player.play()
    }

    fun pause() = player.pause()

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceIn(0L, player.duration.coerceAtLeast(positionMs)))
    }

    fun setPlayWhenReady(value: Boolean) {
        player.playWhenReady = value
    }

    fun setSpeed(value: Float) {
        val clamped = value.coerceIn(MIN_SPEED, MAX_SPEED)
        if (kotlin.math.abs(player.playbackParameters.speed - clamped) > 0.001f) {
            player.setPlaybackSpeed(clamped)
        }
    }

    fun stop() {
        player.pause()
        player.stop()
        player.clearMediaItems()
        loadedPath = null
    }

    fun release() {
        runCatching { player.release() }
    }

    companion object {
        const val MIN_SPEED = 0.25f
        const val MAX_SPEED = 3.0f
    }
}
