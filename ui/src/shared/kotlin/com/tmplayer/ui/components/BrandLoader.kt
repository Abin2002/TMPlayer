package com.tmplayer.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import com.tmplayer.data.DeviceForm
import com.tmplayer.i18n.L
import com.tmplayer.ui.theme.LocalDarkTheme
import com.tmplayer.ui.theme.Motion
import com.tmplayer.ui.theme.TmSchemes
import com.tmplayer.ui.theme.Tone
import kotlinx.coroutines.delay
import kotlin.math.tan

/**
 * The first thing anybody sees after the system's splash, and for a few seconds the only thing.
 *
 * Opening TMPlayer means waiting on TDLib to open its database, find a socket and say hello to
 * Telegram, which on a cold start over a slow connection is long enough to wonder whether the press
 * registered. So the wait shows the name, "TM" in the accent and "Player" in the text colour, with
 * a tilted band of light passing over the letters like light over glass, and under it a line that
 * changes as the wait goes on. A message that never moves reads as a hang after about five seconds.
 *
 * The ground is the app's own near-black or near-white, never the wallpaper's tint, because it is
 * the colour the window was painted before the first frame (`window_background`), so the system
 * splash hands over to this with no flash. Nothing else on it has a colour but the accent.
 *
 * Phone, television and desktop share it; the sizes follow [deviceForm]. [sweepAt] holds the light
 * at one point of its loop (0 to 1), for drawing frames in a test. [below] is room under the lines
 * for a Retry once the wait has gone on too long.
 */
@Composable
fun BrandLoader(
    label: String,
    modifier: Modifier = Modifier,
    lines: List<String> = connectingLines(),
    sweepAt: Float? = null,
    below: @Composable ColumnScope.() -> Unit = {},
) {
    val form = deviceForm()
    val size = LoaderSizes.of(form)
    val still = rememberLessMotion()
    val sweep = if (still || sweepAt != null) null else rememberSweep()

    // Cycles once through and then stays on the last line. Looping back to the first after
    // suggesting the viewer check their connection would be the app forgetting what it just said.
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(lines.size) {
        for (index in lines.indices) {
            delay(if (index == 0) FIRST_STEP_MS else STEP_MS)
            step = index
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(loaderGround())
            .padding(horizontal = size.side),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Wordmark(
                fontSize = size.wordmark,
                // Read in the draw pass only, so a frame of the sweep redraws the word and never
                // recomposes it.
                sweep = { sweepAt ?: sweep?.value ?: NO_LIGHT },
            )
            Spacer(Modifier.height(size.gap))
            Crossfade(label, animationSpec = tween(LINE_FADE_MS, easing = Motion.Standard), label = "loaderLabel") {
                Text(
                    it,
                    style = TextStyle(fontSize = size.label, lineHeight = size.label * 1.35f, fontWeight = FontWeight.Medium),
                    color = Tone.muted,
                    textAlign = TextAlign.Center,
                )
            }
            if (lines.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Crossfade(
                    lines[step.coerceIn(0, lines.lastIndex)],
                    animationSpec = tween(LINE_FADE_MS, easing = Motion.Standard),
                    label = "loaderLine",
                ) {
                    Text(
                        it,
                        style = TextStyle(fontSize = size.line, lineHeight = size.line * 1.45f),
                        color = Tone.muted.copy(alpha = LINE_ALPHA),
                        textAlign = TextAlign.Center,
                        // A sentence that needs two lines breaks near the middle rather than
                        // running the full width of a television and leaving three words under it.
                        modifier = Modifier.widthIn(max = 420.dp),
                    )
                }
            }
            below()
        }
    }
}

/**
 * [BrandLoader] over the window while [label] is not null, fading out over whatever is under it
 * once it is: the sign in pane or the home screen is already drawn underneath, so the loader
 * thinning to nothing over it is the cross-fade, and the one leaving never jumps. Put it last in a
 * `Box` that fills the window, or give it a `zIndex`, so it is drawn above its siblings.
 */
@Composable
fun BrandLoaderHandOff(
    label: String?,
    modifier: Modifier = Modifier,
    /** The second line's words, or null for [connectingLines]. Empty when [below] says it instead. */
    lines: List<String>? = null,
    below: @Composable ColumnScope.() -> Unit = {},
) {
    val showing = label != null
    // The last label shown, so the fading loader keeps its words rather than going blank. A plain
    // holder: it is only read in the composition that wrote it.
    val held = remember { arrayOf(label) }
    if (label != null) held[0] = label
    val alpha = remember { Animatable(if (showing) 1f else 0f) }
    LaunchedEffect(showing) {
        if (showing) alpha.snapTo(1f) else alpha.animateTo(0f, tween(EXIT_MS, easing = Motion.Standard))
    }
    val shown = held[0] ?: return
    if (!showing && alpha.value == 0f) return
    BrandLoader(
        label = shown,
        modifier = modifier.graphicsLayer { this.alpha = alpha.value },
        lines = lines ?: connectingLines(),
        below = below,
    )
}

/**
 * What the second line says, in the order the wait earns it.
 *
 * The last two are the only advice worth giving: everything this app does needs the internet, and
 * a first launch after an install has a database to build before it can talk to anybody.
 */
@Composable
fun connectingLines(): List<String> = listOf(
    L.stateConnectingSession,
    L.stateConnectingFirstLaunch,
    L.stateConnectingStillTrying,
)

/** The loader's ground: the window's own colour for this theme, whatever the wallpaper says. */
@Composable
private fun loaderGround(): Color = TmSchemes.of(LocalDarkTheme.current).background

/**
 * "TMPlayer", with the light passing over it.
 *
 * The word is drawn into a layer of its own, then shaded from top to foot as glass is, then the
 * band is painted with [BlendMode.SrcAtop], which keeps only the part that lands on a letter. The band is
 * two strokes: a wide, faint glow and a thin bright core, tilted [TILT_DEGREES] off upright. The
 * brushes and sizes are made once per size of the word, so a frame allocates nothing.
 */
@Composable
private fun Wordmark(fontSize: TextUnit, sweep: () -> Float) {
    val accent = Tone.accent
    val ink = Tone.text
    val dark = LocalDarkTheme.current
    val word = remember(accent, ink) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = accent)) { append("TM") }
            withStyle(SpanStyle(color = ink)) { append("Player") }
        }
    }
    val style = TextStyle(
        fontSize = fontSize,
        lineHeight = fontSize * 1.15f,
        fontWeight = FontWeight.Bold,
        letterSpacing = TRACKING,
    )
    // The brand reads left to right in every language, and so does the light.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BasicText(
            text = word,
            style = style,
            softWrap = false,
            maxLines = 1,
            modifier = Modifier
                .semantics { contentDescription = BRAND }
                .glassLight(sweep, dark),
        )
    }
}

private fun Modifier.glassLight(sweep: () -> Float, dark: Boolean): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            val h = size.height
            val glowWidth = h * GLOW_WIDTH
            val coreWidth = h * CORE_WIDTH
            val glowPeak = if (dark) GLOW_DARK else GLOW_LIGHT
            val corePeak = if (dark) CORE_DARK else CORE_LIGHT
            val glow = Brush.horizontalGradient(
                0f to Color.White.copy(alpha = 0f),
                0.5f to Color.White.copy(alpha = glowPeak),
                1f to Color.White.copy(alpha = 0f),
                startX = -glowWidth / 2f,
                endX = glowWidth / 2f,
            )
            val core = Brush.horizontalGradient(
                0f to Color.White.copy(alpha = 0f),
                0.5f to Color.White.copy(alpha = corePeak),
                1f to Color.White.copy(alpha = 0f),
                startX = -coreWidth / 2f,
                endX = coreWidth / 2f,
            )
            // The glass's own shading: the dark theme's near-white letters let down towards their
            // foot, so there is something for the light to brighten, and the light theme's dark
            // letters lifted a little at theirs. Painted over the letters rather than taken out of
            // their alpha, which would cap the light at the same level.
            val shade = if (dark) Color.Black else Color.White
            val body = Brush.verticalGradient(
                0f to shade.copy(alpha = if (dark) SHADE_TOP_DARK else 0f),
                1f to shade.copy(alpha = if (dark) SHADE_FOOT_DARK else SHADE_FOOT_LIGHT),
            )
            // Far enough past each end that the tilted band is wholly off the word at 0 and at 1,
            // so the loop's restart is never seen.
            val slant = h * tan(Math.toRadians(TILT_DEGREES.toDouble())).toFloat()
            val start = -glowWidth - slant
            val end = size.width + glowWidth + slant
            val reach = h * 1.2f
            val glowTopLeft = Offset(-glowWidth / 2f, -reach)
            val glowSize = Size(glowWidth, reach * 2f)
            val coreTopLeft = Offset(-coreWidth / 2f, -reach)
            val coreSize = Size(coreWidth, reach * 2f)
            onDrawWithContent {
                drawContent()
                drawRect(body, blendMode = BlendMode.SrcAtop)
                val p = sweep()
                if (p < 0f) return@onDrawWithContent
                translate(left = start + (end - start) * p, top = h / 2f) {
                    rotate(TILT_DEGREES, pivot = Offset.Zero) {
                        drawRect(glow, topLeft = glowTopLeft, size = glowSize, blendMode = BlendMode.SrcAtop)
                        drawRect(core, topLeft = coreTopLeft, size = coreSize, blendMode = BlendMode.SrcAtop)
                    }
                }
            }
        }

/**
 * One infinite transition for the whole screen: a sweep of [SWEEP_MS] on an ease in and out, then
 * [REST_MS] with the light off the word. The first rest is mostly skipped, so the light arrives
 * soon after the screen does.
 */
@Composable
private fun rememberSweep(): State<Float> =
    rememberInfiniteTransition(label = "brandLight").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(SWEEP_MS, delayMillis = REST_MS, easing = SweepEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(REST_MS - FIRST_REST_MS, StartOffsetType.FastForward),
        ),
        label = "sweep",
    )

/** The loader's figures for each kind of device: the word large on a television and a desktop. */
private class LoaderSizes(
    val wordmark: TextUnit,
    val label: TextUnit,
    val line: TextUnit,
    val gap: androidx.compose.ui.unit.Dp,
    val side: androidx.compose.ui.unit.Dp,
) {
    companion object {
        private val Phone = LoaderSizes(44.sp, 15.sp, 13.sp, 24.dp, 16.dp)
        private val Tv = LoaderSizes(64.sp, 18.sp, 15.sp, 30.dp, 72.dp)
        private val Desktop = LoaderSizes(68.sp, 17.sp, 14.sp, 30.dp, 48.dp)

        fun of(form: DeviceForm): LoaderSizes = when (form) {
            DeviceForm.Phone -> Phone
            DeviceForm.Tv -> Tv
            DeviceForm.Desktop -> Desktop
        }
    }
}

/** The name the app is installed under. A brand, never translated. */
private const val BRAND = "TMPlayer"

/** Tight, as a wordmark is set, but every letter still stands on its own. */
private val TRACKING = (-0.025).em

/** How far the band leans off upright, top to the right. */
private const val TILT_DEGREES = 18f

/** Widths of the band's two strokes, as a share of the word's height. */
private const val GLOW_WIDTH = 1.1f
private const val CORE_WIDTH = 0.16f

/**
 * The band's peaks, and the glass's shading at the top and foot of the letters. The dark theme's
 * letters are near-white, so its light is stronger and its letters are let down further, to give
 * the light something to brighten.
 */
private const val GLOW_DARK = 0.7f
private const val CORE_DARK = 1f
private const val GLOW_LIGHT = 0.42f
private const val CORE_LIGHT = 0.9f
private const val SHADE_TOP_DARK = 0.06f
private const val SHADE_FOOT_DARK = 0.38f
private const val SHADE_FOOT_LIGHT = 0.14f

/** Off the word: the sweep's value under reduced motion. */
private const val NO_LIGHT = -1f

private const val SWEEP_MS = 2_100
private const val REST_MS = 900
private const val FIRST_REST_MS = 450

/** Off its mark gently and into its stop gently, so the light glides rather than slides. */
private val SweepEasing = CubicBezierEasing(0.45f, 0f, 0.2f, 1f)

private const val LINE_FADE_MS = 500
private const val LINE_ALPHA = 0.72f
private const val EXIT_MS = 420

/** Long enough that a connection that lands immediately shows the first line and nothing else. */
private const val FIRST_STEP_MS = 1_200L
private const val STEP_MS = 5_000L
