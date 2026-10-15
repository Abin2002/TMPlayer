package com.tmplayer.ui.browse

import androidx.compose.runtime.Composable
import com.tmplayer.data.VideoSort
import com.tmplayer.i18n.Messages
import com.tmplayer.ui.components.ChoiceLine
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
