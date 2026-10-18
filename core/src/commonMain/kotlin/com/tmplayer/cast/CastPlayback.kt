package com.tmplayer.cast

/** A receiver's player state, as its MEDIA_STATUS names it. */
enum class ReceiverState { Idle, Loading, Buffering, Playing, Paused }

/** Why a receiver's player is idle. */
enum class IdleReason { Finished, Cancelled, Interrupted, Error }

/**
 * One media status from the receiver, as either sender hears it: Android's Cast framework or the
 * desktop's own protocol code. [atMs] is when it was heard, on a monotonic clock, so the position
 * can be run on from it while (and only while) the TV is playing.
 */
data class ReceiverMedia(
    val state: ReceiverState,
    val idleReason: IdleReason? = null,
    val positionMs: Long = 0,
    val atMs: Long = 0,
    val rate: Double = 1.0,
) {
    companion object {
        fun state(name: String?): ReceiverState = when (name?.uppercase()) {
            "PLAYING" -> ReceiverState.Playing
            "PAUSED" -> ReceiverState.Paused
            "BUFFERING" -> ReceiverState.Buffering
            "LOADING" -> ReceiverState.Loading
            else -> ReceiverState.Idle
        }

        fun idleReason(name: String?): IdleReason? = when (name?.uppercase()) {
            "FINISHED" -> IdleReason.Finished
            "CANCELLED", "CANCELED" -> IdleReason.Cancelled
            "INTERRUPTED" -> IdleReason.Interrupted
            "ERROR" -> IdleReason.Error
            else -> null
        }
    }
}

/** What the cast screen says the TV is doing. */
enum class CastPhase {
    /** Nothing on the TV yet: the plan, the server, the LOAD. */
    Starting,

    /** The TV is loading the video, or a new start of it (a soundtrack switch on a rewrap). */
    Loading,
    Buffering,
    Playing,
    Paused,
    Ended,

    /** Stopped on the TV, by its own remote or another phone. */
    Stopped,
    Failed,
}

/** The cast screen's reading of the receiver, as of now. */
data class CastView(
    val phase: CastPhase,
    val positionMs: Long,
    /** The play button shows Pause: the TV plays, or will once it has loaded or buffered. */
    val showsPause: Boolean,
) {
    val busy: Boolean get() = phase == CastPhase.Starting || phase == CastPhase.Loading || phase == CastPhase.Buffering
}

/**
 * The sender's state, following the receiver's: PLAYING, PAUSED and BUFFERING as the TV reports
 * them, IDLE told apart by its reason (finished, stopped elsewhere, failed), and the position from
 * the receiver's own, run on from when it was heard only while the TV is playing. A pause stops
 * the clock on the phone the moment the TV pauses, whatever the phone asked for.
 *
 * A press is shown at once: after [requested], the button and the state follow the press for up
 * to [INTENT_MS] or until the receiver agrees, so Pause does not wait a round trip to look pressed.
 */
class CastStateMapper {
    private var intent: Boolean? = null
    private var intentAtMs = 0L
    private var loadedAtMs = Long.MIN_VALUE
    private var lastPlaying = true

    /** The viewer pressed play ([play] true) or pause at [nowMs]. */
    fun requested(play: Boolean, nowMs: Long) {
        intent = play
        intentAtMs = nowMs
    }

    /** The receiver took a LOAD at [nowMs]: a short stretch of IDLE after it is the load, not a stop. */
    fun loaded(nowMs: Long, autoplay: Boolean) {
        loadedAtMs = nowMs
        lastPlaying = autoplay
    }

    /**
     * The view of [m] (null before the receiver has said anything about this video) at [nowMs];
     * [loading] while a LOAD of this sender's is on its way, with [targetMs] where it starts.
     */
    fun map(m: ReceiverMedia?, loading: Boolean, nowMs: Long, targetMs: Long, durationMs: Long = 0): CastView {
        if (loading) return CastView(CastPhase.Loading, targetMs, intent ?: lastPlaying)
        if (m == null) return CastView(CastPhase.Starting, targetMs, intent ?: lastPlaying)
        var phase = when (m.state) {
            ReceiverState.Playing -> CastPhase.Playing
            ReceiverState.Paused -> CastPhase.Paused
            ReceiverState.Buffering -> CastPhase.Buffering
            ReceiverState.Loading -> CastPhase.Loading
            ReceiverState.Idle -> when (m.idleReason) {
                IdleReason.Finished -> CastPhase.Ended
                IdleReason.Error -> CastPhase.Failed
                IdleReason.Cancelled -> CastPhase.Stopped
                IdleReason.Interrupted, null ->
                    if (nowMs - loadedAtMs < SETTLE_MS) CastPhase.Loading else CastPhase.Stopped
            }
        }
        val wanted = intent
        if (wanted != null) {
            val agrees = (wanted && phase == CastPhase.Playing) || (!wanted && phase == CastPhase.Paused)
            if (agrees || nowMs - intentAtMs > INTENT_MS || phase !in INTENT_PHASES) {
                intent = null
            } else if (!wanted && (phase == CastPhase.Playing || phase == CastPhase.Buffering)) {
                phase = CastPhase.Paused
            } else if (wanted && phase == CastPhase.Paused) {
                phase = CastPhase.Buffering
            }
        }
        val running = phase == CastPhase.Playing && m.state == ReceiverState.Playing
        var position = if (running) m.positionMs + ((nowMs - m.atMs).coerceAtLeast(0) * m.rate).toLong() else m.positionMs
        if (durationMs > 0) position = position.coerceIn(0, durationMs)
        when (phase) {
            CastPhase.Playing -> lastPlaying = true
            CastPhase.Paused, CastPhase.Ended, CastPhase.Stopped, CastPhase.Failed -> lastPlaying = false
            else -> Unit
        }
        val showsPause = when (phase) {
            CastPhase.Playing -> true
            CastPhase.Buffering, CastPhase.Loading, CastPhase.Starting -> intent ?: lastPlaying
            else -> false
        }
        return CastView(phase, position, showsPause)
    }

    companion object {
        /** How long a press is shown before the receiver's word overrules it. */
        const val INTENT_MS = 2_500L

        /** How long after a LOAD an IDLE without a reason still reads as loading. */
        const val SETTLE_MS = 8_000L

        private val INTENT_PHASES = setOf(CastPhase.Playing, CastPhase.Paused, CastPhase.Buffering)
    }
}

/** One track in the receiver's list, as its media status gives it (the receiver numbers HLS renditions itself). */
data class RemoteTrack(val id: Long, val type: Type, val name: String?, val language: String?, val contentId: String?) {
    enum class Type { Audio, Text, Video }
}

/**
 * The receiver's track ids for an HLS cast's renditions. The Default Media Receiver makes its own
 * track list from the master playlist, so the sender finds each rendition in it: by its playlist
 * URL where the receiver keeps it, else by the name written into the master, else by language,
 * else by its place among the tracks of its kind (the master's order).
 */
object HlsTrackMatch {

    fun match(renditions: List<HlsRendition>, remote: List<RemoteTrack>): Map<HlsRendition, Long> {
        val out = LinkedHashMap<HlsRendition, Long>()
        for (kind in HlsRendition.Kind.entries) {
            val type = if (kind == HlsRendition.Kind.Audio) RemoteTrack.Type.Audio else RemoteTrack.Type.Text
            val ours = renditions.filter { it.kind == kind }
            val theirs = remote.filter { it.type == type }
            val taken = HashSet<Long>()
            fun take(r: HlsRendition, t: RemoteTrack?) {
                if (t != null && t.id !in taken && r !in out) {
                    out[r] = t.id
                    taken += t.id
                }
            }
            for (r in ours) take(r, theirs.firstOrNull { t -> t.contentId?.let { it == r.uri || it.endsWith("/" + r.uri) } == true })
            for (r in ours) take(r, theirs.firstOrNull { it.id !in taken && it.name == r.name })
            for (r in ours) {
                val lang = r.language ?: continue
                val same = theirs.filter { it.id !in taken && sameLanguage(it.language, lang) }
                if (same.size == 1 && ours.count { it !in out && sameLanguage(it.language, lang) } == 1) take(r, same.first())
            }
            if (ours.size == theirs.size) ours.forEachIndexed { i, r -> take(r, theirs[i]) }
        }
        return out
    }

    /**
     * The ids to make active: the video tracks among [current] as they are, the soundtrack [audio]
     * and the subtitles [text] (none when null), each as [map] found them. A soundtrack not found
     * keeps the one the receiver has on, so the TV never goes silent for want of a match.
     */
    fun activeIds(map: Map<HlsRendition, Long>, remote: List<RemoteTrack>, current: List<Long>, audio: HlsRendition?, text: HlsRendition?): List<Long> {
        val byId = remote.associateBy { it.id }
        return buildList {
            current.filter { byId[it]?.type == RemoteTrack.Type.Video }.forEach(::add)
            val audioId = audio?.let { map[it] } ?: current.firstOrNull { byId[it]?.type == RemoteTrack.Type.Audio }
            audioId?.let(::add)
            text?.let { map[it] }?.let(::add)
        }.distinct()
    }

    private fun sameLanguage(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        val x = HlsNames.languageTag(a) ?: return false
        val y = HlsNames.languageTag(b) ?: return false
        return x.substringBefore('-').equals(y.substringBefore('-'), ignoreCase = true)
    }
}
