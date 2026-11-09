package com.watchwave.streaming

data class ByteRange(val start: Long, val endInclusive: Long) {
    val length: Long get() = endInclusive - start + 1
}

sealed interface RangeParseResult {
    data object Full : RangeParseResult
    data class Partial(val range: ByteRange) : RangeParseResult
    data object NotSatisfiable : RangeParseResult
}

/**
 * Parser for a single RFC 7233 byte range.
 *
 * Multi-range requests are reported as not satisfiable rather than silently returning
 * only part of the requested data; clients (HTML5 video and ExoPlayer) issue single
 * ranges. A malformed range is also rejected so the handler can return a correct 416.
 */
object RangeParser {

    fun parse(header: String?, size: Long): RangeParseResult {
        if (header.isNullOrBlank()) return RangeParseResult.Full
        if (size <= 0L) return RangeParseResult.NotSatisfiable

        val trimmed = header.trim()
        if (!trimmed.startsWith("bytes=", ignoreCase = true)) return RangeParseResult.NotSatisfiable
        val spec = trimmed.substringAfter('=').trim()
        if (spec.isEmpty() || spec.contains(',')) return RangeParseResult.NotSatisfiable

        val dash = spec.indexOf('-')
        if (dash < 0) return RangeParseResult.NotSatisfiable

        val startPart = spec.substring(0, dash).trim()
        val endPart = spec.substring(dash + 1).trim()

        if (startPart.isEmpty()) {
            val suffix = endPart.toLongOrNull() ?: return RangeParseResult.NotSatisfiable
            if (suffix <= 0L) return RangeParseResult.NotSatisfiable
            val start = (size - suffix).coerceAtLeast(0L)
            return RangeParseResult.Partial(ByteRange(start, size - 1))
        }

        val start = startPart.toLongOrNull() ?: return RangeParseResult.NotSatisfiable
        if (start < 0L || start >= size) return RangeParseResult.NotSatisfiable

        val end = if (endPart.isEmpty()) {
            size - 1
        } else {
            val parsed = endPart.toLongOrNull() ?: return RangeParseResult.NotSatisfiable
            if (parsed < start) return RangeParseResult.NotSatisfiable
            parsed.coerceAtMost(size - 1)
        }
        return RangeParseResult.Partial(ByteRange(start, end))
    }

    fun contentRange(start: Long, endInclusive: Long, size: Long): String =
        "bytes $start-$endInclusive/$size"

    fun unsatisfiedContentRange(size: Long): String = "bytes */$size"
}
