package com.tmplayer.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.tmplayer.data.DeviceFit
import com.tmplayer.data.DeviceFitRules
import com.tmplayer.data.DeviceForm
import com.tmplayer.data.SettingsStore
import com.tmplayer.online.OnlineMetadata
import com.tmplayer.ui.onboarding.Setup
import com.tmplayer.ui.onboarding.SetupChoice
import com.tmplayer.ui.onboarding.SetupChoicesList
import com.tmplayer.ui.onboarding.SetupPlace
import com.tmplayer.ui.onboarding.SetupStep
import com.tmplayer.ui.onboarding.applyTo
import com.tmplayer.ui.onboarding.hasPictureSettings
import com.tmplayer.ui.theme.TmMaterialTheme
import com.tmplayer.ui.theme.Tone
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/**
 * Settings' Setup choices (SetupChoicesList) drawn for each form factor: it lists the steps the
 * setup asks there, in the setup's order; an answer changed on it writes what the setup writes;
 * a remote or a keyboard changes an answer with Left, Right and OK; and "Also in" goes to the
 * step's place.
 */
@OptIn(ExperimentalTestApi::class)
class SetupChoicesRenderTest {

    @After
    fun reset() {
        OnlineMetadata.current = null
    }

    @Test
    fun listsTheSetupStepsOnEveryFormFactor() = runComposeUiTest {
        MetaFixture.install("on")
        val settings = store()
        var form by mutableStateOf(DeviceForm.Phone)
        var fit by mutableStateOf(DeviceFit.Fine)
        setContent {
            TmMaterialTheme(dark = true) {
                Surface(Modifier.size(900.dp, 3000.dp), color = Tone.background) {
                    key(form, fit) {
                        SetupChoicesList(
                            settings,
                            place = { null },
                            onRunSetupAgain = {},
                            form = form,
                            fit = fit,
                            modifier = Modifier.wrapContentHeight(),
                        )
                    }
                }
            }
        }
        val fits = listOf(DeviceFit.Fine, DeviceFit(shortOfMemory = false, shortOfStorage = true), DeviceFit(true, true))
        for (f in DeviceForm.entries) {
            for (case in fits) {
                form = f
                fit = case
                waitForIdle()
                val light = runBlocking { settings.lightSettings.first() }
                val recommendations = DeviceFitRules.recommendations(case, light, seekThumbnailsShown = false, pictures = hasPictureSettings)
                val expected = Setup.stepsFor(f, posters = true, recommendations = recommendations)
                waitUntil(timeoutMillis = WAIT_MS) {
                    onAllNodes(hasTestTag("setup-choice-${expected.first().name}")).fetchSemanticsNodes().isNotEmpty()
                }
                val shown = SetupStep.entries
                    .mapNotNull { step ->
                        onAllNodes(hasTestTag("setup-choice-${step.name}")).fetchSemanticsNodes().firstOrNull()
                            ?.let { step to it.positionInRoot.y }
                    }
                    .sortedBy { it.second }
                    .map { it.first }
                assertEquals("$f, $case", expected, shown)
            }
        }
    }

    @Test
    fun anAnswerChangedHereWritesWhatTheSetupWrites() = runComposeUiTest {
        val online = MetaFixture.install("on")
        val settings = store()
        // The setup's own writes, the same answers in the same order, for comparison.
        val reference = store()
        var referencePosters = true
        setContent {
            TmMaterialTheme(dark = true) {
                Surface(Modifier.size(900.dp, 3000.dp), color = Tone.background) {
                    SetupChoicesList(
                        settings,
                        place = { null },
                        onRunSetupAgain = {},
                        form = DeviceForm.Phone,
                        fit = DeviceFit.Fine,
                    )
                }
            }
        }
        val steps = Setup.stepsFor(DeviceForm.Phone, posters = true, recommendations = emptyList())
        waitUntil(timeoutMillis = WAIT_MS) {
            onAllNodes(hasTestTag("setup-choice-${steps.first().name}")).fetchSemanticsNodes().isNotEmpty()
        }
        for (step in steps) {
            for (choice in step.choices.reversed() + step.choices) {
                // The action a tap runs: most of the rows lie below the test window's fold.
                onNode(hasTestTag("setup-answer-${choice.name}")).performSemanticsAction(SemanticsActions.OnClick)
                runBlocking { choice.applyTo(reference, posters = { referencePosters = it }) }
                waitForIdle()
                waitUntil(timeoutMillis = WAIT_MS) { snapshot(settings) == snapshot(reference) }
                assertEquals(choice.name, referencePosters, online.settings.value.enabled)
            }
        }
    }

    @Test
    fun aRemoteChangesAnAnswerWithLeftRightAndOk() = runComposeUiTest {
        val online = MetaFixture.install("on")
        val settings = store()
        val landing = FocusRequester()
        var opened: SetupStep? = null
        setContent {
            TmMaterialTheme(dark = true) {
                Box(Modifier.fillMaxSize()) {
                    SetupChoicesList(
                        settings,
                        place = { step -> SetupPlace("Library") { opened = step } },
                        onRunSetupAgain = {},
                        form = DeviceForm.Tv,
                        fit = DeviceFit.Fine,
                        landing = landing,
                    )
                }
            }
        }
        // The first step is Posters, on (as MetaFixture sets it).
        waitUntil(timeoutMillis = WAIT_MS) {
            onAllNodes(hasTestTag("setup-choice-${SetupStep.Posters.name}")).fetchSemanticsNodes().isNotEmpty()
        }
        runOnIdle { landing.requestFocus() }
        fun press(key: Key) = onNode(isFocused()).performKeyInput { pressKey(key) }
        press(Key.DirectionRight)
        waitUntil(timeoutMillis = WAIT_MS) { !online.settings.value.enabled }
        press(Key.Enter)
        waitUntil(timeoutMillis = WAIT_MS) { online.settings.value.enabled }
        press(Key.DirectionLeft)
        waitForIdle()
        assertTrue(online.settings.value.enabled)

        onAllNodes(hasText("Also in Library") and hasClickAction())[0].performClick()
        waitForIdle()
        assertEquals(SetupStep.Posters, opened)
    }

    /** Each form factor light and dark; with TMPLAYER_SHOTS=<dir> the frames are written there as PNGs. */
    @Test
    fun drawnLightAndDark() {
        MetaFixture.install("on")
        val settings = store()
        for (form in DeviceForm.entries) {
            for (dark in listOf(true, false)) {
                val png = ImageComposeScene(900, 1500, Density(1f)) {
                    TmMaterialTheme(dark = dark) {
                        Surface(Modifier.fillMaxSize(), color = Tone.background) {
                            Box(Modifier.padding(24.dp)) {
                                SetupChoicesList(
                                    settings,
                                    place = { step -> if (step == SetupStep.Performance) null else SetupPlace("Playback") {} },
                                    onRunSetupAgain = {},
                                    form = form,
                                    fit = DeviceFit(shortOfMemory = false, shortOfStorage = form != DeviceForm.Phone),
                                )
                            }
                        }
                    }
                }.use { scene ->
                    scene.render(0)
                    Thread.sleep(300)
                    scene.render(500_000_000)
                    scene.render(1_000_000_000).encodeToData(EncodedImageFormat.PNG)!!.bytes
                }
                assertTrue(png.size > 1000)
                val out = System.getenv("TMPLAYER_SHOTS")?.takeIf { it.isNotBlank() } ?: continue
                val name = "setup-choices-${form.name.lowercase()}-${if (dark) "dark" else "light"}.png"
                File(out).apply { mkdirs() }.resolve(name).writeBytes(png)
            }
        }
    }

    private fun snapshot(settings: SettingsStore): List<Boolean> = runBlocking {
        listOf(
            settings.lowStorageMode.first(),
            settings.lighterPictures.first(),
            settings.reduceMotion.first(),
            settings.trickplay.first(),
            settings.saveMobileData.first(),
            settings.wifiOnlyDownloads.first(),
        )
    }

    private fun store(): SettingsStore = SettingsStore(
        SettingsStore.openDataStore(Files.createTempDirectory("tm-setup-choices").resolve(SettingsStore.FILE_NAME).toFile()),
    )

    private companion object {
        const val WAIT_MS = 5_000L
    }
}
