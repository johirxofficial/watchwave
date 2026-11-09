package com.watchwave.app.net

import com.watchwave.shared.protocol.ActionResponse
import com.watchwave.shared.protocol.ErrorResponse
import com.watchwave.shared.protocol.JoinRequest
import com.watchwave.shared.protocol.JoinResponse
import com.watchwave.shared.protocol.LeaveRequest
import com.watchwave.shared.protocol.MediaTokenResponse
import com.watchwave.shared.protocol.ReconnectRequest
import com.watchwave.shared.protocol.RoomSummaryDto
import com.watchwave.shared.protocol.SyncJson
import com.watchwave.shared.protocol.UploadStatsDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json

/**
 * Typed client for the versioned HTTP API. Used by the Android viewer; the host uses
 * its in-process room registry for authority and only exposes the network surface to
 * other devices.
 */
class RoomApi(baseUrl: String) {

    val baseUrl: String = baseUrl.trim().trimEnd('/')

    private val http = HttpClient(io.ktor.client.engine.okhttp.OkHttp) {
        install(ContentNegotiation) { json(SyncJson.json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000L
            connectTimeoutMillis = 12_000L
            socketTimeoutMillis = 25_000L
        }
    }

    suspend fun room(roomId: String, inviteToken: String): RoomSummaryDto =
        http.get("$baseUrl/api/v1/rooms/$roomId") {
            parameter("t", inviteToken)
        }.ensureSuccess().body()

    suspend fun join(roomId: String, displayName: String, inviteToken: String): JoinResponse =
        http.post("$baseUrl/api/v1/rooms/$roomId/join") {
            contentType(ContentType.Application.Json)
            setBody(JoinRequest(displayName = displayName, inviteToken = inviteToken))
        }.ensureSuccess().body()

    suspend fun reconnect(roomId: String, sessionToken: String): JoinResponse =
        http.post("$baseUrl/api/v1/rooms/$roomId/reconnect") {
            contentType(ContentType.Application.Json)
            setBody(ReconnectRequest(sessionToken))
        }.ensureSuccess().body()

    suspend fun leave(roomId: String, sessionToken: String) {
        runCatching {
            http.post("$baseUrl/api/v1/rooms/$roomId/leave") {
                bearer(sessionToken)
                contentType(ContentType.Application.Json)
                setBody(LeaveRequest(sessionToken))
            }.ensureSuccess()
        }
    }

    suspend fun refreshMediaToken(roomId: String, sessionToken: String): MediaTokenResponse =
        http.post("$baseUrl/api/v1/rooms/$roomId/media-token") {
            bearer(sessionToken)
        }.ensureSuccess().body()

    suspend fun stats(roomId: String, hostToken: String): UploadStatsDto =
        http.get("$baseUrl/api/v1/rooms/$roomId/stats") {
            header(HOST_HEADER, hostToken)
        }.ensureSuccess().body()

    suspend fun closeRoom(roomId: String, hostToken: String) {
        http.post("$baseUrl/api/v1/rooms/$roomId/close") {
            header(HOST_HEADER, hostToken)
        }.ensureSuccess()
    }

    suspend fun leaveQuietly(roomId: String, sessionToken: String): ActionResponse? =
        runCatching {
            http.post("$baseUrl/api/v1/rooms/$roomId/leave") {
                bearer(sessionToken)
                contentType(ContentType.Application.Json)
                setBody(LeaveRequest(sessionToken))
            }.ensureSuccess().body<ActionResponse>()
        }.getOrNull()

    fun close() {
        runCatching { http.close() }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.bearer(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    private suspend fun HttpResponse.ensureSuccess(): HttpResponse {
        if (status.isSuccess()) return this
        val error = runCatching { body<ErrorResponse>() }.getOrNull()
        throw RoomApiException(
            code = error?.error ?: "HTTP_${status.value}",
            message = error?.message ?: "The host returned HTTP ${status.value}.",
            status = status.value,
        )
    }

    companion object {
        const val HOST_HEADER = "X-WatchWave-Host-Token"
    }
}
