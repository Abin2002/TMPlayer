package com.tmplayer.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tmplayer.data.VideoSort
import com.tmplayer.i18n.Messages
import com.tmplayer.ui.components.TmDropdownMenu
import com.tmplayer.ui.i18n.LocalStrings
import com.tmplayer.ui.icons.TmIcons
import com.tmplayer.ui.theme.Tone

/**
 * The Sort button in a chat's header and its menu: the six orders as one radio group, the current
 * one marked. Picking one closes the menu and calls [onPick]; the chat remembers it.
 */
@Composable
internal fun VideoSortMenu(current: VideoSort, onPick: (VideoSort) -> Unit) {
    val s = LocalStrings.current
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(TmIcons.Sort, contentDescription = "${s.gridSort}: ${sortLabel(current, s)}")
        }
        TmDropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.selectableGroup()) {
            Text(
                s.gridSortLabel,
                style = MaterialTheme.typography.labelMedium,
                color = Tone.muted,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
            for (sort in VideoSort.entries) {
                val chosen = sort == current
                DropdownMenuItem(
                    text = { Text(sortLabel(sort, s)) },
                    leadingIcon = { RadioButton(selected = chosen, onClick = null) },
                    onClick = {
                        open = false
                        if (!chosen) onPick(sort)
                    },
                    // Read out as one radio button of the group, and which one is on.
                    modifier = Modifier.semantics {
                        role = Role.RadioButton
                        selected = chosen
                    },
                )
            }
        }
    }
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
