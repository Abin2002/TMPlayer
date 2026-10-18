package com.tmplayer.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The codec decision table: what plays as it is, what is rewrapped, what is converted. */
class CastFormatsTest {

    private fun video(codec: String, tenBit: Boolean = false) = VideoTrack(1, codec, 1920, 1080, tenBit = tenBit)
    private fun audio(n: Int, codec: String, lang: String? = null, default: Boolean = false) = AudioTrack(n, codec, lang, isDefault = default)

    private fun plan(
        container: Container,
        v: VideoTrack?,
        a: List<AudioTrack>,
        caps: ReceiverCaps = ReceiverCaps.GOOGLE_TV,
        preferred: Int? = null,
        convert: Boolean = false,
        transcode: Boolean = false,
    ) = CastFormats.plan(MediaProbe(container, 0, v, a), caps, preferred, convert, transcode)

    @Test
    fun `mp4 with h264 and aac plays directly`() {
        val p = plan(Container.Mp4, video(Codecs.H264), listOf(audio(2, Codecs.AAC)), ReceiverCaps.LEGACY)
        assertEquals(listOf(CastRoute.Direct("video/mp4")), p.routes)
        assertFalse(p.silent)
    }

    @Test
    fun `webm plays directly`() {
        val p = plan(Container.WebM, video(Codecs.VP9), listOf(audio(2, Codecs.OPUS)))
        assertEquals(CastRoute.Direct("video/webm"), p.routes.first())
    }

    @Test
    fun `matroska that is webm in all but name plays as webm`() {
        val p = plan(Container.Matroska, video(Codecs.VP9), listOf(audio(2, Codecs.OPUS)))
        assertEquals(CastRoute.Direct("video/webm"), p.routes.first())
    }

    @Test
    fun `matroska with h264 and aac is rewrapped on an older chromecast`() {
        val p = plan(Container.Matroska, video(Codecs.H264), listOf(audio(2, Codecs.AAC)), ReceiverCaps.LEGACY)
        assertEquals(listOf(CastRoute.Remux(2)), p.routes)
    }

    @Test
    fun `matroska is rewrapped first and tried as it is after on a google tv`() {
        val p = plan(Container.Matroska, video(Codecs.H264), listOf(audio(2, Codecs.AAC)), ReceiverCaps.GOOGLE_TV)
        assertEquals(listOf(CastRoute.Remux(2), CastRoute.Direct("video/x-matroska")), p.routes)
    }

    @Test
    fun `dolby passes through in the rewrap`() {
        val p = plan(Container.Matroska, video(Codecs.HEVC), listOf(audio(2, Codecs.EAC3)), ReceiverCaps.ULTRA)
        assertEquals(CastRoute.Remux(2), p.routes.first())
    }

    @Test
    fun `dts is converted to aac where the device can`() {
        val p = plan(Container.Matroska, video(Codecs.H264), listOf(audio(2, Codecs.DTS, default = true)), ReceiverCaps.LEGACY, convert = true)
        assertEquals(listOf(CastRoute.ConvertAudio(2)), p.routes)
    }

    @Test
    fun `dts without a converter picks another track that plays`() {
        val p = plan(
            Container.Matroska,
            video(Codecs.H264),
            listOf(audio(2, Codecs.DTS, "en", default = true), audio(3, Codecs.AC3, "en")),
            ReceiverCaps.LEGACY,
        )
        assertEquals(3, p.audioTrack)
        assertEquals(listOf(CastRoute.Remux(3)), p.routes)
    }

    @Test
    fun `the viewer's track is kept and converted rather than swapped`() {
        val p = plan(
            Container.Matroska,
            video(Codecs.H264),
            listOf(audio(2, Codecs.AAC, "en", default = true), audio(3, Codecs.TRUEHD, "ja")),
            ReceiverCaps.LEGACY,
            preferred = 3,
            convert = true,
        )
        assertEquals(3, p.audioTrack)
        assertEquals(listOf(CastRoute.ConvertAudio(3)), p.routes)
    }

    @Test
    fun `dts only with no converter sends the picture alone`() {
        val p = plan(Container.Matroska, video(Codecs.H264), listOf(audio(2, Codecs.DTS)), ReceiverCaps.LEGACY)
        assertEquals(listOf(CastRoute.Remux(null)), p.routes)
        assertTrue(p.silent)
    }

    @Test
    fun `hevc on a first chromecast needs the picture converted`() {
        val refused = plan(Container.Mp4, video(Codecs.HEVC), listOf(audio(2, Codecs.AAC)), ReceiverCaps.LEGACY)
        assertFalse(refused.castable)
        assertEquals(CastRefusal.VideoCodec, refused.refusal)
        val converted = plan(Container.Mp4, video(Codecs.HEVC), listOf(audio(2, Codecs.AAC)), ReceiverCaps.LEGACY, transcode = true)
        assertEquals(listOf(CastRoute.TranscodeVideo(2)), converted.routes)
        assertTrue(CastFormats.needsNote(converted.routes.first()))
    }

    @Test
    fun `hevc on an unnamed chromecast is tried and converted if it fails`() {
        val p = plan(Container.Mp4, video(Codecs.HEVC), listOf(audio(2, Codecs.AAC)), ReceiverCaps.UNKNOWN, transcode = true)
        assertEquals(listOf(CastRoute.Direct("video/mp4"), CastRoute.TranscodeVideo(2)), p.routes)
    }

    @Test
    fun `ten bit hevc needs a receiver that shows ten bit`() {
        val ultra = plan(Container.Mp4, video(Codecs.HEVC, tenBit = true), listOf(audio(2, Codecs.AAC)), ReceiverCaps.ULTRA)
        assertEquals(CastRoute.Direct("video/mp4"), ultra.routes.first())
        val legacy = plan(Container.Mp4, video(Codecs.HEVC, tenBit = true), listOf(audio(2, Codecs.AAC)), ReceiverCaps.LEGACY)
        assertFalse(legacy.castable)
    }

    @Test
    fun `av1 only on the receivers that decode it`() {
        assertEquals(Support.Yes, CastFormats.videoSupport(video(Codecs.AV1), ReceiverCaps.STREAMER))
        assertEquals(Support.No, CastFormats.videoSupport(video(Codecs.AV1), ReceiverCaps.ULTRA))
        assertEquals(Support.Maybe, CastFormats.videoSupport(video(Codecs.AV1), ReceiverCaps.GOOGLE_TV))
    }

    @Test
    fun `mpeg4 part 2 and avi are refused without conversion`() {
        assertEquals(CastRefusal.VideoCodec, plan(Container.Matroska, video(Codecs.MPEG4), listOf(audio(2, Codecs.MP3))).refusal)
        assertEquals(CastRefusal.Container, plan(Container.Avi, null, emptyList()).refusal)
    }

    @Test
    fun `mp4 with a soundtrack the receiver cannot play still sends the picture, said so`() {
        val p = plan(Container.Mp4, video(Codecs.H264), listOf(audio(2, Codecs.DTS)), ReceiverCaps.LEGACY)
        assertEquals(listOf(CastRoute.Direct("video/mp4")), p.routes)
        assertTrue(p.silent)
    }

    @Test
    fun `audio support follows the cast documentation`() {
        for (codec in listOf(Codecs.AAC, Codecs.MP3, Codecs.OPUS, Codecs.VORBIS, Codecs.FLAC)) {
            assertEquals(codec, Support.Yes, CastFormats.audioSupport(codec, ReceiverCaps.LEGACY))
        }
        assertEquals(Support.Yes, CastFormats.audioSupport(Codecs.AC3, ReceiverCaps.LEGACY))
        assertEquals(Support.Yes, CastFormats.audioSupport(Codecs.EAC3, ReceiverCaps.LEGACY))
        for (codec in listOf(Codecs.DTS, Codecs.TRUEHD, Codecs.PCM, Codecs.ALAC)) {
            assertEquals(codec, Support.No, CastFormats.audioSupport(codec, ReceiverCaps.LEGACY))
        }
    }

    @Test
    fun `model names map to what the hardware decodes`() {
        assertEquals(ReceiverCaps.ULTRA, ReceiverCaps.forModel("Chromecast Ultra"))
        assertEquals(ReceiverCaps.STREAMER, ReceiverCaps.forModel("Google TV Streamer"))
        assertEquals(ReceiverCaps.GOOGLE_TV, ReceiverCaps.forModel("BRAVIA 4K VH2"))
        assertEquals(ReceiverCaps.UNKNOWN, ReceiverCaps.forModel("Chromecast"))
        assertEquals(ReceiverCaps.UNKNOWN, ReceiverCaps.forModel(null))
    }
}
