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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Runs a user-supplied cloudflared binary as a child process.
 *
 * Android can execute a static, Android-compatible Go binary placed in app-writable
 * storage (for example via `adb push` + `chmod 700`), but a stock Linux glibc build
 * will not run on Android's Bionic libc. This provider therefore never bundles a
 * binary and refuses to start until the configured path exists and is executable.
 * Every connection is re-verified over HTTP before it becomes visible in the UI.
 */
class CloudflaredProcessProvider(
    private val config: TunnelConfig,
    private val scope: CoroutineScope,
    private val verifier: ReachabilityVerifier = HttpReachabilityVerifier(),
) : TunnelProvider {

    override val id: String = "cloudflared"

    private val _status = MutableStateFlow(
        TunnelStatus(state = TunnelState.NOT_CONFIGURED, provider = id, message = "cloudflared is not configured"),
    )
    override val status: StateFlow<TunnelStatus> = _status.asStateFlow()

    private var runJob: Job? = null
    private var process: Process? = null

    @Volatile
    private var stopping: Boolean = false

    override suspend fun start(port: Int) {
        runJob?.cancelAndJoin()
        stopping = false
        _status.value = TunnelStatus(TunnelState.STARTING, id, message = "Launching cloudflared")
        runJob = scope.launch(Dispatchers.IO) { supervise(port) }
    }

    override suspend fun stop() {
        stopping = true
        process?.let { proc ->
            proc.destroy()
            withContext(Dispatchers.IO) {
                if (!proc.waitFor(3, TimeUnit.SECONDS)) proc.destroyForcibly()
            }
        }
        process = null
        runJob?.cancelAndJoin()
        runJob = null
        _status.value = TunnelStatus(TunnelState.STOPPED, id, message = "Tunnel stopped")
    }

    private suspend fun supervise(port: Int) {
        val path = config.executablePath.trim()
        if (path.isEmpty()) {
            fail(TunnelState.NOT_INSTALLED, "Set the path to a verified Android cloudflared binary.")
            return
        }
        val executable = File(path)
        if (!executable.isFile || !executable.canExecute()) {
            fail(
                TunnelState.NOT_INSTALLED,
                "No executable found at the configured path. Use the companion connector if you cannot provide an Android build.",
            )
            return
        }

        var attempt = 0
        while (currentCoroutineContext().isActive && !stopping) {
            attempt += 1
            _status.value = TunnelStatus(
                if (attempt == 1) TunnelState.STARTING else TunnelState.RECONNECTING,
                id,
                message = "Starting cloudflared (attempt $attempt)",
            )

            val started = try {
                runOnce(executable, port)
            } catch (error: Exception) {
                if (stopping) return
                _status.value = TunnelStatus(
                    TunnelState.RECONNECTING,
                    id,
                    message = "cloudflared error: ${redact(error.message ?: "unknown error").take(180)}",
                )
                false
            }

            if (stopping) return
            if (!started && attempt >= MAX_ATTEMPTS) {
                fail(TunnelState.FAILED, "cloudflared could not establish a verified tunnel. Check the binary and network.")
                return
            }
            if (started) attempt = 0
            _status.value = TunnelStatus(
                TunnelState.RECONNECTING,
                id,
                message = "Tunnel dropped; reconnecting",
            )
            delay(backoffMs(attempt))
        }
    }

    /** @return true when a verified tunnel was reached at least once. */
    private suspend fun runOnce(executable: File, port: Int): Boolean {
        val named = config.tunnelToken.isNotBlank()
        val targetHostname = normalizeHostname(config.namedHostname)
        val command = if (named) {
            listOf(executable.absolutePath) + config.quickTunnelArgs.take(2) + "run"
        } else {
            listOf(executable.absolutePath) + config.quickTunnelArgs + "http://127.0.0.1:$port"
        }

        val builder = ProcessBuilder(command)
            .redirectErrorStream(true)
        // Avoid leaking the token on the command line (visible in /proc to the same user).
        builder.environment().remove("TUNNEL_TOKEN")
        if (named && config.tunnelToken.isNotBlank()) {
            builder.environment()["TUNNEL_TOKEN"] = config.tunnelToken
        }

        val proc = try {
            builder.start()
        } catch (error: Exception) {
            _status.value = TunnelStatus(
                TunnelState.FAILED,
                id,
                message = "Could not execute cloudflared: ${redact(error.message ?: "unknown").take(160)}",
            )
            return false
        }
        process = proc

        val discoveredUrl = MutableStateFlow<String?>(null)
        val reader = scope.launch(Dispatchers.IO) {
            try {
                proc.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        QUICK_URL_REGEX.find(line)?.value?.let { found ->
                            if (discoveredUrl.value == null) discoveredUrl.value = found
                        }
                    }
                }
            } catch (_: Exception) {
                // Process teardown closes the stream.
            }
        }

        try {
            val publicUrl = if (named && targetHostname.isNotBlank()) {
                "https://$targetHostname"
            } else if (named) {
                val found = withTimeoutOrNull(45_000L) { discoveredUrl.filterNotNull().first() }
                if (found == null) {
                    _status.value = TunnelStatus(
                        TunnelState.FAILED,
                        id,
                        message = "Named tunnel started but no public hostname was reported. Configure a hostname.",
                    )
                    return false
                }
                found
            } else {
                withTimeoutOrNull(45_000L) { discoveredUrl.filterNotNull().first() } ?: run {
                    _status.value = TunnelStatus(
                        TunnelState.RECONNECTING,
                        id,
                        message = "cloudflared did not report a public URL in time",
                    )
                    return false
                }
            }

            _status.value = TunnelStatus(
                TunnelState.STARTING,
                id,
                publicUrl = publicUrl,
                verified = false,
                message = "Verifying public reachability",
            )

            val verification = verifier.verify(publicUrl, config.healthPath, config.verifyTimeoutMs)
            if (!verification.reachable) {
                _status.value = TunnelStatus(
                    TunnelState.RECONNECTING,
                    id,
                    publicUrl = publicUrl,
                    verified = false,
                    message = "Tunnel URL is not reachable: ${verification.detail.take(160)}",
                )
                return false
            }

            _status.value = TunnelStatus(
                TunnelState.CONNECTED,
                id,
                publicUrl = publicUrl,
                verified = true,
                message = "Tunnel connected and verified",
            )

            withContext(Dispatchers.IO) { proc.waitFor() }
            if (!stopping) {
                _status.value = TunnelStatus(
                    TunnelState.RECONNECTING,
                    id,
                    publicUrl = publicUrl,
                    verified = false,
                    message = "cloudflared exited; reconnecting",
                )
            }
            return true
        } finally {
            reader.cancel()
            if (proc.isAlive) proc.destroyForcibly()
            process = null
        }
    }

    private fun fail(state: TunnelState, message: String) {
        _status.value = TunnelStatus(state, id, message = message)
    }

    private fun normalizeHostname(raw: String): String =
        raw.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')

    private fun redact(value: String): String =
        if (config.tunnelToken.isBlank()) value else value.replace(config.tunnelToken, "[redacted]")

    private fun backoffMs(attempt: Int): Long =
        (2_000L * attempt.coerceIn(1, 5)).coerceAtMost(15_000L)

    companion object {
        private const val MAX_ATTEMPTS = 4
        private val QUICK_URL_REGEX = Regex("https://[a-zA-Z0-9-]+\\.trycloudflare\\.com")
    }
}
