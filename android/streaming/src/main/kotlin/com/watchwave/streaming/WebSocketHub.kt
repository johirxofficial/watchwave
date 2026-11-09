package com.watchwave.streaming

import com.watchwave.shared.protocol.CommandAction
import com.watchwave.shared.protocol.ErrorCodes
import com.watchwave.shared.protocol.MessageType
import com.watchwave.shared.protocol.ParticipantDto
import com.watchwave.shared.protocol.SyncJson
import com.watchwave.shared.protocol.SyncMessage
import com.watchwave.shared.room.RoomManager
import com.watchwave.shared.room.RoomResult
import com.watchwave.shared.security.Clock
import com.watchwave.shared.security.SlidingWindowRateLimiter
import com.watchwave.shared.security.SystemClock
import com.watchwave.shared.sync.CommandOutcome
import com.watchwave.shared.sync.PlaybackCommand
import com.watchwave.shared.sync.SyncEngine
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry and protocol handler for every room's sync WebSocket.
 *
 * The hub never trusts client-provided participant ids: identity is resolved from the
 * session token on the URL. Commands are delegated to the room's [SyncEngine], which
 * validates permissions, ordering and rate limits before anything is broadcast.
 */
class WebSocketHub(
    private val roomManager: () -> RoomManager,
    private val syncEngine: (String) -> SyncEngine?,
    private val clock: Clock = SystemClock,
    private val heartbeatMs: Long = 15_000L,
    private val logger: (String) -> Unit = {},
) {
    private class Connection(
        val roomId: String,
        val participantId: String,
        val isHost: Boolean,
        val session: DefaultWebSocketServerSession,
        val sendMutex: Mutex = Mutex(),
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val connections = ConcurrentHashMap<String, ConcurrentHashMap<String, Connection>>()

    fun connectionCount(roomId: String): Int = connections[roomId]?.size ?: 0

    fun totalConnections(): Int = connections.values.sumOf { it.size }

    suspend fun handle(session: DefaultWebSocketServerSession, roomId: String, token: String) {
        val rooms = roomManager()
        val engine = syncEngine(roomId)
        if (engine == null) {
            closeWith(session, CloseReason.Codes.CANNOT_ACCEPT, "Room is not active")
            return
        }
        val validation = rooms.validateSession(roomId, token)
        if (validation is RoomResult.Failure) {
            closeWith(session, CloseReason.Codes.VIOLATED_POLICY, validation.message)
            return
        }
        val participant = (validation as RoomResult.Success).value.participant
        val connection = Connection(roomId, participant.id, participant.isHost, session)

        connections.computeIfAbsent(roomId) { ConcurrentHashMap<String, Connection>() }
            .put(participant.id, connection)?.let { previous ->
                runCatching { previous.session.close(CloseReason(CloseReason.Codes.NORMAL, "Replaced by reconnect")) }
            }
        rooms.setParticipantConnected(roomId, participant.id, true)

        val limiter = SlidingWindowRateLimiter(maxEvents = 60, windowMs = 10_000L)
        val heartbeat = scope.launch { heartbeatLoop(connection) }

        try {
            send(
                connection,
                SyncMessage(
                    type = MessageType.JOIN_ACK,
                    roomId = roomId,
                    participantId = participant.id,
                    displayName = participant.displayName,
                    isHost = participant.isHost,
                    participants = rooms.participants(roomId),
                    canControl = participant.controlsAllowed || participant.isHost,
                    controlsLocked = rooms.snapshot(roomId)?.controlsLocked ?: false,
                    serverTime = clock.wallMs(),
                    text = "Connected",
                ),
            )
            sendState(connection, engine)
            broadcastParticipants(roomId)
            broadcast(
                roomId,
                SyncMessage(
                    type = MessageType.PARTICIPANT_JOINED,
                    roomId = roomId,
                    participantId = participant.id,
                    displayName = participant.displayName,
                    isHost = participant.isHost,
                ),
            )

            for (frame in session.incoming) {
                if (frame !is Frame.Text) continue
                if (!limiter.tryAcquire(clock.monoMs())) {
                    sendError(connection, ErrorCodes.RATE_LIMITED, "Too many messages.", retryAfterMs = limiter.retryAfterMs(clock.monoMs()))
                    continue
                }
                val message = try {
                    SyncJson.decode(frame.readText())
                } catch (_: Exception) {
                    sendError(connection, ErrorCodes.INVALID_REQUEST, "Malformed JSON frame.")
                    continue
                }
                if (message.roomId != null && message.roomId != roomId) {
                    sendError(connection, ErrorCodes.FORBIDDEN, "Room mismatch.")
                    continue
                }
                when (message.type) {
                    MessageType.SYNC_PING -> send(
                        connection,
                        SyncMessage(
                            type = MessageType.SYNC_PONG,
                            roomId = roomId,
                            serverTime = clock.wallMs(),
                            text = message.text,
                        ),
                    )

                    // Clients answer the server's app-level heartbeat with a pong.
                    // It carries no command and must not be treated as an error.
                    MessageType.SYNC_PONG -> Unit

                    MessageType.LEAVE -> {
                        closeWith(session, CloseReason.Codes.NORMAL, "Client left")
                        break
                    }

                    MessageType.PLAY,
                    MessageType.PAUSE,
                    MessageType.SEEK,
                    MessageType.SET_SPEED,
                    MessageType.ENDED,
                    -> handleCommand(connection, engine, message)

                    else -> sendError(
                        connection,
                        ErrorCodes.INVALID_ACTION,
                        "Unsupported message type ${message.type}.",
                    )
                }
            }
        } catch (error: Exception) {
            if (currentCoroutineContext().isActive) {
                logger("WebSocket closed with ${error::class.simpleName}")
            }
        } finally {
            heartbeat.cancel()
            unregister(connection)
        }
    }

    suspend fun broadcastState(roomId: String) {
        val engine = syncEngine(roomId) ?: return
        val state = engine.state()
        broadcast(
            roomId,
            SyncMessage(
                type = MessageType.STATE_UPDATE,
                roomId = roomId,
                mediaId = state.mediaId,
                mediaTitle = engine.currentMedia().title,
                sequence = state.sequence,
                positionMs = state.positionMs,
                playbackSpeed = state.playbackSpeed,
                durationMs = state.durationMs,
                isPlaying = state.isPlaying,
                serverTimestamp = state.serverTimestamp,
                serverTime = state.serverTime,
            ),
        )
    }

    suspend fun broadcastParticipants(roomId: String) {
        val snapshot = roomManager().snapshot(roomId) ?: return
        broadcast(
            roomId,
            SyncMessage(
                type = MessageType.PARTICIPANTS,
                roomId = roomId,
                participants = snapshot.participants,
                controlsLocked = snapshot.controlsLocked,
                text = "Participants updated",
            ),
        )
    }

    suspend fun broadcastControlPolicy(roomId: String) {
        val snapshot = roomManager().snapshot(roomId) ?: return
        broadcast(
            roomId,
            SyncMessage(
                type = MessageType.CONTROL_POLICY,
                roomId = roomId,
                controlsLocked = snapshot.controlsLocked,
                canControl = !snapshot.controlsLocked,
                text = if (snapshot.controlsLocked) "Viewer controls locked" else "Shared controls enabled",
            ),
        )
        broadcastParticipants(roomId)
    }

    suspend fun announceParticipantLeft(roomId: String, participantId: String, displayName: String) {
        broadcast(
            roomId,
            SyncMessage(
                type = MessageType.PARTICIPANT_LEFT,
                roomId = roomId,
                participantId = participantId,
                displayName = displayName,
            ),
        )
        broadcastParticipants(roomId)
    }

    suspend fun broadcastMediaChanged(roomId: String) {
        val engine = syncEngine(roomId) ?: return
        val state = engine.state()
        broadcast(
            roomId,
            SyncMessage(
                type = MessageType.MEDIA_CHANGED,
                roomId = roomId,
                mediaId = state.mediaId,
                mediaTitle = engine.currentMedia().title,
                durationMs = state.durationMs,
                positionMs = state.positionMs,
                isPlaying = state.isPlaying,
                playbackSpeed = state.playbackSpeed,
                sequence = state.sequence,
                serverTimestamp = state.serverTimestamp,
                serverTime = state.serverTime,
            ),
        )
        broadcastState(roomId)
    }

    suspend fun closeRoom(roomId: String, text: String = "The host closed this room.") {
        broadcast(
            roomId,
            SyncMessage(
                type = MessageType.ROOM_CLOSED,
                roomId = roomId,
                errorCode = ErrorCodes.ROOM_CLOSED,
                errorMessage = text,
                text = text,
                serverTime = clock.wallMs(),
            ),
        )
        closeRoomConnections(roomId, CloseReason.Codes.NORMAL, text)
    }

    /** Closes an existing socket without treating it as a host action. */
    fun disconnectParticipant(roomId: String, participantId: String, reason: String = "Left the room.") {
        val connection = connections[roomId]?.remove(participantId) ?: return
        roomManager().setParticipantConnected(roomId, participantId, false)
        scope.launch {
            runCatching { connection.session.close(CloseReason(CloseReason.Codes.NORMAL, reason)) }
            runCatching { broadcastParticipants(roomId) }
        }
    }

    fun kickParticipant(roomId: String, participantId: String, text: String = "Removed by the host.") {
        val connection = connections[roomId]?.remove(participantId) ?: return
        roomManager().setParticipantConnected(roomId, participantId, false)
        scope.launch {
            runCatching {
                connection.session.send(
                    Frame.Text(
                        SyncJson.encode(
                            SyncMessage(
                                type = MessageType.ERROR,
                                roomId = roomId,
                                participantId = participantId,
                                errorCode = ErrorCodes.FORBIDDEN,
                                errorMessage = text,
                            ),
                        ),
                    ),
                )
            }
            runCatching { connection.session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, text)) }
            runCatching { broadcastParticipants(roomId) }
        }
    }

    fun notifyHostOffline(roomId: String, participantId: String) {
        scope.launch {
            broadcast(
                roomId,
                SyncMessage(
                    type = MessageType.HOST_OFFLINE,
                    roomId = roomId,
                    participantId = participantId,
                    errorCode = ErrorCodes.HOST_OFFLINE,
                    errorMessage = "The host disconnected. Waiting for the host to return.",
                ),
            )
            runCatching { broadcastParticipants(roomId) }
        }
    }

    fun stop() {
        val snapshot = connections.values.flatMap { it.values }
        connections.clear()
        scope.cancel()
        kotlinx.coroutines.runBlocking {
            snapshot.forEach { connection ->
                runCatching {
                    withTimeoutOrNull(300L) {
                        connection.session.close(CloseReason(CloseReason.Codes.GOING_AWAY, "Server stopped"))
                    }
                }
            }
        }
    }

    private suspend fun handleCommand(connection: Connection, engine: SyncEngine, message: SyncMessage) {
        val commandId = message.commandId
        if (commandId.isNullOrBlank()) {
            sendError(connection, ErrorCodes.INVALID_REQUEST, "commandId is required.")
            return
        }
        val participant = roomManager().participant(connection.roomId, connection.participantId)
        if (participant == null) {
            sendError(connection, ErrorCodes.UNAUTHORIZED, "Participant is no longer in the room.")
            return
        }
        val action = when (message.type) {
            MessageType.PLAY -> CommandAction.PLAY
            MessageType.PAUSE -> CommandAction.PAUSE
            MessageType.SEEK -> CommandAction.SEEK
            MessageType.SET_SPEED -> CommandAction.SET_SPEED
            MessageType.ENDED -> CommandAction.ENDED
            else -> return
        }
        val command = PlaybackCommand(
            commandId = commandId,
            action = action,
            positionMs = message.positionMs,
            playbackSpeed = message.playbackSpeed,
            clientTimestamp = message.clientTimestamp,
            expectedSequence = message.expectedSequence,
        )
        when (val outcome = engine.handleCommand(participant, command)) {
            is CommandOutcome.Accepted -> {
                broadcastState(connection.roomId)
            }

            is CommandOutcome.Duplicate -> sendState(connection, engine)

            is CommandOutcome.Rejected -> {
                sendError(
                    connection,
                    outcome.code,
                    outcome.message,
                    retryAfterMs = outcome.retryAfterMs,
                )
                sendState(connection, engine)
            }
        }
    }

    private suspend fun sendState(connection: Connection, engine: SyncEngine) {
        val state = engine.state()
        send(
            connection,
            SyncMessage(
                type = MessageType.STATE_UPDATE,
                roomId = connection.roomId,
                mediaId = state.mediaId,
                mediaTitle = engine.currentMedia().title,
                sequence = state.sequence,
                positionMs = state.positionMs,
                playbackSpeed = state.playbackSpeed,
                durationMs = state.durationMs,
                isPlaying = state.isPlaying,
                serverTimestamp = state.serverTimestamp,
                serverTime = state.serverTime,
            ),
        )
    }

    private suspend fun broadcast(roomId: String, message: SyncMessage) {
        val roomConnections = connections[roomId]?.values?.toList() ?: return
        val raw = SyncJson.encode(message)
        roomConnections.forEach { connection ->
            val ok = sendRaw(connection, raw)
            if (!ok) {
                connections[roomId]?.remove(connection.participantId, connection)
                roomManager().setParticipantConnected(roomId, connection.participantId, false)
            }
        }
    }

    private suspend fun send(connection: Connection, message: SyncMessage): Boolean =
        sendRaw(connection, SyncJson.encode(message))

    private suspend fun sendRaw(connection: Connection, raw: String): Boolean = try {
        withTimeoutOrNull(5_000L) {
            connection.sendMutex.withLock {
                connection.session.send(Frame.Text(raw))
            }
        } != null
    } catch (_: Exception) {
        false
    }

    private suspend fun sendError(
        connection: Connection,
        code: String,
        message: String,
        retryAfterMs: Long? = null,
    ) {
        send(
            connection,
            SyncMessage(
                type = MessageType.ERROR,
                roomId = connection.roomId,
                participantId = connection.participantId,
                errorCode = code,
                errorMessage = message,
                retryAfterMs = retryAfterMs,
            ),
        )
    }

    private suspend fun heartbeatLoop(connection: Connection) {
        while (currentCoroutineContext().isActive) {
            delay(heartbeatMs)
            val ok = send(
                connection,
                SyncMessage(
                    type = MessageType.SYNC_PING,
                    roomId = connection.roomId,
                    serverTime = clock.wallMs(),
                ),
            )
            if (!ok) break
        }
    }

    private suspend fun unregister(connection: Connection) {
        val roomConnections = connections[connection.roomId]
        roomConnections?.remove(connection.participantId, connection)
        if (roomConnections != null && roomConnections.isEmpty()) {
            connections.remove(connection.roomId, roomConnections)
        }
        val rooms = roomManager()
        val participant = rooms.participant(connection.roomId, connection.participantId)
        rooms.setParticipantConnected(connection.roomId, connection.participantId, false)
        if (participant != null) {
            runCatching {
                announceParticipantLeft(connection.roomId, connection.participantId, participant.displayName)
            }
        }
        if (connection.isHost) {
            notifyHostOffline(connection.roomId, connection.participantId)
        }
    }

    private suspend fun closeRoomConnections(
        roomId: String,
        code: CloseReason.Codes,
        text: String,
    ) {
        val roomConnections = connections.remove(roomId) ?: return
        roomConnections.values.forEach { connection ->
            roomManager().setParticipantConnected(roomId, connection.participantId, false)
            runCatching {
                withTimeoutOrNull(2_000L) {
                    connection.session.close(CloseReason(code, text))
                }
            }
        }
    }

    private fun closeRoomConnectionsBlocking(roomId: String) {
        val roomConnections = connections.remove(roomId) ?: return
        roomConnections.values.forEach { connection ->
            scope.launch {
                runCatching { connection.session.close(CloseReason(CloseReason.Codes.GOING_AWAY, "Server stopped")) }
            }
        }
    }
    private suspend fun closeWith(session: DefaultWebSocketServerSession, code: CloseReason.Codes, text: String) {
        runCatching { session.close(CloseReason(code, text.take(120))) }
    }
}
