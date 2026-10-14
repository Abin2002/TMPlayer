package com.tmplayer.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import com.tmplayer.data.DownloadRequest
import com.tmplayer.data.DownloadRunner
import com.tmplayer.data.SettingsStore
import com.tmplayer.data.UpdateScheduler
import com.tmplayer.data.WatchedStore
import com.tmplayer.desktop.DesktopPrefs
import com.tmplayer.desktop.DesktopWatchCache
import com.tmplayer.i18n.Languages
import com.tmplayer.i18n.Translator
import com.tmplayer.online.OnlineMetadata
import com.tmplayer.ui.nav.BackStack
import com.tmplayer.ui.nav.LocalBackStack
import com.tmplayer.ui.onboarding.SetupScreen
import com.tmplayer.ui.onboarding.SetupState
import com.tmplayer.ui.onboarding.SetupStep
import com.tmplayer.ui.theme.TmMaterialTheme
import com.tmplayer.ui.theme.Tone
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The setup after sign in (CP67) and the first-run Home on the desktop: every step light and dark,
 * in Arabic (right to left) and the pseudo-locale, then walked from the keyboard alone. With
 * TMPLAYER_SHOTS=<dir> the frames are written there as PNGs.
 */
@OptIn(ExperimentalTestApi::class)
class SetupRenderTest {

    private val settings = SettingsStore(
        SettingsStore.openDataStore(Files.createTempDirectory("tm-setup-render").resolve(SettingsStore.FILE_NAME).toFile()),
    )
    private val dir = Files.createTempDirectory("tm-setup-render-extras").toFile()
    private val shell = ShellState(
        settings,
        object : DownloadRunner {
            override fun download(request: DownloadRequest) = Unit
            override fun cancel(fileId: Int) = Unit
            override fun pause(fileId: Int) = Unit
        },
        WatchedStore(WatchedStore.openDataStore(Files.createTempDirectory("tm-setup-watched").resolve(WatchedStore.FILE_NAME).toFile())),
        services = {
            DesktopExtras(
                prefs = DesktopPrefs(dir.resolve("desktop.properties")),
                updates = UpdateScheduler(settings.updatePrefs, check = { _, _ -> false }, onSkip = {}),
                watchCache = DesktopWatchCache(settings, { dir.resolve("cache") }),
            )
        },
    )

    private val all = SetupStep.entries.toList()

    @After
    fun reset() {
        OnlineMetadata.current = null
        Translator.use(Languages.ENGLISH)
    }

    @Test
    fun everyStepLightAndDark() {
        MetaFixture.install("on")
        for (step in all) {
            for (dark in listOf(true, false)) {
                save("setup-${step.name.lowercase()}-${if (dark) "dark" else "light"}.png", render(dark) {
                    SetupScreen(settings, remember { SetupState(all, start = step.ordinal) }, onDone = {})
                })
            }
        }
        // A narrow window stacks the words over the answers, as a phone does.
        save("setup-narrow.png", render(true, width = 520, height = 900) {
            SetupScreen(settings, remember { SetupState(all) }, onDone = {})
        })
    }

    @Test
    fun rightToLeftAndPseudo() {
        MetaFixture.install("on")
        Translator.use("ar")
        for (step in all) {
            save("setup-ar-${step.name.lowercase()}.png", render(true, rtl = true) {
                SetupScreen(settings, remember { SetupState(all, start = step.ordinal) }, onDone = {})
            })
        }
        Translator.use(Languages.PSEUDO)
        for (step in all) {
            save("setup-xa-${step.name.lowercase()}.png", render(true) {
                SetupScreen(settings, remember { SetupState(all, start = step.ordinal) }, onDone = {})
            })
        }
    }

    /**
     * From the keyboard: each step opens on the answer set now (the recommended one on a fresh
     * sign in), Enter picks the focused one and moves on, Skip changes nothing, Back walks back,
     * and Skip all ends the setup.
     */
    @Test
    fun walkedFromTheKeyboard() = runComposeUiTest {
        val online = MetaFixture.install("on")
        val back = BackStack()
        val setup = SetupState(all)
        var done = false
        setContent {
            CompositionLocalProvider(LocalBackStack provides back) {
                TmMaterialTheme(dark = true) {
                    Box(Modifier.size(1280.dp, 800.dp)) { SetupScreen(settings, setup, onDone = { done = true }) }
                }
            }
        }
        fun card(title: String) = hasText(title) and hasClickAction()
        fun press(key: Key) = onNode(isFocused()).performKeyInput { pressKey(key) }

        waitForIdle()
        onNode(card("Show everything")).assertIsFocused()
        // Right to the other answer, Enter picks it: the setting changes and the step moves on.
        press(Key.DirectionRight)
        onNode(card("Only my folders")).assertIsFocused()
        press(Key.Enter)
        waitForIdle()
        assertEquals(SetupStep.Posters, setup.step)
        waitUntil(timeoutMillis = WRITE_MS) { runBlocking { settings.hideDefaultGroups.first() } }
        onNode(card("Posters on")).assertIsFocused()

        // Back returns to the library step, on the answer now set.
        back.handleBack()
        waitForIdle()
        assertEquals(SetupStep.Library, setup.step)
        onNode(card("Only my folders")).assertIsFocused()
        press(Key.DirectionLeft)
        press(Key.Enter)
        waitForIdle()
        waitUntil(timeoutMillis = WRITE_MS) { !runBlocking { settings.hideDefaultGroups.first() } }

        // Skip leaves posters as they are.
        onNode(hasText("Skip") and hasClickAction()).performClick()
        waitForIdle()
        assertEquals(SetupStep.Storage, setup.step)
        assertTrue(online.settings.value.enabled)
        onNode(card("Keep what plays")).assertIsFocused()
        press(Key.DirectionRight)
        press(Key.Enter)
        waitForIdle()
        assertTrue("finished after the last step", done)
        waitUntil(timeoutMillis = WRITE_MS) { runBlocking { settings.lowStorageMode.first() } }
    }

    @Test
    fun skipAllEndsItAndChangesNothing() = runComposeUiTest {
        val online = MetaFixture.install("on")
        var done = false
        setContent {
            TmMaterialTheme(dark = false) {
                Box(Modifier.size(1280.dp, 800.dp)) { SetupScreen(settings, remember { SetupState(all) }, onDone = { done = true }) }
            }
        }
        onNode(hasText("Skip all") and hasClickAction()).performClick()
        waitForIdle()
        assertTrue(done)
        assertFalse(runBlocking { settings.hideDefaultGroups.first() })
        assertFalse(runBlocking { settings.lowStorageMode.first() })
        assertTrue(online.settings.value.enabled)
    }

    @Test
    fun postersOffFromTheSetupSticks() = runComposeUiTest {
        val online = MetaFixture.install("on")
        setContent {
            TmMaterialTheme(dark = true) {
                Box(Modifier.size(1280.dp, 800.dp)) {
                    SetupScreen(settings, remember { SetupState(listOf(SetupStep.Posters, SetupStep.Storage)) }, onDone = {})
                }
            }
        }
        onNode(hasText("Posters off") and hasClickAction()).performClick()
        waitForIdle()
        assertFalse(online.settings.value.enabled)
        assertTrue("chosen, not a stale default", online.settings.value.offByChoice)
    }

    @Test
    fun firstRunHome() {
        shell.go(Destination.Home)
        for (dark in listOf(true, false)) {
            save("home-first-run-${if (dark) "dark" else "light"}.png", render(dark) {
                HomeRowsView(shell, emptyList(), emptyList(), emptyMap(), onRowShown = {}, onArtWanted = {}, onRefresh = {})
            })
        }
    }

    private fun render(
        dark: Boolean,
        width: Int = WIDTH,
        height: Int = HEIGHT,
        rtl: Boolean = false,
        page: @Composable () -> Unit,
    ): ByteArray =
        ImageComposeScene(width, height, Density(1f)) {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                TmMaterialTheme(dark = dark) {
                    Surface(Modifier.fillMaxSize(), color = Tone.background) { page() }
                }
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

    private companion object {
        const val WIDTH = 1280
        const val HEIGHT = 800

        /** How long a choice has to reach the settings file, which is written off the UI thread. */
        const val WRITE_MS = 3_000L
    }
}
