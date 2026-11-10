package com.watchwave.tunnel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudflaredCommandTest {

    @Test
    fun quickTunnelUsesHttp2OverIpv4() {
        val command = buildCloudflaredCommand("/data/app/libcloudflared.so", TunnelConfig(), 8080)

        assertEquals(
            listOf(
                "/data/app/libcloudflared.so",
                "tunnel",
                "--no-autoupdate",
                "--protocol",
                "http2",
                "--edge-ip-version",
                "4",
                "--url",
                "http://127.0.0.1:8080",
            ),
            command,
        )
    }

    @Test
    fun namedTunnelPutsTransportFlagsBeforeRun() {
        val config = TunnelConfig(tunnelToken = "token-value")
        val command = buildCloudflaredCommand("/data/app/libcloudflared.so", config, 8080)

        assertEquals(
            listOf(
                "/data/app/libcloudflared.so",
                "tunnel",
                "--no-autoupdate",
                "--protocol",
                "http2",
                "--edge-ip-version",
                "4",
                "run",
            ),
            command,
        )
        assertFalse(command.contains("--url"))
        assertFalse(command.contains("token-value"))
    }

    @Test
    fun transportFlagsCanBeDisabled() {
        val config = TunnelConfig(edgeProtocol = "", edgeIpVersion = "")
        val command = buildCloudflaredCommand("cloudflared", config, 9000)

        assertEquals(
            listOf(
                "cloudflared",
                "tunnel",
                "--no-autoupdate",
                "--url",
                "http://127.0.0.1:9000",
            ),
            command,
        )
        assertTrue(command.none { it == "--protocol" || it == "--edge-ip-version" })
    }
}
