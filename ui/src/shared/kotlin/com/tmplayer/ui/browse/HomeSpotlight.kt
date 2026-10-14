package com.tmplayer.ui.browse

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.tmplayer.data.FeaturedPick
import com.tmplayer.data.HomeFeatured
import com.tmplayer.data.LessonSteps
import com.tmplayer.data.MediaItem
import com.tmplayer.data.ResumeRecord
import com.tmplayer.data.SeriesShelf
import com.tmplayer.data.SettingsStore
import com.tmplayer.data.Thumbnails
import com.tmplayer.i18n.Messages
import com.tmplayer.online.MetaInfo
import com.tmplayer.online.MetaQuery
import com.tmplayer.online.MetaResult
import com.tmplayer.online.OnlineMetadata
import com.tmplayer.ui.components.ArtBackdrop
import com.tmplayer.ui.components.BackdropSource
import com.tmplayer.ui.components.GeneratedCover
import com.tmplayer.ui.components.MediaArt
import com.tmplayer.ui.components.TileBadge
import com.tmplayer.ui.components.TileBadgeInset
import com.tmplayer.ui.components.isTouch
import com.tmplayer.ui.components.rememberCover
import com.tmplayer.ui.components.rememberLowMemory
import com.tmplayer.ui.i18n.LocalStrings
import com.tmplayer.ui.icons.TmIcons
import com.tmplayer.ui.online.OnlineArt
import com.tmplayer.ui.theme.Focus
import com.tmplayer.ui.theme.LocalDarkTheme
import com.tmplayer.ui.theme.Motion
import com.tmplayer.ui.theme.Scrims
import com.tmplayer.ui.theme.Tone
import com.tmplayer.ui.theme.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * One card Home's carousel leads with, and what it has to show for it.
 *
 * [record] is the viewer's saved place in [item], when there is one, so the card offers Resume.
 * [wideUrl] is the online backdrop when that is the picture that earned the card, else null and
 * the Telegram thumbnail is. [info] is what the providers know about it, for its proper name and
 * its overview, or null.
 */
@Immutable
class Spotlight(
    val item: MediaItem,
    val chatTitle: String,
    val record: ResumeRecord?,
    val info: MetaInfo?,
    val wideUrl: String?,
) {
    /** The card's key, the video's progress key, stable while the carousel refills. */
    val key: String get() = SettingsStore.progressKey(item.chatId, item.messageId)
}

/** Where working out the carousel stands. */
sealed interface SpotlightState {
    /** Still finding out whether any candidate has a picture: a television holds its first focus. */
    data object Pending : SpotlightState

    /** Nothing worth leading with: Home starts with its rows. */
    data object None : SpotlightState

    /** One card or more, best first, at most [HomeFeatured.LIMIT]. */
    data class Ready(val spotlights: List<Spotlight>) : SpotlightState
}

/**
 * Home's featured cards: [featured] (the starred chats' films and shows, see [HomeFeatured]) that
 * have a picture worth showing large, at most [HomeFeatured.LIMIT] of them; when none has, the
 * first of [fallback] (the films and shows in progress, see [com.tmplayer.data.HomeRows.spotlight])
 * that does, as one card; else none.
 *
 * A picture worth showing large is an online backdrop at least [SHARP_ONLINE] pixels across (with
 * posters and overviews on), or a Telegram thumbnail at least [SHARP_THUMBNAIL] across. An inline
 * preview alone never is, and neither is a generated cover: Home would rather open on its rows
 * than on a smear or a colour field passed off as a feature.
 *
 * [art] is the Continue watching videos as Telegram describes them, which is where a record's
 * thumbnail comes from; each fallback's is asked for through [onArtWanted]. Until they answer, or
 * [ART_WAIT_MS] passes, the answer is [SpotlightState.Pending] rather than None, so a television
 * does not settle its first focus on a row a moment before the hero arrives above it.
 *
 * The candidates are looked at one at a time, and the first card is shown as soon as it is found
 * while the rest are still being looked at. On a device short of memory only the thumbnail counts,
 * and it is drawn as its soft field: no 1280 pixel decode on a 1 GB stick.
 */
@Composable
fun rememberSpotlights(
    featured: List<FeaturedPick>,
    fallback: List<ResumeRecord>,
    art: Map<String, MediaItem>,
    onArtWanted: (ResumeRecord) -> Unit,
    chatTitle: (Long) -> String = { "" },
): SpotlightState {
    LaunchedEffect(fallback) { fallback.forEach(onArtWanted) }
    val keys = remember(fallback) { fallback.map { SettingsStore.progressKey(it.chatId, it.messageId) } }
    val backups = remember(fallback, art) {
        fallback.mapIndexed { index, record ->
            FeaturedPick(art[keys[index]] ?: record.toMediaItem(), record.chatTitle.ifEmpty { chatTitle(record.chatId) }, record)
        }
    }
    var waited by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(ART_WAIT_MS)
        waited = true
    }
    val answered = waited || keys.all { it in art }
    val online = OnlineMetadata.current
    val metaOn = online?.settings?.collectAsState()?.value?.enabled == true
    val lowMemory = rememberLowMemory()
    // Keyed on what decides the answer, not on the whole art map: another tile's picture
    // arriving must not start the search over.
    val inputs = featured.map { it.item.id to it.item.thumbnailFileId } + backups.map { it.item.id to it.item.thumbnailFileId }
    val state by produceState<SpotlightState>(SpotlightState.Pending, inputs, answered, metaOn, lowMemory) {
        val provider = if (metaOn) online else null
        val firstAnswer = value !is SpotlightState.Ready
        val found = ArrayList<Spotlight>()
        for (pick in featured) {
            if (found.size >= HomeFeatured.LIMIT) break
            val (info, url) = findArt(pick.item, provider, lowMemory) ?: continue
            found += Spotlight(pick.item, pick.chatTitle, pick.record, info, url)
            // The first card goes up at once; a carousel already showing waits for the whole answer.
            if (firstAnswer) value = SpotlightState.Ready(found.toList())
        }
        if (found.isNotEmpty()) {
            value = SpotlightState.Ready(found)
            return@produceState
        }
        for (pick in backups) {
            val (info, url) = findArt(pick.item, provider, lowMemory) ?: continue
            value = SpotlightState.Ready(listOf(Spotlight(pick.item, pick.chatTitle, pick.record, info, url)))
            return@produceState
        }
        value = if (answered) SpotlightState.None else SpotlightState.Pending
    }
    return state
}

/**
 * Whether [item] has art worth a hero, and which: the providers' answer and the backdrop's
 * address when that is it, the answer and null when the thumbnail is. Null for neither.
 */
private suspend fun findArt(item: MediaItem, online: OnlineMetadata?, lowMemory: Boolean): Pair<MetaInfo?, String?>? {
    val info = online?.let { provider ->
        val query = MetaQuery.of(item.nameForParsing, item.caption)?.showOnly() ?: return@let null
        withContext(Dispatchers.IO) {
            (runCatching { provider.lookup(query) }.getOrNull() as? MetaResult.Found)?.info
        }
    }
    val url = info?.backdropUrl
    if (online != null && url != null && !lowMemory) {
        val wide = Thumbnails.peekOnline(url, ONLINE_WIDTH)
            ?: Thumbnails.online(url, ONLINE_WIDTH) { online.image(url) }
        if (wide != null && wide.width >= SHARP_ONLINE) return info to url
    }
    if (item.thumbnailFileId <= 0) return null
    val thumbnail = Thumbnails.peekFull(item.thumbnailFileId) ?: Thumbnails.full(item.thumbnailFileId)
    return if (thumbnail != null && thumbnail.width >= SHARP_THUMBNAIL) info to null else null
}

/** Puts [spotlight]'s large picture in the cache ahead of its turn, so the cross fade has it ready. */
private suspend fun prefetch(spotlight: Spotlight, online: OnlineMetadata?) {
    val url = spotlight.wideUrl
    if (url != null && online != null) {
        if (Thumbnails.peekOnline(url, ONLINE_WIDTH) == null) Thumbnails.online(url, ONLINE_WIDTH) { online.image(url) }
    } else if (spotlight.item.thumbnailFileId > 0 && Thumbnails.peekFull(spotlight.item.thumbnailFileId) == null) {
        Thumbnails.full(spotlight.item.thumbnailFileId)
    }
}

/** How the carousel is driven, which decides how it pages. */
enum class CarouselInput {
    /** A finger: the cards swipe in a pager, and a swipe stops the auto advance for the visit. */
    Touch,

    /**
     * A remote: Left from the pill goes to the card before and Right from the last button to the
     * card after; Down goes into the rows. The buttons stay put while the card changes, so focus
     * stays where it was.
     */
    Remote,

    /** A mouse and a keyboard: arrow buttons on hover, and Left and Right page while focus is in it. */
    Pointer,
}

/**
 * Home's hero: a carousel of the films and shows in the starred chats (see [rememberSpotlights]),
 * one card at a time, full bleed under the page header.
 *
 * Two arrangements. [wide] (a television, the desktop, a phone on its side) puts the picture on
 * the end [SPOTLIGHT_BACKDROP_SHARE] of the width under a start fade and a foot fade, with the
 * words in a column on the start side. Otherwise (a phone upright) the picture fills the width and
 * the words sit centred at its foot, under the long fade. Either way the words are TMPlayer's: the
 * name (the providers' when they know it), a line of facts with the episode or the year, the chat
 * it came from in the accent, the quality and the size, and the providers' overview when there is
 * one. Then one pill, Play or Resume, a round Details button and a round Watch later toggle. No
 * rating, no "featured": every card is from a chat the viewer starred.
 *
 * The cards change by a cross fade of the backdrop, which is never more than the one picture plus
 * the next one fetched ahead; the dots under the words say where in the carousel it is. It moves
 * on by itself every [Motion.HeroAdvanceMs], except under a pointer, with focus in it, after a
 * swipe, with motion turned off, or on a device short of memory. See [CarouselInput] for paging.
 *
 * [resumeFocus] is the pill's, for a television to open Home on it.
 *
 * [under] is how far the picture reaches up behind a page header drawn over the top of it (the
 * page's title and buttons, or a phone's app bar): the hero is that much taller, the picture runs
 * to the top edge, and the words stay where they were, under the header. The header stands on
 * [Scrims.headerTop] meanwhile (see [overHero]). Zero leaves the hero under its header as before.
 */
@Composable
fun HomeCarousel(
    spotlights: List<Spotlight>,
    wide: Boolean,
    height: Dp,
    start: Dp,
    end: Dp,
    input: CarouselInput,
    onPlay: (Spotlight) -> Unit,
    onDetails: (Spotlight) -> Unit,
    modifier: Modifier = Modifier,
    inWatchLater: (Spotlight) -> Boolean = { false },
    onToggleWatchLater: ((Spotlight) -> Unit)? = null,
    resumeFocus: FocusRequester? = null,
    under: Dp = 0.dp,
    /**
     * How much of the words, buttons and dots to show, read while drawing: the pane fades them as
     * the hero scrolls up under a header drawn over it, so the card's name never runs into the
     * page's own title. The picture stays, under the header's scrim.
     */
    wordsAlpha: () -> Float = { 1f },
) {
    if (spotlights.isEmpty()) return
    val s = LocalStrings.current
    val count = spotlights.size
    val lowMemory = rememberLowMemory()
    val still = rememberReducedMotion()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val online = OnlineMetadata.current

    // Which card is up, by key, so a carousel that refills keeps the card the viewer is on.
    var currentKey by remember { mutableStateOf(spotlights.first().key) }
    val index = spotlights.indexOfFirst { it.key == currentKey }.takeIf { it >= 0 } ?: 0
    val pager = if (input == CarouselInput.Touch) rememberPagerState(initialPage = index) { count } else null
    var advanced by remember { mutableStateOf(false) }
    var stopped by remember { mutableStateOf(false) }
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    var focusedInside by remember { mutableStateOf(false) }

    if (pager != null) {
        LaunchedEffect(pager, spotlights) {
            snapshotFlow { pager.currentPage }.collect { page -> spotlights.getOrNull(page)?.let { currentKey = it.key } }
        }
        LaunchedEffect(spotlights) {
            val wanted = spotlights.indexOfFirst { it.key == currentKey }
            if (wanted >= 0 && wanted != pager.currentPage) pager.scrollToPage(wanted)
        }
        LaunchedEffect(pager) {
            pager.interactionSource.interactions.collect { if (it is DragInteraction.Start || it is PressInteraction.Press) stopped = true }
        }
    }
    val go: (Int) -> Unit = { to ->
        val target = ((to % count) + count) % count
        currentKey = spotlights[target].key
    }
    LaunchedEffect(pager, index) {
        if (pager != null && pager.currentPage != index && !pager.isScrollInProgress) {
            if (still) pager.scrollToPage(index) else pager.animateScrollToPage(index)
        }
    }

    // On by itself, slowly, and only while nobody is looking at it closely.
    LaunchedEffect(index, count, hovered, focusedInside, stopped, still, lowMemory) {
        if (count < 2 || hovered || focusedInside || stopped || still || lowMemory) return@LaunchedEffect
        delay(if (advanced) Motion.HeroAdvanceMs else Motion.HeroFirstAdvanceMs)
        advanced = true
        go(index + 1)
    }
    // The next card's picture, fetched while this one is up; never on a device short of memory.
    val next = spotlights[(index + 1) % count]
    LaunchedEffect(next.key, lowMemory) {
        if (count < 2 || lowMemory) return@LaunchedEffect
        delay(PREFETCH_DELAY_MS)
        runCatching { prefetch(next, online) }
    }

    val current = spotlights[index]
    val forward = if (rtl) Key.DirectionLeft else Key.DirectionRight
    val back = if (rtl) Key.DirectionRight else Key.DirectionLeft
    val pointerKeys = if (input == CarouselInput.Pointer && count > 1) {
        Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                forward -> { go(index + 1); true }
                back -> { go(index - 1); true }
                else -> false
            }
        }
    } else {
        Modifier
    }
    // On a remote the buttons keep Left and Right among themselves, and only past the ends page.
    val pillKeys = if (input == CarouselInput.Remote && count > 1) {
        Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown || event.key != back || index == 0) return@onPreviewKeyEvent false
            go(index - 1)
            true
        }
    } else {
        Modifier
    }
    val lastKeys = if (input == CarouselInput.Remote && count > 1) {
        Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown || event.key != forward) return@onPreviewKeyEvent false
            go(index + 1)
            true
        }
    } else {
        Modifier
    }

    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = height + under)
            .hoverable(hover)
            .onFocusChanged { focusedInside = it.hasFocus }
            .then(pointerKeys),
    ) {
        Box(Modifier.matchParentSize()) {
            Crossfade(
                targetState = current,
                animationSpec = if (still) snap() else Motion.standard(Motion.Slow),
                label = "carouselBackdrop",
            ) { card ->
                SpotlightBackdrop(card, wide, under, lowMemory)
            }
        }
        val dotsRoom = if (count > 1) DOTS_ROOM else 0.dp
        Box(Modifier.matchParentSize().graphicsLayer { alpha = wordsAlpha() }) {
        if (pager != null) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxWidth().heightIn(min = height + under),
                key = { spotlights.getOrNull(it)?.key ?: it },
                beyondViewportPageCount = 0,
            ) { page ->
                val card = spotlights[page]
                Box(Modifier.fillMaxWidth().heightIn(min = height + under)) {
                    CardColumn(wide, start, end, dotsRoom) {
                        SpotlightText(card, wide)
                        SpotlightActions(card, wide, onPlay, onDetails, inWatchLater, onToggleWatchLater, null, Modifier, Modifier)
                    }
                }
            }
        } else {
            CardColumn(wide, start, end, dotsRoom) {
                Crossfade(
                    targetState = current,
                    animationSpec = if (still) snap() else Motion.standard(Motion.Normal),
                    label = "carouselWords",
                ) { card ->
                    Column(
                        horizontalAlignment = if (wide) Alignment.Start else Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) { SpotlightText(card, wide) }
                }
                // The buttons stay the same composables from card to card, so focus stays on them.
                SpotlightActions(current, wide, onPlay, onDetails, inWatchLater, onToggleWatchLater, resumeFocus, pillKeys, lastKeys)
            }
        }
        if (count > 1) {
            CarouselDots(
                count = count,
                current = index,
                label = s.homeSpotlightPosition(index + 1, count),
                modifier = Modifier
                    .align(if (wide) Alignment.BottomStart else Alignment.BottomCenter)
                    .padding(start = if (wide) start else 0.dp, bottom = if (wide) 14.dp else 4.dp),
            )
        }
        }
        if (input == CarouselInput.Pointer && count > 1) {
            val shown by animateFloatAsState(if (hovered) 1f else 0f, Motion.standard(Motion.Fast), label = "carouselArrows")
            if (shown > 0f) {
                Row(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = end, bottom = 20.dp)
                        .graphicsLayer { alpha = shown },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ArrowButton(TmIcons.ChevronLeft, s.homeSpotlightPrevious) { go(index - 1) }
                    ArrowButton(TmIcons.ChevronRight, s.homeSpotlightNext) { go(index + 1) }
                }
            }
        }
    }
}

/** One card's picture, under its fades: see [HomeCarousel] for the two arrangements. */
@Composable
private fun SpotlightBackdrop(spotlight: Spotlight, wide: Boolean, under: Dp, lowMemory: Boolean) {
    val item = spotlight.item
    val cover = rememberCover(item, spotlight.chatTitle)
    val source = remember(item, spotlight.wideUrl, cover) {
        BackdropSource(item.miniThumbnail, item.thumbnailFileId, cover, spotlight.wideUrl)
    }
    Box(Modifier.fillMaxSize()) {
        if (wide) {
            Box(Modifier.align(Alignment.TopEnd).fillMaxWidth(SPOTLIGHT_BACKDROP_SHARE).fillMaxHeight()) {
                ArtBackdrop(source, Modifier.fillMaxSize(), fieldOnly = lowMemory)
                Box(Modifier.fillMaxSize().background(Scrims.startLong()))
                // A short fade at the top too, so the picture does not start on a hard line under
                // the header; none where the picture runs on up behind the header instead.
                if (under == 0.dp) {
                    Box(
                        Modifier.fillMaxWidth().height(TOP_FADE)
                            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Tone.scrim, Tone.scrim.copy(alpha = 0f)))),
                    )
                }
                Box(Modifier.fillMaxSize().background(Scrims.bottomLong(WIDE_FOOT_FADE)))
            }
        } else {
            ArtBackdrop(source, Modifier.fillMaxSize(), fieldOnly = lowMemory)
            Box(Modifier.fillMaxSize().background(Scrims.bottomLong(TALL_FOOT_FADE)))
        }
    }
}

/** Where a card's words and buttons stand: the start column of a wide hero, the foot of a tall one. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.CardColumn(
    wide: Boolean,
    start: Dp,
    end: Dp,
    dotsRoom: Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .align(if (wide) Alignment.BottomStart else Alignment.BottomCenter)
            .then(if (wide) Modifier.fillMaxWidth(TEXT_SHARE).widthIn(max = TEXT_MAX) else Modifier.fillMaxWidth())
            .padding(start = start, end = if (wide) 0.dp else end, top = 24.dp, bottom = (if (wide) 20.dp else 8.dp) + dotsRoom),
        horizontalAlignment = if (wide) Alignment.Start else Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/** A card's name, its line of facts and, when the providers have one, its overview. */
@Composable
private fun SpotlightText(spotlight: Spotlight, wide: Boolean) {
    val s = LocalStrings.current
    val words = remember(spotlight, s) { SpotlightWords.of(spotlight.item, spotlight.info, spotlight.record, s) }
    val textAlign = if (wide) TextAlign.Start else TextAlign.Center
    val accent = Tone.accent
    Text(
        words.title,
        style = MaterialTheme.typography.displaySmall,
        color = Tone.text,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
    )
    val chat = spotlight.chatTitle.takeIf { it.isNotBlank() }
    val facts = remember(words, chat, accent) {
        buildAnnotatedString {
            val parts = listOfNotNull(words.lead.takeIf { it.isNotBlank() }, chat, words.file.takeIf { it.isNotBlank() })
            parts.forEachIndexed { i, part ->
                if (i > 0) append(SEPARATOR)
                // The chat it came from in the accent: what TMPlayer always says first about a video.
                if (part === chat) withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append(part) } else append(part)
            }
        }
    }
    if (facts.isNotEmpty()) {
        Text(
            facts,
            style = MaterialTheme.typography.labelLarge,
            color = Tone.muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
        )
    }
    val overview = spotlight.info?.overview.orEmpty()
    if (overview.isNotBlank()) {
        Text(
            overview,
            style = MaterialTheme.typography.bodyMedium,
            color = Tone.muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
        )
    }
}

/** Play or Resume as the pill, then Details and the Watch later toggle as round buttons. */
@Composable
private fun SpotlightActions(
    spotlight: Spotlight,
    wide: Boolean,
    onPlay: (Spotlight) -> Unit,
    onDetails: (Spotlight) -> Unit,
    inWatchLater: (Spotlight) -> Boolean,
    onToggleWatchLater: ((Spotlight) -> Unit)?,
    pillFocus: FocusRequester?,
    pillKeys: Modifier,
    lastKeys: Modifier,
) {
    val s = LocalStrings.current
    Row(
        Modifier.padding(top = 10.dp).then(if (wide) Modifier else Modifier.fillMaxWidth()),
        horizontalArrangement = Arrangement.spacedBy(12.dp, if (wide) Alignment.Start else Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val resume = (spotlight.record?.positionMs ?: 0L) > 0L
        HeroPill(
            label = if (resume) s.commonResume else s.commonPlay,
            onClick = { onPlay(spotlight) },
            stretch = !wide,
            focus = pillFocus,
            modifier = pillKeys.then(if (wide) Modifier else Modifier.weight(1f, fill = false)),
        )
        HeroRound(TmIcons.Info, s.detailOpenDetails, { onDetails(spotlight) }, if (onToggleWatchLater == null) lastKeys else Modifier)
        if (onToggleWatchLater != null) {
            val saved = inWatchLater(spotlight)
            HeroRound(
                if (saved) TmIcons.Check else TmIcons.Clock,
                if (saved) s.detailWatchLaterRemove else s.detailWatchLaterAdd,
                { onToggleWatchLater(spotlight) },
                lastKeys,
            )
        }
    }
}

/** Where in the carousel it is: a dot per card, the current one drawn long. */
@Composable
private fun CarouselDots(count: Int, current: Int, label: String, modifier: Modifier = Modifier) {
    val still = rememberReducedMotion()
    Row(modifier.semantics { contentDescription = label }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            val width by animateDpAsState(
                if (i == current) DOT_ACTIVE else DOT,
                if (still) snap() else Motion.standard(Motion.Normal),
                label = "carouselDot",
            )
            Box(
                Modifier
                    .size(width, DOT)
                    .clip(CircleShape)
                    .background(if (i == current) Tone.text else Tone.text.copy(alpha = 0.35f)),
            )
        }
    }
}

/** A round arrow on a hovered desktop carousel. Pointer only: the keyboard has Left and Right. */
@Composable
private fun ArrowButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    Box(
        Modifier
            .size(ARROW)
            .focusProperties { canFocus = false }
            .clip(CircleShape)
            .background(if (hovered) Tone.surfaceHigh else Tone.surfaceHigh.copy(alpha = 0.72f))
            .border(1.dp, Tone.outline, CircleShape)
            .hoverable(interactions)
            .clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Tone.text, modifier = Modifier.size(24.dp))
    }
}

/** The key Home's lists give the spotlight's item, which [rememberHeroUnderHeader] looks for. */
const val SPOTLIGHT_KEY = "spotlight"

/**
 * Whether Home's hero, the first item of [list] under [SPOTLIGHT_KEY], still reaches down past a
 * header [header] tall drawn over the top of the list. While it does, the header stands on the
 * picture ([overHero] true); once the hero has scrolled up under it, or there is none ([hero]
 * false), the header goes back to its own look.
 *
 * A hero that has just arrived over a list still at its top counts before the list has laid it
 * out, so the header starts on the picture rather than fading over to it.
 */
@Composable
fun rememberHeroUnderHeader(list: LazyListState, header: Dp, hero: Boolean): State<Boolean> {
    val reach = with(LocalDensity.current) { header.roundToPx() }
    return remember(list, reach, hero) {
        derivedStateOf {
            if (!hero) return@derivedStateOf false
            val first = list.layoutInfo.visibleItemsInfo.firstOrNull() ?: return@derivedStateOf true
            if (first.key == SPOTLIGHT_KEY) first.offset + first.size > reach
            else list.layoutInfo.visibleItemsInfo.none { it.key == SPOTLIGHT_KEY } && list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0
        }
    }
}

/**
 * The ground of a page header drawn over Home's hero ([HomeCarousel]'s `under`). Over the
 * picture it is [Scrims.headerTop], reaching [Scrims.HEADER_REACH] times the header's height
 * down, and the header's words and icons are to be [Tone.onArt] in either theme; otherwise it is
 * [solid], the page's own background by default, so the rows scrolling up under the header do not
 * show through it.
 * The two cross fade.
 */
@Composable
fun Modifier.overHero(overArt: Boolean, solid: Color = Tone.background): Modifier {
    val art by animateFloatAsState(if (overArt) 1f else 0f, Motion.standard(Motion.Fast), label = "headerOverHero")
    return drawBehind {
        if (art < 1f) drawRect(solid, alpha = 1f - art)
        if (art > 0f) {
            val reach = size.height * Scrims.HEADER_REACH
            drawRect(Scrims.headerTop(reach), size = Size(size.width, reach), alpha = art)
        }
    }
}


/**
 * The words a carousel card writes, worked out once per video: its [title], the [lead] of its
 * facts (the episode or the year, and the time left when it has a saved place) and the [file]
 * facts that close them (its quality and its size). The chat's name goes between the two.
 */
internal class SpotlightWords(val title: String, val lead: String, val file: String) {
    companion object {
        fun of(item: MediaItem, info: MetaInfo?, record: ResumeRecord?, s: Messages): SpotlightWords {
            val parsed = SeriesShelf.episodeOf(item)
            val episode = parsed.episode?.let { number ->
                parsed.season?.let { s.seriesEpisodeShort(it, number) } ?: s.seriesEpisode(number)
            }
            val title = info?.title?.takeIf { it.isNotBlank() }
                ?: if (episode != null) parsed.title.ifBlank { SeriesShelf.tileTitle(item) } else SeriesShelf.posterTitle(item)
            val left = record?.remainingMs?.takeIf { it > 0 && record.positionMs > 0 }?.let { s.browseTimeLeft(s.formatter.duration(it / 1000)) }
            val lead = listOfNotNull(
                episode,
                parsed.year?.takeIf { episode == null }?.toString(),
                left,
            ).joinToString(SEPARATOR)
            val file = listOfNotNull(
                item.qualityTags.firstOrNull(),
                item.sizeBytes.takeIf { it > 0 }?.let { s.formatter.size(it) },
            ).joinToString(SEPARATOR)
            return SpotlightWords(title, lead, file)
        }
    }
}

/** Between the facts on a card. */
private const val SEPARATOR = "  ·  "


/**
 * The spotlight's one bright action: a pill in [Tone.hero], near white on dark and near black on
 * light, [HERO_PILL_HEIGHT] tall and at most [HERO_PILL_MAX] wide. Under the remote or the
 * keyboard it changes three ways at once: the fill goes to [Tone.focusFill], a [Focus.Ring]
 * goes round it, and it grows by [Motion.FocusScale] (unless motion is off).
 */
@Composable
fun HeroPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    stretch: Boolean = false,
    focus: FocusRequester? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = TmIcons.Play,
) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val hovered by interactions.collectIsHoveredAsState()
    val still = rememberReducedMotion()
    val scale by animateFloatAsState(
        if (focused && !still) Motion.FocusScale else 1f,
        Motion.standard(Motion.Fast),
        label = "heroPillScale",
    )
    val fill by animateColorAsState(
        if (focused) Tone.focusFill else if (hovered) Tone.hero.copy(alpha = 0.88f) else Tone.hero,
        Motion.standard(Motion.Fast),
        label = "heroPillFill",
    )
    val ink = if (focused) Tone.onFocusFill else Tone.onHero
    Row(
        modifier
            .then(if (stretch) Modifier.widthIn(max = HERO_PILL_MAX).fillMaxWidth() else Modifier.widthIn(min = 160.dp, max = HERO_PILL_MAX))
            .heightIn(min = HERO_PILL_HEIGHT)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .clip(CircleShape)
            .background(fill)
            .border(Focus.Ring, if (focused) Tone.accent else Color.Transparent, CircleShape)
            .clickable(interactionSource = interactions, indication = if (isTouch()) androidx.compose.foundation.LocalIndication.current else null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(26.dp).padding(end = 2.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(start = if (icon != null) 6.dp else 0.dp),
        )
    }
}


/** The quiet round buttons beside the pill: an outline at rest, the focus fill and ring under focus. */
@Composable
private fun HeroRound(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val still = rememberReducedMotion()
    val scale by animateFloatAsState(
        if (focused && !still) Motion.FocusScale else 1f,
        Motion.standard(Motion.Fast),
        label = "roundActionScale",
    )
    Box(
        modifier
            .size(HERO_PILL_HEIGHT)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(if (focused) Tone.focusFill else Tone.surfaceHigh.copy(alpha = 0.72f))
            .border(if (focused) Focus.Ring else 1.dp, if (focused) Tone.accent else Tone.outline, CircleShape)
            .clickable(interactionSource = interactions, indication = if (isTouch()) androidx.compose.foundation.LocalIndication.current else null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (focused) Tone.onFocusFill else Tone.text,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** The carousel's dots: [DOT] round, the current one [DOT_ACTIVE] long. */
private val DOT = 6.dp
private val DOT_ACTIVE = 24.dp

/** Room kept under a card's buttons for the dots. */
private val DOTS_ROOM = 18.dp

/** The desktop's arrow buttons. */
private val ARROW = 40.dp

/** How long a card is up before the next one's picture is fetched: a fast swipe through fetches nothing. */
private const val PREFETCH_DELAY_MS = 1_200L


/**
 * A Continue watching card's art: the picture (or the generated cover), the tile's foot fade,
 * which episode or part in small type and the name under it, written on the art at the bottom
 * start; how long is left in a chip at the top end; and a pill of progress across the foot.
 */
@Composable
fun ContinueCardArt(record: ResumeRecord, item: MediaItem, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val cover = rememberCover(item)
    val words = remember(item) { ContinueWords.of(item, s) }
    Box(modifier) {
        OnlineArt(item, Modifier.fillMaxSize()) {
            MediaArt(item.miniThumbnail, item.thumbnailFileId, Modifier.fillMaxSize()) {
                GeneratedCover(cover, Modifier.fillMaxSize(), showTitle = false)
            }
        }
        Box(Modifier.fillMaxSize().background(Scrims.tileFoot))
        if (record.remainingMs > 0) {
            TileBadge(
                s.browseTimeLeft(s.formatter.duration(record.remainingMs / 1000)),
                Modifier.align(Alignment.TopEnd).padding(TileBadgeInset),
            )
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
        ) {
            words.label?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Tone.onArtMuted,
                    maxLines = 1,
                )
            }
            Text(
                words.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Tone.onArt,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            PillProgress(record.fraction, Modifier.padding(top = 8.dp))
        }
    }
}

/** What a continue card writes on its art: "S1 E3", "Part 4" or nothing, then the name. */
internal class ContinueWords(val label: String?, val title: String) {
    companion object {
        fun of(item: MediaItem, s: Messages): ContinueWords {
            val parsed = SeriesShelf.episodeOf(item)
            val episode = parsed.episode?.let { number ->
                parsed.season?.let { s.seriesEpisodeShort(it, number) } ?: s.seriesEpisode(number)
            }
            if (episode != null) {
                val name = parsed.title.ifBlank { SeriesShelf.tileTitle(item) }
                // A file named by its episode alone ("Episode 1") is not given the label twice.
                return if (name.isBlank() || episode.startsWith(name, ignoreCase = true)) ContinueWords(null, episode) else ContinueWords(episode, name)
            }
            val lesson = LessonSteps.lessonOf(item)
            if (lesson != null) return ContinueWords(lesson.code, readable(lesson.course).ifBlank { SeriesShelf.tileTitle(item) })
            return ContinueWords(null, if (SeriesShelf.isFilm(item)) SeriesShelf.posterTitle(item) else SeriesShelf.tileTitle(item))
        }
    }
}

/**
 * How far through: a [PILL_PROGRESS] high pill in the accent on a [Tone.track], rounded at both
 * ends. Grows from the start edge, so from the right in a right to left layout.
 */
@Composable
fun PillProgress(fraction: Float, modifier: Modifier = Modifier) {
    val track = Tone.track
    val fill = if (LocalDarkTheme.current) Tone.accent else MaterialTheme.colorScheme.inversePrimary
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val done = fraction.coerceIn(0f, 1f)
    Canvas(modifier.fillMaxWidth().height(PILL_PROGRESS)) {
        val radius = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(track, cornerRadius = radius)
        val width = (size.width * done).coerceAtLeast(if (done > 0f) size.height else 0f)
        if (width > 0f) {
            drawRoundRect(
                fill,
                topLeft = Offset(if (rtl) size.width - width else 0f, 0f),
                size = Size(width, size.height),
                cornerRadius = radius,
            )
        }
    }
}

/** The pill of progress on a continue card: 3 dp, as Nuvio measures it. */
private val PILL_PROGRESS = 3.dp

/** The hero's action: as tall as a thumb wants and a remote can find. */
val HERO_PILL_HEIGHT = 52.dp

/** On a tablet or a phone on its side, a full-width pill would be a bar; this is its widest. */
private val HERO_PILL_MAX = 420.dp

/** How much of a wide hero's width the picture takes, from the end edge. */
private const val SPOTLIGHT_BACKDROP_SHARE = 0.72f

/** How much of a wide hero's width the words may take, from the start edge. */
private const val TEXT_SHARE = 0.45f

/** The words never run wider than this, however wide the window: past it they cross the picture. */
private val TEXT_MAX = 600.dp

/** The fade over the top edge of a wide hero's picture. */
private val TOP_FADE = 48.dp

/**
 * A course's name as a file name writes it ("small-shelf"), read as words ("Small shelf"):
 * separators become spaces, and a name in lower case alone gets its first letter raised.
 */
internal fun readable(name: String): String {
    val words = name.replace(Regex("[._-]+"), " ").trim().replace(Regex("\\s+"), " ")
    return if (words == words.lowercase()) words.replaceFirstChar { it.titlecase() } else words
}

/** The foot fade of a tall hero, long enough to reach behind its title, facts and pill. */
private val TALL_FOOT_FADE = 260.dp

/** The foot fade of a wide hero, into the rows under it. */
private val WIDE_FOOT_FADE = 140.dp

/** An online backdrop is decoded at this width for the hero, as [ArtBackdrop] decodes it. */
private const val ONLINE_WIDTH = 1280

/** Narrower than this, an online picture is not worth a hero. */
private const val SHARP_ONLINE = 480

/**
 * Narrower than this, a Telegram thumbnail is not worth a hero. Telegram's video thumbnails are
 * 320 pixels across at best; a smaller one (90 pixels, say) is a stamp, not a picture.
 */
private const val SHARP_THUMBNAIL = 280

/** How long a hero waits for its candidates' thumbnails to be looked up before giving up on them. */
private const val ART_WAIT_MS = 1_500L

/** The height a spotlight takes on a screen [screenHeight] tall: see [HomeCarousel]. */
fun spotlightHeight(screenHeight: Dp, wide: Boolean, touch: Boolean): Dp = when {
    !wide -> (screenHeight * 0.56f).coerceIn(320.dp, 520.dp)
    touch -> (screenHeight * 0.7f).coerceIn(220.dp, 400.dp)
    else -> (screenHeight * 0.5f).coerceIn(240.dp, 440.dp)
}

/**
 * The hero's words fade over the first [WORDS_FADE] of scrolling while a header stands over the
 * pane ([under]), and are gone before they reach it; with no header over them they stay.
 */
fun heroWordsAlpha(list: androidx.compose.foundation.lazy.LazyListState, under: Dp, density: androidx.compose.ui.unit.Density): Float {
    if (under <= 0.dp) return 1f
    if (list.firstVisibleItemIndex > 0) return 0f
    val fade = with(density) { WORDS_FADE.toPx() }
    return (1f - list.firstVisibleItemScrollOffset / fade).coerceIn(0f, 1f)
}

/** How far the list scrolls before the hero's words are gone from under a header. */
private val WORDS_FADE = 56.dp
