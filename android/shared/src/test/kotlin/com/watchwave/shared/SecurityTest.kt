package com.watchwave.shared

import com.watchwave.shared.protocol.MessageType
import com.watchwave.shared.protocol.SyncJson
import com.watchwave.shared.protocol.SyncMessage
import com.watchwave.shared.security.FakeClock
import com.watchwave.shared.security.MediaTokenManager
import com.watchwave.shared.security.MediaTokenManager.Companion.randomSecret
import com.watchwave.shared.security.ShareLinks
import com.watchwave.shared.security.TokenService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecurityTest {

    private val tokens = TokenService()

    @Test
    fun `tokens are long unique and url safe`() {
        val first = tokens.newToken()
        val second = tokens.newToken()
        assertNotEquals(first, second)
        assertTrue(first.length >= 40)
        assertTrue(first.matches(Regex("^[A-Za-z0-9_-]+$")))
        assertTrue(tokens.newRoomId().matches(Regex("^[a-z0-9]{12}$")))
        assertTrue(tokens.newParticipantId().startsWith("p_"))
    }

    @Test
    fun `sha256 is stable and constant time comparison works`() {
        assertEquals(tokens.sha256("hello"), tokens.sha256("hello"))
        assertNotEquals(tokens.sha256("hello"), tokens.sha256("Hello"))
        assertTrue(tokens.constantTimeEquals("abc", "abc"))
        assertFalse(tokens.constantTimeEquals("abc", "abd"))
    }

    @Test
    fun `media tokens validate room and expiry`() {
        val clock = FakeClock()
        val manager = MediaTokenManager(randomSecret(tokens), ttlMs = 60_000, clock = clock)
        val token = manager.issue("room12345678", "p_1")

        val claims = manager.validate(token, "room12345678")
        assertNotNull(claims)
        assertEquals("p_1", claims.participantId)

        assertNull(manager.validate(token, "otherroom123"))
        assertNull(manager.validate(token + "x", "room12345678"))

        clock.advance(60_001L)
        assertNull(manager.validate(token, "room12345678"))
    }

    @Test
    fun `tampered media payload fails signature verification`() {
        val manager = MediaTokenManager(randomSecret(tokens), ttlMs = 60_000)
        val token = manager.issue("room12345678", "p_1")
        val parts = token.split('.')
        val tampered = parts[0].dropLast(2) + "AA." + parts[1]
        assertNull(manager.validate(tampered, "room12345678"))
    }

    @Test
    fun `share links build and parse consistently`() {
        val link = ShareLinks.buildShareUrl("https://wave.example.com/", "abc123456789", "tok_en-123")
        assertEquals("https://wave.example.com/room/abc123456789?t=tok_en-123", link)

        val parsed = ShareLinks.parseInvite(link)
        assertNotNull(parsed)
        assertEquals("abc123456789", parsed.roomId)
        assertEquals("tok_en-123", parsed.inviteToken)

        assertEquals("abc123456789", ShareLinks.parseInvite("abc123456789")!!.roomId)
        assertNull(ShareLinks.parseInvite("abc123456789")!!.inviteToken)

        val deep = ShareLinks.buildDeepLink("abc123456789", "tok")
        assertEquals("watchwave://room/abc123456789?t=tok", deep)
        assertEquals("abc123456789", ShareLinks.parseInvite(deep)!!.roomId)

        val lan = ShareLinks.buildLanUrl("192.168.1.20", 8080, "abc123456789", "tok")
        assertEquals("http://192.168.1.20:8080/room/abc123456789?t=tok", lan)

        assertNull(ShareLinks.parseInvite("https://example.com/not-a-room"))
        assertNull(ShareLinks.parseInvite("   "))
        assertNull(ShareLinks.parseInvite("BAD ROOM"))
    }

    @Test
    fun `sync message json omits nulls and ignores unknown fields`() {
        val message = SyncMessage(
            type = MessageType.STATE_UPDATE,
            roomId = "abc123456789",
            sequence = 4,
            positionMs = 12_345,
            isPlaying = true,
            serverTimestamp = 1_000,
        )
        val raw = SyncJson.encode(message)
        assertFalse(raw.contains("commandId"))
        assertTrue(raw.contains("\"STATE_UPDATE\""))

        val decoded = SyncJson.decode(
            """{"type":"PAUSE","roomId":"abc123456789","futureField":42,"v":1}""",
        )
        assertEquals(MessageType.PAUSE, decoded.type)
        assertEquals("abc123456789", decoded.roomId)
        assertNull(decoded.positionMs)
    }
}
