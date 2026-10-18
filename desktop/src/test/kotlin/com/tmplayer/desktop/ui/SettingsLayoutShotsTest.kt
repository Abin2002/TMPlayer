package com.tmplayer.desktop.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.tmplayer.data.DownloadRequest
import com.tmplayer.data.DownloadRunner
import com.tmplayer.data.SettingsStore
import com.tmplayer.data.ThemeChoice
import com.tmplayer.data.UpdateScheduler
import com.tmplayer.data.WatchedStore
import com.tmplayer.desktop.DesktopPrefs
import com.tmplayer.desktop.DesktopWatchCache
import com.tmplayer.desktop.os.SystemAppearance
import com.tmplayer.ui.theme.TmMaterialTheme
import com.tmplayer.ui.theme.Tone
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files

/**
 * Settings drawn at the window sizes a desktop meets (the minimum window, a 1366x768 laptop, a
 * 1440p and a 4K monitor, at 100 and 200 per cent scaling), every page in light and dark: the
 * two pane layout from the minimum window up, one pane below it (a large interface size), and the
 * rows held to a reading width on a wide monitor. With TMPLAYER_SHOTS=<dir> the frames are written
 * there as PNGs.
 */
class SettingsLayoutShotsTest {

    private val settings = SettingsStore(
        SettingsStore.openDataStore(Files.createTempDirectory("tm-settings-layout").resolve(SettingsStore.FILE_NAME).toFile()),
    )
    private val dir = Files.createTempDirectory("tm-settings-layout-extras").toFile()
    private val shell = ShellState(
        settings,
        object : DownloadRunner {
            override fun download(request: DownloadRequest) = Unit
            override fun cancel(fileId: Int) = Unit
            override fun pause(fileId: Int) = Unit
        },
        WatchedStore(WatchedStore.openDataStore(Files.createTempDirectory("tm-settings-layout-watched").resolve(WatchedStore.FILE_NAME).toFile())),
        services = {
            DesktopExtras(
                prefs = DesktopPrefs(dir.resolve("desktop.properties")),
                updates = UpdateScheduler(settings.updatePrefs, check = { _, _ -> false }, onSkip = {}),
                watchCache = DesktopWatchCache(settings, { dir.resolve("cache") }),
            )
        },
    )

    /** Window size in physical pixels, and the OS scaling. */
    private data class Size(val name: String, val width: Int, val height: Int, val scale: Float)

    private val sizes = listOf(
        Size("min", 960, 600, 1f),
        Size("laptop", 1366, 768, 1f),
        Size("1440p", 2560, 1440, 1f),
        Size("4k-200", 3840, 2160, 2f),
        // 960 x 600 dp at 150 per cent leaves 640 dp: the one pane layout.
        Size("narrow", 960, 600, 1.5f),
    )

    @Test
    fun everyPageAtEverySize() {
        for (size in sizes) {
            for (dark in listOf(true, false)) {
                val theme = if (dark) "dark" else "light"
                save("settings-${size.name}-$theme-list.png", render(size, dark) { SettingsPage(shell, "2.0.0") })
                for (category in SettingsCategory.entries) {
                    save(
                        "settings-${size.name}-$theme-${category.name.lowercase()}.png",
                        render(size, dark) { SettingsPage(shell, "2.0.0", initialCategory = category) },
                    )
                }
            }
        }
    }

    /**
     * The Pure black switch under the theme: there under Dark, drawn on true black once it is on,
     * and folded away under Light.
     */
    @Test
    fun pureBlackFollowsTheTheme() {
        val laptop = sizes.first { it.name == "laptop" }
        val page: @Composable () -> Unit = { SettingsPage(shell, "2.0.0", initialCategory = SettingsCategory.Appearance) }
        runBlocking { settings.setThemeChoice(ThemeChoice.Dark); settings.setPureBlack(false) }
        save("settings-pure-black-dark-off.png", render(laptop, dark = true, page = page))
        runBlocking { settings.setPureBlack(true) }
        save("settings-pure-black-dark-on.png", render(laptop, dark = true, black = true, page = page))
        runBlocking { settings.setThemeChoice(ThemeChoice.Light) }
        save("settings-pure-black-light.png", render(laptop, dark = false, page = page))
        runBlocking { settings.setThemeChoice(ThemeChoice.System); settings.setPureBlack(false) }
    }

    @Test
    fun systemAppearanceWords() {
        assertEquals(true, SystemAppearance.portalScheme(org.freedesktop.dbus.types.UInt32(1)))
        assertEquals(false, SystemAppearance.portalScheme(2))
        assertNull(SystemAppearance.portalScheme(0))
        assertEquals(true, SystemAppearance.gsettingsScheme("'prefer-dark'\n", null))
        assertEquals(false, SystemAppearance.gsettingsScheme("'prefer-light'", "'Adwaita-dark'"))
        assertEquals(true, SystemAppearance.gsettingsScheme("'default'", "'Adwaita-dark'"))
        assertEquals(false, SystemAppearance.gsettingsScheme("'default'", "'Adwaita'"))
        assertNull(SystemAppearance.gsettingsScheme(null, null))
    }

    private fun render(size: Size, dark: Boolean, black: Boolean = false, page: @Composable () -> Unit): ByteArray =
        ImageComposeScene(size.width, size.height, Density(size.scale)) {
            TmMaterialTheme(dark = dark, black = black) {
                Surface(Modifier.fillMaxSize(), color = Tone.background) { page() }
            }
        }.use { scene ->
            scene.render(0)
            Thread.sleep(300)
            scene.render(500_000_000)
            scene.render(1_000_000_000).encodeToData(EncodedImageFormat.PNG)!!.bytes
        }

    private fun save(name: String, png: ByteArray) {
        assertTrue(png.size > 1000)
        val out = System.getenv("TMPLAYER_SHOTS")?.takeIf { it.isNotBlank() } ?: return
        File(out).apply { mkdirs() }.resolve(name).writeBytes(png)
    }
}
