package com.watchwave.shared.security

/** Injectable clocks keep playback and expiry logic deterministic in tests. */
interface Clock {
    fun monoMs(): Long
    fun wallMs(): Long
}

/** Monotonic time for playback math; wall clock for user-facing timestamps. */
object SystemClock : Clock {
    override fun monoMs(): Long = System.nanoTime() / 1_000_000L
    override fun wallMs(): Long = System.currentTimeMillis()
}

class FakeClock(
    private var mono: Long = 1_000_000L,
    private var wall: Long = 1_794_200_000_000L,
) : Clock {
    override fun monoMs(): Long = mono

    override fun wallMs(): Long = wall

    fun advance(ms: Long) {
        mono += ms
        wall += ms
    }

    fun setWall(ms: Long) {
        wall = ms
    }
}
