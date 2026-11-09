package com.watchwave.app.host

import android.net.Uri
import com.watchwave.app.core.AppLog
import com.watchwave.app.core.NetworkUtils
import com.watchwave.app.media.ContentUriMediaSource
import com.watchwave.app.media.MediaRepository
import com.watchwave.app.net.ConnectionPhase
import com.watchwave.app.net.LocalPlaybackState
import com.watchwave.app.net.RoomClient
import com.watchwave.app.net.RoomConnectionState
import com.watchwave.app.playback.HostStreamingService
import com.watchwave.app.playback.PlaybackController
import com.watchwave.app.playback.PlaybackSync
import com.watchwave.app.security.SecureStore
import com.watchwave.data.db.VideoMetadataEntity
import com.watchwave.data.settings.AppSettings
import com.watchwave.data.settings.TunnelMode as SettingsTunnelMode
import com.watchwave.shared.protocol.CreateRoomRequest
import com.watchwave.shared.protocol.ErrorCodes
import com.watchwave.shared.protocol.MediaDescriptorDto
import com.watchwave.shared.protocol.RoomStatus
import com.watchwave.shared.protocol.RoomSummaryDto
import com.watchwave.shared.protocol.TunnelStatusDto
import com.watchwave.shared.protocol.UploadStatsDto
import com.watchwave.shared.room.CreateRoomParams
import com.watchwave.shared.room.CreateRoomResult
import com.watchwave.shared.room.RoomManager
import com.watchwave.shared.room.RoomResult
import com.watchwave.shared.security.MediaTokenManager
import com.watchwave.shared.security.ShareLinks
import com.watchwave.shared.security.SystemClock
import com.watchwave.shared.sync.DriftConfig
import com.watchwave.shared.sync.SyncEngine
import com.watchwave.streaming.MediaSource
import com.watchwave.streaming.ServerConfig
import com.watchwave.streaming.ServerRoomStore
import com.watchwave.streaming.StreamingStats
import com.watchwave.streaming.WatchWaveServer
import com.watchwave.streaming.WebAssetsProvider
import com.watchwave.tunnel.TunnelConfig
import com.watchwave.tunnel.TunnelManager
import com.watchwave.tunnel.TunnelMode as ProviderTunnelMode
import com.watchwave.tunnel.TunnelState
import com.watchwave.tunnel.TunnelStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap

enum class HostPhase { IDLE, STARTING, LIVE, STOPPING, FAILED }

data class HostState(
    val phase: HostPhase = HostPhase.IDLE,
    val roomId: String? = null,
    val roomName: String? = null,
    val hostName: String? = null,
    val mediaTitle: String? = null,
    val mediaUri: String? = null,
    val inviteToken: String? = null,
    val joinEnabled: Boolean = true,
    val controlsLocked: Boolean = false,
    val serverPort: Int? = null,
    val maxParticipants: Int = 10,
    val expiresAt: Long? = null,
    val room: RoomSummaryDto? = null,
    val lanUrl: String? = null,
    val tunnelUrl: String? = null,
    val shareUrl: String? = null,
    val tunnel: TunnelStatus = TunnelStatus(TunnelState.NOT_CONFIGURED, "none"),
    val error: String? = null,
    val warnings: List<String> = emptyList(),
) {
    val isLive: Boolean get() = phase == HostPhase.LIVE
}

/**
 * Owns one host room for the process lifetime: the embedded HTTP server, room registry,
 * tunnel, authoritative sync engine and host playback bridge.
 *
 * Room and host secrets are memory-only. Closing the room (or the process dying) makes
 * every previously shared link invalid.
 */
class HostEngine(
    private val context: android.content.Context,
    private val playback: PlaybackController,
    private val mediaRepository: MediaRepository,
    private val tunnelManager: TunnelManager,
    private val secureStore: SecureStore,
    private val webAssets: WebAssetsProvider?,
    private val appLog: AppLog,
    private val scope: CoroutineScope,
    private val stopViewerSession: suspend () -> Unit,
) : ServerRoomStore {

    private val rooms = RoomManager()
    private val mediaTokens = MediaTokenManager(MediaTokenManager.randomSecret())
    private val engines = ConcurrentHashMap<String, SyncEngine>()
    private val sources = ConcurrentHashMap<String, MediaSource>()

    private val _state = MutableStateFlow(HostState())
    val state: StateFlow<HostState> = _state.asStateFlow()

    private val _connection = MutableStateFlow(RoomConnectionState(phase = ConnectionPhase.IDLE))
    val connection: StateFlow<RoomConnectionState> = _connection.asStateFlow()

    private val _localPlayback = MutableStateFlow(LocalPlaybackState())
    val localPlayback: StateFlow<LocalPlaybackState> = _localPlayback.asStateFlow()

    private val _stats = MutableStateFlow(UploadStatsDto())
    val stats: StateFlow<UploadStatsDto> = _stats.asStateFlow()

    private var server: WatchWaveServer? = null
    private var statsTracker: StreamingStats? = null
    private var client: RoomClient? = null
    private var playbackSync: PlaybackSync? = null
    private var hostToken: String = ""
    private var hostSessionToken: String = ""
    private var hostParticipantId: String = ""
    private var historyId: Long? = null
    private var activeSettings: AppSettings? = null
    private var lanBaseUrl: String? = null
    private var observationJobs: List<Job> = emptyList()
    private var shuttingDown: Boolean = false

    suspend fun start(
        video: VideoMetadataEntity,
        settings: AppSettings,
        roomName: String = "Watch Party",
        allowViewerControls: Boolean = true,
    ): Result<Unit> {
        if (_state.value.phase == HostPhase.STARTING || _state.value.phase == HostPhase.LIVE) {
            stop()
        }
        shuttingDown = false
        _state.value = HostState(phase = HostPhase.STARTING)
        _connection.value = RoomConnectionState(phase = ConnectionPhase.CONNECTING)
        _localPlayback.value = LocalPlaybackState()
        activeSettings = settings

        return runCatching {
            appLog.log("Starting host session")
            stopViewerSession()

            val port = findAvailablePort(settings.serverPort)
                ?: error("No free TCP port near ${settings.serverPort}.")
            val stats = StreamingStats()
            val server = WatchWaveServer(
                store = this,
                mediaTokens = mediaTokens,
                stats = stats,
                cfg = ServerConfig(host = "0.0.0.0", port = port),
                webAssets = webAssets,
                tunnelStatus = { tunnelStatusDto() },
                clock = SystemClock,
                logMessage = appLog::log,
            )
            this.server = server
            this.statsTracker = stats
            server.start()

            val result = rooms.createRoom(
                CreateRoomParams(
                    roomName = roomName.ifBlank { "Watch Party" },
                    hostName = settings.displayName,
                    media = MediaDescriptorDto(
                        mediaId = video.uri,
                        title = video.title,
                        durationMs = video.durationMs,
                        mimeType = video.mimeType,
                        sizeBytes = video.sizeBytes,
                    ),
                    maxParticipants = settings.maxParticipants,
                    ttlMinutes = settings.roomTtlMinutes,
                ),
            )
            val created = (result as? RoomResult.Success)?.value
                ?: error((result as? RoomResult.Failure)?.message ?: "Could not create the room.")

            hostToken = created.hostToken
            hostSessionToken = created.hostSessionToken
            hostParticipantId = created.hostParticipantId

            if (!allowViewerControls) {
                rooms.setControlsLocked(created.snapshot.roomId, hostToken, true)
            }

            engines[created.snapshot.roomId] = SyncEngine(created.snapshot.roomId, created.snapshot.media)
            sources[created.snapshot.roomId] = ContentUriMediaSource(
                context = context,
                uri = Uri.parse(video.uri),
                sizeBytes = video.sizeBytes,
                mimeType = video.mimeType,
            )

            tunnelManager.start(buildTunnelConfig(settings), port)

            val baseUrl = "http://127.0.0.1:$port"
            val hostMediaToken = mediaTokens.issue(created.snapshot.roomId, hostParticipantId)
            val client = RoomClient(
                baseUrl = baseUrl,
                roomId = created.snapshot.roomId,
                sessionToken = hostSessionToken,
                initialMediaToken = hostMediaToken,
                mediaPath = "/api/v1/rooms/${created.snapshot.roomId}/video",
                wsPath = "/api/v1/rooms/${created.snapshot.roomId}/sync",
                scope = scope,
                autoReconnect = settings.autoReconnect,
                logger = appLog::log,
            )
            this.client = client
            val sync = PlaybackSync(
                client = client,
                playback = playback,
                scope = scope,
                driftConfig = { currentDriftConfig() },
                logger = appLog::log,
            )
            playbackSync = sync

            lanBaseUrl = NetworkUtils.lanBaseUrl(port)
            val lanShare = lanBaseUrl?.let {
                ShareLinks.buildShareUrl(it, created.snapshot.roomId, created.inviteToken)
            }

            _state.value = HostState(
                phase = HostPhase.LIVE,
                roomId = created.snapshot.roomId,
                roomName = created.snapshot.roomName,
                hostName = settings.displayName,
                mediaTitle = video.title,
                mediaUri = video.uri,
                inviteToken = created.inviteToken,
                joinEnabled = true,
                controlsLocked = !allowViewerControls,
                serverPort = port,
                maxParticipants = created.snapshot.maxParticipants,
                expiresAt = created.snapshot.expiresAt,
                room = summaryFor(created.snapshot.roomId),
                lanUrl = lanShare,
                shareUrl = lanShare,
            )

            observeSession(created.snapshot.roomId, client, sync)
            client.connect()
            HostStreamingService.start(context)
            historyId = mediaRepository.recordRoomStarted(
                roomId = created.snapshot.roomId,
                roomName = created.snapshot.roomName,
                videoTitle = video.title,
                role = "host",
            )
            mediaRepository.markPlayed(video.uri, 0L)
            appLog.log("Room ${created.snapshot.roomId} listening on port $port")
        }.onFailure { error ->
            appLog.log("Host start failed: ${error::class.simpleName}")
            runCatching { stopInternal() }
            _state.value = HostState(
                phase = HostPhase.FAILED,
                error = error.message ?: "Could not start hosting.",
            )
            _connection.value = RoomConnectionState(phase = ConnectionPhase.FAILED)
        }
    }

    suspend fun stop() {
        if (shuttingDown) return
        stopInternal()
        _state.value = HostState(phase = HostPhase.IDLE)
        _connection.value = RoomConnectionState(phase = ConnectionPhase.IDLE)
        _localPlayback.value = LocalPlaybackState()
        _stats.value = UploadStatsDto()
    }

    fun setControlsEnabled(enabled: Boolean) {
        val roomId = _state.value.roomId ?: return
        when (val result = rooms.setControlsLocked(roomId, hostToken, !enabled)) {
            is RoomResult.Success -> {
                _state.update { it.copy(controlsLocked = !enabled) }
                server?.notifyControlPolicyChanged(roomId)
                appLog.log(if (enabled) "Viewer controls enabled" else "Viewer controls locked")
            }

            is RoomResult.Failure -> _state.update { it.copy(error = result.message) }
        }
    }

    fun setJoinEnabled(enabled: Boolean) {
        val roomId = _state.value.roomId ?: return
        when (val result = rooms.setJoinEnabled(roomId, hostToken, enabled)) {
            is RoomResult.Success -> {
                _state.update { it.copy(joinEnabled = enabled) }
                server?.notifyParticipantsChanged(roomId)
                appLog.log(if (enabled) "Joining enabled" else "Joining disabled")
            }

            is RoomResult.Failure -> _state.update { it.copy(error = result.message) }
        }
    }

    fun setMaxParticipants(max: Int) {
        val roomId = _state.value.roomId ?: return
        when (val result = rooms.setMaxParticipants(roomId, hostToken, max)) {
            is RoomResult.Success -> {
                _state.update { it.copy(maxParticipants = max) }
                server?.notifyParticipantsChanged(roomId)
            }

            is RoomResult.Failure -> _state.update { it.copy(error = result.message) }
        }
    }

    fun kickParticipant(participantId: String) {
        val roomId = _state.value.roomId ?: return
        when (val result = rooms.removeParticipant(roomId, hostToken, participantId)) {
            is RoomResult.Success -> {
                server?.kickParticipant(roomId, participantId)
                server?.notifyParticipantsChanged(roomId)
                appLog.log("Removed a participant")
            }

            is RoomResult.Failure -> _state.update { it.copy(error = result.message) }
        }
    }

    fun revokeInvite() {
        val roomId = _state.value.roomId ?: return
        when (val result = rooms.revokeInvites(roomId, hostToken)) {
            is RoomResult.Success -> {
                val invite = result.value
                val lanShare = lanBaseUrl?.let { ShareLinks.buildShareUrl(it, roomId, invite) }
                val tunnelShare = _state.value.tunnel.activeUrl?.let {
                    ShareLinks.buildShareUrl(it, roomId, invite)
                }
                _state.update {
                    it.copy(
                        inviteToken = invite,
                        lanUrl = lanShare,
                        tunnelUrl = tunnelShare,
                        shareUrl = tunnelShare ?: lanShare,
                    )
                }
                appLog.log("Invitation link rotated; old links no longer work")
            }

            is RoomResult.Failure -> _state.update { it.copy(error = result.message) }
        }
    }

    fun refreshNow() {
        scope.launch { refreshSnapshot() }
    }

    fun togglePlayback() {
        playbackSync?.toggle()
    }

    fun seekPlaybackTo(positionMs: Long) {
        playbackSync?.seekTo(positionMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        playbackSync?.setSpeed(speed)
    }

    fun canControlPlayback(): Boolean = playbackSync?.canControl() == true

    fun activeConnections(): Int {
        val roomId = _state.value.roomId ?: return 0
        return server?.activeConnections(roomId) ?: 0
    }

    fun serverRunning(): Boolean = server?.isRunning() == true

    /** Re-runs the configured connector and re-verifies reachability. */
    fun restartTunnel() {
        val port = _state.value.serverPort ?: return
        val settings = activeSettings ?: return
        scope.launch {
            runCatching { tunnelManager.start(buildTunnelConfig(settings), port) }
                .onFailure { _state.update { state -> state.copy(error = it.message) } }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null, warnings = emptyList()) }
    }

    // -----------------------------------------------------------------------
    // ServerRoomStore
    // -----------------------------------------------------------------------

    override fun roomManager(): RoomManager = rooms

    override fun syncEngine(roomId: String): SyncEngine? = engines[roomId]

    override fun mediaSource(roomId: String): MediaSource? = sources[roomId]

    override suspend fun createRoom(request: CreateRoomRequest): RoomResult<CreateRoomResult> =
        RoomResult.Failure(
            ErrorCodes.FORBIDDEN,
            "Rooms are created from the WatchWave app, not the public API.",
        )

    override suspend fun onRoomClosed(roomId: String) {
        appLog.log("Room $roomId ended")
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    private fun observeSession(roomId: String, client: RoomClient, sync: PlaybackSync) {
        observationJobs.forEach { it.cancel() }
        observationJobs = listOf(
            scope.launch {
                client.connection.collect { connection ->
                    _connection.value = connection
                    _state.update { state ->
                        val room = state.room
                        state.copy(
                            room = room?.copy(
                                participantCount = connection.participants.size,
                                controlsLocked = connection.controlsLocked,
                                canJoin = state.joinEnabled,
                            ),
                        )
                    }
                    if (connection.phase == ConnectionPhase.ENDED && !shuttingDown) {
                        val message = connection.message ?: "The room ended."
                        appLog.log("Host connection ended: $message")
                    }
                }
            },
            scope.launch {
                sync.state.collect { _localPlayback.value = it }
            },
            scope.launch {
                tunnelManager.status.collect { applyTunnelStatus(it) }
            },
            scope.launch {
                while (isActive && !shuttingDown) {
                    delay(2_000L)
                    refreshSnapshot()
                    _stats.value = statsTracker?.snapshot() ?: UploadStatsDto()
                }
            },
            scope.launch {
                while (isActive && !shuttingDown) {
                    delay(15_000L)
                    tunnelManager.status.value.let { applyTunnelStatus(it) }
                }
            },
            scope.launch {
                // Keep the room's participant count fresh even when nobody sends a frame.
                delay(500L)
                refreshSnapshot()
            },
        )
    }

    private suspend fun refreshSnapshot() {
        val roomId = _state.value.roomId ?: return
        val snapshot = rooms.snapshot(roomId) ?: return
        _state.update {
            it.copy(
                room = summaryFor(roomId),
                joinEnabled = snapshot.canJoin,
                controlsLocked = snapshot.controlsLocked,
                maxParticipants = snapshot.maxParticipants,
                expiresAt = snapshot.expiresAt,
            )
        }
        if (snapshot.status != RoomStatus.ACTIVE && _state.value.phase == HostPhase.LIVE) {
            appLog.log("Room is no longer active; stopping the host session")
            scope.launch { stop() }
        }
    }

    private fun summaryFor(roomId: String): RoomSummaryDto? {
        val snapshot = rooms.snapshot(roomId) ?: return null
        val engine = engines[roomId]
        val state = engine?.state()
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
        )
    }

    private fun applyTunnelStatus(status: TunnelStatus) {
        val roomId = _state.value.roomId ?: return
        val invite = _state.value.inviteToken ?: return
        val tunnelShare = status.activeUrl?.let { ShareLinks.buildShareUrl(it, roomId, invite) }
        val warnings = when (status.state) {
            TunnelState.FAILED -> listOfNotNull(status.message)
            TunnelState.NOT_INSTALLED -> listOfNotNull(status.message)
            else -> emptyList()
        }
        _state.update {
            it.copy(
                tunnel = status,
                tunnelUrl = tunnelShare,
                shareUrl = tunnelShare ?: it.lanUrl,
                warnings = warnings,
            )
        }
    }

    private suspend fun stopInternal() {
        if (shuttingDown) return
        shuttingDown = true
        _state.update { it.copy(phase = HostPhase.STOPPING) }

        observationJobs.forEach { it.cancel() }
        observationJobs = emptyList()

        val roomId = _state.value.roomId
        if (roomId != null) {
            server?.notifyRoomClosed(roomId)
            if (hostToken.isNotBlank()) {
                runCatching { rooms.closeRoom(roomId, hostToken) }
            }
        }

        playbackSync?.release()
        playbackSync = null
        client?.close()
        client = null
        playback.stop()
        runCatching { HostStreamingService.stop(context) }
        runCatching { tunnelManager.stop() }
        runCatching { server?.stop() }
        server = null
        statsTracker = null

        historyId?.let { id ->
            runCatching { mediaRepository.recordRoomEnded(id) }
        }
        historyId = null

        engines.clear()
        sources.clear()
        runCatching { rooms.closeAll() }
        hostToken = ""
        hostSessionToken = ""
        hostParticipantId = ""
        lanBaseUrl = null
        activeSettings = null
        shuttingDown = false
        appLog.log("Host session stopped")
    }

    private fun buildTunnelConfig(settings: AppSettings): TunnelConfig = when (settings.tunnelMode) {
        SettingsTunnelMode.NONE -> TunnelConfig(mode = ProviderTunnelMode.NONE)
        SettingsTunnelMode.COMPANION_URL -> TunnelConfig(
            mode = ProviderTunnelMode.COMPANION_URL,
            companionUrl = settings.companionPublicUrl,
        )

        SettingsTunnelMode.CLOUDFLARED_PROCESS -> TunnelConfig(
            mode = ProviderTunnelMode.CLOUDFLARED_PROCESS,
            executablePath = settings.cloudflaredPath,
            tunnelToken = secureStore.get(SecureStore.CLOUDFLARED_TOKEN).orEmpty(),
            namedHostname = settings.namedTunnelHostname,
        )
    }

    private fun currentDriftConfig(): DriftConfig {
        val settings = activeSettings ?: return DriftConfig()
        return DriftConfig(
            smallThresholdMs = settings.driftSmallMs,
            mediumThresholdMs = settings.driftMediumMs,
            largeThresholdMs = settings.driftLargeMs,
        )
    }

    private fun tunnelStatusDto(): TunnelStatusDto {
        val status = tunnelManager.status.value
        return TunnelStatusDto(
            state = status.state.name,
            provider = status.provider,
            publicUrl = status.publicUrl,
            verified = status.verified,
            message = status.message,
            updatedAt = status.updatedAt,
        )
    }

    private fun findAvailablePort(preferred: Int): Int? {
        val start = preferred.coerceIn(1024, 65_000)
        for (port in start until (start + 25).coerceAtMost(65_536)) {
            try {
                ServerSocket(port).use { return port }
            } catch (_: Exception) {
                // Port busy; try the next one.
            }
        }
        return null
    }
}
