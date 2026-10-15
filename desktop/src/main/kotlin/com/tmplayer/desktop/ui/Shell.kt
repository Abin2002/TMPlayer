package com.tmplayer.desktop.ui

import kotlinx.coroutines.flow.drop
import androidx.compose.runtime.snapshotFlow
import com.tmplayer.ui.browse.HistoryTab
import com.tmplayer.ui.browse.ChatSort
import com.tmplayer.ui.browse.ChatFilter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tmplayer.data.AuthState
import com.tmplayer.desktop.BuildInfo
import com.tmplayer.desktop.DesktopConnectivity
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.tmplayer.data.SupportReminder
import com.tmplayer.ui.components.UiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import com.tmplayer.data.Td
import com.tmplayer.ui.browse.ChatListViewModel
import com.tmplayer.ui.browse.BrowseTab
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.style.TextOverflow
import com.tmplayer.data.ChatFolderSummary
import com.tmplayer.ui.browse.BrowseSection
import com.tmplayer.ui.browse.NavBrand
import com.tmplayer.ui.browse.NavEntry
import com.tmplayer.ui.browse.NavGroupBody
import com.tmplayer.ui.browse.NavGroupHeading
import com.tmplayer.ui.browse.browseSections
import com.tmplayer.ui.browse.navGroups
import com.tmplayer.ui.browse.DefaultGroups
import com.tmplayer.ui.icons.TmIcons
import com.tmplayer.ui.onboarding.Setup
import com.tmplayer.ui.browse.rememberNavGroups
import com.tmplayer.ui.components.AppLogo
import com.tmplayer.ui.components.LocalToastHost
import com.tmplayer.ui.components.rememberToast
import com.tmplayer.ui.i18n.LocalStrings
import com.tmplayer.ui.nav.BackHandler
import com.tmplayer.ui.nav.LocalBackStack
import com.tmplayer.ui.theme.Tone
import com.tmplayer.ui.lock.PinPad
import com.tmplayer.ui.lock.PinPurpose

/**
 * What the player draws for a [PlayRequest], given a way to close itself.
 *
 * The player lives in `com.tmplayer.desktop.player` and is passed in from `Main.kt`.
 */
typealias PlayerContent = @Composable (request: PlayRequest, onClose: () -> Unit) -> Unit

/**
 * The desktop window's content (B2.1): sign in until Telegram says Ready, then a sidebar above
 * 1000 dp of width (an icon rail below it), the page, and the player over the page when something
 * is playing.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DesktopShell(
    state: ShellState,
    player: PlayerContent,
) {
    val toasts = remember { SnackbarHostState() }
    CompositionLocalProvider(
        LocalBackStack provides state.backStack,
        LocalToastHost provides toasts,
    ) {
        Surface(
            Modifier
                .fillMaxSize()
                // The mouse's back button, wherever the pointer is.
                .onPointerEvent(PointerEventType.Press) { event ->
                    if (event.button == PointerButton.Back) state.backStack.handleBack()
                },
            color = Tone.background,
        ) {
            val auth by Td.auth.collectAsState()
            Box(Modifier.fillMaxSize()) {
                val signedIn = auth == AuthState.Ready
                // A sign in screen arms the setup that follows it (CP67, see Setup).
                LaunchedEffect(auth) {
                    if (Setup.isSignInStep(auth)) {
                        runCatching { state.settings.armSetup() }
                        // The account is gone, however it went (signed out from Telegram's
                        // Devices list is the way back from a forgotten PIN): so is its lock.
                        runCatching { state.settings.clearAppLock() }
                    }
                }
                val appLock by state.settings.appLock.collectAsState(initial = null)
                val appUnlocked by com.tmplayer.data.AppLockSession.unlocked.collectAsState()
                // "Ask when the app opens" (CP57): the pad in place of the window's content
                // until the PIN is given. Closing the window is the way out.
                val askOnOpen = signedIn && appLock?.onOpen == true && !appUnlocked
                if (askOnOpen) {
                    Box(Modifier.fillMaxSize().windowDrag()) {
                        PinPad(
                            settings = state.settings,
                            purpose = PinPurpose.Check,
                            title = LocalStrings.current.lockEnterForApp,
                            onDone = {
                                com.tmplayer.data.AppLockSession.unlock()
                                state.settingsUnlocked = true
                            },
                            onCancel = null,
                        )
                    }
                } else if (signedIn) {
                    // The setup after sign in sits over Browse (SetupOverlay), so the chat list
                    // loads under it and is where it was when the setup ends.
                    Browse(state, player)
                } else {
                    SignInScreen(auth)
                }
                SnackbarHost(toasts, Modifier.align(Alignment.BottomCenter).padding(16.dp))
            }
        }
    }
}

/**
 * Settings behind the app lock's PIN (CP57), asked once per visit. With no PIN set there is
 * nothing to ask, and a PIN set from inside Settings leaves the page open.
 */
@Composable
private fun LockedSettings(state: ShellState, content: @Composable () -> Unit) {
    val lock by state.settings.appLock.collectAsState(initial = null)
    val known = lock ?: return
    LaunchedEffect(known.pinSet) { if (!known.pinSet) state.settingsUnlocked = true }
    if (known.pinSet && !state.settingsUnlocked) {
        PinPad(
            settings = state.settings,
            purpose = PinPurpose.Check,
            title = LocalStrings.current.lockEnterForSettings,
            onDone = { state.settingsUnlocked = true },
            onCancel = { state.go(Destination.Home) },
        )
    } else {
        content()
    }
}

@Composable
private fun Browse(state: ShellState, player: PlayerContent) {
    val s = LocalStrings.current
    // One chat list for the session, the same view model the phone uses. Its store is cleared when
    // the account goes, which cancels everything it was loading.
    val chats = rememberViewModel(Unit) { ChatListViewModel(state.settings) }
    DisposableEffect(chats) { onDispose { chats.reset() } }
    LaunchedEffect(Unit) { chats.refreshIfStale() }
    LaunchedEffect(state) { state.settings.detailFirst.collect { state.detailFirst = it } }
    // "Only my folders", mirrored into the shell for every page to read.
    LaunchedEffect(state) {
        state.settings.hideDefaultGroups.collect {
            state.hideDefaultGroups = it
            // The folders are then the only way into the chats, so they are not left folded.
            if (it) com.tmplayer.ui.browse.NavGroupState.unfold(com.tmplayer.ui.browse.NavGroup.Folders)
        }
    }
    // A section the option has hidden is not left on screen: the Chats page moves to the first
    // folder, or with none, the window goes Home. The chip over Chats is kept as it was.
    val shellFolders by Td.folders.collectAsState()
    LaunchedEffect(state.hideDefaultGroups, shellFolders, state.chatSection) {
        if (!state.hideDefaultGroups || !DefaultGroups.isDefault(state.chatSection)) return@LaunchedEffect
        val first = shellFolders.firstOrNull()
        val onChats = state.destination == Destination.Chats
        if (first != null) {
            state.pickChatSection(BrowseSection.Folder(first.id, first.title))
        } else if (onChats) {
            state.go(Destination.Home)
        }
    }
    // The chips over Chats and History: the saved choice first, then every change written back.
    LaunchedEffect(state) {
        runCatching {
            state.chatFilter = ChatFilter.decode(state.settings.chatFilter.first())
            state.chatSort = ChatSort.decode(state.settings.chatSort.first())
            state.historyTab = HistoryTab.decode(state.settings.historyTab.first())
        }
        snapshotFlow { Triple(state.chatFilter, state.chatSort, state.historyTab) }.drop(1).collect { (filter, sort, tab) ->
            runCatching {
                state.settings.setChatFilter(filter.name)
                state.settings.setChatSort(sort.name)
                state.settings.setHistoryTab(tab.name)
            }
        }
    }
    // "Open the last chat on launch": once the list has it, open it, once per sign in.
    LaunchedEffect(Unit) {
        val target = runCatching { state.settings.autoOpenTarget() }.getOrNull() ?: return@LaunchedEffect
        val chat = chats.state
            .mapNotNull { (it as? UiState.Content)?.value?.chats?.firstOrNull { chat -> chat.id == target } }
            .first()
        if (state.openChat == null && (state.destination == Destination.Home || state.destination == Destination.Chats)) state.openChat(chat)
    }

    // Housekeeping once signed in: the strays streaming left behind, after the first screen has
    // settled. The update check is not here: it runs from Main.kt, sign in or not.
    LaunchedEffect(Unit) {
        delay(HOUSEKEEPING_DELAY_MS)
        runCatching { com.tmplayer.desktop.DesktopStorage.housekeeping(state.extras.watchCache) }
    }
    val update = rememberNavUpdate(state)
    UpdatePopupTrigger(state, update)
    val setupPending by state.settings.setupPending.collectAsState(initial = false)
    val chatsUi by chats.state.collectAsState()
    DownloadToasts(state)
    // Half watched entries that can no longer become a card are swept once per sign in, as on the
    // phone. Left alone they are invisible: the page skips them, so nothing can ever clear them.
    val toast = rememberToast()
    LaunchedEffect(Unit) {
        val removed = runCatching { state.settings.pruneBrokenHistory() }.getOrDefault(0)
        if (removed > 0) {
            toast(
                s.continuePruned(removed),
            )
        }
    }

    // The offline pill, and the refresh once both the network and Telegram are back.
    val browseScope = rememberCoroutineScope()
    LaunchedEffect(Unit) { DesktopConnectivity.start() }
    val network by DesktopConnectivity.status.collectAsState()
    val telegramConnected by Td.connected.collectAsState()
    val connection = rememberConnectionNotice(network, telegramConnected) {
        chats.load()
        browseScope.launch { runCatching { state.extras.updates?.checkIfDue() } }
        toast(s.connectionBackOnline)
    }

    BackHandler(enabled = state.openChat != null) { state.closeChat() }
    // Back from any page lands on Home, the first destination, as on the phone and the TV.
    BackHandler(enabled = state.openChat == null && state.destination != Destination.Home) {
        state.go(Destination.Home)
    }

    // A chat opened from a page takes that page's place, so the page is kept here while the chat
    // is open: back from the chat finds its search, its tab and its scroll as they were. Going
    // to another page from the side bar forgets them, as before.
    val pages = rememberSaveableStateHolder()
    LaunchedEffect(state.destination) {
        Destination.entries.filter { it != state.destination }.forEach { pages.removeState(it.name) }
    }

    Box(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= SIDEBAR_FROM
            Row(Modifier.fillMaxSize()) {
                if (wide) Sidebar(state, update) else Rail(state, update)
                VerticalDivider(color = Tone.outline)
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                    // The detail pane docks beside the page where both fit, so the grid stays in
                    // view and in reach (a click on another poster swaps the pane), and goes over
                    // the page where they do not.
                    val docked = state.detail != null && maxWidth >= DOCK_FROM
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            val open = state.openChat
                            when {
                                open != null -> MediaGridPage(state, open)
                                else -> pages.SaveableStateProvider(state.destination.name) {
                                    when (state.destination) {
                                        Destination.Home -> HomePage(state, chats)
                                        Destination.Chats -> ChatsPage(state, chats, favouritesOnly = false, showSections = !wide)
                                        Destination.Favourites -> ChatsPage(state, chats, favouritesOnly = true)
                                        Destination.History -> HistoryPage(state)
                                        Destination.Downloads -> DownloadsPage(state)
                                        Destination.Settings -> LockedSettings(state) {
                                            SettingsPage(
                                                state,
                                                BuildInfo.VERSION,
                                                chatList = (chatsUi as? UiState.Content)?.value?.chats.orEmpty(),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (docked) DetailPaneSide(state)
                    }
                    // Over the page only, so the side bar stays reachable while a pane is open.
                    if (!docked) DetailPaneHost(state)
                }
            }
        }

        UpdatePopupHost(state)
        LowStorageQuestionHost(state)
        // The language card and What's new wait for the setup, as on the phone.
        if (!setupPending) ShellNotices(state, Modifier.align(Alignment.BottomEnd).padding(24.dp))
        if (setupPending) {
            SetupOverlay(state, chatsUi is UiState.Content, shellFolders.size, (chatsUi as? UiState.Content)?.value?.chats.orEmpty())
        }
        // The quick tour on its own (CP69): taken from the offer, or opened from Settings.
        val tour by state.settings.tourState.collectAsState(initial = com.tmplayer.data.SettingsStore.TourState.Done)
        if (!setupPending && tour == com.tmplayer.data.SettingsStore.TourState.Pending) {
            val tourScope = rememberCoroutineScope()
            Surface(Modifier.fillMaxSize().windowDrag(), color = Tone.background) {
                com.tmplayer.ui.onboarding.TourOnly(onDone = { tourScope.launch { runCatching { state.settings.markTourDone() } } })
            }
        }
        if (SupportReminder.enabled && !setupPending) {
            SupportCardHost(state, Modifier.align(Alignment.BottomEnd).padding(24.dp))
        }

        ConnectionBanner(connection, Modifier.align(Alignment.TopCenter).padding(top = 12.dp))

        // Over the page rather than instead of it, so the page keeps its scroll position and its
        // search, and closing the player is simply taking this away.
        val playing = state.nowPlaying
        if (playing != null) {
            Box(Modifier.fillMaxSize().background(Tone.background)) {
                BackHandler { state.closePlayer() }
                player(playing) { state.closePlayer() }
            }
        }
    }
}

private val SIDEBAR_FROM = 1000.dp
private const val HOUSEKEEPING_DELAY_MS = 20_000L

private fun Destination.icon(): ImageVector = when (this) {
    Destination.Home -> TmIcons.Home
    Destination.Chats -> BrowseTab.Chats.icon
    Destination.Favourites -> TmIcons.StarOutline
    Destination.History -> BrowseTab.History.icon
    Destination.Downloads -> TmIcons.Download
    Destination.Settings -> TmIcons.Settings
}

/**
 * The wide side bar: the name and version, then Watch, Chats and the account's folders folding
 * under their headings, with Settings and Update pinned at the bottom. The grouping is the shared
 * one in :ui, so the phone's drawer and the television's rail fold the same entries the same way.
 */
@Composable
internal fun Sidebar(
    state: ShellState,
    update: NavUpdate? = null,
    folders: List<ChatFolderSummary> = Td.folders.collectAsState().value,
) {
    val s = LocalStrings.current
    val groups = rememberNavGroups(state.currentGroup)
    val sections = remember(folders, state.hideDefaultGroups) { browseSections(folders, state.hideDefaultGroups) }
    val inFlight = rememberDownloadsInFlight()
    // Wide enough for a German or Malayalam label beside a badge, with room to breathe: the side
    // bar is the frame of every page, and a cramped frame makes the whole window feel cramped.
    Column(Modifier.width(SIDEBAR_WIDTH).fillMaxHeight().padding(horizontal = 16.dp, vertical = 16.dp)) {
        NavBrand(BuildInfo.VERSION, Modifier.padding(horizontal = 12.dp, vertical = 14.dp))
        Spacer(Modifier.height(12.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            navGroups(sections).forEach { (group, entries) ->
                val open = groups.isOpen(group)
                if (group.headed) {
                    // A step of air above each heading, so Telegram and Folders read as groups
                    // rather than two more rows of the list above them.
                    Spacer(Modifier.height(10.dp))
                    NavGroupHeading(
                        group = group,
                        open = open,
                        toggleable = groups.canToggle(group),
                        onToggle = { groups.toggle(group) },
                        height = SIDEBAR_ROW,
                    )
                }
                NavGroupBody(open) {
                    entries.forEach { entry ->
                        val target = entry.destination()
                        SidebarItem(
                            label = if (entry is NavEntry.Section) entry.section.label else s.navDownloads,
                            icon = target?.icon() ?: (entry as NavEntry.Section).section.icon,
                            badge = if (entry == NavEntry.Downloads && inFlight > 0) inFlight.toString() else null,
                            selected = when {
                                target != null -> state.destination == target
                                else -> state.destination == Destination.Chats &&
                                    state.chatSection == (entry as NavEntry.Section).section
                            },
                            onClick = {
                                if (target != null) state.go(target) else state.showChats((entry as NavEntry.Section).section)
                            },
                        )
                    }
                }
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), color = Tone.outline)
        // Above Settings, as on the phone and the TV, rather than among the pages: it opens the popup.
        if (update != null) {
            SidebarItem(
                label = update.label,
                icon = TmIcons.Refresh,
                badge = update.version,
                selected = false,
                onClick = { state.updatePopup = true },
                colors = NavigationDrawerItemDefaults.colors(
                    unselectedIconColor = Tone.caution,
                    unselectedTextColor = Tone.caution,
                    unselectedBadgeColor = Tone.caution,
                ),
            )
        }
        SidebarItem(
            label = Destination.Settings.label,
            icon = Destination.Settings.icon(),
            badge = null,
            selected = state.destination == Destination.Settings,
            onClick = { state.go(Destination.Settings) },
        )
    }
}

/** The page an entry opens when it is a page of its own; null for a slice of the chat list. */
private fun NavEntry.destination(): Destination? = when (this) {
    NavEntry.Downloads -> Destination.Downloads
    is NavEntry.Section -> when (section) {
        BrowseSection.of(BrowseTab.Home) -> Destination.Home
        BrowseSection.of(BrowseTab.History) -> Destination.History
        BrowseSection.of(BrowseTab.Favorites) -> Destination.Favourites
        else -> null
    }
}

/** A side bar row, 46 dp rather than Material's 56: a pointer needs no thumb sized target, but a row needs air. */
@Composable
private fun SidebarItem(
    label: String,
    icon: ImageVector,
    badge: String?,
    selected: Boolean,
    onClick: () -> Unit,
    colors: androidx.compose.material3.NavigationDrawerItemColors = sidebarColors(),
) {
    NavigationDrawerItem(
        // Steps down to 12 sp rather than cut: with a download count beside it, Malayalam's
        // "ഡൗൺലോഡുകൾ" was "ഡൗൺലോഡു..." at the full size. Clip, not Ellipsis, because an
        // ellipsized line never reports an overflow and so never steps down.
        label = {
            Text(
                label,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = MaterialTheme.typography.bodyLarge.fontSize),
            )
        },
        icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp)) },
        badge = badge?.let { { Text(it) } },
        selected = selected,
        onClick = onClick,
        // The gap keeps a hovered or focused row from merging into the selected one beside it.
        modifier = Modifier.padding(vertical = 3.dp).height(SIDEBAR_ROW),
        colors = colors,
    )
}

private val SIDEBAR_ROW = 46.dp

private val SIDEBAR_WIDTH = 280.dp

/**
 * The selected page as a full pill one step above the ground, with its icon in the accent: the
 * television rail's selected destination, so the three devices mark "you are here" the same way.
 * The words stay in the text colour, which reads on that step in both themes.
 */
@Composable
private fun sidebarColors(): androidx.compose.material3.NavigationDrawerItemColors =
    NavigationDrawerItemDefaults.colors(
        selectedContainerColor = Tone.surfaceHigh,
        selectedIconColor = Tone.accent,
        selectedTextColor = Tone.text,
        selectedBadgeColor = Tone.text,
        unselectedContainerColor = Color.Transparent,
        unselectedIconColor = Tone.muted,
        unselectedTextColor = Tone.text,
    )

@Composable
private fun Rail(state: ShellState, update: NavUpdate?) {
    val s = LocalStrings.current
    NavigationRail(containerColor = Tone.background) {
        Image(
            AppLogo.Mark,
            contentDescription = null,
            modifier = Modifier.padding(vertical = 12.dp).size(28.dp),
        )
        val inFlight = rememberDownloadsInFlight()
        val folders by Td.folders.collectAsState()
        val hidden = state.hideDefaultGroups
        Destination.entries.forEach { destination ->
            // With the default groups hidden the Chats page lists only the folders, so it is named
            // for them, and with no folders there is nothing for it to show.
            if (destination == Destination.Chats && hidden && folders.isEmpty()) return@forEach
            val label = if (destination == Destination.Chats && hidden) s.navFolders else destination.label
            NavigationRailItem(
                selected = state.destination == destination,
                onClick = { state.go(destination) },
                icon = {
                    if (destination == Destination.Downloads && inFlight > 0) {
                        BadgedBox(badge = { Badge { Text(inFlight.toString()) } }) {
                            Icon(destination.icon(), contentDescription = s.navInProgress(destination.label, inFlight))
                        }
                    } else {
                        val icon = if (destination == Destination.Chats && hidden) TmIcons.Folder else destination.icon()
                        Icon(icon, contentDescription = label)
                    }
                },
                label = { Text(label.substringBefore(' ')) },
                // The side bar's pill, so the narrow window marks the page the same way.
                colors = NavigationRailItemDefaults.colors(
                    indicatorColor = Tone.surfaceHigh,
                    selectedIconColor = Tone.accent,
                    selectedTextColor = Tone.text,
                    unselectedIconColor = Tone.muted,
                    unselectedTextColor = Tone.muted,
                ),
            )
        }
        // The rail cuts labels at the first space, so the version rides in a badge on the icon.
        if (update != null) {
            NavigationRailItem(
                selected = false,
                onClick = { state.updatePopup = true },
                icon = {
                    BadgedBox(badge = {
                        Badge(containerColor = Tone.caution, contentColor = Tone.readableOn(Tone.caution)) {
                            Text(update.version)
                        }
                    }) {
                        Icon(TmIcons.Refresh, contentDescription = "${update.label} ${update.version}")
                    }
                },
                label = { Text(update.label.substringBefore(' ')) },
                colors = NavigationRailItemDefaults.colors(
                    unselectedIconColor = Tone.caution,
                    unselectedTextColor = Tone.caution,
                ),
            )
        }
    }
}

/**
 * The setup after sign in (CP67) over the whole window, once the chat list is in (it brings the
 * folder list with it) or after a wait for one that is slow to come. Browse is drawn under it, so
 * the list is ready when the setup ends.
 */
@Composable
private fun SetupOverlay(state: ShellState, chatsLoaded: Boolean, folderCount: Int, chats: List<com.tmplayer.data.ChatSummary>) {
    val scope = rememberCoroutineScope()
    var waitedOut by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(Setup.WAIT_FOR_CHATS_MS)
        waitedOut = true
    }
    val finish: () -> Unit = { scope.launch { runCatching { state.settings.markSetupDone() } } }
    Surface(Modifier.fillMaxSize().windowDrag(), color = Tone.background) {
        // A disk short of room gets the Device step, offering Low storage mode (CP68).
        val fit = com.tmplayer.ui.onboarding.rememberDeviceFit()
        val light by state.settings.lightSettings.collectAsState(initial = null)
        val now = light
        if (now == null || Setup.decide(pending = true, chatsLoaded = chatsLoaded, waitedOut = waitedOut) == Setup.Decision.Wait) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Tone.accent) }
            return@Surface
        }
        val recommendations = remember {
            com.tmplayer.data.DeviceFitRules.recommendations(fit, now, seekThumbnailsShown = false, pictures = false)
        }
        val steps = remember {
            Setup.steps(
                folderCount,
                posters = com.tmplayer.online.OnlineMetadata.current != null,
                device = recommendations.isNotEmpty(),
                // The desktop is only ever offered Low storage mode there, so Performance stays.
                devicePerformance = false,
            )
        }
        val favourites by state.settings.favorites.collectAsState(initial = emptySet())
        val unreachable = remember(favourites, chats) { DefaultGroups.unreachableFavorites(favourites, chats) }
        // The choices, then the quick tour (CP69); with no choices to make, the tour alone.
        com.tmplayer.ui.onboarding.SetupAndTour(
            state.settings,
            steps,
            onDone = finish,
            unreachableFavourites = unreachable,
            fit = fit,
            recommendations = recommendations,
        )
    }
}
