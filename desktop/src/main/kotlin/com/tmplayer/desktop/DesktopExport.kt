package com.tmplayer.desktop

import java.io.File

/**
 * Copying a finished download out to a folder the viewer picks (CP82). The copy never replaces a
 * file already there: a second copy of "Film.mkv" lands as "Film (2).mkv".
 */
object DesktopExport {

    /** A file in [folder] named [name], or the first "name (n).ext" not taken. */
    fun freeName(folder: File, name: String): File {
        val first = File(folder, name)
        if (!first.exists()) return first
        val base = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "").let { if (it.isEmpty() || it == name) "" else ".$it" }
        var n = 2
        while (true) {
            val next = File(folder, "$base ($n)$extension")
            if (!next.exists()) return next
            n++
        }
    }
}
