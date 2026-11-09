package com.watchwave.streaming

import com.watchwave.shared.protocol.CreateRoomRequest
import com.watchwave.shared.protocol.TunnelStatusDto
import com.watchwave.shared.room.CreateRoomResult
import com.watchwave.shared.room.RoomManager
import com.watchwave.shared.room.RoomResult
import com.watchwave.shared.sync.SyncEngine

/**
 * The server is deliberately independent from the Android host: it only knows the
 * room registry, authoritative sync engines and opaque media sources. This keeps the
 * network layer unit-testable and prevents HTTP code from reaching into storage.
 */
interface ServerRoomStore {
    fun roomManager(): RoomManager
    fun syncEngine(roomId: String): SyncEngine?
    fun mediaSource(roomId: String): MediaSource?

    /**
     * Admin-only room bootstrap used by `POST /api/rooms`. The host app supplies a
     * media source for [CreateRoomRequest.mediaId] before calling it.
     */
    suspend fun createRoom(request: CreateRoomRequest): RoomResult<CreateRoomResult>

    suspend fun onRoomClosed(roomId: String) {}
}

data class ServerConfig(
    val host: String = "0.0.0.0",
    val port: Int = 8080,
    /** Full origins allowed to call the API cross-origin in development. */
    val allowedDevOrigins: Set<String> = setOf(
        "http://localhost:5173",
        "http://127.0.0.1:5173",
    ),
    /** Required for the admin-only POST /api/rooms. Blank disables that endpoint. */
    val adminToken: String = "",
    val joinLimitPerWindow: Int = 20,
    val joinWindowMs: Long = 60_000L,
    val maxConcurrentStreams: Int = 24,
    val staleParticipantGraceMs: Long = 120_000L,
    val cleanupIntervalMs: Long = 30_000L,
    val wsHeartbeatMs: Long = 15_000L,
)

fun interface TunnelStatusSource {
    fun status(): TunnelStatusDto
}
