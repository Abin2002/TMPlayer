package com.tmplayer.ui.downloads

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.tmplayer.data.FormFactor
import com.tmplayer.data.FreeUpSpace
import com.tmplayer.data.StorageChart
import com.tmplayer.data.StorageChart.Part
import com.tmplayer.ui.components.ChoiceList
import com.tmplayer.ui.components.ChoiceSheet
import com.tmplayer.ui.theme.TMPlayerTheme
import com.tmplayer.ui.theme.Tone
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.time.Duration

/**
 * The storage ring on the Downloads page, on a phone and a television, light and dark, and the
 * Free up space sheet over it, for checking by eye. PNGs under `build/device-test/review/`.
 *
 * The figures are a stick that is nearly full, as a friend's was: a download, a paused one, the
 * last video played, an unmatched file, the pictures, and a sliver of posters.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StorageChartRenderTest {

    private val mb = 1024L * 1024
    private val gb = 1024 * mb

    private val figures = StorageChart.Figures(
        downloads = 3 * gb + 200 * mb,
        unfinished = 900 * mb,
        recent = 1 * gb + 400 * mb,
        unmatched = 2 * gb + 500 * mb,
        pictures = 40 * mb,
        deviceTotal = 16 * gb,
        deviceFree = 1 * gb + 100 * mb,
    )

    private val lines = FreeUpSpace.lines(
        lowStorageOn = false,
        short = true,
        recentBytes = figures.recent + figures.unmatched,
        recentCount = 2,
        unfinishedBytes = figures.unfinished,
        unfinishedCount = 1,
        picturesBytes = figures.pictures,
        downloadCount = 4,
    )

    private val actions: Map<Part, () -> Unit> = mapOf(
        Part.Downloads to {}, Part.Unfinished to {}, Part.Recent to {}, Part.Unmatched to {}, Part.Pictures to {},
    )

    @After
    fun tearDown() {
        FormFactor.override(false)
        com.tmplayer.ui.theme.Motion.reduced = false
    }

    /** A television follows the app's own theme setting, not the system's, so light is asked for. */
    private fun theme(choice: com.tmplayer.data.ThemeChoice) = kotlinx.coroutines.runBlocking {
        com.tmplayer.data.SettingsStore(org.robolectric.RuntimeEnvironment.getApplication()).setThemeChoice(choice)
    }

    private fun activity(tv: Boolean, content: @Composable () -> Unit): ComponentActivity {
        FormFactor.override(tv)
        // The ring as it settles, not part way through sweeping in: Robolectric's frame clock
        // carries over between tests, so an animation started late in a run can be caught early.
        com.tmplayer.ui.theme.Motion.reduced = true
        theme(if (tv && "notnight" in org.robolectric.RuntimeEnvironment.getQualifiers()) com.tmplayer.data.ThemeChoice.Light else com.tmplayer.data.ThemeChoice.Default)
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            TMPlayerTheme {
                Box(Modifier.fillMaxSize().background(Tone.background)) { content() }
            }
        }
        return activity
    }

    private fun idle(ms: Long) {
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        var left = ms
        while (left > 0) {
            val step = minOf(left, 20L)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(step))
            left -= step
        }
    }

    private fun shot(activity: ComponentActivity, name: String, dialog: Boolean = false) {
        val root = activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        root.draw(canvas)
        if (dialog) {
            val window = requireNotNull(ShadowDialog.getLatestDialog()?.window) { "no dialog up" }
            canvas.drawColor(0x99000000.toInt())
            window.decorView.draw(canvas)
        }
        val file = File("../build/device-test/review/$name").absoluteFile
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(file.length() > 0)
    }

    @Composable
    private fun Page(tv: Boolean, selected: Part? = null) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (tv) 32.dp else 0.dp, vertical = if (tv) 24.dp else 48.dp)
                .then(if (tv) Modifier.width(720.dp) else Modifier),
        ) {
            StorageSummary(figures = figures, measured = true, actions = actions, onFreeUp = {}, selected = selected)
        }
    }

    @Composable
    private fun Sheet() {
        ChoiceSheet(title = "Free up space", note = "Nothing is removed from Telegram. You can play or download any of it again.", onDismiss = {}, onClose = {}) {
            val first = remember { FocusRequester() }
            ChoiceList { FreeUpLines(lines, first) {} }
        }
    }

    private fun render(tv: Boolean, name: String) {
        val activity = activity(tv) { Page(tv) }
        idle(1500)
        shot(activity, "storage-$name.png")
    }

    private fun renderSheet(tv: Boolean, name: String) {
        val activity = activity(tv) {
            Page(tv)
            Sheet()
        }
        idle(1500)
        shot(activity, "storage-sheet-$name.png", dialog = true)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-notnight-xhdpi")
    fun phoneLight() = render(false, "phone-light")

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun phoneDark() = render(false, "phone-dark")

    @Test
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-notnight-mdpi")
    fun tvLight() = render(true, "tv-light")

    @Test
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-night-mdpi")
    fun tvDark() = render(true, "tv-dark")

    /** A part chosen on the ring: it lifts, the rest dim, and the centre says what it holds. */
    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun phoneDarkChosen() {
        val activity = activity(false) { Page(false, selected = Part.Unmatched) }
        idle(1500)
        shot(activity, "storage-phone-dark-chosen.png")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-notnight-mdpi")
    fun tvLightChosen() {
        val activity = activity(true) { Page(true, selected = Part.Recent) }
        idle(1500)
        shot(activity, "storage-tv-light-chosen.png")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun phoneSheetDark() = renderSheet(false, "phone-dark")

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-notnight-xhdpi")
    fun phoneSheetLight() = renderSheet(false, "phone-light")

    @Test
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-night-mdpi")
    fun tvSheetDark() = renderSheet(true, "tv-dark")

    @Test
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-notnight-mdpi")
    fun tvSheetLight() = renderSheet(true, "tv-light")
}
