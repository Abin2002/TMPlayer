package com.tmplayer.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.onFocusedBoundsChanged
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Keeps a list's top in sight whenever the row the remote stands on fits on the list's first
 * screen. A heading, a blurb or a picture at the top of a list takes no focus, and moving focus
 * back up to the first row only scrolls far enough to show that row: the heading above it stayed
 * cut in half (Settings on a television, going down and coming back to the first page). Here the
 * list goes all the way back to its start instead, which shows that row just the same.
 *
 * Put it on every remote-driven lazy list whose first item is not focusable. [enabled] is false
 * where a pointer or a finger scrolls the list, which never moves focus this way.
 */
fun Modifier.keepTopInSight(state: LazyListState, enabled: Boolean = true): Modifier =
    if (!enabled) {
        this
    } else {
        keepTopInSight(
            firstIndex = { state.firstVisibleItemIndex },
            firstOffset = { state.firstVisibleItemScrollOffset },
            scrollToTop = { state.animateScrollToItem(0) },
        )
    }

/** [keepTopInSight] for a grid. */
fun Modifier.keepTopInSight(state: LazyGridState, enabled: Boolean = true): Modifier =
    if (!enabled) {
        this
    } else {
        keepTopInSight(
            firstIndex = { state.firstVisibleItemIndex },
            firstOffset = { state.firstVisibleItemScrollOffset },
            scrollToTop = { state.animateScrollToItem(0) },
        )
    }

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.keepTopInSight(
    firstIndex: () -> Int,
    firstOffset: () -> Int,
    scrollToTop: suspend () -> Unit,
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val holder = remember { TopHolder() }
    this
        .onGloballyPositioned { holder.list = it }
        .onFocusedBoundsChanged { focused ->
            val list = holder.list ?: return@onFocusedBoundsChanged
            if (focused == null || !list.isAttached || !focused.isAttached) return@onFocusedBoundsChanged
            // Already at the top, or scrolled past the first item: nothing to bring back.
            val offset = firstOffset()
            if (firstIndex() != 0 || offset == 0) return@onFocusedBoundsChanged
            if (holder.job?.isActive == true) return@onFocusedBoundsChanged
            // Where the focused row's foot would be with the list at its top.
            val top = list.localPositionOf(focused, Offset.Zero).y
            val foot = top + focused.size.height + offset
            if (foot <= list.size.height) holder.job = scope.launch { scrollToTop() }
        }
}

private class TopHolder {
    var list: LayoutCoordinates? = null
    var job: Job? = null
}
