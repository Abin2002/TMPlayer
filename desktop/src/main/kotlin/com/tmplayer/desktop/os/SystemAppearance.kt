package com.tmplayer.desktop.os

import com.tmplayer.platform.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.types.Variant
import org.jetbrains.skiko.SystemTheme
import org.jetbrains.skiko.currentSystemTheme
import java.util.concurrent.TimeUnit

/**
 * Whether the OS is in its dark appearance, for the "System" theme, kept current while the app
 * runs so a change in the OS reaches the window without a restart.
 *
 * Windows (the registry's AppsUseLightTheme) and macOS (the effective appearance) are read through
 * skiko, which asks the OS each time. Linux is read from xdg-desktop-portal's
 * `org.freedesktop.appearance color-scheme` over the session bus, which GNOME, KDE and the wlroots
 * portals all answer, and failing that from `gsettings` (the color-scheme key, then a theme name
 * ending in "dark"). Where nothing says, dark: the right default for a window that plays video.
 *
 * Read once at start, then looked at again every couple of seconds on a daemon thread: a portal
 * read is one message on an open connection and skiko's is a native call, so polling costs less
 * than holding a signal subscription open, and works on the desktops that never send one.
 */
object SystemAppearance {

    private val state = MutableStateFlow(true)
    private var started = false

    /** True while the OS asks for dark, or says nothing. */
    val dark: StateFlow<Boolean>
        get() {
            start()
            return state.asStateFlow()
        }

    @Synchronized
    private fun start() {
        if (started) return
        started = true
        state.value = read() ?: true
        Thread({
            while (true) {
                runCatching { TimeUnit.MILLISECONDS.sleep(POLL_MS) }.onFailure { return@Thread }
                val now = runCatching { read() }.getOrNull() ?: continue
                if (now != state.value) {
                    Logger.i(TAG, "system appearance is now ${if (now) "dark" else "light"}")
                    state.value = now
                }
            }
        }, "system-appearance").apply { isDaemon = true }.start()
    }

    /** Dark, light, or null when the OS says nothing. */
    private fun read(): Boolean? = when {
        OsInfo.isLinux -> linux()
        else -> when (currentSystemTheme) {
            SystemTheme.DARK -> true
            SystemTheme.LIGHT -> false
            else -> null
        }
    }

    private var bus: DBusConnection? = null
    private var busFailed = false
    private var portalMissing = false
    private var lastGsettingsNanos = 0L
    private var lastGsettings: Boolean? = null

    private fun linux(): Boolean? {
        if (!portalMissing) {
            portal()?.let { return it }
        }
        // gsettings is a process per look, so it is looked at less often.
        val now = System.nanoTime()
        if (lastGsettingsNanos == 0L || now - lastGsettingsNanos > GSETTINGS_EVERY_NANOS) {
            lastGsettingsNanos = now
            lastGsettings = gsettings()
        }
        return lastGsettings
    }

    @DBusInterfaceName("org.freedesktop.portal.Settings")
    internal interface PortalSettings : DBusInterface {
        @Suppress("FunctionName")
        fun Read(namespace: String, key: String): Variant<*>
    }

    private fun portal(): Boolean? {
        if (bus == null && !busFailed) {
            bus = runCatching { DBusConnectionBuilder.forSessionBus().withShared(false).build() }
                .onFailure { busFailed = true }
                .getOrNull()
        }
        val conn = bus ?: run {
            portalMissing = true
            return null
        }
        val settings = runCatching {
            conn.getRemoteObject("org.freedesktop.portal.Desktop", "/org/freedesktop/portal/desktop", PortalSettings::class.java)
        }.getOrNull() ?: return null
        val value = runCatching { settings.Read("org.freedesktop.appearance", "color-scheme") }
            .onFailure {
                // No portal, or one without the appearance namespace: gsettings from now on.
                portalMissing = true
            }
            .getOrNull() ?: return null
        return portalScheme(unwrap(value))
    }

    /** Read wraps the value in a second variant; ReadOne and some portals do not. */
    private fun unwrap(value: Any?): Any? {
        var v = value
        while (v is Variant<*>) v = v.value
        return v
    }

    /** The portal's color-scheme: 1 prefers dark, 2 prefers light, 0 says nothing. */
    internal fun portalScheme(value: Any?): Boolean? = when ((value as? Number)?.toInt()) {
        1 -> true
        2 -> false
        else -> null
    }

    private fun gsettings(): Boolean? {
        fun get(key: String): String? = runCatching {
            val process = ProcessBuilder("gsettings", "get", "org.gnome.desktop.interface", key)
                .redirectErrorStream(true)
                .start()
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return@runCatching null
            }
            if (process.exitValue() != 0) null else process.inputStream.bufferedReader().readText()
        }.getOrNull()
        return gsettingsScheme(get("color-scheme"), get("gtk-theme"))
    }

    /** `'prefer-dark'` or `'prefer-light'`; else a theme named like `'Adwaita-dark'`; else nothing. */
    internal fun gsettingsScheme(colorScheme: String?, gtkTheme: String?): Boolean? {
        val scheme = colorScheme?.trim()?.trim('\'')
        when (scheme) {
            "prefer-dark" -> return true
            "prefer-light" -> return false
        }
        val theme = gtkTheme?.trim()?.trim('\'')?.lowercase() ?: return null
        if (theme.isEmpty()) return null
        return theme.endsWith("dark") || theme.endsWith("-dark")
    }

    private const val POLL_MS = 2_000L
    private const val GSETTINGS_EVERY_NANOS = 6_000_000_000L
    private const val TAG = "SystemAppearance"
}
