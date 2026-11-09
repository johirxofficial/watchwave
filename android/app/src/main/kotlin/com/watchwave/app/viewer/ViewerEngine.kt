package com.watchwave.app.viewer

import android.content.Context
import com.watchwave.app.core.AppLog
import com.watchwave.app.media.MediaRepository
import com.watchwave.app.net.ConnectionPhase
import com.watchwave.app.net.LocalPlaybackState
import com.watchwave.app.net.RoomApi
import com.watchwave.app.net.RoomApiException
import com.watchwave.app.net.RoomClient
import com.watchwave.app.net.RoomConnectionState
import com.watchwave.app.net.RoomLink
import com.watchwave.app.playback.HostStreamingService
import com.watchwave.app.playback.PlaybackController
import com.watchwave.app.playback.PlaybackSync
import com.watchwave.data.settings.AppSettings
import com.watchwave.shared.protocol.RoomSummaryDto
import com.watchwave.shared.sync.DriftConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ViewerPhase { IDLE, JOINING, LIVE, LEAVING, FAILED }

data class ViewerState(
    val phase: ViewerPhase = ViewerPhase.IDLE,
    val room: RoomSummaryDto? = null,
    val roomId: String? = null,
    val baseUrl: String? = null,
    val displayName: String? = null,
    val error: String? = null,
)

/**
 * Android viewer session. Joins over HTTP, then uses the same WebSocket protocol as the
 * browser client. It is a real client of the remote host — never a second authority.
 */
class ViewerEngine(
    private val context: Context,
    private val playback: PlaybackController,
    private val mediaRepository: MediaRepository,
    private val appLog: AppLog,
    private val scope: CoroutineScope,
    private val stopHostSession: suspend () -> Unit,
) {
    private val _state = MutableStateFlow(ViewerState())
    val state: StateFlow<ViewerState> = _state.asStateFlow()

    private val _connection = MutableStateFlow(RoomConnectionState(phase = ConnectionPhase.IDLE))
    val connection: StateFlow<RoomConnectionState> = _connection.asStateFlow()

    private val _localPlayback = MutableStateFlow(LocalPlaybackState())
    val localPlayback: StateFlow<LocalPlaybackState> = _localPlayback.asStateFlow()

    private var api: RoomApi? = null
    private var client: RoomClient? = null
    private var playbackSync: PlaybackSync? = null
    private var sessionToken: String = ""
    private var roomId: String = ""
    private var historyId: Long? = null
    private var observationJobs: List<Job> = emptyList()
    private var activeSettings: AppSettings? = null

    suspend fun join(link: RoomLink, displayName: String, settings: AppSettings): Result<Unit> {
        if (_state.value.phase == ViewerPhase.LIVE || _state.value.phase == ViewerPhase.JOINING) {
            leave()
        }
        _state.value = ViewerState(phase = ViewerPhase.JOINING, roomId = link.roomId, baseUrl = link.baseUrl)
        _connection.value = RoomConnectionState(phase = ConnectionPhase.CONNECTING)
        _localPlayback.value = LocalPlaybackState()
        activeSettings = settings

        return runCatching {
            appLog.log("Joining room ${link.roomId}")
            stopHostSession()

            val api = RoomApi(link.baseUrl)
            this.api = api
            val preview = api.room(link.roomId, link.inviteToken)
            if (!preview.canJoin) {
                throw RoomApiException("JOIN_DISABLED", "The host is not accepting new participants.", 409)
            }
            val response = api.join(link.roomId, displayName, link.inviteToken)
            sessionToken = response.sessionToken
            roomId = response.room.roomId

            val client = RoomClient(
                baseUrl = link.baseUrl,
                roomId = response.room.roomId,
                sessionToken = response.sessionToken,
                initialMediaToken = response.mediaToken,
                mediaPath = response.mediaUrlPath,
                wsPath = response.wsPath,
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

            _state.value = ViewerState(
                phase = ViewerPhase.LIVE,
                room = response.room,
                roomId = response.room.roomId,
                baseUrl = link.baseUrl,
                displayName = response.participant.displayName,
            )
            observeSession(client, sync)
            client.connect()
            HostStreamingService.start(context)
            historyId = mediaRepository.recordRoomStarted(
                roomId = response.room.roomId,
                roomName = response.room.roomName,
                videoTitle = response.room.media?.title ?: "Video",
                role = "viewer",
            )
            appLog.log("Joined ${response.room.roomName} as ${response.participant.displayName}")
        }.onFailure { error ->
            appLog.log("Join failed: ${error::class.java.simpleName}")
            runCatching { cleanup() }
            _state.value = ViewerState(
                phase = ViewerPhase.FAILED,
                roomId = link.roomId,
                baseUrl = link.baseUrl,
                error = when (error) {
                    is RoomApiException -> error.message
                    else -> error.message ?: "Could not join the room."
                },
            )
            _connection.value = RoomConnectionState(phase = ConnectionPhase.FAILED)
        }
    }

    suspend fun leave() {
        if (_state.value.phase == ViewerPhase.IDLE) return
        _state.update { it.copy(phase = ViewerPhase.LEAVING) }
        val currentRoom = roomId
        val token = sessionToken
        if (currentRoom.isNotBlank() && token.isNotBlank()) {
            runCatching { api?.leave(currentRoom, token) }
        }
        cleanup()
        _state.value = ViewerState(phase = ViewerPhase.IDLE)
        _connection.value = RoomConnectionState(phase = ConnectionPhase.IDLE)
        _localPlayback.value = LocalPlaybackState()
        appLog.log("Left the viewer session")
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
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

    private fun observeSession(client: RoomClient, sync: PlaybackSync) {
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
                                hostOnline = connection.participants.any { it.isHost && it.connected },
                            ),
                        )
                    }
                }
            },
            scope.launch { sync.state.collect { _localPlayback.value = it } },
        )
    }

    private suspend fun cleanup() {
        observationJobs.forEach { it.cancel() }
        observationJobs = emptyList()
        playbackSync?.release()
        playbackSync = null
        client?.close()
        client = null
        playback.stop()
        runCatching { HostStreamingService.stop(context) }
        api?.close()
        api = null
        historyId?.let { id -> runCatching { mediaRepository.recordRoomEnded(id) } }
        historyId = null
        sessionToken = ""
        roomId = ""
        activeSettings = null
    }

    private fun currentDriftConfig(): DriftConfig {
        val settings = activeSettings ?: return DriftConfig()
        return DriftConfig(
            smallThresholdMs = settings.driftSmallMs,
            mediumThresholdMs = settings.driftMediumMs,
            largeThresholdMs = settings.driftLargeMs,
        )
    }
}
