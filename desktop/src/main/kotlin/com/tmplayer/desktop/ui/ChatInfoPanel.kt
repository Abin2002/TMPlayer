package com.tmplayer.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tmplayer.data.ChatInfo
import com.tmplayer.data.ChatKind
import com.tmplayer.data.ChatRepository
import com.tmplayer.data.ChatSummary
import com.tmplayer.data.Td
import com.tmplayer.desktop.os.OpenExternal
import com.tmplayer.i18n.L
import com.tmplayer.ui.components.rememberToast
import com.tmplayer.ui.i18n.LocalStrings
import com.tmplayer.ui.theme.Tone
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import kotlinx.coroutines.launch

/**
 * A chat's info on the desktop, opened from the Info button beside its name: the large picture,
 * the name, the kind and member count, the public @username (a click copies it), a person's bio or
 * a group's or channel's description (selectable), and for a public chat its t.me link with Copy
 * link and Open in Telegram. The same facts as the phone's sheet (user, 2026-10-18).
 */
@Composable
internal fun ChatInfoPanel(chat: ChatSummary, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    val toast = rememberToast()
    val load = com.tmplayer.ui.browse.rememberChatInfo(chat.id)
    val info = load.info
    fun copy(text: String, said: String) {
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
        toast(said)
    }
    DesktopSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val shown = info
            val name = shown?.summary?.title ?: chat.title
            ChatAvatar(chat.miniThumbnail, shown?.bigPhotoFileId?.takeIf { it != 0 } ?: chat.photoFileId, name, 104.dp)
            Text(name, style = MaterialTheme.typography.headlineSmall, color = Tone.text, textAlign = TextAlign.Center)
            shown?.let { facts(it) }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Tone.muted) }
            // A public group or channel not joined yet (CP79): this panel is the preview, Join the confirm.
            if (shown != null && shown.canJoin) {
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                var joining by androidx.compose.runtime.remember(shown.summary.id) { androidx.compose.runtime.mutableStateOf(false) }
                JoinButton(
                    label = when {
                        shown.joinByRequest -> s.linkRequestJoin
                        shown.summary.kind == ChatKind.Channel -> s.linkJoinChannel
                        else -> s.linkJoinGroup
                    },
                    joining = joining,
                    onJoin = {
                        joining = true
                        scope.launch {
                            val outcome = runCatching {
                                val session = Td.awaitConnectedSessionOrNull()
                                    ?: return@runCatching com.tmplayer.data.JoinOutcome.Refused(com.tmplayer.data.Failures.OFFLINE)
                                com.tmplayer.data.TelegramLinkActions(session.client).joinPublic(shown.summary.id)
                            }.getOrElse { com.tmplayer.data.JoinOutcome.Refused(com.tmplayer.data.Failures.humanise(it)) }
                            joining = false
                            toast(
                                when (outcome) {
                                    is com.tmplayer.data.JoinOutcome.Joined -> L.linkJoined(shown.summary.title)
                                    com.tmplayer.data.JoinOutcome.RequestSent -> L.linkRequestSent
                                    is com.tmplayer.data.JoinOutcome.Refused -> outcome.reason
                                },
                            )
                            if (outcome is com.tmplayer.data.JoinOutcome.Joined) load.retry()
                        }
                    },
                )
            }
            // Could not be loaded: said, with Try again, so a failure does not pass for a wait.
            if (load.failed) {
                Text(s.chatsInfoFailed, style = MaterialTheme.typography.bodyMedium, color = Tone.muted, textAlign = TextAlign.Center)
                OutlinedButton(onClick = load.retry) { Text(s.commonTryAgain) }
            }
            shown?.username?.let { username ->
                Text(
                    "@$username",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(onClickLabel = s.chatsInfoCopyUsername) { copy("@$username", L.chatsInfoUsernameCopied) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            shown?.about?.let { about ->
                SelectionContainer {
                    Text(about, style = MaterialTheme.typography.bodyLarge, color = Tone.text, textAlign = TextAlign.Center)
                }
            }
            shown?.link?.let { link ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { copy(link, L.commonLinkCopied) }) { Text(s.commonCopyLink) }
                    TextButton(onClick = { OpenExternal.browse(link) }) { Text(link.removePrefix("https://")) }
                }
            }
            TextButton(onClick = onDismiss) { Text(s.commonClose) }
        }
    }
}

/** "Channel · 12,400 subscribers", "Group · 31 members", or the kind alone. */
private fun facts(info: ChatInfo): String? {
    val kind = info.summary.kind
    val count = info.memberCount?.let { if (kind == ChatKind.Channel) L.chatsInfoSubscribers(it) else L.chatsInfoMembers(it) }
    return listOfNotNull(kind.label.takeIf { kind != ChatKind.Saved }, count).joinToString("  ·  ").ifBlank { null }
}
