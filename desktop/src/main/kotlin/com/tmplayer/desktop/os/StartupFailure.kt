package com.tmplayer.desktop.os

import com.tmplayer.desktop.SelfUpdate
import java.io.PrintWriter
import java.io.StringWriter
import javax.swing.JOptionPane
import javax.swing.JScrollPane
import javax.swing.JTextArea

/**
 * What happens to an error before the main window is up: it is shown in a plain Swing dialog and
 * the process ends.
 *
 * Without this, a launch that failed on the way (a native library that would not load, a corrupt
 * settings file) left a process with no window that TDLib's threads kept alive, holding the single
 * instance lock, so every later launch handed over to it and quit: the app "did not open" and said
 * nothing. The text is English on purpose, since the catalogs may be what failed.
 *
 * The dialog offers the download page, since a crash at start also keeps the in-app updater from
 * running (it waits for the first frame): 1.27.0 on Windows crashed at Home, and the 1.27.1 that
 * fixed it could only be reached by hand.
 */
object StartupFailure {

    @Volatile
    private var started = false

    @Volatile
    private var reported = false

    /** Errors on the main thread or the UI thread end the launch until [started] is called. */
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            previous?.uncaughtException(thread, error)
            if (!started && (thread.name == "main" || thread.name.startsWith("AWT-EventQueue"))) report(error)
        }
    }

    /** The window is up; from here on an error is the app's to handle, not the launch's. */
    fun started() {
        started = true
    }

    /** Shows [error] and ends the process; never returns. */
    fun report(error: Throwable): Nothing {
        if (reported) AppExit.now(1)
        reported = true
        runCatching {
            System.err.println("E/Startup: TMPlayer could not start")
            error.printStackTrace()
            System.err.flush()
        }
        // Sent and flushed before the dialog with the crash reports switch on, written down for the
        // next start otherwise (CP102g): the dialog can sit unanswered, and the exit after it halts.
        com.tmplayer.desktop.DesktopCrashReports.beforeStartupFailure(error)
        runCatching {
            val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
            val log = DesktopLog.file?.let { "\n\nThe full log is in $it" }.orEmpty()
            val newer = "\n\nA newer version may already fix this: ${SelfUpdate.DOWNLOAD_PAGE}"
            val text = JTextArea("TMPlayer could not start.$log$newer\n\n$trace", 16, 72).apply {
                isEditable = false
                caretPosition = 0
            }
            val options = arrayOf<Any>("Open the download page", "Close")
            val choice = JOptionPane.showOptionDialog(
                null, JScrollPane(text), "TMPlayer", JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE,
                null, options, options[0],
            )
            if (choice == 0) OpenExternal.browseNow(SelfUpdate.DOWNLOAD_PAGE)
        }
        AppExit.now(1)
    }
}
