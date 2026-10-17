package com.tmplayer.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.tmplayer.data.Thumbnails
import com.tmplayer.data.decodesFullSize
import com.tmplayer.data.sharperUrl
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import com.tmplayer.online.OnlineMetadata
import com.tmplayer.ui.theme.Motion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A large picture behind a hero or a page, from the best art a video has.
 *
 * The chain, best first: the online wide picture ([wideUrl], a film's backdrop or an episode's
 * still, decoded at [ONLINE_WIDTH]); the full Telegram thumbnail; the inline preview, which
 * stretched this far is already a blur; and last the video's [GeneratedCover]. Each step fades
 * into the next over [Motion.Normal], so a better picture arriving never pops.
 *
 * Telegram's thumbnails are 320 pixels wide at best, and one stretched across a television is a
 * smear of blocks. So a picture under [SHARP_MIN_WIDTH] is not stretched sharp: the frame gets
 * [Thumbnails.backdrop], a 32 pixel shrink of it drawn across the whole frame with bilinear
 * filtering, which is a soft field of its colours, and the picture itself sits over it at its own
 * shape, at the top end of the frame, fading into the field at its start and its foot. The art
 * reads as art rather than as a blur of pixels, and there is no `blur()` anywhere in it.
 *
 * [fieldOnly] draws the field alone and decodes nothing larger, for a device short of memory.
 * The scrims that text needs over all of this are the caller's ([com.tmplayer.ui.theme.Scrims]).
 */
@Composable
fun ArtBackdrop(
    source: BackdropSource,
    modifier: Modifier = Modifier,
    fieldOnly: Boolean = false,
) {
    val online = OnlineMetadata.current
    // Where the platform decodes at full size (the desktop), the online picture is fetched at the
    // width the frame is drawn, in steps, and only ever larger: a window being resized does not
    // fetch at every pixel, and a smaller window keeps the sharper copy it has.
    var drawn by remember { mutableIntStateOf(0) }
    val wanted = if (decodesFullSize) maxOf(ONLINE_WIDTH, (drawn + WIDTH_STEP - 1) / WIDTH_STEP * WIDTH_STEP) else ONLINE_WIDTH
    val peak = remember { intArrayOf(0) }
    peak[0] = maxOf(peak[0], wanted)
    val widest = peak[0]
    var art by remember(source) { mutableStateOf(peek(source, fieldOnly, widest)) }
    LaunchedEffect(source, fieldOnly, widest) {
        if (fieldOnly) {
            Thumbnails.backdrop(source.thumbnailFileId, source.miniThumbnail)?.let { art = BackdropArt(field = it) }
            return@LaunchedEffect
        }
        // Not until the frame has been measured, so the first fetch is the size that is drawn.
        if (decodesFullSize && drawn == 0) return@LaunchedEffect
        val wide = source.wideUrl?.let { url ->
            Thumbnails.peekSharp(url, widest, ONLINE_WIDTH)?.takeIf { it.width >= widest || sharperUrl(url, widest) == url }
                ?: online?.let { provider -> Thumbnails.sharp(url, widest, ONLINE_WIDTH) { provider.image(it) } }
        }
        if (wide != null && wide.width >= SHARP_MIN_WIDTH) {
            art = BackdropArt(picture = wide)
            return@LaunchedEffect
        }
        // Until the thumbnail is in, the inline preview stretched is the backdrop.
        if (art == null) {
            withContext(Dispatchers.Default) { Thumbnails.mini(source.miniThumbnail) }?.let { art = BackdropArt(field = it) }
        }
        val full = Thumbnails.full(source.thumbnailFileId) ?: wide
        val field = Thumbnails.backdrop(source.thumbnailFileId, source.miniThumbnail)
        art = when {
            full != null && full.width >= SHARP_MIN_WIDTH -> BackdropArt(picture = full)
            full != null -> BackdropArt(picture = full, field = field)
            field != null -> BackdropArt(field = field)
            else -> art ?: BackdropArt()
        }
    }

    Box(modifier.onSizeChanged { drawn = it.width }) {
        // No fade for the first step: a cross-fade starts on whatever it is first given, so it
        // only runs between two steps of the chain.
        val shown = art ?: return@Box
        Crossfade(targetState = shown, animationSpec = Motion.standard()) { step ->
            when {
                step.picture != null && step.field == null -> Image(
                    bitmap = step.picture,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = Thumbnails.drawFilter,
                    modifier = Modifier.fillMaxSize(),
                )
                step.field != null -> Box(Modifier.fillMaxSize()) {
                    Image(
                        bitmap = step.field,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.Low,
                        modifier = Modifier.fillMaxSize(),
                    )
                    step.picture?.let { SmallPicture(it, Modifier.align(Alignment.TopEnd)) }
                }
                // The colours alone: the cover's big number and chat initial sit in its top
                // corners, where a page's Close or Back floats, and the page already says both.
                else -> GeneratedCover(
                    remember(source.cover) { source.cover.copy(number = null, chatInitial = null) },
                    Modifier.fillMaxSize(),
                    showTitle = false,
                )
            }
        }
    }
}

/**
 * Where an [ArtBackdrop]'s picture comes from: a video's own art, its [cover] for when it has
 * none, and the online [wideUrl] when metadata is on and matched.
 */
@Immutable
data class BackdropSource(
    val miniThumbnail: ByteArray?,
    val thumbnailFileId: Int,
    val cover: Cover,
    val wideUrl: String? = null,
) {
    override fun equals(other: Any?): Boolean =
        other is BackdropSource &&
            other.thumbnailFileId == thumbnailFileId &&
            other.cover == cover &&
            other.wideUrl == wideUrl &&
            (other.miniThumbnail?.contentEquals(miniThumbnail) ?: (miniThumbnail == null))

    override fun hashCode(): Int =
        ((thumbnailFileId * 31 + cover.hashCode()) * 31 + wideUrl.hashCode()) * 31 + (miniThumbnail?.contentHashCode() ?: 0)
}

/** What the backdrop draws at one step of the chain. Neither set means the generated cover. */
private class BackdropArt(val picture: ImageBitmap? = null, val field: ImageBitmap? = null)

/** What is already decoded for [source], so a backdrop seen before is there on the first frame. */
private fun peek(source: BackdropSource, fieldOnly: Boolean, pixels: Int): BackdropArt? {
    if (fieldOnly) return null
    source.wideUrl?.let { url -> Thumbnails.peekSharp(url, pixels, ONLINE_WIDTH)?.let { return BackdropArt(picture = it) } }
    val full = Thumbnails.peekFull(source.thumbnailFileId) ?: return null
    return if (full.width >= SHARP_MIN_WIDTH) BackdropArt(picture = full) else null
}

/**
 * A small picture over its own soft field: at its own shape, [SMALL_PICTURE_SHARE] of the frame's
 * width (less where the frame is too short for that), against the top end, faded out towards the
 * start and the foot so it has no edge against the field. The start of the frame, where a hero
 * sets its text, is the field alone.
 */
@Composable
private fun SmallPicture(picture: ImageBitmap, modifier: Modifier) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Image(
        bitmap = picture,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        filterQuality = FilterQuality.Low,
        modifier = modifier
            .fillMaxWidth(SMALL_PICTURE_SHARE)
            .aspectRatio(picture.width.toFloat() / picture.height.coerceAtLeast(1))
            // Drawn into its own layer, so the fades below cut the picture away rather than
            // painting black over it.
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                val across = if (rtl) {
                    arrayOf(0f to Color.Black, 1f - PICTURE_FADE to Color.Black, 1f to Color.Transparent)
                } else {
                    arrayOf(0f to Color.Transparent, PICTURE_FADE to Color.Black, 1f to Color.Black)
                }
                drawRect(Brush.horizontalGradient(*across), blendMode = BlendMode.DstIn)
                drawRect(
                    Brush.verticalGradient(0f to Color.Black, 1f - PICTURE_FADE to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
            },
    )
}

/**
 * How wide the online picture is decoded for a backdrop on Android, a hero's width on a 1080p
 * screen, and the least the desktop fetches.
 */
private const val ONLINE_WIDTH = 1280

/** The steps the desktop's measured width is rounded up to before it picks a picture. */
private const val WIDTH_STEP = 320

/**
 * Below this many pixels across, a picture is too small to stretch over a backdrop sharp and
 * sits over its own soft field instead.
 */
private const val SHARP_MIN_WIDTH = 480

/** How much of the frame's width a small picture takes, at most. */
private const val SMALL_PICTURE_SHARE = 0.65f

/** The share of a small picture's width and height that fades into the field. */
private const val PICTURE_FADE = 0.35f
