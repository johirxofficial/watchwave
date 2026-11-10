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
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs a cloudflared binary as a child process.
 *
 * The app bundles a static arm64 build in its native library directory for one-tap
 * Quick Tunnels, while advanced users may still point at their own Android-compatible
 * executable. A PEM CA bundle is supplied through SSL_CERT_FILE because Android does
 * not ship the Linux CA file layout Go expects. Every connection is re-verified over
 * HTTP before it becomes visible in the UI.
 */
class CloudflaredProcessProvider(
    private val config: TunnelConfig,
    private val scope: CoroutineScope,
    private val verifier: ReachabilityVerifier = HttpReachabilityVerifier(),
    providerId: String = "cloudflared",
    /** Receives redacted cloudflared output so Diagnostics can show the real failure. */
    private val logger: (String) -> Unit = {},
) : TunnelProvider {

    override val id: String = providerId

    private val _status = MutableStateFlow(
        TunnelStatus(state = TunnelState.NOT_CONFIGURED, provider = id, message = "cloudflared is not configured"),
    )
    override val status: StateFlow<TunnelStatus> = _status.asStateFlow()

    private var runJob: Job? = null
    private var process: Process? = null
    private val lastOutput = AtomicReference<String?>(null)
    private val lastErrorOutput = AtomicReference<String?>(null)

    @Volatile
    private var stopping: Boolean = false

    /** An executable/configuration error cannot be fixed by retrying the network. */
    @Volatile
    private var lastFailureWasFatal: Boolean = false

    /**
     * Start with HTTP/2 over TCP (works on UDP-blocking carriers), then alternate with
     * cloudflared's automatic QUIC/HTTP2 fallback so a network that blocks TCP:7844 is
     * not stuck failing forever.
     */
    @Volatile
    private var preferTcpTransport: Boolean = true

    override suspend fun start(port: Int) {
        runJob?.cancelAndJoin()
        stopping = false
        lastOutput.set(null)
        lastErrorOutput.set(null)
        logger("Tunnel: launching cloudflared on local port $port")
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
                val detail = sanitize(error.message ?: "unknown error")
                logger("Tunnel: cloudflared crashed: $detail")
                _status.value = TunnelStatus(
                    TunnelState.RECONNECTING,
                    id,
                    message = "cloudflared error: ${detail.take(180)}${lastOutputSuffix()}",
                )
                false
            }

            if (stopping) return
            if (started) {
                attempt = 0
                preferTcpTransport = true
            } else {
                preferTcpTransport = !preferTcpTransport
            }
            if (!started && attempt >= MAX_ATTEMPTS) {
                if (lastFailureWasFatal) {
                    fail(
                        TunnelState.FAILED,
                        "cloudflared could not start. Check the executable path, then open Diagnostics for the tunnel log.",
                    )
                    return
                }
                // Network failures are often temporary (Wi-Fi handoff, VPN, captive
                // portal). Keep trying while the room is live instead of staying
                // permanently stuck on FAILED.
                _status.value = TunnelStatus(
                    TunnelState.FAILED,
                    id,
                    message = "cloudflared could not connect after $MAX_ATTEMPTS attempts. Retrying automatically every ${RETRY_AFTER_FAILURE_MS / 1000}s; open Diagnostics for the tunnel log.",
                )
                logger("Tunnel: ${MAX_ATTEMPTS} attempts failed; retrying in ${RETRY_AFTER_FAILURE_MS / 1000}s")
                delay(RETRY_AFTER_FAILURE_MS)
                attempt = MAX_ATTEMPTS - 1
                continue
            }
            val delayMs = backoffMs(attempt)
            logger("Tunnel: reconnecting in ${delayMs / 1000}s (attempt $attempt)")
            _status.value = TunnelStatus(
                TunnelState.RECONNECTING,
                id,
                message = if (started) {
                    "Tunnel dropped; reconnecting"
                } else {
                    "Tunnel attempt $attempt failed; retrying${lastOutputSuffix()}"
                },
            )
            delay(delayMs)
        }
    }

    /** @return true when a verified tunnel was reached at least once. */
    private suspend fun runOnce(executable: File, port: Int): Boolean {
        lastFailureWasFatal = false
        lastOutput.set(null)
        lastErrorOutput.set(null)
        val named = config.tunnelToken.isNotBlank()
        val targetHostname = normalizeHostname(config.namedHostname)
        val attemptConfig = if (preferTcpTransport) {
            config
        } else {
            config.copy(edgeProtocol = "", edgeIpVersion = "")
        }
        val command = buildCloudflaredCommand(executable.absolutePath, attemptConfig, port)
        val transportLabel = listOfNotNull(
            attemptConfig.edgeProtocol.takeIf { it.isNotBlank() }?.let { "protocol $it" },
            attemptConfig.edgeIpVersion.takeIf { it.isNotBlank() }?.let { "IPv$it edge" },
        ).joinToString(", ").ifBlank { "default transport" }
        logger("Tunnel: starting ${if (named) "named" else "quick"} tunnel ($transportLabel)")

        val builder = ProcessBuilder(command)
            .redirectErrorStream(true)
        // Avoid leaking the token on the command line (visible in /proc to the same user).
        builder.environment().remove("TUNNEL_TOKEN")
        if (named && config.tunnelToken.isNotBlank()) {
            builder.environment()["TUNNEL_TOKEN"] = config.tunnelToken
        }
        if (config.caBundlePath.isNotBlank()) {
            // Android has no /etc/ssl/certs bundle; Go needs an explicit PEM file.
            builder.environment()["SSL_CERT_FILE"] = config.caBundlePath
        }
        if (config.homeDir.isNotBlank()) {
            builder.environment()["HOME"] = config.homeDir
            builder.environment()["TMPDIR"] = config.homeDir
        }

        val proc = try {
            builder.start()
        } catch (error: Exception) {
            lastFailureWasFatal = true
            val detail = sanitize(error.message ?: "unknown")
            logger("Tunnel: could not execute cloudflared: $detail")
            _status.value = TunnelStatus(
                TunnelState.FAILED,
                id,
                message = "Could not execute cloudflared: ${detail.take(160)}",
            )
            return false
        }
        process = proc

        val discoveredUrl = MutableStateFlow<String?>(null)
        val reader = scope.launch(Dispatchers.IO) {
            var logged = 0
            var lastImportantLog = 0L
            try {
                proc.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val cleaned = sanitize(line)
                        if (cleaned.isNotBlank()) {
                            lastOutput.set(cleaned)
                            if (isErrorOutput(cleaned)) lastErrorOutput.set(cleaned)
                            val now = System.currentTimeMillis()
                            val important = isImportantOutput(cleaned)
                            if (logged < 40 || (important && now - lastImportantLog >= 2_000L)) {
                                logger("cloudflared: ${cleaned.take(240)}")
                                logged += 1
                                if (important) lastImportantLog = now
                            }
                        }
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
                // A named tunnel has a stable hostname; give the process a moment to
                // fail fast instead of waiting for a URL that will never be printed.
                delay(600L)
                if (!proc.isAlive) {
                    val detail = lastOutputSuffix()
                    logger("Tunnel: named tunnel exited before startup$detail")
                    _status.value = TunnelStatus(
                        TunnelState.FAILED,
                        id,
                        message = "cloudflared exited before the named tunnel was ready$detail",
                    )
                    return false
                }
                "https://$targetHostname"
            } else {
                val found = awaitUrl(proc, discoveredUrl, URL_WAIT_MS)
                if (found == null && named && targetHostname.isBlank()) {
                    lastFailureWasFatal = true
                    _status.value = TunnelStatus(
                        TunnelState.FAILED,
                        id,
                        message = "Named tunnel has no public hostname configured. Add a hostname or clear the tunnel token.",
                    )
                    return false
                }
                if (found == null) {
                    val alive = proc.isAlive
                    val reason = if (alive) {
                        "cloudflared did not report a public URL within ${URL_WAIT_MS / 1000}s"
                    } else {
                        "cloudflared exited before reporting a public URL"
                    }
                    logger("Tunnel: $reason${lastOutputSuffix()}")
                    _status.value = TunnelStatus(
                        TunnelState.RECONNECTING,
                        id,
                        message = "$reason${lastOutputSuffix()}",
                    )
                    return false
                }
                found
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
                logger("Tunnel: public URL verification failed: ${sanitize(verification.detail)}")
                _status.value = TunnelStatus(
                    TunnelState.RECONNECTING,
                    id,
                    publicUrl = publicUrl,
                    verified = false,
                    message = "Tunnel URL is not reachable: ${sanitize(verification.detail).take(160)}${lastOutputSuffix()}",
                )
                return false
            }

            logger("Tunnel: connected and verified")
            _status.value = TunnelStatus(
                TunnelState.CONNECTED,
                id,
                publicUrl = publicUrl,
                verified = true,
                message = "Tunnel connected and verified",
            )

            val exitCode = withContext(Dispatchers.IO) { proc.waitFor() }
            logger("Tunnel: cloudflared exited with code $exitCode")
            if (!stopping) {
                _status.value = TunnelStatus(
                    TunnelState.RECONNECTING,
                    id,
                    publicUrl = publicUrl,
                    verified = false,
                    message = "cloudflared exited (code $exitCode); reconnecting${lastOutputSuffix()}",
                )
            }
            return true
        } finally {
            reader.cancel()
            if (proc.isAlive) proc.destroyForcibly()
            process = null
        }
    }

    /**
     * Waits for the quick-tunnel URL, but stops immediately when the process dies so
     * the caller can show cloudflared's real error instead of a generic timeout.
     */
    private suspend fun awaitUrl(
        proc: Process,
        discovered: MutableStateFlow<String?>,
        timeoutMs: Long,
    ): String? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            discovered.value?.let { return it }
            if (!proc.isAlive) {
                // Give the reader coroutine a moment to drain the final diagnostics.
                delay(300L)
                return discovered.value
            }
            delay(200L)
        }
        return discovered.value
    }

    private fun isImportantOutput(line: String): Boolean {
        val lower = line.lowercase()
        return IMPORTANT_MARKERS.any { lower.contains(it) }
    }

    private fun isErrorOutput(line: String): Boolean {
        val lower = line.lowercase()
        return ERROR_MARKERS.any { lower.contains(it) }
    }

    private fun lastOutputSuffix(): String {
        val line = (lastErrorOutput.get() ?: lastOutput.get())
            ?.takeIf { it.isNotBlank() }
            ?: return ""
        return " · last output: ${sanitize(line).take(180)}"
    }

    private fun fail(state: TunnelState, message: String) {
        _status.value = TunnelStatus(
            state,
            id,
            message = (message + lastOutputSuffix()).take(320),
        )
    }

    private fun normalizeHostname(raw: String): String =
        raw.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')

    /** Removes tokens and quick-tunnel hostnames before text reaches logs or the UI. */
    private fun sanitize(value: String): String {
        var safe = value.trim()
        if (config.tunnelToken.isNotBlank()) {
            safe = safe.replace(config.tunnelToken, "[redacted]")
        }
        safe = QUICK_URL_REGEX.replace(safe, "https://<quick-tunnel>.trycloudflare.com")
        safe = TOKEN_QUERY_REGEX.replace(safe, "\$1[redacted]")
        return safe.take(400)
    }

    private fun backoffMs(attempt: Int): Long =
        (2_000L * attempt.coerceIn(1, 5)).coerceAtMost(15_000L)

    companion object {
        private const val MAX_ATTEMPTS = 4
        private const val URL_WAIT_MS = 60_000L
        private const val RETRY_AFTER_FAILURE_MS = 30_000L
        private val QUICK_URL_REGEX = Regex("https://[a-zA-Z0-9-]+\\.trycloudflare\\.com")
        private val TOKEN_QUERY_REGEX =
            Regex("(?i)([?&](?:t|token|hostToken|sessionToken|key|secret)=)[^&\\s]+")
        private val IMPORTANT_MARKERS = listOf(
            "error",
            "failed",
            "failure",
            "warn",
            "timeout",
            "timed out",
            "refused",
            "unreachable",
            "dns",
            "tls",
            "certificate",
            "registered tunnel",
            "quick tunnel",
            "retrying",
            "connection",
            "protocol",
        )
        private val ERROR_MARKERS = listOf(
            "error",
            "err ",
            "failed",
            "failure",
            "fatal",
            "timeout",
            "timed out",
            "refused",
            "unreachable",
            "no such host",
            "certificate",
        )
    }
}
