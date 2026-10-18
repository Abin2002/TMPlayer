package com.tmplayer.data

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.tmplayer.App
import com.tmplayer.MainActivity
import com.tmplayer.R
import com.tmplayer.i18n.L
import com.tmplayer.platform.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The two things a phone checks while TMPlayer is closed, as platform jobs (JobScheduler, which
 * needs no library): new videos in the starred chats ([NewVideoAlerts]), every
 * [NEW_VIDEOS_EVERY_MS] while online with the battery not low, and whether the "Don't forget to
 * sponsor" notification is due ([SupportReminder.nudgeDue]), once a day.
 *
 * A TV schedules neither: its notifications are mostly out of sight, and Home's rows already show
 * what is new. Neither asks for the notification permission: the new videos switch asks when it
 * is turned on, and the sponsor reminder simply stays quiet without it.
 *
 * **TDLib.** The process the job wakes starts TDLib in [App], as the download service's does, and
 * the new videos check waits for a connected session for at most [CONNECT_WAIT_MS]. TDLib is the
 * process's one client, shared with any screen that opens, so the check does not close it; it asks
 * for one page per chat, one chat at a time, and the system reclaims the idle process as it does
 * after a download.
 */
class BackgroundChecks : JobService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        running = scope.launch {
            runCatching {
                when (params.jobId) {
                    JOB_NEW_VIDEOS -> checkNewVideos(applicationContext)
                    JOB_SPONSOR -> checkSponsorNudge(applicationContext)
                }
            }.onFailure { Logger.w(TAG, "background check failed", it) }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        // Periodic either way; the next window comes round by itself.
        return false
    }

    companion object {
        private const val TAG = "BackgroundChecks"

        const val JOB_NEW_VIDEOS = 4301
        const val JOB_SPONSOR = 4302

        const val CHANNEL_NEWS = "news"
        const val CHANNEL_NEW_VIDEOS = "new_videos"

        private const val NOTIFY_NEW_VIDEOS = 0x200001
        private const val NOTIFY_SPONSOR = 0x200002

        /** Every three hours, which the system may stretch to suit the battery. */
        const val NEW_VIDEOS_EVERY_MS = 3L * 60 * 60 * 1000
        private const val SPONSOR_EVERY_MS = 24L * 60 * 60 * 1000
        private const val CONNECT_WAIT_MS = 90_000L

        /** A launch from the sponsor reminder: the shell opens the Buy Me a Coffee window. */
        const val OPEN_SUPPORT = "support"

        /** A launch from a new videos notification: the chat to open, or 0 for the chat list. */
        const val EXTRA_OPEN_CHAT = "com.tmplayer.extra.OPEN_CHAT"

        /**
         * Makes the jobs match the settings: called when the app starts and whenever either switch
         * changes. Nothing at all on a TV, or in a build without donation links for the reminder.
         */
        suspend fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            val tv = FormFactor.isTv(context)
            val settings = SettingsStore(context)
            val newVideos = !tv && settings.newVideoAlertsNow()
            val sponsor = !tv && SupportReminder.enabled && settings.sponsorRemindersNow() &&
                !settings.supportCountersNow().finished
            ensure(context, scheduler, JOB_NEW_VIDEOS, newVideos) { component ->
                JobInfo.Builder(JOB_NEW_VIDEOS, component)
                    .setPeriodic(NEW_VIDEOS_EVERY_MS)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setRequiresBatteryNotLow(true)
                    .setPersisted(true)
                    .build()
            }
            ensure(context, scheduler, JOB_SPONSOR, sponsor) { component ->
                JobInfo.Builder(JOB_SPONSOR, component)
                    .setPeriodic(SPONSOR_EVERY_MS)
                    .setRequiresBatteryNotLow(true)
                    .setPersisted(true)
                    .build()
            }
        }

        private fun ensure(
            context: Context,
            scheduler: JobScheduler,
            id: Int,
            wanted: Boolean,
            build: (ComponentName) -> JobInfo,
        ) {
            val pending = scheduler.getPendingJob(id)
            when {
                wanted && pending == null -> runCatching {
                    scheduler.schedule(build(ComponentName(context, BackgroundChecks::class.java)))
                }.onFailure { Logger.w(TAG, "job $id not scheduled", it) }
                !wanted && pending != null -> scheduler.cancel(id)
            }
        }

        /** Whether a notification posted now would be seen: allowed, and on Android 13, granted. */
        fun canNotify(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
            return manager(context).areNotificationsEnabled()
        }

        /** One check of the starred chats, and the notification it finds, if any. */
        suspend fun checkNewVideos(context: Context) {
            val settings = SettingsStore(context)
            if (!settings.newVideoAlertsNow()) return
            Td.start(context)
            val session = withTimeoutOrNull(CONNECT_WAIT_MS) { Td.awaitConnectedSession() } ?: return
            val alert = NewVideoAlerts.check(session.client, settings, ChatGate.awaitReady()) ?: return
            // With the app on screen the lists already show what came in.
            if (App.inFront) return
            postNewVideos(context, alert)
        }

        /** The "Don't forget to sponsor" notification, if it is due and would be seen. */
        suspend fun checkSponsorNudge(context: Context) {
            if (FormFactor.isTv(context) || App.inFront || !canNotify(context)) return
            if (!SupportReminder.claimNudge(SettingsStore(context), System.currentTimeMillis())) return
            ensureChannels(context)
            val notification = Notification.Builder(context, CHANNEL_NEWS)
                .setSmallIcon(R.drawable.ic_stat_support)
                .setContentTitle(L.notifySponsorTitle)
                .setContentText(L.notifySponsorBody)
                .setStyle(Notification.BigTextStyle().bigText(L.notifySponsorBody))
                .setContentIntent(open(context, NOTIFY_SPONSOR) { it.putExtra(MainActivity.EXTRA_OPEN, OPEN_SUPPORT) })
                .setAutoCancel(true)
                .build()
            runCatching { manager(context).notify(NOTIFY_SPONSOR, notification) }
        }

        private fun postNewVideos(context: Context, alert: NewVideoAlerts.Alert) {
            if (!canNotify(context)) return
            ensureChannels(context)
            val builder = Notification.Builder(context, CHANNEL_NEW_VIDEOS)
                .setSmallIcon(R.drawable.ic_stat_new_videos)
                .setContentTitle(alert.title)
                // Kept off the lock screen whatever the alert says: a chat's name is private.
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(
                    Notification.Builder(context, CHANNEL_NEW_VIDEOS)
                        .setSmallIcon(R.drawable.ic_stat_new_videos)
                        .setContentTitle(L.notifyNewVideosPrivate)
                        .build(),
                )
                .setContentIntent(open(context, NOTIFY_NEW_VIDEOS) { it.putExtra(EXTRA_OPEN_CHAT, alert.chatId ?: 0L) })
                .setAutoCancel(true)
            alert.body?.let { builder.setContentText(it).setStyle(Notification.BigTextStyle().bigText(it)) }
            runCatching { manager(context).notify(NOTIFY_NEW_VIDEOS, builder.build()) }
        }

        private fun open(context: Context, code: Int, extras: (Intent) -> Unit): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .also(extras)
            return PendingIntent.getActivity(
                context,
                code,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /** "TMPlayer news", quiet, and "New videos", which may make a sound. */
        fun ensureChannels(context: Context) {
            val news = NotificationChannel(CHANNEL_NEWS, L.notifyChannelNews, NotificationManager.IMPORTANCE_LOW).apply {
                description = L.notifyChannelNewsDescription
                setShowBadge(false)
            }
            val videos = NotificationChannel(CHANNEL_NEW_VIDEOS, L.notifyChannelNewVideos, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = L.notifyChannelNewVideosDescription
            }
            manager(context).createNotificationChannels(listOf(news, videos))
        }

        private fun manager(context: Context): NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
}
