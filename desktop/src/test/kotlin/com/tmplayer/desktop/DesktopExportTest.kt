package com.tmplayer.desktop

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class DesktopExportTest {

    @Test
    fun `a copy never replaces a file already in the folder`() {
        val folder = Files.createTempDirectory("export").toFile()
        try {
            assertEquals("Film.mkv", DesktopExport.freeName(folder, "Film.mkv").name)
            folder.resolve("Film.mkv").writeText("x")
            assertEquals("Film (2).mkv", DesktopExport.freeName(folder, "Film.mkv").name)
            folder.resolve("Film (2).mkv").writeText("x")
            assertEquals("Film (3).mkv", DesktopExport.freeName(folder, "Film.mkv").name)
            folder.resolve("noext").writeText("x")
            assertEquals("noext (2)", DesktopExport.freeName(folder, "noext").name)
        } finally {
            folder.deleteRecursively()
        }
    }
}
