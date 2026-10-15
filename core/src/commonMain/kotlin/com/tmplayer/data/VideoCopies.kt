package com.tmplayer.data

import kotlin.math.abs

/**
 * One video posted more than once, in one chat or several: which copy to play, and whose place
 * counts.
 *
 * A film in 720p, 1080p and 4K is one film to the viewer. Its place, its pick and the quality the
 * viewer tends to choose belong to the film, not to whichever message happened to be played, so
 * switching copies half way carries on at the same moment and Continue watching holds one card.
 * The film is named by [SeriesShelf.sameVideoKey], read off the title a resume record keeps
 * ([keyOfTitle]), so a live video and a stored record are matched the same way.
 */
object VideoCopies {

    /**
     * Which video a title names, for matching a playing copy against stored records, or null for
     * a title that names neither a film nor an episode (a clip is only ever a copy of itself).
     * Read the way [ResumeRecord.toMediaItem] rebuilds a record, the title standing in for the
     * file name, so both sides go through the same parse.
     */
    fun keyOfTitle(title: String): String? = if (title.isBlank()) null else SeriesShelf.sameVideoKey(
        MediaItem(
            chatId = 0, messageId = 0, fileId = 0, title = title, sizeBytes = 0, durationSec = 0,
            mimeType = "", thumbnailFileId = 0, miniThumbnail = null, date = 0, fileName = title,
        ),
    )

    /** [keyOfTitle] for a video in a chat. */
    fun keyOf(item: MediaItem): String? = keyOfTitle(item.title)

    /**
     * The record [item] resumes from: its own, or the newest among the copies of the same video,
     * whichever was written last. Null when none of them was started. A copy's own older place
     * loses to a newer one on another copy, since the newer one is where the viewer stopped.
     */
    fun resumeOf(item: MediaItem, records: List<ResumeRecord>): ResumeRecord? {
        val own = records.firstOrNull { it.chatId == item.chatId && it.messageId == item.messageId }
        val key = keyOf(item) ?: return own
        return records
            .filter { (it.chatId == item.chatId && it.messageId == item.messageId) || keyOfTitle(it.title) == key }
            .maxWithOrNull(compareBy<ResumeRecord> { it.updatedAt }.thenBy { it === own })
    }

    /**
     * The other copies' places to forget when [title] at [chatId] and [messageId] takes over: a
     * place belongs to the video, so writing one for a copy replaces whatever its other copies held.
     * Pairs of chat and message, never the copy itself.
     */
    fun superseded(chatId: Long, messageId: Long, title: String, stored: List<Triple<Long, Long, String>>): List<Pair<Long, Long>> {
        val key = keyOfTitle(title) ?: return emptyList()
        return stored
            .filter { (chat, message, other) -> !(chat == chatId && message == messageId) && keyOfTitle(other) == key }
            .map { (chat, message, _) -> chat to message }
    }

    /**
     * The picture's height a copy was made at, 2160 for 4K: from the name where it says ("1080p",
     * "4K"), else from the size Telegram states, the shorter side. Null when neither says.
     */
    fun heightOf(item: MediaItem): Int? {
        val name = item.fileName.ifBlank { item.title }
        val marked = EpisodeCopies.markers(name).firstNotNullOfOrNull { it.toIntOrNull() }
        if (marked != null) return marked
        if (item.width > 0 && item.height > 0) return snap(minOf(item.width, item.height))
        return null
    }

    /**
     * Which of [copies] to play without asking, in this order:
     * 1. the one the viewer chose for this video before ([chosen], an [MediaItem.id]);
     * 2. one already on this device, a download before a cached copy, so nothing is fetched;
     * 3. the one the viewer started ([started]), so a resume lands in the same file;
     * 4. the one nearest the quality the viewer usually plays ([habit]), lower before higher on a
     *    tie, so someone who watches 1080p is not handed a 4K file;
     * 5. the newest.
     */
    fun pick(
        copies: List<MediaItem>,
        chosen: String? = null,
        started: (MediaItem) -> Boolean = { false },
        habit: QualityHabit = QualityHabit(),
    ): MediaItem? {
        if (copies.size <= 1) return copies.firstOrNull()
        chosen?.let { id -> copies.firstOrNull { it.id == id } }?.let { return it }
        copies.firstOrNull { it.locality == MediaItem.Locality.Downloaded }?.let { return it }
        copies.firstOrNull { it.onDevice }?.let { return it }
        copies.firstOrNull(started)?.let { return it }
        val newest = copies.maxWith(compareBy<MediaItem> { it.date }.thenBy { it.messageId })
        val usual = habit.usual ?: return newest
        return copies
            .mapNotNull { copy -> heightOf(copy)?.let { copy to it } }
            .minWithOrNull(
                compareBy<Pair<MediaItem, Int>> { abs(it.second - usual) }
                    .thenBy { it.second }
                    .thenByDescending { it.first.date },
            )?.first ?: newest
    }

    /** Whether [copies] come from more than one chat, which is when each names its chat. */
    fun spansChats(copies: List<MediaItem>): Boolean = copies.distinctBy { it.chatId }.size > 1

    /** Telegram's stated sizes are rarely the round figure: 1920x800 is a 1080p release cropped wide. */
    private fun snap(shortSide: Int): Int = STEPS.firstOrNull { shortSide <= it * 1.05 } ?: shortSide

    private val STEPS = listOf(240, 360, 480, 576, 720, 1080, 1440, 2160, 4320)
}

/**
 * The qualities the viewer has played, counted, for [VideoCopies.pick]: "1080 twelve times, 720
 * three times". Kept small and plain, a line of `height:count` pairs, since one setting is all it is.
 */
data class QualityHabit(val counts: Map<Int, Int> = emptyMap()) {

    /** The quality played most, the higher on a tie, or null before anything was played. */
    val usual: Int?
        get() = counts.entries.maxWithOrNull(compareBy<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })?.key

    /**
     * One more play at [height]. Counts are halved once one passes [CAP], so a habit that changes
     * (a new television) wins within a few weeks rather than never.
     */
    fun noted(height: Int?): QualityHabit {
        if (height == null || height <= 0) return this
        val next = counts + (height to (counts[height] ?: 0) + 1)
        val trimmed = if (next.values.any { it > CAP }) next.mapValues { it.value / 2 }.filterValues { it > 0 } else next
        return QualityHabit(trimmed)
    }

    fun encode(): String = counts.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" }

    companion object {
        const val CAP = 40

        fun decode(text: String?): QualityHabit = QualityHabit(
            text.orEmpty().split(',').mapNotNull { pair ->
                val (height, count) = pair.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                val h = height.trim().toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                val c = count.trim().toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                h to c
            }.toMap(),
        )
    }
}

/** The short name a copy goes by on a button: "1080p", "4K", else its size. */
fun MediaItem.copyQuality(): String {
    val height = VideoCopies.heightOf(this)
    return when {
        height == null -> MediaMapper.formatSize(sizeBytes)
        height >= 2160 -> "4K"
        else -> "${height}p"
    }
}
