package com.watchwave.streaming

import java.io.InputStream

/**
 * Random-access view of one local video. Implementations must never load the whole
 * file into memory and must open an independent descriptor per call so that several
 * viewers can stream concurrently.
 */
interface MediaSource {
    val sizeBytes: Long
    val mimeType: String?

    /** Opens a stream positioned at [offset]; the caller closes it. */
    fun openStream(offset: Long): InputStream
}

interface MediaSourceProvider {
    fun mediaSource(roomId: String): MediaSource?
}
