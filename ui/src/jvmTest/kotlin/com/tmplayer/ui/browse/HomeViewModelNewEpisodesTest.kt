package com.tmplayer.ui.browse

import com.tmplayer.data.MediaItem
import com.tmplayer.data.WatchedRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** New episodes: one search per show in progress, kept across visits to Home. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelNewEpisodesTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    /** A chat of its own, so the process-wide search cache never carries over from another test. */
    private val chat = 9_001L

    private fun video(id: Long, name: String, date: Int) = MediaItem(
        chatId = chat, messageId = id, fileId = id.toInt(), title = name, sizeBytes = 600L * 1024 * 1024,
        durationSec = 1_400, mimeType = "video/x-matroska", thumbnailFileId = 0, miniThumbnail = null, date = date,
        fileName = name,
    )

    private val one = video(1, "Harbour.Notes.S01E01.1080p.mkv", date = 100)
    private val two = video(2, "Harbour.Notes.S01E02.1080p.mkv", date = 3_000)

    private val searches = mutableListOf<String>()

    private val source = object : HomeSource {
        override suspend fun chatMedia(chatId: Long) = emptyList<MediaItem>()
        override suspend fun recentMedia() = emptyList<MediaItem>()
        override suspend fun mediaItem(chatId: Long, messageId: Long): MediaItem? = null
        override suspend fun showMedia(chatId: Long, messageId: Long, query: String): List<MediaItem> {
            searches += query
            return listOf(two, one)
        }
    }

    @Test
    fun `a show in progress finds its new episode with one search, and a second visit asks nothing`() = runTest(dispatcher) {
        val watched = listOf(WatchedRecord.of(one, "Chat", watchedAt = 1_000_000L, manual = false))
        val first = HomeViewModel(source, work = dispatcher)
        first.followShows(emptyList(), watched)
        advanceUntilIdle()
        assertEquals(listOf("Harbour Notes"), searches)
        assertEquals(listOf("S01E02"), first.newEpisodes.value.map { it.code })

        val again = HomeViewModel(source, work = dispatcher)
        again.followShows(emptyList(), watched)
        advanceUntilIdle()
        assertEquals(1, searches.size)
        assertEquals(listOf(two.id), again.newEpisodes.value.map { it.item.id })

        // Watching it takes it off the row, without another search.
        again.followShows(emptyList(), watched + WatchedRecord.of(two, "Chat", watchedAt = 4_000_000L, manual = false))
        advanceUntilIdle()
        assertEquals(1, searches.size)
        assertEquals(emptyList<Any>(), again.newEpisodes.value)
    }
}
