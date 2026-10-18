package com.tmplayer.ui.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tmplayer.data.ChatInfo
import com.tmplayer.data.ChatRepository
import com.tmplayer.data.Td
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A chat's info as the sheet and the panel ask for it: [info] once it is in, [failed] when Telegram
 * could not be reached or did not answer within [CHAT_INFO_TIMEOUT_MS], and [retry] to ask again.
 * Without the failed state a sheet that could not load looked the same as one still loading.
 */
class ChatInfoLoad(val info: ChatInfo?, val failed: Boolean, val retry: () -> Unit)

@Composable
fun rememberChatInfo(chatId: Long): ChatInfoLoad {
    var attempt by remember(chatId) { mutableIntStateOf(0) }
    val state by produceState<Pair<ChatInfo?, Boolean>>(initialValue = null to false, chatId, attempt) {
        value = null to false
        val answer = try {
            withTimeoutOrNull(CHAT_INFO_TIMEOUT_MS) {
                val session = Td.awaitConnectedSessionOrNull() ?: return@withTimeoutOrNull null
                ChatRepository(session.client).chatInfo(chatId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        value = answer to (answer == null)
    }
    return ChatInfoLoad(state.first, state.second) { attempt++ }
}

/** Twice what a chat's info takes on a slow line, and well past a TV's patience otherwise. */
const val CHAT_INFO_TIMEOUT_MS = 20_000L
