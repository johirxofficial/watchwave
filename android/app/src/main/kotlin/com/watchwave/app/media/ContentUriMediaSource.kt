package com.watchwave.app.media

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.watchwave.streaming.MediaSource
import java.io.IOException
import java.io.InputStream

/**
 * Streams a SAF/content URI with true random access. The host never copies the video
 * into app storage; each HTTP Range request opens a fresh file descriptor at the
 * requested offset.
 */
class ContentUriMediaSource(
    private val context: Context,
    private val uri: Uri,
    override val sizeBytes: Long,
    override val mimeType: String?,
) : MediaSource {

    override fun openStream(offset: Long): InputStream {
        if (offset < 0L) throw IOException("Negative offset")
        val descriptor: ParcelFileDescriptor = context.contentResolver
            .openFileDescriptor(uri, "r")
            ?: throw IOException("The selected video is no longer readable")

        val stream = ParcelFileDescriptor.AutoCloseInputStream(descriptor)
        if (offset == 0L) return stream

        try {
            // FileInputStream exposes a seekable channel for document providers that
            // return a real file descriptor (the normal SAF case).
            stream.channel.position(offset)
        } catch (_: Exception) {
            var remaining = offset
            while (remaining > 0L) {
                val skipped = stream.skip(remaining)
                if (skipped <= 0L) {
                    stream.close()
                    throw IOException("This provider does not support random access")
                }
                remaining -= skipped
            }
        }
        return stream
    }
}
