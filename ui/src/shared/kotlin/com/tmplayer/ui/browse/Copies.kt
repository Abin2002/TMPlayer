package com.tmplayer.ui.browse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tmplayer.data.CopyHandoff
import com.tmplayer.data.MediaFacts
import com.tmplayer.data.MediaItem
import com.tmplayer.data.MediaMapper
import com.tmplayer.data.VideoCopies
import com.tmplayer.data.copyQuality
import com.tmplayer.i18n.Messages
import com.tmplayer.ui.components.isTouch
import com.tmplayer.ui.i18n.LocalStrings
import com.tmplayer.ui.icons.TmIcons
import com.tmplayer.ui.theme.Corner
import com.tmplayer.ui.theme.Tone
import com.tmplayer.ui.theme.focusRing

/**
 * One copy's name on a line of its own: its quality and codec, "1080p  ·  HEVC", or its size
 * where the name and Telegram say nothing about the picture.
 */
fun copyHeading(copy: MediaItem): String {
    val name = copy.fileName.ifBlank { copy.title }
    val facts = MediaFacts.read(name, copy.width, copy.height)
    val quality = VideoCopies.heightOf(copy)?.let { copy.copyQuality() }
    return listOfNotNull(quality ?: MediaMapper.formatSize(copy.sizeBytes).ifEmpty { null }, facts.videoCodec)
        .joinToString("  ·  ")
}

/**
 * What else sets a copy apart, for the line under its [copyHeading]: the size (when the heading
 * did not already give it), the length, when it was posted, the chat where the copies span more
 * than one, whether it is on this device and whether its chat allows saving.
 */
fun copyDetail(copy: MediaItem, s: Messages, chatTitle: String?, started: Boolean = false): String = listOfNotNull(
    copy.sizeBytes.takeIf { it > 0 && VideoCopies.heightOf(copy) != null }?.let { s.formatter.size(it) },
    copy.durationSec.takeIf { it > 0 }?.let { s.formatter.duration(it.toLong()) },
    copy.date.takeIf { it > 0 }?.let { s.detailCopyPosted(s.formatter.date(it * 1000L)) },
    chatTitle?.takeIf { it.isNotBlank() }?.let { s.detailCopyFrom(it) },
    when (copy.locality) {
        MediaItem.Locality.Downloaded -> s.gridBadgeDownloaded
        MediaItem.Locality.Cached -> s.detailCopyOnDevice
        MediaItem.Locality.Remote -> null
    },
    s.detailCopyStarted.takeIf { started },
    s.detailCopyNoSaving.takeIf { !copy.canBeSaved },
).joinToString("  ·  ")

/**
 * The chat each copy of [item] is in, by name, for a list that spans several: [chatTitle] for the
 * page's own chat, the name the page that found the others handed over ([CopyHandoff]) for the rest.
 */
fun copyChat(copy: MediaItem, item: MediaItem, chatTitle: String): String? =
    if (copy.chatId == item.chatId) chatTitle.ifBlank { null } else CopyHandoff.chatTitleOf(copy)

/**
 * A film posted more than once, on its detail page: one quiet line under Play naming the copy that
 * plays ("1080p  ·  1.4 GB  ·  from Film Club") with Change at its end, and under it, once Change
 * is pressed, every copy as a row of its own to choose from.
 *
 * The line is the default because most of the time the copy chosen for the viewer is the right
 * one (one already on the device, the one they started, the quality they usually watch: see
 * [VideoCopies.pick]), and a wall of chips asked a question nobody needed to answer. The rows say
 * what a chip could not: the length, the date, the chat, whether it is here already.
 *
 * Choosing a row hands the copy to [onPick], which remembers it for the video and turns the page to
 * it, so Play, the download line and the facts all speak of that copy.
 */
@Composable
internal fun Versions(
    item: MediaItem,
    onPick: ((MediaItem) -> Unit)?,
    chatTitle: String = "",
    started: (MediaItem) -> Boolean = { false },
) {
    if (item.versions.size <= 1 || onPick == null) return
    // The same file posted twice is one choice, not two identical rows.
    val files = remember(item.versions) { VideoCopies.distinctFiles(item.versions) }
    if (files.size <= 1) return
    val s = LocalStrings.current
    var open by rememberSaveable(item.id) { mutableStateOf(false) }
    val spans = remember(item.versions) { VideoCopies.spansChats(item.versions) }
    val summary = remember(item, chatTitle, s) {
        listOfNotNull(
            copyHeading(item),
            item.sizeBytes.takeIf { it > 0 && VideoCopies.heightOf(item) != null }?.let { s.formatter.size(it) },
            copyChat(item, item, chatTitle)?.let { s.detailCopyFrom(it) },
        ).joinToString("  ·  ")
    }
    // A phone lists the copies in its bottom sheet, over the page; elsewhere they open in place.
    val sheet = com.tmplayer.ui.components.LocalSheetHost.current?.takeIf { isTouch() }
    val list: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    s.detailCopiesTitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = Tone.muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
                files.forEach { (lead, same) ->
                    // The row stands for every post of its file: the one playing if it is among them.
                    val copy = same.firstOrNull { it.id == item.id } ?: lead
                    CopyRow(
                        copy = copy,
                        detail = listOfNotNull(
                            copyDetail(copy, s, if (spans) copyChat(copy, item, chatTitle) else null, same.any(started)),
                            postedAgain(same, s),
                        ).joinToString("  ·  "),
                        chosen = same.any { it.id == item.id },
                        onClick = {
                            open = false
                            if (copy.id != item.id) onPick(copy.asVersion(item))
                        },
                    )
                }
            }
    }
    Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CopySummary(
            summary = summary,
            count = s.detailVersions(files.size),
            action = if (open && sheet == null) s.detailCopyDone else s.detailCopyChange,
            onClick = { open = !open },
        )
        if (sheet == null) {
            AnimatedVisibility(open) { list() }
        }
    }
    if (sheet != null && open) {
        sheet.Sheet(onDismiss = { open = false }) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
            ) { list() }
        }
    }
}

/**
 * For a file posted more than once: "in 2 chats" where the posts are in different chats, else
 * "posted twice"; null for a file posted once.
 */
fun postedAgain(same: List<MediaItem>, s: Messages): String? {
    if (same.size <= 1) return null
    val chats = same.map { it.chatId }.distinct().size
    return if (chats > 1) s.detailCopyInChats(chats) else s.detailCopyPostedTimes(same.size)
}

/** "1080p  ·  1.4 GB  ·  from Film Club" and the count of copies, with Change at the end: one target. */
@Composable
private fun CopySummary(summary: String, count: String, action: String, onClick: () -> Unit) {
    val tv = !isTouch()
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val shape = RoundedCornerShape(Corner.Medium)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            // A phone draws only the outline: a filled block here read as one more card on a page
            // already made of them. The television keeps the fill its focus colour replaces.
            .background(if (focused) Tone.focusFill else if (tv) Tone.surfaceHigh else Color.Transparent)
            .then(if (tv) Modifier else Modifier.border(1.dp, Tone.outline, shape))
            .focusRing(focused, shape)
            .clickable(interactionSource = interactions, indication = if (tv) null else androidx.compose.foundation.LocalIndication.current, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = if (tv) 12.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val ink = if (focused) Tone.onFocusFill else Tone.text
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(summary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(count, style = MaterialTheme.typography.labelMedium, color = if (focused) ink.copy(alpha = 0.8f) else Tone.muted, maxLines = 1)
        }
        Text(action, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = if (focused) ink else Tone.accent, maxLines = 1)
    }
}

/**
 * One copy in the list: a round mark (filled on the copy that plays), its quality in full, and
 * what else sets it apart under that ([copyDetail]). The whole row is the target.
 */
@Composable
internal fun CopyRow(copy: MediaItem, detail: String, chosen: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val tv = !isTouch()
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val shape = RoundedCornerShape(Corner.Medium)
    val heading = remember(copy) { copyHeading(copy) }
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                when {
                    focused -> Tone.focusFill
                    chosen -> Tone.accent.copy(alpha = 0.12f)
                    else -> Color.Transparent
                },
            )
            .focusRing(focused, shape)
            .border(1.dp, if (chosen && !focused) Tone.accent.copy(alpha = 0.5f) else if (focused) Color.Transparent else Tone.outline, shape)
            .semantics {
                selected = chosen
                contentDescription = s.seriesCopy(heading)
            }
            .clickable(interactionSource = interactions, indication = if (tv) null else androidx.compose.foundation.LocalIndication.current, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = if (tv) 12.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val ink = if (focused) Tone.onFocusFill else Tone.text
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(2.dp, if (chosen) (if (focused) ink else Tone.accent) else ink.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (chosen) Box(Modifier.size(10.dp).clip(CircleShape).background(if (focused) ink else Tone.accent))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(heading, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.labelMedium, color = if (focused) ink.copy(alpha = 0.8f) else Tone.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (!copy.canBeSaved) {
            Icon(TmIcons.Lock, contentDescription = null, tint = if (focused) ink else Tone.muted, modifier = Modifier.size(18.dp))
        }
    }
}
