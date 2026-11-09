package com.watchwave.streaming

import com.watchwave.shared.protocol.ActionResponse
import com.watchwave.shared.protocol.API_PREFIX
import com.watchwave.shared.protocol.CreateRoomRequest
import com.watchwave.shared.protocol.CreateRoomResponse
import com.watchwave.shared.protocol.ErrorCodes
import com.watchwave.shared.protocol.ErrorResponse
import com.watchwave.shared.protocol.HealthDto
import com.watchwave.shared.protocol.HostParticipantRequest
import com.watchwave.shared.protocol.HostRevokeResponse
import com.watchwave.shared.protocol.HostToggleRequest
import com.watchwave.shared.protocol.JoinRequest
import com.watchwave.shared.protocol.JoinResponse
import com.watchwave.shared.protocol.LeaveRequest
import com.watchwave.shared.protocol.MediaTokenResponse
import com.watchwave.shared.protocol.ParticipantDto
import com.watchwave.shared.protocol.ReconnectRequest
import com.watchwave.shared.protocol.RoomStatus
import com.watchwave.shared.protocol.RoomSummaryDto
import com.watchwave.shared.protocol.TunnelStatusDto
import com.watchwave.shared.protocol.UploadStatsDto
import com.watchwave.shared.room.RoomManager
import com.watchwave.shared.room.RoomResult
import com.watchwave.shared.security.Clock
import com.watchwave.shared.security.MediaTokenManager
import com.watchwave.shared.security.ShareLinks
import com.watchwave.shared.security.SlidingWindowRateLimiter
import com.watchwave.shared.security.SystemClock
import com.watchwave.shared.security.TokenService
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receive
import io.ktor.server.request.uri
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.options
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.util.pipeline.PipelineContext
import io.ktor.websocket.close
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds

/**
 * Embedded HTTP + WebSocket server hosted on the Android device.
 *
 * Responsibilities:
 *  - versioned REST API (`/api/v1`, with a `/api` compatibility alias)
 *  - authenticated HTTP Range video streaming with backpressure-safe chunking
 *  - room-scoped sync WebSocket
 *  - serving the compiled browser client from assets (same-origin, so no CORS
 *    relaxation is needed for the real tunnel URL)
 */
class WatchWaveServer(
    private val store: ServerRoomStore,
    private val mediaTokens: MediaTokenManager,
    private val stats: StreamingStats,
    private val cfg: ServerConfig = ServerConfig(),
    private val webAssets: WebAssetsProvider? = null,
    private val tunnelStatus: TunnelStatusSource = TunnelStatusSource {
        TunnelStatusDto(state = "NOT_CONFIGURED", provider = "none", message = "No tunnel")
    },
    private val clock: Clock = SystemClock,
    private val logMessage: (String) -> Unit = {},
) {
    private val tokenService = TokenService()
    private val hub = WebSocketHub(
        roomManager = store::roomManager,
        syncEngine = store::syncEngine,
        clock = clock,
        heartbeatMs = cfg.wsHeartbeatMs,
        logger = logMessage,
    )
    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val streamPermits = Semaphore(cfg.maxConcurrentStreams)
    private val joinLimiters = ConcurrentHashMap<String, SlidingWindowRateLimiter>()

    @Volatile
    private var running: Boolean = false

    private var engine: ApplicationEngine? = null
    private var cleanupJob: Job? = null

    val port: Int get() = cfg.port

    fun isRunning(): Boolean = running

    fun start(): WatchWaveServer {
        if (running) return this
        engine = embeddedServer(CIO, host = cfg.host, port = cfg.port) {
            installModule()
        }.start(wait = false)
        running = true
        cleanupJob = serverScope.launch { cleanupLoop() }
        logMessage("HTTP server started on ${cfg.host}:${cfg.port}")
        return this
    }

    fun stop() {
        if (!running) return
        running = false
        cleanupJob?.cancel()
        cleanupJob = null
        hub.stop()
        engine?.stop(500L, 1_500L)
        engine = null
        serverScope.cancel()
        logMessage("HTTP server stopped")
    }

    fun activeConnections(roomId: String): Int = hub.connectionCount(roomId)

    fun broadcastState(roomId: String) {
        serverScope.launch { runCatching { hub.broadcastState(roomId) } }
    }

    fun notifyParticipantsChanged(roomId: String) {
        serverScope.launch { runCatching { hub.broadcastParticipants(roomId) } }
    }

    fun notifyControlPolicyChanged(roomId: String) {
        serverScope.launch { runCatching { hub.broadcastControlPolicy(roomId) } }
    }

    fun notifyMediaChanged(roomId: String) {
        serverScope.launch { runCatching { hub.broadcastMediaChanged(roomId) } }
    }

    fun notifyRoomClosed(roomId: String) {
        serverScope.launch { runCatching { hub.closeRoom(roomId) } }
    }

    fun kickParticipant(roomId: String, participantId: String) {
        hub.kickParticipant(roomId, participantId)
    }

    fun disconnectParticipant(roomId: String, participantId: String) {
        hub.disconnectParticipant(roomId, participantId)
    }

    fun statsSnapshot(): UploadStatsDto = stats.snapshot()

    // -----------------------------------------------------------------------
    // Application wiring
    // -----------------------------------------------------------------------

    /**
     * Explicit bridge used by tests and instrumentation. Member extension functions
     * cannot always be resolved through an implicit receiver from another module,
     * so this gives callers a plain method with an unambiguous signature.
     */
    fun installModuleOn(app: Application) {
        app.installModule()
    }

    fun Application.installModule() {
        install(ContentNegotiation) {
            json(com.watchwave.shared.protocol.SyncJson.json)
        }
        install(WebSockets) {
            pingPeriodMillis = 20_000L
            timeoutMillis = 30_000L
            maxFrameSize = 256L * 1024L
            masking = false
        }
        install(StatusPages) {
            exception<BadRequestException> { call, _ ->
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(ErrorCodes.INVALID_REQUEST, "Malformed request body."),
                )
            }
            exception<Throwable> { call, cause ->
                logMessage("Request failed: ${cause::class.simpleName}")
                if (!call.response.isCommitted) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(ErrorCodes.INTERNAL_ERROR, "The host could not complete this request."),
                    )
                }
            }
        }

        // Development-only CORS. Same-origin production requests need no CORS headers.
        intercept(ApplicationCallPipeline.Setup) {
            applyDevCors(call)
            if (call.request.httpMethod == HttpMethod.Options) {
                call.respond(HttpStatusCode.NoContent)
                finish()
            }
        }

        routing {
            apiRoutes(API_PREFIX)
            apiRoutes("/api")
            staticRoutes()
        }
    }

    private fun Route.apiRoutes(prefix: String) {
        route(prefix) {
            get("/health") {
                call.respond(HealthDto(serverTime = clock.wallMs()))
            }
            get("/tunnel/status") {
                call.respond(tunnelStatus.status())
            }

            route("/rooms") {
                post {
                    createRoomAdmin(call)
                }

                get("/{roomId}") {
                    getRoom(call, requiredRoomId(call))
                }

                post("/{roomId}/join") {
                    joinRoom(call, requiredRoomId(call))
                }

                post("/{roomId}/reconnect") {
                    reconnect(call, requiredRoomId(call))
                }

                post("/{roomId}/leave") {
                    leaveRoom(call, requiredRoomId(call))
                }

                post("/{roomId}/close") {
                    closeRoom(call, requiredRoomId(call))
                }

                get("/{roomId}/state") {
                    getState(call, requiredRoomId(call))
                }

                get("/{roomId}/stats") {
                    getStats(call, requiredRoomId(call))
                }

                get("/{roomId}/video") {
                    streamVideo(call, requiredRoomId(call), headOnly = false)
                }

                head("/{roomId}/video") {
                    streamVideo(call, requiredRoomId(call), headOnly = true)
                }

                post("/{roomId}/media-token") {
                    refreshMediaToken(call, requiredRoomId(call))
                }

                post("/{roomId}/host/participants/{participantId}/remove") {
                    removeParticipant(call, requiredRoomId(call), call.parameters["participantId"] ?: "")
                }

                post("/{roomId}/host/revoke-invite") {
                    revokeInvite(call, requiredRoomId(call))
                }

                post("/{roomId}/host/controls") {
                    setControls(call, requiredRoomId(call))
                }

                post("/{roomId}/host/join-policy") {
                    setJoinPolicy(call, requiredRoomId(call))
                }

                webSocket("/{roomId}/sync") {
                    val roomId = call.parameters["roomId"] ?: run {
                        close(io.ktor.websocket.CloseReason(io.ktor.websocket.CloseReason.Codes.CANNOT_ACCEPT, "Missing room"))
                        return@webSocket
                    }
                    val token = call.request.queryParameters["token"] ?: bearer(call)
                    if (token.isNullOrBlank()) {
                        close(io.ktor.websocket.CloseReason(io.ktor.websocket.CloseReason.Codes.VIOLATED_POLICY, "Missing token"))
                        return@webSocket
                    }
                    hub.handle(this, roomId, token)
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // REST handlers
    // -----------------------------------------------------------------------

    private suspend fun createRoomAdmin(call: ApplicationCall) {
        val provided = call.request.headers[ADMIN_HEADER]
        if (cfg.adminToken.isBlank() || provided == null ||
            !tokenService.constantTimeEquals(provided, cfg.adminToken)
        ) {
            respondError(call, HttpStatusCode.Forbidden, ErrorCodes.FORBIDDEN, "Admin token is required.")
            return
        }
        val request = call.receive<CreateRoomRequest>()
        when (val result = store.createRoom(request)) {
            is RoomResult.Success -> {
                val value = result.value
                call.respond(
                    HttpStatusCode.Created,
                    CreateRoomResponse(
                        room = summaryFor(value.snapshot.roomId) ?: return,
                        hostToken = value.hostToken,
                        hostSessionToken = value.hostSessionToken,
                        hostParticipantId = value.hostParticipantId,
                        inviteToken = value.inviteToken,
                        sharePath = ShareLinks.buildSharePath(value.snapshot.roomId, value.inviteToken),
                        mediaUrlPath = "$API_PREFIX/rooms/${value.snapshot.roomId}/video",
                        wsPath = "$API_PREFIX/rooms/${value.snapshot.roomId}/sync",
                    ),
                )
            }

            is RoomResult.Failure -> respondError(call, HttpStatusCode.BadRequest, result.code, result.message)
        }
    }

    private suspend fun getRoom(call: ApplicationCall, roomId: String) {
        val rooms = store.roomManager()
        val snapshot = rooms.snapshot(roomId)
        if (snapshot == null) {
            respondError(call, HttpStatusCode.NotFound, ErrorCodes.ROOM_NOT_FOUND, "Room does not exist.")
            return
        }
        when (snapshot.status) {
            RoomStatus.CLOSED -> {
                respondError(call, HttpStatusCode.Gone, ErrorCodes.ROOM_CLOSED, "This room has been closed.")
                return
            }

            RoomStatus.EXPIRED -> {
                respondError(call, HttpStatusCode.Gone, ErrorCodes.ROOM_EXPIRED, "This room has expired.")
                return
            }

            RoomStatus.ACTIVE -> Unit
        }
        val invite = call.request.queryParameters["t"]
        val hasSession = authorizeSession(call, roomId) != null
        if (!hasSession && (invite == null || !rooms.validateInvite(roomId, invite))) {
            respondError(call, HttpStatusCode.Forbidden, ErrorCodes.INVITE_INVALID, "Invitation is invalid or was revoked.")
            return
        }
        call.respond(summaryFor(roomId) ?: return)
    }

    private suspend fun joinRoom(call: ApplicationCall, roomId: String) {
        if (!allowJoin(clientIp(call))) {
            respondError(call, HttpStatusCode.TooManyRequests, ErrorCodes.RATE_LIMITED, "Too many join attempts.", retryAfterMs = 5_000L)
            return
        }
        val request = call.receive<JoinRequest>()
        val rooms = store.roomManager()
        when (val result = rooms.join(roomId, request.inviteToken, request.displayName)) {
            is RoomResult.Failure -> {
                respondError(call, statusForCode(result.code), result.code, result.message)
            }

            is RoomResult.Success -> {
                val value = result.value
                val mediaToken = mediaTokens.issue(roomId, value.participant.id)
                call.respond(
                    JoinResponse(
                        room = summaryFor(roomId) ?: return,
                        participant = value.participant,
                        sessionToken = value.sessionToken,
                        mediaToken = mediaToken,
                        mediaUrlPath = "$API_PREFIX/rooms/$roomId/video",
                        wsPath = "$API_PREFIX/rooms/$roomId/sync",
                        canControl = value.participant.controlsAllowed || value.participant.isHost,
                        shareUrl = null,
                    ),
                )
                hub.broadcastParticipants(roomId)
            }
        }
    }

    private suspend fun reconnect(call: ApplicationCall, roomId: String) {
        val request = call.receive<ReconnectRequest>()
        val rooms = store.roomManager()
        when (val result = rooms.reconnect(roomId, request.sessionToken)) {
            is RoomResult.Failure -> respondError(call, statusForCode(result.code), result.code, result.message)
            is RoomResult.Success -> {
                val value = result.value
                val mediaToken = mediaTokens.issue(roomId, value.participant.id)
                call.respond(
                    JoinResponse(
                        room = summaryFor(roomId) ?: return,
                        participant = value.participant,
                        sessionToken = request.sessionToken,
                        mediaToken = mediaToken,
                        mediaUrlPath = "$API_PREFIX/rooms/$roomId/video",
                        wsPath = "$API_PREFIX/rooms/$roomId/sync",
                        canControl = value.participant.controlsAllowed || value.participant.isHost,
                        shareUrl = null,
                    ),
                )
                hub.broadcastParticipants(roomId)
            }
        }
    }

    private suspend fun leaveRoom(call: ApplicationCall, roomId: String) {
        val request = call.receive<LeaveRequest>()
        val rooms = store.roomManager()
        when (val result = rooms.leave(roomId, request.sessionToken)) {
            is RoomResult.Failure -> respondError(call, statusForCode(result.code), result.code, result.message)
            is RoomResult.Success -> {
                val participant = result.value
                hub.disconnectParticipant(roomId, participant.id)
                hub.announceParticipantLeft(roomId, participant.id, participant.displayName)
                call.respond(ActionResponse(message = "Left the room."))
            }
        }
    }

    private suspend fun closeRoom(call: ApplicationCall, roomId: String) {
        val hostToken = hostToken(call) ?: run {
            respondError(call, HttpStatusCode.Unauthorized, ErrorCodes.UNAUTHORIZED, "Host token is required.")
            return
        }
        val rooms = store.roomManager()
        when (val result = rooms.closeRoom(roomId, hostToken)) {
            is RoomResult.Failure -> respondError(call, statusForCode(result.code), result.code, result.message)
            is RoomResult.Success -> {
                hub.closeRoom(roomId)
                store.onRoomClosed(roomId)
                call.respond(ActionResponse(message = "Room closed."))
            }
        }
    }

    private suspend fun getState(call: ApplicationCall, roomId: String) {
        if (!authorizeViewer(call, roomId)) {
            respondError(call, HttpStatusCode.Unauthorized, ErrorCodes.UNAUTHORIZED, "A session or media token is required.")
            return
        }
        val engine = store.syncEngine(roomId) ?: run {
            respondError(call, HttpStatusCode.NotFound, ErrorCodes.ROOM_NOT_FOUND, "Room is not active.")
            return
        }
        call.respond(engine.state())
    }

    private suspend fun getStats(call: ApplicationCall, roomId: String) {
        val hostToken = hostToken(call)
        if (hostToken == null || !store.roomManager().validateHost(roomId, hostToken)) {
            respondError(call, HttpStatusCode.Unauthorized, ErrorCodes.UNAUTHORIZED, "Host token is required.")
            return
        }
        call.respond(stats.snapshot())
    }

    private suspend fun refreshMediaToken(call: ApplicationCall, roomId: String) {
        val participant = authorizeSession(call, roomId)
        if (participant == null) {
            respondError(call, HttpStatusCode.Unauthorized, ErrorCodes.UNAUTHORIZED, "A valid session token is required.")
            return
        }
        call.respond(
            MediaTokenResponse(
                mediaToken = mediaTokens.issue(roomId, participant.id),
                expiresAt = mediaTokens.expiresAt(),
            ),
        )
    }

    private suspend fun removeParticipant(call: ApplicationCall, roomId: String, participantId: String) {
        val hostToken = hostToken(call) ?: run {
            respondError(call, HttpStatusCode.Unauthorized, ErrorCodes.UNAUTHORIZED, "Host token is required.")
            return
        }
        if (participantId.isBlank()) {
            respondError(call, HttpStatusCode.BadRequest, ErrorCodes.INVALID_REQUEST, "Participant id is required.")
            return
        }
        when (val result = store.roomManager().removeParticipant(roomId, hostToken, participantId)) {
            is RoomResult.Failure -> respondError(call, statusForCode(result.code), result.code, result.message)
            is RoomResult.Success -> {
                hub.kickParticipant(roomId, participantId)
                hub.broadcastParticipants(roomId)
                call.respond(ActionResponse(message = "Participant removed."))
            }
        }
    }

    private suspend fun revokeInvite(call: ApplicationCall, roomId: String) {
        val hostToken = hostToken(call) ?: run {
            respondError(call, HttpStatusCode.Unauthorized, ErrorCodes.UNAUTHORIZED, "Host token is required.")
            return
        }
        when (val result = store.roomManager().revokeInvites(roomId, hostToken)) {
            is RoomResult.Failure -> respondError(call, statusForCode(result.code), result.code, result.message)
            is RoomResult.Success -> {
                val invite = result.value
                call.respond(
                    HostRevokeResponse(
                        ok = true,
                        inviteToken = invite,
                        sharePath = ShareLinks.buildSharePath(roomId, invite),
                    ),
                )
            }
        }
    }

    private suspend fun setControls(call: ApplicationCall, roomId: String) {
        val request = call.receive<HostToggleRequest>()
        val hostToken = request.hostToken.ifBlank { hostToken(call) ?: "" }
        when (val result = store.roomManager().setControlsLocked(roomId, hostToken, !request.enabled)) {
            is RoomResult.Failure -> respondError(call, statusForCode(result.code), result.code, result.message)
            is RoomResult.Success -> {
                hub.broadcastControlPolicy(roomId)
                call.respond(ActionResponse(message = if (request.enabled) "Viewer controls enabled." else "Viewer controls locked."))
            }
        }
    }

    private suspend fun setJoinPolicy(call: ApplicationCall, roomId: String) {
        val request = call.receive<HostToggleRequest>()
        val hostToken = request.hostToken.ifBlank { hostToken(call) ?: "" }
        val rooms = store.roomManager()
        when (val result = rooms.setJoinEnabled(roomId, hostToken, request.enabled)) {
            is RoomResult.Failure -> respondError(call, statusForCode(result.code), result.code, result.message)
            is RoomResult.Success -> {
                val max = request.maxParticipants
                if (max != null) {
                    val maxResult = rooms.setMaxParticipants(roomId, hostToken, max)
                    if (maxResult is RoomResult.Failure) {
                        respondError(call, statusForCode(maxResult.code), maxResult.code, maxResult.message)
                        return
                    }
                }
                hub.broadcastParticipants(roomId)
                call.respond(ActionResponse(message = if (request.enabled) "Joining enabled." else "Joining disabled."))
            }
        }
    }

    // -----------------------------------------------------------------------
    // Video streaming
    // -----------------------------------------------------------------------

    private suspend fun streamVideo(call: ApplicationCall, roomId: String, headOnly: Boolean) {
        val rooms = store.roomManager()
        val snapshot = rooms.snapshot(roomId)
        if (snapshot == null) {
            respondError(call, HttpStatusCode.NotFound, ErrorCodes.ROOM_NOT_FOUND, "Room does not exist.")
            return
        }
        if (snapshot.status != RoomStatus.ACTIVE) {
            respondError(call, HttpStatusCode.Gone, ErrorCodes.ROOM_CLOSED, "This room is no longer active.")
            return
        }
        if (!authorizeViewer(call, roomId)) {
            respondError(call, HttpStatusCode.Forbidden, ErrorCodes.UNAUTHORIZED, "A valid media token is required.")
            return
        }
        val source = store.mediaSource(roomId) ?: run {
            respondError(call, HttpStatusCode.Conflict, ErrorCodes.MEDIA_UNAVAILABLE, "The host has not attached a playable video.")
            return
        }
        if (source.sizeBytes <= 0L) {
            respondError(call, HttpStatusCode.Conflict, ErrorCodes.MEDIA_UNAVAILABLE, "Video is empty or unreadable.")
            return
        }

        val rangeHeader = call.request.headers[HttpHeaders.Range]
        val range = RangeParser.parse(rangeHeader, source.sizeBytes)

        call.response.header("Accept-Ranges", "bytes")
        call.response.header("Cache-Control", "private, no-store")
        call.response.header("X-Content-Type-Options", "nosniff")

        if (range is RangeParseResult.NotSatisfiable) {
            call.response.header("Content-Range", RangeParser.unsatisfiedContentRange(source.sizeBytes))
            call.respond(
                HttpStatusCode.RequestedRangeNotSatisfiable,
                ErrorResponse(ErrorCodes.RANGE_NOT_SATISFIABLE, "Requested byte range is not satisfiable."),
            )
            return
        }

        val start: Long
        val length: Long
        val status: HttpStatusCode
        when (range) {
            is RangeParseResult.Full -> {
                start = 0L
                length = source.sizeBytes
                status = HttpStatusCode.OK
            }

            is RangeParseResult.Partial -> {
                start = range.range.start
                length = range.range.length
                status = HttpStatusCode.PartialContent
                call.response.header(
                    "Content-Range",
                    RangeParser.contentRange(range.range.start, range.range.endInclusive, source.sizeBytes),
                )
            }

            RangeParseResult.NotSatisfiable -> return
        }

        val contentType = try {
            source.mimeType?.let { ContentType.parse(it) }
        } catch (_: Exception) {
            null
        } ?: ContentType.Application.OctetStream

        if (headOnly) {
            // Do not set Content-Length on the response and then call respond(status):
            // Ktor's status-only content length is 0, which produces two Content-Length
            // headers. Go through OutgoingContent so the engine emits exactly one.
            call.respond(
                object : OutgoingContent.NoContent() {
                    override val status: HttpStatusCode = status
                    override val contentType: ContentType = contentType
                    override val contentLength: Long = length
                },
            )
            return
        }

        if (!streamPermits.tryAcquire()) {
            call.response.header("Retry-After", "2")
            respondError(
                call,
                HttpStatusCode.ServiceUnavailable,
                ErrorCodes.RATE_LIMITED,
                "The host is at its concurrent stream limit. Try again shortly.",
                retryAfterMs = 2_000L,
            )
            return
        }

        val input: InputStream = try {
            source.openStream(start)
        } catch (error: Exception) {
            streamPermits.release()
            logMessage("Media open failed: ${error::class.simpleName}")
            respondError(
                call,
                HttpStatusCode.Conflict,
                ErrorCodes.MEDIA_PERMISSION_REVOKED,
                "The host lost access to the selected video. Re-select it in the app.",
            )
            return
        }

        stats.onStreamOpened()
        try {
            call.respondOutputStream(contentType = contentType, status = status, contentLength = length) {
                withContext(Dispatchers.IO) {
                    val out = this@respondOutputStream
                    val buffer = ByteArray(BUFFER_SIZE)
                    var remaining = length
                    input.use { stream ->
                        while (remaining > 0L) {
                            currentCoroutineContext().ensureActive()
                            val toRead = min(buffer.size.toLong(), remaining).toInt()
                            val read = stream.read(buffer, 0, toRead)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            stats.addBytes(read.toLong())
                            remaining -= read.toLong()
                        }
                        out.flush()
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // A viewer navigating away closes the socket; this is expected.
            logMessage("Stream interrupted: ${error::class.simpleName}")
        } finally {
            runCatching { input.close() }
            stats.onStreamClosed()
            streamPermits.release()
        }
    }

    // -----------------------------------------------------------------------
    // Static browser client
    // -----------------------------------------------------------------------

    private fun Route.staticRoutes() {
        get("/") {
            serveWeb(call, "index.html")
        }
        get("/{path...}") {
            val path = call.parameters.getAll("path")?.joinToString("/").orEmpty()
            serveWeb(call, path)
        }
    }

    private suspend fun serveWeb(call: ApplicationCall, rawPath: String) {
        val safePath = rawPath.trimStart('/')
        if (safePath.contains("..") || safePath.contains('\\')) {
            respondError(call, HttpStatusCode.BadRequest, ErrorCodes.INVALID_REQUEST, "Invalid path.")
            return
        }
        val requested = safePath.ifBlank { "index.html" }
        val assets = webAssets
        if (assets == null) {
            if (requested.substringAfterLast('/', requested).contains('.')) {
                respondError(call, HttpStatusCode.NotFound, ErrorCodes.ROOM_NOT_FOUND, "Not found.")
            } else {
                call.respondText(FALLBACK_HTML, ContentType.Text.Html)
            }
            return
        }

        val asset = assets.open(requested)
            ?: if (!requested.substringAfterLast('/', requested).contains('.')) {
                assets.open("index.html")
            } else {
                null
            }

        if (asset == null) {
            respondError(call, HttpStatusCode.NotFound, ErrorCodes.ROOM_NOT_FOUND, "Not found.")
            return
        }

        call.response.header("X-Content-Type-Options", "nosniff")
        call.response.header("Referrer-Policy", "same-origin")
        call.response.header(
            "Cache-Control",
            if (asset.immutable) "public, max-age=31536000, immutable" else "no-cache",
        )
        call.respondOutputStream(
            contentType = ContentType.parse(asset.contentType),
            status = HttpStatusCode.OK,
        ) {
            withContext(Dispatchers.IO) {
                val out = this@respondOutputStream
                asset.stream.use { it.copyTo(out, BUFFER_SIZE) }
                out.flush()
            }
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun summaryFor(roomId: String): RoomSummaryDto? {
        val snapshot = store.roomManager().snapshot(roomId) ?: return null
        val state = store.syncEngine(roomId)?.state()
        return RoomSummaryDto(
            roomId = snapshot.roomId,
            roomName = snapshot.roomName,
            hostName = snapshot.hostName,
            status = snapshot.status,
            media = snapshot.media,
            isPlaying = state?.isPlaying ?: false,
            positionMs = state?.positionMs ?: 0L,
            playbackSpeed = state?.playbackSpeed ?: 1f,
            participantCount = snapshot.participantCount,
            maxParticipants = snapshot.maxParticipants,
            hostOnline = snapshot.hostOnline,
            createdAt = snapshot.createdAt,
            expiresAt = snapshot.expiresAt,
            canJoin = snapshot.canJoin,
            controlsLocked = snapshot.controlsLocked,
            shareUrl = null,
        )
    }

    private fun authorizeSession(call: ApplicationCall, roomId: String): ParticipantDto? {
        val token = sessionToken(call) ?: return null
        return when (val result = store.roomManager().validateSession(roomId, token)) {
            is RoomResult.Success -> result.value.participant
            is RoomResult.Failure -> null
        }
    }

    private fun authorizeViewer(call: ApplicationCall, roomId: String): Boolean {
        if (authorizeSession(call, roomId) != null) return true
        val mediaToken = call.request.queryParameters["t"]?.takeIf { it.isNotBlank() } ?: return false
        val claims = mediaTokens.validate(mediaToken, roomId) ?: return false
        return store.roomManager().participant(roomId, claims.participantId) != null
    }

    private fun sessionToken(call: ApplicationCall): String? =
        bearer(call) ?: call.request.queryParameters["token"]?.takeIf { it.isNotBlank() }

    private fun bearer(call: ApplicationCall): String? =
        call.request.headers[HttpHeaders.Authorization]
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.substring(7)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun hostToken(call: ApplicationCall): String? =
        call.request.headers[HOST_HEADER]?.takeIf { it.isNotBlank() }
            ?: call.request.queryParameters["hostToken"]?.takeIf { it.isNotBlank() }

    private fun requiredRoomId(call: ApplicationCall): String = call.parameters["roomId"].orEmpty()

    private fun allowJoin(clientIp: String): Boolean {
        if (joinLimiters.size > 2_048) joinLimiters.clear()
        val limiter = joinLimiters.computeIfAbsent(clientIp) {
            SlidingWindowRateLimiter(cfg.joinLimitPerWindow, cfg.joinWindowMs)
        }
        return limiter.tryAcquire(clock.wallMs())
    }

    private fun clientIp(call: ApplicationCall): String {
        val forwarded = call.request.headers["X-Forwarded-For"]
            ?.substringBefore(',')
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it.length <= 64 }
        return forwarded ?: call.request.origin.remoteHost.ifBlank { "unknown" }
    }

    private suspend fun respondError(
        call: ApplicationCall,
        status: HttpStatusCode,
        code: String,
        message: String,
        retryAfterMs: Long? = null,
    ) {
        if (retryAfterMs != null) {
            call.response.header("Retry-After", ((retryAfterMs + 999L) / 1_000L).toString())
        }
        call.respond(status, ErrorResponse(code, message, retryAfterMs))
    }

    private fun statusForCode(code: String): HttpStatusCode = when (code) {
        ErrorCodes.ROOM_NOT_FOUND -> HttpStatusCode.NotFound
        ErrorCodes.ROOM_CLOSED, ErrorCodes.ROOM_EXPIRED -> HttpStatusCode.Gone
        ErrorCodes.UNAUTHORIZED, ErrorCodes.FORBIDDEN, ErrorCodes.INVITE_INVALID,
        ErrorCodes.INVALID_TOKEN, ErrorCodes.CONTROLS_LOCKED -> HttpStatusCode.Forbidden
        ErrorCodes.ROOM_FULL, ErrorCodes.JOIN_DISABLED -> HttpStatusCode.Conflict
        ErrorCodes.RATE_LIMITED -> HttpStatusCode.TooManyRequests
        else -> HttpStatusCode.BadRequest
    }

    private fun applyDevCors(call: ApplicationCall) {
        val origin = call.request.headers[HttpHeaders.Origin] ?: return
        if (origin in cfg.allowedDevOrigins) {
            call.response.header(HttpHeaders.AccessControlAllowOrigin, origin)
            call.response.header(HttpHeaders.AccessControlAllowMethods, "GET, POST, HEAD, OPTIONS")
            call.response.header(
                HttpHeaders.AccessControlAllowHeaders,
                "Content-Type, Authorization, $HOST_HEADER, $ADMIN_HEADER",
            )
            call.response.header("Access-Control-Max-Age", "600")
            call.response.header(HttpHeaders.Vary, "Origin")
        }
    }

    private suspend fun cleanupLoop() {
        while (currentCoroutineContext().isActive) {
            delay(cfg.cleanupIntervalMs)
            runCatching {
                val rooms = store.roomManager()
                rooms.purgeExpired().forEach { roomId ->
                    runCatching { hub.closeRoom(roomId, "This room expired.") }
                    runCatching { store.onRoomClosed(roomId) }
                }
                rooms.removeStaleParticipants(cfg.staleParticipantGraceMs)
                    .map { it.roomId }
                    .distinct()
                    .forEach { roomId -> runCatching { hub.broadcastParticipants(roomId) } }
            }.onFailure { logMessage("Cleanup failed: ${it::class.simpleName}") }
        }
    }

    companion object {
        private const val BUFFER_SIZE = 64 * 1024
        private const val HOST_HEADER = "X-WatchWave-Host-Token"
        private const val ADMIN_HEADER = "X-WatchWave-Admin"

        private val FALLBACK_HTML = """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <title>WatchWave host</title>
              <style>
                body { margin:0; background:#07080c; color:#f5f7ff; font-family:system-ui,sans-serif; display:grid; place-items:center; min-height:100vh; }
                main { max-width:32rem; padding:2rem; }
                h1 { font-size:1.9rem; margin:0 0 .6rem; }
                p { color:#8a93a6; line-height:1.6; }
                code { background:#161a23; padding:.15rem .4rem; border-radius:.35rem; color:#22d3ee; }
              </style>
            </head>
            <body><main>
              <h1>WatchWave is running</h1>
              <p>The browser client was not bundled into this APK. Build the web app and rebuild, or deploy
              <code>web/dist</code> to any static host that points at this server.</p>
              <p>Health endpoint: <code>/api/v1/health</code>. API: <code>/api/v1</code>.</p>
            </main></body>
            </html>
        """.trimIndent()
    }
}
