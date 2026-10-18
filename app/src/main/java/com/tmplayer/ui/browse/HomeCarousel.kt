package com.tmplayer.ui.browse

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.core.view.WindowCompat
import com.tmplayer.data.FeaturedPick
import com.tmplayer.data.HomeFeatured
import com.tmplayer.data.MediaItem
import com.tmplayer.data.SeriesShelf
import com.tmplayer.online.MetaConfidence
import com.tmplayer.online.MetaQuery
import com.tmplayer.online.OnlineMetadata
import com.tmplayer.ui.components.ArtBackdrop
import com.tmplayer.ui.components.BackdropSource
import com.tmplayer.ui.components.SkeletonBox
import com.tmplayer.ui.components.rememberCover
import com.tmplayer.ui.components.rememberShimmer
import com.tmplayer.ui.i18n.LocalStrings
import com.tmplayer.ui.icons.TmIcons
import com.tmplayer.ui.online.rememberMeta
import com.tmplayer.ui.theme.LocalDarkTheme
import com.tmplayer.ui.theme.Motion
import com.tmplayer.ui.theme.Scrims
import com.tmplayer.ui.theme.Tone
import com.tmplayer.ui.theme.rememberReducedMotion
import kotlinx.coroutines.launch

/**
 * The phone's featured carousel at the top of Home: the newest five different films, shows and
 * videos in the starred chats ([HomeFeatured]), one full-bleed page each, swiped through. Only the
 * picture, the name and its facts move with a swipe: Play and Details stay put at the foot, acting
 * on the page up, with the story-style line of segments over them that fills over [ADVANCE_MS] and
 * moves on.
 *
 * It is [height] tall from its first frame, whatever has arrived: while [picks] is null (the
 * starred chats are still being asked) the same frame holds a shimmering stand-in, so nothing on
 * Home moves when the pages land. The pictures run up under the status bar ([under]).
 *
 * The pages move on by themselves only while nobody is touching them, Home is [active] (on screen,
 * with no sheet or dialog over it), motion is on, and TalkBack is off. Only the page up and its
 * neighbours are composed, so only they fetch pictures.
 */
@Composable
internal fun FeaturedCarousel(
    picks: List<FeaturedPick>?,
    height: Dp,
    under: Dp,
    start: Dp,
    end: Dp,
    watch: SeriesWatch,
    chatTitle: (Long) -> String,
    active: Boolean,
    onPlay: (MediaItem, String) -> Unit,
    onDetails: (FeaturedPick) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().height(height)) {
        if (picks.isNullOrEmpty()) {
            FeaturedSkeleton(start, end)
            return@Box
        }
        val s = LocalStrings.current
        val count = picks.size
        val latest by rememberUpdatedState(picks)
        // The page up, by key, so a carousel that refills stays on the page being looked at.
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
        val talkBack = rememberTouchExploration()
        val auto = count > 1 && !still && !talkBack
        var held by remember { mutableStateOf(false) }
        val dragged by pager.interactionSource.collectIsDraggedAsState()
        val paused = held || dragged || !active
        val progress = remember { Animatable(0f) }
        var progressPage by remember { mutableIntStateOf(-1) }
        val settled = pager.settledPage
        LaunchedEffect(settled, paused, auto, count) {
            // A new page starts its segment from empty; a pause keeps it where it stopped.
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

        Box(
            Modifier
                .fillMaxSize()
                // Watched without consuming anything, so the pager and the buttons get every touch.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        held = true
                        try {
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                            } while (event.changes.any { it.pressed })
                        } finally {
                            held = false
                        }
                    }
                },
        ) {
            HorizontalPager(
                state = pager,
                // Every sideways drag that starts on the carousel stays with it: one past the last
                // page does not carry on into the Home and Chats pager and turn Home over to Chats.
                modifier = Modifier.fillMaxSize().nestedScroll(KeepSideways),
                // The page up and its neighbours, and no further: only they fetch pictures.
                beyondViewportPageCount = 1,
                key = { latest.getOrNull(it)?.key ?: it },
            ) { page ->
                val pick = latest.getOrNull(page) ?: return@HorizontalPager
                FeaturedPage(pick, height, under, start, end, chatTitle, footRoom = controlsRoom(count > 1))
            }
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = start, end = end, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(CONTROLS_GAP),
            ) {
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
                    onPick = { index -> scope.launch { if (still) pager.scrollToPage(index) else pager.animateScrollToPage(index) } },
                )
              }
                latest.getOrNull(pager.currentPage)?.let { pick ->
                    FeaturedControls(pick, watch, chatTitle, onPlay, onDetails)
                }
            }
        }
    }
}

/** What the controls at the carousel's foot take, which a page keeps its name and facts clear of. */
private fun controlsRoom(segments: Boolean): Dp =
    4.dp + HERO_PILL_HEIGHT + if (segments) SEGMENT_TARGET + CONTROLS_GAP else 0.dp

/** One page: the picture under its fades, the name and a line of facts, clear of the controls ([footRoom]). */
@Composable
private fun FeaturedPage(
    pick: FeaturedPick,
    height: Dp,
    under: Dp,
    start: Dp,
    end: Dp,
    chatTitle: (Long) -> String,
    footRoom: Dp,
) {
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
            .semantics { contentDescription = listOf(title, facts).filter { it.isNotBlank() }.joinToString(". ") },
    ) {
        ArtBackdrop(source, Modifier.fillMaxSize())
        // The top darkened under the status bar, light in either theme.
        Box(
            Modifier
                .fillMaxWidth()
                .height(under + TOP_SCRIM)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent))),
        )
        Box(Modifier.fillMaxSize().background(Scrims.bottomLong(height * FOOT_FADE_SHARE)))
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = start, end = end, bottom = footRoom + TEXT_GAP),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Tone.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            if (facts.isNotBlank()) {
                Text(
                    facts,
                    style = MaterialTheme.typography.labelLarge,
                    color = Tone.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Play and Details at the carousel's foot, for the page up; they stay put while the pages move. */
@Composable
private fun FeaturedControls(
    pick: FeaturedPick,
    watch: SeriesWatch,
    chatTitle: (Long) -> String,
    onPlay: (MediaItem, String) -> Unit,
    onDetails: (FeaturedPick) -> Unit,
) {
    val s = LocalStrings.current
    val item = pick.item
    val series = pick.series

    // What Play starts: a show where the viewer is in it (the episode under way, else the next),
    // a film or a video as itself, the copy to play chosen on the way (SettingsStore.copyToPlay).
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

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeroPill(
            label = playLabel,
            onClick = { onPlay(playItem, chatTitle(playItem.chatId)) },
            stretch = true,
            modifier = Modifier.weight(1f, fill = false),
        )
        Box(
            Modifier
                .size(HERO_PILL_HEIGHT)
                .clip(CircleShape)
                .background(Tone.surfaceHigh.copy(alpha = 0.72f))
                .border(1.dp, Tone.outline, CircleShape)
                .clickable(role = Role.Button) { onDetails(pick) }
                .semantics { contentDescription = s.detailOpenDetails },
            contentAlignment = Alignment.Center,
        ) {
            Icon(TmIcons.Info, contentDescription = null, tint = Tone.text, modifier = Modifier.size(24.dp))
        }
    }
}

/**
 * A page's facts: the year (when the name does not already carry it), "Season 2" for a show or
 * "6 versions" for a film posted several times, the quality, and the chat it came from.
 */
internal fun featuredFacts(pick: FeaturedPick, title: String, chat: String, s: com.tmplayer.i18n.Messages): String {
    val item = pick.item
    val series = pick.series
    val name = SeriesShelf.episodeOf(item)
    val year = name.year?.takeIf { series == null && it.toString() !in title }?.toString()
    val which = when {
        series != null -> series.seasons.maxOfOrNull { it.number }?.takeIf { it > 0 }?.let { s.seriesSeason(it) }
        item.versions.size > 1 -> s.detailVersions(item.versions.size)
        else -> null
    }
    return listOfNotNull(year, which, item.qualityTags.firstOrNull(), chat.ifBlank { null }).joinToString(SEPARATOR)
}

/**
 * The line of segments over the top of the carousel, one per page: the pages before the one up
 * filled, the one up filling as its time runs ([fill] is read while drawing, so the run costs no
 * recomposition), the rest empty. Each is a page indicator TalkBack reads as "Page 2 of 5", and a
 * tap on one goes to its page; the touch target is 24 dp tall round a 3 dp line.
 */
@Composable
internal fun Segments(
    count: Int,
    current: Int,
    fill: (Int) -> Float,
    label: (Int) -> String,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** False on a television, where the remote reaches the pages by Left and Right instead. */
    focusable: Boolean = true,
) {
    // In the page's own ink: the segments sit low, where the picture has faded into the page,
    // and white on a light theme's pale foot all but vanished.
    val track = Tone.text.copy(alpha = 0.22f)
    val ink = Tone.text
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SEGMENT_GAP)) {
        repeat(count) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(SEGMENT_TARGET)
                    .focusProperties { canFocus = focusable }
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        role = Role.Tab,
                    ) { onPick(index) }
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
                        if (filled > 0f) {
                            drawRoundRect(ink, topLeft = Offset(0f, top), size = Size(size.width * filled, line), cornerRadius = radius)
                        }
                    },
            )
        }
    }
}

/** The carousel's frame while the starred chats are still being asked: the same size, shimmering. */
@Composable
private fun FeaturedSkeleton(start: Dp, end: Dp) {
    val shimmer = rememberShimmer()
    Box(Modifier.fillMaxSize()) {
        SkeletonBox(shimmer, Modifier.fillMaxSize(), RectangleShape)
        Box(Modifier.fillMaxSize().background(Scrims.bottomLong(SKELETON_FADE)))
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = start, end = end, bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SkeletonBox(shimmer, Modifier.fillMaxWidth(0.6f).height(30.dp), RoundedCornerShape(8.dp))
            SkeletonBox(shimmer, Modifier.fillMaxWidth(0.4f).height(14.dp), RoundedCornerShape(7.dp))
            SkeletonBox(shimmer, Modifier.padding(top = 4.dp).fillMaxWidth(0.7f).height(HERO_PILL_HEIGHT), CircleShape)
        }
    }
}

/**
 * How tall the carousel is in a window [width] wide and [height] tall (below the status bar):
 * width x 1.33, as a tall page's backdrop is, at most [VIEWPORT_SHARE] of the height so the first
 * row shows under it, and never so short that the name and Play have no room.
 */
internal fun featuredHeight(width: Dp, height: Dp): Dp =
    (width * 1.33f).coerceAtMost(height * VIEWPORT_SHARE).coerceAtLeast(min(MIN_HEIGHT, height * 0.9f))

/** Whether [pick] has a sharp online picture already on this device: see [HomeFeatured.rank]. Reads the disk. */
internal fun sharpOnline(pick: FeaturedPick, online: OnlineMetadata?): Boolean {
    online ?: return false
    val item = pick.item
    val query = MetaQuery.of(item.nameForParsing, item.caption)?.let { if (pick.series != null) it.showOnly() else it } ?: return false
    val info = runCatching { online.cachedMatch(query) }.getOrNull() ?: return false
    if (info.wideUrl == null) return false
    return MetaConfidence.basis(SeriesShelf.episodeOf(item), info) != null
}

/**
 * Light status bar icons while the carousel's picture is under the status bar, whatever the
 * theme; the theme's own otherwise, and again when Home leaves.
 */
@Composable
internal fun StatusIconsOverArt(overArt: Boolean) {
    val dark = LocalDarkTheme.current
    val window = LocalActivity.current?.window ?: return
    DisposableEffect(overArt, dark, window) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = !dark && !overArt
        onDispose { controller.isAppearanceLightStatusBars = !dark }
    }
}

/** Takes whatever sideways scroll and fling the carousel leaves over, so no pager outside it moves. */
private val KeepSideways = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = Offset(available.x, 0f)

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = Velocity(available.x, 0f)
}

/** How long a page is up before the carousel moves on. */
private const val ADVANCE_MS = 6_000

/** The most of the window under the status bar the carousel takes. */
private const val VIEWPORT_SHARE = 0.7f

private val MIN_HEIGHT = 320.dp

/** The share of the carousel's height its foot fades over into the page. */
private const val FOOT_FADE_SHARE = 0.55f

private val SKELETON_FADE = 160.dp

/** The dark fade under the status bar. */
private val TOP_SCRIM = 72.dp

/** Between the segments and Play, and between a page's facts and the segments. */
private val CONTROLS_GAP = 8.dp
private val TEXT_GAP = 10.dp

private val SEGMENT_HEIGHT = 3.dp
private val SEGMENT_TARGET = 24.dp
private val SEGMENT_GAP = 4.dp

private const val SEPARATOR = "  ·  "
