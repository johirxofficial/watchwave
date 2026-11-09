package com.watchwave.app.ui.screens

import android.os.Build
import android.widget.Toast
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watchwave.app.AppContainer
import com.watchwave.app.BuildConfig
import com.watchwave.app.core.NetworkUtils
import com.watchwave.app.host.HostPhase
import com.watchwave.app.net.ConnectionPhase
import com.watchwave.app.ui.components.LabeledValue
import com.watchwave.app.ui.components.MetricTile
import com.watchwave.app.ui.components.ScreenScaffold
import com.watchwave.app.ui.components.SectionLabel
import com.watchwave.app.ui.components.StatusPill
import com.watchwave.app.ui.components.WaveCard
import com.watchwave.app.ui.components.formatBitrate
import com.watchwave.app.ui.components.formatBytes
import com.watchwave.app.ui.components.formatClockTime
import com.watchwave.app.ui.theme.Muted
import com.watchwave.app.ui.theme.WaveCyan
import com.watchwave.app.ui.theme.WaveGreen
import com.watchwave.app.ui.theme.WaveRed
import com.watchwave.app.viewer.ViewerPhase

@Composable
fun DiagnosticsScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val host by container.hostEngine.state.collectAsStateWithLifecycle()
    val hostConnection by container.hostEngine.connection.collectAsStateWithLifecycle()
    val stats by container.hostEngine.stats.collectAsStateWithLifecycle()
    val tunnel by container.tunnelManager.status.collectAsStateWithLifecycle()
    val viewer by container.viewerEngine.state.collectAsStateWithLifecycle()
    val viewerConnection by container.viewerEngine.connection.collectAsStateWithLifecycle()
    val logs by container.log.lines.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val lan = NetworkUtils.lanAddress()

    val report = buildString {
            appendLine("WatchWave diagnostics")
            appendLine("version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) package=${BuildConfig.APPLICATION_ID}")
            appendLine("android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT} device=${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("lanIp=${lan?.ipv4 ?: "none"} interface=${lan?.interfaceName ?: "none"}")
            appendLine("hostPhase=${host.phase.name} roomId=${host.roomId ?: "none"} port=${host.serverPort ?: "none"}")
            appendLine("hostServerRunning=${container.hostEngine.serverRunning()} activeConnections=${container.hostEngine.activeConnections()}")
            appendLine("hostWs=${hostConnection.phase.name} participants=${hostConnection.participants.size}")
            appendLine("stats=active:${stats.activeStreams} sent:${stats.bytesSent} bitrate:${stats.approximateBitrateBps}")
            appendLine("tunnel=${tunnel.state.name} provider=${tunnel.provider} verified=${tunnel.verified}")
            appendLine("viewerPhase=${viewer.phase.name} viewerWs=${viewerConnection.phase.name}")
            appendLine("note=invitation tokens and share URLs are omitted from this report")
    }

    ScreenScaffold(
        title = "Diagnostics",
        subtitle = "Honest, token-free runtime state",
        onBack = onBack,
        actions = {
            IconButton(onClick = { container.hostEngine.refreshNow() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    clipboard.setText(AnnotatedString(report))
                    Toast.makeText(context, "Diagnostics copied", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy report")
                }
                OutlinedButton(onClick = { container.log.clear() }) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Clear log")
                }
            }

            SectionLabel("Host")
            WaveCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(
                        when (host.phase) {
                            HostPhase.LIVE -> "LIVE"
                            HostPhase.FAILED -> "FAILED"
                            HostPhase.STARTING -> "STARTING"
                            HostPhase.STOPPING -> "STOPPING"
                            HostPhase.IDLE -> "IDLE"
                        },
                        when (host.phase) {
                            HostPhase.LIVE -> WaveGreen
                            HostPhase.FAILED -> WaveRed
                            else -> WaveCyan
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    StatusPill(
                        if (hostConnection.phase == ConnectionPhase.LIVE) "WS CONNECTED" else hostConnection.phase.name,
                        if (hostConnection.phase == ConnectionPhase.LIVE) WaveGreen else Color(0xFFFBBF24),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "server ${if (container.hostEngine.serverRunning()) "up" else "down"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    LabeledValue("Room", host.roomId ?: "—")
                    LabeledValue("Port", (host.serverPort ?: 0).toString())
                    LabeledValue("Expires", formatClockTime(host.expiresAt ?: 0L))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    LabeledValue("Participants", "${hostConnection.participants.size}/${host.maxParticipants}")
                    LabeledValue("Server connections", "${container.hostEngine.activeConnections()}")
                    LabeledValue("Error", host.error ?: hostConnection.message ?: "none")
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    MetricTile("Active streams", "${stats.activeStreams}", Modifier.weight(1f))
                    MetricTile("Bytes sent", formatBytes(stats.bytesSent), Modifier.weight(1f))
                    MetricTile("Average rate", formatBitrate(stats.approximateBitrateBps), Modifier.weight(1f))
                }
            }

            SectionLabel("Tunnel")
            WaveCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(tunnel.state.name, if (tunnel.verified) WaveGreen else WaveCyan)
                    Spacer(Modifier.width(8.dp))
                    Text("provider ${tunnel.provider}", style = MaterialTheme.typography.bodyMedium, color = Muted)
                }
                Spacer(Modifier.height(10.dp))
                LabeledValue("Verified", if (tunnel.verified) "yes" else "no")
                Spacer(Modifier.height(8.dp))
                LabeledValue("Message", tunnel.message ?: "—")
                Spacer(Modifier.height(8.dp))
                Text(
                    "Public URLs are intentionally omitted from this screen because they contain invitation tokens.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                )
            }

            SectionLabel("Viewer")
            WaveCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(viewer.phase.name, if (viewer.phase == ViewerPhase.LIVE) WaveGreen else WaveCyan)
                    Spacer(Modifier.width(8.dp))
                    StatusPill(viewerConnection.phase.name, if (viewerConnection.phase == ConnectionPhase.LIVE) WaveGreen else Muted)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    LabeledValue("Room", viewer.roomId ?: "—")
                    LabeledValue("Name", viewer.displayName ?: "—")
                    LabeledValue("Host offline", if (viewerConnection.hostOffline) "yes" else "no")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    viewer.error ?: viewerConnection.message ?: "No viewer errors.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                )
            }

            SectionLabel("Runtime log")
            WaveCard {
                if (logs.isEmpty()) {
                    Text("No events yet.", style = MaterialTheme.typography.bodyMedium, color = Muted)
                } else {
                    SelectionContainer {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF080C13), MaterialTheme.shapes.small)
                                .padding(10.dp),
                        ) {
                            logs.takeLast(80).forEach { line ->
                                Text(
                                    line,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = Color(0xFFB9C7DA),
                                )
                            }
                        }
                    }
                }
            }

            SectionLabel("Build")
            WaveCard {
                LabeledValue("App", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                Spacer(Modifier.height(6.dp))
                LabeledValue("Protocol", "v1 / WebSocket + HTTP Range")
                Spacer(Modifier.height(6.dp))
                LabeledValue("Device", "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                Spacer(Modifier.height(6.dp))
                LabeledValue("LAN", lan?.let { "${it.ipv4} on ${it.interfaceName}" } ?: "not detected")
            }
        }
    }
}
