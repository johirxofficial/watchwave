package com.watchwave.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos ORDER BY COALESCE(lastPlayedAt, addedAt) DESC")
    fun observeAll(): Flow<List<VideoMetadataEntity>>

    @Query("SELECT * FROM videos WHERE uri = :uri LIMIT 1")
    suspend fun findByUri(uri: String): VideoMetadataEntity?

    @Query("SELECT * FROM videos WHERE uri = :uri LIMIT 1")
    fun observeByUri(uri: String): Flow<VideoMetadataEntity?>

    @Upsert
    suspend fun upsert(video: VideoMetadataEntity)

    @Query("UPDATE videos SET lastPlayedAt = :timestamp WHERE uri = :uri")
    suspend fun markPlayed(uri: String, timestamp: Long)

    @Query("DELETE FROM videos WHERE uri = :uri")
    suspend fun delete(uri: String)

    @Query("DELETE FROM videos")
    suspend fun clear()
}

@Dao
interface RecentMediaDao {
    @Query("SELECT * FROM recent_media ORDER BY playedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<RecentMediaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recent: RecentMediaEntity)

    @Query(
        "DELETE FROM recent_media WHERE id NOT IN " +
            "(SELECT id FROM recent_media ORDER BY playedAt DESC LIMIT :keep)",
    )
    suspend fun prune(keep: Int = 20)

    @Query("DELETE FROM recent_media")
    suspend fun clear()
}

@Dao
interface RoomHistoryDao {
    @Query("SELECT * FROM room_history ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<RoomHistoryEntity>>

    @Insert
    suspend fun insert(history: RoomHistoryEntity): Long

    @Query("UPDATE room_history SET endedAt = :endedAt WHERE id = :id")
    suspend fun markEnded(id: Long, endedAt: Long)

    @Query("DELETE FROM room_history")
    suspend fun clear()
}

@Dao
interface ConfigurationDao {
    @Query("SELECT * FROM host_configuration WHERE id = 1")
    fun observeHost(): Flow<HostConfigurationEntity?>

    @Upsert
    suspend fun saveHost(configuration: HostConfigurationEntity)

    @Query("SELECT * FROM tunnel_configuration WHERE id = 1")
    fun observeTunnel(): Flow<TunnelConfigurationEntity?>

    @Upsert
    suspend fun saveTunnel(configuration: TunnelConfigurationEntity)

    @Query("SELECT * FROM application_settings WHERE id = 1")
    fun observeSettings(): Flow<ApplicationSettingsEntity?>

    @Upsert
    suspend fun saveSettings(settings: ApplicationSettingsEntity)
}
