package com.tmplayer.cast

import com.tmplayer.platform.Logger
import java.io.IOException

/**
 * Puts one video on the [CastServer] the way its [CastRoute] says, for either platform's sender:
 * the file's own bytes with ranges, or the Matroska rewrap made as it is sent, with its sound
 * converted where the route asks for that. Subtitle files go up beside it as WebVTT.
 */
class CastStreams(
    private val server: CastServer,
    private val converterFactory: (() -> AudioConverter)? = null,
) {

    /** What the receiver is told to load, and how the sender reads its clock. */
    class Published(
        val url: String,
        val mime: String,
        /** Where in the video the stream's zero is: the receiver's time plus this is the video's. */
        val offsetMs: Long,
        /** The receiver can seek in it itself; false for a rewrap, where a seek is a new stream. */
        val seekable: Boolean,
        val route: CastRoute,
    )

    private var counter = 0
    private var lastStream: String? = null
    private val remuxers = HashMap<Pair<Int?, Boolean>, MkvRemuxer>()
    private var remuxersFor: CastSource? = null

    /** Publishes [source] by [route] from [startMs] on. [name] gives the receiver an extension to see. */
    fun publish(source: CastSource, probe: MediaProbe, route: CastRoute, startMs: Long, name: String): Published {
        val n = ++counter
        return when (route) {
            is CastRoute.Direct -> {
                val ext = when (route.mime) {
                    "video/webm" -> "webm"
                    "video/x-matroska" -> "mkv"
                    else -> "mp4"
                }
                val url = server.publish("video.$ext", CastResource.Ranged(source, route.mime))
                Published(url, route.mime, 0, true, route)
            }
            is CastRoute.Remux, is CastRoute.ConvertAudio -> {
                val audio = when (route) {
                    is CastRoute.Remux -> route.audioTrack
                    is CastRoute.ConvertAudio -> route.audioTrack
                    else -> null
                }
                val converter = if (route is CastRoute.ConvertAudio) converterFactory ?: throw IOException("No audio converter") else null
                // One per video and soundtrack, so a seek does not read the cues again.
                if (remuxersFor !== source) {
                    remuxers.clear()
                    remuxersFor = source
                }
                val remuxer = remuxers.getOrPut(audio to (converter != null)) { MkvRemuxer(source, probe, audio, converter) }
                val offset = remuxer.actualStartMs(startMs)
                val resource = CastResource.Stream("video/mp4") { query, out ->
                    val from = query["t"]?.toLongOrNull() ?: 0L
                    try {
                        remuxer.write(from, out)
                    } catch (e: IOException) {
                        // The receiver hanging up mid stream is how a seek or a stop ends one.
                        Logger.i(TAG, "Rewrap ended: ${e.message}")
                    }
                }
                // A new name per stream, so a receiver never mixes the bytes of two starts.
                // The last one is taken down, its open read closed, so Telegram stops fetching there.
                val file = "stream$n.mp4"
                lastStream?.let(server::unpublish)
                lastStream = file
                server.publish(file, resource)
                Published(server.urlFor(file) + "?t=$startMs", "video/mp4", offset, false, route)
            }
            is CastRoute.TranscodeVideo -> throw IOException("Converting the picture is not available here")
        }.also { Logger.i(TAG, "Published ${route::class.simpleName} for $name at ${it.url}") }
    }

    /** A subtitle file as a WebVTT side track; its URL. */
    fun subtitle(id: Int, bytes: ByteArray, extension: String): String =
        server.publish("subs$id.vtt", CastResource.Text(WebVtt.convert(bytes, extension), WebVtt.MIME))

    private companion object {
        const val TAG = "CastStreams"
    }
}
