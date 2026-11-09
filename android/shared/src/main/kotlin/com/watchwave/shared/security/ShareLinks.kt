package com.watchwave.shared.security

import java.net.URLDecoder
import java.net.URLEncoder

/** Builds and parses invitation links entirely on the client side. */
object ShareLinks {

    fun buildSharePath(roomId: String, inviteToken: String): String =
        "/room/$roomId?t=${urlEncode(inviteToken)}"

    fun buildShareUrl(baseUrl: String, roomId: String, inviteToken: String): String =
        baseUrl.trimEnd('/') + buildSharePath(roomId, inviteToken)

    fun buildLanUrl(ipv4: String, port: Int, roomId: String, inviteToken: String): String =
        "http://$ipv4:$port" + buildSharePath(roomId, inviteToken)

    fun buildDeepLink(roomId: String, inviteToken: String? = null): String {
        val base = "watchwave://room/$roomId"
        return if (inviteToken.isNullOrBlank()) base else "$base?t=${urlEncode(inviteToken)}"
    }

    data class ParsedInvite(val roomId: String, val inviteToken: String?)

    /**
     * Accepts any of:
     *  - https://host/room/ROOM_ID?t=TOKEN
     *  - http://192.168.1.2:8080/room/ROOM_ID?t=TOKEN
     *  - watchwave://room/ROOM_ID?t=TOKEN
     *  - ROOM_ID (bare code, no token)
     */
    fun parseInvite(input: String): ParsedInvite? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        val roomIndex = trimmed.indexOf("/room/")
        if (roomIndex >= 0) {
            val after = trimmed.substring(roomIndex + "/room/".length)
            val path = after.substringBefore('?').substringBefore('#').trim('/')
            if (!ROOM_ID_REGEX.matches(path)) return null
            return ParsedInvite(path, tokenFromQuery(after))
        }

        if (ROOM_ID_REGEX.matches(trimmed)) {
            return ParsedInvite(trimmed, null)
        }
        return null
    }

    private fun tokenFromQuery(afterPath: String): String? {
        val query = afterPath.substringAfter('?', missingDelimiterValue = "")
        if (query.isBlank()) return null
        return query.split('&')
            .map { it.split('=', limit = 2) }
            .firstOrNull { it.size == 2 && it[0] == "t" }
            ?.let { urlDecode(it[1]) }
            ?.takeIf { it.isNotBlank() }
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun urlDecode(value: String): String = try {
        URLDecoder.decode(value, "UTF-8")
    } catch (_: IllegalArgumentException) {
        value
    }

    private val ROOM_ID_REGEX = Regex("^[a-z0-9]{6,32}$")
}
