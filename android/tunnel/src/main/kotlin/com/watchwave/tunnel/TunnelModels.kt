package com.watchwave.tunnel

import kotlinx.coroutines.flow.StateFlow

enum class TunnelMode { NONE, COMPANION_URL, CLOUDFLARED_PROCESS }

enum class TunnelState {
    NOT_CONFIGURED,
    NOT_INSTALLED,
    STARTING,
    CONNECTED,
    RECONNECTING,
    FAILED,
    STOPPED,
}

data class TunnelStatus(
    val state: TunnelState,
    val provider: String = "none",
    val publicUrl: String? = null,
    val verified: Boolean = false,
    val message: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    /** The UI may only surface a link while it is verified reachable. */
    val activeUrl: String? get() = publicUrl?.takeIf { state == TunnelState.CONNECTED && verified }
}

data class TunnelConfig(
    val mode: TunnelMode = TunnelMode.NONE,
    val executablePath: String = "",
    val tunnelToken: String = "",
    val namedHostname: String = "",
    val companionUrl: String = "",
    val healthPath: String = "/api/v1/health",
    val verifyTimeoutMs: Long = 45_000L,
    val quickTunnelArgs: List<String> = listOf("tunnel", "--no-autoupdate", "--url"),
)

interface TunnelProvider {
    val id: String
    val status: StateFlow<TunnelStatus>
    suspend fun start(port: Int)
    suspend fun stop()
}

fun interface ReachabilityVerifier {
    /**
     * @return true only when the URL really serves this app's health endpoint.
     */
    suspend fun verify(baseUrl: String, healthPath: String, timeoutMs: Long): VerifyResult
}

data class VerifyResult(val reachable: Boolean, val detail: String)

/** Marker string that a WatchWave host returns from /api/v1/health. */
const val HEALTH_MARKER: String = "WatchWave"
