package com.tmplayer.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class GalleryExportTest {

    @Test
    fun `the gallery shows what the chat called the video`() {
        assertEquals("Harbour Notes 2021.mkv", GalleryExport.nameFor("Harbour Notes 2021.mkv", File("/x/1234.mkv")))
        assertEquals("Harbour Notes.mkv", GalleryExport.nameFor("Harbour Notes", File("/x/1234.mkv")))
        assertEquals("1234.mp4", GalleryExport.nameFor("  ", File("/x/1234.mp4")))
        assertEquals("a b c.mp4", GalleryExport.nameFor("a/b:c", File("/x/1.mp4")))
    }
}
