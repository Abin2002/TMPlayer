package com.tmplayer.player

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.Player
import com.tmplayer.R
import com.tmplayer.data.FormFactor
import com.tmplayer.i18n.L
import com.tmplayer.ui.theme.TMPlayerTheme
import org.junit.After
import org.junit.Assert.assertEquals
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
import java.lang.reflect.Proxy
import java.time.Duration

/**
 * The player pieces added after the seeking work: the speed sheet's fine control on a phone held
 * both ways and on a television, the failure sheet with Send logs, the "Frame saved" answer with
 * its tick and Share, and the fine scrub's hint over the picture and over the bar. Drawn to
 * `build/device-test/review/extras-*.png` for a look.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerExtrasRenderTest {

    @After
    fun tearDown() {
        FormFactor.override(false)
    }

    private fun idle(ms: Long = 1000) {
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        var left = ms
        while (left > 0) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20))
            left -= 20
        }
    }

    private fun save(bitmap: Bitmap, name: String) {
        val file = File("../build/device-test/review/$name").absoluteFile
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(file.length() > 0)
    }

    private fun shot(activity: ComponentActivity, name: String, dialog: Boolean) {
        val root = activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        root.draw(canvas)
        if (dialog) {
            val window = requireNotNull(ShadowDialog.getLatestDialog()?.window) { "no dialog up" }
            canvas.drawColor(0x99000000.toInt())
            window.decorView.draw(canvas)
        }
        save(bitmap, name)
    }

    // ---- the speed sheet ----------------------------------------------------------------------

    private fun speedSheet(tv: Boolean, name: String, presses: Int = 0, faster: Boolean = true): Float {
        FormFactor.override(tv)
        // Stepped before the sheet opens, as presses would leave it: the figure and the buttons'
        // ends are what the picture is for.
        var start = 1.35f
        repeat(presses) { start = PlaybackSpeed.fine(start, up = faster) }
        var speed by mutableStateOf(start)
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            TMPlayerTheme {
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF203040))) {
                    SpeedSheet(
                        current = speed,
                        onPick = { speed = it },
                        onDismiss = {},
                        onClose = {},
                        skipSilence = false,
                        onFine = { speed = it },
                    )
                }
            }
        }
        idle()
        shot(activity, name, dialog = true)
        return speed
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun speedPhoneUpright() {
        speedSheet(tv = false, name = "extras-speed-phone.png")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi")
    fun speedPhoneSideways() {
        assertEquals(1.45f, speedSheet(tv = false, name = "extras-speed-phone-land.png", presses = 2))
    }

    @Test
    @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-notnight-xhdpi")
    fun speedPhoneAtTheTop() {
        // Up to 4x: plus greys out, minus still works.
        assertEquals(4f, speedSheet(tv = false, name = "extras-speed-phone-max.png", presses = 80))
    }

    @Test
    // Taller than a television, so the whole list is in the picture rather than scrolled.
    @Config(sdk = [35], qualifiers = "w960dp-h1000dp-land-night-xhdpi")
    fun speedTelevision() {
        speedSheet(tv = true, name = "extras-speed-tv.png")
    }

    // ---- the failure sheet --------------------------------------------------------------------

    private fun errorSheet(tv: Boolean, name: String) {
        FormFactor.override(tv)
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        // The player's own theme, as the manifest gives PlayerActivity, so the buttons are its buttons.
        activity.setTheme(R.style.Theme_TMPlayer_Player)
        activity.setContentView(R.layout.activity_player)
        val openWith = activity.findViewById<Button>(R.id.status_open_with)
        val logs = PlayerErrorSheet.addSendLogs(openWith)
        // As showError and showFailureActions leave it for a file that may simply be stale.
        activity.findViewById<View>(R.id.status_overlay).visibility = View.VISIBLE
        activity.findViewById<View>(R.id.status_icon).visibility = View.VISIBLE
        activity.findViewById<View>(R.id.status_spinner)?.visibility = View.GONE
        activity.findViewById<View>(R.id.status_progress).visibility = View.GONE
        activity.findViewById<View>(R.id.status_detail).visibility = View.GONE
        activity.findViewById<TextView>(R.id.status_name).apply {
            text = "Night Train S01E04"
            visibility = View.VISIBLE
        }
        activity.findViewById<TextView>(R.id.status_title).text = L.playerCantPlay
        activity.findViewById<TextView>(R.id.status_text).text =
            "The video stopped coming in. Reload fetches it again from the start."
        activity.findViewById<Button>(R.id.status_reload).apply {
            text = L.commonReload
            visibility = View.VISIBLE
            // What markPrimaryAction gives the suggested way out.
            setBackgroundResource(R.drawable.bg_control_pill_primary)
            setTextColor(context.getColorStateList(R.color.player_primary_text))
        }
        activity.findViewById<Button>(R.id.status_back).apply {
            text = L.playerGoBack
            visibility = View.VISIBLE
        }
        logs?.visibility = View.VISIBLE
        idle()
        assertTrue("Send logs is in the sheet", logs != null && logs.isShown)
        shot(activity, name, dialog = false)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-night-xhdpi")
    fun errorSheetPhone() = errorSheet(tv = false, name = "extras-error-phone.png")

    @Test
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-night-xhdpi")
    fun errorSheetTelevision() = errorSheet(tv = true, name = "extras-error-tv.png")

    // ---- the frame saved answer and the fine scrub ---------------------------------------------

    private val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, _ ->
        when (method.name) {
            "getDuration" -> 2_634_000L
            "getCurrentPosition" -> 1_260_000L
            "getBufferedPosition" -> 1_700_000L
            "isPlaying", "getPlayWhenReady" -> true
            else -> when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                java.lang.Float.TYPE -> 0f
                else -> null
            }
        }
    } as Player

    private fun picture(build: (FragmentActivity, FrameLayout) -> Unit): FragmentActivity {
        FormFactor.override(false)
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        val root = FrameLayout(activity).apply {
            id = R.id.player_root
            setBackgroundColor(Color.rgb(0x20, 0x30, 0x40))
        }
        activity.setContentView(root)
        build(activity, root)
        idle(600)
        return activity
    }

    private fun drawWindow(activity: FragmentActivity, name: String) {
        val decor = activity.window.decorView
        val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        decor.draw(Canvas(bitmap))
        save(bitmap, name)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi")
    fun frameSavedAndSwipeScrub() {
        var feedback: PlayerFeedback? = null
        val activity = picture { _, root ->
            feedback = PlayerFeedback(root, null).also {
                it.offer(L.playerFrameSaved, L.commonShare, tick = true) {}
            }
        }
        drawWindow(activity, "extras-frame-saved.png")
        feedback?.clear()
        feedback?.scrub(deltaMs = 42_000, targetMs = 1_302_000, durationMs = 2_634_000, rate = 0.5f)
        idle(600)
        drawWindow(activity, "extras-swipe-half.png")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w891dp-h411dp-land-night-xhdpi")
    fun barScrubHint() {
        var controls: PlayerControls? = null
        val activity = picture { activity, root ->
            LayoutInflater.from(activity).inflate(R.layout.player_controls, root, true)
            controls = PlayerControls(
                root = root,
                isTv = false,
                player = { player },
                onVisibility = {},
                onTogglePlay = {},
                onPickSubtitles = {},
                onPickAudio = {},
                onPickSpeed = {},
                onCycleOrientation = {},
                onPlayEpisode = {},
            ).also { it.show() }
        }
        controls?.showScrubRate(0.25f)
        idle(600)
        drawWindow(activity, "extras-bar-quarter.png")
        controls?.showScrubRate(1f)
        idle(600)
        drawWindow(activity, "extras-bar-finer.png")
    }
}
