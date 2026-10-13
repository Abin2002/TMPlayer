package com.tmplayer.desktop.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.tmplayer.data.MediaItem
import com.tmplayer.player.AbLoop
import com.tmplayer.ui.components.CoverArt
import com.tmplayer.ui.player.LoaderContent
import com.tmplayer.ui.player.LoaderWords
import com.tmplayer.ui.player.PlayerLoader
import com.tmplayer.ui.theme.TmMaterialTheme
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image as SkImage
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The player chrome of the cinematic refresh (WP6) drawn: the bar over a dark frame and over a
 * white one (the scrims' worst case), with an A-B repeat marked so the amber marks and the chip
 * stay in the picture, and the loader on a video with no picture, which now stands on its
 * generated cover. The frames land in build/chrome-shots for a look.
 */
class PlayerChromeShotsTest {

    private val status = PlaybackStatus(
        opened = true, durationMs = 2_634_000, positionMs = 1_260_000, bufferedMs = 1_700_000, playing = true, speed = 1.25f,
    )

    private fun item(id: Long, title: String) = MediaItem(
        chatId = 1, messageId = id, fileId = id.toInt(), title = title, sizeBytes = 1, durationSec = 2634,
        mimeType = "video/x-matroska", thumbnailFileId = 0, miniThumbnail = null, date = 0,
    )

    @Test
    fun barOverDarkAndWhite() {
        shot("bar-dark") { overlay(Color(0xFF203040)) }
        shot("bar-white") { overlay(Color.White) }
    }

    /**
     * Over a white frame the times sit on the bottom scrim, which must be dark enough there for
     * white text: the pixel just above the timebar, at the side margin, is well under mid grey.
     */
    @Test
    fun theBottomScrimHoldsOverWhite() {
        val png = shot("bar-white-probe") { overlay(Color.White) }
        val image = SkImage.makeFromEncoded(png)
        val bitmap = org.jetbrains.skia.Bitmap.makeFromImage(image)
        // Halfway along the bar, in the band of the times: 720 high, the bar about 90 px up.
        val luma = bitmap.getColor(640, 720 - 104).let { (it shr 16 and 0xFF) * 0.3 + (it shr 8 and 0xFF) * 0.59 + (it and 0xFF) * 0.11 }
        assertTrue("scrim too light over white: $luma", luma < 110)
    }

    @Test
    fun loaderOnItsCover() {
        val words = LoaderWords(title = "Harbour Notes", line = "Lecture 7  ·  47m")
        val cover = CoverArt.of(42L, "Harbour.Notes.Lecture.07.mp4")
        for ((name, w, h, d) in listOf(Quad("cover-desktop", 1280, 720, 1f), Quad("cover-phone", 1080, 2400, 2.625f))) {
            val png = ImageComposeScene(w, h, Density(d)) {
                TmMaterialTheme(dark = true) {
                    PlayerLoader(LoaderContent(words, cover = cover), progress = 0.3f, status = "Starting", motion = false)
                }
            }.use { scene ->
                scene.render(0)
                scene.render(1_000_000_000).encodeToData(EncodedImageFormat.PNG)!!.bytes
            }
            assertTrue(png.size > 5_000)
            File(OUT).apply { mkdirs() }.resolve("loader-$name.png").writeBytes(png)
        }
    }

    private data class Quad(val name: String, val w: Int, val h: Int, val d: Float)

    @Composable
    private fun overlay(frame: Color) {
        Box(Modifier.fillMaxSize().background(frame)) {
            PlayerOverlay(
                visible = true,
                status = status,
                title = "Night Train",
                subtitle = "S01E04  ·  The Lighthouse  ·  Film Club",
                episodes = Episodes(previous = item(3, "Three"), next = item(5, "Five")),
                tracks = emptyList(),
                downloaded = null,
                showRemaining = false,
                fullscreen = false,
                menu = null,
                ignoreClicks = false,
                miniPlayerAvailable = true,
                alwaysOnTopAvailable = true,
                fromTelegram = true,
                loop = AbLoop(startMs = 900_000, endMs = 1_500_000),
                onHoverControls = {},
                onBack = {},
                onTogglePlay = {},
                onSeekBy = {},
                onSeekTo = {},
                onEpisode = {},
                onToggleRemaining = {},
                onVolume = {},
                onToggleMute = {},
                onToggleFullscreen = {},
                onMiniPlayer = {},
                onOpenMenu = {},
                onCloseMenu = {},
                onMenuAction = {},
                onEpisodes = {},
            )
        }
    }

    private fun shot(name: String, content: @Composable () -> Unit): ByteArray =
        ImageComposeScene(1280, 720, Density(1f)) {
            PlayerTheme { content() }
        }.use { scene ->
            scene.render(0)
            Thread.sleep(300)
            scene.render(500_000_000)
            Thread.sleep(200)
            val png = scene.render(1_000_000_000).encodeToData(EncodedImageFormat.PNG)!!.bytes
            assertTrue(png.size > 1000)
            File(OUT).apply { mkdirs() }.resolve("player-$name.png").writeBytes(png)
            png
        }

    private companion object {
        const val OUT = "build/chrome-shots"
    }
}
