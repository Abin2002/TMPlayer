package com.tmplayer.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.tmplayer.data.VideoSort
import com.tmplayer.i18n.Messages
import com.tmplayer.ui.components.ChoicePopover
import com.tmplayer.ui.components.PopoverAnchor
import com.tmplayer.ui.components.PopoverChoice
import com.tmplayer.ui.i18n.LocalStrings
import com.tmplayer.ui.icons.TmIcons

/**
 * A chat's Sort, as the phone has it: a small card grown out of the Sort button in the chat's bar
 * ([ChoicePopover]), each order with its glyph and the [current] one tinted and ticked rather than
 * a radio button. A pick applies it and closes the card, and the chat remembers it; Esc or a click
 * outside closes it as it was.
 *
 * Drawn last in a box the size of the page, so the card can sit anywhere over it.
 */
@Composable
internal fun VideoSortPopover(
    open: Boolean,
    anchor: PopoverAnchor,
    current: VideoSort,
    onPick: (VideoSort) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    ChoicePopover(
        open = open,
        anchor = anchor,
        title = s.gridSortLabel,
        choices = VideoSort.entries.map { PopoverChoice(it, sortLabel(it, s), sortIcon(it)) },
        selected = current,
        onPick = onPick,
        onDismiss = onDismiss,
    )
}

/** What each order is called in the menu. */
internal fun sortLabel(sort: VideoSort, s: Messages): String = when (sort) {
    VideoSort.Newest -> s.gridSortNewest
    VideoSort.Oldest -> s.gridSortOldest
    VideoSort.Name -> s.gridSortName
    VideoSort.Year -> s.gridSortYear
    VideoSort.Size -> s.gridSortSize
    VideoSort.Unwatched -> s.gridSortUnwatched
}

/** The glyph beside each order, the phone's. */
internal fun sortIcon(sort: VideoSort): ImageVector = when (sort) {
    VideoSort.Newest -> TmIcons.Clock
    VideoSort.Oldest -> TmIcons.History
    VideoSort.Name -> TmIcons.TextSize
    VideoSort.Year -> TmIcons.Trailer
    VideoSort.Size -> TmIcons.Download
    VideoSort.Unwatched -> TmIcons.Play
}
