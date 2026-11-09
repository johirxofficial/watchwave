package com.watchwave.app.net

import com.watchwave.app.BuildConfig
import com.watchwave.shared.security.ShareLinks
import java.net.URI

/**
 * Turns a shared room link into the base origin plus room credentials.
 *
 * HTTPS links carry everything needed. The `watchwave://` deep link and bare room
 * codes only work when the build configured `watchwave.baseDomain`; the app never
 * guesses a public hostname.
 */
object RoomLinks {

    fun parse(input: String): RoomLink {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) throw RoomLinkException("Paste an invitation link.")

        val invite = ShareLinks.parseInvite(trimmed)
            ?: throw RoomLinkException("That does not look like a WatchWave room link.")

        val base = resolveBaseUrl(trimmed)
        val token = invite.inviteToken
            ?: throw RoomLinkException("This link is missing its invitation token. Ask the host to share the full link.")

        return RoomLink(
            baseUrl = base,
            roomId = invite.roomId,
            inviteToken = token,
            original = trimmed,
        )
    }

    private fun resolveBaseUrl(input: String): String {
        if (input.startsWith("http://", ignoreCase = true) || input.startsWith("https://", ignoreCase = true)) {
            val uri = try {
                URI(input)
            } catch (_: Exception) {
                throw RoomLinkException("The link address is malformed.")
            }
            val scheme = uri.scheme?.lowercase()
            val authority = uri.rawAuthority
            if (scheme !in setOf("http", "https") || authority.isNullOrBlank()) {
                throw RoomLinkException("The link must use http:// or https://.")
            }
            return "$scheme://$authority"
        }

        // watchwave://room/... and bare room ids need a configured public origin.
        val domain = BuildConfig.DEFAULT_BASE_DOMAIN.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')
        if (domain.isBlank()) {
            throw RoomLinkException(
                "Open the HTTPS invitation link instead, or set watchwave.baseDomain when building the app.",
            )
        }
        return "https://$domain"
    }
}
