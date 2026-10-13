package com.tmplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [HomeFeatured.pick]: what Home's carousel may show from the starred chats, and what it never does. */
class HomeFeaturedTest {

    private var nextId = 1L

    private fun chat(id: Long) = ChatSummary(id, "Chat $id", null, 0, ChatKind.Channel)

    private fun video(fileName: String, chatId: Long, date: Int) = MediaItem(
        chatId = chatId,
        messageId = nextId++,
        fileId = nextId.toInt(),
        title = fileName,
        sizeBytes = 1_000_000_000,
        durationSec = 3_000,
        mimeType = "video/x-matroska",
        thumbnailFileId = 1,
        miniThumbnail = null,
        date = date,
        fileName = fileName,
    )

    private fun record(item: MediaItem, fraction: Float) = ResumeRecord(
        chatId = item.chatId,
        messageId = item.messageId,
        fileId = item.fileId,
        title = item.fileName,
        chatTitle = "Chat ${item.chatId}",
        sizeBytes = item.sizeBytes,
        durationSec = item.durationSec,
        positionMs = (3_000_000 * fraction).toLong(),
        durationMs = 3_000_000,
        updatedAt = 1,
    )

    private fun names(picks: List<FeaturedPick>) = picks.map { it.item.fileName }

    private fun key(item: MediaItem) = SettingsStore.progressKey(item.chatId, item.messageId)

    @Test
    fun onlyFilmsAndShowsRoundRobinNewestFirst() {
        val a1 = video("Night.Train.2019.1080p.WEB.mkv", 1, 50)
        val aClip = video("coast-walk-day-2-1080p.mp4", 1, 60)
        val aLecture = video("Calculus Lecture 07 - Limits.mp4", 1, 55)
        val a2 = video("Paper.Moon.2021.720p.mkv", 1, 40)
        val b1 = video("Harbour.Lights.S01E03.1080p.WEB.mkv", 2, 30)
        val b2 = video("Harbour.Lights.S01E02.1080p.WEB.mkv", 2, 20)
        val b3 = video("Big.Buck.Bunny.2008.1080p.BluRay.x264.mkv", 2, 10)
        val picks = HomeFeatured.pick(
            listOf(chat(1), chat(2)),
            mapOf(1L to listOf(aClip, aLecture, a1, a2), 2L to listOf(b3, b2, b1)),
            continueWatching = emptyList(),
            watched = emptySet(),
        )
        // Chat 1's newest film, chat 2's newest (the show, at its newest episode), then the next of each.
        assertEquals(listOf(a1.fileName, b1.fileName, a2.fileName, b3.fileName), names(picks))
        assertEquals("Chat 1", picks.first().chatTitle)
    }

    @Test
    fun theSameFilmOrShowIsOneCardAcrossChats() {
        val film1080 = video("Night.Train.2019.1080p.WEB.mkv", 1, 50)
        val film720 = video("Night.Train.2019.720p.WEB.mkv", 1, 49)
        val filmElsewhere = video("Night Train 2019 2160p HEVC.mkv", 2, 60)
        val show1 = video("Harbour.Lights.S01E03.1080p.WEB.mkv", 1, 40)
        val show2 = video("Harbour.Lights.S01E02.1080p.WEB.mkv", 1, 39)
        val showElsewhere = video("Harbour Lights S01E04 720p.mkv", 2, 55)
        val picks = HomeFeatured.pick(
            listOf(chat(1), chat(2)),
            mapOf(1L to listOf(film1080, film720, show1, show2), 2L to listOf(filmElsewhere, showElsewhere)),
            continueWatching = emptyList(),
            watched = emptySet(),
        )
        assertEquals(2, picks.size)
        assertEquals(1, picks.count { SeriesShelf.isFilm(it.item) })
        assertEquals(1, picks.count { SeriesShelf.episodeOf(it.item).isEpisode })
    }

    @Test
    fun aStartedShowOffersItsNextEpisodeWithItsPlace() {
        val e1 = video("Harbour.Lights.S01E01.1080p.WEB.mkv", 1, 10)
        val e2 = video("Harbour.Lights.S01E02.1080p.WEB.mkv", 1, 20)
        val e3 = video("Harbour.Lights.S01E03.1080p.WEB.mkv", 1, 30)
        val loaded = mapOf(1L to listOf(e3, e2, e1))
        val next = HomeFeatured.pick(listOf(chat(1)), loaded, emptyList(), watched = setOf(key(e1)))
        assertEquals(listOf(e2.fileName), names(next))
        assertNull(next.single().record)

        val partway = record(e2, 0.4f)
        val resumed = HomeFeatured.pick(listOf(chat(1)), loaded, listOf(partway), watched = setOf(key(e1)))
        assertEquals(listOf(e2.fileName), names(resumed))
        assertNotNull(resumed.single().record)

        val done = HomeFeatured.pick(listOf(chat(1)), loaded, emptyList(), watched = setOf(key(e1), key(e2), key(e3)))
        assertTrue(done.isEmpty())
    }

    @Test
    fun watchedAndFinishedFilmsAreLeftOut() {
        val seen = video("Night.Train.2019.1080p.WEB.mkv", 1, 50)
        val ended = video("Paper.Moon.2021.720p.mkv", 1, 40)
        val fresh = video("Big.Buck.Bunny.2008.1080p.BluRay.x264.mkv", 1, 30)
        val picks = HomeFeatured.pick(
            listOf(chat(1)),
            mapOf(1L to listOf(seen, ended, fresh)),
            continueWatching = listOf(record(ended, 0.99f)),
            watched = setOf(key(seen)),
        )
        assertEquals(listOf(fresh.fileName), names(picks))
    }

    @Test
    fun noStarredChatsOrNothingFitMeansNoPicks() {
        assertTrue(HomeFeatured.pick(emptyList(), emptyMap(), emptyList(), emptySet()).isEmpty())
        val clips = listOf(video("birthday-highlights-1080p.mp4", 1, 3), video("Lecture 4.mp4", 1, 2))
        assertTrue(HomeFeatured.pick(listOf(chat(1)), mapOf(1L to clips), emptyList(), emptySet()).isEmpty())
    }

    @Test
    fun atMostTheLimit() {
        val films = (1..8).map { video("Film Number ${'A' + it} 20${10 + it}.1080p.mkv", 1, it) } +
            (1..8).map { video("Other Film ${'A' + it} 19${70 + it}.1080p.mkv", 2, it) }
        val picks = HomeFeatured.pick(listOf(chat(1), chat(2)), films.groupBy { it.chatId }, emptyList(), emptySet(), limit = 5)
        assertEquals(5, picks.size)
        assertEquals(listOf(1L, 2L, 1L, 2L, 1L), picks.map { it.item.chatId })
    }
}
