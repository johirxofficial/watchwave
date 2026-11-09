package com.watchwave.app.net

import com.watchwave.shared.protocol.CHAT_HISTORY_LIMIT
import com.watchwave.shared.protocol.CHAT_MAX_LENGTH
import com.watchwave.shared.protocol.ChatMessageDto
import com.watchwave.shared.protocol.LeaveRequest
import com.watchwave.shared.protocol.MediaTokenResponse
import com.watchwave.shared.protocol.MessageType
import com.watchwave.shared.protocol.ParticipantDto
import com.watchwave.shared.protocol.SyncJson
import com.watchwave.shared.protocol.SyncMessage
import com.watchwave.shared.sync.ClockSync
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Room WebSocket + media-token lifecycle shared by the host and Android viewers.
 *
 * The server is authoritative. This client only:
 *  - keeps one live socket with heartbeats and exponential-backoff reconnects,
 *  - converts server frames into a small immutable UI state,
 *  - refreshes short-lived media tokens before they expire,
 *  - forwards user commands with command ids for idempotency.
 */
class RoomClient(
    baseUrl: String,
    val roomId: String,
    val sessionToken: String,
    initialMediaToken: String,
    mediaPath: String,
    wsPath: String,
    private val scope: CoroutineScope,
    private val autoReconnect: Boolean = true,
    private val logger: (String) -> Unit = {},
) {
    val baseUrl: String = baseUrl.trim().trimEnd('/')

    private val mediaPath: String = mediaPath.ifBlank { "/api/v1/rooms/$roomId/video" }
        .let { if (it.startsWith('/')) it else "/$it" }
    private val wsPath: String = wsPath.ifBlank { "/api/v1/rooms/$roomId/sync" }
        .let { if (it.startsWith('/')) it else "/$it" }

    @Volatile
    var mediaToken: String = initialMediaToken
        private set

    val mediaUrl: String get() = "$baseUrl$mediaPath?t=$mediaToken"

    private val http = HttpClient(io.ktor.client.engine.okhttp.OkHttp) {
        install(ContentNegotiation) { json(SyncJson.json) }
        install(WebSockets)
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000L
            connectTimeoutMillis = 10_000L
            socketTimeoutMillis = 20_000L
        }
    }

    private val _connection = MutableStateFlow(RoomConnectionState())
    val connection: StateFlow<RoomConnectionState> = _connection.asStateFlow()

    private val _playback = MutableStateFlow<AuthoritativePlayback?>(null)
    val playback: StateFlow<AuthoritativePlayback?> = _playback.asStateFlow()

    private val _serverOffset = MutableStateFlow(0L)
    val serverOffsetMs: StateFlow<Long> = _serverOffset.asStateFlow()

    private val _chat = MutableStateFlow<List<ChatMessageDto>>(emptyList())
    val chat: StateFlow<List<ChatMessageDto>> = _chat.asStateFlow()

    private val _events = MutableSharedFlow<SyncMessage>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<SyncMessage> = _events.asSharedFlow()

    private val closed = AtomicBoolean(false)
    private val sendMutex = Mutex()
    private val pendingPings = ConcurrentHashMap<String, Long>()

    @Volatile
    private var socket: DefaultClientWebSocketSession? = null

    private var connectJob: Job? = null
    private var tokenJob: Job? = null

    fun serverNow(): Long = System.currentTimeMillis() + _serverOffset.value

    fun connect() {
        if (connectJob?.isActive == true) return
        closed.set(false)
        connectJob = scope.launch { runConnectionLoop() }
        tokenJob = scope.launch {
            while (currentCoroutineContext().isActive && !closed.get()) {
                delay(TOKEN_REFRESH_MS)
                runCatching { refreshMediaToken() }
                    .onFailure { logger("Media token refresh failed: ${it::class.simpleName}") }
            }
        }
    }

    fun play(positionMs: Long, speed: Float): Boolean =
        sendCommand(command(MessageType.PLAY, positionMs = positionMs, speed = speed))

    fun pause(positionMs: Long, speed: Float): Boolean =
        sendCommand(command(MessageType.PAUSE, positionMs = positionMs, speed = speed))

    fun seek(positionMs: Long, speed: Float): Boolean =
        sendCommand(command(MessageType.SEEK, positionMs = positionMs, speed = speed))

    fun setSpeed(speed: Float, positionMs: Long): Boolean =
        sendCommand(command(MessageType.SET_SPEED, positionMs = positionMs, speed = speed))

    fun ended(positionMs: Long): Boolean =
        sendCommand(command(MessageType.ENDED, positionMs = positionMs, speed = 1f))

    /**
     * Chat is available to every participant, regardless of playback control policy.
     * The server remains authoritative about sender identity and timestamps.
     */
    fun sendChat(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > CHAT_MAX_LENGTH) return false
        if (closed.get() || _connection.value.phase != ConnectionPhase.LIVE) return false
        scope.launch {
            val session = socket ?: return@launch
            runCatching {
                sendFrame(
                    session,
                    SyncMessage(
                        type = MessageType.CHAT_SEND,
                        roomId = roomId,
                        text = trimmed,
                        clientTimestamp = System.currentTimeMillis(),
                    ),
                )
            }
        }
        return true
    }

    suspend fun refreshMediaToken(): Boolean {
        if (closed.get()) return false
        val response = http.post("$baseUrl/api/v1/rooms/$roomId/media-token") {
            header(HttpHeaders.Authorization, "Bearer $sessionToken")
        }
        if (!response.status.isSuccess()) {
            logger("Media token refresh rejected with HTTP ${response.status.value}")
            return false
        }
        val payload = response.body<MediaTokenResponse>()
        mediaToken = payload.mediaToken
        return true
    }

    suspend fun leave() {
        runCatching {
            http.post("$baseUrl/api/v1/rooms/$roomId/leave") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $sessionToken")
                setBody(LeaveRequest(sessionToken))
            }
        }
        close()
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        connectJob?.cancel()
        connectJob = null
        tokenJob?.cancel()
        tokenJob = null
        socket = null
        runCatching { http.close() }
        _chat.value = emptyList()
        _connection.update { it.copy(phase = ConnectionPhase.ENDED) }
    }

    // -----------------------------------------------------------------------
    // Connection lifecycle
    // -----------------------------------------------------------------------

    private suspend fun runConnectionLoop() {
        var consecutiveFailures = 0
        while (!closed.get() && (autoReconnect || consecutiveFailures == 0)) {
            _connection.update {
                it.copy(
                    phase = if (consecutiveFailures == 0) ConnectionPhase.CONNECTING else ConnectionPhase.RECONNECTING,
                    message = if (consecutiveFailures == 0) "Connecting to the host…" else "Reconnecting…",
                )
            }
            try {
                http.webSocket(wsUrl()) {
                    socket = this
                    consecutiveFailures = 0
                    _connection.update {
                        it.copy(phase = ConnectionPhase.LIVE, message = null, errorCode = null)
                    }
                    val heartbeat = launch { heartbeatLoop(this@webSocket) }
                    try {
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            val message = runCatching { SyncJson.decode(frame.readText()) }.getOrNull() ?: continue
                            handleMessage(message, this)
                        }
                    } finally {
                        heartbeat.cancel()
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                logger("WebSocket error: ${error::class.simpleName}")
            } finally {
                socket = null
            }

            if (closed.get()) break
            consecutiveFailures += 1
            if (!autoReconnect || consecutiveFailures > MAX_RECONNECT_ATTEMPTS) {
                _connection.update {
                    it.copy(
                        phase = ConnectionPhase.FAILED,
                        message = "The connection to the host was lost.",
                    )
                }
                return
            }
            _connection.update {
                it.copy(
                    phase = ConnectionPhase.RECONNECTING,
                    message = "Reconnecting (attempt $consecutiveFailures)…",
                )
            }
            delay(backoffMs(consecutiveFailures))
            runCatching { refreshMediaToken() }
        }
    }

    private suspend fun heartbeatLoop(session: DefaultClientWebSocketSession) {
        while (currentCoroutineContext().isActive && !closed.get()) {
            delay(HEARTBEAT_MS)
            val id = UUID.randomUUID().toString()
            pendingPings[id] = System.currentTimeMillis()
            sendFrame(
                session,
                SyncMessage(
                    type = MessageType.SYNC_PING,
                    roomId = roomId,
                    text = id,
                    serverTime = serverNow(),
                ),
            )
            val cutoff = System.currentTimeMillis() - 60_000L
            pendingPings.entries.removeIf { it.value < cutoff }
        }
    }

    private suspend fun handleMessage(message: SyncMessage, session: DefaultClientWebSocketSession) {
        message.serverTime?.let { sampleClock(it) }
        _events.tryEmit(message)

        when (message.type) {
            MessageType.JOIN_ACK -> {
                val isHost = message.isHost ?: false
                val locked = message.controlsLocked ?: false
                val canControl = message.canControl ?: true
                val self = ParticipantDto(
                    id = message.participantId.orEmpty(),
                    displayName = message.displayName ?: "You",
                    isHost = isHost,
                    connected = true,
                    controlsAllowed = canControl,
                )
                _connection.update {
                    it.copy(
                        phase = ConnectionPhase.LIVE,
                        self = self,
                        isHost = isHost,
                        participants = message.participants ?: it.participants,
                        canControl = isHost || (!locked && canControl),
                        controlsLocked = locked,
                        message = null,
                    )
                }
                _chat.value = emptyList()
            }

            MessageType.CHAT_HISTORY -> {
                _chat.value = (message.chatMessages ?: emptyList()).takeLast(CHAT_HISTORY_LIMIT)
            }

            MessageType.CHAT_MESSAGE -> {
                val incoming = message.chatMessages ?: return
                _chat.update { current ->
                    val seen = current.mapTo(HashSet()) { it.messageId }
                    (current + incoming.filter { seen.add(it.messageId) }).takeLast(CHAT_HISTORY_LIMIT)
                }
            }

            MessageType.STATE_UPDATE -> applyState(message)

            MessageType.MEDIA_CHANGED -> applyState(message)

            MessageType.PARTICIPANTS -> {
                val list = message.participants ?: return
                val locked = message.controlsLocked ?: _connection.value.controlsLocked
                applyParticipants(list, locked)
            }

            MessageType.CONTROL_POLICY -> {
                val locked = message.controlsLocked ?: false
                _connection.update {
                    it.copy(
                        controlsLocked = locked,
                        canControl = it.isHost || (!locked && (message.canControl ?: true)),
                        message = message.text,
                    )
                }
            }

            MessageType.PARTICIPANT_JOINED -> {
                val id = message.participantId ?: return
                val existing = _connection.value.participants
                if (existing.none { it.id == id }) {
                    val added = ParticipantDto(
                        id = id,
                        displayName = message.displayName ?: "Viewer",
                        isHost = message.isHost ?: false,
                        connected = true,
                    )
                    _connection.update { it.copy(participants = it.participants + added) }
                }
            }

            MessageType.PARTICIPANT_LEFT -> {
                val id = message.participantId ?: return
                _connection.update { state ->
                    state.copy(participants = state.participants.filterNot { it.id == id })
                }
            }

            MessageType.ROOM_CLOSED -> {
                closed.set(true)
                _connection.update {
                    it.copy(
                        phase = ConnectionPhase.ENDED,
                        message = message.errorMessage ?: message.text ?: "The host closed this room.",
                    )
                }
            }

            MessageType.HOST_OFFLINE -> {
                _connection.update {
                    it.copy(hostOffline = true, message = "The host is offline. Waiting for them to return…")
                }
            }

            MessageType.ERROR -> {
                _connection.update {
                    it.copy(
                        errorCode = message.errorCode,
                        message = message.errorMessage ?: message.text ?: "The host rejected the request.",
                    )
                }
            }

            MessageType.SYNC_PING -> {
                sendFrame(
                    session,
                    SyncMessage(
                        type = MessageType.SYNC_PONG,
                        roomId = roomId,
                        text = message.text,
                        serverTime = serverNow(),
                    ),
                )
            }

            MessageType.SYNC_PONG -> {
                val id = message.text ?: return
                val sentAt = pendingPings.remove(id) ?: return
                val now = System.currentTimeMillis()
                val rtt = (now - sentAt).coerceAtLeast(0L)
                val serverTime = message.serverTime ?: return
                sampleClock(serverTime, rtt)
            }

            else -> Unit
        }
    }

    private fun applyState(message: SyncMessage) {
        val current = _playback.value
        val sequence = message.sequence ?: current?.sequence ?: 0L
        val mediaId = message.mediaId ?: current?.mediaId
        if (current != null && current.mediaId == mediaId && sequence < current.sequence) return
        val anchor = message.serverTimestamp ?: message.serverTime ?: serverNow()
        _playback.value = AuthoritativePlayback(
            mediaId = mediaId,
            mediaTitle = message.mediaTitle ?: current?.mediaTitle,
            isPlaying = message.isPlaying ?: current?.isPlaying ?: false,
            positionMs = message.positionMs ?: current?.positionMs ?: 0L,
            playbackSpeed = message.playbackSpeed ?: current?.playbackSpeed ?: 1f,
            durationMs = message.durationMs ?: current?.durationMs ?: 0L,
            sequence = sequence,
            anchorServerMs = anchor,
        )
    }

    private fun applyParticipants(participants: List<ParticipantDto>, locked: Boolean) {
        val current = _connection.value
        val self = participants.firstOrNull { it.id == current.self?.id } ?: current.self
        val isHost = self?.isHost ?: current.isHost
        _connection.update {
            it.copy(
                participants = participants,
                self = self,
                isHost = isHost,
                controlsLocked = locked,
                canControl = isHost || !locked,
            )
        }
    }

    private fun sampleClock(serverTimeMs: Long, roundTripMs: Long = 0L) {
        if (serverTimeMs <= 0L) return
        val now = System.currentTimeMillis()
        val raw = ClockSync.estimateOffset(serverTimeMs, now, roundTripMs)
        val previous = _serverOffset.value.takeIf { it != 0L }
        _serverOffset.value = ClockSync.smooth(previous, raw)
    }

    // -----------------------------------------------------------------------
    // Commands
    // -----------------------------------------------------------------------

    private fun command(
        type: MessageType,
        positionMs: Long? = null,
        speed: Float? = null,
    ): SyncMessage = SyncMessage(
        type = type,
        roomId = roomId,
        commandId = UUID.randomUUID().toString(),
        positionMs = positionMs,
        playbackSpeed = speed,
        clientTimestamp = System.currentTimeMillis(),
        expectedSequence = _playback.value?.sequence,
    )

    private fun sendCommand(message: SyncMessage): Boolean {
        if (closed.get() || _connection.value.phase != ConnectionPhase.LIVE) return false
        if (!_connection.value.canControl) return false
        scope.launch {
            val session = socket ?: return@launch
            runCatching { sendFrame(session, message) }
        }
        return true
    }

    private suspend fun sendFrame(session: DefaultClientWebSocketSession, message: SyncMessage) {
        sendMutex.withLock {
            session.send(Frame.Text(SyncJson.encode(message)))
        }
    }

    private fun wsUrl(): String {
        val secure = baseUrl.startsWith("https://", ignoreCase = true)
        val host = baseUrl
            .removePrefix("https://").removePrefix("http://")
            .trimEnd('/')
        val protocol = if (secure) "wss" else "ws"
        return "$protocol://$host$wsPath?token=${URLEncoder.encode(sessionToken, "UTF-8")}"
    }

    private fun backoffMs(attempt: Int): Long {
        val base = 750L * (1L shl (attempt - 1).coerceIn(0, 4))
        return base.coerceAtMost(10_000L)
    }

    companion object {
        private const val HEARTBEAT_MS = 15_000L
        private const val TOKEN_REFRESH_MS = 30 * 60 * 1000L
        private const val MAX_RECONNECT_ATTEMPTS = 6
    }
}
