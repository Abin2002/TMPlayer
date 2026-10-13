package com.tmplayer.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * A moment in one video the viewer wants to come back to, with an optional short note: the slide
 * a lecture turns to the answer, the scene a friend should see.
 *
 * [id] is the time it was made, which is unique enough within one video and keeps the order a
 * viewer made them in should two ever share a position.
 */
data class Bookmark(
    val chatId: Long,
    val messageId: Long,
    val id: Long,
    val positionMs: Long,
    val note: String,
) {
    val key: String get() = SettingsStore.progressKey(chatId, messageId)
}

/**
 * The viewer's bookmarks, every video's in one file of their own, beside the settings and the
 * Watched list rather than inside either.
 *
 * Kept the way [WatchedStore] keeps its list, and for the same reasons: it grows with use, it is
 * read whole on every change, and it belongs to the account, so signing out clears it as it clears
 * the Watched list. One key per video holds that video's bookmarks, so the player and the detail
 * page read one entry, not the whole file.
 *
 * Like [WatchedStore], it takes an already opened store: DataStore allows one live instance per
 * file in a process, so each app opens it once with [openDataStore] and shares it.
 */
class BookmarkStore(private val store: DataStore<Preferences>) {

    /** One video's bookmarks, earliest in the video first. */
    fun bookmarks(chatId: Long, messageId: Long): Flow<List<Bookmark>> = store.data
        .map { prefs -> decode(chatId, messageId, prefs[keyOf(chatId, messageId)]) }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    suspend fun bookmarksNow(chatId: Long, messageId: Long): List<Bookmark> = bookmarks(chatId, messageId).first()

    /**
     * Saves [positionMs] in the video with [note], and returns the bookmark as stored.
     *
     * A second press within [SAME_MOMENT_MS] of a bookmark already there is the same moment, not a
     * new one: it keeps the one there, taking the new note if one was written. Past
     * [MAX_PER_VIDEO] the oldest made goes, since a list that long is past being a list.
     */
    suspend fun add(chatId: Long, messageId: Long, positionMs: Long, note: String = "", now: Long = System.currentTimeMillis()): Bookmark {
        val clean = cleanNote(note)
        var saved: Bookmark? = null
        store.edit { prefs ->
            val key = keyOf(chatId, messageId)
            val list = decode(chatId, messageId, prefs[key]).toMutableList()
            val near = list.indexOfFirst { abs(it.positionMs - positionMs) < SAME_MOMENT_MS }
            if (near >= 0) {
                if (clean.isNotEmpty()) list[near] = list[near].copy(note = clean)
                saved = list[near]
            } else {
                // Never the id of one already there, even for two made within the same millisecond.
                val id = maxOf(now, (list.maxOfOrNull { it.id } ?: 0L) + 1)
                val made = Bookmark(chatId, messageId, id, positionMs.coerceAtLeast(0L), clean)
                list += made
                saved = made
                if (list.size > MAX_PER_VIDEO) list.remove(list.minBy { it.id })
            }
            write(prefs, chatId, messageId, list)
        }
        return saved!!
    }

    /** Gives bookmark [id] a new note; a blank one clears it. */
    suspend fun rename(chatId: Long, messageId: Long, id: Long, note: String) {
        val clean = cleanNote(note)
        store.edit { prefs ->
            val list = decode(chatId, messageId, prefs[keyOf(chatId, messageId)])
            write(prefs, chatId, messageId, list.map { if (it.id == id) it.copy(note = clean) else it })
        }
    }

    suspend fun delete(chatId: Long, messageId: Long, id: Long) {
        store.edit { prefs ->
            val list = decode(chatId, messageId, prefs[keyOf(chatId, messageId)])
            write(prefs, chatId, messageId, list.filterNot { it.id == id })
        }
    }

    /** Every video's bookmarks, for a backup. */
    suspend fun all(): List<Bookmark> = buildList {
        for ((key, value) in store.data.first().asMap()) {
            val ids = key.name.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.split('_') ?: continue
            if (ids.size != 2) continue
            val chatId = ids[0].toLongOrNull() ?: continue
            val messageId = ids[1].toLongOrNull() ?: continue
            addAll(decode(chatId, messageId, value as? String))
        }
    }

    /**
     * Writes the bookmarks a restore chose ([Backups.bookmarksToAdd]) in one edit, each keeping its
     * note and, unless one here already has it, its id. A video past [MAX_PER_VIDEO] loses its oldest.
     */
    suspend fun restore(marks: List<Bookmark>) {
        if (marks.isEmpty()) return
        store.edit { prefs ->
            for ((video, adds) in marks.groupBy { it.chatId to it.messageId }) {
                val (chatId, messageId) = video
                val list = decode(chatId, messageId, prefs[keyOf(chatId, messageId)]).toMutableList()
                for (mark in adds) {
                    val taken = list.any { it.id == mark.id }
                    val id = if (taken) (list.maxOf { it.id } + 1) else mark.id
                    list += mark.copy(id = id, note = cleanNote(mark.note))
                }
                while (list.size > MAX_PER_VIDEO) list.remove(list.minBy { it.id })
                write(prefs, chatId, messageId, list)
            }
        }
    }

    /** Empties the file, for sign out. Keys are collected first: the map cannot be walked while it is written. */
    suspend fun clear() {
        store.edit { prefs ->
            val doomed = prefs.asMap().keys.filter { it.name.startsWith(PREFIX) }
            for (key in doomed) prefs.remove(key)
        }
    }

    private fun write(prefs: MutablePreferences, chatId: Long, messageId: Long, list: List<Bookmark>) {
        val key = keyOf(chatId, messageId)
        if (list.isEmpty()) prefs.remove(key) else prefs[key] = encode(list)
    }

    companion object {
        const val FILE_NAME = "bookmarks.preferences_pb"

        /** How many bookmarks one video keeps before the oldest made start dropping off. */
        const val MAX_PER_VIDEO = 200

        /** The longest note kept, in characters: a line, not an essay. */
        const val MAX_NOTE = 120

        /** Two bookmarks closer than this are one moment. */
        const val SAME_MOMENT_MS = 1_000L

        private const val PREFIX = "b_"

        /** Between bookmarks, and between a bookmark's fields: notes can hold anything else. */
        private const val RECORD = '\u001E'
        private const val FIELD = '\u001F'

        private fun keyOf(chatId: Long, messageId: Long) =
            stringPreferencesKey(PREFIX + SettingsStore.progressKey(chatId, messageId))

        /** A note as stored: one line, trimmed, the separators out and cut to [MAX_NOTE]. */
        fun cleanNote(note: String): String = note
            .map { if (it == RECORD || it == FIELD || it.isISOControl()) ' ' else it }
            .joinToString("")
            .replace(SPACES, " ")
            .trim()
            .take(MAX_NOTE)
            .trim()

        private val SPACES = Regex("\\s+")

        internal fun encode(list: List<Bookmark>): String = list.joinToString(RECORD.toString()) {
            listOf(it.id.toString(), it.positionMs.toString(), it.note).joinToString(FIELD.toString())
        }

        /** Rebuilds a video's list, earliest first, skipping any record this build cannot read. */
        internal fun decode(chatId: Long, messageId: Long, encoded: String?): List<Bookmark> {
            if (encoded.isNullOrEmpty()) return emptyList()
            return encoded.split(RECORD).mapNotNull { record ->
                val parts = record.split(FIELD)
                // A later build may append fields; the first three still read the same.
                if (parts.size < 3) return@mapNotNull null
                Bookmark(
                    chatId = chatId,
                    messageId = messageId,
                    id = parts[0].toLongOrNull() ?: return@mapNotNull null,
                    positionMs = parts[1].toLongOrNull() ?: return@mapNotNull null,
                    note = parts[2],
                )
            }.sortedWith(compareBy({ it.positionMs }, { it.id }))
        }

        /** Opens the store at [file]. Call once per process and share the result. */
        fun openDataStore(file: File): DataStore<Preferences> =
            PreferenceDataStoreFactory.createWithPath(produceFile = { file.absolutePath.toPath() })
    }
}

/**
 * A start position handed from a bookmark on the detail page to the player that page opens.
 *
 * The detail page starts a video the way every other screen does, through a callback that knows
 * nothing of positions, so rather than thread one through every caller the page leaves the
 * position here and the player takes it on opening that video, in place of its resume point. It
 * is taken once: the next open of the same video resumes as usual.
 */
object BookmarkJump {
    private val pending = ConcurrentHashMap<String, Long>()

    fun request(chatId: Long, messageId: Long, positionMs: Long) {
        pending[SettingsStore.progressKey(chatId, messageId)] = positionMs.coerceAtLeast(0L)
    }

    /** The position asked for this video, once, or null when none was. */
    fun take(chatId: Long, messageId: Long): Long? = pending.remove(SettingsStore.progressKey(chatId, messageId))
}
