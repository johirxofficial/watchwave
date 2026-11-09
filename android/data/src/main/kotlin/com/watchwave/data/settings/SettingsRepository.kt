package com.watchwave.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class TunnelMode(val storageValue: String) {
    NONE("none"),
    COMPANION_URL("companion"),
    CLOUDFLARED_PROCESS("cloudflared"),
    ;

    companion object {
        fun from(value: String?): TunnelMode =
            entries.firstOrNull { it.storageValue == value } ?: NONE
    }
}

data class AppSettings(
    val displayName: String = "Host",
    val serverPort: Int = 8080,
    val maxParticipants: Int = 10,
    val roomTtlMinutes: Int = 360,
    val tunnelMode: TunnelMode = TunnelMode.NONE,
    val companionPublicUrl: String = "",
    val cloudflaredPath: String = "",
    val namedTunnelHostname: String = "",
    val driftSmallMs: Long = 300L,
    val driftMediumMs: Long = 1_200L,
    val driftLargeMs: Long = 3_000L,
    val autoReconnect: Boolean = true,
)

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "watchwave_settings")

/** Persistent, non-secret settings. Tunnel tokens live in the Android Keystore instead. */
class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.settingsStore.data.map { prefs ->
        val defaults = AppSettings()
        AppSettings(
            displayName = prefs[Keys.DISPLAY_NAME] ?: defaults.displayName,
            serverPort = (prefs[Keys.SERVER_PORT] ?: defaults.serverPort).coerceIn(1024, 65535),
            maxParticipants = (prefs[Keys.MAX_PARTICIPANTS] ?: defaults.maxParticipants).coerceIn(2, 50),
            roomTtlMinutes = (prefs[Keys.ROOM_TTL] ?: defaults.roomTtlMinutes).coerceIn(5, 1440),
            tunnelMode = TunnelMode.from(prefs[Keys.TUNNEL_MODE]),
            companionPublicUrl = prefs[Keys.COMPANION_URL] ?: defaults.companionPublicUrl,
            cloudflaredPath = prefs[Keys.CLOUDFLARED_PATH] ?: defaults.cloudflaredPath,
            namedTunnelHostname = prefs[Keys.NAMED_HOSTNAME] ?: defaults.namedTunnelHostname,
            driftSmallMs = (prefs[Keys.DRIFT_SMALL] ?: defaults.driftSmallMs).coerceIn(50, 2_000),
            driftMediumMs = (prefs[Keys.DRIFT_MEDIUM] ?: defaults.driftMediumMs).coerceIn(200, 10_000),
            driftLargeMs = (prefs[Keys.DRIFT_LARGE] ?: defaults.driftLargeMs).coerceIn(500, 30_000),
            autoReconnect = prefs[Keys.AUTO_RECONNECT] ?: defaults.autoReconnect,
        )
    }

    suspend fun setDisplayName(value: String) = edit { it[Keys.DISPLAY_NAME] = sanitize(value, 32, "Host") }

    suspend fun setServerPort(value: Int) = edit { it[Keys.SERVER_PORT] = value.coerceIn(1024, 65535) }

    suspend fun setMaxParticipants(value: Int) = edit { it[Keys.MAX_PARTICIPANTS] = value.coerceIn(2, 50) }

    suspend fun setRoomTtlMinutes(value: Int) = edit { it[Keys.ROOM_TTL] = value.coerceIn(5, 1440) }

    suspend fun setTunnelMode(value: TunnelMode) = edit { it[Keys.TUNNEL_MODE] = value.storageValue }

    suspend fun setCompanionPublicUrl(value: String) = edit { it[Keys.COMPANION_URL] = value.trim() }

    suspend fun setCloudflaredPath(value: String) = edit { it[Keys.CLOUDFLARED_PATH] = value.trim() }

    suspend fun setNamedTunnelHostname(value: String) = edit { it[Keys.NAMED_HOSTNAME] = value.trim() }

    suspend fun setDriftThresholds(smallMs: Long, mediumMs: Long, largeMs: Long) = edit {
        it[Keys.DRIFT_SMALL] = smallMs.coerceIn(50, 2_000)
        it[Keys.DRIFT_MEDIUM] = mediumMs.coerceIn(200, 10_000)
        it[Keys.DRIFT_LARGE] = largeMs.coerceIn(500, 30_000)
    }

    suspend fun setAutoReconnect(value: Boolean) = edit { it[Keys.AUTO_RECONNECT] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsStore.edit { block(it) }
    }

    private fun sanitize(value: String, max: Int, fallback: String): String {
        val cleaned = value.filterNot { it.isISOControl() }.trim().replace(Regex("\\s+"), " ")
        return cleaned.take(max).ifBlank { fallback }
    }

    private object Keys {
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val SERVER_PORT = intPreferencesKey("server_port")
        val MAX_PARTICIPANTS = intPreferencesKey("max_participants")
        val ROOM_TTL = intPreferencesKey("room_ttl_minutes")
        val TUNNEL_MODE = stringPreferencesKey("tunnel_mode")
        val COMPANION_URL = stringPreferencesKey("companion_public_url")
        val CLOUDFLARED_PATH = stringPreferencesKey("cloudflared_path")
        val NAMED_HOSTNAME = stringPreferencesKey("named_hostname")
        val DRIFT_SMALL = longPreferencesKey("drift_small_ms")
        val DRIFT_MEDIUM = longPreferencesKey("drift_medium_ms")
        val DRIFT_LARGE = longPreferencesKey("drift_large_ms")
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
    }
}
