package com.watchwave.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.watchwave.app.ui.components.EmptyState
import com.watchwave.app.ui.components.ScreenScaffold
import com.watchwave.app.ui.components.StatusPill
import com.watchwave.app.ui.components.WaveCard
import com.watchwave.app.ui.components.formatClockTime
import com.watchwave.app.ui.components.formatDuration
import com.watchwave.app.ui.components.formatRelative
import com.watchwave.app.ui.theme.Muted
import com.watchwave.app.ui.theme.WaveCyan
import com.watchwave.app.ui.theme.WaveGreen
import com.watchwave.app.ui.theme.WaveRed
import com.watchwave.app.ui.theme.WaveViolet
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(container: AppContainer) {
    val history by remember(container) { container.mediaRepository.roomHistory() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = "History",
        subtitle = "${history.size} room${if (history.size == 1) "" else "s"} recorded on this device",
        actions = {
            if (history.isNotEmpty()) {
                IconButton(onClick = { confirmClear = true }) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear history")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (history.isEmpty()) {
                item {
                    EmptyState(
                        title = "No room history",
                        body = "Hosted and joined rooms are recorded with their start time, duration and role. History never contains invitation or session tokens.",
                    )
                }
            } else {
                items(history, key = { it.id }) { entry ->
                    val isHost = entry.role.equals("host", ignoreCase = true)
                    WaveCard(
                        contentPadding = PaddingValues(14.dp),
                        accent = if (isHost) WaveCyan.copy(alpha = 0.26f) else WaveViolet.copy(alpha = 0.26f),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isHost) WaveCyan.copy(alpha = 0.12f) else WaveViolet.copy(alpha = 0.12f),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    if (isHost) Icons.Filled.PlayArrow else Icons.Filled.Visibility,
                                    contentDescription = null,
                                    tint = if (isHost) WaveCyan else WaveViolet,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        entry.roomName,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    StatusPill(if (isHost) "HOSTED" else "VIEWED", if (isHost) WaveCyan else WaveViolet)
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    entry.videoTitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    buildString {
                                        append(formatClockTime(entry.startedAt))
                                        entry.endedAt?.let { ended ->
                                            append(" · ")
                                            append(formatDuration(ended - entry.startedAt))
                                        } ?: append(" · open")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Muted,
                                )
                            }
                            if (entry.endedAt == null) {
                                StatusPill("LIVE", WaveGreen)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(6.dp))
                Text(
                    "History is stored in the local Room database. Clearing it does not affect rooms that are currently live.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear room history?") },
            text = { Text("This removes all local history rows. It does not stop a live room or invalidate any invitation token.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmClear = false
                        scope.launch { container.mediaRepository.clearRoomHistory() }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaveRed, contentColor = Color(0xFF2B0808)),
                ) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            },
        )
    }
}
