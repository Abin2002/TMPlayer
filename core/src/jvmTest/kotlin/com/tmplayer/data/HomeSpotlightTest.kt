package com.tmplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [HomeRows.spotlight]: what Home may lead with, and what it never does. */
class HomeSpotlightTest {

    private var nextId = 1L

    private fun record(fileName: String, updatedAt: Long, chatId: Long = 1) = ResumeRecord(
        chatId = chatId,
        messageId = nextId++,
        fileId = 1,
        title = fileName,
        chatTitle = "Chat $chatId",
        sizeBytes = 1,
        durationSec = 3_000,
        positionMs = 600_000,
        durationMs = 3_000_000,
        updatedAt = updatedAt,
    )

    private fun titles(list: List<ResumeRecord>) = list.map { it.title }

    @Test
    fun newestFirstAndOnlyFilmsAndShows() {
        val clip = record("coast-walk-day-2-1080p.mp4", updatedAt = 50)
        val film = record("Big.Buck.Bunny.2008.1080p.BluRay.x264.mkv", updatedAt = 40)
        val lecture = record("Calculus Lecture 07 - Limits.mp4", updatedAt = 45)
        val episode = record("Harbour.Lights.S01E03.1080p.WEB.mkv", updatedAt = 60)
        assertEquals(
            listOf(episode.title, film.title),
            titles(HomeRows.spotlight(listOf(clip, film, lecture, episode))),
        )
    }

    @Test
    fun aShowIsOfferedOnceByItsNewestRecord() {
        val older = record("Harbour.Lights.S01E02.1080p.WEB.mkv", updatedAt = 10)
        val newer = record("Harbour.Lights.S01E03.1080p.WEB.mkv", updatedAt = 20)
        val elsewhere = record("Harbour.Lights.S01E01.1080p.WEB.mkv", updatedAt = 5, chatId = 2)
        assertEquals(
            listOf(newer.title, elsewhere.title),
            titles(HomeRows.spotlight(listOf(older, newer, elsewhere))),
        )
    }

    @Test
    fun nothingUnderWayMeansNoSpotlight() {
        assertTrue(HomeRows.spotlight(emptyList()).isEmpty())
        val clips = listOf(record("birthday-highlights-1080p.mp4", 3), record("Lecture 4.mp4", 2))
        assertTrue(HomeRows.spotlight(clips).isEmpty())
    }

    @Test
    fun atMostThreeCandidates() {
        val films = (1..6).map { record("Film Number ${'A' + it} 20${10 + it}.1080p.mkv", updatedAt = it.toLong()) }
        val picked = HomeRows.spotlight(films)
        assertEquals(HomeRows.SPOTLIGHT_CANDIDATES, picked.size)
        assertEquals(listOf(6L, 5L, 4L), picked.map { it.updatedAt })
    }
}
