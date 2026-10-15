package com.tmplayer.data

/**
 * One page of the phone's featured carousel on Home: a film or a video ([item]), or a whole show
 * ([series], with [item] its newest episode, the one that put it there).
 */
data class FeaturedPick(val entry: ShelfEntry) {
    /** Stable while the carousel refills, so a pager can key on it and keep the page it is on. */
    val key: String get() = entry.key

    val series: Series? get() = (entry as? ShelfEntry.Show)?.series

    /** The file the page stands for: the film (its copies in [MediaItem.versions]), or the show's newest episode. */
    val item: MediaItem
        get() = when (entry) {
            is ShelfEntry.File -> entry.item
            is ShelfEntry.Show -> entry.series.cover
        }

    /** When it was posted: a show by its newest episode. */
    val date: Int get() = item.date
}

/**
 * What the phone's featured carousel at the top of Home shows: the newest [LIMIT] different films,
 * shows and videos in the starred chats, which is what the "Recently added in starred chats" row
 * holds ([HomeRow.Recent]), so the carousel asks Telegram for nothing of its own.
 *
 * - Different means different: copies of one film (a 720p and a 1080p, in one chat or two) are one
 *   page, as [SeriesShelf.sameVideoKey] keys them, and a show is one page however many of its
 *   episodes arrived, whether they came folded into a show or as a lone episode.
 * - Never an extra: a trailer, a teaser or a sample is not something to put across the top of Home.
 * - Films and shows first. A clip or a recording only fills a page when the starred chats have
 *   fewer than [LIMIT] films and shows among their newest videos, so a carousel is never empty
 *   for a chat of lectures.
 * - Newest first, all from one chat or from several, as they came.
 *
 * [rank] then puts the pages with a sharp online picture first, since a page is a large picture
 * and Telegram's own thumbnails are small and soft.
 */
object HomeFeatured {

    /** Pages in the carousel at most. */
    const val LIMIT = 5

    /** The newest [limit] different films, shows and videos in [entries] (see [HomeFeatured]). */
    fun pick(entries: List<ShelfEntry>, limit: Int = LIMIT): List<FeaturedPick> {
        if (limit <= 0) return emptyList()
        val seen = HashSet<String>()
        val titled = ArrayList<FeaturedPick>()
        val others = ArrayList<FeaturedPick>()
        entries
            .map(::FeaturedPick)
            .sortedWith(compareByDescending<FeaturedPick> { it.date }.thenByDescending { it.item.messageId })
            .forEach { pick ->
                val (key, isTitled) = identity(pick) ?: return@forEach
                if (!seen.add(key)) return@forEach
                if (isTitled) titled += pick else others += pick
            }
        val chosen = (titled.take(limit) + others.take((limit - titled.size).coerceAtLeast(0))).toHashSet()
        // Back in the order they came, newest first, now that the films and shows have their places.
        return (titled + others)
            .filter { it in chosen }
            .sortedWith(compareByDescending<FeaturedPick> { it.date }.thenByDescending { it.item.messageId })
    }

    /**
     * [picks] with those [sharp] says have a sharp online picture first, each half in the order it
     * had. Worked out once when the carousel is filled, never as pictures arrive, so a page does
     * not move while it is being looked at.
     */
    fun rank(picks: List<FeaturedPick>, sharp: (FeaturedPick) -> Boolean): List<FeaturedPick> {
        val (first, rest) = picks.partition(sharp)
        return first + rest
    }

    /**
     * Which film or show [pick] is, and whether it is a film or a show at all; null for an extra.
     * A show's key is its name, so a lone episode of a show already in the carousel is the same page.
     */
    private fun identity(pick: FeaturedPick): Pair<String, Boolean>? {
        val series = pick.series
        if (series != null) return "show:${SeriesShelf.keyOf(series.title)}" to true
        val item = pick.item
        if (item.extra != null) return null
        val name = SeriesShelf.episodeOf(item)
        if (name.extra != null) return null
        if (name.isEpisode) {
            val show = SeriesShelf.keyOf(name.title)
            if (show.isNotEmpty()) return "show:$show" to true
        }
        val key = SeriesShelf.sameVideoKey(item)?.let { "film:$it" } ?: "item:${item.id}"
        return key to SeriesShelf.isFilm(item)
    }
}
