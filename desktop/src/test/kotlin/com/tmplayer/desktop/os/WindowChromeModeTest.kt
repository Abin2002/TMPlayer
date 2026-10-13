package com.tmplayer.desktop.os

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowChromeModeTest {

    private fun linux(vararg env: Pair<String, String>, systemTitleBar: Boolean = false) =
        WindowChromeMode.decide(windows = false, linux = true, env = env.toMap(), systemTitleBar = systemTitleBar)

    @Test
    fun `tiling window managers are recognised by name`() {
        for (name in listOf("sway", "i3", "Hyprland", "river", "dwm", "bspwm", "niri", "sway:wlroots")) {
            assertTrue(name, WindowChromeMode.isTiling(mapOf("XDG_CURRENT_DESKTOP" to name)))
        }
        assertTrue(WindowChromeMode.isTiling(mapOf("XDG_SESSION_DESKTOP" to "i3")))
        assertTrue(WindowChromeMode.isTiling(mapOf("DESKTOP_SESSION" to "/usr/share/xsessions/bspwm")))
    }

    @Test
    fun `tiling window managers are recognised by their sockets`() {
        assertTrue(WindowChromeMode.isTiling(mapOf("SWAYSOCK" to "/run/user/1000/sway-ipc.sock")))
        assertTrue(WindowChromeMode.isTiling(mapOf("I3SOCK" to "/run/user/1000/i3/ipc-socket")))
        assertTrue(WindowChromeMode.isTiling(mapOf("HYPRLAND_INSTANCE_SIGNATURE" to "abc")))
        assertFalse(WindowChromeMode.isTiling(mapOf("SWAYSOCK" to " ")))
    }

    @Test
    fun `desktops that draw a title bar keep it`() {
        for (name in listOf("GNOME", "KDE", "XFCE", "ubuntu:GNOME", "X-Cinnamon", "MATE", "LXQt")) {
            assertFalse(name, WindowChromeMode.isTiling(mapOf("XDG_CURRENT_DESKTOP" to name)))
        }
        assertFalse(WindowChromeMode.isTiling(emptyMap()))
        // A sway socket left over in a GNOME session is not a tiling window manager.
        assertFalse(WindowChromeMode.isTiling(mapOf("XDG_CURRENT_DESKTOP" to "GNOME", "SWAYSOCK" to "/tmp/s")))
    }

    @Test
    fun `each platform gets its mode`() {
        assertEquals(WindowChromeMode.Custom, WindowChromeMode.decide(windows = true, linux = false, env = emptyMap(), systemTitleBar = false))
        assertEquals(WindowChromeMode.Tiling, linux("XDG_CURRENT_DESKTOP" to "sway"))
        assertEquals(WindowChromeMode.System, linux("XDG_CURRENT_DESKTOP" to "GNOME"))
        assertEquals(WindowChromeMode.System, WindowChromeMode.decide(windows = false, linux = false, env = mapOf("SWAYSOCK" to "x"), systemTitleBar = false))
    }

    @Test
    fun `the setting and the variable keep the system title bar`() {
        assertEquals(WindowChromeMode.System, linux("XDG_CURRENT_DESKTOP" to "sway", systemTitleBar = true))
        assertEquals(WindowChromeMode.System, linux("XDG_CURRENT_DESKTOP" to "sway", WindowChromeMode.ENV_SYSTEM_TITLE_BAR to "1"))
        assertEquals(
            WindowChromeMode.System,
            WindowChromeMode.decide(windows = true, linux = false, env = mapOf(WindowChromeMode.ENV_SYSTEM_TITLE_BAR to "true"), systemTitleBar = false),
        )
        assertEquals(WindowChromeMode.Tiling, linux("XDG_CURRENT_DESKTOP" to "sway", WindowChromeMode.ENV_SYSTEM_TITLE_BAR to "0"))
    }
}
