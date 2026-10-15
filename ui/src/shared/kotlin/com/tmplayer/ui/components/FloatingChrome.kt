package com.tmplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tmplayer.data.DeviceForm
import com.tmplayer.ui.theme.FloatingTone
import com.tmplayer.ui.theme.Tone
import com.tmplayer.ui.theme.floatingBorder

/**
 * The phone's floating chrome, outside the main bar: the round Back (or Close) button at the top
 * start of a page, and the sticky bar of a page's own actions at its foot.
 *
 * Both are drawn the way the main floating bar is (a pill or a circle in [FloatingTone.menu], the
 * floating hairline and a soft shadow), so a page's controls read as one family wherever they are.
 * A large phone is held from below, so everything a page does sits at the bottom; Back stays at the
 * top start, where every Android user looks for it.
 */
object FloatingChrome {
    /** The round Back button. */
    val Button = 44.dp

    /** The sticky bar, and the same in a phone held sideways, where height is what is short. */
    val Bar = 56.dp
    val CompactBar = 48.dp

    /** Between the sticky bar and the navigation bar (or the keyboard). */
    val Margin = 12.dp

    /** Between the sticky bar and the last row of a list scrolled to its end. */
    val Gap = 8.dp

    /** Where the round Back button stands, from the status bar and the screen's start edge. */
    val Inset = 12.dp

    val Shadow = 8.dp

    /** How much a list under a sticky bar pads its end by, the navigation bar not included. */
    fun clearance(compact: Boolean): Dp = Margin + (if (compact) CompactBar else Bar) + Gap
}

/** True on a phone, where pages carry the floating chrome; the television and the desktop keep their own. */
@Composable
fun usesFloatingChrome(): Boolean = deviceForm() == DeviceForm.Phone

/**
 * A round floating button: the page's Back, or Close while a selection is on. Over the content, so
 * it is always there to press however far the page has scrolled.
 */
@Composable
fun FloatingRoundButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(FloatingChrome.Button)
            .shadow(FloatingChrome.Shadow, CircleShape)
            .clip(CircleShape)
            .background(FloatingTone.menu)
            .border(floatingBorder(), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Tone.text, modifier = Modifier.size(22.dp))
    }
}

/** [FloatingRoundButton] in its place at the top start of the screen, clear of the status bar and a cutout. */
@Composable
fun FloatingBackButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingRoundButton(
        icon = icon,
        label = label,
        onClick = onClick,
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(start = FloatingChrome.Inset, top = FloatingChrome.Inset / 2),
    )
}

/** How much room the round Back button takes at the top start, for a title drawn beside it. */
val FloatingBackSlot: Dp = FloatingChrome.Inset + FloatingChrome.Button + 8.dp

/**
 * A page's own actions, in a pill at the foot of the screen that stays put while the page scrolls.
 *
 * It sits above the navigation bar and above the keyboard when one is up ([WindowInsets.safeDrawing]
 * takes in both), so a search field drawn in it rides up with the keyboard and comes back down
 * when the keyboard goes. [compact] is for a phone held sideways. [fill] stretches the pill across
 * the screen, for a search field or a selection's count and actions.
 */
@Composable
fun StickyActionBar(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    fill: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(start = 16.dp, end = 16.dp, bottom = FloatingChrome.Margin),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            (if (fill) Modifier.fillMaxWidth() else Modifier)
                .height(if (compact) FloatingChrome.CompactBar else FloatingChrome.Bar)
                .shadow(FloatingChrome.Shadow, CircleShape)
                .clip(CircleShape)
                .background(FloatingTone.menu)
                .border(floatingBorder(), CircleShape)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** True when the window is wide and short, a phone held sideways: the sticky bar goes compact. */
@Composable
fun rememberCompactChrome(): Boolean {
    val height = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height
    val density = androidx.compose.ui.platform.LocalDensity.current
    return with(density) { height.toDp() } < COMPACT_BELOW
}

/** One icon in a sticky bar or an action pill: a 48 dp target around a 24 dp glyph, named for a screen reader. */
@Composable
fun FloatingBarIcon(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Tone.text,
    /** Set for a toggle (the star): read out as on or off. */
    checked: Boolean? = null,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = label
                role = Role.Button
                if (checked != null) selected = checked
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) tint else tint.copy(alpha = 0.38f), modifier = Modifier.size(24.dp))
    }
}

private val COMPACT_BELOW = 480.dp
