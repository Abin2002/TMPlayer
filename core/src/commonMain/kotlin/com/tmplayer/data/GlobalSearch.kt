package com.tmplayer.data

import java.util.Locale

/**
 * A chat as a search result draws it: enough for an avatar and a name, read from `getChat` as the
 * page comes in, so a video from a chat that is not in the loaded list still says where it is.
 */
@androidx.compose.runtime.Immutable
data class ChatBrief(
    val id: Long,
    val title: String,
    val kind: ChatKind,
    val photoFileId: Int = 0,
    val miniThumbnail: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean =
        other is ChatBrief && id == other.id && title == other.title && kind == other.kind &&
            photoFileId == other.photoFileId && miniThumbnail.contentEquals(other.miniThumbnail)

    override fun hashCode(): Int = (id.hashCode() * 31 + title.hashCode()) * 31 + photoFileId

    companion object {
        fun of(chat: ChatSummary) = ChatBrief(chat.id, chat.title, chat.kind, chat.photoFileId, chat.miniThumbnail)
    }
}

/** Which kind of chat Telegram is asked to search: its own chat type filter, so a server side choice. */
enum class SearchChatType { All, Channels, Groups, People }

/**
 * The picture size a result must have a copy at. Read off each copy as the detail panel reads it
 * ([MediaFacts]), so it is decided here, after Telegram has answered.
 */
enum class SearchQuality(val tag: String?) {
    Any(null),
    UHD("4K"),
    FullHd("1080p"),
    Hd("720p"),
}

/** The order of the results. Only [BestMatch] is Telegram's question; the rest reorder what came back. */
enum class SearchSort { BestMatch, Newest, BestQuality, Largest }

/** The filter chips over the results. The defaults are what a search with no chip touched asks. */
data class SearchFilters(
    val chatType: SearchChatType = SearchChatType.All,
    val quality: SearchQuality = SearchQuality.Any,
    /** Leaves out the copies under [SizeFilter.DEFAULT_MIN]: trailers, samples, clips. */
    val hideSmall: Boolean = false,
    /** On by default: somebody looking for a video does not care which list its chat was filed in. */
    val includeArchived: Boolean = true,
) {
    val isDefault: Boolean get() = this == SearchFilters()

    /** What changes the question put to Telegram, as opposed to what only screens its answer. */
    val serverSide: Pair<SearchChatType, Boolean> get() = chatType to includeArchived
}

/** A client side filter that left nothing, named so the empty state can say which and offer to clear it. */
enum class HidingFilter { Quality, SmallClips, Year, ChatType, Archived }

/**
 * What was typed, read: the words to send Telegram, and a year or a quality that act as filters.
 *
 * "2024" or "1080p" alone is a filter over whatever Telegram finds for it, not a title. A quality
 * among other words leaves the words to search ("dune 1080p" asks for "dune" and keeps 1080p
 * copies); a year among other words stays in the text, since years are in titles ("1917",
 * "Blade Runner 2049") and the file names that carry them.
 */
data class SearchQuery(
    /** The cleaned query, as typed but tidied: what the field and the history show. */
    val text: String,
    /** What Telegram is asked. Empty when there is nothing to ask. */
    val server: String,
    val year: Int? = null,
    val quality: SearchQuality = SearchQuality.Any,
) {
    /** A year or a quality and nothing else: results are filtered by it and not ranked by title. */
    val filterOnly: Boolean get() = (year != null || quality != SearchQuality.Any) && titleWords.isEmpty()

    /** The words a title is ranked against: the text without a quality marker, and none for a year alone. */
    val titleWords: List<String>
        get() = Fuzzy.tokens(text).filter { qualityOfToken(it) == null }.let { if (year != null && it.size == 1) emptyList() else it }

    val isEmpty: Boolean get() = server.isEmpty()

    companion object {
        internal fun qualityOfToken(token: String): SearchQuality? = when (token.lowercase(Locale.ROOT)) {
            "4k", "2160p", "uhd" -> SearchQuality.UHD
            "1080p", "1080i", "fhd" -> SearchQuality.FullHd
            "720p", "hd" -> SearchQuality.Hd
            else -> null
        }
    }
}

/**
 * One result card: every copy of one video the search found, across all the chats it was posted
 * in, or one show's episodes in one chat.
 *
 * [copies] are in [GlobalSearch.copyOrder], so the first is the one a tap plays and the card shows.
 */
@androidx.compose.runtime.Immutable
data class ResultGroup(
    val key: String,
    val copies: List<MediaItem>,
    /** The show's name, for a group of episodes of one show in one chat; null for a video. */
    val show: String? = null,
    /** A trailer, a sample or a short clip of something longer: listed, never folded into the video. */
    val extra: Boolean = false,
) {
    val lead: MediaItem get() = copies.first()

    /** The chats the copies are in, the lead's first, each once. */
    val chatIds: List<Long> get() = copies.map { it.chatId }.distinct()

    /** A show with more than one episode found, which opens the series page rather than a video. */
    val isShow: Boolean get() = show != null && episodeCount > 1

    /** How many different episodes a show group holds. */
    val episodeCount: Int get() = copies.mapNotNull { SeriesShelf.sameVideoKey(it) }.distinct().size.coerceAtLeast(1)

    /** The best picture any copy has, as [MediaFacts] names it, or null when none says. */
    val bestQuality: String? get() = copies.mapNotNull(GlobalSearch::qualityOf).maxByOrNull(GlobalSearch::qualityRank)

    /** The smallest and the largest copy, in bytes, leaving out copies of unknown size. */
    val sizeRange: LongRange? get() {
        val sizes = copies.map { it.sizeBytes }.filter { it > 0 }
        return if (sizes.isEmpty()) null else sizes.min()..sizes.max()
    }

    val newest: Int get() = copies.maxOf { it.date }

    /** The lead with every copy as its [MediaItem.versions], for the detail page's copies list. */
    fun asItem(): MediaItem = if (copies.size < 2) lead else lead.copy(versions = copies)
}

/**
 * The pure half of the search across every chat (CP73): reading the query, turning pages of
 * Telegram's answer into one card per video, ranking and sorting them, the local matches that show
 * before Telegram answers, and the filters.
 *
 * Telegram's own search matches whole words, and a file name like `Harbour_Notes_2024.mkv` is one
 * word to it, since an underscore joins words rather than parting them: a search for "harbour"
 * can miss it. Two things answer that. A query of several words is tried again with underscores
 * ([variants]), and everything the app has already seen (chat listings, Continue watching,
 * downloads) is matched here with [Fuzzy], which treats dots, underscores and dashes as spaces and
 * forgives a typo, and merged in by message id ([localVideos]).
 *
 * Kept free of TDLib and of Compose so the whole of it runs in plain JVM tests.
 */
object GlobalSearch {

    /** Chats offered above the results before "Show all". */
    const val CHAT_PREVIEW = 3

    /** The first answer keeps asking until it has this many cards, so it never opens on two. */
    const val FIRST_GROUPS = 12

    /** ... or until it has asked this many times. */
    const val FIRST_PAGES = 3

    /** No list grows past this: a search that matched a thousand files was not specific enough. */
    const val MAX_GROUPS = 300

    /** Recent searches listed on an empty search. */
    const val RECENT_SHOWN = 8

    /** The longest query kept: longer than any real title, short enough to show in a row. */
    const val MAX_QUERY = 80

    /** Under ten minutes, with the same name as a longer copy, is a trailer or a clip of it. */
    const val SHORT_SECONDS = 600

    /**
     * The query tidied: trimmed, single spaced, a pasted link or a leading @handle taken off
     * (a handle alone keeps its name), and at most [MAX_QUERY] characters.
     */
    fun clean(raw: String): String {
        var words = raw.trim().split(WHITESPACE).filter { it.isNotEmpty() }
        words = words.filterNot { URL.matches(it) }
        if (words.size > 1 && words.first().startsWith("@")) words = words.drop(1)
        return words.joinToString(" ") { it.removePrefix("@") }.trim().take(MAX_QUERY).trim()
    }

    /**
     * Whether [query] is worth asking Telegram: two characters, or one in a script that writes a
     * word in a single character (Chinese, Japanese, Korean).
     */
    fun searchable(query: String): Boolean {
        val text = query.trim()
        if (text.isEmpty()) return false
        return text.length >= 2 || text.any(::isCjk)
    }

    /** [raw] read into what to ask and what to filter on; see [SearchQuery]. */
    fun parse(raw: String, maxYear: Int = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) + 1): SearchQuery {
        val text = clean(raw)
        val tokens = text.split(' ').filter { it.isNotEmpty() }
        val qualities = tokens.mapNotNull { SearchQuery.qualityOfToken(it) }
        val rest = tokens.filter { SearchQuery.qualityOfToken(it) == null }
        val yearAlone = rest.singleOrNull()?.toIntOrNull()?.takeIf { it in 1900..maxYear }
        val server = when {
            !searchable(text) -> ""
            rest.isEmpty() -> tokens.first()
            else -> rest.joinToString(" ")
        }
        return SearchQuery(
            text = text,
            server = server.takeIf { searchable(it) }.orEmpty(),
            year = yearAlone,
            quality = qualities.firstOrNull() ?: SearchQuality.Any,
        )
    }

    /**
     * Other spellings to ask Telegram when the query found little: its words joined by underscores
     * and by dots, the way release names are written. Nothing for one word.
     */
    fun variants(server: String): List<String> {
        val words = Fuzzy.tokens(server)
        if (words.size < 2) return emptyList()
        return listOf(words.joinToString("_"), words.joinToString("."))
    }

    /**
     * Whether [item] answers [query] the way a result must: its year when the query is a year, its
     * picture when it names one. Everything passes a plain title query.
     */
    fun answers(item: MediaItem, query: SearchQuery): Boolean {
        if (query.filterOnly && query.year != null && SeriesShelf.episodeOf(item).year != query.year) return false
        return true
    }

    /**
     * [page] folded into [existing]: a copy of a video already on screen joins its card, which
     * stays where it is, and the cards that are new are ordered by [sort] among themselves and go
     * after, so paging never moves a card the viewer is looking at. At most [MAX_GROUPS] cards.
     * Every video once, by message id, whichever source found it.
     */
    fun merge(
        existing: List<ResultGroup>,
        page: List<MediaItem>,
        query: SearchQuery,
        sort: SearchSort = SearchSort.BestMatch,
    ): List<ResultGroup> {
        if (page.isEmpty()) return existing
        val seen = existing.flatMapTo(HashSet()) { group -> group.copies.map { it.id } }
        val fresh = page.filter { seen.add(it.id) && answers(it, query) }
        if (fresh.isEmpty()) return existing
        val byKey = LinkedHashMap<String, MutableList<MediaItem>>()
        val shows = HashMap<String, String>()
        existing.forEach { group -> byKey[group.key] = group.copies.toMutableList(); group.show?.let { shows[group.key] = it } }
        val added = LinkedHashSet<String>()
        for (item in fresh) {
            val (key, show) = keyOf(item)
            byKey.getOrPut(key) { added += key; mutableListOf() } += item
            if (show != null) shows.putIfAbsent(key, show)
        }
        val kept = existing.map { group ->
            val copies = byKey.getValue(group.key)
            if (copies.size == group.copies.size) group else group.copy(copies = copyOrder(copies))
        }
        val appended = added.map { key ->
            ResultGroup(key, copyOrder(byKey.getValue(key)), shows[key], extra = key.startsWith(EXTRA))
        }
        return (kept + order(appended, query, sort)).take(MAX_GROUPS)
    }

    /**
     * What folds a video into a card. An episode goes under its show in its own chat, since a show
     * opens on that chat's series page. A trailer, a sample or a short clip goes under an extras
     * key of its own, so it never joins the video it is a clip of. Anything else under
     * [SeriesShelf.sameVideoKey], across chats, so the same film posted in four channels is one
     * card; a video that names nothing is a card of its own.
     */
    fun keyOf(item: MediaItem): Pair<String, String?> {
        val name = SeriesShelf.episodeOf(item)
        val same = SeriesShelf.sameVideoKey(item)
        if (isExtra(item)) return "$EXTRA${same ?: item.id}" to null
        if (name.episode != null) {
            val show = SeriesShelf.keyOf(name.title)
            if (show.isNotEmpty()) return "show:${item.chatId}:$show" to name.title
        }
        if (same == null) return "video:${item.id}" to null
        return "same:$same" to null
    }

    /**
     * A trailer, a teaser, a sample, or a film's name on something under [SHORT_SECONDS]: what a
     * channel posts beside the film, not a copy of it. An episode is never one by length alone,
     * since short episodes are common.
     */
    fun isExtra(item: MediaItem): Boolean {
        if (EXTRA_WORDS.containsMatchIn(Fuzzy.normalize(item.nameForParsing))) return true
        val name = SeriesShelf.episodeOf(item)
        if (name.episode != null) return false
        return item.durationSec in 1 until SHORT_SECONDS && SeriesShelf.sameVideoKey(item) != null && name.year != null
    }

    /** [groups] in [sort]'s order: see [rank] for the best match. */
    fun order(groups: List<ResultGroup>, query: SearchQuery, sort: SearchSort): List<ResultGroup> = when (sort) {
        SearchSort.BestMatch -> rank(groups, query)
        SearchSort.Newest -> groups.sortedWith(compareBy<ResultGroup> { it.extra }.thenByDescending { it.newest })
        SearchSort.BestQuality -> groups.sortedWith(
            compareByDescending<ResultGroup> { group -> group.bestQuality?.let(::qualityRank) ?: 0 }
                .thenBy { it.extra }
                .thenByDescending { it.newest },
        )
        SearchSort.Largest -> groups.sortedWith(
            compareByDescending<ResultGroup> { it.sizeRange?.last ?: 0L }.thenByDescending { it.newest },
        )
    }

    /**
     * Best first: an exact title, then one that starts with the query, then one holding every
     * word, then a file name that matches, and a mention in a caption last. Extras go after the
     * video they are extras of. Ties go to more copies (a widely posted video is the likelier
     * one), then a better picture, then the newest. A query that is only a year or a quality has
     * no title to rank by, so it is newest first.
     */
    fun rank(groups: List<ResultGroup>, query: SearchQuery): List<ResultGroup> =
        groups.sortedWith(
            compareByDescending<ResultGroup> { if (query.filterOnly) 0 else relevance(it, query) }
                .thenBy { it.extra }
                .thenByDescending { it.copies.size }
                .thenByDescending { group -> group.bestQuality?.let(::qualityRank) ?: 0 }
                .thenByDescending { it.newest },
        )

    /** 5 exact title, 4 prefix, 3 every word in the title, 2 the file name, 1 anything else, 0 nothing. */
    fun relevance(group: ResultGroup, query: SearchQuery): Int {
        val wanted = query.titleWords
        if (wanted.isEmpty()) return 0
        val joined = wanted.joinToString(" ")
        // The parsed name, without its year or tags, so "Night.Train.2019" is exactly "night train".
        val title = Fuzzy.normalize(group.show ?: SeriesShelf.episodeOf(group.lead).title.ifBlank { titleOf(group) })
        val titleWords = title.split(' ')
        return when {
            title == joined -> 5
            title.startsWith(joined) -> 4
            wanted.all { it in titleWords } -> 3
            group.copies.any { Fuzzy.score(it.fileName.ifBlank { it.title }, joined) > 0 } -> 2
            group.copies.any { Fuzzy.score(it.searchText, joined) > 0 } -> 1
            else -> 0
        }
    }

    /** The name a card goes by: the show's, or the tile title, never a shortened poster title. */
    fun titleOf(group: ResultGroup): String = group.show?.takeIf { group.isShow } ?: SeriesShelf.tileTitle(group.asItem())

    /** On this device first, then the best picture, then the newest. */
    fun copyOrder(copies: List<MediaItem>): List<MediaItem> =
        copies.sortedWith(
            compareByDescending<MediaItem> { it.onDevice }
                .thenByDescending { qualityOf(it)?.let(::qualityRank) ?: 0 }
                .thenByDescending { it.date }
                .thenByDescending { it.messageId },
        )

    /** "4K", "1080p", "720p", "SD", or null when neither the name nor the stated size says. */
    fun qualityOf(item: MediaItem): String? =
        MediaMapper.qualityTags(item.fileName.ifBlank { item.title }).firstOrNull()
            ?: MediaFacts.resolutionOf(item.width, item.height)

    fun qualityRank(quality: String): Int = when (quality.uppercase(Locale.ROOT)) {
        "4K" -> 4
        "1080P" -> 3
        "720P" -> 2
        "SD" -> 1
        else -> 0
    }

    /**
     * [groups] screened by the client side filters and by a quality the query named: each card
     * keeps only its copies that pass, and a card with none left goes. Order is kept, so a filter
     * changed while results are on screen never asks Telegram again.
     */
    fun filter(groups: List<ResultGroup>, filters: SearchFilters, query: SearchQuery? = null): List<ResultGroup> {
        val quality = filters.quality.takeIf { it != SearchQuality.Any } ?: query?.quality ?: SearchQuality.Any
        if (quality == SearchQuality.Any && !filters.hideSmall) return groups
        return groups.mapNotNull { group ->
            val kept = group.copies.filter { copy ->
                (quality.tag == null || qualityOf(copy) == quality.tag) &&
                    (!filters.hideSmall || SizeFilter.matches(copy.sizeBytes, SizeFilter.DEFAULT_MIN, SizeFilter.DEFAULT_MAX))
            }
            when {
                kept.isEmpty() -> null
                kept.size == group.copies.size -> group
                else -> group.copy(copies = kept)
            }
        }
    }

    /**
     * Which of the filters in force each, on its own, empties [groups], for an empty state that
     * says what hides the results and clears it in one tap. Empty when nothing was found at all.
     */
    fun hiddenBy(groups: List<ResultGroup>, filters: SearchFilters, query: SearchQuery): List<HidingFilter> {
        if (groups.isEmpty()) {
            return buildList {
                if (filters.chatType != SearchChatType.All) add(HidingFilter.ChatType)
                if (!filters.includeArchived) add(HidingFilter.Archived)
            }
        }
        return buildList {
            if (filters.quality != SearchQuality.Any && filter(groups, SearchFilters(quality = filters.quality)).isEmpty()) add(HidingFilter.Quality)
            if (filters.hideSmall && filter(groups, SearchFilters(hideSmall = true)).isEmpty()) add(HidingFilter.SmallClips)
            if (isEmpty() && filters.quality != SearchQuality.Any && filters.hideSmall) {
                add(HidingFilter.Quality)
                add(HidingFilter.SmallClips)
            }
            if (query.quality != SearchQuality.Any && filter(groups, SearchFilters(), query).isEmpty()) add(HidingFilter.Quality)
        }.distinct()
    }

    /** The chats whose names answer [query], best first, starred chats ahead of the rest on a tie. */
    fun localChats(chats: List<ChatSummary>, query: String, favourites: Set<Long> = emptySet()): List<ChatSummary> {
        val text = clean(query)
        if (text.isBlank()) return emptyList()
        val scored = chats.map { it to Fuzzy.score(it.title, text) }.filter { it.second > 0 }
        return scored.sortedWith(
            compareByDescending<Pair<ChatSummary, Int>> { it.second }.thenByDescending { it.first.id in favourites },
        ).map { it.first }
    }

    /**
     * Whether [item] answers [query] by anything the app knows about it: the tile title, the file
     * name, the caption, and [names] (the online title, in its own language and in English).
     * [Fuzzy] folds case, accents, dots, underscores and dashes, and forgives a typo.
     */
    fun matchesLocally(item: MediaItem, query: SearchQuery, names: List<String> = emptyList()): Boolean {
        if (query.filterOnly) {
            val token = query.server
            return Fuzzy.score(item.searchText, token) > 0 && answers(item, query)
        }
        val text = query.titleWords.joinToString(" ").ifBlank { return false }
        val fields = listOf(SeriesShelf.tileTitle(item), item.fileName, item.searchText) + names
        return fields.any { it.isNotBlank() && Fuzzy.score(it, text) > 0 }
    }

    /**
     * The videos the app already knows of that answer [query], as cards, before Telegram has said
     * anything and whatever it says: what chat listings loaded, Continue watching, downloads.
     * Found without a connection, and merged with Telegram's pages by message id, so a file
     * Telegram's word search cannot see is still found. [names] gives a video's online titles.
     */
    fun localVideos(
        known: List<MediaItem>,
        query: SearchQuery,
        allowed: Set<Long>? = null,
        names: (MediaItem) -> List<String> = { emptyList() },
    ): List<MediaItem> {
        if (query.isEmpty) return emptyList()
        return known.asSequence()
            .filter { KidsLock.allows(allowed, it.chatId) }
            .distinctBy { it.id }
            .filter { matchesLocally(it, query, names(it)) }
            .toList()
    }

    /**
     * The recent chats row of an empty search: starred chats first, then the rest in Telegram's
     * own order, which is already newest activity first.
     */
    fun recentChats(chats: List<ChatSummary>, favourites: Set<Long>, limit: Int = 12): List<ChatSummary> {
        val (starred, rest) = chats.filter { !it.isArchived }.partition { it.id in favourites }
        return (starred + rest).take(limit)
    }

    /** Whether the first answer should ask for another page before it is shown as finished. */
    fun wantsMore(groups: Int, pagesAsked: Int, done: Boolean): Boolean =
        !done && groups < FIRST_GROUPS && pagesAsked < FIRST_PAGES

    private fun isCjk(c: Char): Boolean {
        val block = Character.UnicodeBlock.of(c)
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            block == Character.UnicodeBlock.HIRAGANA ||
            block == Character.UnicodeBlock.KATAKANA ||
            block == Character.UnicodeBlock.HANGUL_SYLLABLES
    }

    private const val EXTRA = "extra:"
    private val WHITESPACE = Regex("\\s+")
    private val URL = Regex("""(?i)^(https?://|www\.|t\.me/|telegram\.me/)\S*$""")
    private val EXTRA_WORDS = Regex("""(?<![a-z0-9])(trailer|teaser|sample|promo)(?![a-z0-9])""")
}
