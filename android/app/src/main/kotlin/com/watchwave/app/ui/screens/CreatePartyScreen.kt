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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.watchwave.app.ui.components.ErrorBanner
import com.watchwave.app.ui.components.InfoBanner
import com.watchwave.app.ui.components.LabeledValue
import com.watchwave.app.ui.components.ScreenScaffold
import com.watchwave.app.ui.components.SectionLabel
import com.watchwave.app.ui.components.StatusPill
import com.watchwave.app.ui.components.WaveCard
import com.watchwave.app.ui.components.formatBytes
import com.watchwave.app.ui.components.formatDuration
import com.watchwave.app.ui.theme.Muted
import com.watchwave.app.ui.theme.SurfaceRaised
import com.watchwave.app.ui.theme.WaveAmber
import com.watchwave.app.ui.theme.WaveCyan
import com.watchwave.app.ui.theme.WaveGreen
import com.watchwave.app.ui.theme.WaveViolet
import com.watchwave.data.settings.TunnelMode
import kotlinx.coroutines.launch

@Composable
fun CreatePartyScreen(
    container: AppContainer,
    videoUri: String,
    onBack: () -> Unit,
    onStarted: () -> Unit,
    onOpenTunnel: () -> Unit,
) {
    val settings by container.settings.collectAsStateWithLifecycle()
    val video by remember(container, videoUri) { container.mediaRepository.observeVideo(videoUri) }
        .collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()

    var roomName by remember { mutableStateOf("Watch Party") }
    var hostName by remember(settings.displayName) { mutableStateOf(settings.displayName) }
    var maxParticipants by remember(settings.maxParticipants) { mutableStateOf(settings.maxParticipants) }
    var ttlHours by remember(settings.roomTtlMinutes) { mutableStateOf((settings.roomTtlMinutes / 60).coerceAtLeast(1)) }
    var allowControls by remember { mutableStateOf(true) }
    var starting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    ScreenScaffold(
        title = "Create party",
        subtitle = "Prepare the room and share one link",
        onBack = onBack,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (video == null) {
                InfoBanner("This video is no longer available. Return to the library and choose it again.", color = WaveAmber)
            } else {
                val item = video!!
                WaveCard(accent = WaveCyan.copy(alpha = 0.35f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(WaveCyan.copy(alpha = 0.13f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = WaveCyan)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${formatDuration(item.durationMs)} · ${formatBytes(item.sizeBytes)} · ${item.mimeType ?: "video"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Muted,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill("Original file", WaveGreen)
                        StatusPill("No transcoding", WaveViolet)
                    }
                }
            }

            errorMessage?.let { ErrorBanner(message = it, onDismiss = { errorMessage = null }) }

            SectionLabel("Room details")
            WaveCard {
                OutlinedTextField(
                    value = roomName,
                    onValueChange = { if (it.length <= 48) roomName = it },
                    label = { Text("Room name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = hostName,
                    onValueChange = { if (it.length <= 32) hostName = it },
                    label = { Text("Your display name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Maximum participants", style = MaterialTheme.typography.titleMedium)
                        Text("Host included · 2 to 50", style = MaterialTheme.typography.labelSmall, color = Muted)
                    }
                    IconButton(
                        onClick = { maxParticipants = (maxParticipants - 1).coerceAtLeast(2) },
                        enabled = maxParticipants > 2,
                    ) { Icon(Icons.Filled.Remove, contentDescription = "Fewer participants") }
                    Text(
                        "$maxParticipants",
                        style = MaterialTheme.typography.titleLarge,
                        color = WaveCyan,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                    IconButton(
                        onClick = { maxParticipants = (maxParticipants + 1).coerceAtMost(50) },
                        enabled = maxParticipants < 50,
                    ) { Icon(Icons.Filled.Add, contentDescription = "More participants") }
                }
                Spacer(Modifier.height(14.dp))
                Text("Room lifetime", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 4, 6, 12, 24).forEach { hours ->
                        FilterChip(
                            selected = ttlHours == hours,
                            onClick = { ttlHours = hours },
                            label = { Text("${hours}h") },
                        )
                    }
                }
            }

            SectionLabel("Playback policy")
            WaveCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(WaveViolet.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (allowControls) Icons.Filled.Link else Icons.Filled.Lock,
                            contentDescription = null,
                            tint = WaveViolet,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Allow viewer controls", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Everyone can play, pause, seek and change speed. You can lock it later.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Muted,
                        )
                    }
                    Switch(checked = allowControls, onCheckedChange = { allowControls = it })
                }
            }

            SectionLabel("Reachability")
            WaveCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Cloud, contentDescription = null, tint = WaveCyan)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tunnelLabel(settings.tunnelMode), style = MaterialTheme.typography.titleMedium)
                        Text(
                            tunnelDescription(settings.tunnelMode),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Muted,
                        )
                    }
                    OutlinedButton(onClick = onOpenTunnel) { Text("Configure") }
                }
                Spacer(Modifier.height(12.dp))
                InfoBanner(
                    message = "LAN viewing works only while everyone is on the same Wi-Fi. A Cloudflare link is shown only after the host verifies it is reachable.",
                    color = WaveAmber,
                )
            }

            Spacer(Modifier.height(6.dp))
            Button(
                onClick = {
                    val item = video ?: return@Button
                    errorMessage = null
                    starting = true
                    scope.launch {
                        val effective = settings.copy(
                            displayName = hostName.trim().ifBlank { "Host" },
                            maxParticipants = maxParticipants,
                            roomTtlMinutes = ttlHours * 60,
                        )
                        val result = container.hostEngine.start(
                            video = item,
                            settings = effective,
                            roomName = roomName.trim().ifBlank { "Watch Party" },
                            allowViewerControls = allowControls,
                        )
                        starting = false
                        result.onSuccess {
                            runCatching { container.settingsRepository.setDisplayName(effective.displayName) }
                            onStarted()
                        }.onFailure { error ->
                            errorMessage = error.message ?: "Could not start the room."
                        }
                    }
                },
                enabled = video != null && !starting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                if (starting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF04222A))
                    Spacer(Modifier.width(10.dp))
                    Text("Starting the server…")
                } else {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Start watch party")
                }
            }
            Text(
                "The server binds to 0.0.0.0 and streams the selected file with byte-range requests. Keep this app installed and the file accessible for the room's lifetime.",
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
        }
    }
}

private fun tunnelLabel(mode: TunnelMode): String = when (mode) {
    TunnelMode.NONE -> "Local network only"
    TunnelMode.ON_DEVICE -> "Instant public link"
    TunnelMode.COMPANION_URL -> "Companion Cloudflare URL"
    TunnelMode.CLOUDFLARED_PROCESS -> "Custom cloudflared"
}

private fun tunnelDescription(mode: TunnelMode): String = when (mode) {
    TunnelMode.NONE -> "Share the device's LAN address. No public link will be shown."
    TunnelMode.ON_DEVICE -> "No setup. The bundled tunnel creates a verified HTTPS link when you start."
    TunnelMode.COMPANION_URL -> "Verify a URL forwarded by another machine or Termux."
    TunnelMode.CLOUDFLARED_PROCESS -> "Run a user-supplied cloudflared binary on this phone."
}
