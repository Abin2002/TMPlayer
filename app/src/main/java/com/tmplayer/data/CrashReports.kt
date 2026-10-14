package com.tmplayer.data

import android.content.Context
import android.util.Log
import com.tmplayer.BuildConfig
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.android.core.SentryAndroid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Crash reporting, off unless the viewer asks for it.
 *
 * The app sends nothing to a server of mine, so this is the one narrow exception. Nothing here
 * runs until [start] is called, [start] does nothing unless the switch in Settings is on, and the
 * switch is off on a fresh install. Sentry's own auto-start ContentProvider is disabled in the
 * manifest, so there is no other way in.
 *
 * With the switch off, [watch] writes a crash down on the device instead, and nothing more. The
 * next start offers it in [pending], and it is sent only if the viewer then says so: once with
 * [sendPending], or from then on by turning the switch on.
 *
 * A report contains the stack trace, the app version, the Android version and the device model,
 * and nothing else: no breadcrumbs, sessions, screen names, filenames or chat names. Every
 * automatic breadcrumb source is off, because a breadcrumb is exactly where the name of the video
 * somebody was watching would otherwise turn up.
 *
 * With no [BuildConfig.SENTRY_DSN] compiled in, which is what a fork and every CI build get, this
 * is inert and Settings does not offer the switch at all.
 */
object CrashReports {

    /** Whether this build has anywhere to send a report. Nothing is offered to the viewer if not. */
    val available: Boolean = BuildConfig.SENTRY_DSN.isNotBlank()

    private var started = false

    /**
     * Brings the SDK up, once, if this build has a DSN and [enabled] is true.
     *
     * Called from [com.tmplayer.App] after reading the setting, and again when the viewer turns
     * the switch on. Turning it off calls [stop].
     */
    @Synchronized
    fun start(context: Context, enabled: Boolean) {
        if (!available || !enabled) return
        enabledNow = true
        if (started) return
        init(context)
        started = true
        Log.i(TAG, "Crash reporting is on, at the viewer's request.")
    }

    private fun init(context: Context) {
        SentryAndroid.init(context.applicationContext) { options ->
            options.dsn = BuildConfig.SENTRY_DSN
            options.release = "tmplayer@${BuildConfig.VERSION_NAME}"
            options.environment = if (BuildConfig.DEBUG) "debug" else "release"

            // Crashes and application-not-responding, and nothing else. Performance tracing sends
            // a sample of ordinary, healthy operations, which is usage data by another name.
            options.tracesSampleRate = 0.0
            options.isEnableAutoSessionTracking = false

            // No trail of what the viewer was doing before the crash: a breadcrumb is written by
            // every activity change, system event and network call, and any of them can carry the
            // name of a chat or a file.
            options.maxBreadcrumbs = 0
            options.isEnableActivityLifecycleBreadcrumbs = false
            options.isEnableAppComponentBreadcrumbs = false
            options.isEnableSystemEventBreadcrumbs = false
            options.isEnableAppLifecycleBreadcrumbs = false
            options.isEnableUserInteractionBreadcrumbs = false
            options.isEnableNetworkEventBreadcrumbs = false

            // No IP address and no device name.
            options.isSendDefaultPii = false
            options.isAttachThreads = true
            options.isAttachStacktrace = true

            // Two gates, in code rather than in configuration. If the switch went off between the
            // crash and the send, the report is dropped. Otherwise it is cut down to the stack
            // trace, the versions and the device model; see CrashReportScrubber.
            // The one exception to the first gate is the crash the viewer has just agreed to send.
            options.setBeforeSend { event, _ ->
                when {
                    sendingPending -> CrashReportScrubber.scrub(event.apply { threads = null })
                    !enabledNow -> null
                    else -> CrashReportScrubber.scrub(event)
                }
            }
            options.setDiagnosticLevel(SentryLevel.ERROR)
        }
    }

    /**
     * Writes a crash down on the device while the switch is off, before the process dies.
     *
     * Called first thing in [com.tmplayer.App.onCreate]. When the switch is on, Sentry puts its own
     * handler in front of this one and reports the crash itself; this one then sees it is on and
     * writes nothing. Only the newest crash is kept.
     */
    fun watch(context: Context) {
        if (!available) return
        file = File(context.filesDir, "crash/pending.json")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            if (!enabledNow) {
                runCatching {
                    CrashRecord.of(error, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toLong())
                        .write(file!!)
                }
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private var file: File? = null

    private val _pending = MutableStateFlow(false)

    /** Whether a crash from an earlier run is waiting for the viewer's answer. */
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    /**
     * What to do with a crash left from the last run, once the switch has been read at startup.
     *
     * A crash from another build is thrown away: its line numbers belong to code this device no
     * longer runs. With the switch on (turned on after the crash, or before Sentry had finished
     * starting) it is sent without asking; otherwise [pending] asks.
     */
    fun review(context: Context, enabled: Boolean) {
        val file = file ?: return
        val record = CrashRecord.read(file)
        if (record == null || record.versionCode != BuildConfig.VERSION_CODE.toLong()) {
            file.delete()
            return
        }
        if (enabled) sendPending(context) else _pending.value = true
    }

    /**
     * Sends the crash left from the last run, once, then forgets it. Blocks while the report goes
     * out, so it belongs on a background thread.
     *
     * With the switch off the SDK is brought up for this one report and closed again after it.
     */
    @Synchronized
    fun sendPending(context: Context) {
        _pending.value = false
        val file = file ?: return
        val record = CrashRecord.read(file)
        file.delete()
        if (record == null || !available) return
        val wasStarted = started
        if (!wasStarted) init(context)
        try {
            sendingPending = true
            Sentry.captureEvent(record.toEvent())
        } finally {
            sendingPending = false
        }
        Sentry.flush(FLUSH_MILLIS)
        if (!wasStarted) runCatching { Sentry.close() }
        Log.i(TAG, "The last crash was sent, at the viewer's request.")
    }

    /** Forgets the crash left from the last run without sending it. */
    fun discardPending() {
        _pending.value = false
        file?.delete()
    }

    @Volatile
    private var sendingPending = false

    /**
     * Turns reporting off and closes the SDK.
     *
     * The flag is cleared first, so a crash occurring during the close is dropped by the
     * beforeSend gate above rather than sent by a half-shut client.
     */
    @Synchronized
    fun stop() {
        enabledNow = false
        if (!started) return
        runCatching { Sentry.close() }
        started = false
        Log.i(TAG, "Crash reporting is off.")
    }

    @Volatile
    private var enabledNow = false

    private const val FLUSH_MILLIS = 5_000L

    private const val TAG = "CrashReports"
}
