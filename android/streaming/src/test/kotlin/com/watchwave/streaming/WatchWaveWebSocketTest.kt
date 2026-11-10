package com.watchwave.streaming

import com.watchwave.shared.protocol.API_PREFIX
import com.watchwave.shared.protocol.CreateRoomRequest
import com.watchwave.shared.protocol.ErrorCodes
import com.watchwave.shared.protocol.MediaDescriptorDto
import com.watchwave.shared.protocol.MessageType
import com.watchwave.shared.protocol.SyncJson
import com.watchwave.shared.protocol.SyncMessage
import com.watchwave.shared.room.CreateRoomParams
import com.watchwave.shared.room.CreateRoomResult
import com.watchwave.shared.room.RoomManager
import com.watchwave.shared.room.RoomResult
import com.watchwave.shared.security.MediaTokenManager
import com.watchwave.shared.security.TokenService
import com.watchwave.shared.sync.SyncEngine
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class WatchWaveWebSocketTest {

    private val tokens = TokenService()
    private val mediaBytes = ByteArray(1_024) { (it % 200).toByte() }
    private lateinit var rooms: RoomManager
    private lateinit var created: CreateRoomResult
    private lateinit var engine: SyncEngine
    private lateinit var server: WatchWaveServer

    private inner class Store : ServerRoomStore {
        override fun roomManager(): RoomManager = rooms
        override fun syncEngine(roomId: String): SyncEngine? = if (roomId == created.snapshot.roomId) engine else null
        override fun mediaSource(roomId: String): MediaSource? =
            if (roomId == created.snapshot.roomId) object : MediaSource {
                override val sizeBytes: Long = mediaBytes.size.toLong()
                override val mimeType: String = "video/mp4"
                override fun openStream(offset: Long): InputStream =
                    ByteArrayInputStream(mediaBytes, offset.toInt(), mediaBytes.size - offset.toInt())
            } else null

        override suspend fun createRoom(request: CreateRoomRequest): RoomResult<CreateRoomResult> =
            RoomResult.Failure("TEST", "Not used")
    }

    @Before
    fun setUp() {
        rooms = RoomManager()
        val result = rooms.createRoom(
            CreateRoomParams(
                roomName = "Socket Room",
                hostName = "Host",
                media = MediaDescriptorDto(
                    mediaId = "m_ws",
                    title = "Socket Video",
                    durationMs = 120_000L,
                    mimeType = "video/mp4",
                    sizeBytes = mediaBytes.size.toLong(),
                ),
            ),
        )
        created = (result as RoomResult.Success).value
        engine = SyncEngine(created.snapshot.roomId, created.snapshot.media)
        server = WatchWaveServer(
            store = Store(),
            mediaTokens = MediaTokenManager(MediaTokenManager.randomSecret(tokens)),
            stats = StreamingStats(),
            webAssets = null,
        )
    }

    @Test
    fun `host connects receives ack and state then controls playback`() = testApplication {
        application { server.installModuleOn(this) }
        val wsClient = createClient { install(WebSockets) }
        val roomId = created.snapshot.roomId

        wsClient.webSocket("$API_PREFIX/rooms/$roomId/sync?token=${created.hostSessionToken}") {
            val ack = decodeNext()
            assertEquals(MessageType.JOIN_ACK, ack.type)
            assertTrue(ack.isHost == true)
            assertEquals("Host", ack.displayName)

            val state = decodeNext()
            assertEquals(MessageType.STATE_UPDATE, state.type)
            assertEquals(false, state.isPlaying)

            send(
                Frame.Text(
                    SyncJson.encode(
                        SyncMessage(
                            type = MessageType.PLAY,
                            roomId = roomId,
                            commandId = "cmd-play",
                            positionMs = 2_000L,
                        ),
                    ),
                ),
            )
            val playing = awaitState(MessageType.STATE_UPDATE) { it.isPlaying == true }
            assertTrue((playing.positionMs ?: 0L) >= 2_000L)
            assertTrue((playing.sequence ?: 0L) > (state.sequence ?: 0L))
        }
    }

    @Test
    fun `a joined viewer shares the authoritative state`() = testApplication {
        application { server.installModuleOn(this) }
        val join = rooms.join(created.snapshot.roomId, created.inviteToken, "Viewer")
        val session = (join as RoomResult.Success).value
        val wsClient = createClient { install(WebSockets) }

        wsClient.webSocket(
            "$API_PREFIX/rooms/${created.snapshot.roomId}/sync?token=${session.sessionToken}",
        ) {
            val ack = decodeNext()
            assertEquals(MessageType.JOIN_ACK, ack.type)
            assertEquals(false, ack.isHost)
            assertEquals("Viewer", ack.displayName)
            assertNotNull(ack.participants?.firstOrNull { it.displayName == "Host" })
        }
    }

    @Test
    fun `a viewer can play pause and seek for the whole room`() = testApplication {
        application { server.installModuleOn(this) }
        val hostClient = createClient { install(WebSockets) }
        val viewerClient = createClient { install(WebSockets) }
        val roomId = created.snapshot.roomId
        val join = rooms.join(roomId, created.inviteToken, "Viewer")
        val session = (join as RoomResult.Success).value

        val hostEvents = Channel<SyncMessage>(Channel.UNLIMITED)
        val viewerEvents = Channel<SyncMessage>(Channel.UNLIMITED)

        hostClient.webSocket("$API_PREFIX/rooms/$roomId/sync?token=${created.hostSessionToken}") {
            assertEquals(MessageType.JOIN_ACK, decodeNext().type)
            val initial = decodeNext()
            assertEquals(MessageType.STATE_UPDATE, initial.type)
            assertEquals(false, initial.isPlaying)

            // Drain the host socket in the background so a slow assertion never
            // blocks the server's send path for this connection.
            val hostReader = launch { collectMessages(hostEvents) }
            try {
                viewerClient.webSocket("$API_PREFIX/rooms/$roomId/sync?token=${session.sessionToken}") {
                    assertEquals(MessageType.JOIN_ACK, decodeNext().type)
                    assertEquals(MessageType.STATE_UPDATE, decodeNext().type)
                    val viewerReader = launch { collectMessages(viewerEvents) }
                    try {
                        send(
                            Frame.Text(
                                SyncJson.encode(
                                    SyncMessage(
                                        type = MessageType.PLAY,
                                        roomId = roomId,
                                        commandId = "viewer-play",
                                        positionMs = 2_000L,
                                        expectedSequence = initial.sequence,
                                    ),
                                ),
                            ),
                        )
                        val viewerPlaying = receiveState(viewerEvents) { it.isPlaying == true }
                        val hostPlaying = receiveState(hostEvents) { it.isPlaying == true }
                        assertTrue((viewerPlaying.sequence ?: 0L) > (initial.sequence ?: 0L))
                        assertTrue((hostPlaying.sequence ?: 0L) > (initial.sequence ?: 0L))

                        send(
                            Frame.Text(
                                SyncJson.encode(
                                    SyncMessage(
                                        type = MessageType.PAUSE,
                                        roomId = roomId,
                                        commandId = "viewer-pause",
                                        expectedSequence = viewerPlaying.sequence,
                                    ),
                                ),
                            ),
                        )
                        val viewerPaused = receiveState(viewerEvents) { it.isPlaying == false }
                        val hostPaused = receiveState(hostEvents) { it.isPlaying == false }
                        assertTrue((viewerPaused.sequence ?: 0L) > (viewerPlaying.sequence ?: 0L))
                        assertEquals(viewerPaused.sequence, hostPaused.sequence)

                        send(
                            Frame.Text(
                                SyncJson.encode(
                                    SyncMessage(
                                        type = MessageType.SEEK,
                                        roomId = roomId,
                                        commandId = "viewer-seek",
                                        positionMs = 5_000L,
                                        expectedSequence = viewerPaused.sequence,
                                    ),
                                ),
                            ),
                        )
                        val viewerSeek = receiveState(viewerEvents) { (it.positionMs ?: 0L) >= 5_000L }
                        val hostSeek = receiveState(hostEvents) { (it.positionMs ?: 0L) >= 5_000L }
                        assertTrue((viewerSeek.sequence ?: 0L) > (viewerPaused.sequence ?: 0L))
                        assertEquals(viewerSeek.sequence, hostSeek.sequence)
                    } finally {
                        viewerReader.cancel()
                    }
                }
            } finally {
                hostReader.cancel()
            }
        }
    }

    @Test
    fun `a state request returns a correlated authoritative snapshot`() = testApplication {
        application { server.installModuleOn(this) }
        val wsClient = createClient { install(WebSockets) }
        val roomId = created.snapshot.roomId

        wsClient.webSocket("$API_PREFIX/rooms/$roomId/sync?token=${created.hostSessionToken}") {
            assertEquals(MessageType.JOIN_ACK, decodeNext().type)
            assertEquals(MessageType.STATE_UPDATE, decodeNext().type)

            send(
                Frame.Text(
                    SyncJson.encode(
                        SyncMessage(
                            type = MessageType.STATE_REQUEST,
                            roomId = roomId,
                            requestId = "snapshot-1",
                        ),
                    ),
                ),
            )
            val snapshot = awaitState(MessageType.STATE_UPDATE) { it.requestId == "snapshot-1" }
            assertEquals(created.snapshot.media.mediaId, snapshot.mediaId)
            assertEquals(false, snapshot.isPlaying)
        }
    }

    @Test
    fun `a pong reply to the server heartbeat is not treated as an error`() = testApplication {
        application { server.installModuleOn(this) }
        val wsClient = createClient { install(WebSockets) }
        val roomId = created.snapshot.roomId

        wsClient.webSocket("$API_PREFIX/rooms/$roomId/sync?token=${created.hostSessionToken}") {
            assertEquals(MessageType.JOIN_ACK, decodeNext().type)
            assertEquals(MessageType.STATE_UPDATE, decodeNext().type)

            send(
                Frame.Text(
                    SyncJson.encode(
                        SyncMessage(
                            type = MessageType.SYNC_PONG,
                            roomId = roomId,
                            serverTime = System.currentTimeMillis(),
                        ),
                    ),
                ),
            )
            send(
                Frame.Text(
                    SyncJson.encode(
                        SyncMessage(
                            type = MessageType.PLAY,
                            roomId = roomId,
                            commandId = "cmd-after-pong",
                            positionMs = 1_000L,
                        ),
                    ),
                ),
            )

            var sawError = false
            withTimeout(5_000L) {
                while (true) {
                    val message = decodeNext()
                    if (message.type == MessageType.ERROR) sawError = true
                    if (message.type == MessageType.STATE_UPDATE && message.isPlaying == true) break
                }
            }
            assertFalse("A pong must not produce a protocol error", sawError)
        }
    }

    @Test
    fun `a viewer can chat and the next viewer receives history`() = testApplication {
        application { server.installModuleOn(this) }
        val join = rooms.join(created.snapshot.roomId, created.inviteToken, "Viewer")
        val session = (join as RoomResult.Success).value
        val wsClient = createClient { install(WebSockets) }

        wsClient.webSocket(
            "$API_PREFIX/rooms/${created.snapshot.roomId}/sync?token=${session.sessionToken}",
        ) {
            assertEquals(MessageType.JOIN_ACK, decodeNext().type)
            assertEquals(MessageType.STATE_UPDATE, decodeNext().type)

            send(
                Frame.Text(
                    SyncJson.encode(
                        SyncMessage(
                            type = MessageType.CHAT_SEND,
                            roomId = created.snapshot.roomId,
                            text = "  hello room  ",
                        ),
                    ),
                ),
            )
            val chat = awaitMessage(MessageType.CHAT_MESSAGE)
            val chatMessage = chat.chatMessages?.single()
            assertNotNull(chatMessage)
            assertEquals("hello room", chatMessage?.text)
            assertEquals("Viewer", chatMessage?.displayName)
            assertEquals(session.participant.id, chatMessage?.participantId)
            assertEquals(false, chatMessage?.isHost)
            assertNotNull(chatMessage?.messageId)
            assertTrue((chatMessage?.sentAt ?: 0L) > 0L)
        }

        val joinSecond = rooms.join(created.snapshot.roomId, created.inviteToken, "Second")
        val second = (joinSecond as RoomResult.Success).value
        wsClient.webSocket(
            "$API_PREFIX/rooms/${created.snapshot.roomId}/sync?token=${second.sessionToken}",
        ) {
            assertEquals(MessageType.JOIN_ACK, decodeNext().type)
            assertEquals(MessageType.STATE_UPDATE, decodeNext().type)
            val history = decodeNext()
            assertEquals(MessageType.CHAT_HISTORY, history.type)
            assertEquals("hello room", history.chatMessages?.single()?.text)
        }
    }

    @Test
    fun `empty chat is rejected with a stable error code`() = testApplication {
        application { server.installModuleOn(this) }
        val wsClient = createClient { install(WebSockets) }
        val roomId = created.snapshot.roomId

        wsClient.webSocket("$API_PREFIX/rooms/$roomId/sync?token=${created.hostSessionToken}") {
            assertEquals(MessageType.JOIN_ACK, decodeNext().type)
            assertEquals(MessageType.STATE_UPDATE, decodeNext().type)
            send(
                Frame.Text(
                    SyncJson.encode(
                        SyncMessage(
                            type = MessageType.CHAT_SEND,
                            roomId = roomId,
                            text = "   ",
                        ),
                    ),
                ),
            )
            val error = awaitMessage(MessageType.ERROR)
            assertEquals(ErrorCodes.CHAT_EMPTY, error.errorCode)
        }
    }

    @Test
    fun `an invalid token is rejected`() = testApplication {
        application { server.installModuleOn(this) }
        val wsClient = createClient { install(WebSockets) }
        val failure = runCatching {
            wsClient.webSocket(
                "$API_PREFIX/rooms/${created.snapshot.roomId}/sync?token=not-a-valid-token",
            ) {
                // The server should close the session before any payload arrives.
                val frame = withTimeout(2_000L) { incoming.receive() }
                if (frame is Frame.Text) {
                    error("Unexpected payload: ${frame.readText()}")
                }
            }
        }
        assertTrue("Expected the connection to be rejected", failure.isFailure)
    }

    private suspend fun io.ktor.websocket.WebSocketSession.decodeNext(): SyncMessage =
        withTimeout(5_000L) {
            val frame = incoming.receive()
            check(frame is Frame.Text) { "Expected a text frame" }
            SyncJson.decode(frame.readText())
        }

    private suspend fun io.ktor.websocket.WebSocketSession.awaitMessage(type: MessageType): SyncMessage =
        withTimeout(5_000L) {
            while (true) {
                val message = decodeNext()
                if (message.type == type) return@withTimeout message
            }
            @Suppress("UNREACHABLE_CODE")
            error("unreachable")
        }

    private suspend fun io.ktor.websocket.WebSocketSession.awaitState(
        type: MessageType,
        predicate: (SyncMessage) -> Boolean,
    ): SyncMessage = withTimeout(5_000L) {
        while (true) {
            val message = decodeNext()
            if (message.type == type && predicate(message)) return@withTimeout message
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }

    private suspend fun io.ktor.websocket.WebSocketSession.collectMessages(
        channel: Channel<SyncMessage>,
    ) {
        for (frame in incoming) {
            if (frame !is Frame.Text) continue
            runCatching { SyncJson.decode(frame.readText()) }
                .getOrNull()
                ?.let { channel.send(it) }
        }
    }

    private suspend fun receiveState(
        channel: Channel<SyncMessage>,
        predicate: (SyncMessage) -> Boolean,
    ): SyncMessage = withTimeout(5_000L) {
        while (true) {
            val message = channel.receive()
            if (message.type == MessageType.STATE_UPDATE && predicate(message)) return@withTimeout message
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }
}
