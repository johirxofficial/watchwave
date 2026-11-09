package com.watchwave.tunnel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Verifies that a public URL actually forwards to this app before it is shown to the
 * host. A tunnel is never reported CONNECTED based only on process output: the health
 * endpoint must answer with the WatchWave marker.
 */
class HttpReachabilityVerifier : ReachabilityVerifier {
    override suspend fun verify(baseUrl: String, healthPath: String, timeoutMs: Long): VerifyResult =
        withContext(Dispatchers.IO) {
            val normalized = baseUrl.trim().trimEnd('/')
            if (!normalized.startsWith("https://") && !normalized.startsWith("http://")) {
                return@withContext VerifyResult(false, "URL must start with http:// or https://")
            }
            val url = try {
                URL(normalized + healthPath)
            } catch (error: Exception) {
                return@withContext VerifyResult(false, "Invalid URL: ${error.message}")
            }
            val deadline = System.currentTimeMillis() + timeoutMs
            var lastDetail = "No response"
            while (System.currentTimeMillis() < deadline) {
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5_000
                    readTimeout = 5_000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "WatchWave-Android")
                }
                try {
                    val code = connection.responseCode
                    if (code == HttpURLConnection.HTTP_OK) {
                        val body = connection.inputStream.bufferedReader().use { reader ->
                            val buffer = CharArray(2_048)
                            val read = reader.read(buffer)
                            if (read > 0) String(buffer, 0, read) else ""
                        }
                        if (body.contains(HEALTH_MARKER)) {
                            return@withContext VerifyResult(true, "Health endpoint verified")
                        }
                        lastDetail = "Reached a server, but it is not a WatchWave host"
                    } else {
                        lastDetail = "HTTP $code"
                    }
                } catch (error: IOException) {
                    lastDetail = error.message ?: "Connection failed"
                } finally {
                    connection.disconnect()
                }
                kotlinx.coroutines.delay(1_500L)
            }
            VerifyResult(false, lastDetail)
        }
}
