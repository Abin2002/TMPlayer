package com.tmplayer.ui.browse

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tmplayer.ui.components.MenuAction
import com.tmplayer.ui.components.TvMenu
import com.tmplayer.ui.i18n.LocalStrings
import com.tmplayer.ui.icons.TmIcons
import com.tmplayer.ui.theme.Motion
import com.tmplayer.ui.theme.Tone
import com.tmplayer.ui.theme.Tv
import com.tmplayer.ui.theme.focusRing
import com.tmplayer.ui.theme.rememberReducedMotion
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Experimental (diag/tv-top-bar): the television's slim bar across the top of the page in place
 * of the side rail. On by default in this test build; the phone and the desktop never see it.
 */
internal const val TV_TOP_BAR = true

/** The bar's row: tall enough for a pill grown under the remote. */
private val BAR_ROW = 48.dp

/** The fade at the foot of the bar's backdrop, where a page passing under it dims away. */
private val BAR_FADE = 12.dp

/** What a page under the bar leaves clear at its top: the safe area, the row and the fade. */
internal val TV_BAR_HEIGHT: Dp = Tv.SafeV + BAR_ROW + BAR_FADE

/**
 * The one start inset every page under the bar lines up with: the start edge of the bar's first
 * item (the account's picture), which is the TV safe area. The end of the bar's last item (the
 * app's mark and name) stands at [TV_BAR_END], where the rows' own end inset is.
 */
internal val TV_START: Dp = Tv.SafeH
internal val TV_BAR_END: Dp = Tv.SafeH + Tv.FocusClearance


/** The phone's three tabs, which the television's bar carries in the same order. */
internal enum class TvBarTab { Home, Chats, Settings }

/** Asks from the bar that a page outside the browser (Settings, Downloads) cannot answer itself. */
internal object TvBarSignals {
    /** Search was picked on a page with no search of its own: the browser opens its search. */
    val searchAsked = MutableStateFlow(false)

    /** Back moved the remote from Home's rows to the bar: Home goes back to its top, the carousel whole. */
    val homeToTop = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}

/**
 * The bar: the account's picture at the start, which opens what the phone's picture opens
 * (Favourites, Downloads, History, Watch later, Refresh where the page has something to fetch again
 * ([onRefresh]), and Update when there is one); then Search, and the tabs Home, Chats and Settings
 * in the phone's order; and at the end the app's mark and name, which take no focus.
 *
 * Behind it the page's own ground, near solid over the row and fading out at its foot, so a page
 * scrolled up under it dims away instead of running into its words, in every theme.
 *
 * The page being shown is marked with an underline in the accent whether or not the remote is on
 * the bar; the item the remote is on fills and grows. [overArt] says Home runs up under the bar.
 */
@Composable
internal fun TvTopBar(
    nav: TouchNav,
    current: TvBarTab?,
    /** A page behind the picture is shown: History, Favourites or Downloads. */
    inProfile: Boolean,
    searchOpen: Boolean,
    onHome: () -> Unit,
    onChats: () -> Unit,
    onSettings: () -> Unit,
    onSearch: () -> Unit,
    /** Called before any item of the account's menu goes anywhere (closes the search, say). */
    beforeProfile: () -> Unit = {},
    onRefresh: (() -> Unit)?,
    /** On the item for the page shown: Up from the page, and Back, land there. */
    currentFocus: FocusRequester,
    /** The page under the bar: Down from the bar goes into it, where the remote last was. */
    contentFocus: FocusRequester,
    overArt: Boolean,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    var profileOpen by remember { mutableStateOf(false) }
    val ground = Tone.background
    val homeFocus = remember { FocusRequester() }
    val chatsFocus = remember { FocusRequester() }
    val settingsFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val profileFocus = remember { FocusRequester() }
    // Which item the shared requester sits on; every other one keeps its own.
    val target = if (searchOpen) null else current
    fun focusOf(tab: TvBarTab, own: FocusRequester) = if (target == tab) currentFocus else own
    val searchRequester = if (searchOpen) currentFocus else searchFocus
    val profileRequester = if (!searchOpen && current == null) currentFocus else profileFocus

    Box(
        modifier
            .fillMaxWidth()
            .height(TV_BAR_HEIGHT)
            .drawBehind {
                val total = size.height
                val row = (Tv.SafeV + BAR_ROW).toPx() / total
                // Over Home's carousel too: its picture already melts into the page at its top, and
                // the rows scrolled up under the bar must dim away like any page.
                drawRect(
                    Brush.verticalGradient(
                        0f to ground,
                        row to ground.copy(alpha = 0.96f),
                        1f to Color.Transparent,
                    ),
                )
            },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = TV_START, end = TV_BAR_END, top = Tv.SafeV)
                .height(BAR_ROW)
                // Entering the bar from the page lands on the page's own item, never on whichever
                // item happens to sit above the tile the remote was on.
                .focusProperties { onEnter = { runCatching { currentFocus.requestFocus() } } }
                // Down goes into the page, whatever stands below this item: the page's own place.
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                        runCatching { contentFocus.requestFocus() }
                        true
                    } else {
                        false
                    }
                }
                .focusGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfileButton(
                account = nav.account,
                chosen = inProfile && !searchOpen,
                badge = when {
                    nav.updateVersion != null -> Tone.caution
                    nav.downloadCount > 0 -> Tone.accent
                    else -> null
                },
                label = nav.account?.name ?: s.browseTabHistory,
                onClick = { profileOpen = true },
                open = profileOpen,
                modifier = Modifier.focusRequester(profileRequester),
            )
            Spacer(Modifier.width(18.dp))
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BarTab(s.commonSearch, TmIcons.Search, searchOpen, onSearch, Modifier.focusRequester(searchRequester))
                BarTab(s.browseTabHome, TmIcons.Home, current == TvBarTab.Home && !searchOpen, onHome, Modifier.focusRequester(focusOf(TvBarTab.Home, homeFocus)))
                BarTab(s.browseTabChats, TmIcons.Chats, current == TvBarTab.Chats && !searchOpen, onChats, Modifier.focusRequester(focusOf(TvBarTab.Chats, chatsFocus)))
                BarTab(s.navSettings, TmIcons.Settings, current == TvBarTab.Settings && !searchOpen, onSettings, Modifier.focusRequester(focusOf(TvBarTab.Settings, settingsFocus)))
            }
            Spacer(Modifier.width(18.dp))
            // The app's own mark and name, the way a television's home screen signs itself.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                com.tmplayer.ui.components.AppMark(28.dp)
                Text("TMPlayer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Tone.text, maxLines = 1)
            }
        }
    }

    if (profileOpen) {
        val go: (() -> Unit) -> () -> Unit = { action ->
            {
                profileOpen = false
                beforeProfile()
                action()
            }
        }
        TvMenu(
            title = nav.account?.name.orEmpty(),
            subtitle = nav.account?.username?.takeIf { it.isNotBlank() }?.let { "@$it" },
            onDismiss = { profileOpen = false },
            actions = buildList {
                add(MenuAction(s.browseTabFavourites, TmIcons.StarOutline, detail = nav.favoriteCount.takeIf { it > 0 }?.toString(), onSelect = go(nav.onFavourites)))
                add(MenuAction(s.navDownloads, TmIcons.Download, detail = nav.downloadCount.takeIf { it > 0 }?.toString(), onSelect = go(nav.onDownloads)))
                add(MenuAction(s.browseTabHistory, TmIcons.History, onSelect = go { nav.onHistory(HistoryTab.Continue) }))
                add(MenuAction(s.browseTabWatchLater, TmIcons.Clock, onSelect = go { nav.onHistory(HistoryTab.WatchLater) }))
                // Home and the chat list fetch again from here: Home also refreshes itself when
                // the app comes back after a while (MainActivity).
                if (onRefresh != null) add(MenuAction(s.commonRefresh, TmIcons.Refresh, onSelect = { profileOpen = false; onRefresh() }))
                nav.updateVersion?.let { version -> add(MenuAction(s.navUpdate, TmIcons.Refresh, detail = version, onSelect = go(nav.onUpdate))) }
            },
        )
    }
}

/** The account's picture's focus ring, and the gap between it and the picture. */
private val RING_WIDTH = 3.dp
private val RING_GAP = 3.dp

/** The height of a tab's pill and the size of the account's picture, so the bar reads as one line. */
private val CLUSTER_ITEM = 40.dp

/** One of the three tabs: its icon and name in a pill, underlined while its page is shown. */
@Composable
private fun BarTab(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val pressed by interactions.collectIsPressedAsState()
    val still = rememberReducedMotion() || Motion.reduced
    val scale by animateFloatAsState(
        when {
            still -> 1f
            pressed -> Motion.PressScale
            focused -> Motion.FocusScale
            else -> 1f
        },
        Motion.standard(Motion.Fast),
        label = "barTabScale",
    )
    val fill by animateColorAsState(if (focused) Tone.focusFill else Color.Transparent, Motion.standard(Motion.Fast), label = "barTabFill")
    val ink by animateColorAsState(
        when {
            focused -> Tone.onFocusFill
            selected -> Tone.text
            else -> Tone.muted
        },
        Motion.standard(Motion.Fast),
        label = "barTabInk",
    )
    val underline by animateDpAsState(if (selected && !focused) 18.dp else 0.dp, Motion.standard(Motion.Fast), label = "barTabLine")
    val pill = CircleShape
    Box(modifier, contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .height(CLUSTER_ITEM)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(pill)
                .background(fill)
                .focusRing(focused, pill)
                .clickable(interactionSource = interactions, indication = null, role = Role.Tab, onClick = onClick)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            com.tmplayer.ui.components.SelectableIcon(icon, selected = selected, tint = ink, size = 20.dp)
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Medium,
                color = ink,
                maxLines = 1,
            )
        }
        // The page shown, marked under its name while the remote is elsewhere.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 6.dp)
                .width(underline)
                .height(3.dp)
                .clip(CircleShape)
                .background(Tone.accent),
        )
    }
}

/** The account's picture, first in the bar: opens the account's menu. Ringed while one of its pages is shown. */
@Composable
private fun ProfileButton(
    account: com.tmplayer.data.Account?,
    chosen: Boolean,
    /** A dot on the picture: a download running (accent) or an update waiting (amber). */
    badge: Color?,
    label: String,
    onClick: () -> Unit,
    /** The account's menu is open over the bar: the picture stays marked as the item in use. */
    open: Boolean,
    modifier: Modifier = Modifier,
) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val pressed by interactions.collectIsPressedAsState()
    val still = rememberReducedMotion() || Motion.reduced
    val lit = focused || open
    val scale by animateFloatAsState(
        when {
            still -> 1f
            pressed -> Motion.PressScale
            lit -> Motion.FocusScale
            else -> 1f
        },
        Motion.standard(Motion.Fast),
        label = "barProfileScale",
    )
    // A ring in the accent a little way out from the picture, with a thin halo of the page's own
    // ground outside it, so it reads over a pale page, a dark one and the carousel's picture.
    val ring by animateFloatAsState(if (lit) 1f else 0f, Motion.standard(Motion.Fast), label = "barProfileRing")
    val accent = Tone.accent
    val ground = Tone.background
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(CLUSTER_ITEM)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .drawBehind {
                    val picture = size.minDimension / 2f
                    if (ring > 0f) {
                        val width = RING_WIDTH.toPx()
                        val gap = RING_GAP.toPx()
                        drawCircle(ground.copy(alpha = 0.7f * ring), radius = picture + gap + width + 1.dp.toPx(), style = Stroke(2.dp.toPx()))
                        drawCircle(accent.copy(alpha = ring), radius = picture + gap + width / 2f, style = Stroke(width))
                    } else if (chosen) {
                        drawCircle(accent, radius = picture + 1.dp.toPx(), style = Stroke(2.dp.toPx()))
                    }
                }
                .clip(CircleShape)
                .clickable(interactionSource = interactions, indication = null, role = Role.Button, onClickLabel = label, onClick = onClick),
        ) {
            AccountPicture(account)
        }
        if (badge != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(badge),
            )
        }
        if (chosen) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 6.dp)
                    .width(if (focused) 0.dp else 14.dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(Tone.accent),
            )
        }
    }
}

/**
 * The bar over a page outside the browser (Settings, Downloads): the same items, and the page
 * under it. Kept in one shape whether or not [shown], so the page under it keeps its state when
 * the bar comes and goes (a Settings page opened from its list hides the bar).
 */
@Composable
internal fun TvBarFrame(
    shown: Boolean,
    barFocus: FocusRequester,
    contentFocus: FocusRequester,
    bar: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        // The page's list runs up under the bar's fade, so it dims away as it scrolls.
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = if (shown) TV_BAR_HEIGHT - 8.dp else 0.dp)
                .then(if (shown) Modifier.underTvBar(contentFocus, barFocus) else Modifier),
        ) { content() }
        if (shown) bar()
    }
}

/**
 * A page under the TV top bar: Up from its top-most item, where the page has nowhere further up
 * to go, goes to the bar's item for the page ([bar]); Down never leaves it; and [content] (Down
 * from the bar) lands on the item the remote was last on here, else the first.
 */
@Composable
internal fun Modifier.underTvBar(content: FocusRequester, bar: FocusRequester): Modifier {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    return this
        .focusRequester(content)
        // After the page's own handling (a heading's See all armed by Up, say): only an Up the
        // page left alone moves here, and where it finds nothing above it goes to the bar.
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown || event.key != Key.DirectionUp) return@onKeyEvent false
            if (!focusManager.moveFocus(FocusDirection.Up)) runCatching { bar.requestFocus() }
            true
        }
        .focusProperties {
            onExit = {
                if (requestedFocusDirection == FocusDirection.Down) cancelFocusChange()
            }
        }
        .focusRestorer()
        .focusGroup()
}
