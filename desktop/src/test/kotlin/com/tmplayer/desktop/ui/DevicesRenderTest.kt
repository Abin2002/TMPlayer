package com.tmplayer.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import com.tmplayer.data.AccountSession
import com.tmplayer.data.MediaItem
import com.tmplayer.data.TelegramQuality
import com.tmplayer.ui.browse.TelegramQualityLine
import com.tmplayer.ui.settings.DevicesScreen
import com.tmplayer.ui.theme.TmMaterialTheme
import com.tmplayer.ui.theme.TmSchemes
import com.tmplayer.ui.theme.Tone
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The Devices page (CP83) and the detail page's line for Telegram's own qualities (CP80), in both
 * themes: each frame has something on it besides the ground. With TMPLAYER_SHOTS=<dir> the frames
 * are written there.
 */
class DevicesRenderTest {

    @Test
    fun `the devices page draws this device and the others`() {
        for (dark in listOf(true, false)) {
            shot("devices-${theme(dark)}", 1366, 900, dark) { DevicesScreen(onClose = {}, preview = SESSIONS) }
            shot("devices-alone-${theme(dark)}", 1366, 600, dark) { DevicesScreen(onClose = {}, preview = SESSIONS.take(1)) }
        }
    }

    @Test
    fun `the quality line names the upload and telegram's copies`() {
        for (dark in listOf(true, false)) {
            shot("telegram-qualities-${theme(dark)}", 720, 160, dark) {
                Box(Modifier.fillMaxSize().background(Tone.background).padding(24.dp)) { TelegramQualityLine(FILM) }
            }
        }
    }

    private fun shot(name: String, w: Int, h: Int, dark: Boolean, content: @Composable () -> Unit) {
        val png = ImageComposeScene(w, h, Density(1f)) {
            TmMaterialTheme(dark = dark) { content() }
        }.use { scene ->
            scene.render(0)
            scene.render(600_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
        }
        val bitmap = Bitmap.makeFromImage(Image.makeFromEncoded(png))
        val ground = TmSchemes.of(dark).background.toArgb()
        var drawn = 0
        for (y in 0 until bitmap.height step 4) for (x in 0 until bitmap.width step 4) if (bitmap.getColor(x, y) != ground) drawn++
        assertTrue("$name has something on it", drawn > 50)
        val out = System.getenv("TMPLAYER_SHOTS")?.takeIf { it.isNotBlank() } ?: return
        File(out).apply { mkdirs() }.resolve("$name.png").writeBytes(png)
    }

    private fun theme(dark: Boolean) = if (dark) "dark" else "light"

    private companion object {
        val NOW = (System.currentTimeMillis() / 1000).toInt()

        val SESSIONS = listOf(
            AccountSession(
                1, current = true, kind = AccountSession.Kind.Computer, device = "Desktop", platform = "Linux",
                systemVersion = "6.8", appName = "TMPlayer", appVersion = "1.27.0", official = false,
                loginDate = NOW - 86_400 * 40, lastActiveDate = NOW, ip = "203.0.113.5", location = "Lisbon, Portugal",
            ),
            AccountSession(
                2, current = false, kind = AccountSession.Kind.Phone, device = "Pixel 8", platform = "Android",
                systemVersion = "15", appName = "Telegram Android", appVersion = "11.4", official = true,
                loginDate = NOW - 86_400 * 200, lastActiveDate = NOW - 3_600 * 3, ip = "198.51.100.7", location = "Lisbon, Portugal",
            ),
            AccountSession(
                3, current = false, kind = AccountSession.Kind.Browser, device = "Firefox 131", platform = "Windows",
                systemVersion = "11", appName = "Telegram Web A", appVersion = "10.9", official = true,
                loginDate = NOW - 86_400 * 3, lastActiveDate = NOW - 86_400 * 2, ip = "192.0.2.44", location = "Madrid, Spain",
                unconfirmed = true,
            ),
        )

        val FILM = MediaItem(
            chatId = 1, messageId = 2, fileId = 10, title = "Harbour Notes", sizeBytes = 2_100_000_000, durationSec = 6_000,
            mimeType = "video/mp4", thumbnailFileId = 0, miniThumbnail = null, date = 0, fileName = "Harbour.Notes.2021.1080p.mkv",
            width = 1920, height = 1080,
            qualities = listOf(
                TelegramQuality(11, 1280, 720, "h264", 900_000_000),
                TelegramQuality(12, 854, 480, "h264", 420_000_000),
            ),
        )
    }
}
