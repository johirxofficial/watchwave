package com.watchwave.shared.room

import com.watchwave.shared.protocol.ErrorCodes
import com.watchwave.shared.protocol.MediaDescriptorDto
import com.watchwave.shared.protocol.ParticipantDto
import com.watchwave.shared.protocol.RoomStatus
import com.watchwave.shared.security.Clock
import com.watchwave.shared.security.SystemClock
import com.watchwave.shared.security.TokenService
import java.util.concurrent.ConcurrentHashMap

data class RoomDefaults(
    val ttlMs: Long = 6 * 60 * 60 * 1000L,
    val minTtlMs: Long = 5 * 60 * 1000L,
    val maxTtlMs: Long = 24 * 60 * 60 * 1000L,
    val maxParticipants: Int = 20,
    val minMaxParticipants: Int = 2,
    val maxMaxParticipants: Int = 50,
    val closedRoomRetentionMs: Long = 10 * 60 * 1000L,
)

data class CreateRoomParams(
    val roomName: String?,
    val hostName: String,
    val media: MediaDescriptorDto,
    val maxParticipants: Int? = null,
    val ttlMinutes: Int? = null,
)

data class RoomSnapshot(
    val roomId: String,
    val roomName: String,
    val hostName: String,
    val status: RoomStatus,
    val media: MediaDescriptorDto,
    val participantCount: Int,
    val maxParticipants: Int,
    val hostOnline: Boolean,
    val createdAt: Long,
    val expiresAt: Long,
    val canJoin: Boolean,
    val controlsLocked: Boolean,
    val participants: List<ParticipantDto>,
)

data class SessionInfo(
    val roomId: String,
    val participant: ParticipantDto,
)

sealed interface RoomResult<out T> {
    data class Success<T>(val value: T) : RoomResult<T>
    data class Failure(val code: String, val message: String) : RoomResult<Nothing>
}

data class CreateRoomResult(
    val snapshot: RoomSnapshot,
    val hostToken: String,
    val hostSessionToken: String,
    val hostParticipantId: String,
    val inviteToken: String,
)

data class JoinOutcome(
    val snapshot: RoomSnapshot,
    val participant: ParticipantDto,
    val sessionToken: String,
)

data class HostSecrets(
    val inviteToken: String,
)

data class RemovedParticipant(
    val roomId: String,
    val participantId: String,
)

/**
 * In-memory authoritative registry of rooms and participants.
 *
 * Runtime room secrets deliberately never touch disk: the app's Room database only
 * stores non-sensitive history. Restarting the host process therefore ends all
 * rooms, which is the safest possible outcome for a token-based system.
 */
class RoomManager(
    private val clock: Clock = SystemClock,
    private val tokens: TokenService = TokenService(),
    val defaults: RoomDefaults = RoomDefaults(),
) {
    private class ParticipantState(
        val id: String,
        var displayName: String,
        val isHost: Boolean,
        val sessionHash: String,
        val joinedAt: Long,
        var connected: Boolean,
        var controlsAllowed: Boolean,
        var removed: Boolean = false,
        var disconnectedAt: Long? = null,
    )

    private class RoomState(
        val roomId: String,
        val roomName: String,
        val hostName: String,
        val hostTokenHash: String,
        val hostParticipantId: String,
        var inviteToken: String,
        val createdAt: Long,
        var expiresAt: Long,
        var status: RoomStatus,
        var closedAt: Long?,
        var maxParticipants: Int,
        var canJoin: Boolean,
        var controlsLocked: Boolean,
        var media: MediaDescriptorDto,
        val participants: LinkedHashMap<String, ParticipantState>,
    )

    private val lock = Any()
    private val rooms = ConcurrentHashMap<String, RoomState>()
    private val reportedExpired = mutableSetOf<String>()

    fun createRoom(params: CreateRoomParams): RoomResult<CreateRoomResult> = synchronized(lock) {
        val hostName = sanitizeName(params.hostName, 32)
            ?: return RoomResult.Failure(ErrorCodes.INVALID_REQUEST, "Host name must be 1-32 characters.")
        val mediaTitle = sanitizeName(params.media.title, 120)
            ?: return RoomResult.Failure(ErrorCodes.INVALID_REQUEST, "Video title is invalid.")
        if (params.media.durationMs <= 0) {
            return RoomResult.Failure(ErrorCodes.INVALID_REQUEST, "Video duration must be positive.")
        }

        val maxParticipants = (params.maxParticipants ?: defaults.maxParticipants)
            .coerceIn(defaults.minMaxParticipants, defaults.maxMaxParticipants)
        val ttlMs = ((params.ttlMinutes?.toLong()?.times(60_000L)) ?: defaults.ttlMs)
            .coerceIn(defaults.minTtlMs, defaults.maxTtlMs)

        val now = clock.wallMs()
        val roomId = uniqueRoomId()
        val hostToken = tokens.newToken(32)
        val hostSessionToken = tokens.newToken(32)
        val hostParticipantId = tokens.newParticipantId()
        val inviteToken = tokens.newToken(32)

        val host = ParticipantState(
            id = hostParticipantId,
            displayName = hostName,
            isHost = true,
            sessionHash = tokens.sha256(hostSessionToken),
            joinedAt = now,
            connected = false,
            controlsAllowed = true,
            disconnectedAt = now,
        )
        val roomName = sanitizeName(params.roomName, 64) ?: "Watch Party"
        val state = RoomState(
            roomId = roomId,
            roomName = roomName,
            hostName = hostName,
            hostTokenHash = tokens.sha256(hostToken),
            hostParticipantId = hostParticipantId,
            inviteToken = inviteToken,
            createdAt = now,
            expiresAt = now + ttlMs,
            status = RoomStatus.ACTIVE,
            closedAt = null,
            maxParticipants = maxParticipants,
            canJoin = true,
            controlsLocked = false,
            media = params.media.copy(title = mediaTitle),
            participants = linkedMapOf(hostParticipantId to host),
        )
        rooms[roomId] = state

        RoomResult.Success(
            CreateRoomResult(
                snapshot = snapshotLocked(state),
                hostToken = hostToken,
                hostSessionToken = hostSessionToken,
                hostParticipantId = hostParticipantId,
                inviteToken = inviteToken,
            ),
        )
    }

    fun join(roomId: String, inviteToken: String?, displayName: String): RoomResult<JoinOutcome> =
        synchronized(lock) {
            val room = rooms[roomId]
                ?: return RoomResult.Failure(ErrorCodes.ROOM_NOT_FOUND, "Room does not exist.")
            refreshStatusLocked(room)
            when (room.status) {
                RoomStatus.CLOSED -> return RoomResult.Failure(ErrorCodes.ROOM_CLOSED, "This room has been closed.")
                RoomStatus.EXPIRED -> return RoomResult.Failure(ErrorCodes.ROOM_EXPIRED, "This room has expired.")
                RoomStatus.ACTIVE -> Unit
            }
            if (!room.canJoin) {
                return RoomResult.Failure(ErrorCodes.JOIN_DISABLED, "The host is not accepting new participants.")
            }
            if (inviteToken.isNullOrBlank() || !tokens.constantTimeEquals(inviteToken, room.inviteToken)) {
                return RoomResult.Failure(ErrorCodes.INVITE_INVALID, "Invitation link is invalid or has been revoked.")
            }
            if (activeParticipantCount(room) >= room.maxParticipants) {
                return RoomResult.Failure(ErrorCodes.ROOM_FULL, "This room is full.")
            }
            val name = sanitizeName(displayName, 32)
                ?: return RoomResult.Failure(ErrorCodes.INVALID_REQUEST, "Display name must be 1-32 characters.")

            val now = clock.wallMs()
            val sessionToken = tokens.newToken(32)
            val participant = ParticipantState(
                id = tokens.newParticipantId(),
                displayName = name,
                isHost = false,
                sessionHash = tokens.sha256(sessionToken),
                joinedAt = now,
                connected = true,
                controlsAllowed = !room.controlsLocked,
            )
            room.participants[participant.id] = participant

            RoomResult.Success(
                JoinOutcome(
                    snapshot = snapshotLocked(room),
                    participant = participant.toDto(),
                    sessionToken = sessionToken,
                ),
            )
        }

    /** Reconnect using an existing session token; identity and name are preserved. */
    fun reconnect(roomId: String, sessionToken: String): RoomResult<JoinOutcome> = synchronized(lock) {
        val room = rooms[roomId]
            ?: return RoomResult.Failure(ErrorCodes.ROOM_NOT_FOUND, "Room does not exist.")
        refreshStatusLocked(room)
        if (room.status != RoomStatus.ACTIVE) {
            return RoomResult.Failure(
                if (room.status == RoomStatus.CLOSED) ErrorCodes.ROOM_CLOSED else ErrorCodes.ROOM_EXPIRED,
                "This room is no longer active.",
            )
        }
        val hash = tokens.sha256(sessionToken)
        val participant = room.participants.values.firstOrNull {
            !it.removed && tokens.constantTimeEquals(it.sessionHash, hash)
        } ?: return RoomResult.Failure(ErrorCodes.UNAUTHORIZED, "Session is invalid or was revoked.")

        participant.connected = true
        participant.disconnectedAt = null
        RoomResult.Success(
            JoinOutcome(
                snapshot = snapshotLocked(room),
                participant = participant.toDto(),
                sessionToken = sessionToken,
            ),
        )
    }

    fun validateSession(roomId: String, sessionToken: String): RoomResult<SessionInfo> = synchronized(lock) {
        val room = rooms[roomId]
            ?: return RoomResult.Failure(ErrorCodes.ROOM_NOT_FOUND, "Room does not exist.")
        refreshStatusLocked(room)
        if (room.status != RoomStatus.ACTIVE) {
            return RoomResult.Failure(
                if (room.status == RoomStatus.CLOSED) ErrorCodes.ROOM_CLOSED else ErrorCodes.ROOM_EXPIRED,
                "This room is no longer active.",
            )
        }
        val hash = tokens.sha256(sessionToken)
        val participant = room.participants.values.firstOrNull {
            !it.removed && tokens.constantTimeEquals(it.sessionHash, hash)
        } ?: return RoomResult.Failure(ErrorCodes.UNAUTHORIZED, "Session is invalid or was revoked.")
        RoomResult.Success(SessionInfo(roomId, participant.toDto()))
    }

    fun validateHost(roomId: String, hostToken: String): Boolean = synchronized(lock) {
        val room = rooms[roomId] ?: return false
        refreshStatusLocked(room)
        room.status == RoomStatus.ACTIVE && tokens.constantTimeEquals(tokens.sha256(hostToken), room.hostTokenHash)
    }

    /** Checked by the landing screen so a room's metadata is not exposed without its link. */
    fun validateInvite(roomId: String, inviteToken: String): Boolean = synchronized(lock) {
        val room = rooms[roomId] ?: return false
        refreshStatusLocked(room)
        room.status == RoomStatus.ACTIVE && room.canJoin &&
            tokens.constantTimeEquals(inviteToken, room.inviteToken)
    }

    fun leave(roomId: String, sessionToken: String): RoomResult<ParticipantDto> = synchronized(lock) {
        val room = rooms[roomId]
            ?: return RoomResult.Failure(ErrorCodes.ROOM_NOT_FOUND, "Room does not exist.")
        val hash = tokens.sha256(sessionToken)
        val participant = room.participants.values.firstOrNull {
            !it.removed && tokens.constantTimeEquals(it.sessionHash, hash)
        } ?: return RoomResult.Failure(ErrorCodes.UNAUTHORIZED, "Session is invalid.")
        participant.connected = false
        participant.disconnectedAt = clock.wallMs()
        participant.removed = true
        room.participants.remove(participant.id)
        RoomResult.Success(participant.toDto().copy(connected = false))
    }

    fun disconnect(roomId: String, participantId: String) = synchronized(lock) {
        rooms[roomId]?.participants?.get(participantId)?.let {
            it.connected = false
            it.disconnectedAt = clock.wallMs()
        }
    }

    fun setParticipantConnected(roomId: String, participantId: String, connected: Boolean) {
        synchronized(lock) {
            rooms[roomId]?.participants?.get(participantId)?.let {
                it.connected = connected
                it.disconnectedAt = if (connected) null else clock.wallMs()
            }
        }
    }

    fun closeRoom(roomId: String, hostToken: String): RoomResult<Unit> = synchronized(lock) {
        val room = rooms[roomId]
            ?: return RoomResult.Failure(ErrorCodes.ROOM_NOT_FOUND, "Room does not exist.")
        if (!tokens.constantTimeEquals(tokens.sha256(hostToken), room.hostTokenHash)) {
            return RoomResult.Failure(ErrorCodes.UNAUTHORIZED, "Host token is invalid.")
        }
        room.status = RoomStatus.CLOSED
        room.canJoin = false
        room.closedAt = clock.wallMs()
        room.participants.values.forEach {
            it.connected = false
            it.disconnectedAt = room.closedAt
        }
        RoomResult.Success(Unit)
    }

    fun removeParticipant(roomId: String, hostToken: String, participantId: String): RoomResult<ParticipantDto> =
        synchronized(lock) {
            val room = rooms[roomId]
                ?: return RoomResult.Failure(ErrorCodes.ROOM_NOT_FOUND, "Room does not exist.")
            if (!tokens.constantTimeEquals(tokens.sha256(hostToken), room.hostTokenHash)) {
                return RoomResult.Failure(ErrorCodes.UNAUTHORIZED, "Host token is invalid.")
            }
            val participant = room.participants[participantId]
                ?: return RoomResult.Failure(ErrorCodes.INVALID_REQUEST, "Participant not found.")
            if (participant.isHost) {
                return RoomResult.Failure(ErrorCodes.FORBIDDEN, "The host cannot be removed.")
            }
            participant.removed = true
            participant.connected = false
            participant.disconnectedAt = clock.wallMs()
            room.participants.remove(participantId)
            RoomResult.Success(participant.toDto().copy(connected = false))
        }

    fun setJoinEnabled(roomId: String, hostToken: String, enabled: Boolean): RoomResult<Unit> = synchronized(lock) {
        val room = rooms[roomId]
            ?: return RoomResult.Failure(ErrorCodes.ROOM_NOT_FOUND, "Room does not exist.")
        if (!tokens.constantTimeEquals(tokens.sha256(hostToken), room.hostTokenHash)) {
            return RoomResult.Failure(ErrorCodes.UNAUTHORIZED, "Host token is invalid.")
        }
        room.canJoin = enabled
        RoomResult.Success(Unit)
    }

    fun setControlsLocked(roomId: String, hostToken: String, locked: Boolean): RoomResult<Unit> = synchronized(lock) {
        val room = rooms[roomId]
            ?: return RoomResult.Failure(ErrorCodes.ROOM_NOT_FOUND, "Room does not exist.")
        if (!tokens.constantTimeEquals(tokens.sha256(hostToken), room.hostTokenHash)) {
            return RoomResult.Failure(ErrorCodes.UNAUTHORIZED, "Host token is invalid.")
        }
        room.controlsLocked = locked
        room.participants.values.forEach { participant ->
            participant.controlsAllowed = participant.isHost || !locked
        }
        RoomResult.Success(Unit)
    }

    fun setMaxParticipants(roomId: String, hostToken: String, max: Int): RoomResult<Unit> = synchronized(lock) {
        val room = rooms[roomId]
            ?: return RoomResult.Failure(ErrorCodes.ROOM_NOT_FOUND, "Room does not exist.")
        if (!tokens.constantTimeEquals(tokens.sha256(hostToken), room.hostTokenHash)) {
            return RoomResult.Failure(ErrorCodes.UNAUTHORIZED, "Host token is invalid.")
        }
        val clamped = max.coerceIn(defaults.minMaxParticipants, defaults.maxMaxParticipants)
        if (clamped < activeParticipantCount(room)) {
            return RoomResult.Failure(ErrorCodes.INVALID_REQUEST, "Limit is below the current participant count.")
        }
        room.maxParticipants = clamped
        RoomResult.Success(Unit)
    }

    /** Rotates the invitation secret; every previously shared link stops working. */
    fun revokeInvites(roomId: String, hostToken: String): RoomResult<String> = synchronized(lock) {
        val room = rooms[roomId]
            ?: return RoomResult.Failure(ErrorCodes.ROOM_NOT_FOUND, "Room does not exist.")
        if (!tokens.constantTimeEquals(tokens.sha256(hostToken), room.hostTokenHash)) {
            return RoomResult.Failure(ErrorCodes.UNAUTHORIZED, "Host token is invalid.")
        }
        room.inviteToken = tokens.newToken(32)
        RoomResult.Success(room.inviteToken)
    }

    fun hostSecrets(roomId: String, hostToken: String): RoomResult<HostSecrets> = synchronized(lock) {
        val room = rooms[roomId]
            ?: return RoomResult.Failure(ErrorCodes.ROOM_NOT_FOUND, "Room does not exist.")
        if (!tokens.constantTimeEquals(tokens.sha256(hostToken), room.hostTokenHash)) {
            return RoomResult.Failure(ErrorCodes.UNAUTHORIZED, "Host token is invalid.")
        }
        RoomResult.Success(HostSecrets(room.inviteToken))
    }

    fun updateMedia(roomId: String, hostToken: String, media: MediaDescriptorDto): RoomResult<MediaDescriptorDto> =
        synchronized(lock) {
            val room = rooms[roomId]
                ?: return RoomResult.Failure(ErrorCodes.ROOM_NOT_FOUND, "Room does not exist.")
            if (!tokens.constantTimeEquals(tokens.sha256(hostToken), room.hostTokenHash)) {
                return RoomResult.Failure(ErrorCodes.UNAUTHORIZED, "Host token is invalid.")
            }
            val title = sanitizeName(media.title, 120)
                ?: return RoomResult.Failure(ErrorCodes.INVALID_REQUEST, "Video title is invalid.")
            if (media.durationMs <= 0) {
                return RoomResult.Failure(ErrorCodes.INVALID_REQUEST, "Video duration must be positive.")
            }
            room.media = media.copy(title = title)
            RoomResult.Success(room.media)
        }

    /** Removes viewers who have been offline longer than [graceMs]; the host is kept. */
    fun removeStaleParticipants(graceMs: Long): List<RemovedParticipant> = synchronized(lock) {
        val now = clock.wallMs()
        val removed = mutableListOf<RemovedParticipant>()
        rooms.values.forEach { room ->
            val iterator = room.participants.values.iterator()
            while (iterator.hasNext()) {
                val participant = iterator.next()
                val offlineSince = participant.disconnectedAt
                if (!participant.isHost && !participant.connected && offlineSince != null &&
                    now - offlineSince >= graceMs
                ) {
                    participant.removed = true
                    iterator.remove()
                    removed += RemovedParticipant(room.roomId, participant.id)
                }
            }
        }
        removed
    }

    fun snapshot(roomId: String): RoomSnapshot? = synchronized(lock) {
        val room = rooms[roomId] ?: return null
        refreshStatusLocked(room)
        snapshotLocked(room)
    }

    fun participants(roomId: String): List<ParticipantDto> = synchronized(lock) {
        val room = rooms[roomId] ?: return emptyList()
        room.participants.values.filterNot { it.removed }.map { it.toDto() }
    }

    fun participant(roomId: String, participantId: String): ParticipantDto? = synchronized(lock) {
        rooms[roomId]?.participants?.get(participantId)?.takeUnless { it.removed }?.toDto()
    }

    fun roomExists(roomId: String): Boolean = rooms.containsKey(roomId)

    fun activeRoomCount(): Int = synchronized(lock) {
        rooms.values.count { it.status == RoomStatus.ACTIVE }
    }

    /** Marks expired rooms, forgets long-closed rooms, returns ids that just expired. */
    fun purgeExpired(): List<String> = synchronized(lock) {
        val now = clock.wallMs()
        val expired = mutableListOf<String>()
        val iterator = rooms.entries.iterator()
        while (iterator.hasNext()) {
            val room = iterator.next().value
            refreshStatusLocked(room)
            if (room.status == RoomStatus.EXPIRED && reportedExpired.add(room.roomId)) {
                expired += room.roomId
            }
            val closedAt = room.closedAt
            if (room.status != RoomStatus.ACTIVE && closedAt != null &&
                now - closedAt > defaults.closedRoomRetentionMs
            ) {
                reportedExpired.remove(room.roomId)
                iterator.remove()
            }
        }
        expired
    }

    fun closeAll(): List<String> = synchronized(lock) {
        val now = clock.wallMs()
        rooms.values.forEach {
            if (it.status == RoomStatus.ACTIVE) {
                it.status = RoomStatus.CLOSED
                it.canJoin = false
                it.closedAt = now
                it.participants.values.forEach { participant ->
                    participant.connected = false
                    participant.disconnectedAt = now
                }
            }
        }
        rooms.keys.toList()
    }

    private fun refreshStatusLocked(room: RoomState) {
        if (room.status == RoomStatus.ACTIVE && clock.wallMs() >= room.expiresAt) {
            room.status = RoomStatus.EXPIRED
            room.canJoin = false
            room.closedAt = clock.wallMs()
            room.participants.values.forEach {
                it.connected = false
                it.disconnectedAt = room.closedAt
            }
        }
    }

    private fun snapshotLocked(room: RoomState): RoomSnapshot {
        val liveParticipants = room.participants.values.filterNot { it.removed }
        return RoomSnapshot(
            roomId = room.roomId,
            roomName = room.roomName,
            hostName = room.hostName,
            status = room.status,
            media = room.media,
            participantCount = liveParticipants.size,
            maxParticipants = room.maxParticipants,
            hostOnline = room.participants[room.hostParticipantId]?.connected == true,
            createdAt = room.createdAt,
            expiresAt = room.expiresAt,
            canJoin = room.canJoin && room.status == RoomStatus.ACTIVE,
            controlsLocked = room.controlsLocked,
            participants = liveParticipants.map { it.toDto() },
        )
    }

    private fun activeParticipantCount(room: RoomState): Int =
        room.participants.values.count { !it.removed }

    private fun ParticipantState.toDto(): ParticipantDto = ParticipantDto(
        id = id,
        displayName = displayName,
        isHost = isHost,
        connected = connected,
        joinedAt = joinedAt,
        controlsAllowed = controlsAllowed,
    )

    private fun uniqueRoomId(): String {
        while (true) {
            val candidate = tokens.newRoomId()
            if (!rooms.containsKey(candidate)) return candidate
        }
    }

    private fun sanitizeName(raw: String?, maxLength: Int): String? {
        if (raw == null) return null
        val cleaned = raw.filterNot { it.isISOControl() }
            .trim()
            .replace(Regex("\\s+"), " ")
        if (cleaned.isEmpty() || cleaned.length > maxLength) return null
        return cleaned
    }
}
