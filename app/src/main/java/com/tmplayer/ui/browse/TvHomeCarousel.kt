package com.tmplayer.ui.browse

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.tmplayer.data.FeaturedPick
import com.tmplayer.data.MediaItem
import com.tmplayer.data.SeriesShelf
import com.tmplayer.ui.components.ArtBackdrop
import com.tmplayer.ui.components.BackdropSource
import com.tmplayer.ui.components.SkeletonBox
import com.tmplayer.ui.components.rememberCover
import com.tmplayer.ui.components.rememberShimmer
import com.tmplayer.ui.i18n.LocalStrings
import com.tmplayer.ui.icons.TmIcons
import com.tmplayer.ui.online.rememberMeta
import com.tmplayer.ui.theme.Corner
import com.tmplayer.ui.theme.Motion
import com.tmplayer.ui.theme.Scrims
import com.tmplayer.ui.theme.Tone
import com.tmplayer.ui.theme.focusRing
import com.tmplayer.ui.theme.rememberReducedMotion

/**
 * The television's featured carousel at the top of Home: the same newest five different films,
 * shows and videos in the starred chats as the phone's ([FeaturedCarousel],
 * [com.tmplayer.data.HomeFeatured]), one picture at a time across the whole width of the page and
 * up behind its [header], with its name, its facts, the line of segments and Play and Details at
 * the start, the phone's order.
 *
 * Made for a remote and a 1 GB stick rather than a finger:
 * - One page is composed at a time, cross-faded to the next: no pager, so only one picture is
 *   fetched and held, a w780 backdrop of about 0.7 MB.
 * - Play and Details stay put while the pages change under them, so the remote never loses its
 *   place. Left on Play goes back a page (from the first, on to the rail as anywhere else), Right
 *   on Details goes on a page; OK on Play plays; Down goes to the rows.
 * - It moves on by itself every [ADVANCE_MS] only while the remote is elsewhere on Home and
 *   nothing is over it ([active]), and never with motion off.
 * - It is [height] tall from the first frame, a stand-in shimmering until the pages arrive, so
 *   Home never jumps.
 */
@Composable
internal fun TvFeaturedCarousel(
    picks: List<FeaturedPick>?,
    height: Dp,
    start: Dp,
    end: Dp,
    watch: SeriesWatch,
    chatTitle: (Long) -> String,
    active: Boolean,
    onPlay: (MediaItem, String) -> Unit,
    onDetails: (FeaturedPick) -> Unit,
    modifier: Modifier = Modifier,
    /** The page's heading, standing on the picture's top; null leaves it to the page. */
    header: (@Composable () -> Unit)? = null,
) {
    var headerPx by remember { mutableIntStateOf(0) }
    // Home gives the carousel the whole width to the screen's edge, as a backdrop should have;
    // the heading on it keeps the safe area at its end, as the rows below do.
    Box(modifier.fillMaxWidth()) {
        // The heading's height and the picture's own make the carousel's size; everything else is
        // laid over that, so the picture runs up behind the heading.
        val headerHeight = with(LocalDensity.current) { headerPx.toDp() }
        Spacer(Modifier.fillMaxWidth().height(headerHeight + height))
        if (picks.isNullOrEmpty()) {
            TvFeaturedSkeleton(start, Modifier.matchParentSize())
            if (header != null) Box(Modifier.fillMaxWidth().onSizeChanged { headerPx = it.height }) { header() }
            return@Box
        }
        val s = LocalStrings.current
        val count = picks.size
        val latest by rememberUpdatedState(picks)
        // The page up, by key, so a carousel that refills stays on the page being looked at.
        var currentKey by remember { mutableStateOf(picks.first().key) }
        val index = picks.indexOfFirst { it.key == currentKey }.coerceAtLeast(0)
        val pick = picks[index]
        val go: (Int) -> Unit = { to -> latest.getOrNull(((to % count) + count) % count)?.let { currentKey = it.key } }

        val still = rememberReducedMotion() || Motion.reduced
        val auto = count > 1 && !still
        var focusedIn by remember { mutableStateOf(false) }
        val paused = focusedIn || !active
        val progress = remember { Animatable(0f) }
        var progressKey by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(currentKey, paused, auto, count) {
            // A new page starts its segment from empty; a pause keeps it where it stopped.
            if (progressKey != currentKey) {
                progress.snapTo(0f)
                progressKey = currentKey
            }
            if (!auto || paused) return@LaunchedEffect
            val left = (1f - progress.value).coerceIn(0f, 1f)
            progress.animateTo(1f, tween((ADVANCE_MS * left).toInt(), easing = LinearEasing))
            go(index + 1)
        }

        Crossfade(targetState = pick, animationSpec = Motion.standard(), label = "tvFeatured", modifier = Modifier.matchParentSize()) { shown ->
            // The heading's words over any picture: a fade from the top, deeper than the heading,
            // under the page's own words.
            TvFeaturedArt(shown, chatTitle, start = start, segments = count > 1, headerFade = if (header != null) headerPx * HEADER_FADE_REACH else 0f)
        }
        if (header != null) {
            Box(Modifier.fillMaxWidth().onSizeChanged { headerPx = it.height }) { header() }
        }

        // What Play starts, worked out as the phone's page does.
        val series = pick.series
        val showProgress = remember(series, watch) { series?.let { watch.progress(it) } }
        val episode = series?.let { showProgress?.next ?: it.episodes.firstOrNull() }
        val playItem = if (series != null && episode != null && showProgress != null) watch.pick(episode, showProgress) else pick.item
        val playLabel = if (series != null && episode != null && showProgress != null) {
            seriesPlayLabel(episode.code, watch.pointOf(episode), showProgress.watched > 0)
        } else if ((watch.point(pick.item)?.positionMs ?: 0L) > 0L) {
            s.commonResume
        } else {
            s.commonPlay
        }
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val back = if (rtl) Key.DirectionRight else Key.DirectionLeft
        val forward = if (rtl) Key.DirectionLeft else Key.DirectionRight

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = start, bottom = PAGE_PAD),
            verticalArrangement = Arrangement.spacedBy(SEGMENTS_GAP),
        ) {
        if (count > 1) {
            Segments(
                count = count,
                current = index,
                fill = { at ->
                    when {
                        at < index -> 1f
                        at > index -> 0f
                        !auto -> 1f
                        else -> progress.value
                    }
                },
                label = { at -> s.homeFeaturedPage(at + 1, count) },
                onPick = { at -> go(at) },
                modifier = Modifier.fillMaxWidth(SEGMENTS_SHARE),
                focusable = false,
            )
        }
        Row(
            Modifier.onFocusChanged { focusedIn = it.hasFocus },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeroPill(
                label = playLabel,
                onClick = { onPlay(playItem, chatTitle(playItem.chatId)) },
                modifier = Modifier.onPreviewKeyEvent { event ->
                    // Back a page; from the first page Left carries on to the rail as usual.
                    if (event.type == KeyEventType.KeyDown && event.key == back && count > 1 && index > 0) {
                        go(index - 1)
                        true
                    } else {
                        false
                    }
                },
            )
            TvDetailsPill(
                label = s.detailOpenDetails,
                onClick = { onDetails(pick) },
                modifier = Modifier.onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == forward && count > 1) {
                        go(index + 1)
                        true
                    } else {
                        false
                    }
                },
            )
        }
        }
    }
}

/** A page's picture, the fades that let its words read, and the name and facts at its start. */
@Composable
private fun TvFeaturedArt(pick: FeaturedPick, chatTitle: (Long) -> String, start: Dp, segments: Boolean, headerFade: Float) {
    val s = LocalStrings.current
    val item = pick.item
    val series = pick.series
    val chat = chatTitle(item.chatId)
    // A show's backdrop rather than one episode's still: the page stands for the whole show.
    val meta = rememberMeta(item, showOnly = series != null)
    val cover = rememberCover(item, chat)
    val source = remember(item, meta?.wideUrl, cover) { BackdropSource(item.miniThumbnail, item.thumbnailFileId, cover, meta?.wideUrl) }
    val title = remember(pick) { series?.title?.ifBlank { null } ?: SeriesShelf.tileTitle(item) }
    val facts = remember(pick, chat, s) { featuredFacts(pick, title, chat, s) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Tone.surface)
            .semantics { contentDescription = listOf(title, facts).filter { it.isNotBlank() }.joinToString(". ") },
    ) {
        ArtBackdrop(source, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Scrims.startLong()))
        if (headerFade > 0f) Box(Modifier.fillMaxSize().drawBehind { drawRect(Scrims.headerTop(headerFade)) })
        // The foot fades into the page, so the first row below reads as the same ground.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Tone.background)))
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(TEXT_SHARE)
                .padding(
                    start = start,
                    bottom = PAGE_PAD + HERO_PILL_HEIGHT + 14.dp + if (segments) SEGMENTS_ROOM else 0.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Tone.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (facts.isNotBlank()) {
                Text(facts, style = MaterialTheme.typography.bodyMedium, color = Tone.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Details beside Play: a quiet pill with the info icon, filled under the remote. */
@Composable
private fun TvDetailsPill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val still = rememberReducedMotion() || Motion.reduced
    val scale by animateFloatAsState(if (focused && !still) Motion.FocusScale else 1f, Motion.standard(Motion.Fast), label = "detailsScale")
    val fill by animateColorAsState(if (focused) Tone.focusFill else Tone.surfaceHigh.copy(alpha = 0.8f), Motion.standard(Motion.Fast), label = "detailsFill")
    val ink = if (focused) Tone.onFocusFill else Tone.text
    Row(
        modifier
            .heightIn(min = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(fill)
            .focusRing(focused, CircleShape)
            .clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick)
            .padding(start = 16.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(TmIcons.Info, contentDescription = null, tint = ink, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = ink, maxLines = 1)
    }
}

/** The carousel's frame while the starred chats are still being asked: the same size, shimmering. */
@Composable
private fun TvFeaturedSkeleton(start: Dp, modifier: Modifier = Modifier) {
    val shimmer = rememberShimmer()
    Box(modifier) {
        SkeletonBox(shimmer, Modifier.fillMaxSize(), RectangleShape)
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = start, bottom = PAGE_PAD),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SkeletonBox(shimmer, Modifier.width(280.dp).height(28.dp), RoundedCornerShape(8.dp))
            SkeletonBox(shimmer, Modifier.width(180.dp).height(14.dp), RoundedCornerShape(7.dp))
            SkeletonBox(shimmer, Modifier.padding(top = 6.dp).width(160.dp).height(48.dp), CircleShape)
        }
    }
}

/** How tall the carousel is on a television under its heading: room below for the first row's titles. */
internal val TV_FEATURED_HEIGHT = 224.dp

/** How long a page is up before the carousel moves on. */
private const val ADVANCE_MS = 7_000

/** The inset of the words, the buttons and the segments from the picture's edge. */
private val PAGE_PAD = 24.dp

/** The share of the width the name and facts may take, over the start's fade. */
private const val TEXT_SHARE = 0.6f

/** The share of the width the segments span, over the start, clear of the picture's subject. */
private const val SEGMENTS_SHARE = 0.4f

/** Between the segments and Play, and what the name and facts climb by to make room for them. */
private val SEGMENTS_GAP = 4.dp
private val SEGMENTS_ROOM = 28.dp

/** How far below the heading its fade reaches, as a share of the heading's height. */
private const val HEADER_FADE_REACH = 1.8f

