package com.tmplayer.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tmplayer.data.MediaItem
import com.tmplayer.data.Series
import com.tmplayer.data.SeriesProgress
import com.tmplayer.ui.components.PhoneSheet
import com.tmplayer.ui.components.isTouch
import com.tmplayer.ui.components.keepScrollInSheet
import com.tmplayer.ui.theme.Tone
import com.tmplayer.ui.theme.Tv

/**
 * A show's tile in a chat's grid or a row on Home: the frame a video's tile has ([VideoTile]), with
 * the show's art and "2 seasons · 18 eps" under its name, as a 2:3 [poster] card or a 16:9 one.
 * [badge] goes in the art's top end corner.
 */
@Composable
internal fun SeriesCard(
    series: Series,
    progress: SeriesProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dense: Boolean = false,
    onFocused: () -> Unit = {},
    badge: (@Composable () -> Unit)? = null,
    /** A 2:3 poster card, in a grid of films and shows; a 16:9 card in a row of videos. */
    poster: Boolean = false,
    /** Episodes posted since the viewer last watched the show: a "New" badge on the picture. */
    fresh: Boolean = false,
) {
    val summary = listOfNotNull(seriesSummary(series), seriesProgressLine(progress)).joinToString("  ·  ")
    VideoTile(
        title = series.title,
        onClick = onClick,
        onFocused = onFocused,
        dense = dense,
        poster = poster,
        modifier = modifier,
        meta = { ink ->
            Text(
                summary,
                style = if (poster) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                color = if (progress.watched > 0) Tone.accent else ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    ) {
        SeriesArt(series, progress, Modifier.fillMaxSize(), compact = dense, badge = badge, poster = poster, fresh = fresh)
    }
}

/**
 * A show, opened: a bottom sheet on a phone and a page of its own on a television. Choosing an
 * episode plays it and closes this, so Back from the player lands on the chat, not on the sheet.
 */
@Composable
internal fun SeriesOpened(
    series: Series,
    watch: SeriesWatch,
    onPlay: (MediaItem) -> Unit,
    onDismiss: () -> Unit,
    onLongClick: ((MediaItem) -> Unit)? = null,
    /** Download season and Download unwatched; see [queueSeason]. Null leaves them out. */
    onDownload: ((SeasonDownloadRequest) -> Unit)? = null,
) {
    if (isTouch()) {
        SeriesSheet(series, watch, onPlay, onDismiss, onLongClick, onDownload)
    } else {
        TvSeriesPage(series, watch, onPlay, onDismiss, onDownload)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeriesSheet(
    series: Series,
    watch: SeriesWatch,
    onPlay: (MediaItem) -> Unit,
    onDismiss: () -> Unit,
    onLongClick: ((MediaItem) -> Unit)?,
    onDownload: ((SeasonDownloadRequest) -> Unit)?,
) {
    // Opened all the way: half a sheet of episodes is two rows, and the list is the point.
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // The detail sheet's fill, corner and hairline, so the two sheets a grid opens look alike. The
    // episodes keep their scrolling to themselves, so reaching the top of the list does not drag
    // the sheet; the header above them still pulls it down.
    PhoneSheet(onDismissRequest = onDismiss, sheetState = state) {
        SeriesPanel(
            series = series,
            watch = watch,
            onPlay = { onDismiss(); onPlay(it) },
            onLongClick = onLongClick,
            onDownload = onDownload,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f).keepScrollInSheet(),
        )
    }
}

/**
 * The television's page: full screen, inside the overscan, with the play button focused first.
 * A window of its own, like [com.tmplayer.ui.components.TvMenu], so Back closes it and the grid
 * behind keeps the focus it had.
 */
@Composable
private fun TvSeriesPage(
    series: Series,
    watch: SeriesWatch,
    onPlay: (MediaItem) -> Unit,
    onDismiss: () -> Unit,
    onDownload: ((SeasonDownloadRequest) -> Unit)?,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Tone.background)) {
            SeriesPanel(
                series = series,
                watch = watch,
                onPlay = { onDismiss(); onPlay(it) },
                onDownload = onDownload,
                contentPadding = PaddingValues(start = Tv.SafeH, end = Tv.SafeH, top = 4.dp, bottom = Tv.SafeV),
                modifier = Modifier.fillMaxSize().padding(top = Tv.SafeV + 8.dp),
            )
        }
    }
}
