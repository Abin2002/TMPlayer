package com.tmplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonDownloadTest {

    private var nextId = 1L

    private fun video(fileName: String, size: Long = 1, canBeSaved: Boolean = true) = MediaItem(
        chatId = 7,
        messageId = nextId++,
        fileId = nextId.toInt(),
        title = fileName,
        sizeBytes = size,
        durationSec = 60,
        mimeType = "video/mp4",
        thumbnailFileId = 0,
        miniThumbnail = null,
        date = nextId.toInt(),
        fileName = fileName,
        canBeSaved = canBeSaved,
    )

    private fun showOf(items: List<MediaItem>) =
        SeriesShelf.arrange(items).filterIsInstance<ShelfEntry.Show>().single().series

    private fun String.code() = Regex("""S\d\dE\d\d""").find(this)!!.value

    /** Every episode of season one in a small copy and a large one, and one episode of season two. */
    private fun twoSizes(): Series = showOf(
        (1..4).flatMap { n ->
            listOf(
                video("Harbour.Notes.S01E0$n.mkv", size = 350_000_000),
                video("Harbour.Notes.S01E0$n.mkv", size = 1_800_000_000),
            )
        } + video("Harbour.Notes.S02E01.mkv"),
    )

    private fun copies(
        series: Series,
        which: SeasonDownload.Which = SeasonDownload.Which.Season,
        season: Int = 1,
        finished: (MediaItem) -> Boolean = { false },
        position: (MediaItem) -> Float = { 0f },
        held: (MediaItem) -> Boolean = { false },
    ) = SeasonDownload.copies(series, series.seasons.single { it.number == season }, which, finished, position, held)

    @Test
    fun `the season is every episode of the season shown, one copy each, in order`() {
        val show = twoSizes()
        val chosen = copies(show)
        assertEquals(listOf("S01E01", "S01E02", "S01E03", "S01E04"), chosen.map { it.fileName.code() })
        assertEquals(chosen.size, chosen.map { it.fileName }.distinct().size)
    }

    @Test
    fun `each copy is the size the viewer has been watching`() {
        val show = twoSizes()
        val small = show.episodes.first().copies.single { it.sizeBytes == 350_000_000L }
        val chosen = copies(show, finished = { it.id == small.id })
        assertTrue(chosen.all { it.sizeBytes == 350_000_000L })
        val large = show.episodes.first().copies.single { it.sizeBytes == 1_800_000_000L }
        assertTrue(copies(show, finished = { it.id == large.id }).all { it.sizeBytes == 1_800_000_000L })
    }

    @Test
    fun `the copy partway through is the one kept`() {
        val show = twoSizes()
        val started = show.episodes[2].copies.single { it.sizeBytes == 1_800_000_000L }
        val chosen = copies(show, position = { if (it.id == started.id) 0.4f else 0f })
        assertEquals(started.id, chosen.single { it.fileName.code() == "S01E03" }.id)
    }

    @Test
    fun `unwatched leaves out every episode with a watched copy`() {
        val show = twoSizes()
        val watched = setOf("S01E01", "S01E03")
        val chosen = copies(show, SeasonDownload.Which.Unwatched, finished = { it.fileName.code() in watched && it.sizeBytes == 350_000_000L })
        assertEquals(listOf("S01E02", "S01E04"), chosen.map { it.fileName.code() })
        assertTrue(chosen.all { it.sizeBytes == 350_000_000L })
    }

    @Test
    fun `an episode already kept or queued in any size is skipped`() {
        val show = twoSizes()
        val kept = show.episodes[1].copies.single { it.sizeBytes == 1_800_000_000L }
        val chosen = copies(show, held = { it.id == kept.id })
        assertEquals(listOf("S01E01", "S01E03", "S01E04"), chosen.map { it.fileName.code() })
    }

    @Test
    fun `a copy that may not be saved gives way to one that may, and an episode with none is left out`() {
        val show = showOf(
            listOf(
                video("Quiet.Hours.S01E01.720p.mkv", canBeSaved = false),
                video("Quiet.Hours.S01E01.1080p.mkv"),
                video("Quiet.Hours.S01E02.720p.mkv", canBeSaved = false),
            ),
        )
        val chosen = copies(show)
        assertEquals(listOf("Quiet.Hours.S01E01.1080p.mkv"), chosen.map { it.fileName })
    }

    @Test
    fun `another season is asked for by its own number`() {
        assertEquals(listOf("S02E01"), copies(twoSizes(), season = 2).map { it.fileName.code() })
    }

    @Test
    fun `unwatched is offered only when it differs from the whole season`() {
        val season = twoSizes().seasons.first()
        assertFalse(SeasonDownload.unwatchedDiffers(season) { false })
        assertFalse(SeasonDownload.unwatchedDiffers(season) { true })
        assertTrue(SeasonDownload.unwatchedDiffers(season) { it.fileName.code() == "S01E02" })
    }

    @Test
    fun `a season in a chat that restricts saving offers nothing`() {
        val show = showOf((1..2).map { video("Closed.Room.S01E0$it.mkv", canBeSaved = false) })
        assertFalse(SeasonDownload.anySavable(show.seasons.single()))
        assertTrue(SeasonDownload.anySavable(twoSizes().seasons.first()))
    }
}
