package com.watchwave.tunnel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Companion connector: a Termux session, desktop, or router runs cloudflared and
 * forwards a public URL to this phone. The URL is only accepted after the health
 * endpoint answers with the WatchWave marker, and it is re-checked continuously.
 */
class CompanionUrlProvider(
    private val config: TunnelConfig,
    private val scope: CoroutineScope,
    private val verifier: ReachabilityVerifier = HttpReachabilityVerifier(),
) : TunnelProvider {

    override val id: String = "companion"

    private val _status = MutableStateFlow(
        TunnelStatus(state = TunnelState.NOT_CONFIGURED, provider = id, message = "Companion URL is not configured"),
    )
    override val status: StateFlow<TunnelStatus> = _status.asStateFlow()

    private var monitorJob: Job? = null

    @Volatile
    private var stopping: Boolean = false

    override suspend fun start(port: Int) {
        monitorJob?.cancelAndJoin()
        stopping = false
        val baseUrl = normalize(config.companionUrl)
        if (baseUrl.isEmpty()) {
            _status.value = TunnelStatus(
                TunnelState.NOT_CONFIGURED,
                id,
                message = "Enter the HTTPS URL reported by your companion connector.",
            )
            return
        }
        _status.value = TunnelStatus(TunnelState.STARTING, id, message = "Verifying companion URL")
        monitorJob = scope.launch(Dispatchers.IO) { monitor(baseUrl) }
    }

    override suspend fun stop() {
        stopping = true
        monitorJob?.cancelAndJoin()
        monitorJob = null
        _status.value = TunnelStatus(TunnelState.STOPPED, id, message = "Tunnel stopped")
    }

    private suspend fun monitor(baseUrl: String) {
        var failures = 0
        while (currentCoroutineContext().isActive && !stopping) {
            val result = verifier.verify(
                baseUrl,
                config.healthPath,
                if (failures == 0) config.verifyTimeoutMs else 8_000L,
            )
            if (result.reachable) {
                failures = 0
                _status.value = TunnelStatus(
                    TunnelState.CONNECTED,
                    id,
                    publicUrl = baseUrl,
                    verified = true,
                    message = "Companion tunnel verified",
                )
                delay(15_000L)
                continue
            }

            failures += 1
            if (failures >= MAX_FAILURES) {
                _status.value = TunnelStatus(
                    TunnelState.FAILED,
                    id,
                    publicUrl = baseUrl,
                    verified = false,
                    message = "Companion URL is unreachable: ${result.detail.take(160)}",
                )
                return
            }
            _status.value = TunnelStatus(
                TunnelState.RECONNECTING,
                id,
                publicUrl = baseUrl,
                verified = false,
                message = "Waiting for companion connector: ${result.detail.take(120)}",
            )
            delay(if (failures == 1) 2_000L else 4_000L)
        }
    }

    private fun normalize(raw: String): String =
        raw.trim().trimEnd('/').let { if (it.startsWith("http://") || it.startsWith("https://")) it else "" }

    companion object {
        private const val MAX_FAILURES = 5
    }
}
