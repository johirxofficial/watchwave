package com.watchwave.shared.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Cryptographically secure identifiers and tokens.
 *
 * Invitation tokens and session tokens are 256-bit random values. The host only
 * stores SHA-256 hashes of session tokens, so memory dumps or logs never reveal a
 * usable credential. The 256-bit invitation secret is kept in memory for the
 * lifetime of the room because the host must be able to re-display the share link;
 * it is never written to disk or logs.
 */
class TokenService(private val random: SecureRandom = SecureRandom()) {

    fun newToken(bytes: Int = 32): String {
        val buffer = ByteArray(bytes)
        random.nextBytes(buffer)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer)
    }

    /** URL-safe room id, e.g. `k3f9xz2m7q1a`. Uses an unambiguous alphabet. */
    fun newRoomId(length: Int = 12): String = randomString(ROOM_ALPHABET, length)

    fun newParticipantId(length: Int = 14): String = "p_" + randomString(ROOM_ALPHABET, length)

    fun newMediaId(length: Int = 12): String = "m_" + randomString(ROOM_ALPHABET, length)

    fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

    private fun randomString(alphabet: String, length: Int): String {
        val out = StringBuilder(length)
        repeat(length) {
            out.append(alphabet[random.nextInt(alphabet.length)])
        }
        return out.toString()
    }

    companion object {
        private const val ROOM_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"
    }
}

data class MediaTokenClaims(
    val roomId: String,
    val participantId: String,
    val expiresAt: Long,
)

/**
 * Short-lived HMAC-SHA256 media tokens.
 *
 * HTML5 `<video>` elements cannot attach Authorization headers, so the media URL
 * carries a signed, expiring token in its query string. The signing secret lives
 * only in host process memory and is regenerated on every app start.
 */
class MediaTokenManager(
    private val secret: ByteArray,
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val clock: Clock = SystemClock,
) {
    private val tokenService = TokenService()

    fun issue(roomId: String, participantId: String): String {
        val expiresAt = clock.wallMs() + ttlMs
        val payload = "v1|$roomId|$participantId|$expiresAt"
        val encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.toByteArray(Charsets.UTF_8))
        return "$encoded.${sign(encoded)}"
    }

    fun expiresAt(): Long = clock.wallMs() + ttlMs

    fun validate(token: String, expectedRoomId: String): MediaTokenClaims? {
        val parts = token.split('.')
        if (parts.size != 2) return null
        val (encoded, signature) = parts
        if (!tokenService.constantTimeEquals(sign(encoded), signature)) return null
        val decoded = try {
            String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            return null
        }
        val fields = decoded.split('|')
        if (fields.size != 4 || fields[0] != "v1") return null
        val roomId = fields[1]
        val participantId = fields[2]
        val expiresAt = fields[3].toLongOrNull() ?: return null
        if (roomId != expectedRoomId) return null
        if (clock.wallMs() > expiresAt) return null
        return MediaTokenClaims(roomId, participantId, expiresAt)
    }

    private fun sign(encoded: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        val raw = mac.doFinal(encoded.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
    }

    companion object {
        /** One hour: long enough for steady playback, short enough to limit leakage. */
        const val DEFAULT_TTL_MS: Long = 60 * 60 * 1000L

        fun randomSecret(tokenService: TokenService = TokenService()): ByteArray {
            val secret = ByteArray(32)
            SecureRandom().nextBytes(secret)
            return secret
        }
    }
}
