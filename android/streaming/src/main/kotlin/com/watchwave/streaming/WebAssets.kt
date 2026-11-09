package com.watchwave.streaming

import java.io.InputStream

data class WebAsset(
    val stream: InputStream,
    val contentType: String,
    val immutable: Boolean = false,
)

/** Supplies the compiled React bundle from Android assets (or any other backing store). */
interface WebAssetsProvider {
    fun open(path: String): WebAsset?
}

object ContentTypes {
    private val map = mapOf(
        "html" to "text/html; charset=utf-8",
        "js" to "text/javascript; charset=utf-8",
        "mjs" to "text/javascript; charset=utf-8",
        "css" to "text/css; charset=utf-8",
        "json" to "application/json; charset=utf-8",
        "webmanifest" to "application/manifest+json; charset=utf-8",
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "svg" to "image/svg+xml",
        "ico" to "image/x-icon",
        "woff2" to "font/woff2",
        "txt" to "text/plain; charset=utf-8",
        "map" to "application/json; charset=utf-8",
        "wasm" to "application/wasm",
    )

    fun forPath(path: String): String {
        val extension = path.substringAfterLast('.', "").lowercase()
        return map[extension] ?: "application/octet-stream"
    }
}
