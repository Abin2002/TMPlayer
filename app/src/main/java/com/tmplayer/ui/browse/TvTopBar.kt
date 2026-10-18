package com.tmplayer.ui.browse

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.launch

/**
 * Experimental (diag/tv-top-bar): the television's slim bar across the top of the page in place
 * of the side rail. On by default in this test build; the phone and the desktop never see it.
 */
internal const val TV_TOP_BAR = true

/** The bar's row: tall enough for a pill and the two lines of the clock. */
private val BAR_ROW = 48.dp

/** What a page under the bar leaves clear at its top: the safe area, the row and a small gap. */
internal val TV_BAR_HEIGHT: Dp = Tv.SafeV + BAR_ROW + 8.dp

/**
 * The one start inset every page under the bar lines up with: the start edge of the bar's first
 * tab, which is the TV safe area. The end of the bar's last item (the account's picture) stands at
 * [TV_BAR_END], where the rows' own end inset is.
 */
internal val TV_START: Dp = Tv.SafeH
internal val TV_BAR_END: Dp = Tv.SafeH + Tv.FocusClearance

/** How far below the bar the page's ground keeps fading out over the Home carousel's picture. */
private val SCRIM_TAIL = 28.dp

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
 * The bar: Home, Chats and Settings at the start, the phone's tabs in the phone's order; then, as
 * one group at the end, Search, Refresh where the page has something to refresh, the clock and the
 * account's picture, which opens what the phone's picture opens (Favourites, Downloads, History,
 * Watch later, and Update when there is one).
 *
 * The page being shown is marked with an underline in the accent whether or not the remote is on
 * the bar; the item the remote is on fills and grows. Over the Home carousel ([overArt]) the page's
 * own ground fades in behind the bar, so its words read over any picture in every theme.
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
    val target = when {
        searchOpen -> null
        current != null -> current
        else -> null
    }
    fun focusOf(tab: TvBarTab, own: FocusRequester) = if (target == tab) currentFocus else own
    val searchRequester = if (searchOpen) currentFocus else searchFocus
    val profileRequester = if (!searchOpen && current == null) currentFocus else profileFocus

    Box(
        modifier
            .fillMaxWidth()
            .height(TV_BAR_HEIGHT + if (overArt) SCRIM_TAIL else 0.dp)
            .drawBehind {
                if (!overArt) return@drawBehind
                val bar = TV_BAR_HEIGHT.toPx()
                val total = size.height
                drawRect(
                    Brush.verticalGradient(
                        0f to ground.copy(alpha = 0.94f),
                        (bar * 0.6f / total) to ground.copy(alpha = 0.82f),
                        (bar / total) to ground.copy(alpha = 0.5f),
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
                .focusGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                BarTab(s.browseTabHome, TmIcons.Home, current == TvBarTab.Home && !searchOpen, onHome, Modifier.focusRequester(focusOf(TvBarTab.Home, homeFocus)))
                BarTab(s.browseTabChats, TmIcons.Chats, current == TvBarTab.Chats && !searchOpen, onChats, Modifier.focusRequester(focusOf(TvBarTab.Chats, chatsFocus)))
                BarTab(s.navSettings, TmIcons.Settings, current == TvBarTab.Settings && !searchOpen, onSettings, Modifier.focusRequester(focusOf(TvBarTab.Settings, settingsFocus)))
            }
            Spacer(Modifier.weight(1f))
            // One group, one gap: the two buttons, the clock and the picture.
            Row(horizontalArrangement = Arrangement.spacedBy(CLUSTER_GAP), verticalAlignment = Alignment.CenterVertically) {
                BarIconButton(s.commonSearch, TmIcons.Search, chosen = searchOpen, onClick = onSearch, modifier = Modifier.focusRequester(searchRequester))
                if (onRefresh != null) RefreshButton(s.commonRefresh, onRefresh)
                TvClock(onArt = false)
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
                    modifier = Modifier.focusRequester(profileRequester),
                )
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
                nav.updateVersion?.let { version -> add(MenuAction(s.navUpdate, TmIcons.Refresh, detail = version, onSelect = go(nav.onUpdate))) }
            },
        )
    }
}

/** Between the end group's members. */
private val CLUSTER_GAP = 14.dp

/** The end group's round buttons and the picture: one size, so the group reads as one. */
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

/** A round icon button in the end group: Search. Filled and grown under the remote. */
@Composable
private fun BarIconButton(
    label: String,
    icon: ImageVector,
    chosen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    rotation: () -> Float = { 0f },
) {
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
        label = "barIconScale",
    )
    val fill by animateColorAsState(if (focused) Tone.focusFill else Color.Transparent, Motion.standard(Motion.Fast), label = "barIconFill")
    val ink by animateColorAsState(
        when {
            focused -> Tone.onFocusFill
            chosen -> Tone.accent
            else -> Tone.muted
        },
        Motion.standard(Motion.Fast),
        label = "barIconInk",
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(CLUSTER_ITEM)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(fill)
                .focusRing(focused, CircleShape)
                .clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = ink, modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = rotation() })
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

/** Refresh, which turns once round every time it is pressed, so the press is never silent. */
@Composable
private fun RefreshButton(label: String, onRefresh: () -> Unit) {
    val scope = rememberCoroutineScope()
    val turn = remember { Animatable(0f) }
    val still = rememberReducedMotion() || Motion.reduced
    BarIconButton(
        label = label,
        icon = TmIcons.Refresh,
        chosen = false,
        rotation = { turn.value },
        onClick = {
            onRefresh()
            if (!still) {
                scope.launch {
                    turn.snapTo(turn.value % 360f)
                    turn.animateTo(turn.value + 360f, Motion.standard(Motion.Slow * 2))
                }
            }
        },
    )
}

/** The account's picture, last in the bar: opens the account's menu. Ringed while one of its pages is shown. */
@Composable
private fun ProfileButton(
    account: com.tmplayer.data.Account?,
    chosen: Boolean,
    /** A dot on the picture: a download running (accent) or an update waiting (amber). */
    badge: Color?,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val pressed by interactions.collectIsPressedAsState()
    val still = rememberReducedMotion() || Motion.reduced
    val scale by animateFloatAsState(
        when {
            still -> 1f
            pressed -> Motion.PressScale
            focused -> Motion.FocusScale + 0.05f
            else -> 1f
        },
        Motion.standard(Motion.Fast),
        label = "barProfileScale",
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(CLUSTER_ITEM)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .focusRing(focused, CircleShape)
                .clip(CircleShape)
                .then(if (chosen && !focused) Modifier.border(2.dp, Tone.accent, CircleShape) else Modifier)
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
    bar: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.padding(top = if (shown) TV_BAR_HEIGHT - Tv.SafeV else 0.dp)) { content() }
        if (shown) bar()
    }
}

@Suppress("unused")
private val keepColumn: @Composable () -> Unit = { Column {} }
