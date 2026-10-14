package com.tmplayer.desktop.promo

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import com.tmplayer.data.AuthState
import com.tmplayer.data.ChatKind
import com.tmplayer.data.ChatFolderSummary
import com.tmplayer.data.ChatSummary
import com.tmplayer.data.DownloadRequest
import com.tmplayer.data.DownloadRunner
import com.tmplayer.data.MediaItem
import com.tmplayer.data.SettingsStore
import com.tmplayer.data.UpdateScheduler
import com.tmplayer.data.WatchedRecord
import com.tmplayer.data.WatchedStore
import com.tmplayer.data.SeriesShelf
import com.tmplayer.data.ShelfEntry
import com.tmplayer.desktop.ui.SeriesPage
import com.tmplayer.promo.PromoCatalog
import com.tmplayer.desktop.ui.rememberSeriesWatch
import com.tmplayer.desktop.DesktopPrefs
import com.tmplayer.desktop.DesktopWatchCache
import com.tmplayer.desktop.player.AbLoop
import com.tmplayer.desktop.player.Episodes
import com.tmplayer.desktop.player.MediaTrack
import com.tmplayer.desktop.player.OpenPrefs
import com.tmplayer.desktop.player.PlaybackEngine
import com.tmplayer.desktop.player.PlaybackStatus
import com.tmplayer.desktop.player.PlayerMedia
import com.tmplayer.desktop.player.PlayerScreen
import com.tmplayer.desktop.player.TrackType
import com.tmplayer.desktop.ui.ChatAvatar
import com.tmplayer.desktop.ui.ChatList
import com.tmplayer.desktop.ui.DesktopExtras
import com.tmplayer.desktop.ui.PageHeader
import com.tmplayer.desktop.ui.PosterSizeStep
import com.tmplayer.desktop.ui.SearchField
import com.tmplayer.desktop.ui.ShellState
import com.tmplayer.desktop.ui.Sidebar
import com.tmplayer.desktop.ui.SignInScreen
import com.tmplayer.desktop.ui.VideoGrid
import com.tmplayer.desktop.ui.rememberKeyboardNav
import com.tmplayer.desktop.ui.ListSurface
import com.tmplayer.i18n.Languages
import com.tmplayer.i18n.Translator
import com.tmplayer.ui.icons.TmIcons
import com.tmplayer.ui.onboarding.SetupChoice
import com.tmplayer.ui.onboarding.SetupScreen
import com.tmplayer.ui.onboarding.SetupState
import com.tmplayer.ui.onboarding.SetupStep
import com.tmplayer.ui.onboarding.setupImageName
import com.tmplayer.ui.theme.TmMaterialTheme
import com.tmplayer.ui.theme.Tone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image as SkImage
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openani.mediamp.source.MediaData
import org.openani.mediamp.source.UriMediaData
import java.io.File
import java.nio.file.Files

/**
 * The desktop's promo fixture, the counterpart of the Android `app/src/promo/`: the real sign in,
 * chat list, chat grid and player, drawn off screen over the demo library both fixtures share
 * ([PromoCatalog]: invented films, shows, a course and clips, with the promo build's own art), so
 * nothing on screen belongs to anybody. Each is drawn light and
 * dark at 1280x800 dp and a density of 1.5, which is a 1920x1200 picture.
 *
 * As a test it only checks that every screen draws. With TMPLAYER_PROMO=<dir> it also writes the
 * PNGs there:
 *
 *     TMPLAYER_PROMO=/tmp/promo ./gradlew :desktop:test --tests '*PromoShots*' -PdesktopVersion=1.22.1
 *
 * (the version is the one the sidebar shows beside the logo; without it, a shot says 1.0.0-dev).
 *
 * They become the README and site screenshots (`desktop-<shot>[-light].webp`) and the desktop
 * setup's pictures (`ui/src/jvmMain/resources/onboarding/`, cropped and marked from the `setup-*`
 * shots by `scripts/annotate-shot.py`), each converted to WebP at quality 88 as CLAUDE.md says. The sign in QR code encodes a made-up token that signs nobody in.
 */
class PromoShots {

    private val dir = Files.createTempDirectory("tm-promo").toFile()
    private val settings = SettingsStore(SettingsStore.openDataStore(dir.resolve(SettingsStore.FILE_NAME)))
    private val prefs = DesktopPrefs(dir.resolve("desktop.properties"))
    private val shell = ShellState(
        settings,
        object : DownloadRunner {
            override fun download(request: DownloadRequest) = Unit
            override fun cancel(fileId: Int) = Unit
            override fun pause(fileId: Int) = Unit
        },
        WatchedStore(WatchedStore.openDataStore(dir.resolve(WatchedStore.FILE_NAME))),
        services = {
            DesktopExtras(
                prefs = prefs,
                updates = UpdateScheduler(settings.updatePrefs, check = { _, _ -> false }, onSkip = {}),
                watchCache = DesktopWatchCache(settings, { dir.resolve("cache") }),
            )
        },
    )

    private val pictures = PromoCatalog.Pictures { name -> File(DEMO_DIR, "$name.webp").takeIf { it.exists() }?.readBytes() }
    private val library = PromoCatalog.Library(pictures)

    private val chats = PromoCatalog.chats.map { chat ->
        ChatSummary(chat.id, chat.title, chat.avatar?.let(pictures::bytes), 0, chat.kind, unreadCount = UNREAD[chat.id] ?: 0, isPinned = chat.id == PromoCatalog.CINEMA)
    }

    private val cinema = chats.first { it.id == PromoCatalog.CINEMA }

    /** The demo's posters and overviews, over the shared canned TMDB, warmed so a frame shows them rather than the wait. */
    private fun installMeta(): com.tmplayer.online.OnlineMetadata {
        val dir = Files.createTempDirectory("tm-promo-meta").toFile()
        val store = com.tmplayer.online.MetadataStore(File(dir, "metadata.properties"))
        store.update { it.copy(enabled = true) }
        val online = com.tmplayer.online.OnlineMetadata(
            buildKey = "promo-key",
            store = store,
            cache = com.tmplayer.online.MetadataCache(File(dir, "cache")),
            appVersion = "promo",
            http = PromoCatalog.Canned(pictures),
        )
        com.tmplayer.online.OnlineMetadata.current = online
        runBlocking {
            for (item in library.all) {
                val query = com.tmplayer.online.MetaQuery.of(item.fileName) ?: continue
                val found = online.lookup(query.showOnly())
                (found as? com.tmplayer.online.MetaResult.Found)?.info?.let { info ->
                    listOfNotNull(info.posterUrl, info.backdropUrl).forEach { url -> online.image(url) }
                }
            }
        }
        return online
    }

    @Test
    fun signIn() = both("signin") {
        SignInScreen(AuthState.Qr("tg://login?token=TMPlayerPromoFixtureSignsNobodyIn"))
    }

    @Test
    fun chats() = both("chats", before = { shell.showChats(shell.chatSection) }) {
        withSidebar {
            val list = rememberLazyListState()
            val nav = rememberKeyboardNav(remember(list) { ListSurface(list) })
            val section = shell.chatSection
            Column(Modifier.fillMaxSize()) {
                PageHeader(
                    title = section.heading,
                    subtitle = section.blurb,
                    actions = {
                        SearchField("", {}, "Search chats", shell.searchFocus, Modifier.width(320.dp))
                        IconButton(onClick = {}) { Icon(TmIcons.Refresh, contentDescription = "Refresh") }
                    },
                )
                ChatList(chats, favourites = library.favourites, listState = list, nav = nav, onOpen = {}, onStar = {})
            }
        }
    }

    /**
     * Cinema Lounge as its page draws it: the films and the show first as one block of 2:3
     * posters (copies of one film folded into one), each with its poster from the canned TMDB.
     */
    @Test
    fun grid() {
        installMeta()
        try {
            both("grid", before = { seedWatching(); shell.showChats(shell.chatSection) }) {
                withSidebar {
                    Column(Modifier.fillMaxSize()) {
                        PageHeader(
                            title = cinema.title,
                            subtitle = "${library.cinema.size} videos",
                            leading = {
                                IconButton(onClick = {}) { Icon(TmIcons.ArrowBack, contentDescription = "Back to chats") }
                                ChatAvatar(cinema.miniThumbnail, 0, cinema.title, 40.dp)
                            },
                            actions = {
                                SearchField("", {}, "Search this chat", shell.searchFocus, Modifier.width(300.dp))
                                PosterSizeStep(shell)
                                IconButton(onClick = {}) { Icon(TmIcons.Refresh, contentDescription = "Refresh") }
                            },
                        )
                        val arranged = remember { SeriesShelf.arrange(library.cinema) }
                        val posters = arranged.filter(SeriesShelf::isPoster)
                        val cells = (posters + arranged.filterNot(SeriesShelf::isPoster)).map {
                            if (it is ShelfEntry.Show) it.series.cover else (it as ShelfEntry.File).item
                        }
                        VideoGrid(
                            shell,
                            cells,
                            cinema.title,
                            series = arranged.filterIsInstance<ShelfEntry.Show>().associate { it.series.cover.id to it.series },
                            posters = posters.mapTo(HashSet()) { if (it is ShelfEntry.Show) it.series.cover.id else (it as ShelfEntry.File).item.id },
                        )
                    }
                }
            }
        } finally {
            com.tmplayer.online.OnlineMetadata.current = null
        }
    }

    /** The demo's shows (and the films after them), every file on its own as a chat lists them. */
    private val seriesVideos: List<MediaItem> = library.cinema + library.anime + library.nature

    /** Where the demo viewer is in the library: see [PromoCatalog.Library]. */
    private fun seedWatching() = runBlocking {
        library.all.filter(library::isFinished).forEach {
            shell.watched.markWatched(
                WatchedRecord(it.chatId, it.messageId, 0, it.title, PromoCatalog.chatTitle(it.chatId), it.sizeBytes, it.durationSec, 1L, manual = true),
            )
        }
        library.continueWatching.forEach { settings.saveResumePosition(it.chatId, it.messageId, it.positionMs, it.durationMs) }
    }

    @Composable
    private fun chatPage(subtitle: String, body: @Composable () -> Unit) {
        withSidebar {
            Column(Modifier.fillMaxSize()) {
                PageHeader(
                    title = cinema.title,
                    subtitle = subtitle,
                    leading = {
                        IconButton(onClick = {}) { Icon(TmIcons.ArrowBack, contentDescription = "Back to chats") }
                        ChatAvatar(cinema.miniThumbnail, 0, cinema.title, 40.dp)
                    },
                    actions = {
                        SearchField("", {}, "Search this chat", shell.searchFocus, Modifier.width(300.dp))
                        PosterSizeStep(shell)
                        IconButton(onClick = {}) { Icon(TmIcons.Refresh, contentDescription = "Refresh") }
                    },
                )
                body()
            }
        }
    }

    /** A show opened: the season dropdown, the play button and the episode list. */
    @Test
    fun seriesOpen() = both("series-open", before = ::seedWatching) {
        val show = (SeriesShelf.arrange(seriesVideos).first { it is ShelfEntry.Show && it.series.key == "harbour lines" } as ShelfEntry.Show).series
        chatPage("${seriesVideos.size} videos") { SeriesPage(shell, show, rememberSeriesWatch(shell), onClose = {}) }
    }

    /** The season dropdown open over the episodes. */
    @Test
    fun seriesDropdown() = both("series-dropdown", before = ::seedWatching, darkOnly = true) {
        val show = (SeriesShelf.arrange(seriesVideos).first { it is ShelfEntry.Show && it.series.key == "harbour lines" } as ShelfEntry.Show).series
        chatPage("${seriesVideos.size} videos") { SeriesPage(shell, show, rememberSeriesWatch(shell), onClose = {}, startOpen = true) }
    }

    @Test
    fun player() = both("player", before = {
        // Controls that never hide, so the shot has them up however long the frame takes.
        runBlocking { settings.updateTouchPrefs { it.copy(controlsTimeoutMs = 0L) } }
    }) {
        val frame = remember { SkImage.makeFromEncoded(demo("promo_quiet_orbit_backdrop")).toComposeImageBitmap() }
        Box(Modifier.fillMaxSize()) {
            // libmpv draws the picture under the window's Compose layer; the fixture puts a still there.
            Image(frame, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            PlayerScreen(
                // Named as a person would name the file, which is what the player's title shows.
                media = PromoMedia(library.quietOrbit),
                startFromBeginning = false,
                onBack = {},
                fullscreen = false,
                onToggleFullscreen = {},
                settings = settings,
                prefs = prefs,
                engineFactory = { StillEngine() },
                backdrop = androidx.compose.ui.graphics.Color.Transparent,
            )
        }
    }

    @org.junit.After
    fun forgetOnline() {
        com.tmplayer.online.OnlineSubtitles.current = null
    }

    /** The subtitle menu with "Search online" in it, in a build that has the OpenSubtitles key. */
    @Test
    fun playerSubtitleMenu() = both("player-subtitles-online", darkOnly = true, before = {
        runBlocking { settings.updateTouchPrefs { it.copy(controlsTimeoutMs = 0L) } }
        com.tmplayer.desktop.ui.OnlineFixture.install("signed_in")
    }) {
        val frame = remember { SkImage.makeFromEncoded(demo("promo_quiet_orbit_backdrop")).toComposeImageBitmap() }
        Box(Modifier.fillMaxSize()) {
            Image(frame, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            PlayerScreen(
                media = PromoMedia(library.quietOrbit),
                startFromBeginning = false,
                onBack = {},
                fullscreen = false,
                onToggleFullscreen = {},
                settings = settings,
                prefs = prefs,
                engineFactory = { StillEngine() },
                backdrop = androidx.compose.ui.graphics.Color.Transparent,
                menuOpen = "Subtitles",
            )
        }
    }

    /**
     * Home over the demo chats: Continue watching, the starred chats (films, a show, an anime, a
     * course and a nature series) and Recently added, without and with posters and overviews.
     * Each at 1280 and 1920 dp wide, light and dark.
     */
    @Test
    fun home() = homeShots("home", meta = false)

    @Test
    fun homeMeta() = homeShots("home-meta", meta = true)

    private fun homeShots(name: String, meta: Boolean) {
        if (meta) installMeta()
        try {
            val rows = com.tmplayer.data.HomeRows.build(
                continueWatching = library.continueWatching,
                favourites = com.tmplayer.data.HomeRows.favouriteChats(chats, library.favourites),
                loaded = library.byChat.filterKeys { it in library.favourites },
                recent = library.all,
                watchLater = library.watchLater,
                fresh = library.newEpisodes,
            )
            for ((width, suffix) in listOf(WIDTH to "", WIDE to "-1920")) {
                both("$name$suffix", width = width, before = ::seedWatching) {
                    withSidebar {
                        com.tmplayer.desktop.ui.HomeRowsView(
                            shell, rows, chats, library.art, onRowShown = {}, onArtWanted = {}, onRefresh = {},
                        )
                    }
                }
            }
        } finally {
            com.tmplayer.online.OnlineMetadata.current = null
        }
    }

    /** The setup after sign in (CP67) as the desktop shows it, step by step; only checked, not shipped. */
    @Test
    fun setup() {
        for (step in SetupStep.entries) {
            both("setup-${step.name.lowercase()}") {
                SetupScreen(settings, remember { SetupState(SetupStep.entries.toList(), start = step.ordinal) }, onDone = {})
            }
        }
    }

    /** The same in the en-XA pseudo-locale: longer, accented text, to see what a translation will do to it. */
    @Test
    fun setupPseudo() {
        Translator.use(Languages.PSEUDO)
        try {
            for (step in SetupStep.entries) {
                both("setup-xa-${step.name.lowercase()}", darkOnly = true) {
                    SetupScreen(settings, remember { SetupState(SetupStep.entries.toList(), start = step.ordinal) }, onDone = {})
                }
            }
        } finally {
            Translator.use(Languages.ENGLISH)
        }
    }

    /**
     * The raw shots the setup's library pictures are cut from: the side bar of an account with two
     * folders, with Chats and Saved Messages, and with only the folders.
     */
    @Test
    fun setupLibrary() {
        for (hidden in listOf(false, true)) {
            both(if (hidden) "setup-raw-library-folders" else "setup-raw-library-everything", before = {
                shell.hideDefaultGroups = hidden
                // Everything opens on Chats, with Chats and Saved Messages in view; only the folders
                // opens on a folder, which is where that side bar starts.
                shell.showChats(if (hidden) com.tmplayer.ui.browse.BrowseSection.Folder(1, "Films") else com.tmplayer.ui.browse.BrowseSection.of(com.tmplayer.ui.browse.BrowseTab.Chats))
            }) {
                Row(Modifier.fillMaxSize()) {
                    Sidebar(shell, null, SETUP_FOLDERS)
                    VerticalDivider(color = Tone.outline)
                }
            }
        }
        shell.hideDefaultGroups = false
    }

    /** The raw shots of the posters step: Cinema Lounge with posters looked up, and without. */
    @Test
    fun setupPosters() {
        for (on in listOf(true, false)) {
            if (on) installMeta()
            try {
                both(if (on) "setup-raw-posters-on" else "setup-raw-posters-off", before = { shell.showChats(shell.chatSection) }) {
                    withSidebar {
                        val arranged = remember { SeriesShelf.arrange(library.cinema) }
                        val posters = if (on) arranged.filter(SeriesShelf::isPoster) else emptyList()
                        val cells = (posters + arranged.filterNot { it in posters }).map {
                            if (it is ShelfEntry.Show) it.series.cover else (it as ShelfEntry.File).item
                        }
                        VideoGrid(
                            shell,
                            cells,
                            cinema.title,
                            series = arranged.filterIsInstance<ShelfEntry.Show>().associate { it.series.cover.id to it.series },
                            posters = posters.mapTo(HashSet()) { if (it is ShelfEntry.Show) it.series.cover.id else (it as ShelfEntry.File).item.id },
                        )
                    }
                }
            } finally {
                com.tmplayer.online.OnlineMetadata.current = null
            }
        }
    }

    @Composable
    private fun withSidebar(page: @Composable () -> Unit) {
        Row(Modifier.fillMaxSize()) {
            Sidebar(shell, null, emptyList())
            VerticalDivider(color = Tone.outline)
            Box(Modifier.weight(1f).fillMaxHeight()) { page() }
        }
    }

    private fun both(name: String, before: () -> Unit = {}, darkOnly: Boolean = false, width: Int = WIDTH, content: @Composable () -> Unit) {
        before()
        for (dark in if (darkOnly) listOf(true) else listOf(true, false)) {
            val png = render(dark, width, content)
            assertTrue("$name drew nothing", png.size > 10_000)
            val out = System.getenv("TMPLAYER_PROMO")?.takeIf { it.isNotBlank() } ?: continue
            File(out).apply { mkdirs() }.resolve(if (dark) "$name.png" else "$name-light.png").writeBytes(png)
        }
    }

    private fun render(dark: Boolean, width: Int = WIDTH, content: @Composable () -> Unit): ByteArray =
        ImageComposeScene(width, HEIGHT, Density(DENSITY)) {
            TmMaterialTheme(dark = dark) {
                Surface(Modifier.fillMaxSize(), color = Tone.background) { content() }
            }
        }.use { scene ->
            scene.render(0)
            // Thumbnails and the QR code are made off the composition thread; give them a moment.
            Thread.sleep(800)
            scene.render(500_000_000)
            Thread.sleep(600)
            scene.render(1_000_000_000)
            // A second pass, so whatever answered during the first (the player's open, the
            // posters) has its fade finished in the frame.
            Thread.sleep(200)
            scene.render(1_600_000_000).encodeToData(EncodedImageFormat.PNG)!!.bytes
        }

    /** A picture from the Android promo build, so both fixtures show the same made-up media. */
    private fun demo(name: String): ByteArray = File(DEMO_DIR, "$name.webp").readBytes()

    /** Plays nothing: opens straight into a paused-free, mid-video state for the controls to show. */
    private class StillEngine : PlaybackEngine {
        private val status = MutableStateFlow(PlaybackStatus())
        override val state: StateFlow<PlaybackStatus> = status
        override val tracks: StateFlow<List<MediaTrack>> = MutableStateFlow(emptyList())
        override suspend fun open(data: MediaData, startAtMs: Long, prefs: OpenPrefs) {
            status.value = PlaybackStatus(
                opened = true, playing = true, durationMs = 7_560_000, positionMs = 4_309_000, bufferedMs = 4_980_000,
                videoWidth = 1920, videoHeight = 1080, volume = 80,
            )
        }
        override fun play() = status.update { it.copy(playing = true) }
        override fun pause() = status.update { it.copy(playing = false) }
        override fun stop() = Unit
        override fun togglePlay() = status.update { it.copy(playing = !it.playing) }
        override fun seekTo(positionMs: Long) = Unit
        override fun seekBy(deltaMs: Long) = Unit
        override fun frameStep(forward: Boolean) = Unit
        override fun setSpeed(speed: Float) = Unit
        override fun setVolume(percent: Int) = Unit
        override fun setMuted(muted: Boolean) = Unit
        override fun selectTrack(type: TrackType, id: Int?) = Unit
        override fun setScale(scale: com.tmplayer.player.VideoScale) = Unit
        override fun setDownmix(stereo: Boolean) = Unit
        override fun setVolumeBoost(on: Boolean) = Unit
        override fun setSubtitleStyle(style: com.tmplayer.player.SubtitleStyle) = Unit
        override fun setSubtitleDelay(ms: Long) = Unit
        override fun setAudioDelay(ms: Long) = Unit
        override fun addSubtitle(path: String): Boolean = false
        override suspend fun screenshot(file: File, withSubtitles: Boolean): Boolean = false
        override fun setAbLoop(loop: AbLoop?) = Unit
        override fun details(): List<Pair<String, String>> = emptyList()
        override fun close() = Unit
    }

    private class PromoMedia(override val item: MediaItem) : PlayerMedia {
        override val chatTitle = PromoCatalog.chatTitle(item.chatId)
        override suspend fun open(): MediaData = UriMediaData("promo.mp4")
        override suspend fun episodes() = Episodes()
        override fun episode(other: MediaItem): PlayerMedia = PromoMedia(other)
        override val downloaded: StateFlow<Float?> = MutableStateFlow(null)
        override suspend fun tdlibVersion(): String? = null
        override fun release() = Unit
        override val fromTelegram: Boolean get() = true
    }

    private companion object {
        const val WIDTH = 1920

        /** Unread counts on the chat list, as a busy account has them. */
        val UNREAD = mapOf(PromoCatalog.ANIME to 3, PromoCatalog.NATURE to 1, PromoCatalog.CLIPS to 12)

        /** 1920 dp across at the same density, for the pages that change with a wide window. */
        const val WIDE = 2880
        const val HEIGHT = 1200
        const val DENSITY = 1.5f

        /** Gradle runs the tests from the module directory. */
        val DEMO_DIR = File("../app/src/promo/res/drawable-nodpi")

        /** The demo account's two Telegram folders, as the setup's library step pictures them. */
        val SETUP_FOLDERS = listOf(ChatFolderSummary(1, "Films"), ChatFolderSummary(2, "Family"))

        init {
            // The setup reads these names; keep the specs in scripts/annotations and the classpath in step.
            check(setupImageName(SetupChoice.Everything, dark = true) == "library-everything-dark.webp")
        }
    }
}
