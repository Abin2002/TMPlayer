package com.tmplayer.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TileTitleTest {

    private fun video(fileName: String, title: String = fileName, caption: String = "") = MediaItem(
        chatId = 7,
        messageId = 1,
        fileId = 0,
        title = title,
        sizeBytes = 1,
        durationSec = 60,
        mimeType = "video/mp4",
        thumbnailFileId = 0,
        miniThumbnail = null,
        date = 0,
        fileName = fileName,
        caption = caption,
    )

    @Test
    fun `a release name loses its tags and keeps its year`() {
        assertEquals("Gladiator II (2024)", SeriesShelf.tileTitle(video("Gladiator.II.2024.1080p.WEB-DL.x265.mkv")))
        assertEquals("Night Train (2019)", SeriesShelf.tileTitle(video("Night Train (2019) 1080p x265.mkv")))
    }

    @Test
    fun `an episode keeps its code`() {
        assertEquals("Harbour Notes S01E03", SeriesShelf.tileTitle(video("Harbour.Notes.S01E03.720p.mkv")))
        // The code from the caption, where the file name has none.
        assertEquals(
            "Harbour Notes S02E04",
            SeriesShelf.tileTitle(video("harbour_notes_720p.mkv", caption = "Harbour Notes S02E04")),
        )
    }

    @Test
    fun `a plain name is only tidied, never cut`() {
        assertEquals("Lecture 07 - Limits", SeriesShelf.tileTitle(video("Lecture_07_-_Limits.mp4")))
        assertEquals("Learn Tamil Fast", SeriesShelf.tileTitle(video("Learn.Tamil.Fast.mp4")))
        assertEquals("Coast walk, day 2", SeriesShelf.tileTitle(video("Coast walk, day 2.mp4")))
    }

    @Test
    fun `a title taken from the caption is left as written`() {
        val camera = video("VID_20241013_120000.mp4", title = "Sunset at the pier, 2019")
        assertEquals("Sunset at the pier, 2019", SeriesShelf.tileTitle(camera))
    }

    @Test
    fun `a saved record reads as cleanly as the video it was saved from`() {
        val later = WatchLaterRecord(
            chatId = 7,
            messageId = 9,
            fileId = 3,
            title = "Gladiator.II.2024.1080p.WEB-DL.x265.mkv",
            fileName = "Gladiator.II.2024.1080p.WEB-DL.x265.mkv",
            chatTitle = "Films",
            sizeBytes = 1,
            durationSec = 60,
            mimeType = "video/mp4",
            addedAt = 0,
        )
        assertEquals("Gladiator II (2024)", SeriesShelf.tileTitle(later.toMediaItem()))
        val resume = ResumeRecord(
            chatId = 7,
            messageId = 9,
            fileId = 3,
            title = "Harbour.Notes.S01E03.720p.mkv",
            chatTitle = "Shows",
            sizeBytes = 1,
            durationSec = 60,
            positionMs = 1_000,
            durationMs = 60_000,
            updatedAt = 0,
        )
        assertEquals("Harbour Notes S01E03", SeriesShelf.tileTitle(resume.toMediaItem()))
    }

    @Test
    fun `films are posters and everything else stays wide`() {
        assertEquals(true, SeriesShelf.isFilm(video("Gladiator.II.2024.1080p.WEB-DL.x265.mkv")))
        assertEquals("Gladiator II", SeriesShelf.posterTitle(video("Gladiator.II.2024.1080p.WEB-DL.x265.mkv")))
        assertEquals(false, SeriesShelf.isFilm(video("Harbour.Notes.S01E03.720p.mkv")))
        assertEquals(false, SeriesShelf.isFilm(video("Lecture_07_-_Limits.mp4")))
        assertEquals(false, SeriesShelf.isFilm(video("VID_20241013_120000.mp4", title = "Sunset at the pier, 2019")))
    }

    @Test
    fun `nothing readable keeps the title`() {
        assertEquals("@@@", SeriesShelf.tileTitle(video("@@@", title = "@@@")))
    }
}
