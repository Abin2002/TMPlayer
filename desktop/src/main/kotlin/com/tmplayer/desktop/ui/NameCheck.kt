package com.tmplayer.desktop.ui

import com.tmplayer.data.NameReport
import com.tmplayer.desktop.DesktopPaths
import com.tmplayer.desktop.DesktopServices
import com.tmplayer.platform.Background
import kotlinx.coroutines.launch
import java.io.File

/**
 * The developer rows in Settings, hidden from everybody else: shown with `-Dtmplayer.dev=true`, or
 * once the version line in About has been clicked [TAPS] times, which turns
 * [com.tmplayer.data.SettingsStore.developerOptions] on for good.
 */
internal object DevOptions {
    const val TAPS = 7

    /** Set on the command line, for a development run. */
    val forced: Boolean = System.getProperty("tmplayer.dev") == "true"

    private var taps = 0

    /** One click on the version line. */
    fun tap() {
        taps++
        if (taps == TAPS) Background.scope.launch { runCatching { DesktopServices.settings.setDeveloperOptions(true) } }
    }
}

/**
 * Writes the name check ([NameReport]) for the last chat listing opened to a text file under the
 * app's own data folder, and returns it; null when no chat has been opened yet. Nothing is sent
 * anywhere: the viewer opens the file, and passes it on only if they choose to.
 */
internal fun saveNameCheck(now: Long = System.currentTimeMillis()): File? {
    val items = NameReport.lastListing
    if (items.isEmpty()) return null
    val dir = File(DesktopPaths.dataDir, "name-check").apply { mkdirs() }
    return File(dir, NameReport.fileName(now)).apply { writeText(NameReport.text(items)) }
}
