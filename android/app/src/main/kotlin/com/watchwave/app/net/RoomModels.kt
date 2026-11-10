package com.watchwave.app.net

import com.watchwave.shared.protocol.ParticipantDto

enum class ConnectionPhase {
    IDLE,
    CONNECTING,
    LIVE,
    RECONNECTING,
    ENDED,
    FAILED,
}

data class RoomConnectionState(
    val phase: ConnectionPhase = ConnectionPhase.CONNECTING,
    val self: ParticipantDto? = null,
    val isHost: Boolean = false,
    val participants: List<ParticipantDto> = emptyList(),
    val canControl: Boolean = true,
    val controlsLocked: Boolean = false,
    val hostOffline: Boolean = false,
    val message: String? = null,
    val errorCode: String? = null,
)

/** Authoritative playback anchored to the host's wall clock. */
data class AuthoritativePlayback(
    val mediaId: String?,
    val mediaTitle: String?,
    val isPlaying: Boolean,
    val positionMs: Long,
    val playbackSpeed: Float,
    val durationMs: Long,
    val sequence: Long,
    val anchorServerMs: Long,
)

/** Local player values rendered by the UI; refreshed several times per second. */
data class LocalPlaybackState(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val isPlaying: Boolean = false,
    val speed: Float = 1f,
    val mediaTitle: String? = null,
    val hasMedia: Boolean = false,
    val ended: Boolean = false,
    /** True when the last authoritative snapshot is too old to trust. */
    val syncStale: Boolean = false,
)

data class RoomLink(
    val baseUrl: String,
    val roomId: String,
    val inviteToken: String,
    val original: String,
)

class RoomLinkException(message: String) : IllegalArgumentException(message)

class RoomApiException(
    val code: String,
    override val message: String,
    val status: Int,
) : Exception(message)
