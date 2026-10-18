package com.tmplayer.desktop.os

import com.tmplayer.platform.Logger
import java.util.concurrent.atomic.AtomicLong

/**
 * Presses on Windows toasts. A toast from an app that is not packaged has no activator to call
 * back into, so each one that should do something carries a `tmplayer:press/<n>` link
 * ([register]). Windows opens the link by starting TMPlayer.exe with it (the scheme is registered
 * for the user by [WindowsNotifications]); [SingleInstance] hands that launch's arguments to the
 * running app, which passes them to [handle], and the press runs there.
 *
 * Only the presses of this run are known: a toast pressed after the app has closed starts it, and
 * the link is ignored. A link that reaches the app from anywhere else at most repeats a press the
 * app itself offered, raising the window or opening a page.
 */
internal object ToastPresses {
    const val SCHEME = "tmplayer"

    private const val PREFIX = "$SCHEME:press/"

    /** The newest presses; an old toast nobody pressed gives its place up. */
    private const val KEPT = 64

    private val next = AtomicLong()
    private val waiting = object : LinkedHashMap<Long, () -> Unit>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, () -> Unit>?) = size > KEPT
    }

    /** The link a toast launches to run [action] when it is pressed. */
    fun register(action: () -> Unit): String {
        val id = next.incrementAndGet()
        synchronized(waiting) { waiting[id] = action }
        return "$PREFIX$id"
    }

    /** Runs the press any of [args] carries, once. True if one did. */
    fun handle(args: List<String>): Boolean {
        val id = args.firstNotNullOfOrNull { arg ->
            arg.trim().removeSuffix("/").takeIf { it.startsWith(PREFIX, ignoreCase = true) }?.substring(PREFIX.length)?.toLongOrNull()
        } ?: return false
        val action = synchronized(waiting) { waiting.remove(id) } ?: return false
        runCatching(action).onFailure { Logger.w("ToastPresses", "toast press failed: ${it.message}") }
        return true
    }
}
