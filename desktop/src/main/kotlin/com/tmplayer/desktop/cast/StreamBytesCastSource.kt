package com.tmplayer.desktop.cast

import com.tmplayer.cast.CastReader
import com.tmplayer.cast.CastSource
import com.tmplayer.cast.FileCastSource
import com.tmplayer.desktop.player.StreamBytes
import com.tmplayer.desktop.player.TdMediaData
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.openani.mediamp.source.MediaData
import org.openani.mediamp.source.UriMediaData
import java.io.EOFException
import java.io.File
import java.io.IOException

/**
 * The desktop's streaming path as the cast server reads it: [StreamBytes] (a Telegram file through
 * TdStreamBytes, split pieces through JoinedStreamBytes, a downloaded piece off the disk), each
 * read waiting for TDLib the way mpv's reads do, with the same Low storage and "Save mobile data"
 * bounds the window applies to any stream.
 */
class StreamBytesCastSource(private val bytes: StreamBytes) : CastSource {

    override val length: Long get() = bytes.size

    override fun open(position: Long): CastReader = object : CastReader {
        private var at = position

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (at >= bytes.size) return -1
            repeat(SHORT_READ_RETRIES) {
                val available = bytes.cachedAvailable(at) ?: blocking { bytes.awaitBytesAt(at) }
                val wanted = minOf(length.toLong(), available, bytes.size - at).toInt()
                try {
                    val n = bytes.read(at, buffer, offset, wanted)
                    if (n > 0) at += n
                    return n
                } catch (_: EOFException) {
                    // The window moved under the read (a Low storage roll); ask again.
                }
            }
            throw IOException("Could not read at $at")
        }

        override fun close() = Unit
    }

    /** A stalled read's recovery ([com.tmplayer.cast.RecoveringCastSource]): TDLib asked afresh from [position]. */
    override fun askAgain(position: Long) = blocking { bytes.askAgain(position) }

    private fun <T> blocking(block: suspend () -> T): T = try {
        runBlocking { withTimeout(READ_TIMEOUT_MS) { block() } }
    } catch (e: TimeoutCancellationException) {
        throw IOException("Telegram did not respond in time", e)
    }

    companion object {
        private const val SHORT_READ_RETRIES = 3
        private const val READ_TIMEOUT_MS = 70_000L

        /** The bytes behind what [com.tmplayer.desktop.player.PlayerMedia.open] gave, or null for a kind it cannot read. */
        fun of(data: MediaData): CastSource? = when (data) {
            is TdMediaData -> StreamBytesCastSource(data.bytes)
            is UriMediaData -> File(data.uri).takeIf { it.isFile }?.let(::FileCastSource)
            else -> null
        }

        /** A file name with an extension, for the probe's guess and the URL the receiver sees. */
        fun nameOf(data: MediaData): String = when (data) {
            is TdMediaData -> data.uri.substringAfterLast('/')
            is UriMediaData -> File(data.uri).name
            else -> "video"
        }
    }
}
