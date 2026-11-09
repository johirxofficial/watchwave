package com.watchwave.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "videos")
data class VideoMetadataEntity(
    @PrimaryKey val uri: String,
    val title: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val mimeType: String?,
    val addedAt: Long,
    val lastPlayedAt: Long? = null,
)

@Entity(tableName = "recent_media")
data class RecentMediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val uri: String,
    val title: String,
    val playedAt: Long,
    val positionMs: Long,
)

@Entity(tableName = "room_history")
data class RoomHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val roomId: String,
    val roomName: String,
    val videoTitle: String,
    val role: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val notes: String? = null,
)

@Entity(tableName = "host_configuration")
data class HostConfigurationEntity(
    @PrimaryKey val id: Int = 1,
    val displayName: String,
    val defaultRoomName: String,
    val maxParticipants: Int,
    val roomTtlMinutes: Int,
    val serverPort: Int,
)

@Entity(tableName = "tunnel_configuration")
data class TunnelConfigurationEntity(
    @PrimaryKey val id: Int = 1,
    val mode: String,
    val publicUrl: String?,
    val cloudflaredPath: String?,
    val namedHostname: String?,
)

@Entity(tableName = "application_settings")
data class ApplicationSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val driftSmallMs: Long,
    val driftMediumMs: Long,
    val driftLargeMs: Long,
    val autoReconnect: Boolean,
    val updatedAt: Long,
)
