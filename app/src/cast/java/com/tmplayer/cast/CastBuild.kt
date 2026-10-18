package com.tmplayer.cast

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastDevice
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.MediaTrack
import com.google.android.gms.cast.TextTrackStyle
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.NotificationOptions
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The regular build's casting: Google's Cast framework (CP73). */
internal object CastBuild {
    fun create(context: Context): CastSupport = GoogleCastSupport(context)
}

/**
 * What the Cast framework reads at start: Google's Default Media Receiver (no receiver app of
 * TMPlayer's own to host), the framework's media notification and lock screen controls while a
 * video is on the TV, and the receiver stopped when the session ends.
 */
class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions {
        val notification = NotificationOptions.Builder()
            .setActions(
                listOf(
                    com.google.android.gms.cast.framework.media.MediaIntentReceiver.ACTION_REWIND,
                    com.google.android.gms.cast.framework.media.MediaIntentReceiver.ACTION_TOGGLE_PLAYBACK,
                    com.google.android.gms.cast.framework.media.MediaIntentReceiver.ACTION_FORWARD,
                    com.google.android.gms.cast.framework.media.MediaIntentReceiver.ACTION_STOP_CASTING,
                ),
                intArrayOf(1, 2),
            )
            .setSkipStepMs(SKIP_MS)
            .setTargetActivityClassName("com.tmplayer.MainActivity")
            .build()
        val media = CastMediaOptions.Builder()
            .setNotificationOptions(notification)
            .setMediaSessionEnabled(true)
            .build()
        return CastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .setCastMediaOptions(media)
            .setStopReceiverApplicationWhenEndingSession(true)
            .build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null

    private companion object {
        const val SKIP_MS = 10_000L
    }
}

/**
 * [CastSupport] over the Cast framework: MediaRouter finds the receivers while a picker is open
 * and selecting one starts a [CastSession]; its [RemoteMediaClient] loads and drives the video, and
 * its status comes back as [RemoteStatus]. Everything here runs on the main thread, as the
 * framework requires.
 */
private class GoogleCastSupport(context: Context) : CastSupport {

    private val main = Handler(Looper.getMainLooper())
    private val castContext: CastContext? = runCatching { CastContext.getSharedInstance(context) }
        .onFailure { Log.w(TAG, "No Cast framework here (no Google Play services?)", it) }
        .getOrNull()
    private val router: MediaRouter? = castContext?.let { runCatching { MediaRouter.getInstance(context) }.getOrNull() }
    private val selector = MediaRouteSelector.Builder()
        .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
        .build()

    override val available: Boolean = castContext != null && router != null

    private val _devices = MutableStateFlow<List<CastDeviceInfo>>(emptyList())
    override val devices: StateFlow<List<CastDeviceInfo>> = _devices.asStateFlow()
    private val _session = MutableStateFlow<CastSessionState>(CastSessionState.Idle)
    override val session: StateFlow<CastSessionState> = _session.asStateFlow()
    private val _remote = MutableStateFlow(RemoteStatus())
    override val remote: StateFlow<RemoteStatus> = _remote.asStateFlow()

    private var discovering = 0
    private var connectingId: String? = null

    private val routeCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshRoutes()
        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshRoutes()
        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshRoutes()
        override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) = refreshRoutes()
        override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) = refreshRoutes()
    }

    private var client: RemoteMediaClient? = null

    private val mediaCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() = publishRemote()
        override fun onMetadataUpdated() = publishRemote()
    }

    private val progress = RemoteMediaClient.ProgressListener { _, _ -> publishRemote() }

    private val castListener = object : com.google.android.gms.cast.Cast.Listener() {
        override fun onVolumeChanged() = publishRemote()
    }

    private val sessions = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {
            _session.value = CastSessionState.Connecting(session.castDevice?.friendlyName.orEmpty())
        }

        override fun onSessionStarted(session: CastSession, sessionId: String) = attach(session)

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            Log.w(TAG, "Cast session failed to start: $error")
            _session.value = CastSessionState.Failed(session.castDevice?.friendlyName.orEmpty())
            connectingId = null
            refreshRoutes()
        }

        override fun onSessionEnding(session: CastSession) = Unit

        override fun onSessionEnded(session: CastSession, error: Int) {
            detach()
            _session.value = CastSessionState.Idle
        }

        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = attach(session)

        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            detach()
            _session.value = CastSessionState.Idle
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {
            _session.value = CastSessionState.Suspended(session.castDevice?.friendlyName.orEmpty())
        }
    }

    init {
        castContext?.sessionManager?.addSessionManagerListener(sessions, CastSession::class.java)
        // A session left from before (the app was killed while casting) is picked up again.
        castContext?.sessionManager?.currentCastSession?.takeIf { it.isConnected }?.let(::attach)
    }

    private fun attach(session: CastSession) {
        detach()
        val device = session.castDevice
        connectingId = null
        session.addCastListener(castListener)
        currentSession = session
        client = session.remoteMediaClient?.also { c ->
            c.registerCallback(mediaCallback)
            c.addProgressListener(progress, PROGRESS_MS)
        }
        _session.value = CastSessionState.Connected(
            name = device?.friendlyName.orEmpty(),
            model = device?.modelName.orEmpty(),
            address = runCatching { device?.inetAddress }.getOrNull(),
        )
        publishRemote()
        refreshRoutes()
    }

    private var currentSession: CastSession? = null

    private fun detach() {
        client?.let { c ->
            runCatching { c.unregisterCallback(mediaCallback) }
            runCatching { c.removeProgressListener(progress) }
        }
        client = null
        currentSession?.let { runCatching { it.removeCastListener(castListener) } }
        currentSession = null
        _remote.value = RemoteStatus()
    }

    private fun publishRemote() {
        val c = client
        val s = currentSession
        val status: MediaStatus? = c?.mediaStatus
        _remote.value = RemoteStatus(
            playerState = when (status?.playerState) {
                MediaStatus.PLAYER_STATE_PLAYING -> RemoteStatus.PLAYING
                MediaStatus.PLAYER_STATE_PAUSED -> RemoteStatus.PAUSED
                MediaStatus.PLAYER_STATE_BUFFERING -> RemoteStatus.BUFFERING
                MediaStatus.PLAYER_STATE_LOADING -> RemoteStatus.LOADING
                else -> RemoteStatus.IDLE
            },
            idleReason = when (status?.idleReason) {
                MediaStatus.IDLE_REASON_FINISHED -> RemoteStatus.IDLE_FINISHED
                MediaStatus.IDLE_REASON_CANCELED -> RemoteStatus.IDLE_CANCELED
                MediaStatus.IDLE_REASON_INTERRUPTED -> RemoteStatus.IDLE_INTERRUPTED
                MediaStatus.IDLE_REASON_ERROR -> RemoteStatus.IDLE_ERROR
                else -> 0
            },
            positionMs = c?.approximateStreamPosition ?: 0L,
            durationMs = c?.streamDuration ?: 0L,
            volume = runCatching { s?.volume }.getOrNull() ?: 1.0,
            muted = runCatching { s?.isMute }.getOrNull() ?: false,
            contentId = c?.mediaInfo?.contentId,
            activeTrackIds = status?.activeTrackIds?.toList().orEmpty(),
        )
    }

    private fun refreshRoutes() {
        val r = router ?: return
        val selected = r.selectedRoute
        val connected = _session.value is CastSessionState.Connected
        _devices.value = r.routes
            .filter { it.isEnabled && !it.isDefault && !it.isBluetooth && it.matchesSelector(selector) }
            .map { route ->
                val device = runCatching { CastDevice.getFromBundle(route.extras) }.getOrNull()
                CastDeviceInfo(
                    id = route.id,
                    name = route.name,
                    model = device?.modelName ?: route.description.orEmpty(),
                    connected = connected && route.id == selected.id,
                    connecting = route.id == connectingId,
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    override fun startDiscovery() {
        val r = router ?: return
        if (discovering++ == 0) r.addCallback(selector, routeCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
        refreshRoutes()
    }

    override fun stopDiscovery() {
        val r = router ?: return
        if (discovering > 0 && --discovering == 0) r.removeCallback(routeCallback)
    }

    override fun connect(id: String) {
        val r = router ?: return
        val route = r.routes.firstOrNull { it.id == id } ?: return
        connectingId = id
        _session.value = CastSessionState.Connecting(route.name)
        refreshRoutes()
        r.selectRoute(route)
    }

    override fun endSession() {
        castContext?.sessionManager?.endCurrentSession(true)
    }

    override fun load(request: CastLoadRequest, onResult: (Boolean) -> Unit) {
        val c = client ?: return onResult(false)
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, request.title)
            if (request.subtitle.isNotBlank()) putString(MediaMetadata.KEY_SUBTITLE, request.subtitle)
        }
        val tracks = request.subtitles.map { s ->
            MediaTrack.Builder(s.id, MediaTrack.TYPE_TEXT)
                .setName(s.name)
                .setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
                .setContentId(s.url)
                .setContentType("text/vtt")
                .apply { s.language?.let { setLanguage(it) } }
                .build()
        }
        val info = MediaInfo.Builder(request.url)
            .setContentUrl(request.url)
            .setContentType(request.mime)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setMetadata(metadata)
            .apply {
                if (request.durationMs > 0) setStreamDuration(request.durationMs)
                if (tracks.isNotEmpty()) {
                    setMediaTracks(tracks)
                    setTextTrackStyle(
                        TextTrackStyle().apply {
                            backgroundColor = 0x00000000
                            edgeType = TextTrackStyle.EDGE_TYPE_OUTLINE
                            edgeColor = 0x000000FF
                        },
                    )
                }
            }
            .build()
        val load = MediaLoadRequestData.Builder()
            .setMediaInfo(info)
            .setAutoplay(request.autoplay)
            .setCurrentTime(request.startMs)
            .apply { if (request.activeTrackIds.isNotEmpty()) setActiveTrackIds(request.activeTrackIds.toLongArray()) }
            .build()
        c.load(load).setResultCallback { result ->
            val ok = result.status.isSuccess
            if (!ok) Log.w(TAG, "Receiver refused the load: ${result.status}")
            main.post { onResult(ok) }
        }
    }

    override fun play() {
        client?.play()
    }

    override fun pause() {
        client?.pause()
    }

    override fun seek(positionMs: Long) {
        client?.seek(MediaSeekOptions.Builder().setPosition(positionMs).build())
    }

    override fun stopMedia() {
        client?.stop()
    }

    override fun setVolume(level: Double) {
        runCatching { currentSession?.volume = level.coerceIn(0.0, 1.0) }
        publishRemote()
    }

    override fun setMuted(muted: Boolean) {
        runCatching { currentSession?.isMute = muted }
        publishRemote()
    }

    override fun setActiveTracks(ids: List<Long>) {
        client?.setActiveMediaTracks(ids.toLongArray())
    }

    override fun setRate(rate: Double) {
        client?.setPlaybackRate(rate)
    }

    private companion object {
        const val TAG = "TMPlayerCast"
        const val PROGRESS_MS = 500L
    }
}
