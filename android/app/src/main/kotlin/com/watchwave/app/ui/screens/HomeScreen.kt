package com.watchwave.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watchwave.app.AppContainer
import com.watchwave.app.host.HostPhase
import com.watchwave.app.ui.components.BrandMark
import com.watchwave.app.ui.components.EmptyState
import com.watchwave.app.ui.components.ResponsiveColumn
import com.watchwave.app.ui.components.ScreenScaffold
import com.watchwave.app.ui.components.SectionLabel
import com.watchwave.app.ui.components.ShareLinkBox
import com.watchwave.app.ui.components.StatusPill
import com.watchwave.app.ui.components.WaveCard
import com.watchwave.app.ui.components.formatDuration
import com.watchwave.app.ui.components.formatRelative
import com.watchwave.app.ui.theme.Muted
import com.watchwave.app.ui.theme.OutlineSoft
import com.watchwave.app.ui.theme.Soft
import com.watchwave.app.ui.theme.Surface
import com.watchwave.app.ui.theme.SurfaceHigh
import com.watchwave.app.ui.theme.WaveCyan
import com.watchwave.app.ui.theme.WaveGreen
import com.watchwave.app.ui.theme.WaveTeal

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
        ResponsiveColumn(modifier = Modifier.padding(padding)) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 28.dp),
            ) {
                BrandMark(modifier = Modifier.padding(top = 6.dp, bottom = 20.dp))

                HeroPanel()

                if (hostState.phase == HostPhase.LIVE || hostState.phase == HostPhase.STARTING) {
                    Spacer(Modifier.height(18.dp))
                    LiveHostCard(
                        roomName = hostState.roomName ?: "Watch party",
                        mediaTitle = hostState.mediaTitle ?: "Video",
                        participantCount = hostConnection.participants.size,
                        onOpen = onResumeHost,
                    )
                }

                if (viewerState.phase == com.watchwave.app.viewer.ViewerPhase.LIVE) {
                    Spacer(Modifier.height(18.dp))
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
                SectionLabel("Start a room")
                Spacer(Modifier.height(10.dp))

                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val wide = maxWidth >= 560.dp
                    if (wide) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            QuickActionCard(
                                modifier = Modifier.weight(1f),
                                accent = WaveCyan,
                                icon = Icons.Filled.Add,
                                title = "Host a party",
                                body = "Pick a video from this phone and share one link.",
                                action = "Choose a video",
                                onClick = onHost,
                            )
                            QuickActionCard(
                                modifier = Modifier.weight(1f),
                                accent = WaveTeal,
                                icon = Icons.Filled.Link,
                                title = "Join a room",
                                body = "Paste the invite link your host sent you.",
                                action = "Enter invite link",
                                onClick = onJoin,
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            QuickActionCard(
                                modifier = Modifier.fillMaxWidth(),
                                accent = WaveCyan,
                                icon = Icons.Filled.Add,
                                title = "Host a party",
                                body = "Pick a video from this phone and share one link.",
                                action = "Choose a video",
                                onClick = onHost,
                            )
                            QuickActionCard(
                                modifier = Modifier.fillMaxWidth(),
                                accent = WaveTeal,
                                icon = Icons.Filled.Link,
                                title = "Join a room",
                                body = "Paste the invite link your host sent you.",
                                action = "Enter invite link",
                                onClick = onJoin,
                            )
                        }
                    }
                }

                if (hostState.phase == HostPhase.LIVE && hostState.shareUrl != null) {
                    Spacer(Modifier.height(18.dp))
                    ShareLinkBox(
                        label = if (hostState.tunnelUrl != null) "PUBLIC LINK · TUNNEL VERIFIED" else "LOCAL NETWORK LINK",
                        url = hostState.shareUrl.orEmpty(),
                        verified = hostState.tunnelUrl != null,
                    )
                }

                Spacer(Modifier.height(26.dp))
                SectionLabel("How it works")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StepChip("01", "Pick a file")
                    StepChip("02", "Share a link")
                    StepChip("03", "Stay in sync")
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Videos never leave your phone. WatchWave streams the original file over HTTP Range " +
                        "requests and keeps every viewer on the same frame over WebSocket.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
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

                Spacer(Modifier.height(22.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(SurfaceHigh.copy(alpha = 0.7f))
                        .border(1.dp, OutlineSoft, MaterialTheme.shapes.medium)
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
                    "WatchWave 1.1.0 · Open source · No accounts · No media uploads",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted.copy(alpha = 0.8f),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun HeroPanel() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF121A2A), Color(0xFF0B1220), Surface),
                ),
            )
            .border(1.dp, WaveCyan.copy(alpha = 0.18f), RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 22.dp),
    ) {
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill("OPEN SOURCE · MIT", WaveTeal)
                StatusPill("ORIGINAL QUALITY", WaveCyan)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = buildAnnotatedString {
                    append("Press play.\n")
                    withStyle(
                        SpanStyle(
                            brush = Brush.linearGradient(listOf(WaveCyan, WaveTeal)),
                        ),
                    ) {
                        append("Everyone follows.")
                    }
                },
                style = MaterialTheme.typography.displaySmall,
                color = Color(0xFFF5F8FF),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "A watch party for the videos already on your phone. Add a file, share one " +
                    "link, and keep every screen on the same frame — with chat for the whole room.",
                style = MaterialTheme.typography.bodyLarge,
                color = Soft,
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    modifier: Modifier,
    accent: Color,
    icon: ImageVector,
    title: String,
    body: String,
    action: String,
    onClick: () -> Unit,
) {
    WaveCard(
        modifier = modifier,
        accent = accent.copy(alpha = 0.4f),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccentIcon(accent, icon)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(3.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accent,
                contentColor = if (accent == WaveTeal) Color(0xFF04241F) else Color(0xFF04162E),
            ),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
            Text(action)
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
        Button(
            onClick = onOpen,
            colors = ButtonDefaults.buttonColors(containerColor = WaveGreen, contentColor = Color(0xFF052016)),
        ) {
            Text("Open host dashboard")
        }
    }
}

@Composable
private fun StepChip(number: String, title: String) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(SurfaceHigh.copy(alpha = 0.8f))
            .border(1.dp, OutlineSoft, CircleShape)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            number,
            style = MaterialTheme.typography.labelSmall,
            color = WaveCyan,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
        Text(title, style = MaterialTheme.typography.labelMedium, color = Soft, maxLines = 1)
    }
}

@Composable
private fun AccentIcon(color: Color, icon: ImageVector) {
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
