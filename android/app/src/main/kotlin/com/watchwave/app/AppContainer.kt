package com.watchwave.app

import android.content.Context
import com.watchwave.app.core.AppLog
import com.watchwave.app.host.HostEngine
import com.watchwave.app.media.AssetWebAssets
import com.watchwave.app.media.MediaRepository
import com.watchwave.app.playback.PlaybackController
import com.watchwave.app.security.SecureStore
import com.watchwave.app.viewer.ViewerEngine
import com.watchwave.data.db.AppDatabase
import com.watchwave.data.settings.AppSettings
import com.watchwave.data.settings.SettingsRepository
import com.watchwave.tunnel.TunnelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Process-wide dependency graph. Engines intentionally outlive Activities so rotation,
 * screen-off playback and the embedded server keep running without re-binding.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val log = AppLog()
    val database: AppDatabase = AppDatabase.get(appContext)
    val settingsRepository = SettingsRepository(appContext)
    val mediaRepository = MediaRepository(appContext, database)
    val secureStore = SecureStore(appContext)
    val playback = PlaybackController(appContext)
    val tunnelManager = TunnelManager(scope)
    val webAssets = AssetWebAssets(appContext.assets)

    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
        scope,
        SharingStarted.Eagerly,
        AppSettings(),
    )

    lateinit var viewerEngine: ViewerEngine
        private set

    val hostEngine: HostEngine = HostEngine(
        context = appContext,
        playback = playback,
        mediaRepository = mediaRepository,
        tunnelManager = tunnelManager,
        secureStore = secureStore,
        webAssets = webAssets,
        appLog = log,
        scope = scope,
        stopViewerSession = { viewerEngine.leave() },
    )

    private val _pendingDeepLink = MutableStateFlow<String?>(null)
    val pendingDeepLink: StateFlow<String?> = _pendingDeepLink

    init {
        viewerEngine = ViewerEngine(
            context = appContext,
            playback = playback,
            mediaRepository = mediaRepository,
            appLog = log,
            scope = scope,
            stopHostSession = { hostEngine.stop() },
        )
    }

    fun offerDeepLink(url: String?) {
        val value = url?.trim().orEmpty()
        if (value.isNotEmpty()) _pendingDeepLink.value = value
    }

    fun consumeDeepLink(): String? {
        val current = _pendingDeepLink.value
        _pendingDeepLink.value = null
        return current
    }

    val defaultBaseDomain: String get() = BuildConfig.DEFAULT_BASE_DOMAIN
}
