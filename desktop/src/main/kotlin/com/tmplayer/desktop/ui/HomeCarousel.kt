package com.tmplayer.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tmplayer.data.FeaturedPick
import com.tmplayer.data.HomeFeatured
import com.tmplayer.data.HomeRow
import com.tmplayer.data.MediaItem
import com.tmplayer.data.Series
import com.tmplayer.data.SeriesShelf
import com.tmplayer.online.MetaConfidence
import com.tmplayer.online.MetaQuery
import com.tmplayer.online.OnlineMetadata
import com.tmplayer.ui.browse.HeroPill
import com.tmplayer.ui.browse.SeriesWatch
import com.tmplayer.ui.browse.seriesPlayLabel
import com.tmplayer.ui.components.ArtBackdrop
import com.tmplayer.ui.components.BackdropSource
import com.tmplayer.ui.components.SkeletonBox
import com.tmplayer.ui.components.pressScale
import com.tmplayer.ui.components.rememberCover
import com.tmplayer.ui.components.rememberShimmer
import com.tmplayer.ui.i18n.LocalStrings
import com.tmplayer.ui.icons.TmIcons
import com.tmplayer.ui.online.rememberMeta
import com.tmplayer.ui.theme.FloatingTone
import com.tmplayer.ui.theme.Motion
import com.tmplayer.ui.theme.Scrims
import com.tmplayer.ui.theme.Tone
import com.tmplayer.ui.theme.floatingBorder
import com.tmplayer.ui.theme.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The featured carousel at the top of Home, the phone's brought to the window: the newest five
 * different films, shows and videos in the starred chats ([HomeFeatured], the same picks as the
 * phone's), one wide page each, with the segmented line over the top that fills over [ADVANCE_MS]
 * and moves on. It takes Home's title's place: the page has no "Home" heading any more.
 *
 * A window is not a phone, so the words keep to the start, where they read against the picture's
 * fade, and a pointer gets what a thumb does not need: arrows at the sides while it hovers, and
 * Left and Right once the carousel has the keyboard. It holds still while the pointer is over it
 * or the keyboard is in it, and with reduced motion. A segment pressed goes to its page.
 *
 * It is as tall from its first frame as with its pages in, a shimmering stand-in while [picks] is
 * null, so nothing on Home moves when they land.
 */
@Composable
internal fun FeaturedCarousel(
    picks: List<FeaturedPick>?,
    height: Dp,
    side: Dp,
    watch: SeriesWatch,
    chatTitle: (Long) -> String,
    onPlay: (MediaItem) -> Unit,
    onDetails: (FeaturedPick) -> Unit,
    modifier: Modifier = Modifier,
    /** The page's own small buttons, at the top end corner over the picture. */
    actions: @Composable () -> Unit = {},
) {
    Box(modifier.fillMaxWidth().height(height)) {
        if (picks == null) {
            FeaturedSkeleton(side)
            return@Box
        }
        if (picks.isEmpty()) return@Box
        val s = LocalStrings.current
        val count = picks.size
        val latest by rememberUpdatedState(picks)
        var currentKey by remember { mutableStateOf(picks.first().key) }
        val pager = rememberPagerState(initialPage = picks.indexOfFirst { it.key == currentKey }.coerceAtLeast(0)) { count }
        LaunchedEffect(pager) {
            snapshotFlow { pager.settledPage }.collect { page -> latest.getOrNull(page)?.let { currentKey = it.key } }
        }
        LaunchedEffect(picks) {
            val wanted = picks.indexOfFirst { it.key == currentKey }
            if (wanted >= 0 && wanted != pager.currentPage) pager.scrollToPage(wanted)
        }

        val still = rememberReducedMotion() || Motion.reduced
        val hover = remember { MutableInteractionSource() }
        val hovered by hover.collectIsHoveredAsState()
        var focused by remember { mutableStateOf(false) }
        val auto = count > 1 && !still
        val paused = hovered || focused
        val progress = remember { Animatable(0f) }
        var progressPage by remember { mutableIntStateOf(-1) }
        val settled = pager.settledPage
        LaunchedEffect(settled, paused, auto, count) {
            if (progressPage != settled) {
                progress.snapTo(0f)
                progressPage = settled
            }
            if (!auto || paused) return@LaunchedEffect
            val left = (1f - progress.value).coerceIn(0f, 1f)
            progress.animateTo(1f, tween((ADVANCE_MS * left).toInt(), easing = LinearEasing))
            pager.animateScrollToPage((settled + 1) % count)
        }
        val scope = rememberCoroutineScope()
        val go: (Int) -> Unit = { page ->
            val target = (page + count) % count
            scope.launch { if (still) pager.scrollToPage(target) else pager.animateScrollToPage(target) }
        }

        Box(
            Modifier
                .fillMaxSize()
                .hoverable(hover)
                .onFocusChanged { focused = it.hasFocus }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || count < 2) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> {
                            go(pager.currentPage - 1)
                            true
                        }
                        Key.DirectionRight -> {
                            go(pager.currentPage + 1)
                            true
                        }
                        else -> false
                    }
                },
        ) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                key = { latest.getOrNull(it)?.key ?: it },
            ) { page ->
                val pick = latest.getOrNull(page) ?: return@HorizontalPager
                FeaturedPage(pick, height, side, watch, chatTitle, onPlay, onDetails)
            }
            if (count > 1) {
                Segments(
                    count = count,
                    current = pager.currentPage,
                    fill = { index ->
                        when {
                            index < pager.currentPage -> 1f
                            index > pager.currentPage -> 0f
                            !auto -> 1f
                            else -> progress.value
                        }
                    },
                    label = { index -> s.homeFeaturedPage(index + 1, count) },
                    onPick = go,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 18.dp, start = side)
                        .widthIn(max = SEGMENTS_WIDTH),
                )
                // The arrows, while the pointer is over the carousel: a mouse has no swipe.
                AnimatedVisibility(hovered, Modifier.align(Alignment.CenterStart).padding(start = 12.dp), enter = fadeIn(), exit = fadeOut()) {
                    ArrowButton(TmIcons.ArrowBack, s.homeRowScrollBack) { go(pager.currentPage - 1) }
                }
                AnimatedVisibility(hovered, Modifier.align(Alignment.CenterEnd).padding(end = 12.dp), enter = fadeIn(), exit = fadeOut()) {
                    ArrowButton(TmIcons.ArrowForward, s.homeRowScrollOn) { go(pager.currentPage + 1) }
                }
            }
            Box(Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = side)) { actions() }
        }
    }
}

/** One page: the picture under its fades, the name, a line of facts, Play and Details. */
@Composable
private fun FeaturedPage(
    pick: FeaturedPick,
    height: Dp,
    side: Dp,
    watch: SeriesWatch,
    chatTitle: (Long) -> String,
    onPlay: (MediaItem) -> Unit,
    onDetails: (FeaturedPick) -> Unit,
) {
    val s = LocalStrings.current
    val item = pick.item
    val series = pick.series
    val chat = chatTitle(item.chatId)
    val meta = rememberMeta(item, showOnly = series != null)
    val cover = rememberCover(item, chat)
    val source = remember(item, meta?.wideUrl, cover) { BackdropSource(item.miniThumbnail, item.thumbnailFileId, cover, meta?.wideUrl) }
    val title = remember(pick) { series?.title?.ifBlank { null } ?: SeriesShelf.tileTitle(item) }
    val facts = remember(pick, chat, s) { featuredFacts(pick, title, chat, s) }
    val showProgress = remember(series, watch) { series?.let { watch.progress(it) } }
    val episode = series?.let { showProgress?.next ?: it.episodes.firstOrNull() }
    val playItem = if (series != null && episode != null && showProgress != null) watch.pick(episode, showProgress) else item
    val playLabel = if (series != null && episode != null && showProgress != null) {
        seriesPlayLabel(episode.code, watch.pointOf(episode), showProgress.watched > 0)
    } else if ((watch.point(item)?.positionMs ?: 0L) > 0L) {
        s.commonResume
    } else {
        s.commonPlay
    }
    Box(
        Modifier
            .fillMaxSize()
            .semantics { contentDescription = listOf(title, facts).filter { it.isNotBlank() }.joinToString(". ") },
    ) {
        ArtBackdrop(source, Modifier.fillMaxSize())
        // Dark at the top under the segments, a fade from the start edge under the words, and
        // the foot fading into the page.
        Box(Modifier.fillMaxSize().background(Scrims.startLong(0.6f)))
        // Over the start fade too, so the white segments read in the light theme.
        Box(
            Modifier
                .fillMaxWidth()
                .height(TOP_SCRIM)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent))),
        )
        Box(Modifier.fillMaxSize().background(Scrims.bottomLong(height * FOOT_FADE_SHARE)))
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = side, end = side, bottom = 12.dp)
                .widthIn(max = WORDS_WIDTH),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold),
                color = Tone.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (facts.isNotBlank()) {
                Text(facts, style = MaterialTheme.typography.titleSmall, color = Tone.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(
                Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeroPill(label = playLabel, onClick = { onPlay(playItem) })
                RoundButton(TmIcons.Info, s.detailOpenDetails, size = com.tmplayer.ui.browse.HERO_PILL_HEIGHT) { onDetails(pick) }
            }
        }
    }
}

@Composable
private fun ArrowButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    RoundButton(icon, label, size = 44.dp, onClick = onClick)
}

/** A round button over the picture in the floating look: Details, the arrows. */
@Composable
private fun RoundButton(icon: ImageVector, label: String, size: Dp, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(size)
            .pressScale(press)
            .clip(CircleShape)
            .background(FloatingTone.menu.copy(alpha = 0.85f))
            .border(floatingBorder(), CircleShape)
            .clickable(interactionSource = press, indication = androidx.compose.foundation.LocalIndication.current, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Tone.text, modifier = Modifier.size(22.dp))
    }
}

/** The year when the name does not carry it, "Season 2" or "6 versions", the quality, the chat. */
private fun featuredFacts(pick: FeaturedPick, title: String, chat: String, s: com.tmplayer.i18n.Messages): String {
    val item = pick.item
    val series = pick.series
    val name = SeriesShelf.episodeOf(item)
    val year = name.year?.takeIf { series == null && it.toString() !in title }?.toString()
    val which = when {
        series != null -> series.seasons.maxOfOrNull { it.number }?.takeIf { it > 0 }?.let { s.seriesSeason(it) }
        item.versions.size > 1 -> s.detailVersions(item.versions.size)
        else -> null
    }
    return listOfNotNull(year, which, item.qualityTags.firstOrNull(), chat.ifBlank { null }).joinToString("  ·  ")
}

/** The line of segments, one per page, the one up filling as its time runs; a press goes to that page. */
@Composable
private fun Segments(
    count: Int,
    current: Int,
    fill: (Int) -> Float,
    label: (Int) -> String,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = Tone.onArt.copy(alpha = 0.35f)
    val ink = Tone.onArt
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SEGMENT_GAP)) {
        repeat(count) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(SEGMENT_TARGET)
                    .clickable(interactionSource = null, indication = null, role = Role.Tab) { onPick(index) }
                    .semantics {
                        contentDescription = label(index)
                        selected = index == current
                        role = Role.Tab
                    }
                    .drawBehind {
                        val line = SEGMENT_HEIGHT.toPx()
                        val top = (size.height - line) / 2f
                        val radius = CornerRadius(line / 2f, line / 2f)
                        drawRoundRect(track, topLeft = Offset(0f, top), size = Size(size.width, line), cornerRadius = radius)
                        val filled = fill(index).coerceIn(0f, 1f)
                        if (filled > 0f) drawRoundRect(ink, topLeft = Offset(0f, top), size = Size(size.width * filled, line), cornerRadius = radius)
                    },
            )
        }
    }
}

@Composable
private fun FeaturedSkeleton(side: Dp) {
    val shimmer = rememberShimmer()
    Box(Modifier.fillMaxSize()) {
        SkeletonBox(shimmer, Modifier.fillMaxSize(), RectangleShape)
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = side, bottom = 12.dp).widthIn(max = WORDS_WIDTH).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SkeletonBox(shimmer, Modifier.fillMaxWidth(0.7f).height(40.dp), RoundedCornerShape(8.dp))
            SkeletonBox(shimmer, Modifier.fillMaxWidth(0.45f).height(16.dp), RoundedCornerShape(8.dp))
            SkeletonBox(shimmer, Modifier.padding(top = 6.dp).fillMaxWidth(0.4f).height(com.tmplayer.ui.browse.HERO_PILL_HEIGHT), CircleShape)
        }
    }
}

/**
 * The carousel's pages from the Recently added row [recent]: null while that row is still being
 * asked for, empty for nothing to feature. Ranked once per fill, sharp online pictures first,
 * off the main thread since that reads the metadata cache.
 */
@Composable
internal fun rememberFeatured(recent: HomeRow.Recent?): List<FeaturedPick>? {
    val online = OnlineMetadata.current
    val base = remember(recent) { recent?.takeIf { it.loaded }?.let { HomeFeatured.pick(it.entries) } }
    var ranked by remember { mutableStateOf<List<FeaturedPick>?>(null) }
    LaunchedEffect(base, online) {
        val picks = base ?: return@LaunchedEffect
        ranked = if (picks.isEmpty()) picks else withContext(Dispatchers.IO) { HomeFeatured.rank(picks) { sharpOnline(it, online) } }
    }
    return ranked
}

/** Whether [pick] has a sharp online picture already on this device: see [HomeFeatured.rank]. */
private fun sharpOnline(pick: FeaturedPick, online: OnlineMetadata?): Boolean {
    online ?: return false
    val item = pick.item
    val query = MetaQuery.of(item.nameForParsing, item.caption)?.let { if (pick.series != null) it.showOnly() else it } ?: return false
    val info = runCatching { online.cachedMatch(query) }.getOrNull() ?: return false
    if (info.wideUrl == null) return false
    return MetaConfidence.basis(SeriesShelf.episodeOf(item), info) != null
}

/** How tall the carousel is in a page [width] by [height]: wide, never most of the window. */
internal fun featuredHeight(width: Dp, height: Dp): Dp =
    (width * 0.42f).coerceAtMost(height * 0.62f).coerceAtLeast(MIN_HEIGHT)

private const val ADVANCE_MS = 6_000
private const val FOOT_FADE_SHARE = 0.5f
private val MIN_HEIGHT = 340.dp
private val TOP_SCRIM = 96.dp
private val WORDS_WIDTH = 640.dp
private val SEGMENTS_WIDTH = 420.dp
private val SEGMENT_HEIGHT = 3.dp
private val SEGMENT_TARGET = 20.dp
private val SEGMENT_GAP = 4.dp
