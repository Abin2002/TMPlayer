package com.tmplayer.ui.browse

import androidx.compose.runtime.Composable
import com.tmplayer.data.VideoSort
import com.tmplayer.i18n.Messages
import androidx.compose.ui.graphics.vector.ImageVector
import com.tmplayer.ui.components.ChoiceLine
import com.tmplayer.ui.components.ChoicePopover
import com.tmplayer.ui.components.PopoverAnchor
import com.tmplayer.ui.components.PopoverChoice
import com.tmplayer.ui.icons.TmIcons
import com.tmplayer.ui.components.ChoiceList
import com.tmplayer.ui.components.ChoiceSheet
import com.tmplayer.ui.components.isTouch
import com.tmplayer.ui.i18n.LocalStrings

/**
 * A chat's Sort menu: the orders of [VideoSort] as one radio group, the [current] one marked.
 * The same panel on a phone and under a remote ([ChoiceSheet]); a pick closes it.
 */
@Composable
internal fun VideoSortSheet(current: VideoSort, onPick: (VideoSort) -> Unit, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    ChoiceSheet(title = s.gridSortLabel, onDismiss = onDismiss, onClose = onDismiss, ignoreRelease = !isTouch()) {
        ChoiceList {
            for (sort in VideoSort.entries) {
                ChoiceLine(sortLabel(sort, s), selected = sort == current) { onPick(sort) }
            }
        }
    }
}

/** What [sort] is called in the menu. */
internal fun sortLabel(sort: VideoSort, s: Messages): String = when (sort) {
    VideoSort.Newest -> s.gridSortNewest
    VideoSort.Oldest -> s.gridSortOldest
    VideoSort.Name -> s.gridSortName
    VideoSort.Year -> s.gridSortYear
    VideoSort.Size -> s.gridSortSize
    VideoSort.Unwatched -> s.gridSortUnwatched
}

/**
 * A chat's Sort menu on a phone: a small popover grown out of the Sort button in the chat's bar
 * ([ChoicePopover]), each order with its glyph, the [current] one tinted and ticked rather than a
 * radio button. A pick applies it and closes the card.
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
        choices = VideoSort.entries.map { PopoverChoice(it, sortLabel(it, s), sortIcon(it)) },
        selected = current,
        onPick = onPick,
        onDismiss = onDismiss,
    )
}

/** The glyph beside [sort] in the phone's popover. */
internal fun sortIcon(sort: VideoSort): ImageVector = when (sort) {
    VideoSort.Newest -> TmIcons.Clock
    VideoSort.Oldest -> TmIcons.History
    VideoSort.Name -> TmIcons.TextSize
    VideoSort.Year -> TmIcons.Trailer
    VideoSort.Size -> TmIcons.Download
    VideoSort.Unwatched -> TmIcons.Play
}
