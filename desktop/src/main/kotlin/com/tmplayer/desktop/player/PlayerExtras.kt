package com.tmplayer.desktop.player

import java.io.File

/**
 * Chapters, shared with the Android player since its bar gained the same marks. See
 * [com.tmplayer.player.Chapters]; the names stay here so the desktop's player code reads as before.
 */
typealias Chapter = com.tmplayer.player.Chapter
typealias Chapters = com.tmplayer.player.Chapters

/**
 * A-B repeat, shared with the Android player since its menu gained the same lines. See
 * [com.tmplayer.player.AbLoop]; the name stays here so the desktop's player code reads as before.
 */
typealias AbLoop = com.tmplayer.player.AbLoop

/** Where a screenshot goes and what it is called. */
object ScreenshotFiles {

    /**
     * "Night Train 01-02-03.png" in [dir]: the video's name and the moment, a dash between the
     * parts of the time since a colon is not allowed in a Windows file name. A name already taken
     * gets " (2)", " (3)" and so on, so a second press on a paused frame keeps both.
     */
    fun fileFor(dir: File, title: String, positionMs: Long, exists: (File) -> Boolean = File::exists): File {
        val base = "${safeName(title)} ${clock(positionMs)}"
        var file = File(dir, "$base.png")
        var n = 2
        while (exists(file)) file = File(dir, "$base (${n++}).png")
        return file
    }

    /**
     * The title as a file name: no path separators or characters Windows refuses, no control
     * characters, no trailing dot or space, and not so long that a deep folder runs out of path.
     */
    fun safeName(title: String): String {
        val cleaned = title.replace(VIDEO_EXTENSION, "")
        val safe = cleaned.map { c -> if (c < ' ' || c in "\\/:*?\"<>|") ' ' else c }.joinToString("")
            .replace(Regex("\\s+"), " ")
            .take(MAX_NAME)
            .trim()
            .trimEnd('.', ' ')
        return safe.ifBlank { "TMPlayer" }
    }

    /** 1-02-03 for an hour or more, 02-03 below one. */
    fun clock(positionMs: Long): String {
        val total = (positionMs / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d-%02d-%02d".format(h, m, s) else "%02d-%02d".format(m, s)
    }

    private const val MAX_NAME = 80

    /** A video file's own extension on the end of a title, which the PNG's name has no use for. */
    private val VIDEO_EXTENSION = Regex("(?i)\\.(mkv|mp4|m4v|avi|webm|mov|ts|wmv|flv)$")
}
