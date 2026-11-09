package com.watchwave.app.media

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.watchwave.data.db.AppDatabase
import com.watchwave.data.db.VideoMetadataEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class ImportReport(
    val imported: List<VideoMetadataEntity> = emptyList(),
    val skipped: List<String> = emptyList(),
)

/**
 * SAF-backed video library. The app stores only metadata — the actual bytes remain in
 * the user's chosen document provider and are streamed on demand.
 */
class MediaRepository(
    private val context: Context,
    private val database: AppDatabase,
) {
    private val resolver = context.contentResolver
    private val videoDao = database.videoDao()

    fun observeLibrary(): Flow<List<VideoMetadataEntity>> = videoDao.observeAll()

    fun observeVideo(uri: String): Flow<VideoMetadataEntity?> = videoDao.observeByUri(uri)

    suspend fun importVideo(uri: Uri): ImportReport = withContext(Dispatchers.IO) {
        persistReadPermission(uri)
        val entity = readMetadata(uri)
        if (entity == null) {
            ImportReport(skipped = listOf(displayName(uri) ?: uri.toString()))
        } else {
            videoDao.upsert(entity)
            ImportReport(imported = listOf(entity))
        }
    }

    suspend fun importVideos(uris: List<Uri>): ImportReport = withContext(Dispatchers.IO) {
        val imported = mutableListOf<VideoMetadataEntity>()
        val skipped = mutableListOf<String>()
        uris.forEach { uri ->
            persistReadPermission(uri)
            val entity = readMetadata(uri)
            if (entity == null) {
                skipped += displayName(uri) ?: uri.toString()
            } else {
                videoDao.upsert(entity)
                imported += entity
            }
        }
        ImportReport(imported, skipped)
    }

    suspend fun importTree(treeUri: Uri): ImportReport = withContext(Dispatchers.IO) {
        persistReadPermission(treeUri)
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return@withContext ImportReport(skipped = listOf("Unreadable folder"))
        val imported = mutableListOf<VideoMetadataEntity>()
        val skipped = mutableListOf<String>()
        val queue = ArrayDeque<DocumentFile>().apply { add(root) }
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_FILES) {
            val document = queue.removeFirst()
            visited += 1
            if (document.isDirectory) {
                document.listFiles().take(MAX_FILES).forEach { queue.add(it) }
                continue
            }
            if (!document.isFile) continue
            val mime = document.type
            if (!isVideo(mime, document.name)) continue
            val entity = readMetadata(document.uri)
            if (entity == null) {
                skipped += document.name ?: document.uri.toString()
            } else {
                videoDao.upsert(entity)
                imported += entity
            }
        }
        if (queue.isNotEmpty()) skipped += "Stopped after $MAX_FILES files"
        ImportReport(imported, skipped)
    }

    suspend fun remove(uri: String) = withContext(Dispatchers.IO) {
        videoDao.delete(uri)
        runCatching { resolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        Unit
    }

    suspend fun markPlayed(uri: String, positionMs: Long) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        videoDao.markPlayed(uri, now)
        database.recentMediaDao().insert(
            com.watchwave.data.db.RecentMediaEntity(
                uri = uri,
                title = displayName(Uri.parse(uri)) ?: "Video",
                playedAt = now,
                positionMs = positionMs.coerceAtLeast(0L),
            ),
        )
        database.recentMediaDao().prune()
    }

    fun roomHistory() = database.roomHistoryDao().observeRecent()

    suspend fun clearRoomHistory() = withContext(Dispatchers.IO) {
        database.roomHistoryDao().clear()
    }

    suspend fun recordRoomStarted(
        roomId: String,
        roomName: String,
        videoTitle: String,
        role: String,
    ): Long = withContext(Dispatchers.IO) {
        database.roomHistoryDao().insert(
            com.watchwave.data.db.RoomHistoryEntity(
                roomId = roomId,
                roomName = roomName,
                videoTitle = videoTitle,
                role = role,
                startedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun recordRoomEnded(historyId: Long) = withContext(Dispatchers.IO) {
        database.roomHistoryDao().markEnded(historyId, System.currentTimeMillis())
    }

    private fun readMetadata(uri: Uri): VideoMetadataEntity? {
        val mime = runCatching { resolver.getType(uri) }.getOrNull()
        val name = displayName(uri) ?: return null
        if (!isVideo(mime, name)) return null
        val size = querySize(uri) ?: 0L
        val duration = readDuration(uri)
        if (duration <= 0L) return null
        val title = name.substringBeforeLast('.', name).ifBlank { name }
        return VideoMetadataEntity(
            uri = uri.toString(),
            title = title,
            durationMs = duration,
            sizeBytes = size,
            mimeType = mime ?: "video/*",
            addedAt = System.currentTimeMillis(),
        )
    }

    private fun readDuration(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun querySize(uri: Uri): Long? {
        var cursor: Cursor? = null
        return try {
            cursor = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else null
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            cursor?.close()
        }
    }

    private fun displayName(uri: Uri): String? {
        var cursor: Cursor? = null
        return try {
            cursor = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else {
                uri.lastPathSegment
            }
        } catch (_: Exception) {
            uri.lastPathSegment
        } finally {
            cursor?.close()
        }
    }

    private fun persistReadPermission(uri: Uri) {
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun isVideo(mime: String?, name: String?): Boolean {
        if (mime?.startsWith("video/") == true) return true
        val extension = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return extension in VIDEO_EXTENSIONS
    }

    companion object {
        private const val MAX_FILES = 500
        val VIDEO_EXTENSIONS = setOf(
            "mp4", "m4v", "mkv", "webm", "mov", "avi", "ts", "m2ts", "mts", "3gp", "ogv",
        )
    }
}
