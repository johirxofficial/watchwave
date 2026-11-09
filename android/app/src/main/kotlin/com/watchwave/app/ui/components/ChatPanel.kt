package com.watchwave.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.watchwave.app.ui.theme.Muted
import com.watchwave.app.ui.theme.SurfaceRaised
import com.watchwave.app.ui.theme.WaveAmber
import com.watchwave.app.ui.theme.WaveCyan
import com.watchwave.shared.protocol.CHAT_MAX_LENGTH
import com.watchwave.shared.protocol.ChatMessageDto
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Compact, friendly room chat shared by the host and viewer dashboards.
 *
 * The server owns sender identity, message ids and timestamps. This panel only renders
 * server messages and sends trimmed text, so nobody can spoof another participant.
 */
@Composable
fun ChatPanel(
    messages: List<ChatMessageDto>,
    selfId: String?,
    connected: Boolean,
    onSend: (String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            runCatching { listState.animateScrollToItem(messages.lastIndex) }
        }
    }

    fun submit() {
        if (!connected) return
        val text = draft.trim()
        if (text.isEmpty()) return
        if (onSend(text)) draft = ""
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Room chat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text(
                if (messages.isEmpty()) "Say hello" else "${messages.size} messages",
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
            Spacer(Modifier.weight(1f))
            StatusPill(
                if (connected) "LIVE" else "OFFLINE",
                if (connected) WaveCyan else WaveAmber,
            )
        }
        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp, max = 260.dp)
                .background(Color(0xFF0C121C), RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            if (messages.isEmpty()) {
                Text(
                    "No messages yet. Chat about the movie, share a reaction, or say hi to the room.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(messages.size) { index ->
                        val message = messages[index]
                        ChatBubble(
                            message = message,
                            mine = message.participantId == selfId,
                            time = timeFormat.format(Date(message.sentAt)),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = draft,
                onValueChange = { if (it.length <= CHAT_MAX_LENGTH) draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(if (connected) "Message the room" else "Reconnecting…") },
                enabled = connected,
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = { submit() },
                enabled = connected && draft.isNotBlank(),
                modifier = Modifier.size(52.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = WaveCyan,
                    contentColor = Color(0xFF04222A),
                ),
            ) {
                Icon(Icons.Filled.Send, contentDescription = "Send message")
            }
        }
        if (draft.length > CHAT_MAX_LENGTH - 100) {
            Spacer(Modifier.height(4.dp))
            Text(
                "${draft.length}/$CHAT_MAX_LENGTH",
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessageDto, mine: Boolean, time: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 290.dp),
        ) {
            if (!mine) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        message.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (message.isHost) WaveAmber else WaveCyan,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (message.isHost) {
                        Spacer(Modifier.width(5.dp))
                        Text("HOST", style = MaterialTheme.typography.labelSmall, color = WaveAmber)
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
            Box(
                modifier = Modifier
                    .background(
                        if (mine) WaveCyan.copy(alpha = 0.16f) else SurfaceRaised,
                        RoundedCornerShape(14.dp),
                    )
                    .padding(horizontal = 11.dp, vertical = 8.dp),
            ) {
                Text(
                    message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFE8F0FA),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                if (mine) "You · $time" else time,
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
        }
    }
}
