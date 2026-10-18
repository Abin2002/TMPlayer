package com.tmplayer.cast

import android.content.Context
import com.tmplayer.data.FormFactor
import kotlinx.coroutines.flow.StateFlow
import java.net.InetAddress

/** A receiver the phone can cast to, as the picker lists it. */
data class CastDeviceInfo(
    val id: String,
    /** "Living room TV". */
    val name: String,
    /** "Chromecast", "Google TV Streamer", or the TV's own model. */
    val model: String,
    val connected: Boolean = false,
    val connecting: Boolean = false,
)

/** Where the phone's cast session stands. */
sealed interface CastSessionState {
    data object Idle : CastSessionState
    data class Connecting(val name: String) : CastSessionState
    data class Connected(val name: String, val model: String, val address: InetAddress?) : CastSessionState

    /** The connection dropped (the TV went off, the Wi-Fi changed); the framework is trying again. */
    data class Suspended(val name: String) : CastSessionState

    data class Failed(val name: String) : CastSessionState
}

/** The receiver's player, as of its last status. */
data class RemoteStatus(
    /** One of [IDLE], [BUFFERING], [PLAYING], [PAUSED], [LOADING]. */
    val playerState: Int = IDLE,
    /** Why it is [IDLE]: [IDLE_FINISHED], [IDLE_ERROR] or another reason; 0 for none. */
    val idleReason: Int = 0,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    /** The device's volume, 0 to 1. */
    val volume: Double = 1.0,
    val muted: Boolean = false,
    /** What the receiver is playing; null when nothing. */
    val contentId: String? = null,
    val activeTrackIds: List<Long> = emptyList(),
) {
    val playing: Boolean get() = playerState == PLAYING
    val ended: Boolean get() = playerState == IDLE && idleReason == IDLE_FINISHED
    val failed: Boolean get() = playerState == IDLE && idleReason == IDLE_ERROR

    companion object {
        const val IDLE = 1
        const val PLAYING = 2
        const val PAUSED = 3
        const val BUFFERING = 4
        const val LOADING = 5

        const val IDLE_FINISHED = 1
        const val IDLE_CANCELED = 2
        const val IDLE_INTERRUPTED = 3
        const val IDLE_ERROR = 4
    }
}

/** A subtitle side track, already on the cast server as WebVTT. */
data class CastSubtitle(val id: Long, val name: String, val language: String?, val url: String)

/** One LOAD for the receiver. */
data class CastLoadRequest(
    val url: String,
    val mime: String,
    val title: String,
    val subtitle: String,
    val durationMs: Long,
    val startMs: Long,
    val autoplay: Boolean = true,
    val subtitles: List<CastSubtitle> = emptyList(),
    val activeTrackIds: List<Long> = emptyList(),
)

/**
 * Casting as the rest of the app sees it, whichever build this is. The regular build answers
 * through Google's Cast framework ([CastBuild] in src/cast); the F-Droid build has no Google code
 * at all, and its [CastBuild] (src/nocast) is never [available], so no Cast button is drawn.
 *
 * A television has no use for casting and gets none either.
 */
interface CastSupport {
    val available: Boolean
    val devices: StateFlow<List<CastDeviceInfo>>
    val session: StateFlow<CastSessionState>
    val remote: StateFlow<RemoteStatus>

    /** Starts looking for receivers, for a picker that is open; [stopDiscovery] when it closes. */
    fun startDiscovery()
    fun stopDiscovery()

    fun connect(id: String)

    /** Ends the session; the TV's player stops. */
    fun endSession()

    /** Loads a video; [onResult] says whether the receiver took it. */
    fun load(request: CastLoadRequest, onResult: (Boolean) -> Unit)

    fun play()
    fun pause()
    fun seek(positionMs: Long)
    fun stopMedia()
    fun setVolume(level: Double)
    fun setMuted(muted: Boolean)
    fun setActiveTracks(ids: List<Long>)
    fun setRate(rate: Double)
}

/** The one [CastSupport] of the process, made on first use from the main thread. */
object Cast {
    @Volatile
    private var support: CastSupport? = null

    fun support(context: Context): CastSupport {
        support?.let { return it }
        synchronized(this) {
            support?.let { return it }
            val made = if (FormFactor.isTv(context)) NoCastSupport else runCatching { CastBuild.create(context.applicationContext) }.getOrDefault(NoCastSupport)
            support = made
            return made
        }
    }
}

/** No casting: the F-Droid build, a television, or a phone without Google Play services. */
object NoCastSupport : CastSupport {
    private val none = kotlinx.coroutines.flow.MutableStateFlow(emptyList<CastDeviceInfo>())
    private val idle = kotlinx.coroutines.flow.MutableStateFlow<CastSessionState>(CastSessionState.Idle)
    private val status = kotlinx.coroutines.flow.MutableStateFlow(RemoteStatus())
    override val available: Boolean get() = false
    override val devices: StateFlow<List<CastDeviceInfo>> get() = none
    override val session: StateFlow<CastSessionState> get() = idle
    override val remote: StateFlow<RemoteStatus> get() = status
    override fun startDiscovery() = Unit
    override fun stopDiscovery() = Unit
    override fun connect(id: String) = Unit
    override fun endSession() = Unit
    override fun load(request: CastLoadRequest, onResult: (Boolean) -> Unit) = onResult(false)
    override fun play() = Unit
    override fun pause() = Unit
    override fun seek(positionMs: Long) = Unit
    override fun stopMedia() = Unit
    override fun setVolume(level: Double) = Unit
    override fun setMuted(muted: Boolean) = Unit
    override fun setActiveTracks(ids: List<Long>) = Unit
    override fun setRate(rate: Double) = Unit
}
