package com.tmplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The seek matrix from docs/design/2026-08-07-v1-design.md §6, expressed against the arithmetic that decides whether a
 * read can be served or the download has to be moved.
 */
class DownloadWindowTest {

    private val size = 1_000_000L

    @Test
    fun `reads inside the downloaded window return what is left of it`() {
        val available = DownloadWindow.availableAt(
            position = 500,
            size = size,
            downloadOffset = 0,
            downloadedPrefixSize = 2_000,
            completed = false,
        )
        assertEquals(1_500, available)
    }

    @Test
    fun `a byte before the window is not readable`() {
        val available = DownloadWindow.availableAt(
            position = 100,
            size = size,
            downloadOffset = 5_000,
            downloadedPrefixSize = 2_000,
            completed = false,
        )
        assertEquals(0, available)
    }

    @Test
    fun `a byte past the window is not readable`() {
        val available = DownloadWindow.availableAt(
            position = 9_000,
            size = size,
            downloadOffset = 0,
            downloadedPrefixSize = 8_000,
            completed = false,
        )
        assertEquals(0, available)
    }

    @Test
    fun `the byte exactly at the window edge is not readable yet`() {
        val available = DownloadWindow.availableAt(
            position = 8_000,
            size = size,
            downloadOffset = 0,
            downloadedPrefixSize = 8_000,
            completed = false,
        )
        assertEquals(0, available)
    }

    @Test
    fun `a fully downloaded file reads to the end from anywhere`() {
        val available = DownloadWindow.availableAt(
            position = 999_000,
            size = size,
            downloadOffset = 0,
            downloadedPrefixSize = 0,
            completed = true,
        )
        assertEquals(1_000, available)
    }

    @Test
    fun `reading past the end of a finished file yields nothing rather than a negative count`() {
        val available = DownloadWindow.availableAt(
            position = size + 10,
            size = size,
            downloadOffset = 0,
            downloadedPrefixSize = 0,
            completed = true,
        )
        assertEquals(0, available)
    }

    // --- Seek case 1: backwards into an area TDLib has moved past ---
    @Test
    fun `seeking backwards restarts the download`() {
        assertTrue(
            DownloadWindow.needsRestart(
                position = 1_000,
                downloadOffset = 500_000,
                downloadedPrefixSize = 10_000,
                active = true,
                completed = false,
            ),
        )
    }

    // --- Seek case 2: a long jump forward the running download will never reach in time ---
    @Test
    fun `seeking far forward restarts the download`() {
        assertTrue(
            DownloadWindow.needsRestart(
                position = 500_000_000,
                downloadOffset = 0,
                downloadedPrefixSize = 1_000,
                active = true,
                completed = false,
            ),
        )
    }

    // --- Seek case 3: a small nudge forward is cheaper to wait out than to restart ---
    @Test
    fun `seeking just ahead of the buffer lets the running download catch up`() {
        assertFalse(
            DownloadWindow.needsRestart(
                position = 1_000_000 + 1_000,
                downloadOffset = 0,
                downloadedPrefixSize = 1_000_000,
                active = true,
                completed = false,
            ),
        )
    }

    // --- Seek case 4: normal sequential playback inside the window ---
    @Test
    fun `reading inside the window never restarts`() {
        assertFalse(
            DownloadWindow.needsRestart(
                position = 400_000,
                downloadOffset = 0,
                downloadedPrefixSize = 500_000,
                active = true,
                completed = false,
            ),
        )
    }

    // --- Seek case 5: TDLib gave up while we sat at the edge of the window ---
    @Test
    fun `a stalled download at the buffer edge is restarted`() {
        assertTrue(
            DownloadWindow.needsRestart(
                position = 500_000,
                downloadOffset = 0,
                downloadedPrefixSize = 500_000,
                active = false,
                completed = false,
            ),
        )
    }

    // --- Seek case 6: everything is already on disk, so any position is fine ---
    @Test
    fun `a completed file never restarts`() {
        assertFalse(
            DownloadWindow.needsRestart(
                position = 0,
                downloadOffset = 900_000,
                downloadedPrefixSize = 0,
                active = false,
                completed = true,
            ),
        )
    }

    /**
     * The sequence that took playback down on a real film: seek forward, watch, seek back.
     *
     * TDLib had moved its window to the forward position, so the far end of that window was a
     * long way past the byte the player now wanted. Judging the read on that end alone said
     * "half a megabyte available" for bytes that had been freed, and the partial file keeps its
     * length, so the read succeeded and returned a hole. The extractor died on the zeroes.
     * Only the near end of the window rules that out.
     */
    @Test
    fun `a byte behind a window that has moved forward is not readable`() {
        val available = DownloadWindow.availableAt(
            position = 120_000,
            size = size,
            downloadOffset = 600_000,
            downloadedPrefixSize = 500_000,
            completed = false,
        )
        assertEquals(0, available)
    }

    @Test
    fun `a negative position reads nothing`() {
        assertEquals(
            0,
            DownloadWindow.availableAt(
                position = -1,
                size = size,
                downloadOffset = 0,
                downloadedPrefixSize = 5_000,
                completed = false,
            ),
        )
    }

    // --- Low storage mode: the rolling window, the same seek matrix with a budget on disk ---

    private val mb = 1024L * 1024
    private val film = 4_000L * 1024 * 1024
    private val w = LowStorageWindow.WINDOW_BYTES
    private fun r(start: Long, end: Long) = LowStorageWindow.Range(start, end)

    @Test
    fun `the first request asks for one window, not the whole film`() {
        val request = LowStorageWindow.plan(emptyList(), 0, film, w)
        assertEquals(w, request.limit)
        assertFalse(request.deleteFirst)
        assertEquals(listOf(r(0, w)), request.ranges)
    }

    @Test
    fun `a window that reaches the end of the film asks for the rest, and no more`() {
        val request = LowStorageWindow.plan(emptyList(), film - 50 * mb, film, w)
        // Never 0: TDLib reads that as no limit and, past the end, fetches the film from the start.
        assertEquals(50 * mb, request.limit)
        assertEquals(listOf(r(film - 50 * mb, film)), request.ranges)
    }

    @Test
    fun `reading in the middle of the window does not roll`() {
        assertFalse(LowStorageWindow.shouldRoll(100 * mb, listOf(r(0, w)), film))
    }

    @Test
    fun `reading near the end of the window rolls`() {
        assertTrue(LowStorageWindow.shouldRoll(w - 1 * mb, listOf(r(0, w)), film))
        assertTrue(LowStorageWindow.shouldRoll(w - LowStorageWindow.ROLL_MARGIN_BYTES, listOf(r(0, w)), film))
        assertFalse(LowStorageWindow.shouldRoll(w - LowStorageWindow.ROLL_MARGIN_BYTES - 1, listOf(r(0, w)), film))
    }

    @Test
    fun `the last window of the film never rolls`() {
        assertFalse(LowStorageWindow.shouldRoll(film - 1, listOf(r(film - w, film)), film))
    }

    @Test
    fun `a read outside every range is a seek, which the seek path restarts`() {
        assertFalse(LowStorageWindow.shouldRoll(900 * mb, listOf(r(0, w)), film))
    }

    @Test
    fun `a roll deletes the file and restarts a little behind the read position`() {
        val position = w - 3 * mb
        val from = LowStorageWindow.rollStart(position)
        assertTrue(from <= position - LowStorageWindow.BACK_BYTES)
        assertTrue(position - from < LowStorageWindow.BACK_BYTES + LowStorageWindow.ALIGN_BYTES)
        assertEquals(0, from % LowStorageWindow.ALIGN_BYTES)
        val request = LowStorageWindow.plan(listOf(r(0, w)), from, film, w, roll = true)
        assertTrue(request.deleteFirst)
        assertEquals(listOf(r(from, from + w)), request.ranges)
        assertTrue(LowStorageWindow.bytes(request.ranges) <= w)
    }

    @Test
    fun `after a roll the read position is within the wait that does not restart`() {
        // The roll lands behind the read position; a restart there would undo it.
        val position = 777 * mb + 12_345
        val from = LowStorageWindow.rollStart(position)
        assertFalse(
            DownloadWindow.needsRestart(
                position = position,
                downloadOffset = from,
                downloadedPrefixSize = 0,
                active = true,
                completed = false,
            ),
        )
    }

    @Test
    fun `a roll at the start of the file does not go below zero`() {
        assertEquals(0, LowStorageWindow.rollStart(1 * mb))
    }

    @Test
    fun `a far seek forward deletes first`() {
        val request = LowStorageWindow.plan(listOf(r(0, w)), 2_000 * mb, film, w)
        assertTrue(request.deleteFirst)
        assertEquals(listOf(r(2_000 * mb, 2_000 * mb + w)), request.ranges)
    }

    @Test
    fun `seeking back past the window deletes and fetches again`() {
        val request = LowStorageWindow.plan(listOf(r(1_000 * mb, 1_000 * mb + w)), 10 * mb, film, w)
        assertTrue(request.deleteFirst)
        assertEquals(listOf(r(10 * mb, 10 * mb + w)), request.ranges)
    }

    @Test
    fun `a short seek back keeps what is on disk when it stays inside the budget`() {
        // Ten megabytes back from a window that started at 300 MB: the two overlap, so the disk
        // holds one window and a little, inside the slack.
        val request = LowStorageWindow.plan(listOf(r(300 * mb, 300 * mb + w)), 290 * mb, film, w)
        assertFalse(request.deleteFirst)
        assertEquals(listOf(r(290 * mb, 300 * mb + w)), request.ranges)
    }

    @Test
    fun `the Matroska index at the far end fits in the slack beside the first window`() {
        val tail = film - 3 * mb
        val request = LowStorageWindow.plan(listOf(r(0, w)), tail, film, w)
        assertFalse(request.deleteFirst)
        assertEquals(3 * mb, request.limit)
        assertEquals(w + 3 * mb, LowStorageWindow.bytes(request.ranges))
    }

    @Test
    fun `a whole film played through never holds more than a window and its slack`() {
        var ranges = LowStorageWindow.plan(emptyList(), 0, film, w).ranges
        var rolls = 0
        var position = 0L
        while (position < film) {
            if (LowStorageWindow.shouldRoll(position, ranges, film)) {
                val request = LowStorageWindow.plan(ranges, LowStorageWindow.rollStart(position), film, w, roll = true)
                assertTrue(request.deleteFirst)
                ranges = request.ranges
                rolls++
            }
            assertTrue(LowStorageWindow.bytes(ranges) <= w + LowStorageWindow.SLACK_BYTES)
            position += 512 * 1024
        }
        // Each roll moves on by the window less its margin and the back buffer.
        assertTrue(rolls in 19..22)
    }

    @Test
    fun `a seek back inside what is on disk keeps it, after the index at the far end was read`() {
        // Seen on the TV emulator with an MP4 whose index sits at the end: the head window and the
        // index are on disk, and a seek to 135 MB, inside the head, used to delete them both and
        // fetch the same bytes again.
        val held = listOf(r(0, w), r(film - 10 * mb, film))
        val request = LowStorageWindow.plan(held, 135 * mb, film, w)
        assertFalse(request.deleteFirst)
        // Grown only as far as the budget allows, and the reading position stays readable.
        assertTrue(LowStorageWindow.bytes(request.ranges) <= w + LowStorageWindow.SLACK_BYTES)
        assertEquals(w + LowStorageWindow.SLACK_BYTES - 10 * mb, request.limit + 135 * mb)
        // And once reading nears that end, the window rolls on in the ordinary way.
        assertTrue(LowStorageWindow.shouldRoll(135 * mb + request.limit - 1 * mb, request.ranges, film))
    }

    @Test
    fun `a seek inside what is on disk with no room left to grow still deletes`() {
        // Right at the end of a full window there is nothing to read before a roll would be due.
        val held = listOf(r(0, w + LowStorageWindow.SLACK_BYTES))
        val request = LowStorageWindow.plan(held, w + LowStorageWindow.SLACK_BYTES - 2 * mb, film, w)
        assertTrue(request.deleteFirst)
    }

    @Test
    fun `the window shrinks on a nearly full disk but never below the minimum`() {
        val headroom = 400 * mb
        assertEquals(w, LowStorageWindow.windowBytes(5_000 * mb, headroom))
        assertEquals(98 * mb, LowStorageWindow.windowBytes(500 * mb, headroom))
        assertEquals(LowStorageWindow.MIN_WINDOW_BYTES, LowStorageWindow.windowBytes(100 * mb, headroom))
    }

    @Test
    fun `the room a stream needs is the window and its back buffer, or the rest when that is less`() {
        assertEquals(w + LowStorageWindow.BACK_BYTES, LowStorageWindow.roomNeeded(film, w))
        assertEquals(30 * mb, LowStorageWindow.roomNeeded(30 * mb, w))
    }

    @Test
    fun `the back buffer and its alignment stay inside the seek slack`() {
        assertTrue(LowStorageWindow.BACK_BYTES + LowStorageWindow.ALIGN_BYTES <= DownloadWindow.FORWARD_SLACK_BYTES)
    }
}
