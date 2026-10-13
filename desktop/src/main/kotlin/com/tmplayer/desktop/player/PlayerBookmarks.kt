package com.tmplayer.desktop.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tmplayer.data.Bookmark
import com.tmplayer.data.BookmarkStore
import com.tmplayer.ui.components.TmAlertDialog
import com.tmplayer.ui.i18n.LocalStrings

/**
 * What the note dialog is for: a new bookmark at [positionMs], or a new note for [editing], which
 * was made at that same position.
 */
internal data class NotePrompt(val positionMs: Long, val editing: Bookmark? = null)

/**
 * The note for a bookmark, asked in a dialog with the cursor already in the field: B, a few words
 * and Enter saves it, B and Enter alone saves it without one. Cancel (or Esc) makes no bookmark.
 */
@Composable
internal fun BookmarkNoteDialog(prompt: NotePrompt, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    var note by remember(prompt) { mutableStateOf(prompt.editing?.note.orEmpty()) }
    val field = remember { FocusRequester() }
    TmAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.playerBookmarkNoteTitle(SeekMath.clock(prompt.positionMs))) },
        text = {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(BookmarkStore.MAX_NOTE) },
                label = { Text(s.playerBookmarkNoteHint) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave(note) }),
                modifier = Modifier.fillMaxWidth().focusRequester(field),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(note) }) {
                Text(if (prompt.editing != null) s.playerBookmarkSaveNote else s.playerBookmarkSave)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.commonCancel) } },
    )
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
}

/**
 * One bookmark in the player menu's list: its time and note, a click away from playing there,
 * with its own buttons to change the note and to delete it.
 */
@Composable
internal fun BookmarkLine(mark: Bookmark, onJump: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val s = LocalStrings.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).heightIn(min = 48.dp).clickable(onClick = onJump).padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Lines up with the entries' text, past the space their tick takes.
            Spacer(Modifier.width(30.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(SeekMath.clock(mark.positionMs), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (mark.note.isNotBlank()) {
                    Text(mark.note, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        IconButton(onClick = onEdit) {
            Icon(PlayerIcons.Edit, contentDescription = if (mark.note.isBlank()) s.playerBookmarkAddNote else s.playerBookmarkEditNote, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDelete) {
            Icon(PlayerIcons.Close, contentDescription = s.playerBookmarkDelete, modifier = Modifier.size(18.dp))
        }
    }
}
