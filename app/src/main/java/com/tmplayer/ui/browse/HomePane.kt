package com.tmplayer.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tmplayer.data.HomeRow
import com.tmplayer.data.HomeRows
import com.tmplayer.data.MediaItem
import com.tmplayer.data.ResumeRecord
import com.tmplayer.data.Series
import com.tmplayer.data.SeriesShelf
import com.tmplayer.data.SettingsStore
import com.tmplayer.data.ShelfEntry
import com.tmplayer.data.WatchLaterRecord
import com.tmplayer.ui.components.TmSecondaryButton
import com.tmplayer.ui.components.isTouch
import com.tmplayer.ui.i18n.LocalStrings
import com.tmplayer.ui.online.LocalOnlineArt
import com.tmplayer.ui.theme.Corner
import com.tmplayer.ui.theme.Tone
import com.tmplayer.ui.theme.Tv
import kotlinx.coroutines.launch
import androidx.compose.material3.MaterialTheme as M3MaterialTheme
import androidx.compose.material3.Text as M3Text

/**
 * Home: the spotlight when there is something worth leading with, then a row of what the viewer
 * was part way through, a row of what they saved for later, the new episodes of the shows they
 * are watching, a row per starred chat, and the newest videos across the starred chats (the rows
 * are built by [com.tmplayer.data.HomeRows]).
 *
 * The spotlight ([HomeSpotlight]) is the newest film or show the viewer is part way through, and
 * only when it has a picture worth showing large; otherwise Home opens on its rows and nothing
 * stands in for it.
 *
 * Continue watching is wide cards with the name written on the art and the time left in a chip.
 * In a starred chat's row and in Recently added, films and shows are 2:3 posters and everything
 * else a 16:9 card, each kind in its own strip under the one heading, posters first, so a row
 * never mixes a tall card with a short one.
 *
 * One vertical lazy list of horizontal lazy rows, so only the rows on screen exist and only the
 * tiles on screen in each of those. Every row and tile is keyed, and every tile is a fixed width,
 * which is what lets a 1 GB television scroll this without measuring anything twice.
 *
 * On a television the D-pad moves along a row and between rows, each row remembering the tile it
 * was left on. Home opens on the spotlight's Resume where there is one, else on the first tile;
 * Down from Resume goes into the rows and Up from the first row comes back. A row that leaves
 * videos out offers "See all" on its heading.
 */
@Composable
internal fun HomePane(
    rows: List<HomeRow>,
    watch: SeriesWatch,
    /** Continue watching videos as Telegram describes them, for their pictures. */
    art: Map<String, MediaItem>,
    chatTitle: (Long) -> String,
    start: Dp,
    end: Dp,
    bottom: Dp,
    onRowShown: (HomeRow) -> Unit,
    onArtWanted: (ResumeRecord) -> Unit,
    onResume: (ResumeRecord) -> Unit,
    onPlay: (MediaItem, String) -> Unit,
    /** A long press, a held OK or the info key on a video's tile: its detail panel. */
    onHoldMedia: (MediaItem, String) -> Unit,
    onSeeContinue: () -> Unit,
    onOpenChat: (Long) -> Unit,
    /**
     * True when no chat is starred, so Home has no chat rows to show and says how to get them.
     * Not needed when [rows] is empty: the empty state already says the same.
     */
    noFavourites: Boolean = false,
    /** What the first-run state says and where its button goes: Saved Messages, or a folder. */
    firstStep: DefaultGroups.FirstStep = DefaultGroups.firstStep(hidden = false, folders = emptyList()),
    /** Opens the first-run state's target. */
    onOpenSection: (BrowseSection) -> Unit = {},
    /** A Watch later tile: its detail or the player, as a Continue watching tile does. */
    onOpenWatchLater: (WatchLaterRecord) -> Unit = {},
    onLaterArtWanted: (WatchLaterRecord) -> Unit = {},
    onSeeWatchLater: () -> Unit = {},
    /** Download season and Download unwatched on a show's page; null leaves them out. */
    onDownloadSeason: ((SeasonDownloadRequest) -> Unit)? = null,
    /**
     * How far the hero's picture runs up behind a header drawn over the top of the pane (a
     * phone's app bar, a television's heading); zero for a header above it.
     */
    under: Dp = 0.dp,
    /** Told whether there is a hero and whether it is under that header, for the header's look. */
    hero: HeroUnderHeader? = null,
) {
    val s = LocalStrings.current
    if (hero != null) {
        DisposableEffect(hero) { onDispose { hero.update(present = false, underHeader = false) } }
    }
    if (rows.isEmpty()) {
        // Nothing played and nothing starred: a first run, so say how to get a first video in.
        SideEffect { hero?.update(present = false, underHeader = false) }
        FirstVideoEmpty(firstStep, onOpenSection)
        return
    }
    val touch = isTouch()
    val first = remember { FocusRequester() }
    val heroFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var openSeries by remember { mutableStateOf<Series?>(null) }
    val listState = rememberLazyListState()
    // Set by the first press of the remote, after which a spotlight arriving late does not take
    // focus away from wherever the viewer has gone.
    var moved by remember { mutableStateOf(false) }

    val candidates = remember(rows) {
        rows.filterIsInstance<HomeRow.Continue>().firstOrNull()?.let { HomeRows.spotlight(it.records + it.more) }.orEmpty()
    }
    val spotlight = (rememberSpotlight(candidates, art, onArtWanted) as? SpotlightState.Ready)?.spotlight
    val heroUnder by rememberHeroUnderHeader(listState, under, spotlight != null)
    if (hero != null) SideEffect { hero.update(present = spotlight != null, underHeader = heroUnder) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // A television and a phone on its side put the words beside the picture; a phone upright
        // puts them under it. Measured on the screen under any header drawn over the pane.
        val wide = !touch || maxWidth > maxHeight
        val heroHeight = spotlightHeight(maxHeight - under, wide, touch)
        // Tiles here may wear the show's or the film's picture (see LocalOnlineArt); a chat's grid keeps frames.
        CompositionLocalProvider(LocalOnlineArt provides true) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (touch) {
                            Modifier
                        } else {
                            Modifier.onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown) moved = true
                                false
                            }
                        },
                    ),
                contentPadding = PaddingValues(
                    top = if (spotlight != null) 0.dp else if (touch) 4.dp else Tv.FocusClearance,
                    bottom = bottom,
                ),
                verticalArrangement = Arrangement.spacedBy(if (touch) ROW_GAP_TOUCH else ROW_GAP_TV),
            ) {
                if (spotlight != null) {
                    item(key = SPOTLIGHT_KEY, contentType = "spotlight") {
                        val record = spotlight.record
                        val chat = record.chatTitle.ifEmpty { chatTitle(record.chatId) }
                        HomeSpotlight(
                            spotlight = spotlight,
                            chatTitle = chat,
                            wide = wide,
                            height = heroHeight,
                            start = start,
                            end = end,
                            onResume = { onResume(record) },
                            onDetails = { onHoldMedia(spotlight.item, chat) },
                            resumeFocus = heroFocus,
                            under = under,
                        )
                    }
                }
                items(rows, key = { it.key }, contentType = { it::class }) { row ->
                    val loaded = when (row) {
                        is HomeRow.Continue, is HomeRow.WatchLater, is HomeRow.NewEpisodes -> true
                        is HomeRow.Chat -> row.loaded
                        is HomeRow.Recent -> row.loaded
                    }
                    // Asked for when the row first comes on screen, and again after a refresh empties it.
                    LaunchedEffect(row.key, loaded) { if (!loaded) onRowShown(row) }
                    val isFirst = row === rows.first()
                    val title = when (row) {
                        is HomeRow.Continue -> s.homeRowContinue
                        is HomeRow.WatchLater -> s.homeRowWatchLater
                        is HomeRow.NewEpisodes -> s.homeRowNewEpisodes
                        is HomeRow.Chat -> row.title
                        is HomeRow.Recent -> s.homeRowRecent
                    }
                    // Only a row that leaves something out offers the rest of it.
                    val seeAll: (() -> Unit)? = when (row) {
                        is HomeRow.Continue -> onSeeContinue
                        is HomeRow.WatchLater -> onSeeWatchLater
                        is HomeRow.Chat -> { { onOpenChat(row.chatId) } }
                        is HomeRow.NewEpisodes, is HomeRow.Recent -> null
                    }?.takeIf { row.hasMore }
                    val countLabel = if (row.totalAtLeast) s.homeSeeAllCountAtLeast(row.total) else s.homeSeeAllCount(row.total)
                    // On a television "See all" sits on the heading, where it can be seen, but it only
                    // takes focus when Up is pressed from this row's tiles: moving down through Home
                    // goes tile row to tile row and never stops on a heading on the way.
                    val seeAllFocus = remember { FocusRequester() }
                    var seeAllArmed by remember { mutableStateOf(false) }
                    val upToSeeAll = if (touch || seeAll == null) {
                        Modifier
                    } else {
                        Modifier.onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown || event.key != Key.DirectionUp) return@onPreviewKeyEvent false
                            seeAllArmed = true
                            scope.launch {
                                // A frame for the armed heading to become focusable.
                                withFrameNanos {}
                                runCatching { seeAllFocus.requestFocus() }
                            }
                            true
                        }
                    }
                    Column {
                        RowHeading(
                            title,
                            start = start,
                            end = end,
                            seeAll = seeAll,
                            count = countLabel,
                            focus = seeAllFocus,
                            armed = seeAllArmed,
                            onLeft = { seeAllArmed = false },
                        )
                        if (!loaded) {
                            PlaceholderRow(start)
                            return@Column
                        }
                        when (row) {
                            is HomeRow.Continue -> TileRow(start, end, upToSeeAll) {
                                items(row.records, key = { "r-${it.chatId}_${it.messageId}" }) { record ->
                                    val key = SettingsStore.progressKey(record.chatId, record.messageId)
                                    LaunchedEffect(key) { onArtWanted(record) }
                                    val item = art[key] ?: remember(record) { record.toMediaItem() }
                                    val chat = record.chatTitle.ifEmpty { chatTitle(record.chatId) }
                                    VideoTile(
                                        title = "",
                                        showTitle = false,
                                        onClick = { onResume(record) },
                                        // The detail panel, as on every other tile here: it already
                                        // offers Resume, Start over and Mark watched for a video with
                                        // a saved place.
                                        onLongClick = { onHoldMedia(item, chat) },
                                        modifier = Modifier.width(continueWidth(touch))
                                            .then(if (isFirst && record === row.records.first()) Modifier.focusRequester(first) else Modifier),
                                        meta = { ink -> CaptionLine(telegramFacts(chat, item), ink) },
                                    ) {
                                        ContinueCardArt(record, item, Modifier.fillMaxSize())
                                    }
                                }
                            }
                            is HomeRow.WatchLater -> TileRow(start, end, upToSeeAll) {
                                items(row.records, key = { "l-${it.key}" }) { record ->
                                    LaunchedEffect(record.key) { onLaterArtWanted(record) }
                                    val item = art[record.key] ?: remember(record) { record.toMediaItem() }
                                    MediaCard(
                                        item = item,
                                        watched = watch.point(item),
                                        onClick = { onOpenWatchLater(record) },
                                        onFocused = {},
                                        // The detail panel, which offers Remove from Watch later.
                                        onLongClick = { onHoldMedia(item, record.chatTitle.ifEmpty { chatTitle(record.chatId) }) },
                                        dense = touch,
                                        modifier = Modifier.width(wideWidth(touch))
                                            .then(if (isFirst && record === row.records.first()) Modifier.focusRequester(first) else Modifier),
                                    )
                                }
                            }
                            is HomeRow.NewEpisodes -> TileRow(start, end, upToSeeAll) {
                                items(row.episodes, key = { "n-${it.item.id}" }) { episode ->
                                    val item = episode.item
                                    val chat = chatTitle(item.chatId)
                                    // The show's name, then which episode: the file name would say the
                                    // same less readably. Under it Telegram's facts, as on every other
                                    // row; the New badge on the art says it is new.
                                    MediaCard(
                                        item = item,
                                        watched = null,
                                        onClick = { onPlay(item, chatTitle(item.chatId)) },
                                        onLongClick = { onHoldMedia(item, chatTitle(item.chatId)) },
                                        onFocused = {},
                                        dense = touch,
                                        title = if (episode.show.isBlank()) episode.code else "${episode.show}  ·  ${episode.code}",
                                        badge = { NewBadge(compact = touch) },
                                        meta = { ink -> CaptionLine(telegramFacts(chat, item), ink) },
                                        modifier = Modifier.width(wideWidth(touch))
                                            .then(if (isFirst && episode === row.episodes.first()) Modifier.focusRequester(first) else Modifier),
                                    )
                                }
                            }
                            is HomeRow.Chat -> ShelfRows(row.entries, watch, touch, isFirst, first, { row.title }, onPlay, onHoldMedia, start, end, upToSeeAll) { openSeries = it }
                            is HomeRow.Recent -> ShelfRows(row.entries, watch, touch, isFirst, first, chatTitle, onPlay, onHoldMedia, start, end, upToSeeAll) { openSeries = it }
                        }
                    }
                }
                // Where the starred chats' rows would be, a card saying how to get them; otherwise a
                // Home of Continue watching alone gives no clue that it could hold more. Never
                // focusable: the remote has nothing to do here, and the way to star a chat is in
                // the chat list, which the hint names.
                if (noFavourites) {
                    item(key = "favourites-hint", contentType = "favourites-hint") {
                        FavouritesHintCard(s.homeFavouritesHint(if (touch) "phone" else "tv"), start = start, end = end)
                    }
                }
            }
        }
    }

    // Somewhere for the remote to stand the moment Home opens. Keyed on the first row, so a row
    // arriving above (Continue appearing after a first play) moves focus to the new top.
    val firstKey = rows.first().key
    val firstReady = when (val top = rows.first()) {
        is HomeRow.Chat -> top.loaded
        is HomeRow.Recent -> top.loaded
        is HomeRow.Continue, is HomeRow.WatchLater, is HomeRow.NewEpisodes -> true
    }
    LaunchedEffect(firstKey, firstReady) {
        if (!touch && firstReady && spotlight == null) runCatching { first.requestFocus() }
    }
    // The spotlight's Resume, once it is known there is one: the first thing on Home, so the
    // first thing the remote stands on, unless the viewer has already moved off somewhere.
    val heroKey = spotlight?.record?.let { SettingsStore.progressKey(it.chatId, it.messageId) }
    LaunchedEffect(heroKey) {
        if (touch || heroKey == null || moved) return@LaunchedEffect
        withFrameNanos {}
        if (listState.firstVisibleItemIndex == 0) runCatching { heroFocus.requestFocus() }
    }

    openSeries?.let { series ->
        SeriesOpened(
            series = series,
            watch = watch,
            onPlay = { onPlay(it, chatTitle(it.chatId)) },
            onDismiss = { openSeries = null },
            onLongClick = { onHoldMedia(it, chatTitle(it.chatId)) },
            onDownload = onDownloadSeason,
        )
    }
}

/**
 * A starred chat's row or Recently added: its films and shows as 2:3 posters in one strip, its
 * other videos as 16:9 cards in a strip under it, each in the row's own order. Only the strips
 * that have anything in them are drawn, so a chat of clips is one strip of cards as before.
 */
@Composable
private fun ShelfRows(
    entries: List<ShelfEntry>,
    watch: SeriesWatch,
    touch: Boolean,
    isFirstRow: Boolean,
    first: FocusRequester,
    chatTitle: (Long) -> String,
    onPlay: (MediaItem, String) -> Unit,
    onHold: (MediaItem, String) -> Unit,
    start: Dp,
    end: Dp,
    upToSeeAll: Modifier,
    onOpenSeries: (Series) -> Unit,
) {
    val (posters, videos) = remember(entries) { entries.partition(SeriesShelf::isPoster) }
    val lead = posters.firstOrNull() ?: videos.firstOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(if (touch) STRIP_GAP else 0.dp)) {
        if (posters.isNotEmpty()) {
            TileRow(start, end, upToSeeAll) {
                entries(posters, poster = true, watch, touch, if (isFirstRow) lead else null, first, chatTitle, onPlay, onHold, onOpenSeries)
            }
        }
        if (videos.isNotEmpty()) {
            TileRow(start, end, if (posters.isEmpty()) upToSeeAll else Modifier) {
                entries(videos, poster = false, watch, touch, if (isFirstRow) lead else null, first, chatTitle, onPlay, onHold, onOpenSeries)
            }
        }
    }
}

private fun LazyListScope.entries(
    entries: List<ShelfEntry>,
    poster: Boolean,
    watch: SeriesWatch,
    touch: Boolean,
    lead: ShelfEntry?,
    first: FocusRequester,
    chatTitle: (Long) -> String,
    onPlay: (MediaItem, String) -> Unit,
    onHold: (MediaItem, String) -> Unit,
    onOpenSeries: (Series) -> Unit,
) {
    items(entries, key = { it.key }, contentType = { if (poster) "poster-${it::class.simpleName}" else it::class }) { entry ->
        val focus = if (entry === lead) Modifier.focusRequester(first) else Modifier
        val modifier = Modifier.width(if (poster) posterWidth(touch) else wideWidth(touch)).then(focus)
        when (entry) {
            is ShelfEntry.File -> MediaCard(
                item = entry.item,
                watched = watch.point(entry.item),
                finished = watch.finished(entry.item),
                onClick = { onPlay(entry.item, chatTitle(entry.item.chatId)) },
                onLongClick = { onHold(entry.item, chatTitle(entry.item.chatId)) },
                onFocused = {},
                dense = touch,
                poster = poster,
                modifier = modifier,
            )
            is ShelfEntry.Show -> SeriesCard(
                series = entry.series,
                progress = remember(entry.series, watch) { watch.progress(entry.series) },
                fresh = remember(entry.series, watch) { watch.newEpisodes(entry.series) > 0 },
                onClick = { onOpenSeries(entry.series) },
                dense = touch,
                poster = poster,
                modifier = modifier,
            )
        }
    }
}

/** One horizontal lazy row, padded to line up with the heading, remembering its tile for the D-pad. */
@Composable
private fun TileRow(
    start: Dp,
    end: Dp,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    val touch = isTouch()
    LazyRow(
        // Coming back down into a row lands on the tile it was left on, not whichever is nearest.
        modifier = modifier.fillMaxWidth().then(if (touch) Modifier else Modifier.focusRestorer()),
        contentPadding = PaddingValues(
            start = start,
            end = end,
            // Room for a focused tile to grow into without being clipped by the row.
            top = if (touch) 0.dp else Tv.FocusClearance,
            bottom = if (touch) 0.dp else Tv.FocusClearance,
        ),
        horizontalArrangement = Arrangement.spacedBy(if (touch) TILE_GAP_TOUCH else TILE_GAP_TV),
        content = content,
    )
}

/** The Telegram facts under a continue card: where it was posted, its quality and its size. */
@Composable
private fun telegramFacts(chat: String, item: MediaItem): String {
    val s = LocalStrings.current
    return remember(chat, item, s) {
        listOfNotNull(
            chat.ifBlank { null },
            item.qualityTags.firstOrNull(),
            item.sizeBytes.takeIf { it > 0 }?.let { s.formatter.size(it) },
        ).joinToString("  ·  ")
    }
}

/** One line of facts under a card, in the tile's [ink]: muted at rest, the full text under focus. */
@Composable
private fun CaptionLine(text: String, ink: Color) {
    if (isTouch()) {
        M3Text(text, style = M3MaterialTheme.typography.labelMedium, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    } else {
        Text(text, style = MaterialTheme.typography.labelMedium, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A row's name, SemiBold: titleLarge on a phone and on a television. A row that leaves videos out
 * offers the rest: on a phone a quiet 32 dp chip with a chevron at the end of the heading, on a
 * television a pill that takes focus only from the row under it (see [HomePane]).
 */
@Composable
private fun RowHeading(
    title: String,
    start: Dp,
    end: Dp,
    seeAll: (() -> Unit)?,
    count: String,
    focus: FocusRequester,
    armed: Boolean,
    onLeft: () -> Unit,
) {
    val s = LocalStrings.current
    val touch = isTouch()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = start, end = end, bottom = if (touch) HEADING_TO_ROW else 0.dp)
            // Every heading is as tall as its button, with or without one, so the gap between a
            // title and its tiles is the same on every row.
            .heightIn(min = if (touch) SEE_ALL_CHIP else TV_SEE_ALL_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (touch) {
            M3Text(
                title,
                style = M3MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Tone.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Tone.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        if (seeAll != null && !touch) {
            // The count beside the words, as the desktop has it, so the row says how much more
            // there is before anyone presses anything.
            TmSecondaryButton(
                onClick = seeAll,
                modifier = Modifier
                    .focusProperties { canFocus = armed }
                    .focusRequester(focus)
                    .onFocusChanged { if (!it.hasFocus) onLeft() },
            ) {
                Text(s.homeSeeAll)
                // The label's own colour, softened, so it reads on the focused fill as well as off it.
                Text("  ·  $count", color = androidx.tv.material3.LocalContentColor.current.copy(alpha = 0.72f))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else if (seeAll != null) {
            // A quiet chip rather than a link in the accent: the accent is for progress, selection
            // and focus. The touch target is Material's 48 dp round the 32 dp chip.
            Row(
                Modifier
                    .padding(start = 12.dp)
                    .minimumInteractiveComponentSize()
                    .height(SEE_ALL_CHIP)
                    .clip(CircleShape)
                    .background(Tone.surface)
                    .clickable(role = Role.Button, onClick = seeAll)
                    .padding(start = 12.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                M3Text(s.homeSeeAll, style = M3MaterialTheme.typography.labelLarge, color = Tone.text)
                androidx.compose.material3.Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Tone.muted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * A row still asking Telegram: blank cards the size of the real ones, so nothing jumps when it
 * fills. The caption is the cards' own, in the same type with nothing visible in it, rather than
 * a guessed height: it then matches at any font scale.
 */
@Composable
private fun PlaceholderRow(start: Dp) {
    val touch = isTouch()
    val width = wideWidth(touch)
    Row(
        Modifier.fillMaxWidth().padding(start = start, top = if (touch) 0.dp else Tv.FocusClearance, bottom = if (touch) 0.dp else Tv.FocusClearance),
        horizontalArrangement = Arrangement.spacedBy(if (touch) TILE_GAP_TOUCH else TILE_GAP_TV),
    ) {
        repeat(PLACEHOLDER_TILES) {
            Column(Modifier.width(width)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(Corner.Medium))
                        .background(Tone.surface),
                )
                Spacer(Modifier.height(if (touch) 8.dp else 10.dp))
                if (touch) {
                    M3Text(" ", style = M3MaterialTheme.typography.titleSmall, minLines = 2, maxLines = 2)
                    Spacer(Modifier.height(2.dp))
                    M3Text(" ", style = M3MaterialTheme.typography.labelMedium, maxLines = 1)
                } else {
                    Text(" ", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Spacer(Modifier.height(2.dp))
                    Text(" ", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }
    }
}

/**
 * A film's or a show's poster: 126 x 189 dp on both, Nuvio's measure, which on a phone held
 * upright shows about 2.8 across, the cut third saying there is more this way.
 */
@Suppress("UNUSED_PARAMETER")
private fun posterWidth(touch: Boolean): Dp = POSTER_WIDTH

private val POSTER_WIDTH = 126.dp

/** Any other video's 16:9 card. A television's fits [Tv.FocusClearance] when it grows. */
private fun wideWidth(touch: Boolean): Dp = if (touch) 200.dp else 208.dp

/** A Continue watching card, a little wider, as it carries its name on the art. */
private fun continueWidth(touch: Boolean): Dp = if (touch) 247.dp else 256.dp

private const val PLACEHOLDER_TILES = 5

/** The TV button's own height, which every TV row heading takes so the rows space evenly. */
private val TV_SEE_ALL_HEIGHT = 40.dp

/** The phone's See all chip. */
private val SEE_ALL_CHIP = 32.dp

/** Between a phone row's heading and its cards. */
private val HEADING_TO_ROW = 10.dp

private val ROW_GAP_TOUCH = 24.dp

/** A television's rows already keep [Tv.FocusClearance] above and below their tiles. */
private val ROW_GAP_TV = 12.dp

private val TILE_GAP_TOUCH = 10.dp
private val TILE_GAP_TV = 12.dp

/** Between a row's poster strip and its strip of cards, on a phone. */
private val STRIP_GAP = 12.dp

/**
 * What Home's hero tells the header drawn over it (the phone's app bar, the television's heading):
 * whether there is a hero, so the header goes over the pane at all, and whether its picture is
 * still under the header, so the header stands on the picture rather than on its own ground.
 */
@Stable
internal class HeroUnderHeader {
    var present by mutableStateOf(false)
        private set
    var underHeader by mutableStateOf(false)
        private set

    fun update(present: Boolean, underHeader: Boolean) {
        if (this.present != present) this.present = present
        if (this.underHeader != underHeader) this.underHeader = underHeader
    }
}
