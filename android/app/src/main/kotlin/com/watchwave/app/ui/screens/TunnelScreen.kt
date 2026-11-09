package com.watchwave.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watchwave.app.AppContainer
import com.watchwave.app.host.HostPhase
import com.watchwave.app.ui.components.InfoBanner
import com.watchwave.app.ui.components.LabeledValue
import com.watchwave.app.ui.components.ScreenScaffold
import com.watchwave.app.ui.components.SectionLabel
import com.watchwave.app.ui.components.ShareLinkBox
import com.watchwave.app.ui.components.StatusPill
import com.watchwave.app.ui.components.WaveCard
import com.watchwave.app.ui.components.formatRelative
import com.watchwave.app.ui.theme.Muted
import com.watchwave.app.ui.theme.WaveAmber
import com.watchwave.app.ui.theme.WaveCyan
import com.watchwave.app.ui.theme.WaveGreen
import com.watchwave.app.ui.theme.WaveRed
import com.watchwave.app.ui.theme.WaveViolet
import com.watchwave.data.settings.TunnelMode
import com.watchwave.tunnel.TunnelState

@Composable
fun TunnelScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val status by container.tunnelManager.status.collectAsStateWithLifecycle()
    val host by container.hostEngine.state.collectAsStateWithLifecycle()
    val settings by container.settings.collectAsStateWithLifecycle()


    val statusColor = when {
        status.state == TunnelState.CONNECTED && status.verified -> WaveGreen
        status.state == TunnelState.STARTING || status.state == TunnelState.RECONNECTING -> WaveAmber
        status.state == TunnelState.FAILED || status.state == TunnelState.NOT_INSTALLED -> WaveRed
        else -> Muted
    }

    ScreenScaffold(
        title = "Tunnel",
        subtitle = "Public reachability without port forwarding",
        onBack = onBack,
        actions = {
            IconButton(onClick = onOpenDiagnostics) {
                Icon(Icons.Filled.Cloud, contentDescription = "Diagnostics")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Tunnel settings")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            WaveCard(
                accent = statusColor.copy(alpha = 0.42f),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (status.verified) Icons.Filled.Verified else Icons.Filled.CloudOff,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(26.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (status.state) {
                                TunnelState.NOT_CONFIGURED -> "No tunnel configured"
                                TunnelState.NOT_INSTALLED -> "Connector not installed"
                                TunnelState.STARTING -> "Connector starting"
                                TunnelState.CONNECTED -> if (status.verified) "Verified public link" else "Connected, verifying"
                                TunnelState.RECONNECTING -> "Reconnecting"
                                TunnelState.FAILED -> "Tunnel failed"
                                TunnelState.STOPPED -> "Tunnel stopped"
                            },
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            "Provider: ${status.provider} · updated ${formatRelative(status.updatedAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Muted,
                        )
                    }
                    StatusPill(status.state.name, statusColor)
                }
                status.message?.let { message ->
                    Spacer(Modifier.height(12.dp))
                    Text(message, style = MaterialTheme.typography.bodyMedium, color = Muted)
                }
                val activeUrl = status.activeUrl
                if (activeUrl != null && host.shareUrl != null) {
                    Spacer(Modifier.height(14.dp))
                    ShareLinkBox(
                        label = "VERIFIED PUBLIC LINK",
                        url = host.shareUrl!!,
                        verified = true,
                    )
                } else if (host.phase == HostPhase.LIVE) {
                    Spacer(Modifier.height(12.dp))
                    InfoBanner(
                        "A public link is shown only after the connector reports a verified, healthy URL. Until then, share the LAN link from the host dashboard.",
                        color = WaveAmber,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { container.hostEngine.restartTunnel() },
                        enabled = host.phase == HostPhase.LIVE,
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Recheck now")
                    }
                    OutlinedButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Configure")
                    }
                }
                if (host.phase != HostPhase.LIVE) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Start a watch party to launch the configured connector. The tunnel is stopped when the room ends.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                    )
                }
            }

            SectionLabel("Current configuration")
            WaveCard {
                LabeledValue("Mode", tunnelModeLabel(settings.tunnelMode))
                Spacer(Modifier.height(8.dp))
                if (settings.companionPublicUrl.isNotBlank()) {
                    LabeledValue("Companion URL", settings.companionPublicUrl)
                    Spacer(Modifier.height(8.dp))
                }
                if (settings.cloudflaredPath.isNotBlank()) {
                    LabeledValue("cloudflared path", settings.cloudflaredPath)
                    Spacer(Modifier.height(8.dp))
                }
                if (settings.namedTunnelHostname.isNotBlank()) {
                    LabeledValue("Named hostname", settings.namedTunnelHostname)
                    Spacer(Modifier.height(8.dp))
                }
                LabeledValue("LAN URL", host.lanUrl ?: "not detected")
                LabeledValue("Server port", (host.serverPort ?: settings.serverPort).toString())
            }

            SectionLabel("Companion mode")
            WaveCard {
                Text(
                    "Run cloudflared on a machine that can reach this phone, then paste its public URL into Settings.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                CodeBlock(
                    listOf(
                        "cloudflared tunnel --url http://<phone-lan-ip>:${host.serverPort ?: settings.serverPort}",
                        "# copy the https://…trycloudflare.com URL",
                        "# WatchWave verifies https://<url>/api/v1/health",
                    ),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "This is the recommended mode when you already control a server, router or Termux session. The app never claims the link works until the health marker and reachability checks succeed.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                )
            }

            SectionLabel("Instant public link")
            WaveCard {
                Text(
                    "No setup. WatchWave bundles Cloudflare's cloudflared for arm64 phones, starts a " +
                        "free Quick Tunnel when the party starts, and only shows the link after the " +
                        "health check succeeds.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                LabeledValue("Included binary", "arm64-v8a")
                Spacer(Modifier.height(8.dp))
                LabeledValue("Account required", "No")
                Spacer(Modifier.height(8.dp))
                LabeledValue("Cost", "Free Quick Tunnel")
                Spacer(Modifier.height(10.dp))
                Text(
                    "Quick Tunnels are best-effort and may get a new hostname each time. For a " +
                        "permanent hostname, use a named tunnel token or companion mode. Other ABIs " +
                        "fall back to LAN, companion, or a custom cloudflared binary.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                )
            }

            InfoBanner(
                "Public exposure is opt-in. LAN mode keeps the server private to your network; tunnel mode requires HTTPS and a verified health response.",
                color = WaveCyan,
            )

            Text(
                "Tunnel status is also available at /api/v1/tunnel/status on the host. The API never returns tokens or filesystem paths.",
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
        }
    }
}

private fun tunnelModeLabel(mode: TunnelMode): String = when (mode) {
    TunnelMode.NONE -> "LAN only"
    TunnelMode.ON_DEVICE -> "Instant public link (bundled)"
    TunnelMode.COMPANION_URL -> "Companion URL"
    TunnelMode.CLOUDFLARED_PROCESS -> "Custom cloudflared"
}

@Composable
private fun CodeBlock(lines: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
            .padding(10.dp),
    ) {
        lines.forEach { line ->
            Text(
                line,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                color = Color(0xFFB8E6F0),
            )
        }
    }
}
