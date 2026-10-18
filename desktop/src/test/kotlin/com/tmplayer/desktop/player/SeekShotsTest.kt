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
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The seek additions drawn: a chaptered video's bar with the "Back to" chip lifted over it, the
 * chip alone with the controls down, "Go to a time" with a refused entry under it, and the error
 * sheet with its logs buttons. The frames land in build/seek-shots for a look.
 */
class SeekShotsTest {

    private val status = PlaybackStatus(
        opened = true, durationMs = 2_634_000, positionMs = 1_260_000, bufferedMs = 1_700_000, playing = true,
        chapters = listOf(
            Chapter(0, "Opening"),
            Chapter(95_000, "Cold open"),
            Chapter(610_000, "The Station"),
            Chapter(1_180_000, "Night Train"),
            Chapter(2_480_000, "Ending"),
        ),
    )

    private fun item(id: Long, title: String) = MediaItem(
        chatId = 1, messageId = id, fileId = id.toInt(), title = title, sizeBytes = 1, durationSec = 2634,
        mimeType = "video/x-matroska", thumbnailFileId = 0, miniThumbnail = null, date = 0,
    )

    @Test
    fun chipOverTheChapteredBar() {
        shot("chip-lifted") {
            Box(Modifier.fillMaxSize().background(Color(0xFF203040))) {
                PlayerOverlay(
                    visible = true,
                    status = status,
                    title = "Night Train",
                    subtitle = "S01E04  ·  The Lighthouse",
                    episodes = Episodes(next = item(5, "Five")),
                    tracks = emptyList(),
                    downloaded = null,
                    showRemaining = false,
                    fullscreen = false,
                    menu = null,
                    ignoreClicks = false,
                    miniPlayerAvailable = true,
                    alwaysOnTopAvailable = true,
                    fromTelegram = true,
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
                )
                UndoSeekChip(backToMs = 312_000, lifted = true, onUndo = {})
            }
        }
    }

    @Test
    fun chipLow() {
        shot("chip-low") {
            Box(Modifier.fillMaxSize().background(Color(0xFF3A3F47))) {
                UndoSeekChip(backToMs = 3_912_000, lifted = false, onUndo = {})
            }
        }
    }

    @Test
    fun goToTime() {
        shot("go-to-time") {
            Box(Modifier.fillMaxSize().background(Color(0xFF203040))) {
                GoToTimeDialog(durationMs = 2_634_000, positionMs = 1_260_000, onGo = {}, onDismiss = {})
            }
        }
    }

    @Test
    fun errorSheetOffersTheLogs() {
        shot("error-logs") {
            Box(Modifier.fillMaxSize().background(Color(0xFF203040))) {
                StatusSheet(
                    phase = Phase.Failed("This video wouldn't play. Try a different copy of it."),
                    title = "Night Train",
                    subtitle = "S01E04",
                    onRetry = {},
                    onBack = {},
                    onWatchAgain = {},
                    onPlayNext = {},
                    onCancelNext = {},
                )
            }
        }
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        val png = ImageComposeScene(1280, 720, Density(1f)) {
            PlayerTheme { content() }
        }.use { scene ->
            scene.render(0)
            Thread.sleep(300)
            scene.render(500_000_000)
            Thread.sleep(200)
            scene.render(1_000_000_000).encodeToData(EncodedImageFormat.PNG)!!.bytes
        }
        assertTrue(png.size > 1000)
        File("build/seek-shots").apply { mkdirs() }.resolve("$name.png").writeBytes(png)
    }
}
