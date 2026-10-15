package com.tmplayer.ui.browse

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.tmplayer.data.Account
import com.tmplayer.data.ChatGate
import com.tmplayer.data.ChatSummary
import com.tmplayer.data.MediaItem
import com.tmplayer.data.ResultGroup
import com.tmplayer.data.ResumeRecord
import com.tmplayer.data.SearchChatType
import com.tmplayer.data.SearchFilters
import com.tmplayer.data.SearchQuality
import com.tmplayer.data.SearchSort
import com.tmplayer.data.Series
import com.tmplayer.data.SeriesShelf
import com.tmplayer.data.SettingsStore
import com.tmplayer.data.ShelfEntry
import com.tmplayer.data.WatchLaterRecord
import com.tmplayer.ui.components.rememberVoiceSearch
import com.tmplayer.ui.i18n.LocalStrings
import com.tmplayer.ui.theme.Motion
import com.tmplayer.ui.theme.Tone
import com.tmplayer.ui.theme.rememberReducedMotion
import kotlinx.coroutines.launch

/**
 * The phone's browser: [TouchBrowseShell] with the page for [tab], changing pages with a short
 * cross-fade and a nudge in the direction of travel, and the search page ([PhoneSearchScreen])
 * that the bar's magnifier opens.
 *
 * The search's text, its filters, its order and its scroll position are kept across a rotation
 * and across the process being stopped behind another app: the text and the list by the saved
 * state, the filters written down beside them and handed back to the model.
 */
@Composable
internal fun PhoneBrowse(
    tab: BrowseSection,
    account: Account?,
    pane: @Composable (BrowseSection) -> Unit,
    countOf: (BrowseSection) -> Int,
    actions: @Composable RowScope.() -> Unit,
    sections: List<BrowseSection>,
    defaultsHidden: Boolean,
    favourites: Set<Long>,
    downloadCount: Int,
    updateVersion: String?,
    onPickTab: (BrowseSection) -> Unit,
    onHistory: (HistoryTab) -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onUpdate: () -> Unit,
    search: GlobalSearchViewModel,
    searchState: GlobalSearchState,
    chats: List<ChatSummary>,
    continueWatching: List<ResumeRecord>,
    watchLater: List<WatchLaterRecord>,
    homeArt: Map<String, MediaItem>,
    watch: SeriesWatch,
    onOpenChat: (ChatSummary) -> Unit,
    onOpenMedia: (MediaItem, String) -> Unit,
    onPlayMedia: (MediaItem, String) -> Unit,
    onResume: (ResumeRecord) -> Unit,
    onMessage: (String) -> Unit,
) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val settings = remember(context) { SettingsStore(context) }
    val scope = rememberCoroutineScope()
    val reduced = rememberReducedMotion() || Motion.reduced

    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchText by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var savedFilters by rememberSaveable { mutableStateOf("") }
    val searchList = rememberLazyListState()
    // The Chats page comes back on the chip it was left on, a folder or one of Telegram's filters.
    var lastChats by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(tab) { if (tab.onChatsPage) lastChats = BrowseSection.encode(tab) }

    // Back after the process was stopped: the model is new, the saved filters are not.
    LaunchedEffect(Unit) {
        decodeFilters(savedFilters)?.let { (filters, sort) ->
            if (searchState.filters.isDefault) search.setFilters(filters)
            if (searchState.sort == SearchSort.BestMatch) search.setSort(sort)
        }
    }
    LaunchedEffect(searchState.filters, searchState.sort) { savedFilters = encodeFilters(searchState.filters, searchState.sort) }
    LaunchedEffect(searchText.text) { search.type(searchText.text) }

    // What the local half of the search matches besides the chat listings already seen.
    val downloads by remember(settings) { settings.downloadHistory }.collectAsState(initial = emptyList())
    LaunchedEffect(continueWatching, watchLater, downloads) {
        search.alsoKnown = (continueWatching + downloads).map { it.toMediaItem() } + watchLater.map { it.toMediaItem() }
    }
    val recent by remember(settings) { settings.recentSearches }.collectAsState(initial = emptyList())
    val allowed by ChatGate.allowed.collectAsState()
    val keepsSearches by remember(settings) { settings.rememberSearches }.collectAsState(initial = true)

    var searchSeries by remember { mutableStateOf<Pair<Series, String>?>(null) }
    val titleOf: (Long) -> String = { id -> searchState.chats[id]?.title ?: chats.firstOrNull { it.id == id }?.title.orEmpty() }
    val openGroup: (ResultGroup) -> Unit = { group ->
        scope.launch {
            // Deleted, or its chat left, since the search: say so, and the card goes.
            if (!search.check(group.lead)) {
                onMessage(s.searchNoLongerThere)
                return@launch
            }
            search.remember()
            val series = if (group.isShow) {
                SeriesShelf.arrange(group.copies).firstNotNullOfOrNull { (it as? ShelfEntry.Show)?.series }
            } else {
                null
            }
            if (series != null) {
                searchSeries = series to titleOf(group.lead.chatId)
            } else {
                onOpenMedia(group.asItem(), titleOf(group.lead.chatId))
            }
        }
    }
    val voice = rememberVoiceSearch(s.browseVoicePromptChat) { heard ->
        searchText = TextFieldValue(heard, TextRange(heard.length))
        search.submit(heard)
    }

    TouchBrowseShell(
        account = account,
        selected = tab,
        favoriteCount = favourites.size,
        downloadCount = downloadCount,
        updateVersion = updateVersion,
        onHome = { onPickTab(BrowseSection.of(BrowseTab.Home)) },
        onChats = {
            val back = BrowseSection.decode(lastChats)?.takeIf { it.isChats || it in sections }
            onPickTab(back ?: chatsDestination(sections, defaultsHidden))
        },
        onFavourites = { onPickTab(BrowseSection.of(BrowseTab.Favorites)) },
        onHistory = onHistory,
        onOpenDownloads = onOpenDownloads,
        onOpenSettings = onOpenSettings,
        onOpenAbout = onOpenAbout,
        onUpdate = onUpdate,
        title = tab.heading,
        searchOpen = searchOpen,
        onOpenSearch = {
            // Back with the last query, all of it selected, so typing replaces it and the arrow keeps it.
            searchText = searchText.copy(selection = TextRange(0, searchText.text.length))
            searchOpen = true
        },
        searchScreen = { field ->
            PhoneSearchScreen(
                state = searchState,
                text = searchText,
                onText = { searchText = it },
                onSubmit = { search.submit(searchText.text) },
                onClose = { searchOpen = false },
                fieldModifier = field,
                chats = chats,
                favourites = favourites,
                // A child's profile keeps no trail of what was looked for.
                recentSearches = if (allowed != null || !keepsSearches) emptyList() else recent,
                continueWatching = continueWatching,
                continueArt = { record -> homeArt[SettingsStore.progressKey(record.chatId, record.messageId)] ?: record.toMediaItem() },
                progress = { item -> watch.point(item)?.fraction },
                onOpenChat = { chat -> search.remember(); onOpenChat(chat) },
                onOpenGroup = openGroup,
                onResume = onResume,
                onFilters = search::setFilters,
                onSort = search::setSort,
                onLoadMore = search::loadMore,
                onRetry = search::retry,
                onRemoveRecent = { entry -> scope.launch { settings.removeRecentSearch(entry) } },
                onClearRecent = {
                    val before = recent
                    scope.launch { settings.clearRecentSearches() }
                    before
                },
                onRestoreRecent = { before -> scope.launch { settings.restoreRecentSearches(before) } },
                onVoice = voice,
                listState = searchList,
            )
        },
        actions = actions,
    ) {
        val order = remember(sections) { navGroups(sections).flatMap { it.entries }.filterIsInstance<NavEntry.Section>().map { it.section } }
        AnimatedContent(
            targetState = tab,
            contentKey = { it.navSection },
            transitionSpec = {
                val from = order.indexOf(initialState.navSection)
                val to = order.indexOf(targetState.navSection)
                val enter = if (reduced) {
                    fadeIn(Motion.standard(Motion.Fast))
                } else {
                    fadeIn(Motion.standard(Motion.Normal)) +
                        slideInHorizontally(Motion.standard(Motion.Normal)) { width -> (if (to >= from) width else -width) / SLIDE_PART }
                }
                enter togetherWith fadeOut(Motion.standard(Motion.Fast))
            },
            label = "phonePage",
        ) { page ->
            // The page's count under the title, where a phone's app bar puts a subtitle.
            Column(Modifier.fillMaxSize()) {
                val count = countOf(page)
                if (count > 0) {
                    Text(
                        countLabel(page, count),
                        style = MaterialTheme.typography.bodySmall,
                        color = Tone.muted,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                    )
                }
                pane(page)
            }
        }
    }

    searchSeries?.let { (series, chatTitle) ->
        SeriesOpened(
            series = series,
            watch = watch,
            onPlay = { onPlayMedia(it, chatTitle) },
            onDismiss = { searchSeries = null },
            chatTitle = chatTitle,
        )
    }
}

/** The search's filters and order, written down for the saved state. */
private fun encodeFilters(filters: SearchFilters, sort: SearchSort): String =
    listOf(filters.chatType.name, filters.quality.name, filters.hideSmall, filters.includeArchived, sort.name).joinToString(",")

private fun decodeFilters(raw: String): Pair<SearchFilters, SearchSort>? {
    val parts = raw.split(',')
    if (parts.size != 5) return null
    return runCatching {
        SearchFilters(
            chatType = SearchChatType.valueOf(parts[0]),
            quality = SearchQuality.valueOf(parts[1]),
            hideSmall = parts[2].toBoolean(),
            includeArchived = parts[3].toBoolean(),
        ) to SearchSort.valueOf(parts[4])
    }.getOrNull()
}

/** How far a page slides in as it fades: a twelfth of the width, a nudge rather than a swipe. */
private const val SLIDE_PART = 12
