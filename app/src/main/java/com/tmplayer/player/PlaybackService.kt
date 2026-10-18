package com.tmplayer.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.tmplayer.R
import com.tmplayer.i18n.L

/**
 * "Keep playing audio with the screen off or in another app" (Settings, Playback; the phone only).
 *
 * The player, its media session and the TDLib stream all stay where they always were, in
 * [PlayerActivity]. When the viewer leaves the player with the setting on (Home, another app, the
 * power button) and picture in picture did not take over, the activity hands its existing session
 * to this service ([start]), which is what Android needs to let a backgrounded app keep sound
 * going: a foreground service of the media playback type, and Media3's media notification with
 * play, pause, the episode steps and, from Android 13, the seek bar. Media3 drives the
 * notification and the foreground state from the session, so a pause from the notification lets
 * the service drop out of the foreground and a play brings it back.
 *
 * Coming back to the player takes the session back ([stop]); the activity was never gone. Swiping
 * the app away, or dismissing the paused notification (which Media3 sends as a stop to the
 * session's player), finishes the activity through [onStopRequested], and its onDestroy releases
 * the player and cancels the TDLib download as it always has.
 *
 * Not exported, and with no MediaSessionService intent filter: nothing outside the app binds it,
 * and the media button receiver never starts it with no video behind it.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var held: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setNotificationId(NOTIFICATION_ID)
                .build()
                .apply { setSmallIcon(R.drawable.ic_notify_resume) },
        )
        take()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        take()
        return result
    }

    /** Adds the session the activity handed over, or stops when there is none (a sticky restart). */
    private fun take() {
        val session = pending
        if (session == null) {
            stopSelf()
            return
        }
        if (session === held) return
        held?.let { runCatching { removeSession(it) } }
        held = session
        runCatching { addSession(session) }.onFailure {
            Log.w("TMPlayer", "Background audio could not take the session", it)
            stopSelf()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = held

    /** Swiped away from Recents: the video goes with the app, as with background audio off. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        held?.player?.pause()
        onStopRequested?.invoke()
        stopSelf()
    }

    override fun onDestroy() {
        held?.let { runCatching { removeSession(it) } }
        held = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 0x7A1

        @Volatile
        private var pending: MediaSession? = null

        @Volatile
        private var onStopRequested: (() -> Unit)? = null

        /** Whether a session is in the service's hands now. */
        val running: Boolean get() = pending != null

        /**
         * Hands [session] over as the viewer leaves the player. Called from onStop, inside the
         * moments after leaving the foreground in which Android still lets an app start its
         * services. [onStop] is how the notification's stop, or a swipe from Recents, ends playback.
         */
        fun start(context: Context, session: MediaSession, onStop: () -> Unit) {
            pending = session
            onStopRequested = onStop
            ensureChannel(context)
            // The notification's tap brings the app back as it was left, the player on top.
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launch ->
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                runCatching {
                    session.setSessionActivity(
                        PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
                    )
                }
            }
            runCatching { context.startService(Intent(context, PlaybackService::class.java)) }
                .onFailure {
                    Log.w("TMPlayer", "Background audio could not start", it)
                    pending = null
                    onStopRequested = null
                }
        }

        /** Takes the session back: the player is in front again, or going away. */
        fun stop(context: Context) {
            if (pending == null) return
            pending = null
            onStopRequested = null
            runCatching { context.stopService(Intent(context, PlaybackService::class.java)) }
        }

        /** One channel, quiet: a media notification is a control, not news. */
        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(CHANNEL_ID, L.notifyChannelPlayback, NotificationManager.IMPORTANCE_LOW).apply {
                description = L.notifyChannelPlaybackDescription
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
