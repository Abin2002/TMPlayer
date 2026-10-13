package com.tmplayer.desktop.os

import java.util.Locale

/**
 * Who draws the window's minimize, maximize and close buttons.
 *
 * - [System]: the window system's own title bar does (GNOME, KDE, Xfce, macOS), or the viewer
 *   asked for it.
 * - [Custom]: Windows. The window is undecorated and the app draws all three at the end of each
 *   page's header, with its own resize edges (see `WindowControls.kt`).
 * - [Tiling]: a tiling Linux window manager that draws no title bar (sway, i3, Hyprland...). The
 *   window keeps its decorations setting, and the app draws close and a fullscreen toggle; there
 *   is no minimize, because a tiling window manager has nowhere to minimize to.
 */
enum class WindowChromeMode {
    System,
    Custom,
    Tiling,
    ;

    /** True when the app draws window buttons of its own. */
    val inApp: Boolean get() = this != System

    companion object {
        /** `TMPLAYER_SYSTEM_TITLEBAR=1` keeps the system's title bar whatever this would pick. */
        const val ENV_SYSTEM_TITLE_BAR = "TMPLAYER_SYSTEM_TITLEBAR"

        /** The mode for this process: the OS, its environment and the Settings switch. */
        fun current(systemTitleBar: Boolean): WindowChromeMode = decide(
            windows = OsInfo.isWindows,
            linux = OsInfo.isLinux,
            env = java.lang.System.getenv(),
            systemTitleBar = systemTitleBar,
        )

        fun decide(windows: Boolean, linux: Boolean, env: Map<String, String>, systemTitleBar: Boolean): WindowChromeMode {
            if (systemTitleBar || env[ENV_SYSTEM_TITLE_BAR].orEmpty().trim() in TRUE) return System
            return when {
                windows -> Custom
                linux && isTiling(env) -> Tiling
                else -> System
            }
        }

        /**
         * True when the Linux session is a tiling window manager that draws no title bar. A
         * desktop that names itself as one that does (GNOME, KDE...) wins over a stray socket
         * variable, so a sway shell nested in GNOME, or one left in the environment, is ignored.
         */
        fun isTiling(env: Map<String, String>): Boolean {
            val names = DESKTOP_KEYS.flatMap { key ->
                env[key].orEmpty().lowercase(Locale.ROOT).split(':').map { it.trim().substringAfterLast('/') }
            }.filter { it.isNotEmpty() }
            if (names.any { it in DECORATED }) return false
            if (names.any { it in TILING }) return true
            return SOCKETS.any { !env[it].isNullOrBlank() }
        }

        private val TRUE = setOf("1", "true", "yes", "on")

        /** `DESKTOP_SESSION` can be a path to the session file (`/usr/share/xsessions/i3`). */
        private val DESKTOP_KEYS = listOf("XDG_CURRENT_DESKTOP", "XDG_SESSION_DESKTOP", "DESKTOP_SESSION")

        private val TILING = setOf(
            "sway", "i3", "hyprland", "river", "dwm", "bspwm", "niri", "dwl", "qtile", "awesome",
            "xmonad", "herbstluftwm", "spectrwm", "leftwm", "i3wm",
        )

        private val DECORATED = setOf(
            "gnome", "kde", "plasma", "xfce", "cinnamon", "x-cinnamon", "mate", "lxqt", "lxde",
            "budgie", "budgie-desktop", "unity", "pantheon", "deepin", "cosmic", "ubuntu", "gnome-classic",
        )

        /** Variables only those window managers set, for a session that names no desktop. */
        private val SOCKETS = listOf("SWAYSOCK", "I3SOCK", "HYPRLAND_INSTANCE_SIGNATURE", "NIRI_SOCKET")
    }
}
