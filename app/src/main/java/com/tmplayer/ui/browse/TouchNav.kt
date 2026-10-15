package com.tmplayer.ui.browse

import android.view.accessibility.AccessibilityManager
import com.tmplayer.ui.nav.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tmplayer.data.Account
import com.tmplayer.ui.components.LocalBottomChrome
import com.tmplayer.ui.components.MediaPreview
import com.tmplayer.ui.i18n.LocalStrings
import com.tmplayer.ui.icons.TmIcons
import com.tmplayer.ui.theme.FloatingTone
import com.tmplayer.ui.theme.Motion
import com.tmplayer.ui.theme.Tone
import com.tmplayer.ui.theme.floatingBorder
import com.tmplayer.ui.theme.rememberReducedMotion

/** Where the phone's floating bar can take the viewer, in the bar's order. The profile button opens [ProfilePopover] instead. */
internal enum class TouchDestination { Home, Chats, Search, Settings }

/** Which of the bar's buttons carries the sliding highlight for [current], or null for none. */
internal fun barHighlightIndex(current: TouchDestination?): Int? = current?.ordinal

/**
 * What the floating bar and the popover behind the picture show and do, the same on every page
 * the bar is on. Built once, by whatever hosts the bar ([TouchNavHost]).
 */
@Immutable
internal class TouchNav(
    val account: Account?,
    val favoriteCount: Int,
    /** Downloads in flight, badged on the Downloads row and on the picture. */
    val downloadCount: Int,
    val updateVersion: String?,
    val onHome: () -> Unit,
    val onChats: () -> Unit,
    val onSearch: () -> Unit,
    val onSettings: () -> Unit,
    val onFavourites: () -> Unit,
    val onDownloads: () -> Unit,
    val onHistory: (HistoryTab) -> Unit,
    val onUpdate: () -> Unit,
)

/**
 * What one page asks of the floating bar while it is the page on screen.
 *
 * @param current the bar button lit for this page, or null for a page behind the picture.
 * @param inProfile the picture is ringed: Favourites, History, Watch later and Downloads.
 * @param hidden the bar steps out of the way for now (a search open, a selection under way)
 *   while the page still keeps its room at the foot.
 * @param actions the page's own buttons, in a small pill above the bar; null for none.
 */
internal class TouchPageSpec(
    val current: TouchDestination?,
    val inProfile: Boolean = false,
    val hidden: Boolean = false,
    val actions: (@Composable RowScope.() -> Unit)? = null,
)

/**
 * The shared state of the one floating bar: which page is on screen ([pageKey]), what each page
 * asked of the bar, and a search asked for from a page that has none of its own.
 *
 * One bar for every page, rather than a bar per page, is what lets its highlight slide from Home to
 * Settings rather than blink, and what keeps it still while the pages under it change.
 */
@Stable
internal class TouchChrome {
    var pageKey by mutableStateOf<Any?>(null)
    internal val specs = mutableStateMapOf<Any, TouchPageSpec>()

    /** Set by the bar's magnifier on a page without a search; the browser opens its search and clears it. */
    var searchAsked by mutableStateOf(false)

    internal val bar = BarVisibility()
}

internal val LocalTouchChrome = staticCompositionLocalOf<TouchChrome?> { null }

/** The key a page reports its [TouchPageSpec] under: the screen it belongs to. */
internal val LocalTouchPageKey = staticCompositionLocalOf<Any> { DEFAULT_PAGE }

/** The bar's shared-element scope, for the search field that grows out of its magnifier. */
@OptIn(ExperimentalSharedTransitionApi::class)
internal val LocalTouchShared = staticCompositionLocalOf<SharedTransitionScope?> { null }

/**
 * Tells the floating bar what this page wants of it, for as long as the page is composed.
 * Nothing at all happens outside a [TouchNavHost].
 */
@Composable
internal fun TouchPage(spec: TouchPageSpec?) {
    val chrome = LocalTouchChrome.current ?: return
    val key = LocalTouchPageKey.current
    SideEffect {
        if (spec == null) chrome.specs.remove(key) else chrome.specs[key] = spec
    }
    DisposableEffect(chrome, key) { onDispose { chrome.specs.remove(key) } }
}

/**
 * The phone's floating bar, hosted once over every page: Home, Chats, Search, Settings and the
 * viewer's picture, with the popover of Favourites, Downloads, History, Watch later and Update
 * behind the picture.
 *
 * The bar floats, so it takes no room from the listing: it slides away as the list is pushed up
 * and comes back on the first pull down, on a change of page and on the way back from the player.
 * It never hides while TalkBack is on (a control that moves away is one a screen reader cannot
 * find again), and it is out of the way while the keyboard is up. How much of the screen it keeps
 * is [LocalBottomChrome], which the lists under it pad their ends by. A page with no
 * [TouchPageSpec] (a chat, a Settings page, About) has no bar.
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun TouchNavHost(
    nav: TouchNav,
    chrome: TouchChrome,
    /** The page on screen: whatever [LocalTouchPageKey] its [TouchPage] reports under. */
    pageKey: Any,
    content: @Composable () -> Unit,
) {
    val reduced = rememberReducedMotion() || Motion.reduced
    val bar = chrome.bar
    var profileOpen by remember { mutableStateOf(false) }
    SideEffect { chrome.pageKey = pageKey }
    val spec = chrome.specs[pageKey]

    // Back closes the popover before it leaves the screen.
    BackHandler(enabled = profileOpen) { profileOpen = false }

    // The bar comes back whenever the page underneath changes: a bar left hidden across a change
    // of page has no pull-down to restore it on a page too short to scroll.
    LaunchedEffect(pageKey, spec?.current, spec?.inProfile, spec?.hidden) { bar.show() }
    // And on the way back from the player, an activity coming forward rather than a recomposition.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) bar.show() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val talkBack = rememberTouchExploration()
    val keyboard = WindowInsets.isImeVisible
    val barShown = spec != null && !spec.hidden && (bar.shown || talkBack) && !keyboard

    val density = LocalDensity.current
    val navigationBar = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    // What the bar keeps, measured from the bottom of the window, with the pill of the page's own
    // buttons over it when there is one.
    val room = if (spec == null) 0.dp else navigationBar + touchShellClearance(withActions = spec.actions != null)

    // The spec as it was when the bar was last out, so the bar does not redraw empty on its way out.
    val lastShown = remember { arrayOf(TouchPageSpec(current = null)) }
    if (spec != null && !spec.hidden) lastShown[0] = spec

    SharedTransitionLayout(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            CompositionLocalProvider(
                LocalBottomChrome provides room,
                LocalTouchChrome provides chrome,
                LocalTouchShared provides this@SharedTransitionLayout,
            ) {
                Box(Modifier.fillMaxSize().nestedScroll(bar.connection)) { content() }
            }

            AnimatedVisibility(
                visible = barShown,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = if (reduced) fadeIn(Motion.standard(0)) else slideInVertically(Motion.standard(Motion.Normal)) { it } + fadeIn(Motion.standard(Motion.Fast)),
                exit = if (reduced) fadeOut(Motion.standard(0)) else slideOutVertically(Motion.standard(Motion.Normal)) { it } + fadeOut(Motion.standard(Motion.Fast)),
            ) {
                val shown = lastShown[0]
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                    shown.actions?.let { actions ->
                        // The page's own buttons: a small pill at the end, over the bar, so the top
                        // of the screen holds nothing to press.
                        Row(
                            Modifier
                                .padding(end = 16.dp, bottom = BAR_GAP)
                                .height(ACTIONS_HEIGHT)
                                .shadow(BAR_SHADOW, CircleShape)
                                .clip(CircleShape)
                                .background(FloatingTone.menu)
                                .border(floatingBorder(), CircleShape)
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            content = actions,
                        )
                    }
                    FloatingBar(
                        current = shown.current,
                        inProfile = shown.inProfile,
                        account = nav.account,
                        badged = nav.downloadCount > 0 || nav.updateVersion != null,
                        onHome = nav.onHome,
                        onChats = nav.onChats,
                        onSearch = nav.onSearch,
                        onSettings = nav.onSettings,
                        onProfile = { profileOpen = true },
                        searchButton = { modifier ->
                            if (reduced) {
                                SearchBarButton(modifier)
                            } else {
                                SearchBarButton(
                                    modifier.sharedBounds(
                                        rememberSharedContentState(SEARCH_KEY),
                                        animatedVisibilityScope = this@AnimatedVisibility,
                                        resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                                    ),
                                )
                            }
                        },
                    )
                }
            }

            ProfilePopover(
                open = profileOpen,
                reduced = reduced,
                favoriteCount = nav.favoriteCount,
                downloadCount = nav.downloadCount,
                updateVersion = nav.updateVersion,
                onDismiss = { profileOpen = false },
                onFavourites = { profileOpen = false; nav.onFavourites() },
                onDownloads = { profileOpen = false; nav.onDownloads() },
                onHistory = { profileOpen = false; nav.onHistory(HistoryTab.Continue) },
                onWatchLater = { profileOpen = false; nav.onHistory(HistoryTab.WatchLater) },
                onUpdate = { profileOpen = false; nav.onUpdate() },
            )
        }
    }
}

/**
 * The touch shell: the page's name along the top, the page, and the search page over everything
 * while it is open. The floating bar is [TouchNavHost]'s, shared with Settings and Downloads; this
 * tells it which page is up and what the page's own buttons are. Outside a host (the screenshot
 * fixture) the shell hosts a bar of its own from [nav].
 *
 * The page's buttons ride in a small pill over the bar rather than in the top bar, so a large phone
 * held in one hand reaches all of them; the top keeps only the title, which scrolls away with the
 * list and back on the first pull down.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun TouchBrowseShell(
    nav: TouchNav,
    selected: BrowseSection,
    /** The page's name in the top bar. */
    title: String,
    searchOpen: Boolean,
    onOpenSearch: () -> Unit,
    /**
     * The search page, drawn over everything while [searchOpen]. [field] goes on its search field,
     * which is what the bar's magnifier turns into.
     */
    searchScreen: @Composable (field: Modifier) -> Unit,
    /** The page's own buttons, in the pill over the bar. */
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val host = LocalTouchChrome.current
    if (host == null) {
        val own = remember { TouchChrome() }
        TouchNavHost(nav, own, DEFAULT_PAGE) {
            TouchBrowseShell(nav, selected, title, searchOpen, onOpenSearch, searchScreen, actions, content)
        }
        return
    }
    val reduced = rememberReducedMotion() || Motion.reduced

    TouchPage(
        TouchPageSpec(
            current = when {
                selected.isHome -> TouchDestination.Home
                selected.onChatsPage -> TouchDestination.Chats
                else -> null
            },
            inProfile = !selected.isHome && !selected.onChatsPage,
            hidden = searchOpen,
            actions = actions,
        ),
    )
    // The bar's magnifier, pressed on a page (Settings, Downloads) that has no search of its own.
    LaunchedEffect(host.searchAsked) {
        if (host.searchAsked) {
            host.searchAsked = false
            onOpenSearch()
        }
    }

    Box(Modifier.fillMaxSize()) {
        // The title slides away with the list and back on the first pull down, as the bar does.
        val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
        LaunchedEffect(selected, title) { scrollBehavior.state.heightOffset = 0f }
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                TopAppBar(
                    scrollBehavior = scrollBehavior,
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) { content() }
        }

        val shared = LocalTouchShared.current
        AnimatedVisibility(
            visible = searchOpen,
            enter = if (reduced) EnterTransition.None else fadeIn(Motion.standard(Motion.Normal)),
            exit = if (reduced) ExitTransition.None else fadeOut(Motion.standard(Motion.Normal)),
        ) {
            val field = if (reduced || shared == null) {
                Modifier
            } else {
                with(shared) {
                    Modifier.sharedBounds(
                        rememberSharedContentState(SEARCH_KEY),
                        animatedVisibilityScope = this@AnimatedVisibility,
                        resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                    )
                }
            }
            searchScreen(field)
        }
    }
}

/**
 * How much a page under the bar pads the end of its list by, the navigation bar not included:
 * the bar, its margin and gap, and the pill of the page's own buttons when it has one.
 */
internal fun touchShellClearance(withActions: Boolean): Dp =
    BAR_MARGIN + BAR_HEIGHT + BAR_GAP + if (withActions) ACTIONS_HEIGHT + BAR_GAP else 0.dp

/**
 * Whether the bar is out, from how the list under it is being moved: pushed up past [HIDE_AFTER_PX]
 * hides it, any pull back down shows it, and a pull at the very top (which nothing above consumes)
 * shows it too.
 */
@Stable
internal class BarVisibility {

    var shown by mutableStateOf(true)
        private set
    private var travelled by mutableFloatStateOf(0f)

    fun show() {
        shown = true
        travelled = 0f
    }

    val connection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            val moved = consumed.y
            when {
                moved < 0f -> {
                    travelled = (travelled + moved).coerceAtMost(0f)
                    if (travelled < -HIDE_AFTER_PX) shown = false
                }
                moved > 0f || available.y > 0f -> show()
            }
            return Offset.Zero
        }
    }
}

/** Whether TalkBack, or anything else exploring the screen by touch, is on, followed as it changes. */
@Composable
private fun rememberTouchExploration(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    var on by remember { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { on = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return on
}

/**
 * The floating bar: one rounded pill, clear of the screen's edge and the navigation bar, with a
 * soft shadow and the floating hairline. The current page's button carries a filled backing in
 * the accent, as a selected chip does. On a page behind the picture (Favourites, History, Watch
 * later, Downloads) no button is lit and the picture wears an accent ring instead.
 */
@Composable
private fun FloatingBar(
    current: TouchDestination?,
    inProfile: Boolean,
    account: Account?,
    badged: Boolean,
    onHome: () -> Unit,
    onChats: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onProfile: () -> Unit,
    searchButton: @Composable (Modifier) -> Unit,
) {
    val s = LocalStrings.current
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = BAR_MARGIN), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .height(BAR_HEIGHT)
                .shadow(BAR_SHADOW, CircleShape)
                .clip(CircleShape)
                .background(FloatingTone.menu)
                .border(floatingBorder(), CircleShape)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(contentAlignment = Alignment.CenterStart) {
            // One highlight under the chosen page, sliding to the next one rather than blinking
            // off under one button and on under another, so the bar shows where the page went.
            val index = barHighlightIndex(current)
            val reduced = rememberReducedMotion() || Motion.reduced
            val x by androidx.compose.animation.core.animateDpAsState(
                targetValue = (BUTTON + BAR_SPACING) * (index ?: 0),
                animationSpec = if (reduced) androidx.compose.animation.core.snap() else androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 500f),
                label = "barHighlight",
            )
            val shown by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (index != null) 1f else 0f,
                animationSpec = Motion.standard(if (reduced) 0 else Motion.Fast),
                label = "barHighlightShown",
            )
            Box(
                Modifier
                    .offset(x = x)
                    .size(BUTTON)
                    .graphicsLayer { alpha = shown }
                    .clip(CircleShape)
                    .background(Tone.accent.copy(alpha = 0.16f)),
            )
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BAR_SPACING)) {
            BarButton(TmIcons.Home, s.browseTabHome, current == TouchDestination.Home, onHome)
            BarButton(TmIcons.Chats, s.browseTabChats, current == TouchDestination.Chats, onChats)
            searchButton(
                Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onSearch)
                    .semantics { contentDescription = s.commonSearch },
            )
            BarButton(TmIcons.Settings, s.navSettings, current == TouchDestination.Settings, onSettings)
            Box(
                Modifier
                    .size(BUTTON)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onProfile)
                    .semantics {
                        contentDescription = s.navProfile
                        selected = inProfile
                    },
                contentAlignment = Alignment.Center,
            ) {
                // A ring round the picture while one of its pages is open: the lists behind it are
                // somewhere the bar can say the viewer is, without lighting a button they are not on.
                val ring by androidx.compose.animation.animateColorAsState(
                    if (inProfile) Tone.accent else Color.Transparent,
                    animationSpec = Motion.standard(if (rememberReducedMotion() || Motion.reduced) 0 else Motion.Fast),
                    label = "profileRing",
                )
                Box(
                    Modifier
                        .size(PICTURE + 8.dp)
                        .border(2.dp, ring, CircleShape)
                        .background(if (inProfile) Tone.accent.copy(alpha = 0.16f) else Color.Transparent, CircleShape),
                )
                ProfilePicture(account, PICTURE)
                if (badged) {
                    // News behind the picture: a download running, or an update to install.
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-6).dp, y = 6.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Tone.caution)
                            .border(1.5.dp, FloatingTone.menu, CircleShape),
                    )
                }
            }
          }
          }
        }
    }
}

/** The bar's magnifier. It is the shared element the search field grows out of. */
@Composable
private fun SearchBarButton(modifier: Modifier) {
    Box(modifier.size(BUTTON).clip(CircleShape).background(FloatingTone.menu), contentAlignment = Alignment.Center) {
        Icon(TmIcons.Search, contentDescription = null, tint = Tone.text, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun BarButton(icon: ImageVector, label: String, chosen: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(BUTTON)
            .clip(CircleShape)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics {
                contentDescription = label
                selected = chosen
                role = Role.Tab
            },
        contentAlignment = Alignment.Center,
    ) {
        // The highlight behind is the bar's (see FloatingBar); the icon takes its colour as it lands.
        val tint by androidx.compose.animation.animateColorAsState(if (chosen) Tone.accent else Tone.text, label = "barIcon")
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun ProfilePicture(account: Account?, size: Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        if (account != null) {
            MediaPreview(
                miniThumbnail = account.miniThumbnail,
                thumbnailFileId = account.photoFileId,
                fallbackLabel = account.name,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        } else {
            Icon(TmIcons.Account, contentDescription = null, tint = Tone.muted, modifier = Modifier.align(Alignment.Center).size(size * 0.7f))
        }
    }
}

/**
 * The viewer's own lists, behind their picture: a small card that grows out of the bottom end
 * corner over a dimmed screen, with Favourites, Downloads, History, Watch later, and Update when
 * there is one. Settings has its own button on the bar, and the account and About are in Settings.
 * A tap outside, Back, or picking a row closes it.
 */
@Composable
private fun ProfilePopover(
    open: Boolean,
    reduced: Boolean,
    favoriteCount: Int,
    downloadCount: Int,
    updateVersion: String?,
    onDismiss: () -> Unit,
    onFavourites: () -> Unit,
    onDownloads: () -> Unit,
    onHistory: () -> Unit,
    onWatchLater: () -> Unit,
    onUpdate: () -> Unit,
) {
    val s = LocalStrings.current
    AnimatedVisibility(
        visible = open,
        enter = fadeIn(Motion.standard(if (reduced) 0 else Motion.Fast)),
        exit = fadeOut(Motion.standard(if (reduced) 0 else Motion.Fast)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(FloatingTone.scrim)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }
    Box(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(top = 8.dp, bottom = BAR_MARGIN + BAR_HEIGHT + BAR_GAP, end = 16.dp, start = 16.dp),
    ) {
        AnimatedVisibility(
            visible = open,
            modifier = Modifier.align(Alignment.BottomEnd),
            enter = if (reduced) {
                fadeIn(Motion.standard(0))
            } else {
                fadeIn(Motion.standard(Motion.Fast)) + scaleIn(Motion.standard(Motion.Normal), initialScale = 0.85f, transformOrigin = TransformOrigin(1f, 1f))
            },
            exit = if (reduced) {
                fadeOut(Motion.standard(0))
            } else {
                fadeOut(Motion.standard(Motion.Fast)) + scaleOut(Motion.standard(Motion.Fast), targetScale = 0.9f, transformOrigin = TransformOrigin(1f, 1f))
            },
        ) {
            Column(
                Modifier
                    .widthIn(max = POPOVER_WIDTH)
                    .shadow(BAR_SHADOW, POPOVER_SHAPE)
                    .clip(POPOVER_SHAPE)
                    .background(FloatingTone.dialog)
                    .border(floatingBorder(), POPOVER_SHAPE)
                    // Taps inside the card stay inside it rather than reaching the scrim.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
            ) {
                val rows = buildList<@Composable (Modifier) -> Unit> {
                    add { m -> PopoverRow(TmIcons.StarOutline, s.browseTabFavourites, favoriteCount.takeIf { it > 0 }?.toString(), modifier = m, onClick = onFavourites) }
                    add { m -> PopoverRow(TmIcons.Download, s.navDownloads, downloadCount.takeIf { it > 0 }?.toString(), modifier = m, onClick = onDownloads) }
                    add { m -> PopoverRow(TmIcons.History, s.browseTabHistory, null, modifier = m, onClick = onHistory) }
                    add { m -> PopoverRow(TmIcons.Clock, s.browseTabWatchLater, null, modifier = m, onClick = onWatchLater) }
                    if (updateVersion != null) {
                        // Amber, as on the television's rail and the desktop's side bar: the one row that is news.
                        add { m -> PopoverRow(TmIcons.Refresh, s.navUpdate, updateVersion, ink = Tone.caution, modifier = m, onClick = onUpdate) }
                    }
                }
                rows.take(4).forEach { it(Modifier) }
                if (rows.size > 4) {
                    HorizontalDivider(Modifier.padding(horizontal = 14.dp, vertical = 2.dp))
                    rows.drop(4).forEach { it(Modifier) }
                }
            }
        }
    }
}

@Composable
private fun PopoverRow(
    icon: ImageVector,
    label: String,
    badge: String?,
    ink: Color = Tone.text,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(POPOVER_ROW)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (badge != null) {
            Text(
                badge,
                style = MaterialTheme.typography.labelMedium,
                color = if (ink == Tone.text) Tone.onAccent else ink,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (ink == Tone.text) Tone.accent else Color.Transparent)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/** The key the bar's magnifier and the search field share, for the one to grow into the other. */
private const val SEARCH_KEY = "search-field"

/** The bar's height: a 48 dp button with a little room above and below it. */
internal val BAR_HEIGHT = 60.dp

/** Between the bar and the navigation bar (or the screen's edge). */
internal val BAR_MARGIN = 16.dp

/** Between the bar and whatever a list puts last, or a button floating above it. */
internal val BAR_GAP = 8.dp

private val BUTTON = 48.dp

/** The pill of a page's own buttons over the bar: one row of 48 dp buttons. */
internal val ACTIONS_HEIGHT = 48.dp

/** The key a page reports under when nothing names it, which is the page outside any host. */
internal const val DEFAULT_PAGE = "page"

/** Between two of the bar's buttons. */
private val BAR_SPACING = 4.dp
private val PICTURE = 32.dp
internal val BAR_SHADOW = 8.dp
private val POPOVER_WIDTH = 208.dp
private val POPOVER_ROW = 44.dp
private val POPOVER_SHAPE = RoundedCornerShape(20.dp)

/** How far a list has to be pushed up before the bar gets out of the way, so a nudge does not. */
private const val HIDE_AFTER_PX = 48f
