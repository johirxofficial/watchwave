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
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watchwave.app.AppContainer
import com.watchwave.app.net.RoomApi
import com.watchwave.app.net.RoomApiException
import com.watchwave.app.net.RoomLink
import com.watchwave.app.net.RoomLinkException
import com.watchwave.app.net.RoomLinks
import com.watchwave.app.ui.components.ErrorBanner
import com.watchwave.app.ui.components.InfoBanner
import com.watchwave.app.ui.components.LabeledValue
import com.watchwave.app.ui.components.ScreenScaffold
import com.watchwave.app.ui.components.SectionLabel
import com.watchwave.app.ui.components.StatusPill
import com.watchwave.app.ui.components.WaveCard
import com.watchwave.app.ui.components.formatDuration
import com.watchwave.app.ui.theme.Muted
import com.watchwave.app.ui.theme.WaveCyan
import com.watchwave.app.ui.theme.WaveGreen
import com.watchwave.shared.protocol.RoomSummaryDto
import kotlinx.coroutines.launch

@Composable
fun JoinScreen(
    container: AppContainer,
    initialLink: String,
    onBack: () -> Unit,
    onJoined: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val settings by container.settings.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var link by remember(initialLink) { mutableStateOf(initialLink) }
    var displayName by remember(settings.displayName) { mutableStateOf(settings.displayName) }
    var preview by remember { mutableStateOf<RoomSummaryDto?>(null) }
    var previewError by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var joining by remember { mutableStateOf(false) }
    var joinError by remember { mutableStateOf<String?>(null) }

    fun checkLink() {
        val value = link.trim()
        if (value.isEmpty()) {
            previewError = "Paste an invitation link first."
            return
        }
        previewError = null
        joinError = null
        checking = true
        scope.launch {
            var api: RoomApi? = null
            try {
                val parsed = RoomLinks.parse(value)
                api = RoomApi(parsed.baseUrl)
                preview = api.room(parsed.roomId, parsed.inviteToken)
            } catch (error: RoomLinkException) {
                preview = null
                previewError = error.message
            } catch (error: RoomApiException) {
                preview = null
                previewError = error.message
            } catch (error: Exception) {
                preview = null
                previewError = error.message ?: "Could not reach that host."
            } finally {
                checking = false
                api?.close()
            }
        }
    }

    LaunchedEffect(initialLink) {
        if (initialLink.isNotBlank()) {
            link = initialLink
            checkLink()
        }
    }

    fun doJoin() {
        val value = link.trim()
        joinError = null
        joining = true
        scope.launch {
            try {
                val parsed: RoomLink = RoomLinks.parse(value)
                val result = container.viewerEngine.join(
                    link = parsed,
                    displayName = displayName.trim().ifBlank { "Viewer" },
                    settings = settings,
                )
                joining = false
                result.onSuccess {
                    runCatching { container.settingsRepository.setDisplayName(displayName.trim().ifBlank { "Viewer" }) }
                    onJoined()
                }.onFailure { error ->
                    joinError = error.message ?: "Could not join the room."
                }
            } catch (error: RoomLinkException) {
                joining = false
                joinError = error.message
            }
        }
    }

    ScreenScaffold(
        title = "Join a room",
        subtitle = "One link, real-range streaming",
        onBack = onBack,
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            WaveCard(accent = WaveCyan.copy(alpha = 0.32f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(WaveCyan.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Link, contentDescription = null, tint = WaveCyan)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Invitation link", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "https://host/room/ROOM_ID?t=TOKEN",
                            style = MaterialTheme.typography.labelSmall,
                            color = Muted,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = link,
                    onValueChange = {
                        link = it
                        preview = null
                        previewError = null
                    },
                    label = { Text("Paste the full link") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            val text = clipboard.getText()?.text.orEmpty()
                            if (text.isNotBlank()) {
                                link = text.trim()
                                preview = null
                                previewError = null
                            }
                        },
                    ) {
                        Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Paste")
                    }
                    OutlinedButton(onClick = { checkLink() }, enabled = !checking && link.isNotBlank()) {
                        if (checking) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.People, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(if (checking) "Checking…" else "Check link")
                    }
                }
            }

            previewError?.let { ErrorBanner(message = it) }
            joinError?.let { ErrorBanner(message = it, onDismiss = { joinError = null }) }

            preview?.let { room ->
                SectionLabel("Room preview")
                WaveCard(accent = WaveGreen.copy(alpha = 0.4f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                room.roomName,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "Hosted by ${room.hostName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Muted,
                            )
                        }
                        StatusPill(
                            if (room.canJoin) "OPEN" else "CLOSED",
                            if (room.canJoin) WaveGreen else MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        room.media?.title ?: "Video",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        LabeledValue("Duration", formatDuration(room.media?.durationMs ?: 0L))
                        LabeledValue("Participants", "${room.participantCount}/${room.maxParticipants}")
                        LabeledValue("Host", if (room.hostOnline) "Online" else "Offline")
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (room.controlsLocked) StatusPill("Host controls locked", MaterialTheme.colorScheme.tertiary)
                        else StatusPill("Everyone can control", WaveCyan)
                    }
                }
            }

            if (preview != null) {
                SectionLabel("You")
                WaveCard {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { if (it.length <= 32) displayName = it },
                        label = { Text("Display name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Joining creates a unique session token. It is kept in memory for this session only and never written to disk.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                    )
                }
            }

            Button(
                onClick = { doJoin() },
                enabled = !joining && link.isNotBlank() && (preview?.canJoin ?: true),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                if (joining) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF04222A),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Joining…")
                } else {
                    Icon(Icons.Filled.Login, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Join watch party")
                }
            }

            InfoBanner(
                message = "If this is a LAN link, both devices must be on the same network. Public links use HTTPS and are reachable anywhere.",
                color = WaveCyan,
            )

            Text(
                "Tip: open the invitation in a browser to watch without installing the app. The host serves the same React client to every guest.",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
            )
        }
    }
}
