package com.tmplayer.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tmplayer.data.OfflineDownloads
import com.tmplayer.data.Series
import com.tmplayer.data.SeriesSeason
import com.tmplayer.data.SettingsStore
import com.tmplayer.ui.browse.PageDiscButton
import com.tmplayer.ui.browse.SeriesArt
import com.tmplayer.ui.browse.SeriesPanel
import com.tmplayer.ui.browse.SeriesWatch
import com.tmplayer.ui.browse.seriesProgressLine
import com.tmplayer.ui.browse.seriesSummary
import com.tmplayer.ui.components.rememberToast
import com.tmplayer.ui.i18n.LocalStrings
import com.tmplayer.ui.nav.BackHandler
import com.tmplayer.ui.theme.Tone
import kotlinx.coroutines.launch

/**
 * The Watched list and saved positions, read the way [MediaTile] reads them, for the series view,
 * with when each was last watched for the "New" badge on a show's poster.
 */
@Composable
internal fun rememberSeriesWatch(state: ShellState): SeriesWatch {
    val progress by state.settings.watchProgress.collectAsState(initial = emptyMap())
    val watched by state.watched.watched.collectAsState(initial = emptyMap())
    val resume by state.settings.continueWatching.collectAsState(initial = emptyList())
    val times = remember(resume, watched) { com.tmplayer.data.NewEpisodes.touchTimes(resume, watched.values) }
    return remember(progress, watched, times) {
        SeriesWatch(
            point = { progress[SettingsStore.progressKey(it.chatId, it.messageId)] },
            finished = { SettingsStore.progressKey(it.chatId, it.messageId) in watched },
            touchedAt = { times[SettingsStore.progressKey(it.chatId, it.messageId)] },
        )
    }
}

/**
 * A show in a chat's grid, laid out like a video's poster: the art with its stack, the name and
 * "2 seasons · 18 eps". Hover or keyboard focus lifts it as it does a poster; a click or Enter opens
 * the show.
 */
@Composable
internal fun SeriesPoster(
    series: Series,
    watch: SeriesWatch,
    onOpen: () -> Unit,
    nav: KeyboardNav? = null,
    index: Int = 0,
    /** A 2:3 poster card, as in a chat's grid of films and shows. */
    poster: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (hovered || focused) 1.05f else 1f)
    val requester = remember { FocusRequester() }
    val progress = watch.progress(series)
    Column(
        Modifier
            .hoverable(interaction)
            .then(if (nav != null) Modifier.navCell(nav, index, requester) else Modifier.focusRequester(requester))
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.Spacebar)) {
                    onOpen()
                    true
                } else {
                    false
                }
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onOpen),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .then(if (focused) Modifier.border(com.tmplayer.ui.theme.Focus.Ring, Tone.accent, MaterialTheme.shapes.medium) else Modifier),
        ) {
            SeriesArt(series, progress, Modifier.fillMaxWidth(), poster = poster, fresh = remember(series, watch) { watch.newEpisodes(series) > 0 })
        }
        // The poster's own lines (see Poster), so a show sits level with the videos beside it.
        if (poster) {
            Text(series.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
        } else {
            Text(series.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            listOfNotNull(seriesSummary(series), seriesProgressLine(progress)).joinToString("  ·  "),
            style = if (poster) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            color = if (progress.watched > 0) Tone.accent else Tone.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A show opened on the desktop: the shared panel, with the seasons in a dropdown rather than tabs,
 * which suits a pointer and keeps a show of twelve seasons on one line. Back, Escape or the arrow
 * returns to the chat's grid.
 *
 * Download season and Download unwatched go through [downloadSelected], the grid's own batch, once
 * every episode kept or coming down in any copy is left out; the toast is the batch's sentence.
 */
@Composable
internal fun SeriesPage(
    state: ShellState,
    series: Series,
    watch: SeriesWatch,
    onClose: () -> Unit,
    startOpen: Boolean = false,
) {
    val s = LocalStrings.current
    BackHandler(enabled = true) { onClose() }
    val scope = rememberCoroutineScope()
    val toast = rememberToast()
    val history by state.settings.downloadHistory.collectAsState(initial = emptyList())
    val index = remember(history) { history.associateBy { "${it.chatId}:${it.messageId}" } }
    Box(Modifier.fillMaxSize()) {
        SeriesPanel(
            series = series,
            watch = watch,
            onPlay = { state.openPlayer(it, startFromBeginning = false) },
            contentPadding = PaddingValues(start = 32.dp, end = 32.dp, bottom = 24.dp),
            modifier = Modifier.fillMaxSize(),
            seasonPicker = { selected, onSelect ->
                SeasonDropdown(series.seasons, selected, onSelect, startOpen)
            },
            onOpenItem = { state.openDetail(DetailRequest(it, state.chatTitleOf(it.chatId), outsideChat = true)) },
            onDownload = { request ->
                scope.launch {
                    val active = OfflineDownloads.active.value
                    val chosen = request.copies { it.id in index || active[it.fileId]?.busy == true }
                    toast(
                        if (chosen.isEmpty()) {
                            s.browseAlreadyDownloaded
                        } else {
                            downloadSelected(state.downloads, state.settings, chosen, state.chatTitleOf(chosen.first().chatId), index)
                        },
                    )
                }
            },
            chatTitle = state.chatTitleOf(series.cover.chatId),
        )
        // Over the backdrop, on a dark disc so it reads on any picture.
        PageDiscButton(
            Icons.AutoMirrored.Filled.ArrowBack,
            s.seriesBack,
            onClose,
            Modifier.align(Alignment.TopStart).padding(16.dp),
        )
    }
}

/** "Season 2 · 4 episodes" on a button, and every season in the menu under it. */
@Composable
internal fun SeasonDropdown(
    seasons: List<SeriesSeason>,
    selected: Int,
    onSelect: (Int) -> Unit,
    startOpen: Boolean = false,
) {
    val s = LocalStrings.current
    var open by remember { mutableStateOf(startOpen) }
    val current = seasons.firstOrNull { it.number == selected } ?: seasons.first()
    Box {
        OutlinedButton(onClick = { open = true }) {
            Text(
                s.seriesSeason(current.number) + "  ·  " + s.seriesEpisodesCount(current.episodes.size),
                style = MaterialTheme.typography.labelLarge,
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = s.seriesSeasonPicker, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            seasons.forEach { season ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                s.seriesSeason(season.number),
                                color = if (season.number == selected) Tone.accent else Color.Unspecified,
                            )
                            Text(
                                s.seriesEpisodesCount(season.episodes.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = Tone.muted,
                            )
                        }
                    },
                    onClick = {
                        onSelect(season.number)
                        open = false
                    },
                )
            }
        }
    }
}
