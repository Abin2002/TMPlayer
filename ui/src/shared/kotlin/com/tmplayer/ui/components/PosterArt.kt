package com.tmplayer.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tmplayer.data.Thumbnails
import com.tmplayer.ui.online.MetaPicture
import com.tmplayer.ui.theme.Motion
import com.tmplayer.ui.theme.Scrims
import com.tmplayer.ui.theme.Tone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The picture on a film's or a show's 2:3 poster card, by the best thing there is:
 *
 * 1. the online poster, when posters and overviews are on and the providers know the title;
 * 2. a portrait Telegram thumbnail, which fills the card as it is;
 * 3. a landscape thumbnail, shown whole across the upper middle of the card over a soft field of
 *    the same picture ([Thumbnails.backdrop], a 32 px decode stretched, no blur call) with the
 *    tile's foot scrim, so nothing of the frame is cropped away to make it tall, and the film's
 *    name set in full in the space under it, so a film no provider recognised still reads as a
 *    poster rather than a frame with an empty band under it;
 * 4. the file's [GeneratedCover], when there is no picture at all.
 *
 * The caller clips and sizes it; [posterUrl] is the online poster's address, or null.
 */
@Composable
fun PosterArt(
    miniThumbnail: ByteArray?,
    thumbnailFileId: Int,
    cover: Cover,
    posterUrl: String?,
    modifier: Modifier = Modifier,
    compactCover: Boolean = false,
    /** The name set under a landscape picture; the cover's own title unless told otherwise. */
    title: String = cover.title,
) {
    // The inline preview first, off the composition thread, then the thumbnail when it arrives.
    val mini by produceState<ImageBitmap?>(null, miniThumbnail) {
        value = withContext(Dispatchers.Default) { Thumbnails.mini(miniThumbnail) }
    }
    var full by remember(thumbnailFileId) { mutableStateOf(Thumbnails.peekFull(thumbnailFileId)) }
    LaunchedEffect(thumbnailFileId) { if (full == null) full = Thumbnails.full(thumbnailFileId) }
    val picture = full ?: mini
    Box(modifier.background(Tone.surface)) {
        Crossfade(targetState = picture, animationSpec = Motion.standard(), label = "posterPicture") { shown ->
            when {
                shown == null -> GeneratedCover(cover, Modifier.fillMaxSize(), compact = compactCover)
                shown.height >= shown.width -> Image(
                    shown,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = Thumbnails.drawFilter,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> WidePicture(shown, miniThumbnail, thumbnailFileId, title, compactCover)
            }
        }
        MetaPicture(posterUrl, Modifier.fillMaxSize(), maxWidth = POSTER_WIDTH)
    }
}

/** A landscape picture whole across a tall card, over a soft field of itself. */
@Composable
private fun WidePicture(picture: ImageBitmap, miniThumbnail: ByteArray?, thumbnailFileId: Int, title: String, compact: Boolean) {
    val field by produceState<ImageBitmap?>(null, thumbnailFileId, miniThumbnail) {
        value = Thumbnails.backdrop(thumbnailFileId, miniThumbnail)
    }
    Box(Modifier.fillMaxSize()) {
        Image(
            field ?: picture,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.Low,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = FIELD_DIM)))
        Image(
            picture,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            // Above the middle when there is a name to set under it.
            alignment = if (title.isBlank()) Alignment.Center else PICTURE_RAISED,
            filterQuality = Thumbnails.drawFilter,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(Scrims.tileFoot))
        if (title.isNotBlank()) {
            Text(
                title,
                style = (if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium)
                    .copy(fontWeight = FontWeight.SemiBold, lineHeight = if (compact) 16.sp else 20.sp),
                color = Color.White,
                maxLines = if (compact) 3 else 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = if (compact) 8.dp else 12.dp, vertical = if (compact) 8.dp else 12.dp),
            )
        }
    }
}

/** The online poster's decode width: a card is at most about 180 dp wide. */
private const val POSTER_WIDTH = 342

/** Where a landscape picture sits on a poster that sets the name under it: a little above the middle. */
private val PICTURE_RAISED = androidx.compose.ui.BiasAlignment(0f, -0.4f)

/** The soft field is dimmed so the sharp picture over it reads as the picture. */
private const val FIELD_DIM = 0.3f
