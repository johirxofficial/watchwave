package com.watchwave.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watchwave.app.AppContainer
import com.watchwave.app.host.HostPhase
import com.watchwave.app.net.ConnectionPhase
import com.watchwave.app.ui.components.ChatPanel
import com.watchwave.app.ui.components.ConnectedDot
import com.watchwave.app.ui.components.ErrorBanner
import com.watchwave.app.ui.components.InfoBanner
import com.watchwave.app.ui.components.LabeledValue
import com.watchwave.app.ui.components.MetricTile
import com.watchwave.app.ui.components.PlaybackControls
import com.watchwave.app.ui.components.PlayerPlaceholder
import com.watchwave.app.ui.components.ScreenScaffold
import com.watchwave.app.ui.components.SectionLabel
import com.watchwave.app.ui.components.ShareLinkBox
import com.watchwave.app.ui.components.StatusPill
import com.watchwave.app.ui.components.SyncStatusBadge
import com.watchwave.app.ui.components.VideoSurface
import com.watchwave.app.ui.components.WaveCard
import com.watchwave.app.ui.components.formatBitrate
import com.watchwave.app.ui.components.formatBytes
import com.watchwave.app.ui.components.formatClockTime
import com.watchwave.app.ui.components.formatDuration
import com.watchwave.app.ui.theme.Muted
import com.watchwave.app.ui.theme.SurfaceRaised
import com.watchwave.app.ui.theme.WaveAmber
import com.watchwave.app.ui.theme.WaveCyan
import com.watchwave.app.ui.theme.WaveGreen
import com.watchwave.app.ui.theme.WaveRed
import com.watchwave.app.ui.theme.WaveViolet
import com.watchwave.shared.protocol.ParticipantDto
import com.watchwave.tunnel.TunnelState
import kotlinx.coroutines.launch

@Composable
fun HostScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onEnded: () -> Unit,
    onOpenTunnel: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val state by container.hostEngine.state.collectAsStateWithLifecycle()
    val connection by container.hostEngine.connection.collectAsStateWithLifecycle()
    val playback by container.hostEngine.localPlayback.collectAsStateWithLifecycle()
    val stats by container.hostEngine.stats.collectAsStateWithLifecycle()
    val chat by container.hostEngine.chat.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var confirmEnd by remember { mutableStateOf(false) }
    var confirmRotate by remember { mutableStateOf(false) }

    when (state.phase) {
        HostPhase.IDLE, HostPhase.FAILED -> {
            ScreenScaffold(title = "Host dashboard", onBack = onBack) { padding ->
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    state.error?.let { ErrorBanner(message = it) }
                    Spacer(Modifier.height(18.dp))
                    Text("No room is live", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Start a party from the library. The embedded server, tunnel and synchronised playback all live in this process.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                    )
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = onBack) { Text("Back to home") }
                }
            }
        }

        HostPhase.STARTING, HostPhase.STOPPING -> {
            ScreenScaffold(title = "Host dashboard", onBack = onBack) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(14.dp))
                        Text(
                            if (state.phase == HostPhase.STARTING) "Starting the server and tunnel…" else "Stopping the room…",
                            color = Muted,
                        )
                    }
                }
            }
        }

        HostPhase.LIVE -> {
            ScreenScaffold(
                title = state.roomName ?: "Watch party",
                subtitle = state.mediaTitle ?: "Host dashboard",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onOpenDiagnostics) {
                        Icon(Icons.Filled.Sensors, contentDescription = "Diagnostics")
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
                    state.error?.let { message ->
                        ErrorBanner(message = message, onDismiss = { container.hostEngine.clearError() })
                    }
                    state.warnings.forEach { warning ->
                        InfoBanner(message = warning, color = WaveAmber)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(MaterialTheme.shapes.large),
                    ) {
                        if (playback.hasMedia) {
                            VideoSurface(
                                player = container.playback.player,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            PlayerPlaceholder(
                                title = state.mediaTitle ?: "Preparing video",
                                subtitle = "Opening the range stream from this device",
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SyncStatusBadge(
                            connected = connection.phase == ConnectionPhase.LIVE,
                            hostOffline = false,
                            message = connection.message ?: "Connecting",
                        )
                        StatusPill("${connection.participants.size}/${state.maxParticipants}", WaveCyan, icon = {
                            Icon(Icons.Filled.People, contentDescription = null, modifier = Modifier.size(13.dp), tint = WaveCyan)
                        })
                        Spacer(Modifier.weight(1f))
                        if (state.controlsLocked) {
                            StatusPill("Controls locked", WaveAmber)
                        } else {
                            StatusPill("Viewer control", WaveGreen)
                        }
                    }

                    PlaybackControls(
                        state = playback,
                        canControl = container.hostEngine.canControlPlayback() || state.phase == HostPhase.LIVE,
                        onToggle = { container.hostEngine.togglePlayback() },
                        onSeek = { container.hostEngine.seekPlaybackTo(it) },
                        onSpeed = { container.hostEngine.setPlaybackSpeed(it) },
                    )

                    SectionLabel("Invitation")
                    val publicShare = state.tunnelUrl
                    val lanShare = state.lanUrl
                    when {
                        publicShare != null -> {
                            ShareLinkBox(
                                label = "PUBLIC LINK · TUNNEL VERIFIED",
                                url = publicShare,
                                verified = true,
                            )
                            if (lanShare != null) {
                                ShareLinkBox(
                                    label = "LOCAL NETWORK ALTERNATIVE",
                                    url = lanShare,
                                )
                            }
                        }

                        lanShare != null -> {
                            ShareLinkBox(
                                label = "LOCAL NETWORK LINK",
                                url = lanShare,
                            )
                            if (state.tunnel.state != TunnelState.NOT_CONFIGURED) {
                                InfoBanner(
                                    message = "Tunnel ${state.tunnel.state.name.lowercase().replace('_', ' ')}: ${state.tunnel.message ?: "waiting for verification"}. Only the LAN link is shown until verification succeeds.",
                                    color = WaveAmber,
                                )
                            }
                        }

                        else -> {
                            InfoBanner(
                                message = "No LAN address was detected and no tunnel is verified. Connect to Wi-Fi or configure a tunnel before sharing.",
                                color = WaveAmber,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { confirmRotate = true }) {
                            Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Rotate invite")
                        }
                        OutlinedButton(onClick = onOpenTunnel) {
                            Icon(Icons.Filled.Cloud, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Tunnel")
                        }
                    }

                    SectionLabel("Room controls")
                    WaveCard {
                        ToggleRow(
                            title = "Allow viewer controls",
                            subtitle = "Play, pause, seek and speed changes from every participant",
                            checked = !state.controlsLocked,
                            onCheckedChange = { container.hostEngine.setControlsEnabled(it) },
                        )
                        Spacer(Modifier.height(10.dp))
                        ToggleRow(
                            title = "Allow new joins",
                            subtitle = "Existing participants stay connected when disabled",
                            checked = state.joinEnabled,
                            onCheckedChange = { container.hostEngine.setJoinEnabled(it) },
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Participant limit", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Currently ${connection.participants.size} connected",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Muted,
                                )
                            }
                            IconButton(
                                onClick = { container.hostEngine.setMaxParticipants((state.maxParticipants - 1).coerceAtLeast(2)) },
                                enabled = state.maxParticipants > 2,
                            ) { Icon(Icons.Filled.Remove, contentDescription = "Decrease limit") }
                            Text(
                                "${state.maxParticipants}",
                                style = MaterialTheme.typography.titleLarge,
                                color = WaveCyan,
                                modifier = Modifier.padding(horizontal = 6.dp),
                            )
                            IconButton(
                                onClick = { container.hostEngine.setMaxParticipants((state.maxParticipants + 1).coerceAtMost(50)) },
                                enabled = state.maxParticipants < 50,
                            ) { Icon(Icons.Filled.Add, contentDescription = "Increase limit") }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { confirmRotate = true }) { Text("Rotate link") }
                            Button(
                                onClick = { confirmEnd = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = WaveRed.copy(alpha = 0.16f),
                                    contentColor = WaveRed,
                                ),
                            ) {
                                Icon(Icons.Filled.StopCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("End party")
                            }
                        }
                    }

                    SectionLabel("Participants")
                    WaveCard {
                        if (connection.participants.isEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("Waiting for the first guest to join…", color = Muted)
                            }
                        } else {
                            connection.participants.forEachIndexed { index, participant ->
                                ParticipantRow(
                                    participant = participant,
                                    isSelf = participant.id == connection.self?.id,
                                    onKick = { container.hostEngine.kickParticipant(participant.id) },
                                )
                                if (index != connection.participants.lastIndex) {
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
                    }

                    SectionLabel("Chat")
                    WaveCard {
                        ChatPanel(
                            messages = chat,
                            selfId = connection.self?.id,
                            connected = connection.phase == ConnectionPhase.LIVE,
                            onSend = { container.hostEngine.sendChat(it) },
                        )
                    }

                    SectionLabel("Live upload")
                    WaveCard {
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            MetricTile("Active streams", "${stats.activeStreams}", Modifier.weight(1f))
                            MetricTile("Sent", formatBytes(stats.bytesSent), Modifier.weight(1f))
                            MetricTile("Average", formatBitrate(stats.approximateBitrateBps), Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            MetricTile("Streams opened", "${stats.totalStreamsStarted}", Modifier.weight(1f))
                            LabeledValue("Server port", "${state.serverPort ?: "—"}")
                            LabeledValue("Expires", formatClockTime(state.expiresAt ?: 0L))
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "These counters reflect bytes actually written to viewers. Original file only; no transcoding or re-encoding.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Muted,
                        )
                    }

                    SectionLabel("Room identity")
                    WaveCard {
                        LabeledValue("Room ID", state.roomId ?: "—")
                        Spacer(Modifier.height(8.dp))
                        LabeledValue("Tunnel", "${state.tunnel.provider} · ${state.tunnel.state.name}")
                        Spacer(Modifier.height(8.dp))
                        Text(
                            state.roomId ?: "",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            color = Muted,
                        )
                    }
                }
            }
        }
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("End this watch party?") },
            text = { Text("Every viewer is disconnected, all invitation links stop working, and the media server and tunnel are shut down.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmEnd = false
                        scope.launch {
                            container.hostEngine.stop()
                            onEnded()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaveRed, contentColor = Color(0xFF2B0808)),
                ) { Text("End party") }
            },
            dismissButton = {
                TextButton(onClick = { confirmEnd = false }) { Text("Cancel") }
            },
        )
    }

    if (confirmRotate) {
        AlertDialog(
            onDismissRequest = { confirmRotate = false },
            title = { Text("Rotate the invitation?") },
            text = { Text("The current link stops working immediately. Connected participants stay in the room, but anyone with the old link can no longer join.") },
            confirmButton = {
                Button(onClick = {
                    confirmRotate = false
                    container.hostEngine.revokeInvite()
                }) { Text("Rotate link") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRotate = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (checked) WaveGreen.copy(alpha = 0.14f) else SurfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (checked) Icons.Filled.LockOpen else Icons.Filled.Lock,
                contentDescription = null,
                tint = if (checked) WaveGreen else Muted,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Muted)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ParticipantRow(
    participant: ParticipantDto,
    isSelf: Boolean,
    onKick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(Color(0xFF0C121C))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConnectedDot(connected = participant.connected)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (participant.isHost) "${participant.displayName} (host)" else participant.displayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when {
                    isSelf -> "This device"
                    participant.connected -> "Connected"
                    else -> "Disconnected"
                },
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
        }
        if (participant.isHost) {
            StatusPill("HOST", WaveViolet)
        } else if (!isSelf) {
            IconButton(onClick = onKick) {
                Icon(
                    Icons.Filled.PersonRemove,
                    contentDescription = "Remove ${participant.displayName}",
                    tint = WaveRed.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
