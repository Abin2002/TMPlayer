package com.tmplayer.desktop.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import com.tmplayer.data.DetailAction
import com.tmplayer.data.MediaItem
import com.tmplayer.data.SeriesShelf
import com.tmplayer.data.ShelfEntry
import com.tmplayer.data.WatchPoint
import com.tmplayer.online.MetaQuery
import com.tmplayer.online.MetaResult
import com.tmplayer.online.OnlineMetadata
import com.tmplayer.ui.browse.DetailRow
import com.tmplayer.ui.browse.LocalSeriesWatch
import com.tmplayer.ui.browse.MediaDetailScreen
import com.tmplayer.ui.browse.SeriesPanel
import com.tmplayer.ui.browse.SeriesWatch
import com.tmplayer.ui.icons.TmIcons
import com.tmplayer.ui.theme.TmMaterialTheme
import com.tmplayer.ui.theme.Tone
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface as SkSurface
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The title pages after the cinematic refresh (WP4, CP60): a video's detail page and a show's page,
 * tall as on a phone held upright and wide as on a phone on its side, in both themes, with and
 * without posters and overviews. With TMPLAYER_SHOTS=<dir> the frames are written there.
 */
class TitlePagesRenderTest {

    private fun item(id: Long, chatId: Long, name: String, colour: Int, date: Int = 1_790_000_000, mini: Boolean = true) = MediaItem(
        chatId = chatId, messageId = id, fileId = 0, title = name, sizeBytes = 885L * 1024 * 1024,
        durationSec = 1_496 + (id % 10).toInt() * 120, mimeType = "video/x-matroska", thumbnailFileId = 0,
        miniThumbnail = if (mini) jpeg(colour) else null, date = date, fileName = name, width = 1920, height = 1080,
    )

    private val bunny = item(500, 2, "Big.Buck.Bunny.2008.1080p.BluRay.x264.mkv", 0xFF46A758.toInt())
    private val episodes = (1..8).map { n ->
        item(600L + n, 4, "Harbour.Lights.S0${if (n > 5) 2 else 1}E%02d.1080p.WEB-DL.mkv".format(if (n > 5) n - 5 else n), 0xFF2AABEE.toInt(), 1_790_000_000 + n, mini = n != 3)
    }
    /** The same film in two more copies, and the first three episodes again in 720p. */
    private val bunnyCopies = listOf(
        bunny,
        item(501, 2, "Big.Buck.Bunny.2008.720p.BluRay.x264.mkv", 0xFF46A758.toInt()).copy(sizeBytes = 610L * 1024 * 1024, width = 1280, height = 720),
        item(502, 2, "Big.Buck.Bunny.2008.2160p.HEVC.mkv", 0xFF46A758.toInt()).copy(sizeBytes = 2_900L * 1024 * 1024, width = 3840, height = 2160),
    )
    private val bunnyFolded = bunny.copy(versions = bunnyCopies)
    private val smallEpisodes = (1..3).map { n ->
        item(650L + n, 4, "Harbour.Lights.S01E%02d.720p.WEB-DL.mkv".format(n), 0xFF2AABEE.toInt(), 1_780_000_000 + n)
            .copy(sizeBytes = 350L * 1024 * 1024, width = 1280, height = 720)
    }
    private val lecture = item(700, 9, "Lecture_07_-_Compilers_and_parsers.mp4", 0xFFF5A524.toInt(), mini = false)

    private fun rows(resume: Boolean): List<DetailRow> = listOfNotNull(
        if (resume) DetailRow(DetailAction.Resume, "Resume 12:04", TmIcons.Play, onSelect = {}) else DetailRow(DetailAction.Play, "Play", TmIcons.Play, onSelect = {}),
        DetailRow(DetailAction.StartOver, "Start over", TmIcons.Refresh, onSelect = {}).takeIf { resume },
        DetailRow(DetailAction.MarkWatched, "Mark as watched", TmIcons.Check, onSelect = {}),
        DetailRow(DetailAction.AddWatchLater, "Add to Watch later", TmIcons.Clock, onSelect = {}),
        DetailRow(DetailAction.Download, "Download", TmIcons.Download, onSelect = {}),
    )

    private val watch = SeriesWatch(
        point = { if (it.messageId == 602L) WatchPoint(600_000, 1_600_000) else null },
        finished = { it.messageId == 601L },
    )

    @Test
    fun detailPages() = kotlinx.coroutines.runBlocking {
        val online = MetaFixture.install("on")
        try {
            warm(online, bunny, episodes[4])
            for (dark in listOf(true, false)) {
                val theme = if (dark) "dark" else "light"
                save("detail-film-tall-$theme.png", render(412, 900, dark) {
                    MediaDetailScreen(bunny, "Film Club", WatchPoint(726_000, 1_700_000), false, rows(resume = true), onClose = {})
                })
                save("detail-film-wide-$theme.png", render(960, 540, dark) {
                    MediaDetailScreen(bunny, "Film Club", null, false, rows(resume = false), onClose = {})
                })
                save("detail-versions-tall-$theme.png", render(412, 1100, dark) {
                    MediaDetailScreen(bunnyFolded, "Film Club", null, false, rows(resume = false), onClose = {}, onOpenItem = {})
                })
                save("detail-versions-wide-$theme.png", render(960, 540, dark) {
                    MediaDetailScreen(bunnyFolded, "Film Club", null, false, rows(resume = false), onClose = {}, onOpenItem = {})
                })
            }
            // The top end action (Cast, CP73) in one row with Close, on and off, upright and sideways.
            for ((active, label) in listOf(false to "off", true to "on")) {
                save("detail-cast-$label-tall.png", render(412, 900, true) {
                    MediaDetailScreen(bunny, "Film Club", null, false, rows(resume = false), onClose = {}, topEndAction = {
                        com.tmplayer.ui.browse.PageDiscButton(TmIcons.Cast, "Cast to a TV", {}, active = active, prominent = true)
                    })
                })
                save("detail-cast-$label-wide.png", render(960, 540, false) {
                    MediaDetailScreen(bunny, "Film Club", null, false, rows(resume = false), onClose = {}, topEndAction = {
                        com.tmplayer.ui.browse.PageDiscButton(TmIcons.Cast, "Cast to a TV", {}, active = active, prominent = true)
                    })
                })
            }
            save("detail-episode-tall.png", render(412, 1400, true) {
                MediaDetailScreen(episodes[4], "Weekend series", null, true, rows(resume = false), onClose = {})
            })
            MetaFixture.install("off")
            for (dark in listOf(true, false)) {
                val theme = if (dark) "dark" else "light"
                save("detail-lecture-tall-$theme.png", render(412, 900, dark) {
                    MediaDetailScreen(lecture, "CS Lectures", null, false, rows(resume = false), onClose = {})
                })
                save("detail-noart-wide-$theme.png", render(960, 540, dark) {
                    MediaDetailScreen(lecture, "CS Lectures", null, false, rows(resume = false), onClose = {})
                })
                save("detail-thumb-tall-$theme.png", render(412, 900, dark) {
                    MediaDetailScreen(episodes[1], "Weekend series", null, false, rows(resume = false), onClose = {})
                })
            }
        } finally {
            OnlineMetadata.current = null
        }
    }

    @Test
    fun seriesPages() = kotlinx.coroutines.runBlocking {
        val show = (SeriesShelf.arrange(episodes + smallEpisodes).first() as ShelfEntry.Show).series
        val online = MetaFixture.install("on")
        try {
            warm(online, episodes[4])
            for (dark in listOf(true, false)) {
                val theme = if (dark) "dark" else "light"
                save("series-meta-tall-$theme.png", render(412, 1200, dark) { Series(show) })
                save("series-meta-wide-$theme.png", render(960, 540, dark) { Series(show) })
            }
            MetaFixture.install("off")
            for (dark in listOf(true, false)) {
                val theme = if (dark) "dark" else "light"
                save("series-plain-tall-$theme.png", render(412, 1200, dark) { Series(show) })
                save("series-plain-wide-$theme.png", render(960, 540, dark) { Series(show) })
            }
        } finally {
            OnlineMetadata.current = null
        }
    }

    @Composable
    private fun Series(show: com.tmplayer.data.Series) {
        SeriesPanel(
            show,
            watch,
            onPlay = {},
            onDownload = { },
            chatTitle = "Weekend series",
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            modifier = Modifier.fillMaxSize(),
        )
    }

    private suspend fun warm(online: OnlineMetadata, vararg items: MediaItem) {
        for (it in items) {
            val q = MetaQuery.of(it.fileName)!!
            online.lookup(q)
            val info = (online.lookup(q.showOnly()) as? MetaResult.Found)?.info
            if (info != null) {
                online.extras(info)
                for (url in listOfNotNull(info.posterUrl, info.backdropUrl, info.wideUrl)) {
                    for (width in listOf(240, 342, 480, 1280)) com.tmplayer.data.Thumbnails.online(url, width) { online.image(url) }
                }
            }
        }
    }

    private fun render(width: Int, height: Int, dark: Boolean, page: @Composable () -> Unit): ByteArray =
        ImageComposeScene(width, height, Density(1f)) {
            TmMaterialTheme(dark = dark) {
                CompositionLocalProvider(LocalSeriesWatch provides watch) {
                    Surface(Modifier.fillMaxSize(), color = Tone.background) { page() }
                }
            }
        }.use { scene ->
            scene.render(0)
            Thread.sleep(500)
            scene.render(500_000_000)
            Thread.sleep(300)
            scene.render(1_000_000_000)
            scene.render(2_000_000_000).encodeToData(EncodedImageFormat.PNG)!!.bytes
        }

    private fun save(name: String, png: ByteArray) {
        assertTrue(png.size > 1000)
        val dir = System.getenv("TMPLAYER_SHOTS")?.takeIf { it.isNotBlank() } ?: return
        File(dir).apply { mkdirs() }.resolve(name).writeBytes(png)
    }

    /** A tiny JPEG, a gradient in [argb], standing in for Telegram's minithumbnail. */
    private fun jpeg(argb: Int): ByteArray {
        val surface = SkSurface.makeRasterN32Premul(40, 24)
        surface.canvas.drawRect(Rect.makeWH(40f, 24f), Paint().apply { color = argb })
        surface.canvas.drawRect(Rect.makeXYWH(0f, 14f, 40f, 10f), Paint().apply { color = Color.makeRGB(16, 20, 24) })
        surface.canvas.drawRect(Rect.makeXYWH(24f, 2f, 10f, 8f), Paint().apply { color = Color.makeRGB(240, 220, 160) })
        return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.JPEG, 80)!!.bytes
    }
}
