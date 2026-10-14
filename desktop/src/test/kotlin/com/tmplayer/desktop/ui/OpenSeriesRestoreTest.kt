package com.tmplayer.desktop.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.runComposeUiTest
import com.tmplayer.data.MediaItem
import com.tmplayer.data.Series
import com.tmplayer.data.SeriesEpisode
import com.tmplayer.data.SeriesSeason
import com.tmplayer.ui.browse.OpenSeries
import com.tmplayer.ui.browse.RESTORE_WAIT_MS
import com.tmplayer.ui.browse.rememberOpenSeries
import org.junit.Test

/**
 * A show's page survives the activity being made again, as it is when a phone turns: the open show
 * is saved by its key and chat, found again once the screen has loaded its shows, held from then on,
 * and forgotten when it never turns up.
 */
@OptIn(ExperimentalTestApi::class)
class OpenSeriesRestoreTest {

    private fun item(chatId: Long, id: Long) = MediaItem(
        chatId = chatId, messageId = id, fileId = id.toInt(), title = "Loki S01E0$id", sizeBytes = 1,
        durationSec = 2634, mimeType = "video/x-matroska", thumbnailFileId = 0, miniThumbnail = null,
        date = id.toInt(), fileName = "Loki.S01E0$id.mkv",
    )

    private fun show(key: String, title: String, chatId: Long) =
        Series(key, title, listOf(SeriesSeason(1, listOf(SeriesEpisode(item(chatId, 1), 1, 1)))))

    private val loki = show("loki", "Loki", chatId = 7)
    private val other = show("loki", "Loki elsewhere", chatId = 8)

    @Test
    fun `the open show comes back after a recreate, once the rows are loaded again`() = runComposeUiTest {
        var rows by mutableStateOf(listOf(other, loki))
        lateinit var open: OpenSeries
        val restore = Recreator(this)
        restore.setContent {
            open = rememberOpenSeries { key, chat -> rows.firstOrNull { it.key == key && it.cover.chatId == chat } }
            Text(open.series?.title ?: "closed")
        }
        onNodeWithText("closed").assertExists()
        runOnIdle { open.open(loki) }
        onNodeWithText("Loki").assertExists()

        // Made again before the rows have loaded: nothing open yet, and nothing else opened.
        rows = emptyList()
        restore.recreate()
        onNodeWithText("closed").assertExists()

        // The rows come back: the same show in the same chat, not its namesake in another.
        rows = listOf(other, loki)
        waitForIdle()
        onNodeWithText("Loki").assertExists()

        // Held once found: a reload that empties the rows does not close the page.
        rows = emptyList()
        waitForIdle()
        onNodeWithText("Loki").assertExists()

        runOnIdle { open.open(null) }
        restore.recreate()
        rows = listOf(loki)
        waitForIdle()
        onNodeWithText("closed").assertExists()
    }

    @Test
    fun `a show that never turns up is forgotten rather than opened late`() = runComposeUiTest {
        var rows by mutableStateOf(listOf(loki))
        lateinit var open: OpenSeries
        val restore = Recreator(this)
        restore.setContent {
            open = rememberOpenSeries { key, chat -> rows.firstOrNull { it.key == key && it.cover.chatId == chat } }
            Text(open.series?.title ?: "closed")
        }
        runOnIdle { open.open(loki) }
        rows = emptyList()
        restore.recreate()
        mainClock.advanceTimeBy(RESTORE_WAIT_MS + 1_000)
        rows = listOf(loki)
        waitForIdle()
        onNodeWithText("closed").assertExists()
    }

    /**
     * The activity made again, as far as saved state goes: what the content saved is kept, the
     * content is thrown away and composed anew from it. Compose's own StateRestorationTester is
     * not implemented on the desktop.
     */
    private class Recreator(private val test: ComposeUiTest) {
        private var registry by mutableStateOf(SaveableStateRegistry(null) { true })
        private var alive by mutableStateOf(true)

        fun setContent(content: @Composable () -> Unit) = test.setContent {
            // Not key(): the saved values are filed under where they sit in the composition, and a
            // key would move them.
            if (alive) CompositionLocalProvider(LocalSaveableStateRegistry provides registry, content = content)
        }

        fun recreate() {
            test.runOnIdle {
                val saved = registry.performSave()
                registry = SaveableStateRegistry(saved) { true }
                alive = false
            }
            test.waitForIdle()
            test.runOnIdle { alive = true }
            test.waitForIdle()
        }
    }
}
