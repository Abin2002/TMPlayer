package com.tmplayer.data

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class GlobalSearchTest {

    private fun video(
        chatId: Long,
        messageId: Long,
        fileName: String,
        date: Int = messageId.toInt(),
        size: Long = 900 * SizeFilter.MB,
        duration: Int = 6_000,
        caption: String = "",
        onDevice: Boolean = false,
    ) = MediaItem(
        chatId = chatId,
        messageId = messageId,
        fileId = messageId.toInt(),
        title = fileName,
        sizeBytes = size,
        durationSec = duration,
        mimeType = "video/x-matroska",
        thumbnailFileId = 0,
        miniThumbnail = null,
        date = date,
        fileName = fileName,
        caption = caption,
        onDevice = onDevice,
    )

    private fun chat(id: Long, title: String) = ChatSummary(id, title, null, 0, ChatKind.Channel)

    private fun q(text: String) = GlobalSearch.parse(text, maxYear = 2026)

    // ---- reading the query ----------------------------------------------------------------

    @Test
    fun `cleans spacing, pasted links and a leading handle`() {
        assertEquals("night train", GlobalSearch.clean("  night \t  train "))
        assertEquals("night train", GlobalSearch.clean("@moviechannel night train"))
        assertEquals("moviechannel", GlobalSearch.clean("@moviechannel"))
        assertEquals("night train", GlobalSearch.clean("https://t.me/somechannel/42 night train"))
        assertEquals("", GlobalSearch.clean("t.me/somechannel"))
        assertEquals(GlobalSearch.MAX_QUERY, GlobalSearch.clean("a".repeat(200)).length)
    }

    @Test
    fun `one letter is not searched, one ideograph is`() {
        assertFalse(GlobalSearch.searchable("a"))
        assertTrue(GlobalSearch.searchable("ab"))
        assertTrue(GlobalSearch.searchable("海"))
        assertTrue(q("x").isEmpty)
    }

    @Test
    fun `a year or a quality alone is a filter, not a title`() {
        val year = q("2024")
        assertEquals(2024, year.year)
        assertTrue(year.filterOnly)
        assertEquals("2024", year.server)

        val quality = q("1080p")
        assertEquals(SearchQuality.FullHd, quality.quality)
        assertTrue(quality.filterOnly)

        val mixed = q("dune 1080p")
        assertEquals("dune", mixed.server)
        assertEquals(SearchQuality.FullHd, mixed.quality)
        assertFalse(mixed.filterOnly)

        // A year among other words stays part of the title: "1917" and "2049" are in names.
        val titled = q("blade runner 2049")
        assertEquals("blade runner 2049", titled.server)
        assertEquals(null, titled.year)
    }

    @Test
    fun `underscored and dotted spellings for a query of several words`() {
        assertEquals(listOf("harbour_notes", "harbour.notes"), GlobalSearch.variants("Harbour Notes"))
        assertTrue(GlobalSearch.variants("harbour").isEmpty())
    }

    // ---- local matching -------------------------------------------------------------------

    @Test
    fun `local fallback finds a file Telegram's word search misses`() {
        val known = listOf(
            video(1, 1, "Harbour_Notes_2024_1080p.mkv"),
            video(1, 2, "Ledger.of.Small.Hours.2019.720p.mkv"),
            video(2, 3, "IMG_2231.mp4", caption = "Harbour notes, the director's talk"),
        )
        val found = GlobalSearch.localVideos(known, q("harbour")).map { it.messageId }
        assertEquals(listOf(1L, 3L), found)
        // Case, accents and a typo are forgiven.
        assertEquals(listOf(2L), GlobalSearch.localVideos(known, q("LEDGER of smal hours")).map { it.messageId })
        // An online title the file name does not carry.
        val named = GlobalSearch.localVideos(known, q("quiet harbour")) { if (it.messageId == 2L) listOf("The Quiet Harbour") else emptyList() }
        assertTrue(2L in named.map { it.messageId })
    }

    @Test
    fun `local fallback keeps to the kids lock and lists each video once`() {
        val known = listOf(video(1, 1, "Harbour.Notes.2024.mkv"), video(1, 1, "Harbour.Notes.2024.mkv"), video(5, 9, "Harbour.Notes.2024.mkv"))
        assertEquals(listOf(1L), GlobalSearch.localVideos(known, q("harbour"), allowed = setOf(1L)).map { it.chatId })
    }

    @Test
    fun `a year query keeps only that year`() {
        val known = listOf(video(1, 1, "Night.Train.2024.1080p.mkv"), video(1, 2, "Night.Train.2019.1080p.mkv"))
        val groups = GlobalSearch.merge(emptyList(), known, q("2024"))
        assertEquals(listOf(1L), groups.flatMap { it.copies }.map { it.messageId })
    }

    @Test
    fun `local and server results merge by message id`() {
        val local = video(1, 1, "Night.Train.2019.1080p.mkv")
        val first = GlobalSearch.merge(emptyList(), listOf(local), q("night train"))
        val again = GlobalSearch.merge(first, listOf(local, video(2, 7, "Night.Train.2019.720p.mkv")), q("night train"))
        assertEquals(1, again.size)
        assertEquals(2, again.single().copies.size)
    }

    // ---- grouping and ranking -------------------------------------------------------------

    @Test
    fun `copies of a film across chats are one card, best copy first`() {
        val groups = GlobalSearch.merge(
            emptyList(),
            listOf(
                video(1, 10, "Night.Train.2019.720p.mkv", date = 30),
                video(2, 11, "Night.Train.2019.2160p.mkv", date = 10),
                video(3, 12, "Night.Train.2019.1080p.mkv", date = 20, onDevice = true),
            ),
            q("night train"),
        )
        val card = groups.single()
        assertEquals(listOf(12L, 11L, 10L), card.copies.map { it.messageId })
        assertEquals(listOf(3L, 2L, 1L), card.chatIds)
        assertEquals("4K", card.bestQuality)
        assertEquals(3, card.asItem().versions.size)
    }

    @Test
    fun `a trailer never folds into the film and ranks after it`() {
        val groups = GlobalSearch.merge(
            emptyList(),
            listOf(
                video(1, 1, "Night.Train.2019.Trailer.1080p.mp4", date = 50, duration = 140),
                video(1, 2, "Night.Train.2019.1080p.mkv", date = 10),
                video(2, 3, "Night.Train.2019.mp4", date = 40, duration = 300, size = 40 * SizeFilter.MB),
            ),
            q("night train"),
        )
        assertEquals(listOf(listOf(2L)), groups.filterNot { it.extra }.map { g -> g.copies.map { it.messageId } })
        assertTrue(groups.first().copies.single().messageId == 2L)
        assertTrue(groups.drop(1).all { it.extra })
    }

    @Test
    fun `exact title beats prefix beats every word beats a caption mention`() {
        val groups = GlobalSearch.merge(
            emptyList(),
            listOf(
                video(1, 1, "IMG_0001.mp4", caption = "we talked about the night train", date = 90),
                video(1, 2, "The.Last.Night.Train.Home.2021.mkv", date = 80),
                video(1, 3, "Night.Train.Express.2020.mkv", date = 70),
                video(1, 4, "Night.Train.2019.mkv", date = 60),
            ),
            q("night train"),
        )
        assertEquals(listOf(4L, 3L, 2L, 1L), groups.map { it.lead.messageId })
    }

    @Test
    fun `paging never moves a card already on screen`() {
        val first = GlobalSearch.merge(emptyList(), listOf(video(1, 1, "Night.Train.Express.2020.mkv")), q("night train"))
        val next = GlobalSearch.merge(first, listOf(video(1, 2, "Night.Train.2019.mkv")), q("night train"))
        assertEquals(listOf(1L, 2L), next.map { it.lead.messageId })
    }

    @Test
    fun `other sorts reorder what came back`() {
        val groups = GlobalSearch.merge(
            emptyList(),
            listOf(
                video(1, 1, "Alpha.Story.2019.720p.mkv", date = 10, size = 3 * SizeFilter.GB),
                video(1, 2, "Beta.Story.2020.2160p.mkv", date = 5, size = 1 * SizeFilter.GB),
                video(1, 3, "Gamma.Story.2021.1080p.mkv", date = 30, size = 2 * SizeFilter.GB),
            ),
            q("story"),
        )
        assertEquals(listOf(3L, 1L, 2L), GlobalSearch.order(groups, q("story"), SearchSort.Newest).map { it.lead.messageId })
        assertEquals(listOf(2L, 3L, 1L), GlobalSearch.order(groups, q("story"), SearchSort.BestQuality).map { it.lead.messageId })
        assertEquals(listOf(1L, 3L, 2L), GlobalSearch.order(groups, q("story"), SearchSort.Largest).map { it.lead.messageId })
    }

    @Test
    fun `episodes of one show in one chat are one card`() {
        val groups = GlobalSearch.merge(
            emptyList(),
            listOf(
                video(1, 1, "Harbour.Notes.S01E01.1080p.mkv"),
                video(1, 2, "Harbour.Notes.S01E02.1080p.mkv"),
                video(2, 3, "Harbour.Notes.S01E01.720p.mkv"),
            ),
            q("harbour notes"),
        )
        val show = groups.first { it.chatIds == listOf(1L) }
        assertTrue(show.isShow)
        assertEquals(2, show.episodeCount)
        assertFalse(groups.first { it.chatIds == listOf(2L) }.isShow)
    }

    // ---- filters --------------------------------------------------------------------------

    @Test
    fun `quality and small clip filters keep the copies that pass, in place`() {
        val groups = GlobalSearch.merge(
            emptyList(),
            listOf(
                video(1, 1, "Night.Train.2019.720p.mkv", size = 30 * SizeFilter.MB),
                video(2, 2, "Night.Train.2019.1080p.mkv"),
                video(1, 3, "Ledger.of.Small.Hours.2019.720p.mkv"),
            ),
            q("2019"),
        )
        val hd = GlobalSearch.filter(groups, SearchFilters(quality = SearchQuality.FullHd))
        assertEquals(listOf(2L), hd.flatMap { it.copies }.map { it.messageId })
        val big = GlobalSearch.filter(groups, SearchFilters(hideSmall = true))
        assertEquals(setOf(2L, 3L), big.flatMap { it.copies }.map { it.messageId }.toSet())
        // A quality named in the query filters too.
        assertEquals(listOf(2L), GlobalSearch.filter(groups, SearchFilters(), q("1080p")).flatMap { it.copies }.map { it.messageId })
    }

    @Test
    fun `says which filter hides everything`() {
        val groups = GlobalSearch.merge(emptyList(), listOf(video(1, 1, "Night.Train.2019.720p.mkv")), q("night train"))
        assertEquals(
            listOf(HidingFilter.Quality),
            GlobalSearch.hiddenBy(groups, SearchFilters(quality = SearchQuality.UHD), q("night train")),
        )
        assertEquals(
            listOf(HidingFilter.ChatType),
            GlobalSearch.hiddenBy(emptyList(), SearchFilters(chatType = SearchChatType.Groups), q("night train")),
        )
    }

    @Test
    fun `chats match fuzzily, starred first on a tie`() {
        val chats = listOf(chat(1, "Film Club"), chat(2, "Film Club"), chat(3, "Cooking"))
        assertEquals(listOf(2L, 1L), GlobalSearch.localChats(chats, "film club", favourites = setOf(2L)).map { it.id })
        assertEquals(listOf(3L), GlobalSearch.localChats(chats, "cookng").map { it.id })
    }

    @Test
    fun `the first answer asks again while it is thin`() {
        assertTrue(GlobalSearch.wantsMore(groups = 3, pagesAsked = 1, done = false))
        assertFalse(GlobalSearch.wantsMore(groups = 3, pagesAsked = 3, done = false))
        assertFalse(GlobalSearch.wantsMore(groups = 12, pagesAsked = 1, done = false))
        assertFalse(GlobalSearch.wantsMore(groups = 3, pagesAsked = 1, done = true))
    }
}
