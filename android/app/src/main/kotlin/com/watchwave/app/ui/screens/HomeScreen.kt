package com.watchwave.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watchwave.app.AppContainer
import com.watchwave.app.host.HostPhase
import com.watchwave.app.ui.components.BrandMark
import com.watchwave.app.ui.components.EmptyState
import com.watchwave.app.ui.components.MetricTile
import com.watchwave.app.ui.components.ScreenScaffold
import com.watchwave.app.ui.components.SectionLabel
import com.watchwave.app.ui.components.ShareLinkBox
import com.watchwave.app.ui.components.StatusPill
import com.watchwave.app.ui.components.WaveCard
import com.watchwave.app.ui.components.formatDuration
import com.watchwave.app.ui.components.formatRelative
import com.watchwave.app.ui.theme.Muted
import com.watchwave.app.ui.theme.Surface
import com.watchwave.app.ui.theme.WaveCyan
import com.watchwave.app.ui.theme.WaveGreen
import com.watchwave.app.ui.theme.WaveViolet

@Composable
fun HomeScreen(
    container: AppContainer,
    onHost: () -> Unit,
    onJoin: () -> Unit,
    onResumeHost: () -> Unit,
    onResumeViewer: () -> Unit,
    onHistory: () -> Unit,
    onTunnel: () -> Unit,
    onDiagnostics: () -> Unit,
) {
    val hostState by container.hostEngine.state.collectAsStateWithLifecycle()
    val hostConnection by container.hostEngine.connection.collectAsStateWithLifecycle()
    val viewerState by container.viewerEngine.state.collectAsStateWithLifecycle()
    val settings by container.settings.collectAsStateWithLifecycle()
    val history by remember { container.mediaRepository.roomHistory() }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    ScreenScaffold(
        title = "WatchWave",
        subtitle = "Self-hosted watch parties",
        actions = {
            IconButton(onClick = onTunnel) {
                Icon(Icons.Filled.Cloud, contentDescription = "Tunnel")
            }
            IconButton(onClick = onDiagnostics) {
                Icon(Icons.Filled.Sensors, contentDescription = "Diagnostics")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp),
        ) {
            BrandMark(modifier = Modifier.padding(top = 8.dp, bottom = 22.dp))

            Text(
                text = "Your movie night,\non your own terms.",
                style = MaterialTheme.typography.displaySmall,
                color = Color(0xFFF5F8FF),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Pick a video from this phone. WatchWave streams the original file over HTTP with real synchronised playback — no accounts, no re-uploads, no transcoding.",
                style = MaterialTheme.typography.bodyLarge,
                color = Muted,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill("Original quality", WaveCyan)
                StatusPill("Device-hosted", WaveViolet)
                StatusPill("LAN + tunnel", WaveGreen)
            }

            if (hostState.phase == HostPhase.LIVE || hostState.phase == HostPhase.STARTING) {
                Spacer(Modifier.height(22.dp))
                LiveHostCard(
                    roomName = hostState.roomName ?: "Watch party",
                    mediaTitle = hostState.mediaTitle ?: "Video",
                    participantCount = hostConnection.participants.size,
                    onOpen = onResumeHost,
                )
            }

            if (viewerState.phase == com.watchwave.app.viewer.ViewerPhase.LIVE) {
                Spacer(Modifier.height(22.dp))
                WaveCard(accent = WaveGreen.copy(alpha = 0.45f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = WaveGreen)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Watching ${viewerState.room?.roomName ?: "a room"}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                viewerState.room?.media?.title ?: "Playback in progress",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Button(onClick = onResumeViewer) { Text("Resume") }
                    }
                }
            }

            Spacer(Modifier.height(26.dp))
            SectionLabel("Start here")
            Spacer(Modifier.height(10.dp))

            WaveCard(
                accent = WaveCyan.copy(alpha = 0.4f),
                onClick = onHost,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AccentIcon(WaveCyan, Icons.Filled.Add)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Host a watch party", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Choose a local video and start the embedded streaming server.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Muted,
                        )
                    }
                    Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = WaveCyan)
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onHost,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Icon(Icons.Filled.VideoLibrary, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Choose a video")
                    }
                    Text(
                        text = "Runs a real HTTP Range server on this device",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            WaveCard(
                accent = WaveViolet.copy(alpha = 0.4f),
                onClick = onJoin,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AccentIcon(WaveViolet, Icons.Filled.Link)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Join a room", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Paste the invitation link from the host. The browser client is already served by the host.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Muted,
                        )
                    }
                    Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = WaveViolet)
                }
                Spacer(Modifier.height(14.dp))
                OutlinedButton(onClick = onJoin) {
                    Icon(Icons.Filled.People, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Enter invite link")
                }
            }

            if (hostState.phase == HostPhase.LIVE && hostState.shareUrl != null) {
                Spacer(Modifier.height(22.dp))
                ShareLinkBox(
                    label = if (hostState.tunnelUrl != null) "PUBLIC LINK · TUNNEL VERIFIED" else "LOCAL NETWORK LINK",
                    url = hostState.shareUrl.orEmpty(),
                    verified = hostState.tunnelUrl != null,
                )
            }

            Spacer(Modifier.height(28.dp))
            SectionLabel("How it works")
            Spacer(Modifier.height(10.dp))
            StepRow(
                number = "01",
                title = "Pick a local file",
                body = "The video stays on your device. WatchWave reads it through Android's document provider and never copies it.",
            )
            StepRow(
                number = "02",
                title = "Share one link",
                body = "Use the LAN address on the same Wi-Fi, or configure a verified Cloudflare tunnel for remote friends.",
            )
            StepRow(
                number = "03",
                title = "Stay in sync",
                body = "A WebSocket clock authority and drift correction keep every phone and browser on the same frame.",
            )

            Spacer(Modifier.height(26.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Recent rooms")
                Spacer(Modifier.weight(1f))
                if (history.isNotEmpty()) {
                    OutlinedButton(onClick = onHistory) {
                        Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("All")
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            if (history.isEmpty()) {
                EmptyState(
                    title = "No rooms yet",
                    body = "Your hosted and joined rooms will appear here with their duration and role.",
                )
            } else {
                history.take(3).forEach { entry ->
                    WaveCard(
                        modifier = Modifier.padding(bottom = 10.dp),
                        contentPadding = PaddingValues(14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    entry.roomName,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    entry.videoTitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${entry.role.replaceFirstChar { it.uppercase() }} · ${formatRelative(entry.startedAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Muted,
                                )
                            }
                            val ended = entry.endedAt
                            if (ended != null) {
                                Text(
                                    formatDuration(ended - entry.startedAt),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = WaveCyan,
                                )
                            } else {
                                StatusPill("Live", WaveGreen)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(Surface.copy(alpha = 0.6f))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Router, contentDescription = null, tint = Muted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Host listen port: ${settings.serverPort} · ${settings.maxParticipants} max viewers per room",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "WatchWave 1.0.0 · No accounts · No third-party media uploads",
                style = MaterialTheme.typography.labelSmall,
                color = Muted.copy(alpha = 0.8f),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LiveHostCard(
    roomName: String,
    mediaTitle: String,
    participantCount: Int,
    onOpen: () -> Unit,
) {
    WaveCard(accent = WaveGreen.copy(alpha = 0.45f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill("ROOM LIVE", WaveGreen)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.People, contentDescription = null, tint = Muted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text("$participantCount", style = MaterialTheme.typography.labelMedium, color = Muted)
        }
        Spacer(Modifier.height(12.dp))
        Text(roomName, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(mediaTitle, style = MaterialTheme.typography.bodyMedium, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(14.dp))
        Button(onClick = onOpen, colors = ButtonDefaults.buttonColors(containerColor = WaveGreen, contentColor = Color(0xFF052016))) {
            Text("Open host dashboard")
        }
    }
}

@Composable
private fun StepRow(number: String, title: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(WaveCyan.copy(alpha = 0.22f), WaveViolet.copy(alpha = 0.22f))),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number,
                style = MaterialTheme.typography.labelMedium,
                color = WaveCyan,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(3.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Muted)
        }
    }
}

@Composable
private fun AccentIcon(color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
    }
}
