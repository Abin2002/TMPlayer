package com.tmplayer.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * A video the viewer put aside to watch later, without starring the chat it came from.
 *
 * Carries what a tile needs to be drawn and played without that chat being loaded, as a
 * [WatchedRecord] does. The picture is not kept: a thumbnail's file id belongs to the TDLib
 * instance that handed it out, so Home asks Telegram for the video again (see
 * [ChatRepository.mediaItem]), as it does for Continue watching.
 */
data class WatchLaterRecord(
    val chatId: Long,
    val messageId: Long,
    val fileId: Int,
    val title: String,
    /** The file's own name, which can say more than a title taken from the caption. */
    val fileName: String,
    val chatTitle: String,
    val sizeBytes: Long,
    val durationSec: Int,
    val mimeType: String,
    val addedAt: Long,
) {
    val key: String get() = SettingsStore.progressKey(chatId, messageId)

    fun toMediaItem(): MediaItem = MediaItem(
        chatId = chatId,
        messageId = messageId,
        fileId = fileId,
        title = title,
        sizeBytes = sizeBytes,
        durationSec = durationSec,
        mimeType = mimeType,
        thumbnailFileId = 0,
        miniThumbnail = null,
        date = 0,
        fileName = fileName.ifEmpty { title },
    )

    internal fun encode(): String = listOf(
        addedAt.toString(),
        fileId.toString(),
        title.replace(SEP, ' '),
        fileName.replace(SEP, ' '),
        chatTitle.replace(SEP, ' '),
        sizeBytes.toString(),
        durationSec.toString(),
        mimeType.replace(SEP, ' '),
    ).joinToString(SEP.toString())

    companion object {
        /** Unit separator, for the reason [ResumeRecord] uses it: titles contain everything else. */
        private const val SEP = '\u001F'
        private const val FIELDS = 8

        fun of(item: MediaItem, chatTitle: String, addedAt: Long) = WatchLaterRecord(
            chatId = item.chatId,
            messageId = item.messageId,
            fileId = item.fileId,
            title = item.title,
            fileName = item.fileName,
            chatTitle = chatTitle,
            sizeBytes = item.sizeBytes,
            durationSec = item.durationSec,
            mimeType = item.mimeType,
            addedAt = addedAt,
        )

        /** Rebuilds a record, or null for a line this build cannot trust. */
        internal fun decode(ids: String, encoded: String?): WatchLaterRecord? {
            if (encoded.isNullOrEmpty()) return null
            val idParts = ids.split('_')
            if (idParts.size != 2) return null
            val chatId = idParts[0].toLongOrNull() ?: return null
            val messageId = idParts[1].toLongOrNull() ?: return null
            val parts = encoded.split(SEP)
            // A later build may append fields; everything up to here still reads the same.
            if (parts.size < FIELDS) return null
            return WatchLaterRecord(
                chatId = chatId,
                messageId = messageId,
                addedAt = parts[0].toLongOrNull() ?: return null,
                fileId = parts[1].toIntOrNull() ?: 0,
                title = parts[2],
                fileName = parts[3],
                chatTitle = parts[4],
                sizeBytes = parts[5].toLongOrNull() ?: 0L,
                durationSec = parts[6].toIntOrNull() ?: 0,
                mimeType = parts[7],
            )
        }
    }
}

/**
 * Watch later: single videos the viewer saved for another time, newest first.
 *
 * Starring a chat puts all of it on Home, which is too much for one film somebody posted in a busy
 * group. This keeps the one video instead. Nothing is downloaded, so it works in a chat that
 * restricts saving content as well as anywhere else.
 *
 * It lives in the Watched list's file ([WatchedStore.watchLater]) under a prefix of its own, which
 * is what lets finishing a video, or marking it watched, cross it off here in the same write that
 * ticks it. "Clear watched" leaves it alone; signing out clears both.
 */
class WatchLaterStore internal constructor(private val store: DataStore<Preferences>) {

    private fun <T> read(transform: (Preferences) -> T): Flow<T> = store.data
        .map(transform)
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    /** The list, most recently added first. */
    val items: Flow<List<WatchLaterRecord>> = read { prefs ->
        decodeAll(prefs).sortedByDescending { it.addedAt }
    }

    /** Every saved video's [SettingsStore.progressKey], for a menu deciding between add and remove. */
    val keys: Flow<Set<String>> = read { prefs ->
        prefs.asMap().keys.mapNotNullTo(HashSet()) { key -> key.name.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX) }
    }

    suspend fun contains(chatId: Long, messageId: Long): Boolean =
        store.data.first()[keyOf(chatId, messageId)] != null

    /** Adds [record], or moves it to the top with the new time when it is there already. */
    suspend fun add(record: WatchLaterRecord) {
        store.edit { prefs ->
            prefs[keyOf(record.chatId, record.messageId)] = record.encode()
            evictOldest(prefs)
        }
    }

    suspend fun remove(chatId: Long, messageId: Long) {
        store.edit { it.remove(keyOf(chatId, messageId)) }
    }

    /** Empties the list. Keys are collected first: the map cannot be walked while it is written. */
    suspend fun clear() {
        store.edit { prefs ->
            val doomed = prefs.asMap().keys.filter { it.name.startsWith(PREFIX) }
            for (key in doomed) prefs.remove(key)
        }
    }

    private fun decodeAll(prefs: Preferences): List<WatchLaterRecord> = buildList {
        for ((key, value) in prefs.asMap()) {
            val name = key.name
            if (!name.startsWith(PREFIX)) continue
            WatchLaterRecord.decode(name.removePrefix(PREFIX), value as? String)?.let(::add)
        }
    }

    /**
     * Keeps the list to [MAX_WATCH_LATER]. A list longer than that has stopped being a plan and
     * become an archive, and the whole file is read on every change.
     */
    internal fun evictOldest(prefs: MutablePreferences) {
        val all = decodeAll(prefs)
        if (all.size <= MAX_WATCH_LATER) return
        all.sortedBy { it.addedAt }
            .take(all.size - MAX_WATCH_LATER)
            .forEach { prefs.remove(keyOf(it.chatId, it.messageId)) }
    }

    companion object {
        /** How many videos the list holds before the oldest start dropping off. */
        const val MAX_WATCH_LATER = 1_000

        /** Not the Watched list's "w_": the two share a file and must never read each other's lines. */
        private const val PREFIX = "l_"

        internal fun keyOf(chatId: Long, messageId: Long) =
            stringPreferencesKey(PREFIX + SettingsStore.progressKey(chatId, messageId))
    }
}
