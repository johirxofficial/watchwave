package com.watchwave.shared.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Wire protocol version. Bumped only for breaking changes. */
const val PROTOCOL_VERSION: Int = 1

/** Canonical HTTP API prefix. */
const val API_PREFIX: String = "/api/v1"

/**
 * Every WebSocket frame exchanged between clients and the host is a [SyncMessage].
 *
 * A single envelope type is used instead of polymorphic sealed classes so that the
 * Kotlin host, the Kotlin viewer and the TypeScript browser client can share one
 * simple, forward-compatible schema. Unknown fields are ignored by all peers.
 */
@Serializable
enum class MessageType {
    JOIN,
    JOIN_ACK,
    LEAVE,
    PLAY,
    PAUSE,
    SEEK,
    SET_SPEED,
    ENDED,
    STATE_UPDATE,
    STATE_REQUEST,
    PARTICIPANT_JOINED,
    PARTICIPANT_LEFT,
    PARTICIPANTS,
    ROOM_CLOSED,
    HOST_OFFLINE,
    SYNC_PING,
    SYNC_PONG,
    ERROR,
    MEDIA_CHANGED,
    CONTROL_POLICY,
    ROOM_INFO,
    CHAT_SEND,
    CHAT_MESSAGE,
    CHAT_HISTORY,
}

/** Server-enforced chat limits, shared by the Android and browser clients. */
const val CHAT_MAX_LENGTH: Int = 500
const val CHAT_HISTORY_LIMIT: Int = 100

@Serializable
enum class RoomStatus { ACTIVE, CLOSED, EXPIRED }

@Serializable
enum class CommandAction { PLAY, PAUSE, SEEK, SET_SPEED, ENDED }

@Serializable
data class ParticipantDto(
    val id: String,
    val displayName: String,
    val isHost: Boolean = false,
    val connected: Boolean = true,
    val joinedAt: Long = 0L,
    val controlsAllowed: Boolean = true,
)

@Serializable
data class ChatMessageDto(
    val messageId: String,
    val participantId: String,
    val displayName: String,
    val text: String,
    val sentAt: Long,
    val isHost: Boolean = false,
)

@Serializable
data class MediaDescriptorDto(
    val mediaId: String,
    val title: String,
    val durationMs: Long,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
)

@Serializable
data class PlaybackStateDto(
    val roomId: String,
    val mediaId: String,
    val isPlaying: Boolean,
    val positionMs: Long,
    val playbackSpeed: Float,
    val durationMs: Long,
    val sequence: Long,
    val serverTimestamp: Long,
    val serverTime: Long = serverTimestamp,
)

@Serializable
data class RoomSummaryDto(
    val roomId: String,
    val roomName: String,
    val hostName: String,
    val status: RoomStatus,
    val media: MediaDescriptorDto? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val playbackSpeed: Float = 1f,
    val participantCount: Int = 0,
    val maxParticipants: Int = 20,
    val hostOnline: Boolean = false,
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L,
    val canJoin: Boolean = true,
    val controlsLocked: Boolean = false,
    val shareUrl: String? = null,
)

@Serializable
data class UploadStatsDto(
    val bytesSent: Long = 0L,
    val activeStreams: Int = 0,
    val totalStreamsStarted: Long = 0L,
    val approximateBitrateBps: Long = 0L,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class HealthDto(
    @EncodeDefault val status: String = "ok",
    @EncodeDefault val app: String = "WatchWave",
    @EncodeDefault val version: String = "1.0.0",
    @EncodeDefault val protocolVersion: Int = PROTOCOL_VERSION,
    val serverTime: Long = 0L,
)

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String,
    val retryAfterMs: Long? = null,
)

// ---------------------------------------------------------------------------
// REST request / response bodies
// ---------------------------------------------------------------------------

@Serializable
data class CreateRoomRequest(
    val roomName: String? = null,
    val hostName: String = "Host",
    val mediaId: String,
    val videoTitle: String,
    val durationMs: Long,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val maxParticipants: Int? = null,
    val ttlMinutes: Int? = null,
)

@Serializable
data class CreateRoomResponse(
    val room: RoomSummaryDto,
    /** Secret used only by the host app for host-only REST actions. Never shared. */
    val hostToken: String,
    /** Session token used by the host app to open the sync WebSocket. Never shared. */
    val hostSessionToken: String,
    val hostParticipantId: String,
    /** Invitation token embedded in the share link. Rotating it revokes old links. */
    val inviteToken: String,
    val sharePath: String,
    val mediaUrlPath: String,
    val wsPath: String,
)

@Serializable
data class JoinRequest(
    val displayName: String,
    val inviteToken: String? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class JoinResponse(
    val room: RoomSummaryDto,
    val participant: ParticipantDto,
    val sessionToken: String,
    val mediaToken: String,
    val mediaUrlPath: String,
    val wsPath: String,
    @EncodeDefault val canControl: Boolean = true,
    val shareUrl: String? = null,
)

@Serializable
data class ReconnectRequest(
    val sessionToken: String,
)

@Serializable
data class LeaveRequest(
    val sessionToken: String,
)

@Serializable
data class ActionResponse(
    val ok: Boolean = true,
    val message: String? = null,
)

@Serializable
data class MediaTokenResponse(
    val mediaToken: String,
    val expiresAt: Long,
)

@Serializable
data class HostParticipantRequest(
    val hostToken: String,
    val participantId: String? = null,
)

@Serializable
data class HostToggleRequest(
    val hostToken: String,
    val enabled: Boolean,
    val maxParticipants: Int? = null,
)

@Serializable
data class HostRevokeResponse(
    val ok: Boolean = true,
    val inviteToken: String,
    val sharePath: String,
)

@Serializable
data class TunnelStatusDto(
    val state: String,
    val provider: String,
    val publicUrl: String? = null,
    val verified: Boolean = false,
    val message: String? = null,
    val updatedAt: Long = 0L,
)

// ---------------------------------------------------------------------------
// WebSocket envelope
// ---------------------------------------------------------------------------

/**
 * One schema for every WebSocket event. Fields are nullable because a given event
 * only uses a subset. `encodeDefaults = false` keeps frames small on mobile networks.
 */
@Serializable
data class SyncMessage(
    val type: MessageType,
    val v: Int = PROTOCOL_VERSION,
    val roomId: String? = null,
    val participantId: String? = null,
    val displayName: String? = null,
    val isHost: Boolean? = null,
    val commandId: String? = null,
    val sequence: Long? = null,
    val positionMs: Long? = null,
    val playbackSpeed: Float? = null,
    val durationMs: Long? = null,
    val isPlaying: Boolean? = null,
    val serverTimestamp: Long? = null,
    val serverTime: Long? = null,
    val mediaId: String? = null,
    val mediaTitle: String? = null,
    val participants: List<ParticipantDto>? = null,
    val canControl: Boolean? = null,
    val controlsLocked: Boolean? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val clientTimestamp: Long? = null,
    val expectedSequence: Long? = null,
    val retryAfterMs: Long? = null,
    val requestId: String? = null,
    val text: String? = null,
    val chatMessages: List<ChatMessageDto>? = null,
)

object SyncJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
        isLenient = false
    }

    fun encode(message: SyncMessage): String = json.encodeToString(SyncMessage.serializer(), message)

    fun decode(raw: String): SyncMessage = json.decodeFromString(SyncMessage.serializer(), raw)
}

/** Stable machine-readable error codes shared by REST and WebSocket layers. */
object ErrorCodes {
    const val INVALID_REQUEST = "INVALID_REQUEST"
    const val INVALID_TOKEN = "INVALID_TOKEN"
    const val INVITE_INVALID = "INVITE_INVALID"
    const val UNAUTHORIZED = "UNAUTHORIZED"
    const val FORBIDDEN = "FORBIDDEN"
    const val ROOM_NOT_FOUND = "ROOM_NOT_FOUND"
    const val ROOM_CLOSED = "ROOM_CLOSED"
    const val ROOM_EXPIRED = "ROOM_EXPIRED"
    const val ROOM_FULL = "ROOM_FULL"
    const val JOIN_DISABLED = "JOIN_DISABLED"
    const val CONTROLS_LOCKED = "CONTROLS_LOCKED"
    const val RATE_LIMITED = "RATE_LIMITED"
    const val STALE_SEQUENCE = "STALE_SEQUENCE"
    const val DUPLICATE_COMMAND = "DUPLICATE_COMMAND"
    const val INVALID_ACTION = "INVALID_ACTION"
    const val INVALID_POSITION = "INVALID_POSITION"
    const val INVALID_SPEED = "INVALID_SPEED"
    const val MEDIA_UNAVAILABLE = "MEDIA_UNAVAILABLE"
    const val MEDIA_PERMISSION_REVOKED = "MEDIA_PERMISSION_REVOKED"
    const val RANGE_NOT_SATISFIABLE = "RANGE_NOT_SATISFIABLE"
    const val TUNNEL_NOT_READY = "TUNNEL_NOT_READY"
    const val HOST_OFFLINE = "HOST_OFFLINE"
    const val CHAT_EMPTY = "CHAT_EMPTY"
    const val CHAT_TOO_LONG = "CHAT_TOO_LONG"
    const val INTERNAL_ERROR = "INTERNAL_ERROR"
}
