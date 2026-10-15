package com.tmplayer.ui.browse

import com.tmplayer.data.WatchPoint
import org.junit.Assert.assertEquals
import org.junit.Test

/** The show's Play pill names what it starts, so pressing it is never a guess. */
class SeriesPlayLabelTest {

    @Test
    fun `an episode left part way says how much is left`() {
        assertEquals("Resume S01E04 · 12 min left", seriesPlayLabel("S01E04", WatchPoint(positionMs = 18 * 60_000L, durationMs = 30 * 60_000L), anyWatched = true))
        // Part of a minute left still counts as one.
        assertEquals("Resume S01E04 · 1 min left", seriesPlayLabel("S01E04", WatchPoint(positionMs = 29 * 60_000L + 20_000, durationMs = 30 * 60_000L), anyWatched = false))
    }

    @Test
    fun `without a known length it is a bare resume`() {
        assertEquals("Resume S01E04", seriesPlayLabel("S01E04", WatchPoint(positionMs = 60_000L, durationMs = 0L), anyWatched = false))
    }

    @Test
    fun `the next episode after watched ones continues, a fresh show plays`() {
        assertEquals("Continue S01E05", seriesPlayLabel("S01E05", null, anyWatched = true))
        assertEquals("Play S01E01", seriesPlayLabel("S01E01", null, anyWatched = false))
    }
}
