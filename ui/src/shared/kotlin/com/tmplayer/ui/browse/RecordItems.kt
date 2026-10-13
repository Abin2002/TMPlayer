package com.tmplayer.ui.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.tmplayer.data.MediaItem
import com.tmplayer.data.SettingsStore
import kotlinx.coroutines.CancellationException

/**
 * The videos behind History's saved records (Continue watching, Watch later, Watched) as Telegram
 * describes them, for their pictures.
 *
 * A record keeps no thumbnail, so a card drawn from it alone has only its generated cover. Home
 * asks Telegram for the message ([HomeViewModel.requestArt]) and draws its real thumbnail, and
 * History does the same through here: one lookup per video, kept for the life of the process in
 * a small cache so a tab switched back to draws its pictures on the first frame. What Home has
 * already looked up goes into the same cache ([keep]), so History does not ask for it again.
 */
object RecordItems {

    /** Enough for a few screens of History; the oldest goes first past it. */
    private const val KEEP = 200

    private val cache = object : LinkedHashMap<String, MediaItem>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MediaItem>?): Boolean = size > KEEP
    }

    internal fun peek(key: String): MediaItem? = synchronized(cache) { cache[key] }

    /** A video Telegram has described elsewhere (Home's tiles), for History to draw without asking. */
    fun keep(item: MediaItem) {
        synchronized(cache) { cache[SettingsStore.progressKey(item.chatId, item.messageId)] = item }
    }

    internal suspend fun lookup(chatId: Long, messageId: Long, source: HomeSource = TdHomeSource): MediaItem? {
        val key = SettingsStore.progressKey(chatId, messageId)
        peek(key)?.let { return it }
        val item = try {
            source.mediaItem(chatId, messageId)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            null
        } ?: return null
        synchronized(cache) { cache[key] = item }
        return item
    }
}

/**
 * The video a saved record stands for, as Telegram describes it once it answers, with its
 * thumbnail; [fallback] (the record's own [MediaItem], which has none) until then, and for good
 * when Telegram has no answer. Draw it as Home draws its tiles: the online picture where allowed,
 * the thumbnail, and the generated cover only when there is no thumbnail.
 */
@Composable
fun rememberRecordItem(chatId: Long, messageId: Long, fallback: MediaItem): MediaItem {
    val key = SettingsStore.progressKey(chatId, messageId)
    val item by produceState(RecordItems.peek(key) ?: fallback, key) {
        if (RecordItems.peek(key) == null) RecordItems.lookup(chatId, messageId)?.let { value = it }
    }
    return item
}
