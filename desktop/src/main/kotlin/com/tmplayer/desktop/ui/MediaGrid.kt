package com.tmplayer.desktop.ui

import com.tmplayer.ui.browse.HistoryTab
import com.tmplayer.ui.browse.BrowseTab
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.tmplayer.i18n.L
import com.tmplayer.ui.i18n.LocalStrings
import com.tmplayer.ui.theme.Focus
import com.tmplayer.data.DiskInfo
import com.tmplayer.desktop.DesktopPaths
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.border
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed as keyCtrl
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import com.tmplayer.ui.components.TmAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import com.tmplayer.ui.components.TmDropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tmplayer.data.ChatSummary
import com.tmplayer.data.ContentProtection
import com.tmplayer.data.MediaItem
import com.tmplayer.data.MediaMapper
import com.tmplayer.data.OfflineDownloads
import com.tmplayer.data.ResumeRecord
import com.tmplayer.data.SettingsStore
import com.tmplayer.online.MetaNotice
import com.tmplayer.online.OnlineMetadata
import com.tmplayer.data.SponsoredItem
import com.tmplayer.data.SponsoredReportOption
import com.tmplayer.data.SponsoredReportOutcome
import com.tmplayer.data.Td
import com.tmplayer.data.valueOrNull
import com.tmplayer.data.WatchPoint
import com.tmplayer.desktop.WatchedWords
import com.tmplayer.desktop.setWatchLater
import com.tmplayer.desktop.setWatched
import com.tmplayer.ui.components.WatchedBadge
import com.tmplayer.player.StreamStats
import com.tmplayer.ui.browse.MediaListViewModel
import com.tmplayer.ui.browse.topicMemory
import com.tmplayer.ui.components.MediaArt
import com.tmplayer.ui.components.TmIcons
import com.tmplayer.ui.components.UiState
import com.tmplayer.ui.components.rememberToast
import com.tmplayer.ui.theme.Corner
import com.tmplayer.ui.theme.Tone
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import com.tmplayer.desktop.os.OpenExternal
import com.tmplayer.desktop.DownloadIndex
import com.tmplayer.ui.browse.CourseHeader
import com.tmplayer.ui.nav.BackHandler
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import java.io.File
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.text.font.FontWeight
import com.tmplayer.data.MediaName
import com.tmplayer.data.SeriesShelf
import com.tmplayer.ui.components.GeneratedCover
import com.tmplayer.ui.components.ProgressLine
import com.tmplayer.ui.components.ProgressLineInset
import com.tmplayer.ui.components.TileBadge
import com.tmplayer.ui.components.TileBadgeInset
import com.tmplayer.ui.components.rememberCover
import com.tmplayer.ui.theme.Motion
import com.tmplayer.ui.theme.Scrims
import com.tmplayer.ui.theme.rememberReducedMotion
import com.tmplayer.ui.components.PosterArt

/**
 * One chat's videos as a grid of posters: the shared [MediaListViewModel] (paging, search, size
 * limits, sponsored messages), [GridCells.Adaptive] on the poster width the toolbar steps, a
 * scrollbar, and the next page fetched as the end comes into view.
 */
@Composable
fun MediaGridPage(state: ShellState, chat: ChatSummary) {
    val s = LocalStrings.current
    val minSize by state.settings.minSizeBytes.collectAsState(initial = null)
    val maxSize by state.settings.maxSizeBytes.collectAsState(initial = null)
    val min = minSize
    val max = maxSize
    Column(Modifier.fillMaxSize()) {
        if (min == null || max == null) {
            Centred { CircularProgressIndicator() }
            return@Column
        }
        val model = rememberViewModel(Triple(chat.id, min, max)) { MediaListViewModel(chat.id, min, max, onSearched = { state.settings.addRecentSearch(it) }, downloadedIds = { DownloadIndex.presentIds(state.settings) }, topicMemory = state.settings.topicMemory()) }
        val ui by model.state.collectAsState()
        var query by remember(chat.id) { mutableStateOf("") }
        var openSeries by remember(chat.id) { mutableStateOf<com.tmplayer.data.Series?>(null) }
        // A show's seasons and episodes take the page, as they do from Home; Close comes back here.
        openSeries?.let { shown ->
            SeriesPage(state, shown, rememberSeriesWatch(state), onClose = { openSeries = null })
            return@Column
        }
        // The grid's keyboard focus, once the grid exists; the search field's Down arrow enters it.
        var gridNav by remember(chat.id) { mutableStateOf<KeyboardNav?>(null) }
        LaunchedEffect(model, query) {
            delay(SEARCH_SETTLE_MS)
            model.search(query.trim())
        }
        LaunchedEffect(chat.id) { state.settings.rememberChatOpened(chat.id) }

        PageHeader(
            title = chat.title,
            subtitle = (ui as? UiState.Content)?.value?.items?.size?.let { s.browseVideosCount(it) },
            leading = {
                IconButton(onClick = { state.closeChat() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.browseBackToChats)
                }
                ChatAvatar(chat.miniThumbnail, chat.photoFileId, chat.title, 40.dp)
            },
            actions = {
                SearchField(query, { query = it }, s.browseSearchChat, state.searchFocus, Modifier.width(300.dp), onDown = { gridNav?.focus(0) })
                PosterSizeStep(state)
                IconButton(onClick = { model.load() }) { Icon(Icons.Filled.Refresh, contentDescription = s.commonRefresh) }
            },
        )
        val recent by state.settings.recentSearches.collectAsState(initial = emptyList())
        val scope = rememberCoroutineScope()
        if (query.isBlank()) {
            RecentSearchRow(recent, onPick = { query = it }, onClear = { scope.launch { state.settings.clearRecentSearches() } })
        }
        val topicBar by model.topics.collectAsState()
        topicBar?.let { bar ->
            com.tmplayer.ui.browse.TopicTabs(bar, model::selectTopic, Modifier.padding(bottom = 12.dp), edge = 24.dp)
        }
        StateBox(ui, onRetry = { model.load() }, onAction = model::act) { content ->
            val grid = rememberLazyGridState()
            LoadMoreNearEnd(grid, enabled = !content.endReached && !content.loadingMore) { model.loadMore() }
            val ad = content.sponsored?.messages?.firstOrNull()
            // A show as one poster that opens its seasons, and copies of one film (720p, 1080p,
            // HEVC) as one poster with the rest a pick in its details. While searching, every
            // episode stands on its own so the one searched for can be found.
            val grouping = query.isBlank()
            val arranged = remember(content.items, grouping) {
                if (grouping) {
                    com.tmplayer.data.SeriesShelf.arrange(content.items)
                } else {
                    com.tmplayer.data.SeriesShelf.foldFilmCopies(content.items).map { com.tmplayer.data.ShelfEntry.File(it) }
                }
            }
            // Films and shows first as one block of 2:3 posters, then every other video, each in
            // the chat's own order, so tall and short cards meet in one row at most.
            val posters = remember(arranged) {
                arranged.filter(SeriesShelf::isPoster).mapTo(HashSet()) { it.key }
            }
            val cells = remember(arranged, posters) {
                (arranged.filter { it.key in posters } + arranged.filter { it.key !in posters })
                    .map { if (it is com.tmplayer.data.ShelfEntry.Show) it.series.cover else (it as com.tmplayer.data.ShelfEntry.File).item }
            }
            val posterIds = remember(arranged, posters) {
                arranged.filter { it.key in posters }.mapTo(HashSet()) {
                    if (it is com.tmplayer.data.ShelfEntry.Show) it.series.cover.id else (it as com.tmplayer.data.ShelfEntry.File).item.id
                }
            }
            // A chat or a topic that is one course of lessons says how far through it the viewer
            // is, over the posters; not while searching, when the listing is what was searched for.
            val watch = rememberSeriesWatch(state)
            val course = remember(content.items, watch, grouping) { if (grouping) watch.course(content.items) else null }
            val shows = remember(arranged) {
                arranged.filterIsInstance<com.tmplayer.data.ShelfEntry.Show>().associate { it.series.cover.id to it.series }
            }
            val metaNotice = grouping && metaNoticeShown(state)
            MetaInviteDialog(state)
            VideoGrid(
                state = state,
                items = cells,
                series = shows,
                onOpenSeries = { openSeries = it },
                posters = posterIds,
                chatTitle = chat.title,
                grid = grid,
                onNav = { gridNav = it },
                // Online metadata is on by default, so the first grid says once where posters come
                // from; not while searching, when the listing is what was searched for.
                header = if (ad == null && course == null && !metaNotice) null else {
                    {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (metaNotice) MetaNoticeCard(state)
                            course?.let { CourseHeader(it, topicBar?.let { bar -> bar.nameOf(bar.selected) } ?: chat.title, onPlay = { item -> state.openPlayer(item, startFromBeginning = false) }) }
                            ad?.let { SponsoredCard(it, model) }
                        }
                    }
                },
                loadingMore = content.loadingMore,
            )
        }
    }
}

/**
 * A chat's posters in an adaptive grid with a scrollbar, the arrow keys of [KeyboardNav] over
 * them, an optional full width [header] (the sponsored message) and a spinner row while the next
 * page loads. Split from [MediaGridPage] so the UI tests can drive it without TDLib.
 */
@Composable
internal fun VideoGrid(
    state: ShellState,
    items: List<MediaItem>,
    chatTitle: String,
    grid: LazyGridState = rememberLazyGridState(),
    onNav: (KeyboardNav) -> Unit = {},
    header: (@Composable () -> Unit)? = null,
    loadingMore: Boolean = false,
    /** Shows by the id of their cover, the item standing in for each in [items]. */
    series: Map<String, com.tmplayer.data.Series> = emptyMap(),
    onOpenSeries: (com.tmplayer.data.Series) -> Unit = {},
    /** A video's own mark for its poster's top end corner, a "New" say, or null for none. */
    badge: (MediaItem) -> (@Composable () -> Unit)? = { null },
    /**
     * The ids of the cells drawn as 2:3 posters, films and shows. With any, every cell takes a
     * poster's width (about three quarters of [ShellState.posterWidth]), so the arrow keys still
     * move one column at a time; the videos after the posters are then smaller 16:9 cards.
     */
    posters: Set<String> = emptySet(),
) {
    val watch = if (series.isEmpty()) null else rememberSeriesWatch(state)
    // Every file on its own, episodes included: a chat lists what was posted, one by one.
    val cells by rememberUpdatedState(items)
    val headerItems by rememberUpdatedState(if (header != null) 1 else 0)
    val nav = rememberKeyboardNav(remember(grid) { GridSurface(grid, { headerItems }, { cells.size }) })
    LaunchedEffect(nav) { onNav(nav) }
    val history by state.settings.downloadHistory.collectAsState(initial = emptyList())
    val index = remember(history) { history.associateBy { "${it.chatId}:${it.messageId}" } }
    val selection = remember(grid) { GridSelection { cells.getOrNull(it)?.id } }
    BackHandler(enabled = selection.active) { selection.clear() }
    Box(Modifier.fillMaxSize()) {
      CompositionLocalProvider(LocalGridSelection provides selection, LocalDownloadIndex provides index) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (posters.isEmpty()) state.posterWidth else state.posterWidth * POSTER_SHARE),
            state = grid,
            contentPadding = PaddingValues(start = 24.dp, end = 32.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize().navKeys(nav) { cells.size },
        ) {
            if (header != null) {
                item(key = "sponsored", span = { GridItemSpan(maxLineSpan) }) { header() }
            }
            itemsIndexed(items, key = { _, it -> "media-${it.id}" }) { index, item ->
                val show = series[item.id]
                if (show != null && watch != null) {
                    SeriesPoster(show, watch, onOpen = { onOpenSeries(show) }, nav = nav, index = index, poster = item.id in posters)
                } else {
                    MediaTile(state, item, chatTitle, nav, index, badge = badge(item), poster = item.id in posters)
                }
            }
            if (loadingMore) {
                item(key = "more", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                    }
                }
            }
        }
      }
        VerticalScrollbar(
            rememberScrollbarAdapter(grid),
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp),
        )
        if (selection.active) {
            SelectionBar(state, selection, items, chatTitle, index, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
        }
    }
}

/** Calls [loadMore] once the last few posters are on screen. */
@Composable
internal fun LoadMoreNearEnd(grid: LazyGridState, enabled: Boolean, loadMore: () -> Unit) {
    LaunchedEffect(grid, enabled) {
        if (!enabled) return@LaunchedEffect
        snapshotFlow {
            val info = grid.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - LOAD_AHEAD
        }.distinctUntilChanged().collect { near -> if (near) loadMore() }
    }
}

/** The toolbar's poster size step: smaller and larger, through [POSTER_STEPS]. */
@Composable
fun PosterSizeStep(state: ShellState) {
    val s = LocalStrings.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { state.stepPoster(-1) }, enabled = state.posterWidth != POSTER_STEPS.first()) {
            Icon(TmIcons.Grid, contentDescription = s.browseSmallerPosters)
        }
        IconButton(onClick = { state.stepPoster(1) }, enabled = state.posterWidth != POSTER_STEPS.last()) {
            Icon(Icons.Filled.Add, contentDescription = s.browseLargerPosters)
        }
    }
}

/**
 * History's Continue tab: what the viewer was part way through, newest first, as posters.
 * The records keep no artwork (see the phone's ContinueArt), so each poster is the play mark.
 * [tabs] is History's tab row, drawn under the heading.
 */
@Composable
fun ContinuePage(state: ShellState, tabs: @Composable () -> Unit = {}) {
    val s = LocalStrings.current
    val records by state.settings.continueWatching.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val toast = rememberToast()
    // The card waiting on "Remove from Continue watching?": its saved place cannot be got back.
    var forgetting by remember { mutableStateOf<ResumeRecord?>(null) }
    forgetting?.let { record ->
        ConfirmDialog(
            title = s.confirmForgetResumeTitle,
            message = s.confirmForgetResumeMessage(record.title),
            detail = s.browseForgetResumeDetail,
            confirmLabel = s.commonRemove,
            onDismiss = { forgetting = null },
            onConfirm = {
                forgetting = null
                scope.launch { state.settings.clearResumePosition(record.chatId, record.messageId) }
            },
        )
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader(BrowseTab.History.heading, HistoryTab.Continue.blurb, actions = { PosterSizeStep(state) })
        tabs()
        val list = records
        when {
            list == null -> Centred { CircularProgressIndicator() }
            list.isEmpty() -> Centred { Text(s.continueEmpty, color = Tone.muted) }
            else -> {
                val grid = rememberLazyGridState()
                val records by rememberUpdatedState(list)
                val nav = rememberKeyboardNav(remember(grid) { GridSurface(grid, { 0 }, { records.size }) })
                Box(Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        // A quarter wider than a poster: the card carries its title on the art.
                        columns = GridCells.Adaptive(state.posterWidth * CONTINUE_WIDTH),
                        state = grid,
                        contentPadding = PaddingValues(start = 24.dp, end = 32.dp, top = 8.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.fillMaxSize().navKeys(nav) { records.size },
                    ) {
                        itemsIndexed(list, key = { _, it -> "${it.chatId}:${it.messageId}" }) { index, record ->
                            LaunchedEffect(record) { state.noteChatTitle(record.chatId, record.chatTitle) }
                            ContinueTile(
                                state, record, nav, index,
                                onMarkWatched = {
                                    // On the page's scope: marking takes the card off this page.
                                    val item = record.toMediaItem()
                                    toast(s.watchedMarkedWatched(item.title))
                                    scope.launch {
                                        runCatching { setWatched(state.watched, state.settings, item, record.chatTitle, watched = true) }
                                    }
                                },
                            ) {
                                forgetting = record
                            }
                        }
                    }
                    VerticalScrollbar(rememberScrollbarAdapter(grid), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                }
            }
        }
    }
}

/**
 * A video part way through, as a card a quarter wider than a poster: the art with the episode
 * ("S1 E3") and the title written on it over the tile's foot scrim, how long is left under it, and
 * the progress line at its foot. The records keep no picture, so the art is the video's
 * [GeneratedCover] unless a thumbnail turns up.
 */
@Composable
private fun ContinueTile(
    state: ShellState,
    record: ResumeRecord,
    nav: KeyboardNav?,
    index: Int,
    onMarkWatched: () -> Unit,
    onForget: () -> Unit,
) {
    val s = LocalStrings.current
    val item = remember(record) { record.toMediaItem() }
    Poster(
        state = state,
        nav = nav,
        index = index,
        item = item,
        chatTitle = record.chatTitle,
        progress = record.fraction,
        art = { ContinueArt(item) },
        titleBelow = false,
        subtitle = s.continueLeft(record.chatTitle, MediaMapper.formatDuration((record.remainingMs / 1000).toInt())),
        extraMenu = { close ->
            // Marking also forgets the position, so the card leaves this page with it.
            DropdownMenuItem(text = { Text(WatchedWords.markLabel(onList = false)) }, onClick = {
                close()
                onMarkWatched()
            })
            DropdownMenuItem(text = { Text(s.continueRemove) }, onClick = { close(); onForget() })
        },
        markToggle = false,
    )
}

/** A continue card's art: the picture or the cover, and the episode and the title written on it. */
@Composable
private fun ContinueArt(item: MediaItem) {
    val s = LocalStrings.current
    val cover = rememberCover(item)
    val parsed = remember(item) { MediaName.parse(item.nameForParsing, item.caption) }
    val title = remember(item) { SeriesShelf.tileTitle(item) }
    // An episode is named by its show, with the episode above it; anything else by its own name.
    val episode = parsed.episode?.let { number -> parsed.season?.let { s.seriesEpisodeShort(it, number) } ?: s.seriesEpisode(number) }
    val name = if (episode != null) parsed.title.ifBlank { title } else title
    Box(Modifier.fillMaxSize()) {
        MediaArt(item.miniThumbnail, item.thumbnailFileId, Modifier.fillMaxSize()) {
            GeneratedCover(cover, Modifier.fillMaxSize(), showTitle = false)
        }
        Box(Modifier.fillMaxSize().background(Scrims.tileFoot))
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp + ProgressLineInset),
        ) {
            if (episode != null) {
                Text(
                    episode,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Tone.onArt,
                    maxLines = 1,
                )
            }
            Text(
                name,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Tone.onArt,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The art of a saved record's poster (Watched, Watch later): the thumbnail when the item carries
 * one, else the video's [GeneratedCover], rather than a play mark on a grey square.
 */
@Composable
internal fun RecordArt(item: MediaItem) {
    MediaArt(item.miniThumbnail, item.thumbnailFileId, Modifier.fillMaxSize(), cover = rememberCover(item))
}

/** A video in a chat's grid. */
@Composable
internal fun MediaTile(
    state: ShellState,
    item: MediaItem,
    chatTitle: String,
    nav: KeyboardNav? = null,
    index: Int = 0,
    /** On Home or in the search across chats, where the pane offers to go to the chat. */
    outsideChat: Boolean = false,
    /** The caller's own mark for the art's top end corner, a "New" say. */
    badge: (@Composable () -> Unit)? = null,
    /** A film's 2:3 poster card ([SeriesShelf.isFilm]); the caller says, as only it knows the grid. */
    poster: Boolean = false,
) {
    val s = LocalStrings.current
    // The grid's selection, when there is one, so the pane can start one with this poster.
    val selection = LocalGridSelection.current?.takeIf { item.canBeSaved }
    val progress by state.settings.watchProgress.collectAsState(initial = emptyMap())
    val key = SettingsStore.progressKey(item.chatId, item.messageId)
    val point: WatchPoint? = progress[key]
    val watched by state.watched.watched.collectAsState(initial = emptyMap())
    val finished = key in watched
    Poster(
        state = state,
        nav = nav,
        index = index,
        item = item,
        chatTitle = chatTitle,
        // Only a saved place draws the line; a finished video says so with its tick.
        progress = point?.fraction,
        finished = finished,
        badge = badge,
        poster = poster,
        onDetails = { refocus ->
            state.openDetail(
                DetailRequest(
                    item = item,
                    chatTitle = chatTitle,
                    outsideChat = outsideChat,
                    selectThis = selection?.let { picking -> { if (!picking.isSelected(item.id)) picking.toggle(index) } },
                    onClosed = refocus,
                ),
            )
        },
        art = {
            if (poster) {
                val meta = com.tmplayer.ui.online.rememberMeta(item, showOnly = true)
                PosterArt(item.miniThumbnail, item.thumbnailFileId, rememberCover(item), meta?.posterUrl, Modifier.fillMaxSize())
            } else {
                // On Home, the show's or the film's own picture over the frame once a match arrives.
                com.tmplayer.ui.online.OnlineArt(item, Modifier.fillMaxSize()) {
                    MediaArt(item.miniThumbnail, item.thumbnailFileId, Modifier.fillMaxSize(), cover = rememberCover(item))
                }
            }
        },
        subtitle = buildString {
            // A poster leads with the film's year and leaves the quality to the chip on its art.
            if (poster) MediaName.parse(item.nameForParsing, item.caption).year?.let { append(it).append("  ·  ") }
            if (!poster && item.durationSec > 0) append(MediaMapper.formatDuration(item.durationSec)).append("  ·  ")
            append(if (item.versions.size > 1) s.detailVersions(item.versions.size) else MediaMapper.formatSize(item.sizeBytes))
            if (!poster) item.qualityTags.firstOrNull()?.let { append("  ·  ").append(it) }
            if (WatchedWords.showsWatched(point?.fraction, finished)) append("  ·  ").append(s.watchedBadge)
        },
    )
}

/**
 * The poster every grid draws: art at 16:9 with no panel round it, a progress line on its foot, a
 * line of name and a line of facts under it. Hovering for a moment lifts it by [Motion.FocusScale]
 * and shows Play and the overflow; a right click (Ctrl+click on macOS) opens the same overflow
 * where the pointer is. The line of facts goes from muted to the full text colour while lifted.
 *
 * One badge in the art's top start corner at most: selected, else the download (kept, coming, or
 * cached), else the watched tick. [badge] is the caller's own for the top end corner, which the
 * overflow button takes over while the poster is lifted.
 *
 * From the keyboard ([nav], B2.1): focus draws a ring and lifts it like a hover, the arrows move
 * between posters, Enter, Space or P plays, and the context menu key or Shift+F10 opens the
 * overflow.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun Poster(
    state: ShellState,
    nav: KeyboardNav?,
    index: Int,
    item: MediaItem,
    chatTitle: String,
    progress: Float?,
    art: @Composable () -> Unit,
    subtitle: String,
    extraMenu: (@Composable (close: () -> Unit) -> Unit)? = null,
    /** On the Watched list, or marked watched: the tick, when no other badge has the corner. */
    finished: Boolean = false,
    /** False for a card that writes its title on the art itself, and keeps only [subtitle] under it. */
    titleBelow: Boolean = true,
    badge: (@Composable () -> Unit)? = null,
    /** A film's 2:3 poster card rather than a video's 16:9 one, named without its year. */
    poster: Boolean = false,
    /** Whether the menu offers "Mark as watched" or "Mark as unwatched" by [finished]. */
    markToggle: Boolean = true,
    /** The menu's play, download and link lines; off for a page whose [extraMenu] is the menu. */
    fileMenu: Boolean = true,
    /**
     * Opens the detail pane in place of the menu: a right click, the context menu key, I and the
     * overflow button all come here. Handed a way to put focus back on this poster when it closes.
     * Null keeps the menu, as Continue watching and Watched do.
     */
    onDetails: ((refocus: () -> Unit) -> Unit)? = null,
) {
    val s = LocalStrings.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var lifted by remember { mutableStateOf(false) }
    // Hover intent: a pointer passing over on its way somewhere else does not lift every poster.
    LaunchedEffect(hovered) {
        if (hovered) {
            delay(HOVER_INTENT_MS)
            lifted = true
        } else {
            lifted = false
        }
    }
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if ((lifted || focused) && !rememberReducedMotion()) Motion.FocusScale else 1f,
        animationSpec = Motion.standard(Motion.Fast),
    )
    val ink by animateColorAsState(
        targetValue = if (lifted || focused) Tone.text else Tone.muted,
        animationSpec = Motion.standard(Motion.Fast),
    )
    var menu by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    // A video its chat will not let anyone keep cannot be picked for Download selected.
    val selection = LocalGridSelection.current?.takeIf { item.canBeSaved }
    // Set by a Ctrl or Shift press just before the click it belongs to, so that click picks the
    // poster instead of playing it.
    var picking by remember { mutableStateOf(false) }
    val play = {
        if (picking && selection != null) {
            picking = false
        } else {
            state.openPlayer(item, startFromBeginning = false)
        }
    }
    val downloads by OfflineDownloads.active.collectAsState()
    val download = downloads[item.fileId]
    val record = LocalDownloadIndex.current[item.id]
    val selected = selection?.isSelected(item.id) == true
    val details: (() -> Unit)? = onDetails?.let { open -> { open { runCatching { requester.requestFocus() } } } }
    val openMenu: () -> Unit = {
        if (details != null) {
            details()
        } else {
            menu = true
        }
    }

    Column(
        Modifier
            .hoverable(interaction)
            .onPointerEvent(PointerEventType.Press) { event ->
                val macContext = IS_MAC && event.keyboardModifiers.isCtrlPressed && event.buttons.isPrimaryPressed
                if (event.buttons.isSecondaryPressed || macContext) openMenu()
            }
            .onPointerEvent(PointerEventType.Press, PointerEventPass.Initial) { event ->
                if (selection == null || !event.buttons.isPrimaryPressed) return@onPointerEvent
                val mods = event.keyboardModifiers
                val toggle = if (IS_MAC) mods.isMetaPressed else mods.isCtrlPressed
                when {
                    mods.isShiftPressed -> {
                        picking = true
                        selection.extend(index)
                    }
                    toggle -> {
                        picking = true
                        selection.toggle(index)
                    }
                    else -> picking = false
                }
            }
            .then(if (nav != null) Modifier.navCell(nav, index, requester) else Modifier.focusRequester(requester))
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                when {
                    GridNav.isMenuKey(event) -> {
                        openMenu()
                        true
                    }
                    // I for "info", the detail pane, as the remote's info key opens it on a TV.
                    details != null && event.type == KeyEventType.KeyDown && event.key == Key.I && !event.keyCtrl &&
                        !event.isAltPressed && !event.isMetaPressed && !event.isShiftPressed -> {
                        details()
                        true
                    }
                    event.type == KeyEventType.KeyDown && event.key in PLAY_KEYS && !event.keyCtrl &&
                        !event.isAltPressed && !event.isMetaPressed && !event.isShiftPressed -> {
                        play()
                        true
                    }
                    else -> false
                }
            }
            // No hover or press plate: the lift and the ring on the art are the feedback.
            .clickable(interactionSource = interaction, indication = null, onClick = play),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .padding(bottom = 4.dp)
                .fillMaxWidth()
                .aspectRatio(if (poster) 2f / 3f else 16f / 9f)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(MaterialTheme.shapes.medium)
                .then(if (focused || selected) Modifier.border(Focus.Ring, Tone.accent, MaterialTheme.shapes.medium) else Modifier),
        ) {
            art()
            if (progress != null && progress > 0f) {
                ProgressLine(progress, Modifier.align(Alignment.BottomCenter))
            }
            val corner = Modifier.align(Alignment.TopStart).padding(TileBadgeInset)
            when {
                selected -> TileBadge(s.browseBadgeSelected, corner, icon = Icons.Filled.CheckCircle)
                record != null -> TileBadge(s.downloadsDownloaded, corner, icon = Icons.Filled.CheckCircle)
                download != null && download.busy -> TileBadge(s.downloadsDownloading, corner, icon = TmIcons.Download)
                item.onDevice -> TileBadge(s.browseBadgeCached, corner, icon = TmIcons.Download)
                finished -> WatchedBadge(corner)
            }
            val showing = lifted || menu || focused
            // The top end corner: the caller's mark, else on a poster the quality its name states.
            val quality = item.qualityTags.firstOrNull()
            if (!showing) when {
                badge != null -> Box(Modifier.align(Alignment.TopEnd).padding(TileBadgeInset)) { badge() }
                // Only on its own: two chips across a narrow poster would meet in the middle.
                poster && quality != null && !selected && record == null && download?.busy != true && !item.onDevice && !finished -> TileBadge(quality, Modifier.align(Alignment.TopEnd).padding(TileBadgeInset))
            }
            if (showing) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
                Surface(
                    shape = CircleShape,
                    color = Tone.accent,
                    modifier = Modifier.align(Alignment.Center).size(48.dp)
                        .clickable { state.openPlayer(item, startFromBeginning = false) },
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = s.playerPlay, tint = Tone.onAccent, modifier = Modifier.padding(10.dp))
                }
                Box(Modifier.align(Alignment.TopEnd)) {
                    IconButton(onClick = { openMenu() }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = s.commonMore, tint = Color.White)
                    }
                }
            }
            if (details == null) Box(Modifier.align(Alignment.TopEnd)) {
                TileMenu(state, item, chatTitle, expanded = menu, onDismiss = {
                    menu = false
                    // Back to the poster, so the arrows carry on from where the menu was opened.
                    if (focused || nav?.current == index) runCatching { requester.requestFocus() }
                }, extra = extraMenu, watchedToggle = if (markToggle) finished else null, fileMenu = fileMenu)
            }
        }
        // The name cleaned of its release tags, a film of several copies by the film's own name
        // (see SeriesShelf.tileTitle); the detail pane keeps the whole file name.
        if (titleBelow) {
            val title = remember(item, poster) { if (poster) SeriesShelf.posterTitle(item) else SeriesShelf.tileTitle(item) }
            if (poster) {
                Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
            } else {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(
            subtitle,
            style = if (poster) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "Download (12.3 GB free)", or the bare label while the disk has not answered. */
internal fun withFree(label: String, freeBytes: Long): String =
    DiskInfo.freeLabel(freeBytes)?.let { L.commonWithDetail(label, it) } ?: label

/**
 * Play, Play from start, the one download entry for where the video is (Download when it is on
 * Telegram only, Save to Downloads when it is cached, Downloading while it is queued, In Downloads
 * and Remove from Downloads once it is kept), Open in another app for a whole file, Copy link.
 */
@Composable
internal fun TileMenu(
    state: ShellState,
    item: MediaItem,
    chatTitle: String,
    expanded: Boolean,
    onDismiss: () -> Unit,
    extra: (@Composable (close: () -> Unit) -> Unit)?,
    /** Null leaves the mark line out; otherwise whether the video is on the Watched list. */
    watchedToggle: Boolean? = null,
    fileMenu: Boolean = true,
) {
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    val toast = rememberToast()
    val downloads by OfflineDownloads.active.collectAsState()
    val record = LocalDownloadIndex.current[item.id]
    val row = downloads[item.fileId]
    val title = chatTitle.ifBlank { state.chatTitleOf(item.chatId) }
    val laterKeys by state.watched.watchLater.keys.collectAsState(initial = emptySet())
    val later = SettingsStore.progressKey(item.chatId, item.messageId) in laterKeys
    // Measured each time the menu opens, off the UI thread, for the "x GB free" on the download line.
    val free by produceState(0L, expanded) {
        if (expanded) value = withContext(Dispatchers.IO) { DiskInfo.of(DesktopPaths.downloadsDir).freeBytes }
    }

    // Held here rather than in the menu, which is gone by the time the prompt is answered.
    var confirmingRemove by remember { mutableStateOf<ResumeRecord?>(null) }
    confirmingRemove?.let { doomed ->
        ConfirmDialog(
            title = s.gridRemoveDownloadTitle,
            message = s.gridRemoveDownloadMessage(item.title),
            confirmLabel = s.commonRemove,
            onDismiss = { confirmingRemove = null },
            onConfirm = {
                confirmingRemove = null
                scope.launch {
                    if (DownloadIndex.delete(state.settings, doomed)) {
                        toast(s.downloadsRemoved(item.title))
                    } else {
                        toast(s.downloadsInUse)
                    }
                }
            },
        )
    }

    TmDropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
      if (!fileMenu) {
        extra?.invoke(onDismiss)
      } else {
        DropdownMenuItem(text = { Text(s.playerPlay) }, onClick = { onDismiss(); state.openPlayer(item, startFromBeginning = false) })
        DropdownMenuItem(text = { Text(s.playerPlayFromStart) }, onClick = { onDismiss(); state.openPlayer(item, startFromBeginning = true) })
        // Beside the play lines, because it is about watching rather than about the file. Marking
        // also forgets the saved position, so the video leaves Continue watching with it.
        if (watchedToggle != null) {
            DropdownMenuItem(text = { Text(WatchedWords.markLabel(watchedToggle)) }, onClick = {
                onDismiss()
                val mark = !watchedToggle
                scope.launch {
                    runCatching { setWatched(state.watched, state.settings, item, title, mark) }
                    toast(if (mark) s.watchedMarkedWatched(item.title) else s.watchedMarkedUnwatched(item.title))
                }
            })
        }
        // Nothing is copied, so this stays for a chat that restricts saving.
        DropdownMenuItem(text = { Text(if (later) s.detailWatchLaterRemove else s.detailWatchLaterAdd) }, onClick = {
            onDismiss()
            scope.launch {
                runCatching { setWatchLater(state.watched, item, title, add = !later) }
                toast(if (later) s.detailWatchLaterRemoved(item.title) else s.detailWatchLaterAdded(item.title))
            }
        })
        when {
            record != null -> {
                DropdownMenuItem(text = { Text(s.downloadsInDownloads) }, onClick = { onDismiss(); state.go(Destination.Downloads) })
                DropdownMenuItem(text = { Text(s.downloadsRemove) }, onClick = {
                    onDismiss()
                    confirmingRemove = record
                })
            }
            row != null && row.busy -> {
                DropdownMenuItem(text = { Text(s.downloadsDownloadingEllipsis) }, onClick = { onDismiss(); state.go(Destination.Downloads) })
                DropdownMenuItem(text = { Text(s.downloadsCancel) }, onClick = {
                    onDismiss()
                    OfflineDownloads.cancel(state.downloads, item.fileId)
                })
            }
            // A chat with "restrict saving content" lets its videos be watched and nothing more,
            // so neither a download nor, below, a hand off to another app is offered.
            !item.canBeSaved -> Unit
            item.onDevice -> DropdownMenuItem(text = { Text(withFree(s.downloadsSave, free)) }, onClick = {
                onDismiss()
                OfflineDownloads.start(state.downloads, item, title)
                toast(s.downloadsSavingTitle(item.title))
            })
            else -> DropdownMenuItem(text = { Text(withFree(s.downloadsDownload, free)) }, onClick = {
                onDismiss()
                OfflineDownloads.start(state.downloads, item, title)
                toast(s.downloadsDownloadingTitle(item.title))
            })
        }
        if (item.canBeSaved && (record != null || item.onDevice)) {
            DropdownMenuItem(text = { Text(s.commonOpenElsewhere) }, onClick = {
                onDismiss()
                scope.launch {
                    // A tile built from a saved record (Continue watching) does not know the
                    // chat's setting, so the cached copy is only handed over once Telegram agrees.
                    if (record == null && !Td.maySave(item.chatId, item.messageId)) {
                        toast(ContentProtection.NOT_SAVABLE)
                        return@launch
                    }
                    val file = record?.localPath?.let(::File)?.takeIf { it.isFile }
                        ?: runCatching { Td.localFilePath(Td.currentFileId(item.chatId, item.messageId, item.fileId)) }
                            .getOrNull()?.let(::File)
                    if (file == null) toast(s.downloadsFileGone) else OpenExternal.open(file)
                }
            })
        }
        DropdownMenuItem(text = { Text(s.commonCopyLink) }, onClick = {
            onDismiss()
            scope.launch {
                val link = runCatching {
                    Td.client.getMessageLink(item.chatId, item.messageId, 0, 0, "", false, false).valueOrNull?.link
                }.getOrNull()
                if (link.isNullOrBlank()) {
                    toast(s.commonNoLinks)
                } else {
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(link), null)
                    toast(s.commonLinkCopied)
                }
            }
        })
        extra?.invoke(onDismiss)
      }
    }
}

/** Telegram's sponsored message for a channel, shown above its videos as the phone shows it. */
@Composable
private fun SponsoredCard(ad: SponsoredItem, model: MediaListViewModel) {
    val s = LocalStrings.current
    val toast = rememberToast()
    var reportOptions by remember(ad.messageId) { mutableStateOf<Pair<String, List<SponsoredReportOption>>?>(null) }

    fun report(item: SponsoredItem, optionId: ByteArray) {
        model.reportSponsored(
            item = item,
            optionId = optionId,
            onResult = { outcome ->
                when (outcome) {
                    is SponsoredReportOutcome.Options -> reportOptions = outcome.title to outcome.options
                    SponsoredReportOutcome.Reported -> toast(L.sponsoredReported)
                    SponsoredReportOutcome.AdsHidden -> toast(L.sponsoredHidden)
                    SponsoredReportOutcome.PremiumRequired -> toast(L.sponsoredPremiumRequired)
                    SponsoredReportOutcome.Unavailable -> toast(L.sponsoredUnavailable)
                }
            },
            onFailure = toast,
        )
    }

    LaunchedEffect(ad.messageId) { model.markSponsoredViewed(ad.messageId) }
    Surface(shape = MaterialTheme.shapes.large, color = Tone.surface, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (ad.miniThumbnail != null || ad.thumbnailFileId > 0) {
                MediaArt(ad.miniThumbnail, ad.thumbnailFileId, Modifier.width(SPONSORED_ART).aspectRatio(16f / 9f).clip(MaterialTheme.shapes.small)) {}
            }
            Column(Modifier.weight(1f)) {
                Text(ad.label.ifBlank { s.sponsoredLabel }, style = MaterialTheme.typography.labelMedium, color = Tone.muted)
                Text(ad.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(ad.text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (ad.sponsorUrl.isNotBlank()) {
                OutlinedButton(onClick = {
                    model.clickSponsored(ad, media = false, onSuccess = {
                        OpenExternal.browse(ad.sponsorUrl)
                    }, onFailure = toast)
                }) { Text(ad.buttonText.ifBlank { s.commonOpen }) }
            }
            if (ad.canBeReported) {
                TextButton(onClick = { report(ad, byteArrayOf()) }) { Text(s.sponsoredReport, color = Tone.muted) }
            }
        }
    }

    // Telegram answers a first report with the reasons it accepts; the chosen one is sent back
    // the same way, and may itself be answered with a narrower list.
    reportOptions?.let { (title, options) ->
        TmAlertDialog(
            onDismissRequest = { reportOptions = null },
            title = { Text(title.ifBlank { s.sponsoredReportWhy }) },
            text = {
                Column {
                    options.forEach { option ->
                        TextButton(
                            onClick = {
                                reportOptions = null
                                report(ad, option.id)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(option.text, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { reportOptions = null }) { Text(s.commonCancel) } },
        )
    }
}

/** Whether the grid's note on online metadata is due: lookups on and the note not put away. */
@Composable
private fun metaNoticeShown(state: ShellState): Boolean {
    val online = OnlineMetadata.current ?: return false
    val meta by online.settings.collectAsState()
    // True until the store answers, so the note never flashes up for somebody who put it away.
    val dismissed by state.settings.onlineMetaNoticeDismissed.collectAsState(initial = true)
    return MetaNotice.shouldShow(meta.enabled, dismissed)
}

/**
 * The one-time invite for a viewer whose online metadata is off from an older version, when off
 * was the default (see [MetaNotice.shouldInvite]), the phone's and television's OnlineMetaInvite.
 * Not over the tour Settings can open over this page. Not now, Escape or a click outside all
 * answer it, and it is never asked again.
 */
@Composable
private fun MetaInviteDialog(state: ShellState) {
    val online = OnlineMetadata.current ?: return
    val meta by online.settings.collectAsState()
    // Seen until the stores answer, so it never flashes up for somebody who already answered.
    val seen by state.settings.onlineMetaInviteSeen.collectAsState(initial = true)
    val toured by state.settings.overviewSeen.collectAsState(initial = false)
    var answered by remember { mutableStateOf(false) }
    if (answered || !toured || !MetaNotice.shouldInvite(meta, seen)) return
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    fun answer(turnOn: Boolean) {
        answered = true
        if (turnOn) online.setEnabled(true)
        scope.launch { runCatching { state.settings.markOnlineMetaInviteSeen(turnedOn = turnOn) } }
    }
    TmAlertDialog(
        onDismissRequest = { answer(turnOn = false) },
        icon = { Icon(TmIcons.Image, contentDescription = null, tint = Tone.accent) },
        title = { Text(s.metadataInviteTitle) },
        text = { Text(s.metadataInviteBody) },
        confirmButton = { TextButton(onClick = { answer(turnOn = true) }) { Text(s.metadataInviteTurnOn) } },
        dismissButton = { TextButton(onClick = { answer(turnOn = false) }) { Text(s.metadataInviteNotNow) } },
    )
}

/**
 * The grid's one-time note on online metadata, the phone's and television's OnlineMetaNotice:
 * posters and overviews are looked up by each video's cleaned title, and nothing else is sent.
 * Keep puts it away; Turn off switches lookups off as Settings, Online metadata does, puts it away
 * and says where to turn them back on.
 */
@Composable
private fun MetaNoticeCard(state: ShellState) {
    val s = LocalStrings.current
    val toast = rememberToast()
    val scope = rememberCoroutineScope()
    fun dismiss() {
        scope.launch { runCatching { state.settings.setOnlineMetaNoticeDismissed() } }
    }
    Surface(shape = MaterialTheme.shapes.large, color = Tone.surface, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(TmIcons.Image, contentDescription = null, tint = Tone.accent, modifier = Modifier.size(20.dp))
            Text(s.gridMetaNotice, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                OnlineMetadata.current?.setEnabled(false)
                dismiss()
                toast(s.gridMetaNoticeTurnedOff)
            }) { Text(s.gridMetaNoticeTurnOff) }
            TextButton(onClick = { dismiss() }) { Text(s.gridMetaNoticeKeep) }
        }
    }
}

private val PLAY_KEYS = setOf(Key.Enter, Key.NumPadEnter, Key.Spacebar, Key.P)
private val IS_MAC = System.getProperty("os.name").orEmpty().lowercase().contains("mac")
private const val HOVER_INTENT_MS = 300L
private const val SEARCH_SETTLE_MS = 300L
private const val LOAD_AHEAD = 8

/** A film or show poster's width, as a share of the toolbar's poster size step. */
private const val POSTER_SHARE = 0.75f

/** How much wider a Continue watching card is than a poster. */
private const val CONTINUE_WIDTH = 1.25f

/** A sponsored message's picture: 16:9 like every other, about the height its three lines take. */
private val SPONSORED_ART = 112.dp
