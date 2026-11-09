package com.watchwave.app.media

import android.content.res.AssetManager
import com.watchwave.streaming.ContentTypes
import com.watchwave.streaming.WebAsset
import com.watchwave.streaming.WebAssetsProvider

/**
 * Serves the Vite build copied into `assets/web` by the Gradle `syncWebAssets` task.
 * Hashed bundle files are cached immutably; the HTML shell is always revalidated.
 */
class AssetWebAssets(private val assets: AssetManager) : WebAssetsProvider {

    override fun open(path: String): WebAsset? {
        val clean = path.trim().trimStart('/')
        if (clean.isEmpty() || clean.contains("..") || clean.contains('\\')) return null
        val stream = try {
            assets.open("web/$clean")
        } catch (_: Exception) {
            return null
        }
        val immutable = clean.startsWith("assets/") && clean.substringAfterLast('/').contains('-')
        return WebAsset(
            stream = stream,
            contentType = ContentTypes.forPath(clean),
            immutable = immutable,
        )
    }
}
