package com.tmplayer.desktop.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tmplayer.data.ChatSummary
import com.tmplayer.data.HomeRow
import com.tmplayer.data.HomeRows
import com.tmplayer.data.MediaItem
import com.tmplayer.data.MediaMapper
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
import com.tmplayer.ui.browse.HistoryTab
import com.tmplayer.ui.browse.HomeSpotlight
import com.tmplayer.ui.browse.HomeViewModel
import com.tmplayer.ui.browse.NewBadge
import com.tmplayer.ui.browse.SpotlightState
import com.tmplayer.ui.browse.rememberHomeRows
import com.tmplayer.ui.browse.rememberSpotlight
import com.tmplayer.ui.components.UiState
import com.tmplayer.ui.i18n.LocalStrings
import com.tmplayer.ui.online.LocalOnlineArt
import com.tmplayer.ui.online.OnlineArt
import com.tmplayer.ui.theme.Tone
import kotlinx.coroutines.flow.first

/**
 * Home on the desktop: the same spotlight and rows as the phone and the television (see
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
 * The spotlight first, when the newest film or show the viewer is part way through has a picture
 * worth it (see [rememberSpotlight]): the picture on the end of the page under its fades, and the
 * chat, the name, the facts, Resume and Details on the start side. Then the rows: Continue
 * watching as wide cards with the name on the art, and in a starred chat's row and in Recently
 * added the films and shows as 2:3 posters in one strip with the other videos as cards in a strip
 * under it. Hovering a tile does not move the spotlight: a pointer passing over the rows would
 * make it flicker.
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
    val shown = openSeries
    if (shown != null) {
        SeriesPage(state, shown, rememberSeriesWatch(state), onClose = { openSeries = null })
        return
    }
    val titles = remember(chats) { chats.associate { it.id to it.title } }
    LaunchedEffect(titles) { state.noteChatTitles(chats) }
    val candidates = remember(rows) {
        rows.filterIsInstance<HomeRow.Continue>().firstOrNull()?.let { HomeRows.spotlight(it.records + it.more) }.orEmpty()
    }
    val spotlight = (rememberSpotlight(candidates, art, onArtWanted) as? SpotlightState.Ready)?.spotlight
    Column(Modifier.fillMaxSize()) {
        PageHeader(
            BrowseTab.Home.heading,
            BrowseTab.Home.blurb,
            actions = {
                IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, contentDescription = s.commonRefresh) }
                PosterSizeStep(state)
            },
        )
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
            val heroHeight = (maxHeight * HERO_SHARE).coerceIn(HERO_MIN, HERO_MAX)
            val side = if (maxWidth > WIDE_WINDOW) 32.dp else 24.dp
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (spotlight != null) {
                    item(key = "spotlight", contentType = "spotlight") {
                        val record = spotlight.record
                        val chatTitle = record.chatTitle.ifEmpty { titles[record.chatId].orEmpty() }
                        HomeSpotlight(
                            spotlight = spotlight,
                            chatTitle = chatTitle,
                            wide = true,
                            height = heroHeight,
                            start = side,
                            end = side,
                            onResume = { state.openPlayer(spotlight.item, startFromBeginning = false) },
                            onDetails = {
                                state.openDetail(DetailRequest(item = spotlight.item, chatTitle = chatTitle, outsideChat = true))
                            },
                        )
                    }
                }
                items(rows, key = { it.key }, contentType = { it::class }) { row ->
                    val loaded = when (row) {
                        is HomeRow.Continue, is HomeRow.WatchLater, is HomeRow.NewEpisodes -> true
                        is HomeRow.Chat -> row.loaded
                        is HomeRow.Recent -> row.loaded
                    }
                    LaunchedEffect(row.key, loaded) { if (!loaded) onRowShown(row) }
                    val heading: Pair<String, (() -> Unit)?> = when (row) {
                        is HomeRow.Continue -> s.homeRowContinue to { state.showHistory(HistoryTab.Continue) }
                        is HomeRow.WatchLater -> s.homeRowWatchLater to { state.showHistory(HistoryTab.WatchLater) }
                        is HomeRow.Chat -> row.title to { chats.firstOrNull { it.id == row.chatId }?.let(state::openChat); Unit }
                        is HomeRow.NewEpisodes -> s.homeRowNewEpisodes to null
                        is HomeRow.Recent -> s.homeRowRecent to null
                    }
                    // Only a row that leaves something out offers the rest of it.
                    val title = heading.first
                    val seeAll = heading.second?.takeIf { row.hasMore }
                    Column {
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
                            if (seeAll != null) {
                                TextButton(onClick = seeAll) {
                                    Text(s.homeSeeAll)
                                    Text(
                                        "  ·  " + if (row.totalAtLeast) s.homeSeeAllCountAtLeast(row.total) else s.homeSeeAllCount(row.total),
                                        color = Tone.muted,
                                    )
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        if (!loaded) {
                            Row(Modifier.padding(start = side, top = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                // A card's art and its caption in the same type with nothing in it, so the
                                // rows below stay put when this one fills.
                                repeat(4) {
                                    Column(Modifier.width(state.posterWidth), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                            return@Column
                        }
                        when (row) {
                            is HomeRow.Continue -> Strip(side) {
                                items(row.records, key = { "r-${it.chatId}_${it.messageId}" }) { record ->
                                    val key = SettingsStore.progressKey(record.chatId, record.messageId)
                                    LaunchedEffect(key) { onArtWanted(record) }
                                    LaunchedEffect(key) { state.noteChatTitle(record.chatId, record.chatTitle) }
                                    val item = art[key] ?: remember(record) { record.toMediaItem() }
                                    val chatTitle = record.chatTitle.ifEmpty { titles[record.chatId].orEmpty() }
                                    Box(Modifier.width(state.posterWidth * CONTINUE_SHARE)) {
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
                                            subtitle = listOfNotNull(
                                                chatTitle.ifBlank { null },
                                                item.qualityTags.firstOrNull(),
                                                item.sizeBytes.takeIf { it > 0 }?.let { MediaMapper.formatSize(it) },
                                            ).joinToString("  ·  "),
                                            onDetails = { refocus ->
                                                state.openDetail(DetailRequest(item = item, chatTitle = chatTitle, outsideChat = true, onClosed = refocus))
                                            },
                                        )
                                    }
                                }
                            }
                            // The card a chat's grid draws, so the pane offers Remove from Watch later.
                            is HomeRow.WatchLater -> Strip(side) {
                                items(row.records, key = { "l-${it.key}" }) { record ->
                                    LaunchedEffect(record.key) { onLaterArtWanted(record) }
                                    LaunchedEffect(record.key) { state.noteChatTitle(record.chatId, record.chatTitle) }
                                    val item = art[record.key] ?: remember(record) { record.toMediaItem() }
                                    Box(Modifier.width(state.posterWidth)) {
                                        OnHome { MediaTile(state, item, record.chatTitle, outsideChat = true) }
                                    }
                                }
                            }
                            is HomeRow.NewEpisodes -> Strip(side) {
                                items(row.episodes, key = { "n-${it.item.id}" }) { episode ->
                                    val item = episode.item
                                    val chatTitle = titles[item.chatId].orEmpty()
                                    Box(Modifier.width(state.posterWidth)) {
                                        Poster(
                                            state = state,
                                            nav = null,
                                            index = 0,
                                            item = item,
                                            chatTitle = chatTitle,
                                            progress = null,
                                            badge = { NewBadge(compact = false) },
                                            art = { OnHome { OnlineArt(item, Modifier.fillMaxSize()) { RecordArt(item) } } },
                                            // Which episode and that it is new, then where it was posted.
                                            subtitle = listOf(
                                                if (episode.show.isBlank()) s.homeNewEpisode(episode.code) else "${episode.show} ${s.homeNewEpisode(episode.code)}",
                                                chatTitle,
                                            ).filter { it.isNotBlank() }.joinToString("  ·  "),
                                            onDetails = { refocus ->
                                                state.openDetail(DetailRequest(item = item, chatTitle = chatTitle, outsideChat = true, onClosed = refocus))
                                            },
                                        )
                                    }
                                }
                            }
                            is HomeRow.Chat -> ShelfStrips(state, side, row.entries, { row.title }) { openSeries = it }
                            is HomeRow.Recent -> ShelfStrips(state, side, row.entries, { titles[it].orEmpty() }) { openSeries = it }
                        }
                    }
                }
            }
        }
    }
}

/** One horizontal strip of a row, lined up with the heading. */
@Composable
private fun Strip(side: Dp, content: LazyListScope.() -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(start = side, end = side + 8.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
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
    entries: List<ShelfEntry>,
    chatTitle: (Long) -> String,
    onOpenSeries: (Series) -> Unit,
) {
    val (posters, videos) = remember(entries) { entries.partition(SeriesShelf::isPoster) }
    if (posters.isNotEmpty()) Strip(side) { shelf(state, posters, poster = true, chatTitle, onOpenSeries) }
    if (videos.isNotEmpty()) Strip(side) { shelf(state, videos, poster = false, chatTitle, onOpenSeries) }
}

private fun LazyListScope.shelf(
    state: ShellState,
    entries: List<ShelfEntry>,
    poster: Boolean,
    chatTitle: (Long) -> String,
    onOpenSeries: (Series) -> Unit,
) {
    items(entries, key = { it.key }, contentType = { if (poster) "poster-${it::class.simpleName}" else it::class }) { entry ->
        Box(Modifier.width(if (poster) state.posterWidth * POSTER_SHARE else state.posterWidth)) {
            when (entry) {
                is ShelfEntry.File -> OnHome { MediaTile(state, entry.item, chatTitle(entry.item.chatId), outsideChat = true, poster = poster) }
                is ShelfEntry.Show -> SeriesPoster(entry.series, rememberSeriesWatch(state), onOpen = { onOpenSeries(entry.series) }, poster = poster)
            }
        }
    }
}

/** Home's tiles may wear the show's or the film's picture (see LocalOnlineArt); a chat's grid keeps frames. */
@Composable
private fun OnHome(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalOnlineArt provides true, content = content)
}

/** A poster on Home, as in a chat's grid: three quarters of a card's width. */
private const val POSTER_SHARE = 0.75f

/** A Continue watching card, a quarter wider than a card: it carries its name on the art. */
private const val CONTINUE_SHARE = 1.25f

/** The spotlight's share of the window's height, within [HERO_MIN] and [HERO_MAX]. */
private const val HERO_SHARE = 0.5f
private val HERO_MIN = 360.dp
private val HERO_MAX = 440.dp

/** Past this width the page's side margin grows from 24 to 32 dp. */
private val WIDE_WINDOW = 1440.dp
