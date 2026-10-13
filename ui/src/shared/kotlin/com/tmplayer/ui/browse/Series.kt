package com.tmplayer.ui.browse

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import com.tmplayer.ui.components.BackdropSource
import com.tmplayer.ui.components.CoverArt
import com.tmplayer.ui.components.TileBadgeInset
import com.tmplayer.ui.components.rememberCover
import com.tmplayer.ui.icons.TmIcons
import com.tmplayer.ui.online.TrailerIcon
import com.tmplayer.ui.theme.Focus
import com.tmplayer.ui.theme.Scrims
import com.tmplayer.ui.theme.rememberReducedMotion
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tmplayer.data.CourseProgress
import com.tmplayer.data.EpisodeCopies
import com.tmplayer.data.MediaItem
import com.tmplayer.data.MediaMapper
import com.tmplayer.data.NewEpisodes
import com.tmplayer.data.SeasonDownload
import com.tmplayer.data.Series
import com.tmplayer.data.SeriesEpisode
import com.tmplayer.data.SeriesProgress
import com.tmplayer.data.SeriesSeason
import com.tmplayer.data.SeriesShelf
import com.tmplayer.data.WatchPoint
import com.tmplayer.data.Watching
import com.tmplayer.player.StreamStats
import com.tmplayer.ui.components.MediaArt
import com.tmplayer.ui.online.MetaCastRow
import com.tmplayer.ui.online.MetaCredit
import com.tmplayer.ui.online.MetaFactsLines
import com.tmplayer.ui.online.MoreLikeThisRow
import com.tmplayer.ui.online.TrailerHost
import com.tmplayer.ui.online.TrailerQr
import com.tmplayer.ui.online.rememberMetaExtras
import com.tmplayer.ui.online.rememberTrailerLauncher
import com.tmplayer.ui.online.MetaPicture
import com.tmplayer.ui.online.rememberMeta
import com.tmplayer.ui.components.WatchedBadge
import com.tmplayer.ui.components.isTouch
import com.tmplayer.ui.i18n.LocalStrings
import com.tmplayer.ui.theme.Corner
import com.tmplayer.ui.theme.Tone
import com.tmplayer.ui.theme.focusRing

/*
 * The series view (R5), shared by the phone, the television and the desktop: a show's picture for
 * its tile, and the panel with the season picker and the episode list. Each platform puts its own chrome round these: a bottom sheet on the phone, a full
 * screen page on the television, and a page with a season dropdown on the desktop.
 */

/**
 * What the panels need to know about watching, read from the platform's stores.
 *
 * [finished] is handed the Watched list; a video also counts as watched once its saved position is
 * past the watched mark (see [Watching.counts]), so a show's count and a course's agree.
 */
class SeriesWatch(
    val point: (MediaItem) -> WatchPoint?,
    finished: (MediaItem) -> Boolean,
    /**
     * When the viewer last watched a video, in milliseconds, or null when nothing says (see
     * [NewEpisodes.touchTimes]). Only the "New" badge reads it; without it no show is new.
     */
    val touchedAt: (MediaItem) -> Long? = { null },
) {
    private val onList = finished
    val finished: (MediaItem) -> Boolean = { item -> Watching.counts(onList(item), point(item)) }

    /** How far through the course [items] make up, or null when they are not one: see [CourseProgress.of]. */
    fun course(items: List<MediaItem>): CourseProgress? = CourseProgress.of(items, onList, point)

    fun progress(series: Series): SeriesProgress =
        SeriesShelf.progress(series, finished) { point(it)?.fraction ?: 0f }

    /** How many of [series]' episodes arrived since the viewer last watched it: see [NewEpisodes.inSeries]. */
    fun newEpisodes(series: Series): Int =
        NewEpisodes.inSeries(series, { finished(it) || (point(it)?.fraction ?: 0f) > 0f }, touchedAt)

    /** Watched when any copy of the episode is. */
    fun finishedEpisode(episode: SeriesEpisode): Boolean = episode.copies.any(finished)

    /** Where the viewer stopped in whichever copy of the episode got furthest. */
    fun pointOf(episode: SeriesEpisode): WatchPoint? =
        episode.copies.mapNotNull(point).maxByOrNull { it.fraction }

    /** The copy of [episode] to play: see [SeriesShelf.pick]. */
    fun pick(episode: SeriesEpisode, progress: SeriesProgress): MediaItem =
        SeriesShelf.pick(episode, finished, { point(it)?.fraction ?: 0f }, progress.like)

    /** "Download season" or "Download unwatched" for [season]: see [SeasonDownload.copies]. */
    fun download(series: Series, season: SeriesSeason, which: SeasonDownload.Which) = SeasonDownloadRequest { held ->
        SeasonDownload.copies(series, season, which, finished, { point(it)?.fraction ?: 0f }, held)
    }

    companion object {
        val None = SeriesWatch({ null }, { false })
    }
}

/**
 * A press of "Download season" or "Download unwatched", answered with the copies to queue once the
 * platform has said which are downloaded already or on the queue: that answer is the platform's
 * to read, and on a phone it is a walk of the download records off the drawing thread.
 */
fun interface SeasonDownloadRequest {
    fun copies(held: (MediaItem) -> Boolean): List<MediaItem>
}

/**
 * Where a chat-list search looks: the chats by name, or the videos in every chat as well (see
 * [com.tmplayer.data.SearchScope]).
 */
@Composable
fun SearchScopeToggle(
    scope: com.tmplayer.data.SearchScope,
    onChange: (com.tmplayer.data.SearchScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val tv = !isTouch()
    Row(
        modifier
            .wrapContentWidth(Alignment.Start)
            .clip(CircleShape)
            .background(Tone.surfaceHigh)
            .padding(3.dp)
            .semantics { contentDescription = s.browseSearchScope },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Segment(s.browseSearchScopeChats, chosen = scope == com.tmplayer.data.SearchScope.Chats, tv = tv) {
            onChange(com.tmplayer.data.SearchScope.Chats)
        }
        Segment(s.browseSearchScopeAllVideos, chosen = scope == com.tmplayer.data.SearchScope.AllVideos, tv = tv) {
            onChange(com.tmplayer.data.SearchScope.AllVideos)
        }
    }
}

@Composable
private fun Segment(label: String, chosen: Boolean, tv: Boolean, onClick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    // Which segment is chosen and where the remote is are two different answers, and in the dark
    // theme the focus fill is the accent, so focus is a ring rather than a fill here.
    val fill = when {
        chosen -> Tone.accent
        focused -> Tone.text.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    val ink = if (chosen) Tone.onAccent else Tone.text
    Box(
        Modifier
            .height(if (tv) 40.dp else 34.dp)
            .clip(CircleShape)
            .background(fill)
            .border(if (focused) 2.dp else 0.dp, if (focused) Tone.text else Color.Transparent, CircleShape)
            .semantics {
                role = Role.Tab
                selected = chosen
            }
            .clickable(interactionSource = interactions, indication = if (tv) null else androidx.compose.foundation.LocalIndication.current, onClick = onClick)
            .padding(horizontal = if (tv) 20.dp else 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = if (tv) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge,
            color = ink,
            maxLines = 1,
        )
    }
}

/** "2 seasons · 18 eps", the tile's second line. */
@Composable
fun seriesSummary(series: Series): String =
    LocalStrings.current.seriesSummary(series.seasons.size, series.episodeCount)

/** "3 of 18 watched", "All watched", or nothing for a show not yet started. */
@Composable
fun seriesProgressLine(progress: SeriesProgress): String? {
    val s = LocalStrings.current
    return when {
        progress.finished -> s.seriesAllWatched
        progress.watched > 0 -> s.seriesProgress(progress.watched, progress.total)
        else -> null
    }
}

/**
 * The seasons as a row of tabs. On a television the season under the remote is the one shown, so
 * walking Left and Right along the row pages through the seasons and Down enters the episodes.
 */
@Composable
fun SeasonTabs(
    seasons: List<SeriesSeason>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val s = LocalStrings.current
    val tv = !isTouch()
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        seasons.forEach { season ->
            val interactions = remember { MutableInteractionSource() }
            val focused by interactions.collectIsFocusedAsState()
            val chosen = season.number == selected
            LaunchedEffect(focused) { if (focused && tv) onSelect(season.number) }
            val fill = when {
                focused -> Tone.focusFill
                chosen -> Tone.accent.copy(alpha = 0.18f)
                else -> Tone.surfaceHigh
            }
            Column(
                Modifier
                    .then(if (chosen && focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .clip(RoundedCornerShape(Corner.Small))
                    .background(fill)
                    .border(
                        if (chosen && !focused) 1.5.dp else 0.dp,
                        if (chosen && !focused) Tone.accent else Color.Transparent,
                        RoundedCornerShape(Corner.Small),
                    )
                    .focusRing(focused, RoundedCornerShape(Corner.Small))
                    .semantics {
                        role = Role.Tab
                        this.selected = chosen
                    }
                    .clickable(interactionSource = interactions, indication = null) { onSelect(season.number) }
                    .padding(horizontal = if (tv) 20.dp else 16.dp, vertical = if (tv) 10.dp else 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    s.seriesSeason(season.number),
                    style = if (tv) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
                    fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
                    color = when {
                        focused -> Tone.onFocusFill
                        chosen -> Tone.accent
                        else -> Tone.text
                    },
                    maxLines = 1,
                )
                Text(
                    s.seriesEpisodesCount(season.episodes.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (focused) Tone.onFocusFill.copy(alpha = 0.85f) else Tone.muted,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * One episode as a card: its Telegram thumbnail with the progress line and the tick on it, the
 * "S1 E3" label, "Episode 3", the file's own name, and the running time, size and where the
 * viewer stopped.
 *
 * The thumbnail is the one TDLib already has for the message (the inline preview first, then the
 * small thumbnail file), loaded as the row scrolls into a lazy list; nothing of the video itself is
 * fetched for it. An episode posted with no thumbnail gets its generated cover, with its number set
 * large, so a long season of bare files still reads as a numbered set.
 *
 * An episode the chat holds in several copies (350 MB, 600 MB, 1.8 GB) is still one card. The row
 * plays [chosen], the copy most like the one the viewer watched last, and a line of chips under it
 * names every copy by its quality and size so another can be picked with [onCopy].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EpisodeRow(
    episode: SeriesEpisode,
    point: WatchPoint?,
    finished: Boolean,
    upNext: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    chosen: MediaItem = episode.item,
    onCopy: ((MediaItem) -> Unit)? = null,
    /** The episode playing, in the player's episode list: tinted and badged as such. */
    current: Boolean = false,
    /**
     * On a television, holding OK is [onLongClick]: the player's episode list marks an episode
     * watched that way. Off elsewhere, where OK only plays.
     */
    holdOk: Boolean = false,
    /** Hung on the row itself rather than on the copies under it, for focus to land on. */
    lineModifier: Modifier = Modifier,
) {
    val tv = !isTouch()
    val shape = RoundedCornerShape(Corner.Medium)
    val card = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(if (current) Tone.accent.copy(alpha = 0.14f) else Tone.surface)
        .border(1.dp, if (current) Tone.accent.copy(alpha = 0.5f) else Tone.outline, shape)
    if (episode.copies.size <= 1 || onCopy == null) {
        EpisodeLine(episode, chosen, point, finished, upNext, onClick, card.then(lineModifier), onLongClick, current, holdOk)
        return
    }
    // DOWN from the row goes to the chip of the copy it plays, not to whichever chip sits nearest
    // the row's middle (which, on a wide television row, was the last one).
    val chips = remember(episode.copies.size) { List(episode.copies.size) { FocusRequester() } }
    val landing = chips[episode.copies.indexOfFirst { it.id == chosen.id }.coerceAtLeast(0)]
    Column(card) {
        EpisodeLine(episode, chosen, point, finished, upNext, onClick, lineModifier.focusProperties { down = landing }, onLongClick, current, holdOk)
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(
                    // Under the text, past the picture and the row's own padding and gap.
                    start = if (tv) EPISODE_ART_TV + 10.dp + 16.dp else EPISODE_ART_TOUCH + 8.dp + 12.dp,
                    end = 8.dp,
                    bottom = 10.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            episode.copies.forEachIndexed { at, copy ->
                CopyChip(copy, chosen = copy.id == chosen.id, onClick = { onCopy(copy) }, modifier = Modifier.focusRequester(chips[at]))
            }
        }
    }
}

/**
 * One copy of an episode or a film, by its quality and size: "1080p · 1.8 GB", in a round chip.
 * Under the remote it takes the focus fill, as every other television control does.
 */
@Composable
internal fun CopyChip(copy: MediaItem, chosen: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val tv = !isTouch()
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val label = EpisodeCopies.label(copy)
    val shape = CircleShape
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
        color = when {
            focused -> Tone.onFocusFill
            chosen -> Tone.accent
            else -> Tone.muted
        },
        maxLines = 1,
        modifier = modifier
            .clip(shape)
            .background(
                when {
                    focused -> Tone.focusFill
                    chosen -> Tone.accent.copy(alpha = 0.14f)
                    else -> Color.Transparent
                },
            )
            .focusRing(focused, shape)
            .border(
                1.dp,
                when {
                    focused -> Color.Transparent
                    chosen -> Tone.accent.copy(alpha = 0.6f)
                    else -> Tone.muted.copy(alpha = 0.4f)
                },
                shape,
            )
            .semantics { contentDescription = s.seriesCopy(label) }
            .clickable(interactionSource = interactions, indication = if (tv) null else androidx.compose.foundation.LocalIndication.current, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = if (tv) 5.dp else 3.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeLine(
    episode: SeriesEpisode,
    item: MediaItem,
    point: WatchPoint?,
    finished: Boolean,
    upNext: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    onLongClick: (() -> Unit)?,
    current: Boolean = false,
    holdOk: Boolean = false,
) {
    val s = LocalStrings.current
    val tv = !isTouch()
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val shape = RoundedCornerShape(Corner.Medium)
    val cover = rememberCover(item)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (focused) Tone.surfaceHigh else Color.Transparent)
            .border(if (focused) Focus.Edge else 0.dp, if (focused) Tone.accent else Color.Transparent, shape)
            .then(
                if (onLongClick != null && tv && holdOk) {
                    Modifier.okHold(interactions, onClick, onLongClick)
                } else if (onLongClick != null && !tv) {
                    Modifier.combinedClickable(interactionSource = interactions, indication = androidx.compose.foundation.LocalIndication.current, onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier.clickable(interactionSource = interactions, indication = if (tv) null else androidx.compose.foundation.LocalIndication.current, onClick = onClick)
                },
            )
            .padding(if (tv) 10.dp else 8.dp),
        horizontalArrangement = Arrangement.spacedBy(if (tv) 16.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(if (tv) EPISODE_ART_TV else EPISODE_ART_TOUCH)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(Corner.Small)),
        ) {
            MediaArt(item.miniThumbnail, item.thumbnailFileId, Modifier.fillMaxSize(), cover = cover, compactCover = true)
            val fraction = when {
                point != null && point.fraction > 0f -> point.fraction
                finished -> 1f
                else -> 0f
            }
            if (fraction > 0f) {
                Box(Modifier.matchParentSize().background(Scrims.tileFoot))
                com.tmplayer.ui.components.ProgressLine(fraction, Modifier.align(Alignment.BottomCenter), inset = 6.dp)
            }
            if (finished) {
                WatchedBadge(
                    size = if (tv) 24.dp else 20.dp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(TileBadgeInset),
                )
            }
            if (upNext && !finished) {
                Icon(
                    TmIcons.Play,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(if (tv) 40.dp else 32.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(4.dp),
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    s.seriesEpisodeShort(episode.season, episode.episode),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Tone.muted,
                    maxLines = 1,
                )
                when {
                    current -> EpisodeBadge(s.episodesPlaying)
                    upNext && !finished -> EpisodeBadge(s.seriesUpNextBadge)
                }
            }
            Text(
                s.seriesEpisode(episode.episode),
                style = if (tv) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                color = Tone.text,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                item.title,
                style = if (tv) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                color = Tone.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val stopped = point?.takeIf { it.positionMs > 0 && !finished }
                ?.let { s.seriesStoppedAt(StreamStats.formatClock(it.positionMs)) }
            val meta = listOfNotNull(
                MediaMapper.formatDuration(item.durationSec).ifEmpty { null },
                MediaMapper.formatSize(item.sizeBytes).ifEmpty { null },
                stopped ?: s.seriesWatchedLabel.takeIf { finished },
            ).joinToString("  ·  ")
            if (meta.isNotEmpty()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (stopped != null || finished) Tone.accent else Tone.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** "Up next" or "Playing" beside an episode's label, in the accent. */
@Composable
private fun EpisodeBadge(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = Tone.onAccent,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(Tone.accent)
            .padding(horizontal = 8.dp, vertical = 1.dp),
    )
}

/**
 * A show opened, as a page: the backdrop (the show's own when posters and overviews have one,
 * else the next episode's Telegram thumbnail as a soft field, else its generated cover) with the
 * poster beside the title when online, the facts line ("2021 · 3 seasons · 24 eps · 12 of 24
 * watched · the chat"), the overview, one bright pill straight to the next episode and the other
 * actions round beside it, then the season picker and the episodes of the chosen season as cards.
 * Everything scrolls as one, so the backdrop leaves the screen as the episodes come up.
 *
 * [seasonPicker] is the platform's: tabs on a phone and a television ([SeasonTabs], the default),
 * a dropdown on the desktop. On a television the play button takes focus first, so OK straight
 * after opening a show carries on where the viewer left it, and Down walks to the seasons and
 * then the episodes.
 *
 * Beside the play button, Download queues the season shown. Once some of that season is watched
 * and some is not, Download season and Download unwatched are two buttons side by side, so a
 * remote reaches both along the same line.
 *
 * [contentPadding] is the page's margin for the words and the rows; the backdrop runs full bleed
 * under it. [trailing] sits at the top end over the backdrop, for the platform's own close or back.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeriesPanel(
    series: Series,
    watch: SeriesWatch,
    onPlay: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onLongClick: ((MediaItem) -> Unit)? = null,
    seasonPicker: @Composable (selected: Int, onSelect: (Int) -> Unit) -> Unit = { selected, onSelect ->
        SeasonTabs(series.seasons, selected, onSelect)
    },
    /** At the top end over the backdrop, for the platform's own close or back control. */
    trailing: @Composable () -> Unit = {},
    /** Opens a video's detail, for "More like this"; null leaves that row out. */
    onOpenItem: ((MediaItem) -> Unit)? = onLongClick,
    /**
     * Queues the season shown, or its unwatched episodes, through the platform's multi-download;
     * null leaves the download buttons out.
     */
    onDownload: ((SeasonDownloadRequest) -> Unit)? = null,
    /** The chat the show was posted in, for the facts line; null leaves it out. */
    chatTitle: String? = null,
) {
    val s = LocalStrings.current
    val tv = !isTouch()
    val progress = watch.progress(series)
    val next = progress.next
    var season by rememberSaveable(series.key) {
        mutableIntStateOf(next?.season ?: series.seasons.first().number)
    }
    val shown = series.seasons.firstOrNull { it.number == season } ?: series.seasons.first()
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(series.key) { if (tv) runCatching { playFocus.requestFocus() } }
    // Download is for the season shown, so its buttons follow the season picked.
    val downloads = onDownload != null && SeasonDownload.anySavable(shown)
    val bothDownloads = downloads && SeasonDownload.unwatchedDiffers(shown, watch.finished)

    val meta = rememberMeta(series.cover, showOnly = true)
    val extras = rememberMetaExtras(meta)
    val trailers = rememberTrailerLauncher()
    val target = next ?: series.episodes.first()
    val art = next?.item ?: target.item
    val cover = remember(art.chatId, art.messageId, series.title) { CoverArt.of(art).copy(title = series.title, number = null) }
    val source = remember(art.id, cover, meta?.backdropUrl) { BackdropSource(art.miniThumbnail, art.thumbnailFileId, cover, meta?.backdropUrl) }
    val reduced = rememberReducedMotion()
    val pad = contentPadding.horizontalOnly()

    // An anime season runs to hundreds of episodes, and scrolling to the 850th is no way to find
    // it. Past [EPISODE_RANGE] the season is cut into ranges, opened on the one holding the next
    // episode to watch.
    val ranges = remember(shown) {
        if (shown.episodes.size > EPISODE_RANGE) shown.episodes.chunked(EPISODE_RANGE) else emptyList()
    }
    var rangeAt by remember(shown) {
        mutableIntStateOf(ranges.indexOfFirst { range -> next != null && range.any { it.sameAs(next) } }.coerceAtLeast(0))
    }
    val listed = ranges.getOrNull(rangeAt) ?: shown.episodes
    val list = rememberLazyListState()
    // Another season or range picked while the episodes fill the screen: back up to the picker,
    // with the new list under it. Opening is its own case, below.
    var opened by remember(series.key) { mutableStateOf(false) }
    // Where the up-next episode sits in the list shown, or -1 when it is in another season or range.
    val upNextAt = if (next != null) listed.indexOfFirst { it.sameAs(next) } else -1
    val upNextFocus = remember(series.key) { FocusRequester() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(shown.number, rangeAt) {
        if (!opened) {
            opened = true
            // A long season (an anime's hundreds) opens on its up-next episode under a finger, one
            // card of what came before above it, so nobody scrolls to the 57th by hand. A short one
            // opens at the top, where the hero and Play are. A remote gets there with Down from Play.
            if (!tv && upNextAt > 0 && listed.size > LONG_SEASON) list.scrollToItem(FIRST_EPISODE_ITEM + upNextAt - 1)
            return@LaunchedEffect
        }
        if (list.firstVisibleItemIndex > SEASONS_ITEM) list.scrollToItem(SEASONS_ITEM)
    }
    // On a television Down from Play goes straight to the up-next card, scrolled into view first
    // since a lazy list has not composed it yet; Up from the cards still reaches the seasons.
    val downToUpNext = if (tv && upNextAt >= 0) {
        Modifier.onPreviewKeyEvent { event ->
            if (event.key != Key.DirectionDown) return@onPreviewKeyEvent false
            if (event.type == KeyEventType.KeyDown) {
                scope.launch {
                    list.scrollToItem((FIRST_EPISODE_ITEM + upNextAt - 1).coerceAtLeast(SEASONS_ITEM))
                    withFrameNanos { }
                    runCatching { upNextFocus.requestFocus() }
                }
            }
            true
        }
    } else {
        Modifier
    }

    BoxWithConstraints(modifier) {
        val wide = maxWidth > maxHeight || maxWidth >= WIDE_PAGE
        val heroHeight = if (wide) {
            (maxHeight * 0.62f).coerceIn(280.dp, 560.dp)
        } else {
            tallBackdropHeight(maxWidth, maxHeight, online = meta?.backdropUrl != null)
        }
        val heading: @Composable () -> Unit = {
            Text(
                meta?.title?.takeIf { it.isNotBlank() } ?: series.title,
                style = MaterialTheme.typography.displaySmall,
                color = Tone.text,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
        }
        val poster: @Composable () -> Unit = {
            meta?.posterUrl?.let { url ->
                Box(
                    Modifier
                        .width(if (tv) SERIES_POSTER_TV else if (wide) SERIES_POSTER_WIDE else SERIES_POSTER)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(Corner.Medium))
                        .background(Tone.surfaceHigh),
                ) {
                    MetaPicture(url, Modifier.fillMaxSize(), maxWidth = 342, contentDescription = s.metadataPoster)
                }
            }
        }
        val facts: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FactLine(
                    listOfNotNull(
                        meta?.year?.let { Fact(it.toString()) },
                        Fact(seriesSummary(series)),
                        seriesProgressLine(progress)?.let { Fact(it, accent = true) },
                        chatTitle?.takeIf { it.isNotBlank() }?.let { Fact(it) },
                    ),
                )
                MetaFactsLines(extras, maxLines = 1)
                // How far through the whole show; the facts line says it in words.
                if (progress.watched > 0) ProgressLine(progress.watched.toFloat() / progress.total, Modifier.widthIn(max = 240.dp))
            }
        }
        val overview: @Composable () -> Unit = {
            meta?.overview?.takeIf { it.isNotBlank() }?.let { text ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Synopsis(text, lines = if (wide) 3 else null)
                    MetaCredit(meta)
                }
            }
        }
        val actions: @Composable () -> Unit = {
            val started = (watch.pointOf(target)?.positionMs ?: 0L) > 0
            val others = buildList {
                extras?.trailer?.let { trailer -> add(Triple(s.metadataTrailer, TrailerIcon) { trailers.open(trailer) }) }
                if (downloads) {
                    add(Triple(s.seriesDownloadSeason, TmIcons.Download) { onDownload?.invoke(watch.download(series, shown, SeasonDownload.Which.Season)); Unit })
                }
                if (bothDownloads) {
                    add(Triple(s.seriesDownloadUnwatched, TmIcons.Download) { onDownload?.invoke(watch.download(series, shown, SeasonDownload.Which.Unwatched)); Unit })
                }
            }
            val hero: @Composable (Modifier) -> Unit = { m ->
                HeroPill(
                    label = when {
                        started -> s.seriesResume(target.code)
                        progress.watched > 0 -> s.seriesContinue(target.code)
                        else -> s.seriesPlay(target.code)
                    },
                    icon = TmIcons.Play,
                    onClick = { onPlay(next?.item ?: watch.pick(target, progress)) },
                    modifier = m.then(downToUpNext),
                    focusRequester = playFocus,
                )
            }
            Column(Modifier.padding(pad), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (tv) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        hero(Modifier)
                        others.forEach { (label, icon, run) -> RoundAction(label, icon, run) }
                    }
                } else {
                    hero(if (wide) Modifier else Modifier.fillMaxWidth())
                    if (others.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            others.forEach { (label, icon, run) -> RoundAction(label, icon, run) }
                        }
                    }
                }
                trailers.unopened?.takeIf { it == extras?.trailer }?.let { TrailerQr(it) }
                TrailerHost(trailers)
            }
        }

        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(if (tv) 8.dp else 6.dp),
        ) {
            item(key = "hero") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.fillMaxWidth().heightIn(min = heroHeight).clipToBounds()) {
                        Box(Modifier.matchParentSize()) {
                            PageBackdrop(
                                source,
                                fromStart = wide,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .fillMaxWidth(if (wide) BACKDROP_SHARE else 1f)
                                    .fillMaxHeight()
                                    .graphicsLayer {
                                        // Half the list's speed while the hero is on screen; none with animations off.
                                        if (!reduced && list.firstVisibleItemIndex == 0) translationY = list.firstVisibleItemScrollOffset * PARALLAX
                                    },
                            )
                        }
                        Row(
                            Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth(if (wide) TEXT_SHARE else 1f)
                                .padding(pad)
                                .padding(top = heroHeight * 0.35f, bottom = if (wide) 8.dp else 0.dp),
                            horizontalArrangement = Arrangement.spacedBy(if (tv) 24.dp else 16.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            poster()
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                heading()
                                if (wide) {
                                    facts()
                                    overview()
                                }
                            }
                        }
                        Box(Modifier.align(Alignment.TopEnd)) { trailing() }
                    }
                    if (!wide) {
                        Column(Modifier.padding(pad), verticalArrangement = Arrangement.spacedBy(12.dp)) { facts() }
                    }
                    actions()
                    if (!wide) Box(Modifier.padding(pad)) { overview() }
                    Spacer(Modifier.height(4.dp))
                }
            }
            item(key = "seasons") {
                Column(Modifier.fillMaxWidth().padding(pad), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    seasonPicker(shown.number) { season = it }
                    if (ranges.isNotEmpty()) EpisodeRanges(ranges = ranges, selected = rangeAt, onSelect = { rangeAt = it })
                    Spacer(Modifier.height(2.dp))
                }
            }
            items(listed, key = { it.item.id }) { episode ->
                val chosen = if (next != null && episode.sameAs(next)) next.item else watch.pick(episode, progress)
                EpisodeRow(
                    episode = episode,
                    point = watch.pointOf(episode),
                    finished = watch.finishedEpisode(episode),
                    upNext = next != null && episode.sameAs(next),
                    onClick = { onPlay(chosen) },
                    onLongClick = onLongClick?.let { { it(chosen) } },
                    chosen = chosen,
                    onCopy = onPlay,
                    modifier = Modifier.padding(pad).widthIn(max = EPISODES_MAX),
                    lineModifier = if (next != null && episode.sameAs(next)) Modifier.focusRequester(upNextFocus) else Modifier,
                )
            }
            // The cast and "More like this" after the episodes: composed only once scrolled to.
            if (meta != null && extras != null) {
                if (extras.cast.isNotEmpty()) {
                    item(key = "meta-cast") { MetaCastRow(extras.cast, Modifier.padding(pad).padding(top = 16.dp)) }
                }
                if (extras.similar.isNotEmpty() && onOpenItem != null) {
                    item(key = "meta-more") { MoreLikeThisRow(meta, extras, onOpenItem, Modifier.padding(pad).padding(top = 16.dp)) }
                }
            }
        }
    }
}

/**
 * A long season's episodes in ranges of [EPISODE_RANGE], "1-100", "101-200", drawn like the
 * season tabs. Numbered by the episodes in each range, so a season with gaps says what it holds.
 */
@Composable
private fun EpisodeRanges(
    ranges: List<List<SeriesEpisode>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tv = !isTouch()
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ranges.forEachIndexed { index, range ->
            val interactions = remember { MutableInteractionSource() }
            val focused by interactions.collectIsFocusedAsState()
            val chosen = index == selected
            val shape = RoundedCornerShape(Corner.Small)
            Text(
                "${range.first().episode}-${range.last().episode}",
                style = if (tv) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge,
                fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
                color = when {
                    focused -> Tone.onFocusFill
                    chosen -> Tone.accent
                    else -> Tone.text
                },
                maxLines = 1,
                modifier = Modifier
                    .clip(shape)
                    .background(
                        when {
                            focused -> Tone.focusFill
                            chosen -> Tone.accent.copy(alpha = 0.18f)
                            else -> Tone.surfaceHigh
                        },
                    )
                    .border(if (chosen && !focused) 1.5.dp else 0.dp, if (chosen && !focused) Tone.accent else Color.Transparent, shape)
                    .focusRing(focused, shape)
                    .semantics {
                        role = Role.Tab
                        this.selected = chosen
                    }
                    .clickable(interactionSource = interactions, indication = null) { onSelect(index) }
                    .padding(horizontal = if (tv) 18.dp else 14.dp, vertical = if (tv) 10.dp else 8.dp),
            )
        }
    }
}

/** Episodes per range once a season is longer than this; see [EpisodeRanges]. */
private const val EPISODE_RANGE = 100

/**
 * A thin rounded bar of how much of a show or a course is watched, for the top of its list: the
 * tile's bar, drawn at rest on the page rather than over a picture.
 */
@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(CircleShape)
            .background(Tone.muted.copy(alpha = 0.25f)),
    ) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(Tone.accent))
    }
}

@Composable
internal fun PlayButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val tv = !isTouch()
    Row(
        modifier
            .height(if (tv) 48.dp else 40.dp)
            .clip(CircleShape)
            .background(Tone.accent)
            .border(if (focused) 2.dp else 0.dp, if (focused) Tone.text else Color.Transparent, CircleShape)
            .clickable(interactionSource = interactions, indication = if (tv) null else androidx.compose.foundation.LocalIndication.current, onClick = onClick)
            .padding(start = 14.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val ink = Tone.onAccent
        Icon(TmIcons.Play, contentDescription = null, tint = ink, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = ink, maxLines = 1)
    }
}

private fun PaddingValues.horizontalOnly(): PaddingValues =
    PaddingValues(
        start = calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
        end = calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
    )

private val SERIES_POSTER: Dp = 96.dp
private val SERIES_POSTER_WIDE: Dp = 120.dp
private val SERIES_POSTER_TV: Dp = 140.dp

/** As wide as this, a show's page puts its backdrop at the end and its words over the start. */
private val WIDE_PAGE: Dp = 720.dp

/** How much of a wide page's width the title, facts and overview take, from the start. */
private const val TEXT_SHARE = 0.6f

/** The episode cards stop growing here, so a wide window does not stretch a line of text across it. */
private val EPISODES_MAX: Dp = 960.dp

/** The season picker's place in a show page's list, after the hero. */
private const val SEASONS_ITEM = 1

/** The first episode card's place in that list, after the hero and the season picker. */
private const val FIRST_EPISODE_ITEM = 2

/** A season longer than this opens on its up-next episode on a phone or a desktop. */
private const val LONG_SEASON = 12
private val EPISODE_ART_TOUCH: Dp = 128.dp
private val EPISODE_ART_TV: Dp = 176.dp

/**
 * OK plays, holding OK is [onHold]: the remote's long press, written out of key events because
 * a D-pad hold does not reach `combinedClickable` on a television. A hold is a repeat of the key
 * while it is still down, which the system sends first at its own long-press interval (the signal
 * the app's `holdable` reads too); the press then counts on the way up, so a hold is never also a
 * play. A release with no press seen here (the OK that opened the window) does nothing.
 */
private fun Modifier.okHold(
    interactions: MutableInteractionSource,
    onClick: () -> Unit,
    onHold: () -> Unit,
): Modifier {
    // Not state: nothing draws from it. [0] 1 while a press is live; [1] 1 once this press has
    // been taken as a hold.
    val press = longArrayOf(0L, 0L)
    return this
        .semantics {
            role = Role.Button
            onClick { onClick(); true }
            onLongClick { onHold(); true }
        }
        .onPreviewKeyEvent { event ->
            if (event.key !in OK_KEYS) return@onPreviewKeyEvent false
            when (event.type) {
                KeyEventType.KeyDown -> {
                    if (press[0] == 0L) {
                        press[0] = 1L
                        press[1] = 0L
                    } else if (press[1] == 0L) {
                        press[1] = 1L
                        onHold()
                    }
                    true
                }
                KeyEventType.KeyUp -> {
                    val pressed = press[0] != 0L
                    val held = press[1] != 0L
                    press[0] = 0L
                    press[1] = 0L
                    if (pressed && !held) onClick()
                    true
                }
                else -> false
            }
        }
        .focusable(interactionSource = interactions)
}

private val OK_KEYS = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)


