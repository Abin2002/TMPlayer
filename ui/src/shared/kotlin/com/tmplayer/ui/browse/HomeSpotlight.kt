package com.tmplayer.ui.browse

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.tmplayer.data.LessonSteps
import com.tmplayer.data.MediaItem
import com.tmplayer.data.ResumeRecord
import com.tmplayer.data.SeriesShelf
import com.tmplayer.data.SettingsStore
import com.tmplayer.data.Thumbnails
import com.tmplayer.i18n.Messages
import com.tmplayer.online.MetaInfo
import com.tmplayer.online.MetaQuery
import com.tmplayer.online.MetaResult
import com.tmplayer.online.OnlineMetadata
import com.tmplayer.ui.components.ArtBackdrop
import com.tmplayer.ui.components.BackdropSource
import com.tmplayer.ui.components.GeneratedCover
import com.tmplayer.ui.components.MediaArt
import com.tmplayer.ui.components.TileBadge
import com.tmplayer.ui.components.TileBadgeInset
import com.tmplayer.ui.components.isTouch
import com.tmplayer.ui.components.rememberCover
import com.tmplayer.ui.components.rememberLowMemory
import com.tmplayer.ui.i18n.LocalStrings
import com.tmplayer.ui.icons.TmIcons
import com.tmplayer.ui.online.OnlineArt
import com.tmplayer.ui.theme.Focus
import com.tmplayer.ui.theme.LocalDarkTheme
import com.tmplayer.ui.theme.Motion
import com.tmplayer.ui.theme.Scrims
import com.tmplayer.ui.theme.Tone
import com.tmplayer.ui.theme.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The video Home leads with, and what it has to show for it.
 *
 * [wideUrl] is the online backdrop when that is the picture that earned the spot, else null and
 * the Telegram thumbnail is. [info] is what the providers know about it, for its proper name and
 * its overview, or null.
 */
@Immutable
class Spotlight(
    val record: ResumeRecord,
    val item: MediaItem,
    val info: MetaInfo?,
    val wideUrl: String?,
)

/** Where working out the spotlight stands. */
sealed interface SpotlightState {
    /** Still finding out whether any candidate has a picture: a television holds its first focus. */
    data object Pending : SpotlightState

    /** Nothing worth leading with: Home starts with its rows. */
    data object None : SpotlightState

    data class Ready(val spotlight: Spotlight) : SpotlightState
}

/**
 * Home's spotlight, from [HomeRows.spotlight]'s candidates: the first whose picture is worth
 * showing large, which is an online backdrop at least [SHARP_ONLINE] pixels across (with posters
 * and overviews on), or a Telegram thumbnail at least [SHARP_THUMBNAIL] across. An inline preview
 * alone never is, and neither is a generated cover: Home would rather open on its rows than on a
 * smear or a colour field passed off as a feature.
 *
 * [art] is the Continue watching videos as Telegram describes them, which is where a record's
 * thumbnail comes from; each candidate's is asked for through [onArtWanted]. Until they answer,
 * or [ART_WAIT_MS] passes, the answer is [SpotlightState.Pending] rather than None, so a television
 * does not settle its first focus on a row a moment before the hero arrives above it.
 *
 * On a device short of memory only the thumbnail counts, and it is drawn as its soft field: no
 * 1280 pixel decode on a 1 GB stick.
 */
@Composable
fun rememberSpotlight(
    candidates: List<ResumeRecord>,
    art: Map<String, MediaItem>,
    onArtWanted: (ResumeRecord) -> Unit,
): SpotlightState {
    LaunchedEffect(candidates) { candidates.forEach(onArtWanted) }
    val keys = remember(candidates) { candidates.map { SettingsStore.progressKey(it.chatId, it.messageId) } }
    val items = remember(candidates, art) {
        candidates.mapIndexed { index, record -> art[keys[index]] ?: record.toMediaItem() }
    }
    var waited by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(ART_WAIT_MS)
        waited = true
    }
    val answered = waited || keys.all { it in art }
    val online = OnlineMetadata.current
    val metaOn = online?.settings?.collectAsState()?.value?.enabled == true
    val lowMemory = rememberLowMemory()
    // Keyed on what decides the answer, not on the whole art map: another tile's picture
    // arriving must not start the search over.
    val inputs = items.map { it.id to it.thumbnailFileId }
    val state by produceState<SpotlightState>(SpotlightState.Pending, inputs, answered, metaOn, lowMemory) {
        if (candidates.isEmpty()) {
            value = SpotlightState.None
            return@produceState
        }
        candidates.forEachIndexed { index, record ->
            val item = items[index]
            val found = findArt(item, if (metaOn) online else null, lowMemory)
            if (found != null) {
                value = SpotlightState.Ready(Spotlight(record, item, found.first, found.second))
                return@produceState
            }
        }
        value = if (answered) SpotlightState.None else SpotlightState.Pending
    }
    return state
}

/**
 * Whether [item] has art worth a hero, and which: the providers' answer and the backdrop's
 * address when that is it, the answer and null when the thumbnail is. Null for neither.
 */
private suspend fun findArt(item: MediaItem, online: OnlineMetadata?, lowMemory: Boolean): Pair<MetaInfo?, String?>? {
    val info = online?.let { provider ->
        val query = MetaQuery.of(item.nameForParsing, item.caption)?.showOnly() ?: return@let null
        withContext(Dispatchers.IO) {
            (runCatching { provider.lookup(query) }.getOrNull() as? MetaResult.Found)?.info
        }
    }
    val url = info?.backdropUrl
    if (online != null && url != null && !lowMemory) {
        val wide = Thumbnails.peekOnline(url, ONLINE_WIDTH)
            ?: Thumbnails.online(url, ONLINE_WIDTH) { online.image(url) }
        if (wide != null && wide.width >= SHARP_ONLINE) return info to url
    }
    if (item.thumbnailFileId <= 0) return null
    val thumbnail = Thumbnails.peekFull(item.thumbnailFileId) ?: Thumbnails.full(item.thumbnailFileId)
    return if (thumbnail != null && thumbnail.width >= SHARP_THUMBNAIL) info to null else null
}

/**
 * Home's hero: the video the viewer was last part way through, when it is a film or a show and
 * has a picture to show for it (see [rememberSpotlight]).
 *
 * Two arrangements. [wide] (a television, the desktop, a phone on its side) puts the picture on
 * the end [SPOTLIGHT_BACKDROP_SHARE] of the width under a start fade and a foot fade, with the words in a
 * column on the start side. Otherwise (a phone upright) the picture fills the width and the
 * words sit centred at its foot, under the long fade. Either way the words are TMPlayer's: the
 * chat it came from in the accent, the name (the providers' when they know it), and a line of
 * facts with the episode, the time left, the quality and the size. Then one pill, Resume, and a
 * round Details button. No rating, no "featured": it is there because the viewer started it.
 *
 * [resumeFocus] is the pill's, for a television to open Home on it. One picture at a time, and on
 * a device short of memory only the thumbnail's soft field.
 *
 * [under] is how far the picture reaches up behind a page header drawn over the top of it (the
 * page's title and buttons, or a phone's app bar): the hero is that much taller, the picture runs
 * to the top edge, and the words stay where they were, under the header. The header stands on
 * [Scrims.headerTop] meanwhile (see [overHero]). Zero leaves the hero under its header as before.
 */
@Composable
fun HomeSpotlight(
    spotlight: Spotlight,
    chatTitle: String,
    wide: Boolean,
    height: Dp,
    start: Dp,
    end: Dp,
    onResume: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
    resumeFocus: FocusRequester? = null,
    under: Dp = 0.dp,
) {
    val s = LocalStrings.current
    val item = spotlight.item
    val record = spotlight.record
    val lowMemory = rememberLowMemory()
    val cover = rememberCover(item, chatTitle)
    val source = remember(item, spotlight.wideUrl, cover) {
        BackdropSource(item.miniThumbnail, item.thumbnailFileId, cover, spotlight.wideUrl)
    }
    val words = remember(item, spotlight.info, record, s) { SpotlightWords.of(item, spotlight.info, record, s) }
    Box(modifier.fillMaxWidth().heightIn(min = height + under)) {
        Box(Modifier.matchParentSize()) {
            if (wide) {
                Box(Modifier.align(Alignment.TopEnd).fillMaxWidth(SPOTLIGHT_BACKDROP_SHARE).fillMaxHeight()) {
                    ArtBackdrop(source, Modifier.fillMaxSize(), fieldOnly = lowMemory)
                    Box(Modifier.fillMaxSize().background(Scrims.startLong()))
                    // A short fade at the top too, so the picture does not start on a hard line under
                    // the header; none where the picture runs on up behind the header instead.
                    if (under == 0.dp) {
                        Box(
                            Modifier.fillMaxWidth().height(TOP_FADE)
                                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Tone.scrim, Tone.scrim.copy(alpha = 0f)))),
                        )
                    }
                    Box(Modifier.fillMaxSize().background(Scrims.bottomLong(WIDE_FOOT_FADE)))
                }
            } else {
                ArtBackdrop(source, Modifier.fillMaxSize(), fieldOnly = lowMemory)
                Box(Modifier.fillMaxSize().background(Scrims.bottomLong(TALL_FOOT_FADE)))
            }
        }
        val align = if (wide) Alignment.Start else Alignment.CenterHorizontally
        val textAlign = if (wide) TextAlign.Start else TextAlign.Center
        Column(
            Modifier
                .align(if (wide) Alignment.BottomStart else Alignment.BottomCenter)
                .then(if (wide) Modifier.fillMaxWidth(TEXT_SHARE).widthIn(max = TEXT_MAX) else Modifier.fillMaxWidth())
                .padding(start = start, end = if (wide) 0.dp else end, top = 24.dp, bottom = if (wide) 20.dp else 8.dp),
            horizontalAlignment = align,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (chatTitle.isNotBlank()) {
                Text(
                    chatTitle,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Tone.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = textAlign,
                )
            }
            Text(
                words.title,
                style = MaterialTheme.typography.displaySmall,
                color = Tone.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = textAlign,
            )
            if (words.facts.isNotBlank()) {
                Text(
                    words.facts,
                    style = MaterialTheme.typography.labelLarge,
                    color = Tone.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = textAlign,
                )
            }
            val overview = spotlight.info?.overview.orEmpty()
            if (wide && overview.isNotBlank()) {
                Text(
                    overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Tone.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                Modifier.padding(top = 10.dp).then(if (wide) Modifier else Modifier.fillMaxWidth()),
                horizontalArrangement = Arrangement.spacedBy(12.dp, if (wide) Alignment.Start else Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeroPill(
                    label = if (record.positionMs > 0) s.commonResume else s.commonPlay,
                    onClick = onResume,
                    stretch = !wide,
                    focus = resumeFocus,
                    modifier = if (wide) Modifier else Modifier.weight(1f, fill = false),
                )
                RoundAction(s.detailOpenDetails, onDetails)
            }
        }
    }
}

/** The key Home's lists give the spotlight's item, which [rememberHeroUnderHeader] looks for. */
const val SPOTLIGHT_KEY = "spotlight"

/**
 * Whether Home's hero, the first item of [list] under [SPOTLIGHT_KEY], still reaches down past a
 * header [header] tall drawn over the top of the list. While it does, the header stands on the
 * picture ([overHero] true); once the hero has scrolled up under it, or there is none ([hero]
 * false), the header goes back to its own look.
 *
 * A hero that has just arrived over a list still at its top counts before the list has laid it
 * out, so the header starts on the picture rather than fading over to it.
 */
@Composable
fun rememberHeroUnderHeader(list: LazyListState, header: Dp, hero: Boolean): State<Boolean> {
    val reach = with(LocalDensity.current) { header.roundToPx() }
    return remember(list, reach, hero) {
        derivedStateOf {
            if (!hero) return@derivedStateOf false
            val first = list.layoutInfo.visibleItemsInfo.firstOrNull() ?: return@derivedStateOf true
            if (first.key == SPOTLIGHT_KEY) first.offset + first.size > reach
            else list.layoutInfo.visibleItemsInfo.none { it.key == SPOTLIGHT_KEY } && list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0
        }
    }
}

/**
 * The ground of a page header drawn over Home's hero ([HomeSpotlight]'s `under`). Over the
 * picture it is [Scrims.headerTop], reaching [Scrims.HEADER_REACH] times the header's height
 * down, and the header's words and icons are to be [Tone.onArt] in either theme; otherwise it is
 * [solid], the page's own background by default, so the rows scrolling up under the header do not
 * show through it.
 * The two cross fade.
 */
@Composable
fun Modifier.overHero(overArt: Boolean, solid: Color = Tone.background): Modifier {
    val art by animateFloatAsState(if (overArt) 1f else 0f, Motion.standard(Motion.Fast), label = "headerOverHero")
    return drawBehind {
        if (art < 1f) drawRect(solid, alpha = 1f - art)
        if (art > 0f) drawRect(Scrims.headerTop, size = Size(size.width, size.height * Scrims.HEADER_REACH), alpha = art)
    }
}

/** The words a spotlight writes, worked out once per video. */
internal class SpotlightWords(val title: String, val facts: String) {
    companion object {
        fun of(item: MediaItem, info: MetaInfo?, record: ResumeRecord, s: Messages): SpotlightWords {
            val parsed = SeriesShelf.episodeOf(item)
            val episode = parsed.episode?.let { number ->
                parsed.season?.let { s.seriesEpisodeShort(it, number) } ?: s.seriesEpisode(number)
            }
            val title = info?.title?.takeIf { it.isNotBlank() }
                ?: if (episode != null) parsed.title.ifBlank { SeriesShelf.tileTitle(item) } else SeriesShelf.posterTitle(item)
            val left = record.remainingMs.takeIf { it > 0 }?.let { s.browseTimeLeft(s.formatter.duration(it / 1000)) }
            val facts = listOfNotNull(
                episode,
                parsed.year?.takeIf { episode == null }?.toString(),
                left,
                item.qualityTags.firstOrNull(),
                item.sizeBytes.takeIf { it > 0 }?.let { s.formatter.size(it) },
            ).joinToString("  ·  ")
            return SpotlightWords(title, facts)
        }
    }
}

/**
 * The spotlight's one bright action: a pill in [Tone.hero], near white on dark and near black on
 * light, [HERO_PILL_HEIGHT] tall and at most [HERO_PILL_MAX] wide. Under the remote or the
 * keyboard it changes three ways at once: the fill goes to [Tone.focusFill], a [Focus.Ring]
 * goes round it, and it grows by [Motion.FocusScale] (unless motion is off).
 */
@Composable
fun HeroPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    stretch: Boolean = false,
    focus: FocusRequester? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = TmIcons.Play,
) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val hovered by interactions.collectIsHoveredAsState()
    val still = rememberReducedMotion()
    val scale by animateFloatAsState(
        if (focused && !still) Motion.FocusScale else 1f,
        Motion.standard(Motion.Fast),
        label = "heroPillScale",
    )
    val fill by animateColorAsState(
        if (focused) Tone.focusFill else if (hovered) Tone.hero.copy(alpha = 0.88f) else Tone.hero,
        Motion.standard(Motion.Fast),
        label = "heroPillFill",
    )
    val ink = if (focused) Tone.onFocusFill else Tone.onHero
    Row(
        modifier
            .then(if (stretch) Modifier.widthIn(max = HERO_PILL_MAX).fillMaxWidth() else Modifier.widthIn(min = 160.dp, max = HERO_PILL_MAX))
            .heightIn(min = HERO_PILL_HEIGHT)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .clip(CircleShape)
            .background(fill)
            .border(Focus.Ring, if (focused) Tone.accent else Color.Transparent, CircleShape)
            .clickable(interactionSource = interactions, indication = if (isTouch()) androidx.compose.foundation.LocalIndication.current else null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(26.dp).padding(end = 2.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(start = if (icon != null) 6.dp else 0.dp),
        )
    }
}

/** The quiet round button beside the pill: an outline at rest, the focus fill and ring under focus. */
@Composable
private fun RoundAction(label: String, onClick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val still = rememberReducedMotion()
    val scale by animateFloatAsState(
        if (focused && !still) Motion.FocusScale else 1f,
        Motion.standard(Motion.Fast),
        label = "roundActionScale",
    )
    Box(
        Modifier
            .size(HERO_PILL_HEIGHT)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(if (focused) Tone.focusFill else Tone.surfaceHigh.copy(alpha = 0.72f))
            .border(if (focused) Focus.Ring else 1.dp, if (focused) Tone.accent else Tone.outline, CircleShape)
            .clickable(interactionSource = interactions, indication = if (isTouch()) androidx.compose.foundation.LocalIndication.current else null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            TmIcons.Info,
            contentDescription = label,
            tint = if (focused) Tone.onFocusFill else Tone.text,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * A Continue watching card's art: the picture (or the generated cover), the tile's foot fade,
 * which episode or part in small type and the name under it, written on the art at the bottom
 * start; how long is left in a chip at the top end; and a pill of progress across the foot.
 */
@Composable
fun ContinueCardArt(record: ResumeRecord, item: MediaItem, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val cover = rememberCover(item)
    val words = remember(item) { ContinueWords.of(item, s) }
    Box(modifier) {
        OnlineArt(item, Modifier.fillMaxSize()) {
            MediaArt(item.miniThumbnail, item.thumbnailFileId, Modifier.fillMaxSize()) {
                GeneratedCover(cover, Modifier.fillMaxSize(), showTitle = false)
            }
        }
        Box(Modifier.fillMaxSize().background(Scrims.tileFoot))
        if (record.remainingMs > 0) {
            TileBadge(
                s.browseTimeLeft(s.formatter.duration(record.remainingMs / 1000)),
                Modifier.align(Alignment.TopEnd).padding(TileBadgeInset),
            )
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
        ) {
            words.label?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Tone.onArtMuted,
                    maxLines = 1,
                )
            }
            Text(
                words.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Tone.onArt,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            PillProgress(record.fraction, Modifier.padding(top = 8.dp))
        }
    }
}

/** What a continue card writes on its art: "S1 E3", "Part 4" or nothing, then the name. */
internal class ContinueWords(val label: String?, val title: String) {
    companion object {
        fun of(item: MediaItem, s: Messages): ContinueWords {
            val parsed = SeriesShelf.episodeOf(item)
            val episode = parsed.episode?.let { number ->
                parsed.season?.let { s.seriesEpisodeShort(it, number) } ?: s.seriesEpisode(number)
            }
            if (episode != null) {
                val name = parsed.title.ifBlank { SeriesShelf.tileTitle(item) }
                // A file named by its episode alone ("Episode 1") is not given the label twice.
                return if (name.isBlank() || episode.startsWith(name, ignoreCase = true)) ContinueWords(null, episode) else ContinueWords(episode, name)
            }
            val lesson = LessonSteps.lessonOf(item)
            if (lesson != null) return ContinueWords(lesson.code, readable(lesson.course).ifBlank { SeriesShelf.tileTitle(item) })
            return ContinueWords(null, if (SeriesShelf.isFilm(item)) SeriesShelf.posterTitle(item) else SeriesShelf.tileTitle(item))
        }
    }
}

/**
 * How far through: a [PILL_PROGRESS] high pill in the accent on a [Tone.track], rounded at both
 * ends. Grows from the start edge, so from the right in a right to left layout.
 */
@Composable
fun PillProgress(fraction: Float, modifier: Modifier = Modifier) {
    val track = Tone.track
    val fill = if (LocalDarkTheme.current) Tone.accent else MaterialTheme.colorScheme.inversePrimary
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val done = fraction.coerceIn(0f, 1f)
    Canvas(modifier.fillMaxWidth().height(PILL_PROGRESS)) {
        val radius = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(track, cornerRadius = radius)
        val width = (size.width * done).coerceAtLeast(if (done > 0f) size.height else 0f)
        if (width > 0f) {
            drawRoundRect(
                fill,
                topLeft = Offset(if (rtl) size.width - width else 0f, 0f),
                size = Size(width, size.height),
                cornerRadius = radius,
            )
        }
    }
}

/** The pill of progress on a continue card: 3 dp, as Nuvio measures it. */
private val PILL_PROGRESS = 3.dp

/** The hero's action: as tall as a thumb wants and a remote can find. */
val HERO_PILL_HEIGHT = 52.dp

/** On a tablet or a phone on its side, a full-width pill would be a bar; this is its widest. */
private val HERO_PILL_MAX = 420.dp

/** How much of a wide hero's width the picture takes, from the end edge. */
private const val SPOTLIGHT_BACKDROP_SHARE = 0.72f

/** How much of a wide hero's width the words may take, from the start edge. */
private const val TEXT_SHARE = 0.45f

/** The words never run wider than this, however wide the window: past it they cross the picture. */
private val TEXT_MAX = 600.dp

/** The fade over the top edge of a wide hero's picture. */
private val TOP_FADE = 48.dp

/**
 * A course's name as a file name writes it ("small-shelf"), read as words ("Small shelf"):
 * separators become spaces, and a name in lower case alone gets its first letter raised.
 */
internal fun readable(name: String): String {
    val words = name.replace(Regex("[._-]+"), " ").trim().replace(Regex("\\s+"), " ")
    return if (words == words.lowercase()) words.replaceFirstChar { it.titlecase() } else words
}

/** The foot fade of a tall hero, long enough to reach behind its title, facts and pill. */
private val TALL_FOOT_FADE = 260.dp

/** The foot fade of a wide hero, into the rows under it. */
private val WIDE_FOOT_FADE = 140.dp

/** An online backdrop is decoded at this width for the hero, as [ArtBackdrop] decodes it. */
private const val ONLINE_WIDTH = 1280

/** Narrower than this, an online picture is not worth a hero. */
private const val SHARP_ONLINE = 480

/**
 * Narrower than this, a Telegram thumbnail is not worth a hero. Telegram's video thumbnails are
 * 320 pixels across at best; a smaller one (90 pixels, say) is a stamp, not a picture.
 */
private const val SHARP_THUMBNAIL = 280

/** How long a hero waits for its candidates' thumbnails to be looked up before giving up on them. */
private const val ART_WAIT_MS = 1_500L

/** The height a spotlight takes on a screen [screenHeight] tall: see [HomeSpotlight]. */
fun spotlightHeight(screenHeight: Dp, wide: Boolean, touch: Boolean): Dp = when {
    !wide -> (screenHeight * 0.56f).coerceIn(320.dp, 520.dp)
    touch -> (screenHeight * 0.7f).coerceIn(220.dp, 400.dp)
    else -> (screenHeight * 0.5f).coerceIn(240.dp, 440.dp)
}
