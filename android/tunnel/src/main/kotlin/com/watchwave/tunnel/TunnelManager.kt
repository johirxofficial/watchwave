package com.watchwave.tunnel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the active [TunnelProvider] and exposes one stable status stream to the UI and
 * the diagnostics API. Switching modes always stops the previous provider first.
 */
class TunnelManager(
    private val scope: CoroutineScope,
    private val logger: (String) -> Unit = {},
) {

    private val _status = MutableStateFlow(
        TunnelStatus(state = TunnelState.NOT_CONFIGURED, provider = "none", message = "No tunnel configured"),
    )
    val status: StateFlow<TunnelStatus> = _status.asStateFlow()

    private var provider: TunnelProvider? = null
    private var collectJob: Job? = null
    private var activeConfig: TunnelConfig? = null

    suspend fun start(config: TunnelConfig, port: Int) {
        stopInternal(markStopped = false)
        activeConfig = config
        when (config.mode) {
            TunnelMode.NONE -> {
                _status.value = TunnelStatus(
                    TunnelState.NOT_CONFIGURED,
                    "none",
                    message = "Tunnel disabled; share the LAN link only",
                )
                return
            }

            TunnelMode.ON_DEVICE -> {
                provider = CloudflaredProcessProvider(
                    config,
                    scope,
                    providerId = "built-in",
                    logger = logger,
                )
            }

            TunnelMode.COMPANION_URL -> {
                provider = CompanionUrlProvider(config, scope)
            }

            TunnelMode.CLOUDFLARED_PROCESS -> {
                provider = CloudflaredProcessProvider(config, scope, logger = logger)
            }
        }

        val active = provider ?: return
        collectJob = scope.launch(Dispatchers.Default) {
            active.status.collect { _status.value = it }
        }
        active.start(port)
    }

    suspend fun stop() {
        stopInternal(markStopped = true)
    }

    fun currentConfig(): TunnelConfig? = activeConfig

    fun currentProviderState(): TunnelState = _status.value.state

    /** Used by the Tunnel screen to verify an arbitrary URL before saving it. */
    suspend fun verifyUrl(url: String, healthPath: String = "/api/v1/health"): VerifyResult {
        if (_status.value.state == TunnelState.CONNECTED && _status.value.publicUrl == url.trim().trimEnd('/')) {
            return VerifyResult(true, "Already verified")
        }
        return HttpReachabilityVerifier().verify(url, healthPath, 20_000L)
    }

    private suspend fun stopInternal(markStopped: Boolean) {
        collectJob?.cancelAndJoin()
        collectJob = null
        provider?.stop()
        provider = null
        if (markStopped) {
            _status.value = TunnelStatus(TunnelState.STOPPED, "none", message = "Tunnel stopped")
        }
    }
}
