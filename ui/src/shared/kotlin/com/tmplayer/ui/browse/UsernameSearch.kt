package com.tmplayer.ui.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.tmplayer.data.ChatRepository
import com.tmplayer.data.ChatSummary
import com.tmplayer.data.Td
import com.tmplayer.data.Usernames
import kotlinx.coroutines.delay

/**
 * The chat whose public username [query] spells (`@name`, `name` or a t.me address), looked up
 * on Telegram's servers once the typing has paused, so Search finds a person, group or channel
 * by username even when it is not in the chat list or its title says something else. Null while
 * the query cannot be a username, while it is being asked, and when nobody has that name.
 */
@Composable
fun rememberUsernameChat(query: String): ChatSummary? {
    val found by produceState<ChatSummary?>(initialValue = null, query) {
        value = null
        val name = Usernames.clean(query) ?: return@produceState
        delay(USERNAME_PAUSE_MS)
        val session = Td.awaitConnectedSessionOrNull() ?: return@produceState
        value = runCatching { ChatRepository(session.client).findByUsername(name) }.getOrNull()
    }
    return found
}

/** [local] title matches with the username's chat in front, once, when it is not already among them. */
fun withUsernameChat(local: List<ChatSummary>, found: ChatSummary?): List<ChatSummary> =
    if (found == null || local.any { it.id == found.id }) local else listOf(found) + local

/** How long the typing rests before a username is asked for, so each letter is not a server call. */
private const val USERNAME_PAUSE_MS = 450L
