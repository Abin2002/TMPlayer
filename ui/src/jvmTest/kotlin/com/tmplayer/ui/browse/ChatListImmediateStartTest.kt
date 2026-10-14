package com.tmplayer.ui.browse

import com.tmplayer.data.Account
import com.tmplayer.data.ChatKind
import com.tmplayer.data.ChatSummary
import com.tmplayer.data.SettingsStore
import com.tmplayer.ui.components.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * A signed in cold start, with the main dispatcher running work in place as the desktop's
 * Dispatchers.Main.immediate does: the remembered header and list are painted from inside the
 * constructor, so every field they touch has to exist by then. 1.26.0's desktop crashed here on
 * launch, because the header's flow was declared below the init block.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatListImmediateStartTest {

    private val dir: File = Files.createTempDirectory("chatlist-immediate").toFile()
    private val file = dir.resolve(SettingsStore.FILE_NAME)

    @After fun tearDown() {
        Dispatchers.resetMain()
        dir.deleteRecursively()
    }

    private val chats = listOf(
        ChatSummary(id = 1, title = "Films", miniThumbnail = null, photoFileId = 0, kind = ChatKind.Channel),
    )

    @Test
    fun `a remembered account and list paint on a main dispatcher that runs in place`() {
        val store = SettingsStore(SettingsStore.openDataStore(file))
        runBlocking {
            store.saveAccountSnapshot(Account(name = "Ada", username = "ada", miniThumbnail = null, photoFileId = 0))
            store.saveChatSnapshot(chats)
            // Warm the store, so the reads in the constructor answer without suspending.
            store.cachedAccountSnapshot()
        }
        Dispatchers.setMain(UnconfinedTestDispatcher())

        val model = ChatListViewModel(store)

        runBlocking {
            withTimeout(5_000) {
                assertEquals("Ada", model.accountHeader.first { it != null }?.name)
                val shown = model.state.first { it is UiState.Content }
                assertTrue(shown is UiState.Content)
                assertEquals(listOf(1L), (shown as UiState.Content).value.chats.map { it.id })
            }
        }
    }
}
