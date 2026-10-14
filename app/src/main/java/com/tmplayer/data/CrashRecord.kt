package com.tmplayer.data

import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.protocol.Mechanism
import io.sentry.protocol.SentryException
import io.sentry.protocol.SentryStackFrame
import io.sentry.protocol.SentryStackTrace
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Date

/**
 * A crash written down on the device while crash reporting is off, so the next start can ask
 * whether to send it.
 *
 * It holds what a report would: the exception chain with its stack frames, the moment, and the
 * build it happened in. It is never sent on its own; [CrashReports] turns it into a report only
 * after the viewer says yes, and the report then goes through [CrashReportScrubber] like any other.
 */
internal class CrashRecord(
    val time: Long,
    val versionName: String,
    val versionCode: Long,
    /** Outermost first, each followed by its cause. */
    val chain: List<Link>,
) {

    class Link(
        val type: String,
        val message: String?,
        val frames: List<StackTraceElement>,
    )

    /**
     * The report as Sentry would have built it at the crash: causes before the exception that
     * caused nothing, oldest frame first, marked as unhandled.
     */
    fun toEvent(): SentryEvent = SentryEvent(Date(time)).apply {
        level = SentryLevel.FATAL
        release = "tmplayer@$versionName"
        setExceptions(chain.reversed().map { link ->
            SentryException().apply {
                type = link.type.substringAfterLast('.')
                module = link.type.substringBeforeLast('.', "")
                value = link.message
                mechanism = Mechanism().apply {
                    type = "UncaughtExceptionHandler"
                    isHandled = false
                }
                stacktrace = SentryStackTrace(link.frames.reversed().map { element ->
                    SentryStackFrame().apply {
                        module = element.className
                        function = element.methodName
                        filename = element.fileName
                        lineno = element.lineNumber.takeIf { it >= 0 }
                        isNative = element.isNativeMethod
                        isInApp = element.className.startsWith("com.tmplayer")
                    }
                })
            }
        })
    }

    fun write(file: File) {
        val json = JSONObject()
            .put("time", time)
            .put("versionName", versionName)
            .put("versionCode", versionCode)
            .put("chain", JSONArray(chain.map { link ->
                JSONObject()
                    .put("type", link.type)
                    .put("message", link.message ?: JSONObject.NULL)
                    .put("frames", JSONArray(link.frames.map { element ->
                        JSONObject()
                            .put("class", element.className)
                            .put("method", element.methodName)
                            .put("file", element.fileName ?: JSONObject.NULL)
                            .put("line", element.lineNumber)
                    }))
            }))
        file.parentFile?.mkdirs()
        // Through a temporary file, so a process killed halfway leaves the old record or none,
        // never half a one.
        val partial = File(file.path + ".part")
        partial.writeText(json.toString())
        partial.renameTo(file)
    }

    companion object {

        /** Deep enough for any real chain, and a stop for one that loops back on itself. */
        private const val MAX_CAUSES = 8

        fun of(error: Throwable, versionName: String, versionCode: Long): CrashRecord {
            val chain = generateSequence(error) { it.cause.takeIf { cause -> cause !== it } }
                .take(MAX_CAUSES)
                .map { Link(it.javaClass.name, it.message, it.stackTrace.toList()) }
                .toList()
            return CrashRecord(System.currentTimeMillis(), versionName, versionCode, chain)
        }

        /** The record in [file], or null when there is none or it cannot be read. */
        fun read(file: File): CrashRecord? = runCatching {
            if (!file.isFile) return null
            val json = JSONObject(file.readText())
            val chain = json.getJSONArray("chain").let { links ->
                (0 until links.length()).map { i ->
                    val link = links.getJSONObject(i)
                    val frames = link.getJSONArray("frames")
                    Link(
                        type = link.getString("type"),
                        message = link.optString("message").takeUnless { link.isNull("message") },
                        frames = (0 until frames.length()).map { f ->
                            val frame = frames.getJSONObject(f)
                            StackTraceElement(
                                frame.getString("class"),
                                frame.getString("method"),
                                frame.optString("file").takeUnless { frame.isNull("file") },
                                frame.getInt("line"),
                            )
                        },
                    )
                }
            }
            CrashRecord(json.getLong("time"), json.getString("versionName"), json.getLong("versionCode"), chain)
        }.getOrNull()
    }
}
