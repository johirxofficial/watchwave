package com.watchwave.streaming

import com.watchwave.shared.protocol.API_PREFIX
import com.watchwave.shared.protocol.CreateRoomRequest
import com.watchwave.shared.protocol.MediaDescriptorDto
import com.watchwave.shared.room.CreateRoomParams
import com.watchwave.shared.room.CreateRoomResult
import com.watchwave.shared.room.RoomManager
import com.watchwave.shared.room.RoomResult
import com.watchwave.shared.security.MediaTokenManager
import com.watchwave.shared.security.TokenService
import com.watchwave.shared.sync.SyncEngine
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.head
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class WatchWaveServerTest {

    private val tokens = TokenService()
    private val mediaBytes = ByteArray(4_096) { (it % 251).toByte() }
    private lateinit var rooms: RoomManager
    private lateinit var created: CreateRoomResult
    private lateinit var engine: SyncEngine
    private lateinit var mediaTokens: MediaTokenManager
    private lateinit var server: WatchWaveServer
    private lateinit var store: FakeStore

    private inner class FakeStore : ServerRoomStore {
        override fun roomManager(): RoomManager = rooms
        override fun syncEngine(roomId: String): SyncEngine? = if (roomId == created.snapshot.roomId) engine else null
        override fun mediaSource(roomId: String): MediaSource? =
            if (roomId == created.snapshot.roomId) FakeMediaSource(mediaBytes) else null

        override suspend fun createRoom(request: CreateRoomRequest): RoomResult<CreateRoomResult> =
            RoomResult.Failure("TEST", "Not used")
    }

    private class FakeMediaSource(private val data: ByteArray) : MediaSource {
        override val sizeBytes: Long = data.size.toLong()
        override val mimeType: String = "video/mp4"

        override fun openStream(offset: Long): InputStream =
            ByteArrayInputStream(data, offset.toInt(), (data.size - offset).toInt())
    }

    @Before
    fun setUp() {
        rooms = RoomManager()
        val result = rooms.createRoom(
            CreateRoomParams(
                roomName = "Test Room",
                hostName = "Host",
                media = MediaDescriptorDto(
                    mediaId = "m_test",
                    title = "Test Video",
                    durationMs = 60_000L,
                    mimeType = "video/mp4",
                    sizeBytes = mediaBytes.size.toLong(),
                ),
                maxParticipants = 5,
                ttlMinutes = 60,
            ),
        )
        created = (result as RoomResult.Success).value
        engine = SyncEngine(created.snapshot.roomId, created.snapshot.media)
        mediaTokens = MediaTokenManager(MediaTokenManager.randomSecret(tokens))
        store = FakeStore()
        server = WatchWaveServer(
            store = store,
            mediaTokens = mediaTokens,
            stats = StreamingStats(),
            webAssets = null,
        )
    }

    private fun tokenForHost(): String = mediaTokens.issue(created.snapshot.roomId, created.hostParticipantId)

    @Test
    fun `health endpoint identifies the app`() = testApplication {
        application { server.installModuleOn(this) }
        val response = client.get("$API_PREFIX/health")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("WatchWave"))
        assertTrue(body.contains("protocolVersion"))
    }

    @Test
    fun `legacy unversioned api alias still answers health`() = testApplication {
        application { server.installModuleOn(this) }
        val response = client.get("/api/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("WatchWave"))
    }

    @Test
    fun `room metadata requires the invitation token`() = testApplication {
        application { server.installModuleOn(this) }
        val roomId = created.snapshot.roomId

        val forbidden = client.get("$API_PREFIX/rooms/$roomId")
        assertEquals(HttpStatusCode.Forbidden, forbidden.status)

        val allowed = client.get("$API_PREFIX/rooms/$roomId?t=${created.inviteToken}")
        assertEquals(HttpStatusCode.OK, allowed.status)
        assertTrue(allowed.bodyAsText().contains("Test Room"))
    }

    @Test
    fun `join returns a session and media token`() = testApplication {
        application { server.installModuleOn(this) }
        val roomId = created.snapshot.roomId
        val response = client.postJson(
            "$API_PREFIX/rooms/$roomId/join",
            """{"displayName":"Viewer","inviteToken":"${created.inviteToken}"}""",
        )
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("sessionToken"))
        assertTrue(body.contains("mediaToken"))
        assertTrue(body.contains("\"canControl\":true"))
    }

    @Test
    fun `video requires a valid media token`() = testApplication {
        application { server.installModuleOn(this) }
        val roomId = created.snapshot.roomId
        val denied = client.get("$API_PREFIX/rooms/$roomId/video")
        assertEquals(HttpStatusCode.Forbidden, denied.status)
    }

    @Test
    fun `full video response streams every byte`() = testApplication {
        application { server.installModuleOn(this) }
        val roomId = created.snapshot.roomId
        val response = client.get("$API_PREFIX/rooms/$roomId/video?t=${tokenForHost()}")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(mediaBytes.size.toString(), response.headers[HttpHeaders.ContentLength])
        assertEquals("bytes", response.headers["Accept-Ranges"])
        assertEquals(mediaBytes.size, response.body<ByteArray>().size)
    }

    @Test
    fun `range request returns 206 with correct content range`() = testApplication {
        application { server.installModuleOn(this) }
        val roomId = created.snapshot.roomId
        val response = client.get("$API_PREFIX/rooms/$roomId/video?t=${tokenForHost()}") {
            header(HttpHeaders.Range, "bytes=100-199")
        }
        assertEquals(HttpStatusCode.PartialContent, response.status)
        assertEquals("bytes 100-199/4096", response.headers["Content-Range"])
        assertEquals("100", response.headers[HttpHeaders.ContentLength])
        val body = response.body<ByteArray>()
        assertEquals(100, body.size)
        assertEquals(mediaBytes[100], body[0])
        assertEquals(mediaBytes[199], body[99])
    }

    @Test
    fun `open ended range streams to the end`() = testApplication {
        application { server.installModuleOn(this) }
        val roomId = created.snapshot.roomId
        val response = client.get("$API_PREFIX/rooms/$roomId/video?t=${tokenForHost()}") {
            header(HttpHeaders.Range, "bytes=4000-")
        }
        assertEquals(HttpStatusCode.PartialContent, response.status)
        assertEquals("bytes 4000-4095/4096", response.headers["Content-Range"])
        assertEquals(96, response.body<ByteArray>().size)
    }

    @Test
    fun `invalid range returns 416`() = testApplication {
        application { server.installModuleOn(this) }
        val roomId = created.snapshot.roomId
        val response = client.get("$API_PREFIX/rooms/$roomId/video?t=${tokenForHost()}") {
            header(HttpHeaders.Range, "bytes=99999-100000")
        }
        assertEquals(HttpStatusCode.RequestedRangeNotSatisfiable, response.status)
        assertEquals("bytes */4096", response.headers["Content-Range"])
    }

    @Test
    fun `head returns metadata without a body`() = testApplication {
        application { server.installModuleOn(this) }
        val roomId = created.snapshot.roomId
        val response = client.head("$API_PREFIX/rooms/$roomId/video?t=${tokenForHost()}")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("4096", response.headers[HttpHeaders.ContentLength])
        assertEquals(0, response.body<ByteArray>().size)
    }

    @Test
    fun `state endpoint accepts a media token`() = testApplication {
        application { server.installModuleOn(this) }
        val roomId = created.snapshot.roomId
        val response = client.get("$API_PREFIX/rooms/$roomId/state?t=${tokenForHost()}")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"isPlaying\":false"))
    }

    @Test
    fun `unknown room returns 404`() = testApplication {
        application { server.installModuleOn(this) }
        val response = client.get("$API_PREFIX/rooms/doesnotexist/video?t=whatever")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    private suspend fun io.ktor.client.HttpClient.postJson(
        url: String,
        json: String,
    ): io.ktor.client.statement.HttpResponse =
        post(url) {
            header(HttpHeaders.ContentType, "application/json")
            setBody(json)
        }
}
