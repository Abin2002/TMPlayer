package com.tmplayer.player

import android.net.Uri
import android.os.SystemClock
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
import com.tmplayer.cast.CastPhase
import com.tmplayer.cast.CastPlan
import com.tmplayer.cast.CastReader
import com.tmplayer.cast.CastRefusal
import com.tmplayer.cast.CastRoute
import com.tmplayer.cast.CastServer
import com.tmplayer.cast.CastSessionState
import com.tmplayer.cast.CastSource
import com.tmplayer.cast.CastStateMapper
import com.tmplayer.cast.CastStreams
import com.tmplayer.cast.CastSubtitle
import com.tmplayer.cast.CastSupport
import com.tmplayer.cast.CastView
import com.tmplayer.cast.HlsRendition
import com.tmplayer.cast.HlsTrackMatch
import com.tmplayer.cast.LanAddress
import com.tmplayer.cast.MediaProbe
import com.tmplayer.cast.ReceiverCaps
import com.tmplayer.cast.RemoteStatus
import com.tmplayer.cast.Support
import com.tmplayer.data.EpisodeSteps
import com.tmplayer.data.MediaItem
import com.tmplayer.i18n.L
import com.tmplayer.ui.player.CastRemoteState
import com.tmplayer.ui.theme.TMPlayerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
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

/** One audio, subtitle or speed choice as the cast screen's pickers list it. */
data class CastChoice(val id: Long, val label: String, val selected: Boolean)

/**
 * The phone's player while the video is on a TV (CP73): the Cast button's picker, the hand over
 * from the phone's own player at its position and back again, the cast server and the plan
 * ([CastFormats]), the LOAD and its fallbacks, and the remote that stands in for the picture
 * ([CastRemoteScreen]). The Cast framework draws the notification and the lock screen controls
 * from the same session.
 *
 * A Matroska file goes as HLS where it can ([CastRoute.Hls]): the receiver then knows the whole
 * length, seeks itself, and switches soundtracks and subtitles by its own track ids, which the
 * pickers find with [HlsTrackMatch]. What the screen says follows the receiver's own status
 * ([CastStateMapper]): a TV that pauses or stalls stops the phone's clock with it.
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

    /** What the remote draws. */
    val screen = mutableStateOf(CastRemoteState(title = "", device = ""))
    val pickerOpen = mutableStateOf(false)
    val audioPickerOpen = mutableStateOf(false)
    val subtitlePickerOpen = mutableStateOf(false)
    val speedPickerOpen = mutableStateOf(false)
    val audioChoices = mutableStateOf(emptyList<CastChoice>())
    val subtitleChoices = mutableStateOf(emptyList<CastChoice>())

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

    /** Side tracks, for a file played as it is or the one rewrapped stream. */
    private val subs = ArrayList<CastSubtitle>()
    private var activeSub: Long? = null

    /** HLS: the soundtrack and subtitles chosen, and whether the receiver has been told. */
    private var hlsAudio: HlsRendition? = null
    private var hlsText: HlsRendition? = null
    private var tracksSent = false

    private val mapper = CastStateMapper()
    private var view = CastView(CastPhase.Starting, 0, true)
    private var device = ""
    private var lost = false
    private var error: String? = null
    private var note: String? = null
    private var rate = 1f
    private var wantPlaying = true
    private var loading = false
    private var loadedAt = 0L
    private var endedReported = false
    private var lastKnownMs = 0L
    private var seekTarget: Long? = null
    private var seekAt = 0L
    private var starting: Job? = null
    private var ticker: Job? = null

    private val composeView = ComposeView(activity).apply {
        visibility = View.GONE
        // Not named Content: inside apply that resolves to ComposeView.Content (see PlayerTvMenu).
        setContent { TMPlayerTheme { CastRemoteScreen(this@PlayerCast) } }
    }
    private val pickerView = ComposeView(activity).apply {
        setContent { TMPlayerTheme { CastPickerHost(this@PlayerCast) } }
    }

    init {
        root.addView(composeView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(pickerView, ViewGroup.LayoutParams(0, 0))
        activity.lifecycleScope.launch { support.session.collect(::onSession) }
        activity.lifecycleScope.launch { support.remote.collect(::onRemote) }
    }

    val connected: Boolean get() = support.session.value is CastSessionState.Connected

    /** Where the TV is in the video, for the resume writes; null while not casting. */
    val positionMs: Long? get() = if (casting && published != null) lastKnownMs else null
    val durationMs: Long get() = probe?.durationMs ?: 0L
    val playing: Boolean get() = casting && view.phase == CastPhase.Playing

    fun openPicker() {
        pickerOpen.value = true
    }

    private fun onSession(state: CastSessionState) {
        when (state) {
            is CastSessionState.Connected -> {
                if (casting && lost) {
                    // Back after a drop: the receiver may have kept playing, or not; load again here.
                    lost = false
                    reload(lastKnownMs)
                } else if (!casting && ready) {
                    begin(hooks.handOff())
                }
            }
            is CastSessionState.Suspended -> if (casting) {
                lost = true
                refresh()
            }
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
        lost = false
        error = null
        note = null
        rate = 1f
        published = null
        lastKnownMs = startMs ?: 0L
        device = connectedState.name
        view = CastView(CastPhase.Starting, lastKnownMs, true)
        refresh()
        composeView.visibility = View.VISIBLE
        hooks.onCasting(true)
        startTicker()
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
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Cast failed to start", e)
                error = e.message ?: L.castLoadFailed
                refresh()
            }
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = activity.lifecycleScope.launch {
            while (isActive && casting) {
                delay(TICK_MS)
                refresh()
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
        lastKnownMs = atMs
        refresh()
        try {
            val chosen = when (r) {
                is CastRoute.Hls -> r.copy(audioTrack = audio?.takeIf { a -> r.audio.any { it.number == a } } ?: r.audioTrack)
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
                durationMs = pub.durationMs.takeIf { it > 0 } ?: if (p.durationMs > 0) (p.durationMs - pub.offsetMs).coerceAtLeast(0) else 0,
                startMs = if (pub.seekable) atMs else 0,
                autoplay = wantPlaying,
                // HLS carries its subtitles in the playlist; side tracks are for the rest.
                subtitles = if (pub.hls) emptyList() else subs.toList(),
                activeTrackIds = if (pub.hls) emptyList() else listOfNotNull(activeSub),
                hls = pub.hls,
            )
            val ok = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                support.load(request) { result -> if (cont.isActive) cont.resumeWith(Result.success(result)) }
            }
            if (!ok) return false
            published = pub
            route = chosen
            loadedAt = System.nanoTime()
            mapper.loaded(SystemClock.elapsedRealtime(), wantPlaying)
            if (pub.hls) {
                val renditions = pub.renditions
                hlsAudio = renditions.firstOrNull { it.kind == HlsRendition.Kind.Audio && it.trackNumber == audio }
                    ?: renditions.firstOrNull { it.kind == HlsRendition.Kind.Audio && it.isDefault }
                // A subtitle file the viewer had on, else the one picked before a reload, else none.
                hlsText = renditions.firstOrNull { it.fileId != null && it.fileId == activeSub }
                    ?: hlsText?.let { prev -> renditions.firstOrNull { it.uri == prev.uri } }
                tracksSent = false
            }
            if (rate != 1f) support.setRate(rate.toDouble())
            error = null
            note = noteFor(chosen)
            publishChoices()
            return true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Load failed", e)
            return false
        } finally {
            loading = false
            refresh()
        }
    }

    private fun reload(atMs: Long) {
        val r = route ?: return retryFromStart(atMs)
        activity.lifecycleScope.launch {
            if (!load(r, atMs)) {
                error = L.castLoadFailed
                refresh()
            }
        }
    }

    private fun retryFromStart(atMs: Long) {
        activity.lifecycleScope.launch {
            routeIndex = 0
            runCatching { startRoute(atMs) }.onFailure {
                error = L.castLoadFailed
                refresh()
            }
        }
    }

    private fun noteFor(r: CastRoute): String? {
        val p = probe ?: return null
        val model = (support.session.value as? CastSessionState.Connected)?.model
        val audioCodec = p.audio.firstOrNull { it.number == audio }?.codec
        return when {
            r is CastRoute.TranscodeVideo -> L.castConvertingPhone
            r is CastRoute.ConvertAudio -> L.castSoundConvertedPhone
            r is CastRoute.Hls && r.audio.isEmpty() && p.audio.isNotEmpty() -> L.castSilent
            r is CastRoute.Hls && r.audio.firstOrNull { it.number == audio }?.convert == true -> L.castSoundConvertedPhone
            r is CastRoute.Remux && r.audioTrack == null && p.audio.isNotEmpty() -> L.castSilent
            r is CastRoute.Direct && audioCodec != null && CastFormats.audioSupport(audioCodec, ReceiverCaps.forModel(model)) == Support.No -> L.castSilent
            r !is CastRoute.Hls && p.text.any { it.isText } && subs.isEmpty() -> L.castSubtitlesInside
            else -> null
        }
    }

    private var remote = RemoteStatus()

    private fun onRemote(m: RemoteStatus) {
        remote = m
        if (!casting) return
        val pub = published ?: return
        if (m.contentId != null && m.contentId != pub.url) return
        refresh()
        if (view.phase == CastPhase.Failed && !loading) {
            val pl = plan
            val since = (System.nanoTime() - loadedAt) / 1_000_000
            if (pl != null && since < EARLY_FAILURE_MS && routeIndex + 1 < pl.routes.size) {
                Log.i(TAG, "Receiver failed on ${route?.let { it::class.simpleName }}, trying the next way")
                routeIndex++
                val at = lastKnownMs
                published = null
                activity.lifecycleScope.launch {
                    runCatching { startRoute(at) }.onFailure {
                        error = L.castLoadFailed
                        refresh()
                    }
                }
            }
            return
        }
        if (pub.hls && !tracksSent && m.tracks.isNotEmpty()) {
            tracksSent = true
            sendTracks(force = false)
        }
        if (view.phase == CastPhase.Ended && !endedReported) {
            endedReported = true
            hooks.onEnded()
        }
    }

    /** The HLS soundtrack and subtitles the viewer wants, as the receiver's track ids. */
    private fun sendTracks(force: Boolean) {
        val pub = published ?: return
        val map = HlsTrackMatch.match(pub.renditions, remote.tracks)
        val ids = HlsTrackMatch.activeIds(map, remote.tracks, remote.activeTrackIds, hlsAudio, hlsText)
        Log.i(TAG, "Tracks ${remote.tracks} matched $map; active ${remote.activeTrackIds} -> $ids")
        if (force || ids.toSet() != remote.activeTrackIds.toSet()) support.setActiveTracks(ids)
    }

    /** The screen, from what is known now: the receiver's state run through the mapper. */
    private fun refresh() {
        if (!casting) return
        val now = SystemClock.elapsedRealtime()
        val pub = published
        val mine = pub != null && (remote.contentId == null || remote.contentId == pub.url)
        val duration = probe?.durationMs?.takeIf { it > 0 } ?: pub?.durationMs ?: 0L
        val media = if (mine) remote.media?.let { it.copy(positionMs = it.positionMs + (pub?.offsetMs ?: 0)) } else null
        view = when {
            pub == null && !loading -> CastView(CastPhase.Starting, lastKnownMs, wantPlaying)
            else -> mapper.map(media, loading, now, lastKnownMs, duration)
        }
        var shown = view.positionMs
        // A seek just asked for: its target, until the TV is there.
        seekTarget?.let { target ->
            if (now - seekAt < SEEK_HOLD_MS && kotlin.math.abs(shown - target) > 2_000) shown = target else seekTarget = null
        }
        if (!loading && pub != null) lastKnownMs = shown
        screen.value = CastRemoteState(
            title = hooks.title(),
            subtitle = hooks.subtitleLine(),
            device = device,
            phase = view.phase,
            lost = lost,
            error = error,
            note = note,
            positionMs = shown,
            durationMs = duration,
            showsPause = view.showsPause,
            volume = remote.volume.toFloat(),
            muted = remote.muted,
            volumeFixed = remote.volumeFixed,
            hasAudioChoice = audioChoices.value.size > 1,
            hasSubtitles = true,
            subtitlesOn = if (pub?.hls == true) hlsText != null else activeSub != null,
            speed = if (remote.supportsRate) rate else null,
        )
    }

    /** The pickers' lists, from the probe and the renditions. */
    private fun publishChoices() {
        val p = probe ?: return
        val pub = published
        audioChoices.value = if (pub?.hls == true) {
            pub.renditions.filter { it.kind == HlsRendition.Kind.Audio }.mapNotNull { r ->
                val a = p.audio.firstOrNull { it.number == r.trackNumber } ?: return@mapNotNull null
                CastChoice(a.number.toLong(), audioLabel(a), r.uri == hlsAudio?.uri)
            }
        } else {
            p.audio.map { a -> CastChoice(a.number.toLong(), audioLabel(a), a.number == audio) }
        }
        subtitleChoices.value = if (pub?.hls == true) {
            pub.renditions.filter { it.kind == HlsRendition.Kind.Text }.mapIndexed { i, r ->
                val t = r.trackNumber?.let { n -> p.text.firstOrNull { it.number == n } }
                val label = t?.let { textLabel(it.name, it.language, r.name) } ?: r.name
                CastChoice(i.toLong(), label, r.uri == hlsText?.uri)
            }
        } else {
            subs.map { CastChoice(it.id, it.name, it.id == activeSub) }
        }
    }

    private fun audioLabel(a: com.tmplayer.cast.AudioTrack): String {
        val label = listOfNotNull(
            a.name?.takeIf { it.isNotBlank() },
            a.language?.let { L.messages.formatter.trackLanguage(it) ?: it },
        ).distinct().joinToString(" · ").ifBlank { L.playerTrackNumber(a.number.toString()) }
        return "$label (${a.codec.uppercase(Locale.ROOT)})"
    }

    private fun textLabel(name: String?, language: String?, fallback: String): String =
        listOfNotNull(name?.takeIf { it.isNotBlank() }, language?.let { L.messages.formatter.trackLanguage(it) ?: it })
            .distinct().joinToString(" · ").ifBlank { fallback }

    // ---- the controls ------------------------------------------------------------------------

    fun togglePlay() {
        val now = SystemClock.elapsedRealtime()
        when (view.phase) {
            CastPhase.Ended -> {
                wantPlaying = true
                seekTo(0)
                support.play()
            }
            CastPhase.Stopped, CastPhase.Failed -> {
                wantPlaying = true
                retry()
            }
            else -> {
                wantPlaying = !view.showsPause
                mapper.requested(wantPlaying, now)
                if (wantPlaying) support.play() else support.pause()
            }
        }
        refresh()
    }

    fun seekTo(positionMs: Long) {
        val pub = published ?: return
        val duration = screen.value.durationMs
        val target = positionMs.coerceAtLeast(0).let { if (duration > 0) it.coerceAtMost(duration - 500) else it }
        lastKnownMs = target
        endedReported = false
        seekTarget = target
        seekAt = SystemClock.elapsedRealtime()
        if (pub.seekable) support.seek(target) else reload(target)
        refresh()
    }

    fun seekBy(deltaMs: Long) = seekTo(lastKnownMs + deltaMs)

    fun setVolume(level: Float) = support.setVolume(level.toDouble())

    fun toggleMute() = support.setMuted(!remote.muted)

    /** Volume keys while casting move the TV's volume, five per cent a press. */
    fun stepVolume(up: Boolean) {
        if (remote.volumeFixed) return
        val now = remote.volume.toFloat()
        support.setVolume((now + if (up) VOLUME_STEP else -VOLUME_STEP).coerceIn(0f, 1f).toDouble())
    }

    fun pickAudio(id: Long) {
        audioPickerOpen.value = false
        val number = id.toInt()
        val pub = published
        val r = route
        if (pub?.hls == true) {
            val rendition = pub.renditions.firstOrNull { it.kind == HlsRendition.Kind.Audio && it.trackNumber == number } ?: return
            if (rendition.uri == hlsAudio?.uri) return
            audio = number
            hlsAudio = rendition
            publishChoices()
            val map = HlsTrackMatch.match(pub.renditions, remote.tracks)
            if (map[rendition] != null) {
                sendTracks(force = true)
            } else {
                // The receiver's list has no match for it: start the playlist again on that soundtrack.
                reload(lastKnownMs)
            }
            refresh()
            return
        }
        if (number == audio) return
        if (r is CastRoute.Direct) {
            note = L.castAudioCannotSwitch
            refresh()
            return
        }
        audio = number
        publishChoices()
        val codec = probe?.audio?.firstOrNull { it.number == number }?.codec
        val next = if (codec != null && codec in CastFormats.MP4_AUDIO) CastRoute.Remux(number) else CastRoute.ConvertAudio(number)
        val at = lastKnownMs
        activity.lifecycleScope.launch {
            if (!load(next, at)) {
                error = L.castLoadFailed
                refresh()
            }
        }
    }

    /** A subtitle choice by its id in [subtitleChoices], or off with null. */
    fun pickSubtitle(id: Long?) {
        subtitlePickerOpen.value = false
        val pub = published
        if (pub?.hls == true) {
            val texts = pub.renditions.filter { it.kind == HlsRendition.Kind.Text }
            hlsText = id?.let { texts.getOrNull(it.toInt()) }
            activeSub = hlsText?.fileId
            publishChoices()
            sendTracks(force = true)
            refresh()
            return
        }
        activeSub = id
        support.setActiveTracks(listOfNotNull(id))
        publishChoices()
        refresh()
    }

    fun pickSpeed(speed: Float) {
        speedPickerOpen.value = false
        rate = speed
        support.setRate(speed.toDouble())
        refresh()
    }

    /** A subtitle file loaded while casting; true once it is on the TV. */
    fun addSubtitle(file: File, mime: String, label: String): Boolean {
        if (!casting) return false
        if (!addSubtitleFile(file, mime, label)) return false
        activeSub = subs.last().id
        publishChoices()
        // Side tracks are declared in the LOAD, and HLS renditions in the playlist, so the video
        // is loaded again where it is, the new subtitles on.
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
        val url = runCatching { st.subtitle(id.toInt(), bytes, ext, label) }.getOrNull() ?: return false
        subs += CastSubtitle(id, label, null, url)
        return true
    }

    fun playEpisode(item: MediaItem) = hooks.playEpisode(item)

    /** Said over the subtitle picker when the file has text subtitles of its own that stay behind. */
    fun subtitlesInsideNote(): String? =
        if (published?.hls != true && probe?.text?.any { it.isText } == true) L.castSubtitlesInside else null

    val episodes: StateFlow<EpisodeSteps> get() = hooks.episodes

    /** Try again after the TV refused or stopped: the same way again from where it was, or the plan from the top. */
    fun retry() {
        error = null
        lost = false
        val at = lastKnownMs
        if (published == null || route == null) retryFromStart(at) else reload(at)
        refresh()
    }

    /** Stop casting: the session ends, the TV's player stops, and the video carries on here. */
    fun stopCasting() {
        val at = lastKnownMs
        end(resumeHere = false)
        support.endSession()
        hooks.takeBack(at)
    }

    /** Play here, after the TV was lost or refused the video. */
    fun playHere() = stopCasting()

    fun reconnect() {
        val dev = support.devices.value.firstOrNull { it.name == device }
        if (dev != null) support.connect(dev.id) else pickerOpen.value = true
    }

    fun back() = hooks.close()

    private fun end(resumeHere: Boolean) {
        if (!casting) return
        val at = lastKnownMs
        casting = false
        starting?.cancel()
        ticker?.cancel()
        runCatching { server?.stop() }
        server = null
        streams = null
        published = null
        composeView.visibility = View.GONE
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
        ticker?.cancel()
        runCatching { server?.stop() }
        server = null
    }

    private fun sameLanguage(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        if (a.equals(b, ignoreCase = true)) return true
        return runCatching { Locale.forLanguageTag(a).isO3Language == Locale.forLanguageTag(b).isO3Language }.getOrDefault(false)
    }

    companion object {
        private const val TAG = "TMPlayerCast"
        private const val EARLY_FAILURE_MS = 20_000L
        private const val SUB_BASE = 1000L
        private const val VOLUME_STEP = 0.05f
        private const val TICK_MS = 250L
        private const val SEEK_HOLD_MS = 4_000L

        /** The speeds the receiver is offered: the Default Media Receiver takes 0.5 to 2. */
        val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    }
}
