package com.tmplayer.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.tmplayer.data.ChatSummary
import com.tmplayer.data.HomeRow
import com.tmplayer.data.HomeRows
import com.tmplayer.data.MediaItem
import com.tmplayer.data.MediaMapper
import com.tmplayer.data.NewEpisode
import com.tmplayer.data.ResumeRecord
import com.tmplayer.data.Series
import com.tmplayer.data.SeriesShelf
import com.tmplayer.data.SettingsStore
import com.tmplayer.data.ShelfEntry
import com.tmplayer.data.WatchLaterRecord
import com.tmplayer.ui.browse.BrowseTab
import com.tmplayer.ui.browse.ChatListViewModel
import com.tmplayer.ui.browse.ContinueCardArt
import com.tmplayer.ui.browse.FirstVideoCard
import com.tmplayer.ui.browse.HomeViewModel
import com.tmplayer.ui.browse.NewBadge
import com.tmplayer.ui.browse.rememberHomeRows
import com.tmplayer.ui.components.UiState
import com.tmplayer.ui.components.WithinMargins
import com.tmplayer.ui.components.edgeFade
import com.tmplayer.ui.i18n.LocalStrings
import com.tmplayer.ui.icons.TmIcons
import com.tmplayer.ui.nav.BackHandler
import com.tmplayer.ui.online.LocalOnlineArt
import com.tmplayer.ui.online.OnlineArt
import com.tmplayer.ui.theme.Motion
import com.tmplayer.ui.theme.Tone
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Home on the desktop: the same rows as the phone and the television (see
 * [com.tmplayer.data.HomeRows]), each row a horizontal strip that loads when it scrolls into view.
 */
@Composable
fun HomePage(state: ShellState, chats: ChatListViewModel) {
    val model = rememberViewModel(Unit) {
        HomeViewModel(sizeLimits = { state.settings.minSizeBytes.first() to state.settings.maxSizeBytes.first() })
    }
    val chatState by chats.state.collectAsState()
    val list = (chatState as? UiState.Content)?.value?.chats.orEmpty()
    val favourites by state.settings.favorites.collectAsState(initial = emptySet())
    val history by state.settings.continueWatching.collectAsState(initial = emptyList())
    val later by state.watched.watchLater.items.collectAsState(initial = emptyList())
    val watched by state.watched.watched.collectAsState(initial = emptyMap())
    val art by model.art.collectAsState()
    HomeRowsView(
        state = state,
        rows = rememberHomeRows(model, list, favourites, history, later, watched.values),
        chats = list,
        art = art,
        onRowShown = { row ->
            when (row) {
                is HomeRow.Chat -> model.request(row.chatId)
                is HomeRow.Recent -> model.requestRecent()
                is HomeRow.Continue, is HomeRow.WatchLater, is HomeRow.NewEpisodes -> Unit
            }
        },
        onArtWanted = model::requestArt,
        onLaterArtWanted = model::requestArt,
        onRefresh = model::refresh,
    )
}

/**
 * Home's rows as drawn, given everything they are built from; the render test calls this directly.
 *
 * Continue watching as wide cards with the name on the art, and in a starred chat's row and in
 * Recently added the films and shows as 2:3 posters in one strip with the other videos as cards in
 * a strip under it.
 *
 * Like a desktop app rather than a stretched phone: the cards are sized from the page's width so
 * whole cards fill a row and a wider window shows more of them; a hovered row shows arrows at its
 * ends and scrolls sideways under a trackpad or Shift and the wheel; every row has "See all",
 * which opens the row as a page of its own ([RowPage]). From the keyboard the arrows move between
 * cards and rows, Enter opens a card's details and Space plays it; a click opens the details and
 * a double click plays, as everywhere else.
 */
@Composable
internal fun HomeRowsView(
    state: ShellState,
    rows: List<HomeRow>,
    chats: List<ChatSummary>,
    art: Map<String, MediaItem>,
    onRowShown: (HomeRow) -> Unit,
    onArtWanted: (ResumeRecord) -> Unit,
    onRefresh: () -> Unit,
    onLaterArtWanted: (WatchLaterRecord) -> Unit = {},
) {
    val s = LocalStrings.current
    var openSeries by remember { mutableStateOf<Series?>(null) }
    var openRow by remember { mutableStateOf<String?>(null) }
    val titles = remember(chats) { chats.associate { it.id to it.title } }
    LaunchedEffect(titles) { state.noteChatTitles(chats) }
    val shown = openSeries
    if (shown != null) {
        SeriesPage(state, shown, rememberSeriesWatch(state), onClose = { openSeries = null })
        return
    }
    val paged = openRow?.let { key -> rows.firstOrNull { it.key == key } }
    if (paged != null) {
        RowPage(
            state = state,
            row = paged,
            titles = titles,
            chats = chats,
            art = art,
            onArtWanted = onArtWanted,
            onLaterArtWanted = onLaterArtWanted,
            onOpenSeries = { openSeries = it },
            onBack = { openRow = null },
        )
        return
    }
    val list = rememberLazyListState()
    val header: @Composable () -> Unit = {
        PageHeader(
            BrowseTab.Home.heading,
            BrowseTab.Home.blurb,
            actions = {
                IconButton(onClick = onRefresh) { Icon(TmIcons.Refresh, contentDescription = s.commonRefresh) }
                PosterSizeStep(state)
            },
        )
    }
    // In line with the page header's title, whatever the window's width.
    val side = pageMargin()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            header()
            if (rows.isEmpty()) {
                // Nothing played and nothing starred: a first run, so say how to get a first video in:
                // through Saved Messages, or with the default groups hidden, through a folder.
                val folders by com.tmplayer.data.Td.folders.collectAsState()
                val step = remember(state.hideDefaultGroups, folders) {
                    com.tmplayer.ui.browse.DefaultGroups.firstStep(state.hideDefaultGroups, folders)
                }
                Box(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val target = step.target
                    FirstVideoCard(
                        title = s.homeFirstTitle,
                        body = step.body,
                        more = s.homeFirstMore,
                        button = step.button?.takeIf { target != null },
                        onButton = { target?.let(state::showChats) },
                    )
                }
                return@Column
            }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val sizes = CardSizes.of(state.posterWidth, maxWidth - side * 2)
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = list,
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(rows, key = { it.key }, contentType = { it::class }) { row ->
                        val loaded = row.isLoaded()
                        LaunchedEffect(row.key, loaded) { if (!loaded) onRowShown(row) }
                        Column {
                            RowHeading(
                                title = row.title(s, titles),
                                count = row.countLabel(s),
                                side = side,
                                // Every loaded row opens as a page of its own.
                                onSeeAll = if (loaded) ({ openRow = row.key }) else null,
                            )
                            if (!loaded) {
                                PlaceholderStrip(side, sizes.card)
                                return@Column
                            }
                            when (row) {
                                is HomeRow.Continue -> Strip(side) {
                                    items(row.records, key = { "r-${it.chatId}_${it.messageId}" }) { record ->
                                        Box(Modifier.width(sizes.wide)) { ContinueCell(state, record, art, titles, onArtWanted) }
                                    }
                                }
                                // The card a chat's grid draws, so the pane offers Remove from Watch later.
                                is HomeRow.WatchLater -> Strip(side) {
                                    items(row.records, key = { "l-${it.key}" }) { record ->
                                        Box(Modifier.width(sizes.card)) { LaterCell(state, record, art, onLaterArtWanted) }
                                    }
                                }
                                is HomeRow.NewEpisodes -> Strip(side) {
                                    items(row.episodes, key = { "n-${it.item.id}" }) { episode ->
                                        Box(Modifier.width(sizes.card)) { NewEpisodeCell(state, episode, titles) }
                                    }
                                }
                                is HomeRow.Chat -> ShelfStrips(state, side, sizes, row.entries, { row.title }) { openSeries = it }
                                is HomeRow.Recent -> ShelfStrips(state, side, sizes, row.entries, { titles[it].orEmpty() }) { openSeries = it }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A Home row opened by its "See all": every video the row holds, Home's own cards in a grid as
 * wide as the page, under a header with the row's name, how many there are and the way back (the
 * back button or Esc). A starred chat's row also offers its chat, which holds every video rather
 * than the ones Home fetched.
 */
@Composable
private fun RowPage(
    state: ShellState,
    row: HomeRow,
    titles: Map<Long, String>,
    chats: List<ChatSummary>,
    art: Map<String, MediaItem>,
    onArtWanted: (ResumeRecord) -> Unit,
    onLaterArtWanted: (WatchLaterRecord) -> Unit,
    onOpenSeries: (Series) -> Unit,
    onBack: () -> Unit,
) {
    val s = LocalStrings.current
    BackHandler(enabled = true) { onBack() }
    val title = row.title(s, titles)
    // A starred chat's row holds what Home fetched; the chat itself holds every video.
    val more: Pair<String, () -> Unit>? = (row as? HomeRow.Chat)?.let { chatRow ->
        chats.firstOrNull { it.id == chatRow.chatId }?.let { chat -> s.detailOpenChat(chatRow.title) to { state.openChat(chat) } }
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader(
            title,
            row.countLabel(s),
            leading = {
                IconButton(onClick = onBack) { Icon(TmIcons.ArrowBack, contentDescription = s.commonBack) }
            },
            actions = {
                if (more != null) {
                    TextButton(onClick = more.second) {
                        Text(more.first)
                        Icon(TmIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            },
        )
        val side = pageMargin()
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val sizes = CardSizes.of(state.posterWidth, maxWidth - side * 2, peek = false)
            // Each kind of card in its own grid, a row of cells at a time, so the grid stays lazy.
            val sections: List<Pair<Dp, List<Pair<String, @Composable () -> Unit>>>> = remember(row, art, sizes) {
                when (row) {
                    is HomeRow.Continue -> listOf(
                        sizes.wide to (row.records + row.more).map { record ->
                            "r-${record.chatId}_${record.messageId}" to @Composable { ContinueCell(state, record, art, titles, onArtWanted) }
                        },
                    )
                    is HomeRow.WatchLater -> listOf(
                        sizes.card to (row.records + row.more).map { record ->
                            "l-${record.key}" to @Composable { LaterCell(state, record, art, onLaterArtWanted) }
                        },
                    )
                    is HomeRow.NewEpisodes -> listOf(
                        sizes.card to row.episodes.map { episode -> "n-${episode.item.id}" to @Composable { NewEpisodeCell(state, episode, titles) } },
                    )
                    is HomeRow.Chat -> shelfSections(state, sizes, row.entries + SeriesShelf.arrange(row.more), { row.title }, onOpenSeries)
                    is HomeRow.Recent -> shelfSections(state, sizes, row.entries, { titles[it].orEmpty() }, onOpenSeries)
                }
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = side, end = side, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                sections.forEachIndexed { at, (width, cells) ->
                    val columns = sizes.columnsFor(width)
                    val lines = cells.chunked(columns)
                    lines.forEachIndexed { line, cellsInLine ->
                        item(key = "s$at-$line-${cellsInLine.first().first}") {
                            Row(horizontalArrangement = Arrangement.spacedBy(CARD_GAP)) {
                                cellsInLine.forEach { (_, cell) -> Box(Modifier.width(width)) { cell() } }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A row's posters and its other videos, as two grids of their own (see [ShelfStrips]). */
private fun shelfSections(
    state: ShellState,
    sizes: CardSizes,
    entries: List<ShelfEntry>,
    chatTitle: (Long) -> String,
    onOpenSeries: (Series) -> Unit,
): List<Pair<Dp, List<Pair<String, @Composable () -> Unit>>>> {
    val (posters, videos) = entries.distinctBy { it.key }.partition(SeriesShelf::isPoster)
    return listOf(
        sizes.poster to posters.map { entry -> entry.key to @Composable { ShelfCell(state, entry, true, chatTitle, onOpenSeries) } },
        sizes.card to videos.map { entry -> entry.key to @Composable { ShelfCell(state, entry, false, chatTitle, onOpenSeries) } },
    ).filter { it.second.isNotEmpty() }
}

/** A row's heading: its name, and "See all" with how many videos it holds. */
@Composable
private fun RowHeading(title: String, count: String?, side: Dp, onSeeAll: (() -> Unit)?) {
    val s = LocalStrings.current
    Row(
        Modifier.fillMaxWidth().padding(start = side, end = side).heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onSeeAll != null) {
            TextButton(onClick = onSeeAll) {
                Text(s.homeSeeAll)
                if (count != null) Text("  ·  $count", color = Tone.muted)
                Icon(TmIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** A row still asking Telegram: a card's art and its caption with nothing in it, so the rows below stay put. */
@Composable
private fun PlaceholderStrip(side: Dp, width: Dp) {
    Row(Modifier.padding(start = side, top = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(CARD_GAP)) {
        repeat(4) {
            Column(Modifier.width(width), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    Modifier.padding(bottom = 4.dp).fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Tone.surface, MaterialTheme.shapes.medium),
                )
                Text(" ", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(" ", style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

/** A Continue watching card: the name and the progress on the art, Telegram's facts under it. */
@Composable
private fun ContinueCell(
    state: ShellState,
    record: ResumeRecord,
    art: Map<String, MediaItem>,
    titles: Map<Long, String>,
    onArtWanted: (ResumeRecord) -> Unit,
) {
    val key = SettingsStore.progressKey(record.chatId, record.messageId)
    LaunchedEffect(key) { onArtWanted(record) }
    LaunchedEffect(key) { state.noteChatTitle(record.chatId, record.chatTitle) }
    val item = art[key] ?: remember(record) { record.toMediaItem() }
    val chatTitle = record.chatTitle.ifEmpty { titles[record.chatId].orEmpty() }
    Poster(
        state = state,
        nav = null,
        index = 0,
        item = item,
        chatTitle = chatTitle,
        // The card draws its own pill of progress, under the name.
        progress = null,
        art = { OnHome { ContinueCardArt(record, item, Modifier.fillMaxSize()) } },
        titleBelow = false,
        // Where it came from and what the file is: Telegram's facts.
        subtitle = telegramFacts(chatTitle, item),
        onDetails = { refocus ->
            state.openDetail(DetailRequest(item = item, chatTitle = chatTitle, outsideChat = true, onClosed = refocus))
        },
    )
}

/** A Watch later card: the one a chat's grid draws, so the pane offers Remove from Watch later. */
@Composable
private fun LaterCell(
    state: ShellState,
    record: WatchLaterRecord,
    art: Map<String, MediaItem>,
    onLaterArtWanted: (WatchLaterRecord) -> Unit,
) {
    LaunchedEffect(record.key) { onLaterArtWanted(record) }
    LaunchedEffect(record.key) { state.noteChatTitle(record.chatId, record.chatTitle) }
    val item = art[record.key] ?: remember(record) { record.toMediaItem() }
    OnHome { MediaTile(state, item, record.chatTitle, outsideChat = true) }
}

/** A new episode of a show in progress, with the New badge on its art. */
@Composable
private fun NewEpisodeCell(state: ShellState, episode: NewEpisode, titles: Map<Long, String>) {
    val item = episode.item
    val chatTitle = titles[item.chatId].orEmpty()
    Poster(
        state = state,
        nav = null,
        index = 0,
        item = item,
        chatTitle = chatTitle,
        progress = null,
        badge = { NewBadge(compact = false) },
        art = { OnHome { OnlineArt(item, Modifier.fillMaxSize()) { RecordArt(item) } } },
        // Telegram's facts, as on every other row: the title already names the episode, and the
        // New badge on the art says it is new.
        subtitle = telegramFacts(chatTitle, item),
        onDetails = { refocus ->
            state.openDetail(DetailRequest(item = item, chatTitle = chatTitle, outsideChat = true, onClosed = refocus))
        },
    )
}

/** A starred chat's video or show, as a 2:3 [poster] or a 16:9 card. */
@Composable
private fun ShelfCell(
    state: ShellState,
    entry: ShelfEntry,
    poster: Boolean,
    chatTitle: (Long) -> String,
    onOpenSeries: (Series) -> Unit,
) {
    when (entry) {
        is ShelfEntry.File -> OnHome { MediaTile(state, entry.item, chatTitle(entry.item.chatId), outsideChat = true, poster = poster) }
        is ShelfEntry.Show -> SeriesPoster(entry.series, rememberSeriesWatch(state), onOpen = { onOpenSeries(entry.series) }, poster = poster)
    }
}

/** "Film Club  ·  1080p  ·  2.1 GB": where a video came from and what the file is. */
private fun telegramFacts(chatTitle: String, item: MediaItem): String =
    listOfNotNull(
        chatTitle.ifBlank { null },
        item.qualityTags.firstOrNull(),
        item.sizeBytes.takeIf { it > 0 }?.let { MediaMapper.formatSize(it) },
    ).joinToString("  ·  ")

private fun HomeRow.isLoaded(): Boolean = when (this) {
    is HomeRow.Continue, is HomeRow.WatchLater, is HomeRow.NewEpisodes -> true
    is HomeRow.Chat -> loaded
    is HomeRow.Recent -> loaded
}

private fun HomeRow.title(s: com.tmplayer.i18n.Messages, titles: Map<Long, String>): String = when (this) {
    is HomeRow.Continue -> s.homeRowContinue
    is HomeRow.WatchLater -> s.homeRowWatchLater
    is HomeRow.Chat -> title.ifBlank { titles[chatId].orEmpty() }
    is HomeRow.NewEpisodes -> s.homeRowNewEpisodes
    is HomeRow.Recent -> s.homeRowRecent
}

/** How many videos a row holds, as its "See all" says it; null when it cannot tell. */
private fun HomeRow.countLabel(s: com.tmplayer.i18n.Messages): String? {
    val count = when (this) {
        is HomeRow.Continue, is HomeRow.WatchLater, is HomeRow.Chat -> total
        is HomeRow.NewEpisodes -> episodes.size
        is HomeRow.Recent -> entries.size
    }
    if (count <= 0) return null
    return if (totalAtLeast) s.homeSeeAllCountAtLeast(count) else s.homeSeeAllCount(count)
}

/**
 * The widths of Home's cards for a page [available] wide: whole cards across, as many as fit at
 * the size picked in the header ([ShellState.posterWidth]) or larger, so a wider window shows more
 * of them rather than larger ones. With [peek] a strip also shows a slice of the next card, which
 * says the row goes on.
 */
private data class CardSizes(val card: Dp, val wide: Dp, val poster: Dp, private val available: Dp) {
    /** Cells per line of a grid of cards [width] wide. */
    fun columnsFor(width: Dp): Int = (((available + CARD_GAP) / (width + CARD_GAP)).toInt()).coerceAtLeast(1)

    companion object {
        fun of(base: Dp, available: Dp, peek: Boolean = true): CardSizes {
            fun fit(size: Dp): Dp {
                if (available <= 0.dp) return size
                val slice = if (peek) PEEK else 0f
                fun widthFor(count: Int): Dp = (available - CARD_GAP * (count - if (peek) 0 else 1)) / (count + slice)
                // As many as fit at the picked size, or one more a little smaller: whichever is nearer it.
                val fits = ((available + CARD_GAP) / (size + CARD_GAP) - slice).toInt().coerceAtLeast(1)
                return listOf(fits, fits + 1).map(::widthFor).minBy { (it - size).value.let(Math::abs) }.coerceAtLeast(size * 0.75f)
            }
            return CardSizes(fit(base), fit(base * CONTINUE_SHARE), fit(base * POSTER_SHARE), available)
        }
    }
}

/**
 * One horizontal strip of a row, lined up with the heading. A pointer over it shows round arrows
 * at its ends, which move it most of a page; a trackpad's sideways swipe, or Shift and the wheel,
 * scroll it, while the wheel alone still scrolls the page.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Strip(side: Dp, content: LazyListScope.() -> Unit) {
    val strip = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(
        Modifier
            .hoverable(hover)
            .onPointerEvent(PointerEventType.Scroll, PointerEventPass.Initial) { event ->
                val change = event.changes.firstOrNull() ?: return@onPointerEvent
                val delta = change.scrollDelta
                val sideways = when {
                    delta.x != 0f -> delta.x
                    event.keyboardModifiers.isShiftPressed -> delta.y
                    else -> 0f
                }
                if (sideways == 0f) return@onPointerEvent
                scope.launch { strip.scrollBy((if (rtl) -sideways else sideways) * WHEEL_STEP_PX) }
                event.changes.forEach { it.consume() }
            },
    ) {
        // Cards scrolled past the margins fade out there rather than being cut off at the sidebar,
        // and a card the keyboard moves to stays inside them.
        WithinMargins(side, side + 8.dp) {
            LazyRow(
                modifier = Modifier.edgeFade(strip, side, side + 8.dp),
                state = strip,
                contentPadding = PaddingValues(start = side, end = side + 8.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(CARD_GAP),
                content = content,
            )
        }
        val shown by animateFloatAsState(if (hovered) 1f else 0f, Motion.standard(Motion.Fast), label = "stripArrows")
        if (shown > 0f) {
            val page = { strip.layoutInfo.viewportSize.width * PAGE_SHARE }
            if (strip.canScrollBackward) {
                StripArrow(TmIcons.ChevronLeft, LocalStrings.current.homeRowScrollBack, Modifier.align(Alignment.CenterStart).padding(start = 8.dp), shown) {
                    scope.launch { strip.animateScrollBy(-page(), Motion.standard(Motion.Slow)) }
                }
            }
            if (strip.canScrollForward) {
                StripArrow(TmIcons.ChevronRight, LocalStrings.current.homeRowScrollOn, Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), shown) {
                    scope.launch { strip.animateScrollBy(page(), Motion.standard(Motion.Slow)) }
                }
            }
        }
    }
}

/** A round arrow over a strip's end. Pointer only, so the keyboard's way through a row stays card to card. */
@Composable
private fun StripArrow(icon: ImageVector, label: String, modifier: Modifier, alpha: Float, onClick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    Box(
        modifier
            .size(STRIP_ARROW)
            .graphicsLayer { this.alpha = alpha }
            .focusProperties { canFocus = false }
            .clip(CircleShape)
            .background(if (hovered) Tone.surfaceHigh else Tone.surfaceHigh.copy(alpha = 0.88f))
            .border(1.dp, Tone.outline, CircleShape)
            .hoverable(interactions)
            .clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Tone.text, modifier = Modifier.size(26.dp))
    }
}

/**
 * A starred chat's row or Recently added: films and shows as 2:3 posters in one strip, every
 * other video as a 16:9 card in a strip under it, each in the row's order, so a row never mixes
 * a tall card with a short one.
 */
@Composable
private fun ShelfStrips(
    state: ShellState,
    side: Dp,
    sizes: CardSizes,
    entries: List<ShelfEntry>,
    chatTitle: (Long) -> String,
    onOpenSeries: (Series) -> Unit,
) {
    val (posters, videos) = remember(entries) { entries.partition(SeriesShelf::isPoster) }
    if (posters.isNotEmpty()) Strip(side) { shelf(state, posters, sizes.poster, poster = true, chatTitle, onOpenSeries) }
    if (videos.isNotEmpty()) Strip(side) { shelf(state, videos, sizes.card, poster = false, chatTitle, onOpenSeries) }
}

private fun LazyListScope.shelf(
    state: ShellState,
    entries: List<ShelfEntry>,
    width: Dp,
    poster: Boolean,
    chatTitle: (Long) -> String,
    onOpenSeries: (Series) -> Unit,
) {
    items(entries, key = { it.key }, contentType = { if (poster) "poster-${it::class.simpleName}" else it::class }) { entry ->
        Box(Modifier.width(width)) { ShelfCell(state, entry, poster, chatTitle, onOpenSeries) }
    }
}

/** Home's and History's tiles may wear the show's or the film's picture (see LocalOnlineArt); a chat's grid keeps frames. */
@Composable
internal fun OnHome(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalOnlineArt provides true, content = content)
}

/** A poster on Home, as in a chat's grid: three quarters of a card's width. */
private const val POSTER_SHARE = 0.75f

/** A Continue watching card, a quarter wider than a card: it carries its name on the art. */
private const val CONTINUE_SHARE = 1.25f

/** Between two cards in a strip or a grid. */
private val CARD_GAP = 16.dp

/** How much of the next card a strip shows past its whole ones. */
private const val PEEK = 0.3f

/** How much of a strip's width one of its arrows moves it. */
private const val PAGE_SHARE = 0.8f

/** A strip's arrow buttons. */
private val STRIP_ARROW = 44.dp

/** Pixels a strip moves per step of the wheel or the trackpad's sideways delta. */
private const val WHEEL_STEP_PX = 64f
