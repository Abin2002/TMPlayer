package com.tmplayer.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.key
import com.tmplayer.data.BookmarkJump
import com.tmplayer.data.ContentProtection
import com.tmplayer.data.DetailAction
import com.tmplayer.data.DetailActions
import com.tmplayer.data.DetailContext
import com.tmplayer.data.DiskInfo
import com.tmplayer.data.MediaItem
import com.tmplayer.data.OfflineDownloads
import com.tmplayer.data.SettingsStore
import com.tmplayer.data.Td
import com.tmplayer.data.valueOrNull
import com.tmplayer.desktop.DesktopPaths
import com.tmplayer.desktop.DownloadIndex
import com.tmplayer.desktop.os.OpenExternal
import com.tmplayer.desktop.setWatchLater
import com.tmplayer.desktop.setWatched
import com.tmplayer.ui.browse.DETAIL_PANE_WIDTH
import com.tmplayer.ui.browse.DetailRow
import com.tmplayer.ui.browse.MediaDetailPanel
import com.tmplayer.ui.browse.detailRow
import com.tmplayer.ui.components.rememberToast
import com.tmplayer.ui.i18n.LocalStrings
import com.tmplayer.ui.nav.BackHandler
import com.tmplayer.ui.theme.Corner
import com.tmplayer.ui.theme.FloatingTone
import com.tmplayer.ui.theme.floatingBorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File

/**
 * The detail pane (R5) over whatever page is showing, when [ShellState.detail] asks for one: the
 * shared [MediaDetailPanel] along the right edge, over a scrim. Esc, Back or a click on the scrim
 * closes it, and focus goes back to the poster it came from.
 *
 * What the shell draws where the page is too narrow to keep beside the pane ([DetailPaneSide]),
 * and what the render tests draw over a page of their own.
 */
@Composable
fun BoxScope.DetailPaneHost(state: ShellState) {
    val request = state.detail ?: return
    BackHandler { state.closeDetail() }
    Box(
        Modifier
            .matchParentSize()
            .background(FloatingTone.scrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { state.closeDetail() },
    )
    PaneSheet(state, request, Modifier.align(Alignment.CenterEnd))
}

/**
 * The detail pane docked as a side panel beside the page, with no scrim: the page stays in view
 * and in reach, a click on another poster swaps what the pane shows, and Esc, Back or its close
 * button hide it.
 */
@Composable
fun DetailPaneSide(state: ShellState) {
    val request = state.detail ?: return
    BackHandler { state.closeDetail() }
    PaneSheet(state, request, Modifier)
}

@Composable
private fun PaneSheet(state: ShellState, request: DetailRequest, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(topStart = Corner.ExtraLarge, bottomStart = Corner.ExtraLarge),
        color = FloatingTone.sheet,
        border = floatingBorder(),
        modifier = modifier
            .fillMaxHeight()
            .width(DETAIL_PANE_WIDTH)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    state.closeDetail()
                    true
                } else {
                    false
                }
            },
    ) {
        // A new video is a new pane: nothing of the last one's state carries over.
        key(request.item.id) { DetailPane(state, request) }
    }
}

/** From this width of page the pane docks beside it: the pane and a row of posters still fit. */
internal val DOCK_FROM = DETAIL_PANE_WIDTH + 560.dp

@Composable
private fun DetailPane(state: ShellState, request: DetailRequest) {
    val s = LocalStrings.current
    val item = request.item
    val scope = rememberCoroutineScope()
    val toast = rememberToast()
    val progress by state.settings.watchProgress.collectAsState(initial = emptyMap())
    val watchedList by state.watched.watched.collectAsState(initial = emptyMap())
    val laterKeys by state.watched.watchLater.keys.collectAsState(initial = emptySet())
    val history by state.settings.downloadHistory.collectAsState(initial = emptyList())
    val downloads by OfflineDownloads.active.collectAsState()
    val key = SettingsStore.progressKey(item.chatId, item.messageId)
    val point = progress[key]
    val finished = key in watchedList
    val record = history.firstOrNull { it.chatId == item.chatId && it.messageId == item.messageId }
    val queued = downloads[item.fileId]?.busy == true
    val title = request.chatTitle.ifBlank { state.chatTitleOf(item.chatId) }
    val chat = state.chatOf(item.chatId)
    val free by produceState(0L, item.id) {
        value = withContext(Dispatchers.IO) { DiskInfo.of(DesktopPaths.downloadsDir).freeBytes }
    }
    val first = remember(item.id) { FocusRequester() }
    val bookmarks by remember(item.chatId, item.messageId) {
        state.bookmarks?.bookmarks(item.chatId, item.messageId) ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val actions = DetailActions.of(
        DetailContext(
            canBeSaved = item.canBeSaved,
            resumeMs = point?.positionMs ?: 0L,
            finished = finished,
            watchLater = key in laterKeys,
            inDownloads = record != null,
            cached = item.onDevice,
            downloading = queued,
            canSelect = request.selectThis != null,
            // Another app is the desktop's one hand off: there is no share sheet, so Share is dropped below.
            canHandOff = true,
            hasClipboard = true,
            outsideChat = request.outsideChat && chat != null,
        ),
    ).filterNot { it == DetailAction.Share }

    fun close() = state.closeDetail()
    // "Remove from Downloads?" is asked over the pane, which stays open until it is answered.
    var confirmingRemove by remember(item.id) { mutableStateOf(false) }

    fun run(action: DetailAction) {
        if (action == DetailAction.RemoveDownload) {
            confirmingRemove = record != null
            return
        }
        close()
        when (action) {
            DetailAction.Resume, DetailAction.Play -> state.openPlayer(item, startFromBeginning = false)
            DetailAction.StartOver -> state.openPlayer(item, startFromBeginning = true)
            DetailAction.MarkWatched, DetailAction.MarkUnwatched -> {
                val mark = action == DetailAction.MarkWatched
                scope.launch {
                    runCatching { setWatched(state.watched, state.settings, item, title, mark) }
                    toast(if (mark) s.watchedMarkedWatched(item.title) else s.watchedMarkedUnwatched(item.title))
                }
            }
            DetailAction.AddWatchLater, DetailAction.RemoveWatchLater -> {
                val add = action == DetailAction.AddWatchLater
                scope.launch {
                    runCatching { setWatchLater(state.watched, item, title, add) }
                    toast(if (add) s.detailWatchLaterAdded(item.title) else s.detailWatchLaterRemoved(item.title))
                }
            }
            DetailAction.Download -> {
                OfflineDownloads.start(state.downloads, item, title)
                toast(s.downloadsDownloadingTitle(item.title))
            }
            DetailAction.SaveToDownloads -> {
                OfflineDownloads.start(state.downloads, item, title)
                toast(s.downloadsSavingTitle(item.title))
            }
            DetailAction.InDownloads -> state.go(Destination.Downloads)
            DetailAction.RemoveDownload -> Unit
            DetailAction.CancelDownload -> OfflineDownloads.cancel(state.downloads, item.fileId)
            DetailAction.SelectVideos -> request.selectThis?.invoke()
            DetailAction.Share -> Unit
            DetailAction.OpenElsewhere -> scope.launch {
                if (record == null && !Td.maySave(item.chatId, item.messageId)) {
                    toast(ContentProtection.NOT_SAVABLE)
                    return@launch
                }
                val file = record?.localPath?.let(::File)?.takeIf { it.isFile }
                    ?: runCatching { Td.localFilePath(Td.currentFileId(item.chatId, item.messageId, item.fileId)) }
                        .getOrNull()?.let(::File)
                if (file == null) toast(s.downloadsFileGone) else OpenExternal.open(file)
            }
            DetailAction.CopyLink -> scope.launch {
                val link = runCatching {
                    Td.client.getMessageLink(item.chatId, item.messageId, 0, 0, "", false, false).valueOrNull?.link
                }.getOrNull()
                if (link.isNullOrBlank()) {
                    toast(s.commonNoLinks)
                } else {
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(link), null)
                    toast(s.commonLinkCopied)
                }
            }
            DetailAction.OpenChat -> chat?.let(state::openChat)
        }
    }

    val freeLabel = DiskInfo.freeLabel(free)
    val rows: List<DetailRow> = actions.map { action ->
        val row = detailRow(action, s, point, chat?.title ?: title, onSelect = { run(action) }, free = freeLabel)
        // The desktop's own words where it has them: the queue lives on the Downloads page.
        if (action == DetailAction.CancelDownload) row.copy(label = s.downloadsCancel) else row
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                s.detailHeading,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            IconButton(onClick = { close() }) { Icon(Icons.Filled.Close, contentDescription = s.commonClose) }
        }
        // Keyed by the video, so "More like this" opening another starts it at the top.
        androidx.compose.runtime.key(item.id) {
            MediaDetailPanel(
                item = item,
                chatTitle = title,
                watched = point,
                finished = finished,
                rows = rows,
                firstAction = first,
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
                onOpenItem = { other ->
                    state.openDetail(DetailRequest(other, state.chatTitleOf(other.chatId), outsideChat = true, onClosed = request.onClosed))
                },
                bookmarks = bookmarks,
                // The player takes the position on opening this video, in place of its resume point.
                onOpenBookmark = { mark ->
                    close()
                    BookmarkJump.request(item.chatId, item.messageId, mark.positionMs)
                    state.openPlayer(item, startFromBeginning = false)
                },
            )
        }
    }
    LaunchedEffect(item.id) { runCatching { first.requestFocus() } }

    if (confirmingRemove) {
        ConfirmDialog(
            title = s.gridRemoveDownloadTitle,
            message = s.gridRemoveDownloadMessage(item.title),
            confirmLabel = s.commonRemove,
            onDismiss = { confirmingRemove = false },
            onConfirm = {
                confirmingRemove = false
                val kept = record
                scope.launch {
                    if (kept != null) {
                        toast(if (DownloadIndex.delete(state.settings, kept)) s.downloadsRemoved(item.title) else s.downloadsInUse)
                    }
                    close()
                }
            },
        )
    }
}
