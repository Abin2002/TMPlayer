package com.tmplayer.desktop.cast

import com.tmplayer.cast.CastFormats
import com.tmplayer.cast.CastPlan
import com.tmplayer.cast.CastRefusal
import com.tmplayer.cast.CastRoute
import com.tmplayer.cast.CastServer
import com.tmplayer.cast.CastSource
import com.tmplayer.cast.CastStreams
import com.tmplayer.cast.Codecs
import com.tmplayer.cast.LanAddress
import com.tmplayer.cast.MediaProbe
import com.tmplayer.cast.ReceiverCaps
import com.tmplayer.desktop.player.AbLoop
import com.tmplayer.desktop.player.MediaTrack
import com.tmplayer.desktop.player.OpenPrefs
import com.tmplayer.desktop.player.PlaybackEngine
import com.tmplayer.desktop.player.PlaybackStatus
import com.tmplayer.desktop.player.TrackType
import com.tmplayer.i18n.L
import com.tmplayer.platform.Logger
import com.tmplayer.player.SubtitleStyle
import com.tmplayer.player.VideoScale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.openani.mediamp.source.MediaData
import java.io.File
import java.io.IOException
import java.util.Locale

/** What the TV is told the video is called. */
data class CastTitle(val title: String, val subtitle: String = "")

/**
 * The player as the overlay sees it, with the picture on a TV: every [PlaybackEngine] call turned
 * into the Cast protocol, and the receiver's MEDIA_STATUS turned back into [PlaybackStatus], so the
 * keys, the timebar, the volume and the menus drive the TV with no change of their own.
 *
 * [open] reads what the video is ([MediaProbe]), plans how the receiver can play it
 * ([CastFormats.plan]), serves it from this computer ([CastServer], [CastStreams]) and loads it,
 * walking down the plan's routes when the receiver turns one away. A rewrapped stream has no
 * seeking of its own on the receiver, so a seek there is a new stream from the cue before it.
 */
class CastPlaybackEngine(
    val client: CastClient,
    private val describe: () -> CastTitle,
) : PlaybackEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(PlaybackStatus())
    override val state: StateFlow<PlaybackStatus> = _state.asStateFlow()
    private val _tracks = MutableStateFlow<List<MediaTrack>>(emptyList())
    override val tracks: StateFlow<List<MediaTrack>> = _tracks.asStateFlow()

    private val _note = MutableStateFlow<String?>(null)

    /** A line for the casting panel: what was converted, what will not play, what cannot show. */
    val note: StateFlow<String?> = _note.asStateFlow()

    private var server: CastServer? = null
    private var streams: CastStreams? = null
    private var data: MediaData? = null
    private var source: CastSource? = null
    private var name = "video"
    private var probe: MediaProbe? = null
    private var plan: CastPlan? = null
    private var routeIndex = 0
    private var route: CastRoute? = null
    private var published: CastStreams.Published? = null
    private var audio: Int? = null

    private class Sub(val id: Int, val label: String, val language: String?, val url: String)

    private val subs = ArrayList<Sub>()
    private var activeSub: Int? = null
    private var wantPlaying = true
    private var speed = 1f
    private var loadedAt = 0L
    private var loading = false
    private var watcher: Job? = null

    override suspend fun open(data: MediaData, startAtMs: Long, prefs: OpenPrefs) {
        withContext(Dispatchers.IO) {
            closeData()
            this@CastPlaybackEngine.data = data
            _state.value = PlaybackStatus(buffering = true, volume = volumePercent())
            subs.clear()
            activeSub = null
            try {
                val src = StreamBytesCastSource.of(data) ?: throw IOException(L.castCannotCastContainer)
                source = src
                name = StreamBytesCastSource.nameOf(data)
                val p = MediaProbe.of(src, name)
                probe = p
                val preferred = prefs.audioLanguage?.let { lang -> p.audio.firstOrNull { sameLanguage(it.language, lang) }?.number }
                val caps = ReceiverCaps.forModel(client.device.model)
                val pl = CastFormats.plan(p, caps, preferred, canConvertAudio = false, canTranscodeVideo = false)
                plan = pl
                Logger.i(TAG, "Plan for $name on ${client.device.model}: $pl ($p)")
                if (!pl.castable) {
                    throw IOException(
                        when (pl.refusal) {
                            CastRefusal.VideoCodec -> L.castCannotCastPicture
                            else -> L.castCannotCastContainer
                        },
                    )
                }
                audio = pl.audioTrack
                speed = prefs.speed
                ensureServer()
                routeIndex = 0
                startRoute(startAtMs)
                publishTracks()
                watch()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(TAG, "Cast open failed", e)
                _state.value = PlaybackStatus(error = e.message ?: L.castLoadFailed)
            }
        }
    }

    private fun ensureServer() {
        if (server?.running == true) return
        val bind = LanAddress.forReceiver(client.device.address) ?: throw IOException(L.castConnectFailed(client.device.name))
        val s = CastServer(bind)
        s.start()
        server = s
        streams = CastStreams(s)
    }

    /** Loads the plan's current route at [atMs], moving down the plan while the receiver refuses. */
    private suspend fun startRoute(atMs: Long) {
        val pl = plan ?: return
        while (routeIndex < pl.routes.size) {
            val r = pl.routes[routeIndex]
            if (load(r, atMs)) return
            routeIndex++
        }
        throw IOException(L.castLoadFailed)
    }

    /** One LOAD; true when the receiver took it. */
    private suspend fun load(r: CastRoute, atMs: Long): Boolean {
        val st = streams ?: return false
        val src = source ?: return false
        val p = probe ?: return false
        loading = true
        try {
            val chosen = when (r) {
                is CastRoute.Remux -> r.copy(audioTrack = audio?.takeIf { a -> p.audio.firstOrNull { it.number == a }?.codec in CastFormats.MP4_AUDIO })
                else -> r
            }
            val pub = st.publish(src, p, chosen, atMs, name)
            val title = describe()
            val media = JSONObject()
                .put("contentId", pub.url)
                .put("contentUrl", pub.url)
                .put("contentType", pub.mime)
                .put("streamType", "BUFFERED")
                .put(
                    "metadata",
                    JSONObject().put("metadataType", 0).put("title", title.title).apply {
                        if (title.subtitle.isNotBlank()) put("subtitle", title.subtitle)
                    },
                )
            if (p.durationMs > 0) media.put("duration", (p.durationMs - pub.offsetMs).coerceAtLeast(0) / 1000.0)
            if (subs.isNotEmpty()) {
                media.put(
                    "tracks",
                    JSONArray(
                        subs.map { s ->
                            JSONObject().put("trackId", s.id).put("type", "TEXT").put("subtype", "SUBTITLES")
                                .put("trackContentId", s.url).put("trackContentType", "text/vtt").put("name", s.label)
                                .apply { s.language?.let { put("language", it) } }
                        },
                    ),
                )
                media.put("textTrackStyle", JSONObject().put("backgroundColor", "#00000000").put("edgeType", "OUTLINE").put("edgeColor", "#000000FF"))
            }
            val answer = client.load(media, if (pub.seekable) atMs else 0L, autoplay = wantPlaying, activeTrackIds = listOfNotNull(activeSub))
            val type = answer.optString("type")
            if (type != "MEDIA_STATUS") {
                Logger.w(TAG, "Receiver refused ${chosen::class.simpleName}: $answer")
                return false
            }
            published = pub
            route = chosen
            loadedAt = System.nanoTime()
            if (speed != 1f) client.setRate(speed.toDouble())
            _note.value = noteFor(chosen)
            _state.value = _state.value.copy(opened = true, error = null, positionMs = atMs, durationMs = p.durationMs, playing = wantPlaying)
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(TAG, "Load failed", e)
            return false
        } finally {
            loading = false
        }
    }

    private fun noteFor(r: CastRoute): String? {
        val p = probe ?: return null
        val audioCodec = p.audio.firstOrNull { it.number == audio }?.codec
        return when {
            r is CastRoute.TranscodeVideo -> L.castConvertingComputer
            r is CastRoute.ConvertAudio -> L.castSoundConvertedComputer
            audioCodec != null && CastFormats.audioSupport(audioCodec, ReceiverCaps.forModel(client.device.model)) == com.tmplayer.cast.Support.No -> L.castSilent
            r is CastRoute.Remux && r.audioTrack == null && p.audio.isNotEmpty() -> L.castSilent
            p.text.any { it.isText } && subs.isEmpty() -> L.castSubtitlesInside
            else -> null
        }
    }

    /** Keeps [state] in step with the receiver, and moves to the next route if the receiver fails early. */
    private fun watch() {
        watcher?.cancel()
        watcher = scope.launch {
            launch { client.volume.collect { publishState() } }
            launch { client.media.collect { m -> onMedia(m) } }
            while (true) {
                delay(TICK_MS)
                publishState()
            }
        }
    }

    private suspend fun onMedia(m: RemoteMediaStatus) {
        val pub = published ?: return
        if (m.contentId != null && m.contentId != pub.url) return
        if (m.playerState == "IDLE" && m.idleReason == "ERROR" && !loading) {
            val sinceLoad = (System.nanoTime() - loadedAt) / 1_000_000
            val pl = plan
            if (pl != null && sinceLoad < EARLY_FAILURE_MS && routeIndex + 1 < pl.routes.size) {
                Logger.i(TAG, "Receiver failed on ${route?.let { it::class.simpleName }}, trying the next way")
                routeIndex++
                runCatching { startRoute(_state.value.positionMs) }.onFailure { _state.value = _state.value.copy(error = L.castLoadFailed) }
                return
            }
            _state.value = _state.value.copy(error = L.castLoadFailed, playing = false)
            return
        }
        publishState()
    }

    private fun publishState() {
        val pub = published ?: return
        val m = client.media.value
        if (m.contentId != null && m.contentId != pub.url) return
        val v = client.volume.value
        val p = probe
        val ended = m.playerState == "IDLE" && m.idleReason == "FINISHED"
        val position = if (loading) _state.value.positionMs else pub.offsetMs + m.positionAt()
        val duration = p?.durationMs?.takeIf { it > 0 } ?: (m.durationMs + pub.offsetMs)
        _state.value = _state.value.copy(
            opened = true,
            positionMs = position.coerceIn(0, duration.coerceAtLeast(position)),
            durationMs = duration,
            bufferedMs = position,
            playing = wantPlaying && !ended,
            buffering = loading || m.playerState == "BUFFERING" || m.playerState == "LOADING",
            seekPending = loading,
            ended = ended,
            volume = (v.level * 100).toInt(),
            muted = v.muted,
            speed = speed,
        )
    }

    private fun publishTracks() {
        val p = probe ?: return
        val audioTracks = p.audio.map { a ->
            MediaTrack(a.number, TrackType.Audio, a.name, a.language, a.codec, a.channels.takeIf { it > 0 }, a.number == audio, a.isDefault, false)
        }
        val subTracks = subs.map { s -> MediaTrack(s.id, TrackType.Subtitle, s.label, s.language, "webvtt", null, s.id == activeSub, false, true) }
        _tracks.value = audioTracks + subTracks
    }

    private fun volumePercent(): Int = (client.volume.value.level * 100).toInt()

    // ---- the controls ------------------------------------------------------------------------

    override fun play() {
        wantPlaying = true
        if (_state.value.ended) seekTo(0)
        client.play()
        publishState()
    }

    override fun pause() {
        wantPlaying = false
        client.pause()
        publishState()
    }

    override fun stop() {
        wantPlaying = false
        client.stopMedia()
        _state.value = _state.value.copy(playing = false)
    }

    override fun togglePlay() = if (wantPlaying && !_state.value.ended) pause() else play()

    override fun seekTo(positionMs: Long) {
        val pub = published ?: return
        val target = positionMs.coerceAtLeast(0).let { t -> _state.value.durationMs.takeIf { it > 0 }?.let { t.coerceAtMost(it - 500) } ?: t }
        _state.value = _state.value.copy(positionMs = target, seekPending = true)
        if (pub.seekable) {
            client.seek(target)
        } else {
            // A rewrap starts again from the cue before the target.
            val r = route ?: return
            scope.launch { if (!load(r, target)) _state.value = _state.value.copy(error = L.castLoadFailed) }
        }
    }

    override fun seekBy(deltaMs: Long) = seekTo(_state.value.positionMs + deltaMs)

    override fun frameStep(forward: Boolean) = Unit

    override fun setSpeed(speed: Float) {
        this.speed = speed
        client.setRate(speed.toDouble())
        publishState()
    }

    override fun setVolume(percent: Int) = client.setVolume(percent.coerceIn(0, 100) / 100.0)

    override fun setMuted(muted: Boolean) = client.setMuted(muted)

    override fun selectTrack(type: TrackType, id: Int?) {
        when (type) {
            TrackType.Audio -> {
                if (id == null || id == audio) return
                val r = route
                if (r is CastRoute.Direct) {
                    // The receiver plays the file's own soundtrack; no other is reachable.
                    _note.value = L.castAudioCannotSwitch
                    return
                }
                audio = id
                publishTracks()
                val at = _state.value.positionMs
                scope.launch { load(CastRoute.Remux(id), at) }
            }
            TrackType.Subtitle -> {
                activeSub = id
                client.setActiveTracks(listOfNotNull(id))
                publishTracks()
            }
            TrackType.Video -> Unit
        }
    }

    override fun addSubtitle(path: String): Boolean {
        val st = streams ?: return false
        val file = File(path)
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return false
        val id = SUB_BASE + subs.size
        val url = runCatching { st.subtitle(id, bytes, file.extension.ifBlank { "srt" }) }.getOrNull() ?: return false
        subs += Sub(id, file.nameWithoutExtension, null, url)
        activeSub = id
        publishTracks()
        // Side tracks are declared in the LOAD itself, so the video is loaded again where it is.
        val r = route ?: return true
        val at = _state.value.positionMs
        scope.launch { load(r, at) }
        return true
    }

    override fun setScale(scale: VideoScale) = Unit
    override fun setDownmix(stereo: Boolean) = Unit
    override fun setVolumeBoost(on: Boolean) = Unit
    override fun setSubtitleStyle(style: SubtitleStyle) = Unit
    override fun setSubtitleDelay(ms: Long) = Unit
    override fun setAudioDelay(ms: Long) = Unit
    override suspend fun screenshot(file: File, withSubtitles: Boolean): Boolean = false
    override fun setAbLoop(loop: AbLoop?) = Unit

    override fun details(): List<Pair<String, String>> {
        val p = probe
        val out = ArrayList<Pair<String, String>>()
        out += L.castCastingTo(client.device.name) to client.device.model
        p?.video?.let { out += "Video" to "${it.codec} ${it.width}x${it.height}" }
        p?.audio?.firstOrNull { it.number == audio }?.let { out += "Audio" to "${it.codec} ${it.channels}ch" }
        route?.let { out += "Route" to it.toString() }
        return out
    }

    /** Where the video is, for the switch back to this computer. */
    val positionMs: Long get() = _state.value.positionMs

    override fun close() {
        watcher?.cancel()
        runCatching { client.stopMedia() }
        scope.cancel()
        runCatching { server?.stop() }
        server = null
        closeData()
    }

    /** Lets go of the stream and the server without telling the receiver anything (it is gone). */
    fun abandon() {
        watcher?.cancel()
        scope.cancel()
        runCatching { server?.stop() }
        server = null
        closeData()
    }

    private fun closeData() {
        runCatching { data?.close() }
        data = null
        source = null
        published = null
    }

    private fun sameLanguage(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        if (a.equals(b, ignoreCase = true)) return true
        return runCatching { Locale.forLanguageTag(a).isO3Language == Locale.forLanguageTag(b).isO3Language }.getOrDefault(false)
    }

    private companion object {
        const val TAG = "CastEngine"
        const val TICK_MS = 500L
        const val EARLY_FAILURE_MS = 20_000L
        const val SUB_BASE = 1000
    }
}
