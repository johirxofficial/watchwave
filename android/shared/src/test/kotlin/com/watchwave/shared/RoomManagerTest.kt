package com.watchwave.shared

import com.watchwave.shared.protocol.ErrorCodes
import com.watchwave.shared.protocol.MediaDescriptorDto
import com.watchwave.shared.protocol.RoomStatus
import com.watchwave.shared.room.CreateRoomParams
import com.watchwave.shared.room.RoomManager
import com.watchwave.shared.room.RoomResult
import com.watchwave.shared.security.FakeClock
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomManagerTest {

    private val clock = FakeClock()
    private val manager = RoomManager(clock = clock)
    private val media = MediaDescriptorDto(
        mediaId = "m_test1234567",
        title = "Big Buck Bunny",
        durationMs = 600_000L,
        mimeType = "video/mp4",
        sizeBytes = 1_000_000L,
    )

    private fun create(
        maxParticipants: Int? = 10,
        ttlMinutes: Int? = 60,
        roomName: String? = "Movie Night",
    ) = manager.createRoom(
        CreateRoomParams(
            roomName = roomName,
            hostName = "Alice",
            media = media,
            maxParticipants = maxParticipants,
            ttlMinutes = ttlMinutes,
        ),
    )

    private fun <T> RoomResult<T>.value(): T = (this as RoomResult.Success<T>).value

    @Test
    fun `create room returns secure secrets and sensible snapshot`() {
        val created = create().value()

        assertEquals(12, created.snapshot.roomId.length)
        assertTrue(created.hostToken.length >= 40)
        assertTrue(created.hostSessionToken.length >= 40)
        assertTrue(created.inviteToken.length >= 40)
        assertEquals(RoomStatus.ACTIVE, created.snapshot.status)
        assertEquals("Movie Night", created.snapshot.roomName)
        assertEquals("Alice", created.snapshot.hostName)
        assertEquals(1, created.snapshot.participantCount)
        assertTrue(created.snapshot.participants.single().isHost)
        assertTrue(manager.validateHost(created.snapshot.roomId, created.hostToken))
        assertFalse(manager.validateHost(created.snapshot.roomId, "wrong-token"))
        assertNotEquals(created.hostToken, created.hostSessionToken)
    }

    @Test
    fun `join requires the invitation token`() {
        val created = create().value()
        val roomId = created.snapshot.roomId

        val missing = manager.join(roomId, null, "Bob")
        val wrong = manager.join(roomId, "not-the-token", "Bob")
        assertEquals(ErrorCodes.INVITE_INVALID, (missing as RoomResult.Failure).code)
        assertEquals(ErrorCodes.INVITE_INVALID, (wrong as RoomResult.Failure).code)

        val ok = manager.join(roomId, created.inviteToken, "  Bob   the   Viewer ")
        val joined = (ok as RoomResult.Success).value
        assertEquals("Bob the Viewer", joined.participant.displayName)
        assertFalse(joined.participant.isHost)
        assertTrue(joined.participant.connected)
        assertEquals(2, joined.snapshot.participantCount)
        assertTrue(manager.validateSession(roomId, joined.sessionToken) is RoomResult.Success)
    }

    @Test
    fun `room enforces participant limit`() {
        val created = create(maxParticipants = 2).value()
        val roomId = created.snapshot.roomId

        assertTrue(manager.join(roomId, created.inviteToken, "Bob") is RoomResult.Success)
        val full = manager.join(roomId, created.inviteToken, "Carol")
        assertEquals(ErrorCodes.ROOM_FULL, (full as RoomResult.Failure).code)
    }

    @Test
    fun `host can disable joining and lock viewer controls`() {
        val created = create().value()
        val roomId = created.snapshot.roomId
        val joined = (manager.join(roomId, created.inviteToken, "Bob") as RoomResult.Success).value

        assertTrue(manager.setJoinEnabled(roomId, created.hostToken, false) is RoomResult.Success)
        val blocked = manager.join(roomId, created.inviteToken, "Carol")
        assertEquals(ErrorCodes.JOIN_DISABLED, (blocked as RoomResult.Failure).code)

        assertTrue(manager.setControlsLocked(roomId, created.hostToken, true) is RoomResult.Success)
        val viewer = manager.participant(roomId, joined.participant.id)
        val host = manager.participant(roomId, created.hostParticipantId)
        assertNotNull(viewer)
        assertNotNull(host)
        assertFalse(viewer.controlsAllowed)
        assertTrue(host.controlsAllowed)

        manager.setControlsLocked(roomId, created.hostToken, false)
        assertTrue(manager.participant(roomId, joined.participant.id)!!.controlsAllowed)
    }

    @Test
    fun `revoking invitations invalidates old links only`() {
        val created = create().value()
        val roomId = created.snapshot.roomId

        val revocation = manager.revokeInvites(roomId, created.hostToken)
        val newToken = (revocation as RoomResult.Success).value
        assertNotEquals(created.inviteToken, newToken)

        assertEquals(
            ErrorCodes.INVITE_INVALID,
            (manager.join(roomId, created.inviteToken, "Bob") as RoomResult.Failure).code,
        )
        assertTrue(manager.join(roomId, newToken, "Bob") is RoomResult.Success)
    }

    @Test
    fun `host can remove a participant and their session stops working`() {
        val created = create().value()
        val roomId = created.snapshot.roomId
        val joined = (manager.join(roomId, created.inviteToken, "Bob") as RoomResult.Success).value

        val removed = manager.removeParticipant(roomId, created.hostToken, joined.participant.id)
        assertTrue(removed is RoomResult.Success)
        assertEquals(1, manager.participants(roomId).size)
        val session = manager.validateSession(roomId, joined.sessionToken)
        assertEquals(ErrorCodes.UNAUTHORIZED, (session as RoomResult.Failure).code)

        val removeHost = manager.removeParticipant(roomId, created.hostToken, created.hostParticipantId)
        assertEquals(ErrorCodes.FORBIDDEN, (removeHost as RoomResult.Failure).code)
    }

    @Test
    fun `reconnect keeps identity and rejects invalid sessions`() {
        val created = create().value()
        val roomId = created.snapshot.roomId
        val joined = (manager.join(roomId, created.inviteToken, "Bob") as RoomResult.Success).value
        manager.disconnect(roomId, joined.participant.id)
        assertFalse(manager.participants(roomId).first { it.id == joined.participant.id }.connected)

        val reconnected = (manager.reconnect(roomId, joined.sessionToken) as RoomResult.Success).value
        assertEquals(joined.participant.id, reconnected.participant.id)
        assertEquals("Bob", reconnected.participant.displayName)
        assertTrue(reconnected.participant.connected)

        val bad = manager.reconnect(roomId, "bogus")
        assertEquals(ErrorCodes.UNAUTHORIZED, (bad as RoomResult.Failure).code)
    }

    @Test
    fun `closing a room blocks joins and host validation`() {
        val created = create().value()
        val roomId = created.snapshot.roomId

        assertTrue(manager.closeRoom(roomId, created.hostToken) is RoomResult.Success)
        assertEquals(RoomStatus.CLOSED, manager.snapshot(roomId)!!.status)
        assertEquals(
            ErrorCodes.ROOM_CLOSED,
            (manager.join(roomId, created.inviteToken, "Bob") as RoomResult.Failure).code,
        )
        assertFalse(manager.validateHost(roomId, created.hostToken))
        assertNull(manager.snapshot("doesnotexist"))
    }

    @Test
    fun `expired rooms reject joins and are reported once`() {
        val created = create(ttlMinutes = 5).value()
        val roomId = created.snapshot.roomId

        clock.advance(5 * 60 * 1000L + 1L)

        val expired = manager.join(roomId, created.inviteToken, "Bob")
        assertEquals(ErrorCodes.ROOM_EXPIRED, (expired as RoomResult.Failure).code)
        assertEquals(RoomStatus.EXPIRED, manager.snapshot(roomId)!!.status)
        assertEquals(listOf(roomId), manager.purgeExpired())
        assertTrue(manager.purgeExpired().isEmpty())
    }

    @Test
    fun `invalid display names and durations are rejected`() {
        val blankName = manager.createRoom(
            CreateRoomParams(roomName = "x", hostName = "   ", media = media),
        )
        assertEquals(ErrorCodes.INVALID_REQUEST, (blankName as RoomResult.Failure).code)

        val badDuration = manager.createRoom(
            CreateRoomParams(roomName = "x", hostName = "Alice", media = media.copy(durationMs = 0)),
        )
        assertEquals(ErrorCodes.INVALID_REQUEST, (badDuration as RoomResult.Failure).code)

        val created = create().value()
        val longName = manager.join(created.snapshot.roomId, created.inviteToken, "x".repeat(33))
        assertEquals(ErrorCodes.INVALID_REQUEST, (longName as RoomResult.Failure).code)
    }
}
