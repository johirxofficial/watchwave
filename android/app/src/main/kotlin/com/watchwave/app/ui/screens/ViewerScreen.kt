package com.watchwave.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watchwave.app.AppContainer
import com.watchwave.app.net.ConnectionPhase
import com.watchwave.app.ui.components.ChatPanel
import com.watchwave.app.ui.components.ConnectedDot
import com.watchwave.app.ui.components.ErrorBanner
import com.watchwave.app.ui.components.InfoBanner
import com.watchwave.app.ui.components.LabeledValue
import com.watchwave.app.ui.components.PlaybackControls
import com.watchwave.app.ui.components.PlayerPlaceholder
import com.watchwave.app.ui.components.ScreenScaffold
import com.watchwave.app.ui.components.SectionLabel
import com.watchwave.app.ui.components.StatusPill
import com.watchwave.app.ui.components.SyncStatusBadge
import com.watchwave.app.ui.components.VideoSurface
import com.watchwave.app.ui.components.WaveCard
import com.watchwave.app.ui.components.formatDuration
import com.watchwave.app.ui.theme.Muted
import com.watchwave.app.ui.theme.WaveAmber
import com.watchwave.app.ui.theme.WaveCyan
import com.watchwave.app.ui.theme.WaveGreen
import com.watchwave.app.ui.theme.WaveRed
import com.watchwave.app.viewer.ViewerPhase
import kotlinx.coroutines.launch

@Composable
fun ViewerScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onLeft: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val state by container.viewerEngine.state.collectAsStateWithLifecycle()
    val connection by container.viewerEngine.connection.collectAsStateWithLifecycle()
    val playback by container.viewerEngine.localPlayback.collectAsStateWithLifecycle()
    val chat by container.viewerEngine.chat.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var confirmLeave by remember { mutableStateOf(false) }

    when (state.phase) {
        ViewerPhase.IDLE -> {
            ScreenScaffold(title = "Viewer", onBack = onBack) { padding ->
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("You are not watching a room", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Open an invitation link to join a synchronised watch party.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                    )
                }
            }
        }

        ViewerPhase.JOINING, ViewerPhase.LEAVING -> {
            ScreenScaffold(title = "Viewer", onBack = onBack) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (state.phase == ViewerPhase.JOINING) "Joining the room…" else "Leaving…",
                        color = Muted,
                    )
                }
            }
        }

        ViewerPhase.FAILED -> {
            ScreenScaffold(title = "Viewer", onBack = onBack) { padding ->
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    state.error?.let { ErrorBanner(message = it) }
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = onBack) { Text("Back to home") }
                }
            }
        }

        ViewerPhase.LIVE -> {
            val room = state.room
            ScreenScaffold(
                title = room?.roomName ?: "Watching",
                subtitle = room?.media?.title ?: state.displayName ?: "Viewer",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onOpenDiagnostics) {
                        Icon(Icons.Filled.Sensors, contentDescription = "Diagnostics")
                    }
                    IconButton(onClick = { confirmLeave = true }) {
                        Icon(Icons.Filled.Logout, contentDescription = "Leave room")
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
                                title = "Buffering the host's stream",
                                subtitle = "Waiting for the first range response",
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
                            hostOffline = connection.hostOffline,
                            message = connection.message ?: "Connecting",
                        )
                        StatusPill(
                            "${connection.participants.size} watching",
                            WaveCyan,
                            icon = {
                                Icon(
                                    Icons.Filled.People,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = WaveCyan,
                                )
                            },
                        )
                        Spacer(Modifier.weight(1f))
                        if (!connection.canControl) {
                            StatusPill("Host controls locked", WaveAmber)
                        }
                    }

                    if (connection.hostOffline) {
                        InfoBanner(
                            message = "The host connection dropped. Playback may continue briefly from the buffer, but controls will queue until the socket reconnects.",
                            color = WaveAmber,
                        )
                    }
                    if (connection.phase == ConnectionPhase.RECONNECTING) {
                        InfoBanner(
                            message = "Reconnecting to the host. The player keeps its position and will re-sync automatically.",
                            color = WaveAmber,
                        )
                    }
                    if (connection.phase == ConnectionPhase.ENDED || connection.phase == ConnectionPhase.FAILED) {
                        ErrorBanner(message = connection.message ?: "The host ended this room.")
                    }
                    connection.message
                        ?.takeIf { connection.errorCode != null && connection.phase == ConnectionPhase.LIVE }
                        ?.let { message -> ErrorBanner(message = message) }

                    PlaybackControls(
                        state = playback,
                        canControl = container.viewerEngine.canControlPlayback(),
                        onToggle = { container.viewerEngine.togglePlayback() },
                        onSeek = { container.viewerEngine.seekPlaybackTo(it) },
                        onSpeed = { container.viewerEngine.setPlaybackSpeed(it) },
                    )

                    SectionLabel("Room")
                    WaveCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(WaveCyan.copy(alpha = 0.13f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = WaveCyan)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    room?.media?.title ?: "Video",
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "Hosted by ${room?.hostName ?: "host"} · ${formatDuration(room?.media?.durationMs ?: 0L)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Muted,
                                )
                            }
                            if (!connection.hostOffline && connection.phase == ConnectionPhase.LIVE) {
                                StatusPill("LIVE", WaveGreen)
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            LabeledValue("Your name", state.displayName ?: "Viewer")
                            LabeledValue("Role", if (connection.isHost) "Host" else "Viewer")
                            LabeledValue("Port", (state.baseUrl ?: "").substringAfterLast(':').ifBlank { "—" })
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "The player fetches the host's original file with HTTP byte-range requests. Media tokens are refreshed in the background.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Muted,
                        )
                    }

                    SectionLabel("Participants")
                    WaveCard {
                        if (connection.participants.isEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Sync, contentDescription = null, tint = Muted, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text("Waiting for the participant list…", color = Muted)
                            }
                        } else {
                            connection.participants.forEachIndexed { index, participant ->
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
                                    Text(
                                        text = if (participant.isHost) "${participant.displayName} (host)" else participant.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (participant.id == connection.self?.id) {
                                        StatusPill("YOU", WaveCyan)
                                    }
                                }
                                if (index != connection.participants.lastIndex) Spacer(Modifier.height(8.dp))
                            }
                        }
                    }

                    SectionLabel("Chat")
                    WaveCard {
                        ChatPanel(
                            messages = chat,
                            selfId = connection.self?.id,
                            connected = connection.phase == ConnectionPhase.LIVE,
                            onSend = { container.viewerEngine.sendChat(it) },
                        )
                    }

                    OutlinedButton(
                        onClick = { confirmLeave = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Leave this room")
                    }
                }
            }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave the room?") },
            text = { Text("Playback stops and your participant slot is released. You can rejoin with the same invitation link while the host is live.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmLeave = false
                        scope.launch {
                            container.viewerEngine.leave()
                            onLeft()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaveRed, contentColor = Color(0xFF2B0808)),
                ) { Text("Leave") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text("Stay") }
            },
        )
    }
}
