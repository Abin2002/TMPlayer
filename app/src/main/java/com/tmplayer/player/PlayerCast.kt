package com.tmplayer.player

import android.net.Uri
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.tmplayer.cast.AndroidAudioConverter
import com.tmplayer.cast.CastFormats
import com.tmplayer.cast.CastLoadRequest
import com.tmplayer.cast.CastPlan
import com.tmplayer.cast.CastReader
import com.tmplayer.cast.CastRefusal
import com.tmplayer.cast.CastRoute
import com.tmplayer.cast.CastServer
import com.tmplayer.cast.CastSessionState
import com.tmplayer.cast.CastSource
import com.tmplayer.cast.CastStreams
import com.tmplayer.cast.CastSubtitle
import com.tmplayer.cast.CastSupport
import com.tmplayer.cast.LanAddress
import com.tmplayer.cast.MediaProbe
import com.tmplayer.cast.ReceiverCaps
import com.tmplayer.cast.RemoteStatus
import com.tmplayer.cast.Support
import com.tmplayer.data.EpisodeSteps
import com.tmplayer.data.MediaItem
import com.tmplayer.i18n.L
import com.tmplayer.ui.theme.TMPlayerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * A video file's bytes for the cast server, read through the player's own Media3 data sources
 * (TdDataSource for a Telegram file, JoinedDataSource for split pieces, the disk for a download),
 * so a cast keeps to Low storage mode's and "Save mobile data"'s bounds as the phone's playback does.
 */
@UnstableApi
class DataSourceCastSource(
    private val factory: DataSource.Factory,
    private val uri: Uri,
    override val length: Long,
) : CastSource {

    override fun open(position: Long): CastReader {
        val source = factory.createDataSource()
        source.open(DataSpec.Builder().setUri(uri).setPosition(position).build())
        return object : CastReader {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val n = source.read(buffer, offset, length)
                return if (n == C.RESULT_END_OF_INPUT) -1 else n
            }

            override fun close() = source.close()
        }
    }
}

/** What the cast screen draws, as of now. */
data class CastScreen(
    val device: String = "",
    val starting: Boolean = true,
    val lost: Boolean = false,
    val error: String? = null,
    val note: String? = null,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val ended: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val volume: Float = 1f,
    val muted: Boolean = false,
    val audio: List<CastChoice> = emptyList(),
    val subtitles: List<CastChoice> = emptyList(),
    val subtitlesOn: Boolean = false,
)

/** One audio or subtitle track as the cast screen's pickers list it. */
data class CastChoice(val id: Long, val label: String, val selected: Boolean)

/**
 * The phone's player while the video is on a TV (CP73): the Cast button's picker, the hand over
 * from the phone's own player at its position and back again, the cast server and the plan
 * ([CastFormats]), the LOAD and its fallbacks, and the screen of controls that stands in for the
 * picture ([CastRemoteScreen]). The Cast framework draws the notification and the lock screen
 * controls from the same session.
 *
 * The activity keeps its ExoPlayer: stopped while casting, so its stream lets go and TDLib serves
 * the TV alone, and prepared again at the TV's position when the cast ends or the viewer asks to
 * play here.
 */
@UnstableApi
class PlayerCast(
    private val activity: ComponentActivity,
    root: ViewGroup,
    val support: CastSupport,
    private val hooks: Hooks,
) {
    interface Hooks {
        /** Stops the phone's playback for the cast; where it was, or null before anything played. */
        fun handOff(): Long?

        /** Plays here again from [positionMs]. */
        fun takeBack(positionMs: Long)

        /** The video's bytes and file name, or null before they are known. */
        fun source(): Pair<CastSource, String>?

        fun title(): String
        fun subtitleLine(): String
        fun audioLanguage(): String?

        /** The subtitle files loaded over this video: file, MIME type, label. */
        fun subtitleFiles(): List<Triple<File, String, String>>

        val episodes: StateFlow<EpisodeSteps>
        fun playEpisode(item: MediaItem)

        /** The TV reached the end: the phone's end of video logic (next episode, Finished). */
        fun onEnded()

        /** The cast screen went up or came down. */
        fun onCasting(casting: Boolean)

        fun close()
    }

    val screen = mutableStateOf(CastScreen())
    val pickerOpen = mutableStateOf(false)
    val audioPickerOpen = mutableStateOf(false)
    val subtitlePickerOpen = mutableStateOf(false)

    /** Set once the activity has its player and its stream: a session up before then waits for it. */
    var ready = false

    /** True while the cast screen stands in for the picture. */
    var casting = false
        private set

    private var server: CastServer? = null
    private var streams: CastStreams? = null
    private var source: CastSource? = null
    private var name = "video"
    private var probe: MediaProbe? = null
    private var plan: CastPlan? = null
    private var routeIndex = 0
    private var route: CastRoute? = null
    private var published: CastStreams.Published? = null
    private var audio: Int? = null
    private val subs = ArrayList<CastSubtitle>()
    private var activeSub: Long? = null
    private var wantPlaying = true
    private var loading = false
    private var loadedAt = 0L
    private var endedReported = false
    private var lastKnownMs = 0L
    private var starting: Job? = null

    private val view = ComposeView(activity).apply {
        visibility = View.GONE
        // Not named Content: inside apply that resolves to ComposeView.Content (see PlayerTvMenu).
        setContent { TMPlayerTheme { CastRemoteScreen(this@PlayerCast) } }
    }
    private val pickerView = ComposeView(activity).apply {
        setContent { TMPlayerTheme { CastPickerHost(this@PlayerCast) } }
    }

    init {
        root.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(pickerView, ViewGroup.LayoutParams(0, 0))
        activity.lifecycleScope.launch { support.session.collect(::onSession) }
        activity.lifecycleScope.launch { support.remote.collect(::onRemote) }
    }

    val connected: Boolean get() = support.session.value is CastSessionState.Connected

    /** Where the TV is in the video, for the resume writes; null while not casting. */
    val positionMs: Long? get() = if (casting && published != null) lastKnownMs else null
    val durationMs: Long get() = probe?.durationMs ?: 0L
    val playing: Boolean get() = casting && screen.value.playing && !screen.value.ended

    fun openPicker() {
        pickerOpen.value = true
    }

    private fun onSession(state: CastSessionState) {
        when (state) {
            is CastSessionState.Connected -> {
                if (casting && screen.value.lost) {
                    // Back after a drop: the receiver may have kept playing, or not; load again here.
                    screen.value = screen.value.copy(lost = false)
                    reload(lastKnownMs)
                } else if (!casting && ready) {
                    begin(hooks.handOff())
                }
            }
            is CastSessionState.Suspended -> if (casting) screen.value = screen.value.copy(lost = true)
            is CastSessionState.Idle, is CastSessionState.Failed -> if (casting) end(resumeHere = true)
            is CastSessionState.Connecting -> Unit
        }
    }

    /**
     * The phone's video goes to the TV. [startMs] is where it had reached, or null when nothing
     * played yet (a video opened with a session up), which starts at the saved position the
     * activity already seeked to.
     */
    fun begin(startMs: Long?) {
        val connectedState = support.session.value as? CastSessionState.Connected ?: return
        casting = true
        endedReported = false
        wantPlaying = true
        lastKnownMs = startMs ?: 0L
        screen.value = CastScreen(device = connectedState.name, positionMs = lastKnownMs)
        view.visibility = View.VISIBLE
        hooks.onCasting(true)
        starting?.cancel()
        starting = activity.lifecycleScope.launch {
            try {
                val (src, file) = hooks.source() ?: throw IOException(L.castCannotCastContainer)
                source = src
                name = file
                val canConvert = AndroidAudioConverter.available()
                val p = withContext(Dispatchers.IO) { MediaProbe.of(src, file) }
                probe = p
                val preferred = hooks.audioLanguage()?.let { lang -> p.audio.firstOrNull { sameLanguage(it.language, lang) }?.number }
                val caps = ReceiverCaps.forModel(connectedState.model)
                val pl = CastFormats.plan(p, caps, preferred, canConvertAudio = canConvert, canTranscodeVideo = false)
                plan = pl
                Log.i(TAG, "Plan for $file on ${connectedState.model}: $pl ($p)")
                if (!pl.castable) {
                    throw IOException(if (pl.refusal == CastRefusal.VideoCodec) L.castCannotCastPicture else L.castCannotCastContainer)
                }
                audio = pl.audioTrack
                withContext(Dispatchers.IO) { ensureServer(connectedState) }
                subs.clear()
                hooks.subtitleFiles().forEach { (f, mime, label) -> addSubtitleFile(f, mime, label) }
                activeSub = subs.lastOrNull()?.id
                routeIndex = 0
                startRoute(lastKnownMs)
                publishTracks()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Cast failed to start", e)
                screen.value = screen.value.copy(starting = false, error = e.message ?: L.castLoadFailed)
            }
        }
    }

    private fun ensureServer(state: CastSessionState.Connected) {
        if (server?.running == true) return
        val bind = LanAddress.forReceiver(state.address) ?: throw IOException(L.castConnectFailed(state.name))
        val s = CastServer(bind)
        s.start()
        server = s
        streams = CastStreams(s) { AndroidAudioConverter() }
    }

    private suspend fun startRoute(atMs: Long) {
        val pl = plan ?: return
        while (routeIndex < pl.routes.size) {
            if (load(pl.routes[routeIndex], atMs)) return
            routeIndex++
        }
        throw IOException(L.castLoadFailed)
    }

    private suspend fun load(r: CastRoute, atMs: Long): Boolean {
        val st = streams ?: return false
        val src = source ?: return false
        val p = probe ?: return false
        loading = true
        try {
            val chosen = when (r) {
                is CastRoute.Remux -> r.copy(audioTrack = r.audioTrack?.let { audio ?: it }?.takeIf { a -> p.audio.firstOrNull { it.number == a }?.codec in CastFormats.MP4_AUDIO })
                is CastRoute.ConvertAudio -> audio?.let { CastRoute.ConvertAudio(it) } ?: r
                else -> r
            }
            val pub = withContext(Dispatchers.IO) { st.publish(src, p, chosen, atMs, name) }
            val request = CastLoadRequest(
                url = pub.url,
                mime = pub.mime,
                title = hooks.title(),
                subtitle = hooks.subtitleLine(),
                durationMs = if (p.durationMs > 0) (p.durationMs - pub.offsetMs).coerceAtLeast(0) else 0,
                startMs = if (pub.seekable) atMs else 0,
                autoplay = wantPlaying,
                subtitles = subs.toList(),
                activeTrackIds = listOfNotNull(activeSub),
            )
            val ok = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                support.load(request) { result -> if (cont.isActive) cont.resumeWith(Result.success(result)) }
            }
            if (!ok) return false
            published = pub
            route = chosen
            loadedAt = System.nanoTime()
            screen.value = screen.value.copy(starting = false, error = null, note = noteFor(chosen), positionMs = atMs, durationMs = p.durationMs)
            return true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Load failed", e)
            return false
        } finally {
            loading = false
        }
    }

    private fun reload(atMs: Long) {
        val r = route ?: return
        activity.lifecycleScope.launch {
            if (!load(r, atMs)) screen.value = screen.value.copy(error = L.castLoadFailed)
        }
    }

    private fun noteFor(r: CastRoute): String? {
        val p = probe ?: return null
        val model = (support.session.value as? CastSessionState.Connected)?.model
        val audioCodec = p.audio.firstOrNull { it.number == audio }?.codec
        return when {
            r is CastRoute.TranscodeVideo -> L.castConvertingPhone
            r is CastRoute.ConvertAudio -> L.castSoundConvertedPhone
            r is CastRoute.Remux && r.audioTrack == null && p.audio.isNotEmpty() -> L.castSilent
            r is CastRoute.Direct && audioCodec != null && CastFormats.audioSupport(audioCodec, ReceiverCaps.forModel(model)) == Support.No -> L.castSilent
            p.text.any { it.isText } && subs.isEmpty() -> L.castSubtitlesInside
            else -> null
        }
    }

    private fun onRemote(m: RemoteStatus) {
        if (!casting) return
        val pub = published ?: return
        if (m.contentId != null && m.contentId != pub.url) return
        if (m.failed && !loading) {
            val pl = plan
            val since = (System.nanoTime() - loadedAt) / 1_000_000
            if (pl != null && since < EARLY_FAILURE_MS && routeIndex + 1 < pl.routes.size) {
                routeIndex++
                val at = lastKnownMs
                activity.lifecycleScope.launch {
                    runCatching { startRoute(at) }.onFailure { screen.value = screen.value.copy(error = L.castLoadFailed) }
                }
            } else {
                screen.value = screen.value.copy(error = L.castLoadFailed, playing = false)
            }
            return
        }
        val position = if (loading) lastKnownMs else pub.offsetMs + m.positionMs
        if (!loading) lastKnownMs = position
        screen.value = screen.value.copy(
            playing = wantPlaying && !m.ended,
            buffering = loading || m.playerState == RemoteStatus.BUFFERING || m.playerState == RemoteStatus.LOADING,
            ended = m.ended,
            positionMs = position,
            durationMs = probe?.durationMs?.takeIf { it > 0 } ?: (m.durationMs + pub.offsetMs),
            volume = m.volume.toFloat(),
            muted = m.muted,
            subtitlesOn = m.activeTrackIds.isNotEmpty(),
        )
        if (m.ended && !endedReported) {
            endedReported = true
            hooks.onEnded()
        }
    }

    private fun publishTracks() {
        val p = probe ?: return
        screen.value = screen.value.copy(
            audio = p.audio.map { a ->
                val label = listOfNotNull(
                    a.name?.takeIf { it.isNotBlank() },
                    a.language?.let { L.messages.formatter.trackLanguage(it) ?: it },
                ).distinct().joinToString(" · ").ifBlank { L.playerTrackNumber(a.number.toString()) }
                CastChoice(a.number.toLong(), "$label (${a.codec})", a.number == audio)
            },
            subtitles = subs.map { CastChoice(it.id, it.name, it.id == activeSub) },
        )
    }

    // ---- the controls ------------------------------------------------------------------------

    fun togglePlay() {
        if (screen.value.ended) {
            seekTo(0)
            wantPlaying = true
            support.play()
            return
        }
        wantPlaying = !wantPlaying
        if (wantPlaying) support.play() else support.pause()
        screen.value = screen.value.copy(playing = wantPlaying)
    }

    fun seekTo(positionMs: Long) {
        val pub = published ?: return
        val duration = screen.value.durationMs
        val target = positionMs.coerceAtLeast(0).let { if (duration > 0) it.coerceAtMost(duration - 500) else it }
        lastKnownMs = target
        endedReported = false
        screen.value = screen.value.copy(positionMs = target, ended = false)
        if (pub.seekable) support.seek(target) else reload(target)
    }

    fun seekBy(deltaMs: Long) = seekTo(lastKnownMs + deltaMs)

    fun setVolume(level: Float) = support.setVolume(level.toDouble())

    fun toggleMute() = support.setMuted(!screen.value.muted)

    /** Volume keys while casting move the TV's volume, five per cent a press. */
    fun stepVolume(up: Boolean) {
        val now = screen.value.volume
        support.setVolume((now + if (up) VOLUME_STEP else -VOLUME_STEP).coerceIn(0f, 1f).toDouble())
    }

    fun pickAudio(id: Long) {
        audioPickerOpen.value = false
        val number = id.toInt()
        if (number == audio) return
        val r = route
        if (r is CastRoute.Direct) {
            screen.value = screen.value.copy(note = L.castAudioCannotSwitch)
            return
        }
        audio = number
        publishTracks()
        val codec = probe?.audio?.firstOrNull { it.number == number }?.codec
        val next = if (codec != null && codec in CastFormats.MP4_AUDIO) CastRoute.Remux(number) else CastRoute.ConvertAudio(number)
        val at = lastKnownMs
        activity.lifecycleScope.launch { if (!load(next, at)) screen.value = screen.value.copy(error = L.castLoadFailed) }
    }

    fun pickSubtitle(id: Long?) {
        subtitlePickerOpen.value = false
        activeSub = id
        support.setActiveTracks(listOfNotNull(id))
        publishTracks()
    }

    /** A subtitle file loaded while casting; true once it is on the TV. */
    fun addSubtitle(file: File, mime: String, label: String): Boolean {
        if (!casting) return false
        if (!addSubtitleFile(file, mime, label)) return false
        activeSub = subs.last().id
        publishTracks()
        // Side tracks are declared in the LOAD, so the video is loaded again where it is.
        reload(lastKnownMs)
        return true
    }

    private fun addSubtitleFile(file: File, mime: String, label: String): Boolean {
        val st = streams ?: return false
        val id = SUB_BASE + subs.size
        val ext = when (mime) {
            MimeTypes.TEXT_SSA -> "ass"
            MimeTypes.TEXT_VTT -> "vtt"
            else -> "srt"
        }
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return false
        val url = runCatching { st.subtitle(id.toInt(), bytes, ext) }.getOrNull() ?: return false
        subs += CastSubtitle(id, label, null, url)
        return true
    }

    fun playEpisode(item: MediaItem) = hooks.playEpisode(item)

    fun titleNow(): String = hooks.title()
    fun subtitleNow(): String = hooks.subtitleLine()

    /** Said over the subtitle picker when the file has text subtitles of its own that stay behind. */
    fun subtitlesInsideNote(): String? = if (probe?.text?.any { it.isText } == true) L.castSubtitlesInside else null

    val episodes: StateFlow<EpisodeSteps> get() = hooks.episodes

    /** Stop casting: the session ends, the TV's player stops, and the video carries on here. */
    fun stopCasting() {
        val at = lastKnownMs
        end(resumeHere = false)
        support.endSession()
        hooks.takeBack(at)
    }

    /** Play here, after the TV was lost or refused the video. */
    fun playHere() {
        val at = lastKnownMs
        end(resumeHere = false)
        support.endSession()
        hooks.takeBack(at)
    }

    fun reconnect() {
        val device = support.devices.value.firstOrNull { it.name == screen.value.device }
        if (device != null) support.connect(device.id) else pickerOpen.value = true
    }

    fun back() = hooks.close()

    private fun end(resumeHere: Boolean) {
        if (!casting) return
        val at = lastKnownMs
        casting = false
        starting?.cancel()
        runCatching { server?.stop() }
        server = null
        streams = null
        published = null
        view.visibility = View.GONE
        hooks.onCasting(false)
        if (resumeHere) hooks.takeBack(at)
    }

    /**
     * The activity is going away. The TV is stopped only when it is still playing this activity's
     * video: the next episode's player loads its own a moment before this one is destroyed.
     */
    fun release() {
        val mine = published?.url
        if (casting && mine != null && support.remote.value.contentId == mine) support.stopMedia()
        casting = false
        runCatching { server?.stop() }
        server = null
    }

    private fun sameLanguage(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        if (a.equals(b, ignoreCase = true)) return true
        return runCatching { Locale.forLanguageTag(a).isO3Language == Locale.forLanguageTag(b).isO3Language }.getOrDefault(false)
    }

    private companion object {
        const val TAG = "TMPlayerCast"
        const val EARLY_FAILURE_MS = 20_000L
        const val SUB_BASE = 1000L
        const val VOLUME_STEP = 0.05f
    }
}
