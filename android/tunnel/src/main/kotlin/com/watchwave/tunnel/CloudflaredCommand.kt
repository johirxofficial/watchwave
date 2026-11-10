package com.watchwave.tunnel

/**
 * Builds the exact cloudflared invocation. Kept pure so the quick-tunnel transport
 * flags can be unit-tested without starting a process.
 */
internal fun buildCloudflaredCommand(
    executablePath: String,
    config: TunnelConfig,
    port: Int,
): List<String> {
    val named = config.tunnelToken.isNotBlank()
    val transport = buildList {
        config.edgeProtocol.trim().takeIf { it.isNotEmpty() }?.let {
            add("--protocol")
            add(it)
        }
        config.edgeIpVersion.trim().takeIf { it.isNotEmpty() }?.let {
            add("--edge-ip-version")
            add(it)
        }
    }
    return if (named) {
        listOf(executablePath) + config.quickTunnelArgs.take(2) + transport + "run"
    } else {
        val urlIndex = config.quickTunnelArgs.indexOf("--url")
        val preUrlArgs = if (urlIndex >= 0) {
            config.quickTunnelArgs.take(urlIndex)
        } else {
            config.quickTunnelArgs
        }
        listOf(executablePath) + preUrlArgs + transport +
            "--url" + "http://127.0.0.1:$port"
    }
}
