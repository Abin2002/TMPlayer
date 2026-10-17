package com.tmplayer.data

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * How the Downloads page lists what is kept on the device: the episodes of one show together, in
 * watching order under the show's name, and every other video on its own, newest first.
 *
 * Presentation only. Nothing here changes a record or its order on disk; the page reads the same
 * rows it always did and only draws them in these groups.
 */
object DownloadShelf {

    /** One thing in the list: a show with its episodes, or a video on its own. */
    sealed interface Entry<T> {
        val key: String

        /** The newest of the rows it stands for, which places it in the list. */
        val newest: Long

        data class Show<T>(
            override val key: String,
            val title: String,
            /** In watching order: season, then episode. */
            val episodes: List<T>,
            override val newest: Long,
        ) : Entry<T>

        data class Single<T>(override val key: String, val row: T, override val newest: Long) : Entry<T>
    }

    /**
     * [rows] grouped by show. A show needs [SeriesShelf.MIN_EPISODES] downloaded episodes to be a
     * group: one episode alone reads better as a video of its own than as a heading over one line.
     *
     * @param item the row as a [MediaItem], whose name is read for the show and the episode.
     * @param key a key unique to the row, for the list.
     * @param at when the row was downloaded, for the order.
     */
    fun <T> arrange(
        rows: List<T>,
        item: (T) -> MediaItem,
        key: (T) -> String,
        at: (T) -> Long,
        corrections: Map<String, VideoCorrection> = VideoCorrections.current,
    ): List<Entry<T>> {
        class Found(val row: T, val show: String?, val title: String, val season: Int, val episode: Int)

        val found = rows.map { row ->
            val name = SeriesShelf.episodeOf(item(row), corrections)
            val episode = name.episode
            val show = if (episode != null) SeriesShelf.keyOf(name.title).ifEmpty { null } else null
            Found(row, show, name.title, name.season ?: 1, episode ?: 0)
        }
        val byShow = found.filter { it.show != null }.groupBy { it.show!! }
        val entries = ArrayList<Entry<T>>()
        found.forEach { f ->
            val members = f.show?.let(byShow::get)
            if (members == null || members.size < SeriesShelf.MIN_EPISODES) {
                entries += Entry.Single(key(f.row), f.row, at(f.row))
            }
        }
        byShow.forEach { (show, members) ->
            if (members.size < SeriesShelf.MIN_EPISODES) return@forEach
            val ordered = members.sortedWith(compareBy({ it.season }, { it.episode }))
            entries += Entry.Show(
                key = "show:$show",
                title = members.maxBy { at(it.row) }.title,
                episodes = ordered.map { it.row },
                newest = members.maxOf { at(it.row) },
            )
        }
        // Stable, so rows downloaded in the same moment keep the order they came in.
        return entries.sortedByDescending { it.newest }
    }
}

/**
 * Today, Yesterday or Earlier, by calendar day as [WatchedWhen] counts them: something played at
 * eleven last night is Yesterday this morning.
 */
enum class DayGroup {
    Today,
    Yesterday,
    Earlier,
    ;

    companion object {
        fun of(at: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): DayGroup {
            val then = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            return when (ChronoUnit.DAYS.between(then, today)) {
                in Long.MIN_VALUE..0L -> Today
                1L -> Yesterday
                else -> Earlier
            }
        }

        /**
         * [items] under day headings, in the order they came, or null when headings would not
         * help: a list that falls on one day only, or one whose times do not run newest first
         * (headings over a list out of order would repeat).
         */
        fun <T> headings(items: List<T>, at: (T) -> Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<Pair<DayGroup, List<T>>>? {
            if (items.isEmpty()) return null
            val groups = items.map { of(at(it), now, zone) }
            if (groups.zipWithNext().any { (a, b) -> b < a }) return null
            if (groups.distinct().size < 2) return null
            return items.indices.groupBy { groups[it] }.map { (day, indices) -> day to indices.map(items::get) }
        }
    }
}
