package com.watchwave.shared.security

/**
 * Sliding-window rate limiter used to stop command flooding.
 *
 * Both REST join attempts and WebSocket playback commands are limited. The limiter
 * is intentionally tiny and allocation-free in the steady state once the window is
 * full; it is safe to call from multiple threads.
 */
class SlidingWindowRateLimiter(
    private val maxEvents: Int,
    private val windowMs: Long,
) {
    private val timestamps = ArrayDeque<Long>()

    @Synchronized
    fun tryAcquire(nowMs: Long): Boolean {
        prune(nowMs)
        if (timestamps.size >= maxEvents) return false
        timestamps.addLast(nowMs)
        return true
    }

    @Synchronized
    fun retryAfterMs(nowMs: Long): Long {
        prune(nowMs)
        if (timestamps.size < maxEvents) return 0L
        val oldest = timestamps.firstOrNull() ?: return 0L
        return (oldest + windowMs - nowMs).coerceAtLeast(1L)
    }

    @Synchronized
    fun reset() = timestamps.clear()

    private fun prune(nowMs: Long) {
        while (timestamps.isNotEmpty() && nowMs - timestamps.first() >= windowMs) {
            timestamps.removeFirst()
        }
    }
}
