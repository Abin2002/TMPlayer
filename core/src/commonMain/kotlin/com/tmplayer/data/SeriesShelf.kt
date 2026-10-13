package com.tmplayer.data

import java.util.Locale

/**
 * One episode of a series, with the numbers [MediaName] read off its name or its caption.
 *
 * [episode] is never null here: a file that names a season but no episode (a whole-season pack)
 * stays a loose file, since there is nowhere in an episode list to put it.
 *
 * [copies] is every file of this one episode, newest first: channels post the same episode in
 * several sizes. [item] is the first of them, the copy the list shows when nothing says which
 * one the viewer prefers; [SeriesShelf.pick] is how to choose.
 */
data class SeriesEpisode(
    val item: MediaItem,
    val season: Int,
    val episode: Int,
    val copies: List<MediaItem> = listOf(item),
) {
    /** "S01E02"; a release with no season of its own counts as season one. */
    val code: String
        get() = "S%02dE%02d".format(Locale.ROOT, season, episode)

    /** The same episode, whichever copy each of the two stands for. */
    fun sameAs(other: SeriesEpisode): Boolean = season == other.season && episode == other.episode
}

/** One season's episodes, in watching order. */
data class SeriesSeason(val number: Int, val episodes: List<SeriesEpisode>)

/**
 * Every file in a chat that belongs to one show, grouped by season.
 *
 * [key] is what the files were grouped on and stays stable while pages load, so a list can key
 * the tile on it. [title] is the show's name as the first of its files wrote it.
 */
data class Series(val key: String, val title: String, val seasons: List<SeriesSeason>) {
    val episodes: List<SeriesEpisode> get() = seasons.flatMap { it.episodes }
    val episodeCount: Int get() = seasons.sumOf { it.episodes.size }

    /** The newest upload in the show, for the tile's picture: it is what was posted last. */
    val cover: MediaItem get() = episodes.maxBy { it.item.date }.item
}

/** One thing in a chat's listing once series are folded together: a show, or a file on its own. */
sealed interface ShelfEntry {
    val key: String

    data class Show(val series: Series) : ShelfEntry {
        override val key: String get() = "series-${series.key}"
    }

    data class File(val item: MediaItem) : ShelfEntry {
        override val key: String get() = "media-${item.id}"
    }
}

/** How far through a show the viewer is, for the tile's line and the episode list. */
data class SeriesProgress(
    /** Episodes on the Watched list. */
    val watched: Int,
    val total: Int,
    /**
     * Where to carry on from: the episode in progress, else the one after the furthest finished
     * episode, else the first. Null once every episode is watched.
     */
    val next: SeriesEpisode?,
    /**
     * The copy the viewer watched last, in progress or the furthest finished, or null for a show
     * not yet started. The next episode's copy is chosen to be like it: see [SeriesShelf.pick].
     */
    val like: MediaItem? = null,
) {
    val finished: Boolean get() = total > 0 && watched >= total
}

/**
 * Folds a chat's videos into one tile per show.
 *
 * Grouped on the cleaned title, lower-cased and stripped of punctuation, so "Harbour.Notes.S01E01"
 * and "Harbour Notes - 02" land together. A show needs [MIN_EPISODES] episodes before it earns a
 * tile of its own; a lone episode is clearer as the file it is. Copies of one episode (the same
 * episode posted at 350 MB and at 1.8 GB) are one episode with several [SeriesEpisode.copies].
 *
 * Once the chat's files have established a show, a file that names it less carefully joins it:
 * a bare "E05" the parser would not trust on its own, a caption titled in another script or by a
 * shorter name ("Family Full House Episode 5" for "Family Full House With Rohit Sharma").
 *
 * Order follows the chat: a show sits where its newest file would have been, so the listing still
 * reads newest first and nothing jumps when the Series view is turned on.
 */
object SeriesShelf {

    const val MIN_EPISODES = 2

    /** The parse a file is grouped by: its name first, its caption second. */
    fun episodeOf(item: MediaItem): ParsedName =
        MediaName.parse(item.nameForParsing, item.caption)

    /** "harbour notes" for "Harbour.Notes", "Harbour Notes:" and "HARBOUR NOTES". */
    fun keyOf(title: String): String =
        title.lowercase(Locale.ROOT)
            .replace(NOT_WORD, " ")
            .trim()
            .replace(SPACES, " ")

    /** One file that names an episode: the key it was read under, and the others it could go by. */
    private class Found(
        val item: MediaItem,
        val key: String,
        val name: ParsedName,
        val episode: Int,
        /** The file name's own title and the caption's, where they differ from [key]. */
        val alternates: List<String>,
    )

    fun arrange(items: List<MediaItem>): List<ShelfEntry> {
        val strict = items.map { item ->
            val name = episodeOf(item)
            val key = keyOf(name.title)
            val number = name.episode
            if (key.isEmpty() || number == null) null else Found(item, key, name, number, alternatesOf(item, key))
        }
        // A show is two episodes or more, not two files: three copies of one episode are one.
        val showKeys = strict.filterNotNull()
            .groupBy { it.key }
            .filterValues { files -> files.distinctBy { (it.name.season ?: 1) to it.episode }.size >= MIN_EPISODES }
            .keys

        // The same season uploaded twice under two names, "Kerala Crime Files" and "Kerala Crime
        // Files Shiju Parayil Veedu Neendakara", is one show: the longer name joins the shorter
        // when it starts with it and most of its episodes are ones the shorter already has. A
        // sequel under a longer name has episodes of its own and stays apart.
        val numbersOf = strict.filterNotNull().groupBy { it.key }
            .mapValues { (_, files) -> files.mapTo(HashSet()) { (it.name.season ?: 1) to it.episode } }
        val alias = showKeys.associateWith { key ->
            val mine = numbersOf[key].orEmpty()
            showKeys.filter { other -> other != key && key.startsWith("$other ") && other.split(' ').size >= 2 }
                .maxByOrNull { it.length }
                ?.takeIf { other -> mine.count { it in numbersOf[other].orEmpty() } * 2 >= mine.size }
                ?: key
        }

        // Everything else gets a second look, now that the chat has said which shows it holds.
        val placedUnder = items.indices.map { index ->
            val found = strict[index] ?: loose(items[index])
            when {
                found == null -> null
                found.key in showKeys -> alias.getValue(found.key).let { key ->
                    if (key == found.key) found else Found(found.item, key, found.name, found.episode, emptyList())
                }
                else -> joining(found, showKeys)?.let { key -> Found(found.item, alias.getValue(key), found.name, found.episode, emptyList()) }
            }
        }
        val shows = placedUnder.filterNotNull()
            .groupBy { it.key }
            .mapValues { (key, files) -> series(key, files) }

        // What is left over and names a film, or one episode of a show the chat has no more of:
        // copies of it (a 720p and a 1080p of Loki S02E05) fold into one tile.
        fun copyKey(index: Int): String? = filmKey(items[index])
            ?: strict[index]?.let { "episode:${it.key}|${it.name.season ?: 1}|${it.episode}" }
        val films = items.indices
            .filter { placedUnder[it] == null }
            .mapNotNull { index -> copyKey(index)?.let { index to it } }
            .groupBy({ it.second }, { items[it.first] })
            .filterValues { it.size >= 2 }
            .mapValues { (_, copies) -> copies.sortedWith(compareByDescending<MediaItem> { it.date }.thenBy { it.messageId }) }

        val placed = HashSet<String>()
        return items.mapIndexedNotNull { index, item ->
            val key = placedUnder[index]?.key
            val show = key?.let { shows[it] }
            val copies = if (show == null) copyKey(index) else null
            val film = copies?.let { films[it] }
            when {
                film != null -> {
                    if (placed.add("film:$copies")) ShelfEntry.File(film.first().copy(versions = film)) else null
                }
                show == null -> ShelfEntry.File(item)
                placed.add(key) -> ShelfEntry.Show(show)
                else -> null
            }
        }
    }

    /**
     * [items] with the copies of each film folded into one, and nothing else touched: what a
     * chat's grid lists. Episodes stay one tile apiece, in the chat's order, since a chat lists
     * what was posted; only a film posted five times over (720p, 1080p, HEVC, a small x264) is one
     * tile, its [MediaItem.versions] the copies, newest first. It sits where its newest copy was.
     */
    fun foldFilmCopies(items: List<MediaItem>): List<MediaItem> {
        val grouped = arrange(items).mapNotNull { (it as? ShelfEntry.File)?.item?.takeIf { item -> item.versions.size > 1 } }
        if (grouped.isEmpty()) return items
        val groupOf = HashMap<String, MediaItem>()
        grouped.forEach { film -> film.versions.forEach { groupOf[it.id] = film } }
        val placed = HashSet<String>()
        return items.mapNotNull { item ->
            val film = groupOf[item.id] ?: return@mapNotNull item
            if (placed.add(film.id)) film else null
        }
    }

    /**
     * What makes [item] a copy of another file: the same film (see [filmKey]) or the same episode
     * of the same show, whatever its size or quality. Null for a video that names neither, which
     * is never a copy of anything. The key [arrange] folds copies on, for a file read alone.
     */
    fun sameVideoKey(item: MediaItem): String? {
        filmKey(item)?.let { return it }
        val name = episodeOf(item)
        val episode = name.episode ?: return null
        val key = keyOf(name.title)
        if (key.isEmpty()) return null
        return "episode:$key|${name.season ?: 1}|$episode"
    }

    /**
     * The film's own name for a tile that stands for several copies: "Night Train (2019)" rather
     * than whichever file name happened to be newest. The item's title when the name reads as nothing.
     */
    fun filmTitle(item: MediaItem): String {
        val name = episodeOf(item)
        if (name.title.isBlank()) return item.title
        return if (name.year != null) "${name.title} (${name.year})" else name.title
    }

    /**
     * The name a video's tile goes by, wherever a tile is drawn: a chat's grid, Home, Continue
     * watching, Watched and Watch later. The film's name for a tile of several copies ([filmTitle]),
     * the cleaned file name for a release ([MediaName.tileTitle]), and the title as it is for a
     * video named by its caption, since a person wrote that and nothing in it is a release tag.
     *
     * Works on an item rebuilt from a saved record as well (`ResumeRecord.toMediaItem()` and the
     * like), whose file name is the title that was stored, so a record written before this existed
     * reads as cleanly as a fresh one and nothing stored changes.
     */
    fun tileTitle(item: MediaItem): String = when {
        item.versions.size > 1 -> filmTitle(item)
        item.fileName.isBlank() || MediaMapper.isGenericName(item.fileName) -> item.title
        else -> MediaName.tileTitle(item.fileName, item.caption) ?: item.title
    }

    /**
     * Whether [item] is a film, which a grid draws as a 2:3 poster card: several copies of one
     * film, or a release name that states a year and no episode. Everything else, a clip, a
     * lecture, a screen recording or a file named by its caption, is a video and stays a wide
     * card that shows what the file is.
     */
    fun isFilm(item: MediaItem): Boolean {
        if (item.versions.size > 1) return true
        if (item.fileName.isBlank() || MediaMapper.isGenericName(item.fileName)) return false
        val name = MediaName.parse(item.fileName, item.caption)
        return !name.isSeries && name.year != null && name.title.isNotBlank()
    }

    /** Whether [entry] is drawn as a poster: a show, or a film ([isFilm]). */
    fun isPoster(entry: ShelfEntry): Boolean = when (entry) {
        is ShelfEntry.Show -> true
        is ShelfEntry.File -> isFilm(entry.item)
    }

    /**
     * A film poster's name, without the year ("Gladiator II"), which its line of facts carries
     * instead; [tileTitle] where the name reads as nothing.
     */
    fun posterTitle(item: MediaItem): String =
        MediaName.parse(item.nameForParsing, item.caption).title.ifBlank { tileTitle(item) }

    /**
     * What makes two files the same film: the title read off the name, and the year. A file names
     * a film only when it carries a year or a quality marker (1080p, x265), so two clips that share
     * a plain name ("Lecture", "Video") are never taken for copies of one another.
     */
    private fun filmKey(item: MediaItem): String? {
        val name = episodeOf(item)
        if (name.isSeries) return null
        val key = keyOf(name.title)
        if (key.isEmpty()) return null
        if (name.year == null && EpisodeCopies.markers(item.nameForParsing).isEmpty()) return null
        return "$key|${name.year ?: ""}"
    }

    /**
     * A file the parser would not call an episode on its own, read the lenient way: "Show E05",
     * "Show, E5. Vlog", which only counts once [joining] finds the show it names.
     */
    private fun loose(item: MediaItem): Found? {
        val caption = item.caption.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
        val name = listOfNotNull(item.nameForParsing.ifBlank { null }, caption)
            .firstNotNullOfOrNull { MediaName.looseEpisode(it) }
            ?: return null
        val key = keyOf(name.title)
        val number = name.episode ?: return null
        if (key.isEmpty()) return null
        return Found(item, key, name, number, alternatesOf(item, key))
    }

    /**
     * The established show [found] belongs to, or null. Its own key or one of its alternates,
     * exactly; else the one show whose name starts with it, for a caption that shortens the name.
     * A shortened name has to keep two words at least, so "Harbour" never swallows a file into
     * "Harbour Notes".
     */
    private fun joining(found: Found, showKeys: Set<String>): String? {
        val keys = listOf(found.key) + found.alternates
        keys.firstOrNull { it in showKeys }?.let { return it }
        return keys.asSequence()
            .filter { it.split(' ').size >= 2 }
            .mapNotNull { short -> showKeys.filter { it.startsWith("$short ") }.singleOrNull() }
            .firstOrNull()
    }

    /** The keys the file name alone and the caption alone would give, other than [key]. */
    private fun alternatesOf(item: MediaItem, key: String): List<String> {
        val caption = item.caption.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
        return listOfNotNull(
            item.fileName.ifBlank { null }?.let { MediaName.parse(it).title },
            caption?.let { MediaName.parse(it).title },
        ).map(::keyOf).filter { it.isNotEmpty() && it != key }.distinct()
    }

    private fun series(key: String, files: List<Found>): Series {
        // A release with no season (the fansub form, a bare "Ep 04") is season one, unless every
        // file that does name a season names the same one.
        val named = files.mapNotNull { it.name.season }.distinct()
        val defaultSeason = named.singleOrNull() ?: 1
        val episodes = files
            .groupBy { (it.name.season ?: defaultSeason) to it.episode }
            .map { (number, copies) ->
                // Copies of one episode (720p and 1080p) under one row, the newer first.
                val sorted = copies.map { it.item }.sortedWith(compareByDescending<MediaItem> { it.date }.thenBy { it.messageId })
                SeriesEpisode(sorted.first(), number.first, number.second, sorted)
            }
        val seasons = episodes
            .groupBy { it.season }
            .toSortedMap()
            .map { (number, list) -> SeriesSeason(number, list.sortedBy { it.episode }) }
        // "harbour_notes_720p" is the same show as "Harbour.Notes", but the second is how to write it.
        // A file that joined under another name is a last resort for the title.
        val titles = files.sortedBy { keyOf(it.name.title) != key }.map { it.name.title }
        val title = titles.firstOrNull { it != it.lowercase(Locale.ROOT) && keyOf(it) == key }
            ?: titles.firstOrNull { keyOf(it) == key }
            ?: titles.first()
        return Series(key = key, title = title, seasons = seasons)
    }

    /**
     * Where the viewer is in [series]. An episode counts as watched when any copy of it is, and
     * as started when any copy is partway.
     *
     * @param finished whether an item is on the Watched list.
     * @param position how far into an item the viewer got, 0 to 1, or 0 when never started.
     */
    fun progress(
        series: Series,
        finished: (MediaItem) -> Boolean,
        position: (MediaItem) -> Float,
    ): SeriesProgress {
        val all = series.episodes
        val done = all.map { episode -> episode.copies.any(finished) }
        val watched = done.count { it }
        fun started(index: Int) = all[index].copies.firstOrNull { !finished(it) && position(it) > 0f }
        // Partway through one beats everything: it is literally where the viewer stopped.
        val inProgress = all.indices.lastOrNull { !done[it] && started(it) != null }
        val furthest = done.lastIndexOf(true)
        val like = inProgress?.let(::started)
            ?: all.getOrNull(furthest)?.copies?.firstOrNull(finished)
        val next = when {
            inProgress != null -> all[inProgress]
            else -> (furthest + 1 until all.size).firstOrNull { !done[it] }?.let { all[it] }
                ?: all.indices.firstOrNull { !done[it] }?.let { all[it] }
        }
        return SeriesProgress(
            watched = watched,
            total = all.size,
            next = next?.let { it.copy(item = pick(it, finished, position, like)) },
            like = like,
        )
    }

    /**
     * Which copy of [episode] to play: the one partway through, else the one most like [like]
     * (the copy the viewer watched last), else the newest.
     */
    fun pick(
        episode: SeriesEpisode,
        finished: (MediaItem) -> Boolean,
        position: (MediaItem) -> Float,
        like: MediaItem?,
    ): MediaItem {
        if (episode.copies.size <= 1) return episode.item
        episode.copies.firstOrNull { !finished(it) && position(it) > 0f }?.let { return it }
        return like?.let { EpisodeCopies.closest(it, episode.copies) } ?: episode.item
    }

    private val NOT_WORD = Regex("""[^\p{L}\p{N}]+""")
    private val SPACES = Regex("""\s+""")
}
