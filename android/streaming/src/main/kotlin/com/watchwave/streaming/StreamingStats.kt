package com.watchwave.streaming

import com.watchwave.shared.protocol.UploadStatsDto
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Tracks the bytes actually sent to viewers. The reported bitrate is the average
 * upload rate for the current server session, which is the most honest number a host
 * can use to compare against their network's upstream capacity.
 */
class StreamingStats {
    private val bytesSent = AtomicLong(0)
    private val streamsStarted = AtomicLong(0)
    private val activeStreams = AtomicInteger(0)
    private val startedAtMono = System.nanoTime()

    fun onStreamOpened() {
        activeStreams.incrementAndGet()
        streamsStarted.incrementAndGet()
    }

    fun onStreamClosed() {
        activeStreams.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
    }

    fun addBytes(count: Long) {
        if (count > 0) bytesSent.addAndGet(count)
    }

    fun snapshot(): UploadStatsDto {
        val elapsedMs = ((System.nanoTime() - startedAtMono) / 1_000_000L).coerceAtLeast(1L)
        val bytes = bytesSent.get()
        val bitrate = (bytes * 8_000L) / elapsedMs
        return UploadStatsDto(
            bytesSent = bytes,
            activeStreams = activeStreams.get(),
            totalStreamsStarted = streamsStarted.get(),
            approximateBitrateBps = bitrate,
        )
    }

    fun resetSessionCounters() {
        bytesSent.set(0)
        streamsStarted.set(0)
    }
}
