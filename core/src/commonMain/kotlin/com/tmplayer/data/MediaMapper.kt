package com.tmplayer.data

import com.tmplayer.i18n.L
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageAnimation
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.MessageVideo
import java.text.SimpleDateFormat
import java.io.File
import java.util.Date
import java.util.Locale

/**
 * One playable thing in a chat, flattened out of whatever message shape Telegram used.
 *
 * Equality is over every field, not the id alone, or a list whose badges have changed compares
 * equal to the one before it and the change never reaches the screen. It also lets Compose skip
 * the rows that genuinely did not move. The blurred preview is compared by content because TDLib
 * hands back a fresh array on each read.
 */
@androidx.compose.runtime.Immutable
data class MediaItem(
    val chatId: Long,
    val messageId: Long,
    val fileId: Int,
    val title: String,
    val sizeBytes: Long,
    val durationSec: Int,
    val mimeType: String,
    val thumbnailFileId: Int,
    val miniThumbnail: ByteArray?,
    val date: Int,
    val fileName: String = "",
    /**
     * Whether a whole copy of the video is on this device, whichever kind: true for both
     * [Locality.Cached] and [Locality.Downloaded]. Kept beside [locality] for the callers that only
     * ask "can this play without a connection".
     */
    val onDevice: Boolean = false,
    /**
     * Which kind of copy that is, which is what the tile badge and the menu name. Follows [onDevice]
     * unless told otherwise: a mapper that only knows TDLib's answer can say no more than cached.
     */
    val locality: Locality = if (onDevice) Locality.Cached else Locality.Remote,
    /**
     * False when the chat or the message has "restrict saving content" on. The video still streams,
     * but every way of keeping a copy is withheld: see [ContentProtection].
     */
    val canBeSaved: Boolean = true,
    /**
     * The post's caption, trimmed, or empty. A second source for the episode when the file name
     * does not carry one: see [MediaName.parse].
     */
    val caption: String = "",
    /**
     * The picture's size in pixels as Telegram states it, or 0 where it does not: a video sent as
     * a file carries no dimensions, and the detail panel then reads the resolution off its name.
     */
    val width: Int = 0,
    val height: Int = 0,
    /**
     * Every copy of this one film in the chat, this one among them, newest first, when the chat
     * posted it more than once (a 720p and a 1080p): see [SeriesShelf.arrange]. Empty for a file
     * that is the only copy, and for an episode, whose copies live on its [SeriesEpisode].
     */
    val versions: List<MediaItem> = emptyList(),
    /**
     * The forum topic the post is in, for a group that has topics turned on, or 0. Lets a topic's
     * listing tell its own live posts from the rest of the group's, and the player keep the next
     * episode inside the topic the viewer was browsing.
     */
    val topicId: Int = 0,
    /**
     * Every piece of a film cut into several files ("CD1", "CD2"), this one first, in order, when
     * this is the first piece: see [SeriesShelf.arrange]. Played one after another as one video.
     * Empty for a whole file. Like [versions], arranging's answer and not part of equality.
     */
    val parts: List<MediaItem> = emptyList(),
    /**
     * Set when this file is a trailer, a teaser or a sample posted beside a film rather than the
     * film: never folded into it, listed after it, and labelled. See [SeriesShelf.arrange].
     */
    val extra: ExtraKind? = null,
) {
    /**
     * The file whose Watched mark stands for this tile: a split film's last piece, since the film
     * is watched only once that is, and the file itself otherwise.
     */
    val watchedBy: MediaItem get() = parts.lastOrNull()?.takeIf { parts.size > 1 } ?: this

    /** This copy with the same [versions] list, for moving from one version to another. */
    fun asVersion(of: MediaItem): MediaItem = if (of.versions.isEmpty()) this else copy(versions = of.versions)

    /**
     * Where a video's bytes are, as far as the viewer is concerned.
     *
     * Three answers rather than a yes or no, because the two kinds of copy behave differently: a
     * download is in TMPlayer's Downloads folder until the viewer deletes it, while a cached copy is
     * in TDLib's cache and the next video played may take it.
     */
    enum class Locality {
        /** Only on Telegram. Playing it streams it. */
        Remote,

        /** Whole in TDLib's cache, because it was played. Evictable. */
        Cached,

        /** In the Downloads folder, listed in the download index. Kept until deleted. */
        Downloaded,
    }

    /** This item with its copy described again, keeping [onDevice] and [locality] in step. */
    fun withLocality(locality: Locality): MediaItem =
        copy(onDevice = locality != Locality.Remote, locality = locality)

    /**
     * This item with its cached state read again from the disk: [wholeOnDevice] is TDLib's answer
     * now, which may differ from the one it gave when the item was fetched.
     *
     * The watch cache holds one video, so playing another takes the last one's copy away, and a
     * tile fetched before that went on saying Cached. Downloaded is left alone, since only the
     * download index can say a download has gone. Returns this same instance when nothing moved,
     * so a caller can tell a list that needs publishing from one that does not.
     */
    fun recheckedCache(wholeOnDevice: Boolean): MediaItem = when {
        locality == Locality.Downloaded -> this
        wholeOnDevice == (locality == Locality.Cached) -> this
        wholeOnDevice -> withLocality(Locality.Cached)
        else -> withLocality(Locality.Remote)
    }

    /**
     * "4K", "1080p": read off the file name, which is where releases state it.
     *
     * Computed once and kept rather than on every read: as a `get()` the regex would run on every
     * recomposition of a tile, several hundred times a second on a scrolling grid, for an answer
     * that cannot change.
     */
    val qualityTags: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        MediaMapper.qualityTags(fileName)
    }

    val id: String get() = "$chatId:$messageId"

    /**
     * The name to read a show, an episode or a year out of: the file's own, unless a camera or
     * Telegram made it up, in which case the title, which is then the caption's first line.
     */
    val nameForParsing: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (fileName.isBlank() || MediaMapper.isGenericName(fileName)) title else fileName
    }

    /**
     * [nameForParsing] read by [MediaName.parse] with the caption as a second source, once per item:
     * grouping, keying and naming all ask for it, and a chat's listing asks for every file.
     */
    val parsedName: ParsedName by lazy(LazyThreadSafetyMode.PUBLICATION) {
        MediaName.parse(nameForParsing, caption)
    }

    /** The same without the caption, for a caption the chat puts under every post. */
    val parsedNameAlone: ParsedName by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (caption.isBlank()) parsedName else MediaName.parse(nameForParsing)
    }

    /**
     * Everything a search inside a chat is matched against: Telegram finds a video by its caption
     * as well as by its file name, so ranking by the name alone threw away every video it found
     * through the words under it.
     */
    val searchText: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        listOf(fileName, title, caption).filter { it.isNotBlank() }.distinct().joinToString("\n")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MediaItem) return false
        return chatId == other.chatId &&
            messageId == other.messageId &&
            fileId == other.fileId &&
            title == other.title &&
            sizeBytes == other.sizeBytes &&
            durationSec == other.durationSec &&
            mimeType == other.mimeType &&
            thumbnailFileId == other.thumbnailFileId &&
            date == other.date &&
            fileName == other.fileName &&
            onDevice == other.onDevice &&
            locality == other.locality &&
            canBeSaved == other.canBeSaved &&
            caption == other.caption &&
            width == other.width &&
            height == other.height &&
            topicId == other.topicId &&
            extra == other.extra &&
            parts.size == other.parts.size &&
            // By id: a copy found since (a new upload of the film) has to reach the tile and the
            // page, and comparing whole copies would compare their copies' thumbnails over again.
            versions.size == other.versions.size &&
            versions.indices.all { versions[it].id == other.versions[it].id } &&
            miniThumbnail.contentEquals(other.miniThumbnail)
    }

    override fun hashCode(): Int {
        var result = chatId.hashCode()
        result = 31 * result + messageId.hashCode()
        result = 31 * result + fileId
        result = 31 * result + title.hashCode()
        result = 31 * result + sizeBytes.hashCode()
        result = 31 * result + durationSec
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + thumbnailFileId
        result = 31 * result + date
        result = 31 * result + fileName.hashCode()
        result = 31 * result + onDevice.hashCode()
        result = 31 * result + locality.hashCode()
        result = 31 * result + canBeSaved.hashCode()
        result = 31 * result + caption.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + topicId
        result = 31 * result + (extra?.hashCode() ?: 0)
        result = 31 * result + parts.size
        result = 31 * result + (miniThumbnail?.contentHashCode() ?: 0)
        result = 31 * result + versions.fold(0) { acc, copy -> 31 * acc + copy.id.hashCode() }
        return result
    }
}

/**
 * Turns Telegram messages into playable entries, dropping everything that is not a video.
 *
 * Videos are posted both as videos and, very often because it preserves quality and
 * multi-track audio, as plain documents, so both shapes are accepted and documents are
 * screened by MIME type and file extension.
 */
object MediaMapper {

    /** Container formats Media3 can demux, plus the ones NextLib's decoders cover. */
    private val VIDEO_EXTENSIONS = setOf(
        "mkv", "mp4", "m4v", "avi", "mov", "webm", "ts", "m2ts", "mts",
        "flv", "wmv", "mpg", "mpeg", "3gp", "ogv", "divx", "vob", "asf", "rmvb",
    )

    /**
     * The video in [message], or null when there is none or it is self-destructing.
     *
     * [chatProtected] is the chat's "restrict saving content", for a caller that has the chat to
     * hand; the message's own flag usually says the same, see [ContentProtection.verdict].
     */
    fun fromMessage(message: Message, chatProtected: Boolean = false): MediaItem? =
        if (selfDestructs(message)) null else mapped(message, chatProtected)

    /** A page of search results, with the self-destructing videos left out and counted. */
    fun screen(messages: List<Message>, chatProtected: Boolean): ContentProtection.Screened =
        ContentProtection.screen(messages, ::selfDestructs) { mapped(it, chatProtected) }

    /** Media Telegram means to be seen once, in Telegram: a timer, or "view once". */
    fun selfDestructs(message: Message): Boolean = message.selfDestructType != null

    /**
     * Whether [content] is something [fromMessage] would list: a video, a silent clip, or a file
     * that looks like a video. For an edit, where TDLib sends the new content without its message.
     */
    fun isVideoContent(content: dev.g000sha256.tdl.dto.MessageContent): Boolean = when (content) {
        is MessageVideo, is MessageAnimation -> true
        is dev.g000sha256.tdl.dto.MessagePaidMedia -> content.media.any { it is dev.g000sha256.tdl.dto.PaidMediaVideo }
        is MessageDocument -> looksLikeVideo(content.document.fileName.orEmpty(), content.document.mimeType.orEmpty())
        else -> false
    }

    private fun fromVideo(message: Message, video: dev.g000sha256.tdl.dto.Video, caption: String?) = MediaItem(
        chatId = message.chatId,
        messageId = message.id,
        fileId = video.video.id,
        title = displayTitle(video.fileName, caption, L.mediaUntitledVideo),
        fileName = video.fileName,
        sizeBytes = fileSize(video.video.size, video.video.expectedSize),
        durationSec = video.duration,
        mimeType = video.mimeType.orEmpty(),
        thumbnailFileId = video.thumbnail?.file?.id ?: 0,
        miniThumbnail = video.minithumbnail?.data,
        date = message.date,
        onDevice = onDevice(video.video),
        caption = captionOf(caption),
        width = video.width,
        height = video.height,
    )

    /** The video in [message] with its saving rule applied, whatever its timer says. */
    private fun mapped(message: Message, chatProtected: Boolean): MediaItem? {
        // Not a post yet: a scheduled one has a stand-in id until it goes out, and one still
        // sending may yet fail. Either would be a tile pointing at a message that does not exist.
        if (message.schedulingState != null || message.sendingState != null) return null
        // Withheld by Telegram on this platform or in this country. Listed, it opens to an error.
        if (!message.restrictionInfo?.restrictionReason.isNullOrEmpty()) return null
        val topic = (message.topicId as? dev.g000sha256.tdl.dto.MessageTopicForum)?.forumTopicId ?: 0
        val item = video(message)?.let { if (topic != 0) it.copy(topicId = topic) else it } ?: return null
        return when (ContentProtection.verdict(message.canBeSaved, chatProtected, selfDestructs = false)) {
            ContentProtection.Verdict.Full -> item
            else -> item.copy(canBeSaved = false)
        }
    }

    private fun video(message: Message): MediaItem? = when (val content = message.content) {
        is MessageVideo -> fromVideo(message, content.video, content.caption?.text)
        // A post sold for Stars, or one the viewer has bought: the video is there once unlocked.
        // Still locked it is a preview with no file, and nothing to play. A post of several takes
        // the first: a tile is one message, and the rest are a tap away in Telegram.
        is dev.g000sha256.tdl.dto.MessagePaidMedia -> content.media
            .firstNotNullOfOrNull { it as? dev.g000sha256.tdl.dto.PaidMediaVideo }
            ?.let { fromVideo(message, it.video, content.caption?.text) }

        is MessageDocument -> {
            val document = content.document
            val name = document.fileName.orEmpty()
            if (!looksLikeVideo(name, document.mimeType.orEmpty())) null
            else MediaItem(
                chatId = message.chatId,
                messageId = message.id,
                fileId = document.document.id,
                title = displayTitle(name, content.caption?.text, L.mediaUntitledFile),
                fileName = name,
                sizeBytes = fileSize(document.document.size, document.document.expectedSize),
                durationSec = 0,
                mimeType = document.mimeType.orEmpty(),
                thumbnailFileId = document.thumbnail?.file?.id ?: 0,
                miniThumbnail = document.minithumbnail?.data,
                date = message.date,
                onDevice = onDevice(document.document),
                caption = captionOf(content.caption?.text),
            )
        }

        // Silent MP4s, the way most short clips and trailers arrive.
        is MessageAnimation -> {
            val animation = content.animation
            MediaItem(
                chatId = message.chatId,
                messageId = message.id,
                fileId = animation.animation.id,
                title = displayTitle(animation.fileName, content.caption?.text, L.mediaUntitledClip),
                fileName = animation.fileName,
                sizeBytes = fileSize(animation.animation.size, animation.animation.expectedSize),
                durationSec = animation.duration,
                mimeType = animation.mimeType.orEmpty(),
                thumbnailFileId = animation.thumbnail?.file?.id ?: 0,
                miniThumbnail = animation.minithumbnail?.data,
                date = message.date,
                onDevice = onDevice(animation.animation),
                caption = captionOf(content.caption?.text),
                width = animation.width,
                height = animation.height,
            )
        }

        else -> null
    }

    fun looksLikeVideo(fileName: String, mimeType: String): Boolean {
        if (mimeType.startsWith("video/", ignoreCase = true)) return true
        // Plenty of uploads arrive as application/octet-stream; fall back to the extension.
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return extension in VIDEO_EXTENSIONS
    }

    /**
     * The one marker worth a badge: how sharp the file is.
     *
     * Descriptive file names put this in the name and nowhere else; the container would have to be
     * opened to learn it otherwise, which is not worth a download per tile. Deliberately only
     * resolution: codec and dynamic range tell somebody choosing what to watch nothing they can
     * act on, and a tile wearing three badges is harder to read than one wearing the badge that
     * decides anything.
     */
    fun qualityTags(fileName: String?): List<String> {
        val name = fileName?.lowercase() ?: return emptyList()
        return when {
            RESOLUTION_4K.containsMatchIn(name) -> listOf("4K")
            name.contains("1080p") || name.contains("1080i") || (BARE_1080.containsMatchIn(name) && RELEASE_WORDS.containsMatchIn(name)) -> listOf("1080p")
            name.contains("720p") || (BARE_720.containsMatchIn(name) && RELEASE_WORDS.containsMatchIn(name)) -> listOf("720p")
            name.contains("480p") || name.contains("360p") -> listOf("SD")
            else -> emptyList()
        }
    }

    private val RESOLUTION_4K = Regex("""\b(2160p|4k|uhd)\b""")

    /**
     * "HDRip - 1080.mkv": the height with its "p" left off, which some uploaders do. Only as a
     * word of its own, so a 1920x1080 or a bitrate with digits round it is not read as one.
     */
    private val BARE_1080 = Regex("""(?<![\w])1080(?![\w])""")
    private val BARE_720 = Regex("""(?<![\w])720(?![\w])""")

    /**
     * Only a release names its height bare, "HDRip - 1080". Without one of these beside it the
     * number is an episode: anime run to four figures, and "One Piece - 1080" is not a 1080p file.
     */
    private val RELEASE_WORDS = Regex("""(rip|web|blu-?ray|x26[45]|h\.?26[45]|hevc|avc|aac|hq)""")

    /** A name a person chose beats a caption, a caption beats a made-up name, and either beats nothing. */
    fun displayTitle(fileName: String?, caption: String?, fallback: String): String {
        val name = fileName?.trim().orEmpty()
        val firstCaptionLine = captionOf(caption).lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        // A name a phone or Telegram made up says nothing a viewer can choose by, and the caption
        // the poster wrote under it usually does: "Episode 5, The Storm" over VID_20241013_1200.
        if (name.isNotEmpty() && !(isGenericName(name) && firstCaptionLine.isNotEmpty())) return name
        if (firstCaptionLine.isNotEmpty()) return firstCaptionLine.take(TITLE_MAX)
        return fallback
    }

    /**
     * Whether [fileName] is one a camera, a messenger or a screen recorder chose rather than a
     * person: `VID_20241013_120000.mp4`, `video_2024-10-13_12-00-00.mp4`, `IMG_1234.MOV`,
     * `PXL_20240101_093000.mp4`, `VID-20240101-WA0001.mp4`, `video.mp4`, a bare number or a hash.
     * A release name such as `1917.2019.1080p.mkv` carries words or tags and is never one.
     */
    fun isGenericName(fileName: String): Boolean {
        val base = fileName.trim().substringBeforeLast('.').trim().lowercase()
        if (base.isEmpty()) return true
        return GENERIC_NAMES.any { it.matches(base) }
    }

    /**
     * The first line of the caption that is not the title already, for a second line under it.
     * Null when the caption has nothing more to say.
     */
    fun captionLine(item: MediaItem, repeated: Set<String> = emptySet()): String? =
        item.caption.lineSequence().map { it.trim() }
            .firstOrNull {
                it.isNotEmpty() && !it.equals(item.title.trim(), ignoreCase = true) && it.lowercase() !in repeated
            }

    /**
     * Caption lines a chat puts under most of its posts, lowercased: a channel's signature, a
     * "New every Friday", a quality note. Shown under every row they would read as part of each
     * video and tell none of them apart, so [captionLine] skips them. A line counts once it is on
     * at least three posts and on at least [REPEAT_SHARE] of the ones listed.
     */
    fun repeatedCaptionLines(items: List<MediaItem>): Set<String> {
        if (items.size < MIN_REPEATS) return emptySet()
        val counts = HashMap<String, Int>()
        for (item in items) {
            item.caption.lineSequence().map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
                .forEach { counts[it] = (counts[it] ?: 0) + 1 }
        }
        val needed = maxOf(MIN_REPEATS, (items.size * REPEAT_SHARE).toInt())
        return counts.filterValues { it >= needed }.keys
    }

    private const val MIN_REPEATS = 3
    private const val REPEAT_SHARE = 0.4

    /**
     * The caption with the channel's boilerplate taken off and kept short: it is read for an
     * episode and shown as a synopsis, never as a whole post.
     *
     * A line that is nothing but links, @mentions and #hashtags, or one of those behind "Join" or
     * "Subscribe", is the channel advertising itself under every post. It says nothing about the
     * video, and as a synopsis or a title it would be the same line on every tile.
     */
    internal fun captionOf(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return text.lineSequence()
            .map { it.trimEnd() }
            .filterNot { isBoilerplate(it) }
            .joinToString("\n")
            .replace(BLANK_RUN, "\n\n")
            .trim()
            .take(CAPTION_MAX)
    }

    private fun isBoilerplate(line: String): Boolean {
        if (line.isBlank() || !LINKISH.containsMatchIn(line)) return false
        val rest = line.replace(LINKISH, " ").replace(NOT_A_LETTER, " ").trim().lowercase()
        if (rest.isEmpty()) return true
        return rest.split(' ').filter { it.isNotEmpty() }.all { it in CALL_WORDS }
    }

    private const val TITLE_MAX = 120

    private val GENERIC_NAMES = listOf(
        // Camera rolls, Telegram's own saves and screen recorders: a prefix, then the date and time.
        Regex("""(vid|img|mov|pxl|mvimg|video|movie|clip|file|document|telegram|tg|record|recording|screen[ _-]?record(ing)?|screenrecorder|wa)[ _-]*\d[\d _.:-]*(wa\d+)?([ _-]?\(\d+\))?"""),
        // "video", "video (2)", "untitled".
        Regex("""(video|vid|movie|clip|file|untitled|new video)([ _-]*\(?\d+\)?)?"""),
        // A number and nothing else, or a date and time and nothing else.
        Regex("""[\d _.:-]+"""),
        // A hash or a UUID.
        Regex("""[0-9a-f]{16,}|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"""),
    )

    private val LINKISH = Regex("""(https?://\S+|www\.\S+|t\.me/\S+|telegram\.me/\S+|@[A-Za-z0-9_]{4,}|#\S+)""")
    private val NOT_A_LETTER = Regex("""[^\p{L}\p{N} ]+""")
    private val BLANK_RUN = Regex("""\n{3,}""")

    /** The words a channel puts in front of its own link; a line of only these and a link is an ad. */
    private val CALL_WORDS = setOf(
        "join", "us", "subscribe", "follow", "share", "channel", "our", "for", "more", "via", "by",
        "from", "on", "telegram", "main", "backup", "group", "updates", "official", "here", "click",
        "visit", "and", "to", "the", "now", "link", "links",
    )

    private const val CAPTION_MAX = 600

    /** TDLib reports 0 for a file it has never touched, but usually knows the expected size. */
    private fun fileSize(size: Long, expectedSize: Long) = if (size > 0) size else expectedSize

    private fun onDevice(file: dev.g000sha256.tdl.dto.File): Boolean {
        val diskFile = file.local.path.takeIf { it.isNotBlank() }?.let(::File)
        return LocalFilePolicy.evaluate(
            downloadCompleted = file.local.isDownloadingCompleted,
            pathPresent = diskFile != null,
            regularFile = diskFile?.isFile == true,
            length = diskFile?.length() ?: 0,
            size = file.size,
            expectedSize = file.expectedSize,
        ) == LocalFileAvailability.Complete
    }

    fun formatSize(bytes: Long): String = L.messages.formatter.size(bytes)

    fun formatDuration(seconds: Int): String = L.messages.formatter.duration(seconds.toLong())
}
