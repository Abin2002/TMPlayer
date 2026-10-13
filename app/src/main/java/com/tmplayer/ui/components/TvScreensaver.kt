package com.tmplayer.ui.components

import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.tmplayer.data.ChatRepository
import com.tmplayer.data.ResumeRecord
import com.tmplayer.data.Screensaver
import com.tmplayer.data.Td
import com.tmplayer.data.Thumbnails
import com.tmplayer.i18n.L
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withTimeoutOrNull
import java.text.DateFormat
import java.util.Date

/**
 * When the television last heard from the remote, and whether the screensaver is up (CP57).
 *
 * Fed by `MainActivity.dispatchKeyEvent` before anything else sees the key, which is how a key
 * that wakes the screensaver is eaten whole, down, repeats and up, instead of also moving focus or
 * pressing whatever was under it.
 */
object TvIdle {
    private val last = MutableStateFlow(SystemClock.uptimeMillis())
    private val shown = MutableStateFlow(false)
    private var swallowing: Int? = null

    /** The uptime of the last key, from [SystemClock.uptimeMillis]. */
    val lastInput: StateFlow<Long> = last.asStateFlow()
    val showing: StateFlow<Boolean> = shown.asStateFlow()

    /** Every key, first. True when this one only woke the screensaver and must go no further. */
    fun onKey(event: KeyEvent): Boolean {
        last.value = SystemClock.uptimeMillis()
        val held = swallowing
        if (held != null && event.keyCode == held) {
            if (event.action == KeyEvent.ACTION_UP) swallowing = null
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN && shown.value) {
            shown.value = false
            swallowing = event.keyCode
            return true
        }
        return false
    }

    fun show() {
        shown.value = true
    }

    fun hide() {
        shown.value = false
    }
}

/**
 * Raises the screensaver after [minutes] without a key, while [browsing] (a browse screen in
 * front, not the player, not a sign in). Off at 0 minutes, and dropped the moment [browsing] stops.
 */
@Composable
fun ScreensaverTimer(minutes: Int, browsing: Boolean) {
    LaunchedEffect(minutes, browsing) {
        val idle = Screensaver.idleMs(minutes)
        if (idle == null || !browsing) {
            TvIdle.hide()
            return@LaunchedEffect
        }
        TvIdle.lastInput.collectLatest { last ->
            delay((last + idle - SystemClock.uptimeMillis()).coerceAtLeast(0L))
            TvIdle.show()
        }
    }
}

/**
 * The screensaver: black, with the clock and one picture moving round the screen a minute at a
 * time so nothing lights the same pixels for long. The pictures are the Continue watching videos'
 * own thumbnails, fetched one at a time and only while this is up, faded one into the next; with
 * none, the app's mark stands in. No blur, no more than one crossfade at once, and a redraw a
 * second at most: it has to sit lightly on a stick that may be left on it all evening.
 *
 * It takes no keys itself. [TvIdle.onKey] has already eaten the one that ends it.
 */
@Composable
fun TvScreensaver(records: List<ResumeRecord>) {
    // The dim, rather than a cut to black.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val shade by animateFloatAsState(if (entered) 1f else 0f, tween(FADE_IN_MS), label = "screensaverShade")

    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var slot by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            clock = System.currentTimeMillis()
            // To the next whole minute of the clock, so the time shown is never a minute stale,
            // and a new spot with it ([Screensaver.MOVE_EVERY_MS]).
            delay(Screensaver.MOVE_EVERY_MS - clock % Screensaver.MOVE_EVERY_MS)
            slot += 1
        }
    }

    // The pictures, one fetched at a time, oldest wish first. Ids, not a fixed list, so a record
    // finished in the meantime does not keep its tile.
    val pictures = remember { mutableStateListOf<ImageBitmap>() }
    val ids = remember(records) { records.take(MAX_PICTURES).map { it.chatId to it.messageId } }
    LaunchedEffect(ids) {
        pictures.clear()
        val client = withTimeoutOrNull(5_000L) { Td.awaitAuthorizedSession().client } ?: return@LaunchedEffect
        val repository = ChatRepository(client)
        for ((chatId, messageId) in ids) {
            val item = runCatching { repository.mediaItem(chatId, messageId) }.getOrNull() ?: continue
            val bitmap = runCatching { Thumbnails.full(item.thumbnailFileId) }.getOrNull()
                ?: Thumbnails.mini(item.miniThumbnail)
                ?: continue
            pictures += bitmap
        }
    }
    var showing by remember { mutableIntStateOf(0) }
    LaunchedEffect(pictures.size) {
        while (pictures.size > 1) {
            delay(Screensaver.PICTURE_EVERY_MS)
            showing = (showing + 1) % pictures.size
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .alpha(shade)
            .background(Color.Black),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(48.dp)) {
            val (fx, fy) = Screensaver.spot(slot)
            val roomX = (maxWidth - BLOCK_WIDTH).coerceAtLeast(0.dp)
            val roomY = (maxHeight - BLOCK_HEIGHT).coerceAtLeast(0.dp)
            // A slow glide from one spot to the next rather than a jump, which would read as a
            // flicker in a dark room.
            val x by animateFloatAsState(fx, tween(DRIFT_MS, easing = LinearEasing), label = "saverX")
            val y by animateFloatAsState(fy, tween(DRIFT_MS, easing = LinearEasing), label = "saverY")
            val density = androidx.compose.ui.platform.LocalDensity.current
            Column(
                Modifier
                    .width(BLOCK_WIDTH)
                    .offset {
                        with(density) { IntOffset((roomX * x).roundToPx(), (roomY * y).roundToPx()) }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.width(BLOCK_WIDTH).height(PICTURE_HEIGHT).clip(RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (pictures.isEmpty()) {
                        AppMark(size = 96.dp, modifier = Modifier.alpha(PICTURE_ALPHA))
                    } else {
                        Crossfade(
                            targetState = pictures.getOrNull(showing) ?: pictures.first(),
                            animationSpec = tween(CROSSFADE_MS),
                            label = "screensaverPicture",
                        ) { bitmap ->
                            Image(
                                bitmap = bitmap,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                filterQuality = FilterQuality.Low,
                                modifier = Modifier.fillMaxSize().alpha(PICTURE_ALPHA),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(clock)),
                    fontSize = 64.sp,
                    color = CLOCK,
                )
                Text(
                    DateFormat.getDateInstance(DateFormat.FULL).format(Date(clock)),
                    fontSize = 18.sp,
                    color = FAINT,
                )
                Spacer(Modifier.size(12.dp))
                Text(L.screensaverWake, fontSize = 14.sp, color = FAINT)
            }
        }
    }
}

private val BLOCK_WIDTH = 384.dp
private val PICTURE_HEIGHT = 216.dp
private val BLOCK_HEIGHT = 216.dp + 160.dp

/** Grey rather than white, and the picture held back: bright on black is what marks a panel. */
private val CLOCK = Color(0xFFB8B8B8)
private val FAINT = Color(0xFF6E6E6E)
private const val PICTURE_ALPHA = 0.7f

private const val MAX_PICTURES = 8
private const val FADE_IN_MS = 1_500
private const val CROSSFADE_MS = 2_000
private const val DRIFT_MS = 8_000
