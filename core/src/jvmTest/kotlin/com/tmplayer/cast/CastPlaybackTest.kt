package com.tmplayer.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sender following the receiver: its state, its clock, a press shown at once, and the receiver's track ids. */
class CastPlaybackTest {

    private fun media(state: ReceiverState, positionMs: Long, atMs: Long, reason: IdleReason? = null, rate: Double = 1.0) =
        ReceiverMedia(state, reason, positionMs, atMs, rate)

    @Test
    fun `the clock runs on only while the tv plays`() {
        val m = CastStateMapper()
        val playing = m.map(media(ReceiverState.Playing, 60_000, atMs = 1_000), loading = false, nowMs = 4_000, targetMs = 0)
        assertEquals(CastPhase.Playing, playing.phase)
        assertEquals(63_000, playing.positionMs)
        assertTrue(playing.showsPause)
        // The stall seen on the stick: the TV paused at 69.7 s; the phone must not count on to 2:00.
        val paused = m.map(media(ReceiverState.Paused, 69_700, atMs = 10_000), loading = false, nowMs = 60_000, targetMs = 0)
        assertEquals(CastPhase.Paused, paused.phase)
        assertEquals(69_700, paused.positionMs)
        assertFalse(paused.showsPause)
    }

    @Test
    fun `buffering keeps the clock still and the pause button up`() {
        val m = CastStateMapper()
        m.map(media(ReceiverState.Playing, 1_000, 0), false, 0, 0)
        val b = m.map(media(ReceiverState.Buffering, 5_000, atMs = 0), loading = false, nowMs = 9_000, targetMs = 0)
        assertEquals(CastPhase.Buffering, b.phase)
        assertEquals(5_000, b.positionMs)
        assertTrue(b.showsPause)
        assertTrue(b.busy)
    }

    @Test
    fun `playback rate scales the run on, and the duration caps it`() {
        val m = CastStateMapper()
        assertEquals(13_000, m.map(media(ReceiverState.Playing, 10_000, 0, rate = 1.5), false, 2_000, 0).positionMs)
        assertEquals(11_000, m.map(media(ReceiverState.Playing, 10_000, 0), false, 5_000, 0, durationMs = 11_000).positionMs)
    }

    @Test
    fun `idle is told apart by its reason`() {
        val m = CastStateMapper()
        m.loaded(nowMs = 0, autoplay = true)
        assertEquals(CastPhase.Ended, m.map(media(ReceiverState.Idle, 0, 0, IdleReason.Finished), false, 20_000, 0).phase)
        assertEquals(CastPhase.Failed, m.map(media(ReceiverState.Idle, 0, 0, IdleReason.Error), false, 20_000, 0).phase)
        assertEquals(CastPhase.Stopped, m.map(media(ReceiverState.Idle, 0, 0, IdleReason.Cancelled), false, 20_000, 0).phase)
        assertEquals(CastPhase.Stopped, m.map(media(ReceiverState.Idle, 0, 0, IdleReason.Interrupted), false, 20_000, 0).phase)
        // Just after a LOAD, an idle with no reason is the load getting going.
        m.loaded(nowMs = 30_000, autoplay = true)
        assertEquals(CastPhase.Loading, m.map(media(ReceiverState.Idle, 0, 30_100), false, 31_000, 0).phase)
    }

    @Test
    fun `a load on its way shows where it starts`() {
        val m = CastStateMapper()
        val v = m.map(media(ReceiverState.Playing, 1_000, 0), loading = true, nowMs = 5_000, targetMs = 42_000)
        assertEquals(CastPhase.Loading, v.phase)
        assertEquals(42_000, v.positionMs)
        assertEquals(CastPhase.Starting, m.map(null, false, 0, 7_000).phase)
    }

    @Test
    fun `a press is shown at once and the receiver has the last word`() {
        val m = CastStateMapper()
        val playing = media(ReceiverState.Playing, 10_000, atMs = 0)
        m.requested(play = false, nowMs = 1_000)
        val pressed = m.map(playing, false, nowMs = 1_100, targetMs = 0)
        assertEquals(CastPhase.Paused, pressed.phase)
        assertFalse(pressed.showsPause)
        // The clock stops with the press.
        assertEquals(10_000, pressed.positionMs)
        // The TV never paused (another phone pressed play): after a while its state stands.
        val later = m.map(playing, false, nowMs = 1_000 + CastStateMapper.INTENT_MS + 1, targetMs = 0)
        assertEquals(CastPhase.Playing, later.phase)
        // Play pressed on a paused TV reads as getting going until it plays.
        m.requested(play = true, nowMs = 10_000)
        val resuming = m.map(media(ReceiverState.Paused, 20_000, 9_000), false, 10_100, 0)
        assertEquals(CastPhase.Buffering, resuming.phase)
        assertTrue(resuming.showsPause)
    }

    @Test
    fun `receiver states and reasons are read from the protocol's words`() {
        assertEquals(ReceiverState.Buffering, ReceiverMedia.state("BUFFERING"))
        assertEquals(ReceiverState.Idle, ReceiverMedia.state(null))
        assertEquals(IdleReason.Cancelled, ReceiverMedia.idleReason("CANCELLED"))
        assertEquals(null, ReceiverMedia.idleReason(""))
    }

    // ---- track ids ---------------------------------------------------------------------------

    private val renditions = listOf(
        HlsRendition(HlsRendition.Kind.Audio, "a2.m3u8", "Japanese", "ja", 2, null, true),
        HlsRendition(HlsRendition.Kind.Audio, "a4.m3u8", "English", "en", 4, null, false),
        HlsRendition(HlsRendition.Kind.Text, "s3.m3u8", "English", "en", 3, null, false),
        HlsRendition(HlsRendition.Kind.Text, "f1000.m3u8", "Mine", null, null, 1000, false),
    )

    @Test
    fun `renditions are found by their playlist where the receiver keeps it`() {
        val remote = listOf(
            RemoteTrack(1, RemoteTrack.Type.Video, null, null, "http://x/t/hls1/v.m3u8"),
            RemoteTrack(2, RemoteTrack.Type.Audio, "", "en", "http://x/t/hls1/a4.m3u8"),
            RemoteTrack(3, RemoteTrack.Type.Audio, "", "ja", "http://x/t/hls1/a2.m3u8"),
            RemoteTrack(4, RemoteTrack.Type.Text, null, "en", "http://x/t/hls1/s3.m3u8"),
            RemoteTrack(5, RemoteTrack.Type.Text, null, null, "http://x/t/hls1/f1000.m3u8"),
        )
        val map = HlsTrackMatch.match(renditions, remote)
        assertEquals(listOf(3L, 2L, 4L, 5L), renditions.map { map[it] })
        assertEquals(listOf(1L, 2L, 5L), HlsTrackMatch.activeIds(map, remote, current = listOf(1, 3), audio = renditions[1], text = renditions[3]))
        // Subtitles off: the picture and the sound only.
        assertEquals(listOf(1L, 3L), HlsTrackMatch.activeIds(map, remote, current = listOf(1, 3, 4), audio = renditions[0], text = null))
    }

    @Test
    fun `without urls the names, the languages and the order find them`() {
        val remote = listOf(
            RemoteTrack(10, RemoteTrack.Type.Audio, "English", "en-US", null),
            RemoteTrack(11, RemoteTrack.Type.Audio, "Japanese", "ja", null),
            RemoteTrack(20, RemoteTrack.Type.Text, null, "eng", null),
            RemoteTrack(21, RemoteTrack.Type.Text, null, null, null),
        )
        val map = HlsTrackMatch.match(renditions, remote)
        assertEquals(listOf(11L, 10L, 20L, 21L), renditions.map { map[it] })
    }

    @Test
    fun `a soundtrack not found keeps the one on`() {
        val remote = listOf(RemoteTrack(7, RemoteTrack.Type.Audio, "x", null, null), RemoteTrack(8, RemoteTrack.Type.Audio, "y", null, null), RemoteTrack(9, RemoteTrack.Type.Audio, "z", null, null))
        val map = HlsTrackMatch.match(renditions.take(2), remote)
        assertTrue(map.isEmpty())
        assertEquals(listOf(8L), HlsTrackMatch.activeIds(map, remote, current = listOf(8), audio = renditions[1], text = null))
    }
}
