package com.tmplayer.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.tmplayer.data.DownloadRequest
import com.tmplayer.data.DownloadRunner
import com.tmplayer.data.MediaItem
import com.tmplayer.data.ResumeRecord
import com.tmplayer.data.SeriesShelf
import com.tmplayer.data.SettingsStore
import com.tmplayer.data.ShelfEntry
import com.tmplayer.data.UpdateScheduler
import com.tmplayer.data.WatchLaterRecord
import com.tmplayer.data.WatchedRecord
import com.tmplayer.data.WatchedStore
import com.tmplayer.desktop.DesktopPrefs
import com.tmplayer.desktop.DesktopWatchCache
import com.tmplayer.ui.components.TileBadge
import com.tmplayer.ui.theme.TmMaterialTheme
import com.tmplayer.ui.theme.Tone
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface as SkSurface
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The art first video tiles (WP2 of the cinematic refresh) on the desktop, in both themes: a chat's
 * grid with release names, files with no picture, a show, the corner badges and a caller's "New",
 * then the grid with keyboard focus on one poster and the pointer resting on another, then the
 * Continue watching, Watched and Watch later pages built from saved records alone. With
 * TMPLAYER_SHOTS=<dir> the frames are written there as `tiles-*.png`.
 */
class TileRenderTest {

    private val settings = SettingsStore(
        SettingsStore.openDataStore(Files.createTempDirectory("tm-tiles").resolve(SettingsStore.FILE_NAME).toFile()),
    )
    private val dir = Files.createTempDirectory("tm-tiles-extras").toFile()
    private val watched = WatchedStore(WatchedStore.openDataStore(Files.createTempDirectory("tm-tiles-watched").resolve(WatchedStore.FILE_NAME).toFile()))
    private val shell = ShellState(
        settings,
        object : DownloadRunner {
            override fun download(request: DownloadRequest) = Unit
            override fun cancel(fileId: Int) = Unit
            override fun pause(fileId: Int) = Unit
        },
        watched,
        services = {
            DesktopExtras(
                prefs = DesktopPrefs(dir.resolve("desktop.properties")),
                updates = UpdateScheduler(settings.updatePrefs, check = { _, _ -> false }, onSkip = {}),
                watchCache = DesktopWatchCache(settings, { dir.resolve("cache") }),
            )
        },
    )

    private val colours = listOf(0xFF2AABEE, 0xFFE5484D, 0xFF46A758, 0xFFF5A524, 0xFF8E4EC6, 0xFF0090FF, 0xFF12A594, 0xFFD6409F)
    private var nextId = 1L

    private fun video(name: String, art: Boolean = true, onDevice: Boolean = false, caption: String = "") = nextId++.let { id ->
        MediaItem(
            chatId = 2, messageId = id, fileId = id.toInt(), title = name,
            sizeBytes = (300L + id * 170L) * 1024 * 1024, durationSec = 1500 + (id * 431 % 3000).toInt(),
            mimeType = "video/mp4", thumbnailFileId = 0,
            miniThumbnail = if (art) jpeg(colours[(id % colours.size).toInt()].toInt()) else null,
            date = (1000 - id).toInt(), fileName = name, onDevice = onDevice, caption = caption,
        )
    }

    private val items = listOf(
        video("Gladiator.II.2024.1080p.WEB-DL.x265.mkv"),
        video("Lecture_07_-_Limits_and_continuity.mp4", art = false),
        video("Harbour.Lights.S02E05.720p.mkv"),
        video("Harbour.Lights.S02E06.720p.mkv"),
        video("Night Train (2019) 1080p x265.mkv", onDevice = true),
        video("Organic Chemistry Chapter 12.mp4", art = false),
        video("Coast walk, day 2.mp4"),
        video("Module 3 Sourdough starter.mp4", art = false),
        video("The.Long.Road.S01E01.1080p.mkv", art = false),
        video("The.Long.Road.S01E02.1080p.mkv", art = false),
        video("Mountains.2021.2160p.HDR.mkv"),
        video("Birthday highlights.mp4", art = false),
        video("Low.Tide.2022.720p.WEB.mkv", art = false),
        // One film in three copies, and a name no lookup will know, so the poster has to read
        // well on its own.
        video("Ponniyin.Selvan.Part.Two.2023.Tamil.1080p.WEB-DL.x265.mkv"),
        video("Ponniyin.Selvan.Part.Two.2023.Tamil.720p.HDRip.x264.mkv"),
        video("Ponniyin.Selvan.Part.Two.2023.Tamil.480p.mkv", art = false),
    )

    @Test
    fun tiles() {
        runBlocking {
            // A part way video, a finished one, and saved records for the History pages.
            val half = items[0]
            settings.saveResumePosition(half.chatId, half.messageId, 1_800_000, half.durationSec * 1000L, describe(half))
            val lecture = items[1]
            settings.saveResumePosition(lecture.chatId, lecture.messageId, 600_000, lecture.durationSec * 1000L, describe(lecture))
            val episode = items[2]
            settings.saveResumePosition(episode.chatId, episode.messageId, 900_000, episode.durationSec * 1000L, describe(episode))
            listOf(items[6], items[11]).forEachIndexed { at, done ->
                watched.markWatched(
                    WatchedRecord(done.chatId, done.messageId, done.fileId, done.title, "Film Club", done.sizeBytes, done.durationSec, 1_000L + at, manual = true),
                )
            }
            listOf(items[10], items[5]).forEach { later ->
                watched.watchLater.add(WatchLaterRecord.of(later, "Film Club", addedAt = later.messageId))
            }
        }
        // As the chat's page lays it out: films and shows first as posters, then the other videos.
        val arranged = SeriesShelf.arrange(items).let { all -> all.filter(SeriesShelf::isPoster) + all.filterNot(SeriesShelf::isPoster) }
        fun cellOf(entry: ShelfEntry) = if (entry is ShelfEntry.Show) entry.series.cover else (entry as ShelfEntry.File).item
        val cells = arranged.map(::cellOf)
        val posters = arranged.filter(SeriesShelf::isPoster).mapTo(HashSet()) { cellOf(it).id }
        val shows = arranged.filterIsInstance<ShelfEntry.Show>().associate { it.series.cover.id to it.series }
        val badged = items[4].id

        for (dark in listOf(true, false)) {
            val theme = if (dark) "dark" else "light"
            save("tiles-grid-$theme.png", render(dark) { Grid(cells, shows, badged, posters) })
            save("tiles-focus-$theme.png", render(dark, hover = Offset(560f, 180f)) { Grid(cells, shows, badged, posters, focus = 1) })
            save("tiles-continue-$theme.png", render(dark) { ContinuePage(shell) })
            save("tiles-watched-$theme.png", render(dark) { WatchedPage(shell) })
            save("tiles-later-$theme.png", render(dark) { WatchLaterPage(shell) })
        }
    }

    @Composable
    private fun Grid(cells: List<MediaItem>, shows: Map<String, com.tmplayer.data.Series>, badged: String, posters: Set<String>, focus: Int? = null) {
        var nav by remember { mutableStateOf<KeyboardNav?>(null) }
        Box(Modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                PageHeader("Film Club", "12 videos")
                VideoGrid(
                    state = shell,
                    items = cells,
                    chatTitle = "Film Club",
                    series = shows,
                    onNav = { nav = it },
                    posters = posters,
                    badge = { item -> if (item.id == badged) ({ TileBadge("New") }) else null },
                )
            }
        }
        LaunchedEffect(nav, focus) { if (focus != null) nav?.focus(focus) }
    }

    private fun describe(item: MediaItem) = ResumeRecord.encode(
        fileId = item.fileId,
        title = item.title,
        chatTitle = "Film Club",
        sizeBytes = item.sizeBytes,
        durationSec = item.durationSec,
        updatedAt = 1_000L + item.messageId,
    )

    private fun render(dark: Boolean, hover: Offset? = null, page: @Composable () -> Unit): ByteArray =
        ImageComposeScene(WIDTH, HEIGHT, Density(1f)) {
            TmMaterialTheme(dark = dark) {
                Surface(Modifier.fillMaxSize(), color = Tone.background) { page() }
            }
        }.use { scene ->
            scene.render(0)
            if (hover != null) scene.sendPointerEvent(PointerEventType.Move, hover)
            // Thumbnails decode off the composition thread, and a hover lifts after a pause.
            Thread.sleep(600)
            scene.render(500_000_000)
            Thread.sleep(200)
            scene.render(1_000_000_000)
            scene.render(2_000_000_000).encodeToData(EncodedImageFormat.PNG)!!.bytes
        }

    private fun save(name: String, png: ByteArray) {
        assertTrue(png.size > 1000)
        val out = System.getenv("TMPLAYER_SHOTS")?.takeIf { it.isNotBlank() } ?: return
        File(out).apply { mkdirs() }.resolve(name).writeBytes(png)
    }

    /** A tiny JPEG, a band of [argb] over a dark foot, standing in for Telegram's minithumbnail. */
    private fun jpeg(argb: Int): ByteArray {
        val surface = SkSurface.makeRasterN32Premul(40, 24)
        surface.canvas.drawRect(Rect.makeWH(40f, 24f), Paint().apply { color = argb })
        surface.canvas.drawRect(Rect.makeXYWH(0f, 14f, 40f, 10f), Paint().apply { color = 0xFF101418.toInt() })
        surface.canvas.drawCircle(28f, 8f, 5f, Paint().apply { color = 0xFFF5D76E.toInt() })
        return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.JPEG, 80)!!.bytes
    }

    private companion object {
        const val WIDTH = 1280
        const val HEIGHT = 820
    }
}
