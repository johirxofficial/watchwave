package com.watchwave.app.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.watchwave.app.net.LocalPlaybackState
import com.watchwave.app.ui.theme.Muted
import com.watchwave.app.ui.theme.WaveCyan
import com.watchwave.app.ui.theme.WaveGreen

@Composable
fun VideoSurface(
    player: Player,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                keepScreenOn = true
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(AndroidColor.BLACK)
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                this.player = player
            }
        },
        update = { view -> view.player = player },
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black),
    )
}

/**
 * Shared transport bar for host and viewer screens. Local dragging is kept separate
 * from the authoritative clock so the thumb does not fight updates from the host.
 */
@Composable
fun PlaybackControls(
    state: LocalPlaybackState,
    canControl: Boolean,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeed: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val duration = state.durationMs.coerceAtLeast(1L)
    var dragValue by remember { mutableStateOf<Float?>(null) }
    var speedMenu by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = dragValue ?: (state.positionMs.toFloat() / duration).coerceIn(0f, 1f),
            onValueChange = { value -> if (canControl) dragValue = value },
            onValueChangeFinished = {
                dragValue?.let { value -> onSeek((value * duration).toLong()) }
                dragValue = null
            },
            enabled = canControl && state.hasMedia,
            colors = SliderDefaults.colors(
                thumbColor = WaveCyan,
                activeTrackColor = WaveCyan,
                inactiveTrackColor = Color(0xFF2A3446),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                formatDuration(dragValue?.let { (it * duration).toLong() } ?: state.positionMs),
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFD6DEEB),
            )
            Text(
                " / ${formatDuration(state.durationMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = Muted,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${String.format(java.util.Locale.US, "%.2f", state.speed)}×",
                style = MaterialTheme.typography.labelMedium,
                color = Muted,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            IconButton(
                onClick = { onSeek((state.positionMs - 10_000L).coerceAtLeast(0L)) },
                enabled = canControl && state.hasMedia,
            ) {
                Icon(Icons.Filled.Replay10, contentDescription = "Back 10 seconds")
            }
            Spacer(Modifier.width(18.dp))
            FilledIconButton(
                onClick = onToggle,
                enabled = canControl && state.hasMedia,
                modifier = Modifier.size(58.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = WaveCyan,
                    contentColor = Color(0xFF04222A),
                    disabledContainerColor = Color(0xFF2A3446),
                    disabledContentColor = Muted,
                ),
            ) {
                Icon(
                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(Modifier.width(18.dp))
            IconButton(
                onClick = { onSeek((state.positionMs + 10_000L).coerceAtMost(duration)) },
                enabled = canControl && state.hasMedia,
            ) {
                Icon(Icons.Filled.Forward10, contentDescription = "Forward 10 seconds")
            }
            Spacer(Modifier.width(10.dp))
            Box {
                IconButton(
                    onClick = { speedMenu = true },
                    enabled = canControl && state.hasMedia,
                ) {
                    Icon(Icons.Filled.Speed, contentDescription = "Playback speed")
                }
                DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                        DropdownMenuItem(
                            text = { Text("${speed}×") },
                            onClick = {
                                speedMenu = false
                                onSpeed(speed)
                            },
                            leadingIcon = if (kotlin.math.abs(state.speed - speed) < 0.01f) {
                                { Icon(Icons.Filled.Sync, contentDescription = null, tint = WaveCyan) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
        if (!canControl) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = Muted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Only the host can control playback right now",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                )
            }
        }
    }
}

@Composable
fun SyncStatusBadge(
    connected: Boolean,
    hostOffline: Boolean,
    message: String?,
    modifier: Modifier = Modifier,
) {
    val color = when {
        hostOffline -> Color(0xFFFBBF24)
        connected -> WaveGreen
        else -> Color(0xFFF87171)
    }
    val label = when {
        hostOffline -> "Host offline"
        connected -> "In sync"
        else -> message ?: "Connecting"
    }
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun PlayerPlaceholder(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF0B1220), Color(0xFF121B2B), Color(0xFF0B1220)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(WaveCyan.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = WaveCyan,
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Muted)
        }
    }
}
