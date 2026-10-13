package com.tmplayer.data

/**
 * One card of Home's featured carousel: the video it plays, the chat it came from, and the saved
 * place in it when the viewer has one (the card's pill then reads Resume rather than Play).
 */
data class FeaturedPick(
    val item: MediaItem,
    val chatTitle: String,
    val record: ResumeRecord? = null,
) {
    /** Stable for a card while the carousel refills, so a pager can key on it. */
    val key: String get() = SettingsStore.progressKey(item.chatId, item.messageId)
}

/**
 * What Home's featured carousel may show, best first, for the screen to keep the first
 * [HomeFeatured.LIMIT] that have a picture worth showing large (see the spotlight's art rule).
 *
 * Drawn from the starred chats' newest videos, which Home already holds for their rows, so the
 * carousel costs no search of its own:
 * - Films and shows only, as [SeriesShelf] and [MediaName] read them. A clip, a lecture or a
 *   screen recording is better met in its row than blown up across the top of the screen.
 * - Round robin across the chats in their Home order, each chat's newest first, so one busy
 *   channel does not fill every card.
 * - The same film or show never twice: copies of a film fold on [SeriesShelf.sameVideoKey] and a
 *   show on its name, across chats as well as within one. A show is one card, at the episode to
 *   watch next when the viewer has started it, else at its newest episode.
 * - Nothing on the Watched list, nothing played to its end, and no show with every episode seen.
 *
 * Never a pick of TMPlayer's own beyond that: with no starred chat, or nothing in them that
 * qualifies, the list is empty and Home falls back to [HomeRows.spotlight].
 */
object HomeFeatured {

    /** Cards in the carousel at most. */
    const val LIMIT = 5

    /**
     * Candidates offered, so that some can fail the art rule and still leave [LIMIT]: twice as
     * many, which also caps how many pictures the screen looks at to find them.
     */
    const val CANDIDATES = LIMIT * 2

    /** Played this far, a video counts as finished even before the player wrote it as watched. */
    const val FINISHED = 0.95f

    /**
     * @param favourites the starred chats in Home's order (see [HomeRows.favouriteChats]).
     * @param loaded each starred chat's newest videos, by chat id, as Home fetched them.
     * @param continueWatching the saved places, for Resume and for where a show stands.
     * @param watched the progress keys ([SettingsStore.progressKey]) on the Watched list.
     */
    fun pick(
        favourites: List<ChatSummary>,
        loaded: Map<Long, List<MediaItem>>,
        continueWatching: List<ResumeRecord>,
        watched: Set<String>,
        limit: Int = CANDIDATES,
    ): List<FeaturedPick> {
        if (favourites.isEmpty() || limit <= 0) return emptyList()
        val records = continueWatching.associateBy { keyOf(it.chatId, it.messageId) }
        fun recordOf(item: MediaItem) = records[keyOf(item.chatId, item.messageId)]
        fun finished(item: MediaItem): Boolean =
            keyOf(item.chatId, item.messageId) in watched || (recordOf(item)?.fraction ?: 0f) >= FINISHED
        fun position(item: MediaItem): Float = recordOf(item)?.fraction ?: 0f

        // Each chat's candidates, newest first, with the key that says which film or show it is.
        val queues = favourites.distinctBy { it.id }.map { chat ->
            val items = loaded[chat.id].orEmpty()
                .distinctBy { it.id }
                .sortedWith(compareByDescending<MediaItem> { it.date }.thenByDescending { it.messageId })
            SeriesShelf.arrange(items).mapNotNull { entry ->
                when (entry) {
                    is ShelfEntry.Show -> showPick(entry.series, ::finished, ::position)
                        ?.let { "show:${entry.series.key}" to it }
                    is ShelfEntry.File -> filePick(entry.item, ::finished)
                }
            }.map { (key, item) -> key to FeaturedPick(item, chat.title, recordOf(item)?.takeIf { it.positionMs > 0 }) }
        }

        val seen = HashSet<String>()
        val out = ArrayList<FeaturedPick>()
        var round = 0
        while (out.size < limit && queues.any { round < it.size }) {
            for (queue in queues) {
                val (key, pick) = queue.getOrNull(round) ?: continue
                if (seen.add(key)) out += pick
                if (out.size >= limit) break
            }
            round++
        }
        return out
    }

    /**
     * The episode a show's card plays: the next to watch once the viewer has started the show,
     * else its newest. Null for a show seen to the end.
     */
    private fun showPick(
        series: Series,
        finished: (MediaItem) -> Boolean,
        position: (MediaItem) -> Float,
    ): MediaItem? {
        val progress = SeriesShelf.progress(series, finished, position)
        val started = progress.watched > 0 || series.episodes.any { episode -> episode.copies.any { position(it) > 0f } }
        if (!started) return series.cover.takeUnless(finished)
        return progress.next?.item
    }

    /**
     * A file on its own: a film (its copies already folded into one, see [SeriesShelf.arrange]),
     * or the one episode a chat has of a show, keyed so the same film or show in another chat is
     * the same card. Null for anything else, and for what the viewer has finished.
     */
    private fun filePick(item: MediaItem, finished: (MediaItem) -> Boolean): Pair<String, MediaItem>? {
        val copies = item.versions.ifEmpty { listOf(item) }
        if (copies.any(finished)) return null
        if (LessonSteps.lessonOf(item) != null) return null
        if (SeriesShelf.isFilm(item)) {
            val key = SeriesShelf.sameVideoKey(item) ?: "item:${item.id}"
            return "film:$key" to item
        }
        val name = SeriesShelf.episodeOf(item)
        if (!name.isEpisode) return null
        val show = SeriesShelf.keyOf(name.title)
        if (show.isEmpty()) return null
        return "show:$show" to item
    }

    private fun keyOf(chatId: Long, messageId: Long) = SettingsStore.progressKey(chatId, messageId)
}
