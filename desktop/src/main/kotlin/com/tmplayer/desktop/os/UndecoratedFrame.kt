package com.tmplayer.desktop.os

import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinUser
import com.tmplayer.platform.Logger
import java.awt.Frame
import java.awt.Rectangle
import java.awt.Toolkit

/**
 * What an undecorated window on Windows needs to behave like a framed one, short of the
 * JetBrains Runtime's custom title bar.
 *
 * - Maximized, an undecorated frame would cover the taskbar (JDK-4737788): AWT answers
 *   `WM_GETMINMAXINFO` from [Frame.getMaximizedBounds], so that is held to the work area of the
 *   monitor the window is on, and moved with it.
 * - The taskbar button minimizes and restores only a window with `WS_MINIMIZEBOX`, and its menu
 *   (and Alt+Space) needs `WS_SYSMENU`; an undecorated frame has neither, so they are put back.
 *   None of them draws anything on a window with no caption.
 */
object UndecoratedFrame {

    /** The work area of the monitor [frame] is on, in AWT's screen coordinates. */
    fun workArea(frame: Frame): Rectangle? {
        val gc = frame.graphicsConfiguration ?: return null
        val b = gc.bounds
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(gc)
        return Rectangle(
            b.x + insets.left,
            b.y + insets.top,
            b.width - insets.left - insets.right,
            b.height - insets.top - insets.bottom,
        )
    }

    /** Holds [frame]'s maximized bounds to its monitor's work area; a maximized frame is fitted again. */
    fun holdToWorkArea(frame: Frame) {
        if (!frame.isUndecorated) return
        val area = workArea(frame) ?: return
        if (area == frame.maximizedBounds) return
        frame.maximizedBounds = area
        // Maximized before the bounds were known (a window that opens maximized), it covers the
        // whole monitor: maximize it again so Windows asks for the new bounds.
        if (frame.extendedState and Frame.MAXIMIZED_BOTH == Frame.MAXIMIZED_BOTH && frame.isShowing) {
            val state = frame.extendedState
            frame.extendedState = state and Frame.MAXIMIZED_BOTH.inv()
            frame.extendedState = state
        }
    }

    /** Puts the minimize and maximize boxes and the system menu back on an undecorated frame. */
    fun restoreCaptionStyles(frame: Frame) {
        if (!OsInfo.isWindows || !frame.isUndecorated || !frame.isDisplayable) return
        runCatching {
            val hwnd = NativeFullscreen.windowsHandle(frame) ?: return
            val user32 = User32.INSTANCE
            val style = user32.GetWindowLong(hwnd, WinUser.GWL_STYLE)
            val wanted = style or WinUser.WS_MINIMIZEBOX or WinUser.WS_MAXIMIZEBOX or WinUser.WS_SYSMENU
            if (wanted != style) user32.SetWindowLong(hwnd, WinUser.GWL_STYLE, wanted)
        }.onFailure { Logger.w(TAG, "could not set the window styles: ${it.message}") }
    }

    private const val TAG = "UndecoratedFrame"
}
