package com.tmplayer.ui.browse

import com.tmplayer.data.ChatFolderSummary
import com.tmplayer.data.ChatKind
import com.tmplayer.data.ChatSummary
import com.tmplayer.data.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class DefaultGroupsTest {

    private fun chat(id: Long, kind: ChatKind = ChatKind.Channel, folders: List<Int> = emptyList(), archived: Boolean = false) =
        ChatSummary(id, "Chat $id", null, 0, kind, isArchived = archived, folderIds = folders)

    private val folders = listOf(ChatFolderSummary(3, "Films"), ChatFolderSummary(7, "Family"))

    @Test
    fun `hiding keeps Chats while there are folders, and Chats then opens the first folder`() {
        val shown = browseSections(folders)
        val hidden = browseSections(folders, hideDefaultGroups = true)
        assertEquals(shown, hidden)
        assertFalse(BrowseSection.of(BrowseTab.Saved) in shown)
        assertEquals(BrowseSection.Folder(3, "Films"), chatsDestination(hidden, hideDefaultGroups = true))
        assertEquals(BrowseSection.of(BrowseTab.Chats), chatsDestination(shown, hideDefaultGroups = false))
    }

    @Test
    fun `unfolding the folders leaves the other folds alone`() {
        NavGroupState.reset()
        NavGroupState.unfold(NavGroup.Folders)
        assertEquals(setOf(NavGroup.Watch, NavGroup.Folders), NavGroupState.open.value)
        NavGroupState.unfold(NavGroup.Folders)
        assertEquals(setOf(NavGroup.Watch, NavGroup.Folders), NavGroupState.open.value)
        NavGroupState.reset()
    }

    @Test
    fun `hidden with no folders, only Watch is left`() {
        val bare = navGroups(browseSections(emptyList(), hideDefaultGroups = true))
        assertEquals(
            listOf(
                NavEntry.Section(BrowseSection.of(BrowseTab.Home)),
                NavEntry.Section(BrowseSection.of(BrowseTab.History)),
                NavEntry.Section(BrowseSection.of(BrowseTab.Favorites)),
                NavEntry.Downloads,
            ),
            bare.single().entries,
        )
    }

    @Test
    fun `a remembered default section lands on Home only while hidden`() {
        val home = BrowseSection.of(BrowseTab.Home)
        assertEquals(home, DefaultGroups.reachable(BrowseSection.of(BrowseTab.Chats), hidden = true))
        assertEquals(home, DefaultGroups.reachable(BrowseSection.of(BrowseTab.Saved), hidden = true))
        assertEquals(BrowseSection.of(BrowseTab.Saved), DefaultGroups.reachable(BrowseSection.of(BrowseTab.Saved), hidden = false))
        val folder = BrowseSection.Folder(3, "Films")
        assertEquals(folder, DefaultGroups.reachable(folder, hidden = true))
        assertEquals(BrowseSection.of(BrowseTab.Favorites), DefaultGroups.reachable(BrowseSection.of(BrowseTab.Favorites), hidden = true))
    }

    @Test
    fun `unreachable favourites are the starred chats in no folder, and archived ones`() {
        val chats = listOf(
            chat(1, folders = listOf(3)),
            chat(2),
            chat(3, ChatKind.Saved),
            chat(4, folders = listOf(3, 7), archived = true),
            chat(5, folders = listOf(7)),
            chat(6),
        )
        val favourites = setOf(1L, 2L, 3L, 4L, 99L)
        // 1 is in a folder; 2 and 3 are in none; 4 is archived, which a folder's list leaves out;
        // 5 and 6 are not starred; 99 is starred but not in the list, so it is not counted.
        assertEquals(setOf(2L, 3L, 4L), DefaultGroups.unreachableFavorites(favourites, chats))
        assertEquals(emptySet<Long>(), DefaultGroups.unreachableFavorites(emptySet(), chats))
        assertEquals(emptySet<Long>(), DefaultGroups.unreachableFavorites(setOf(1L, 5L), chats))
    }

    @Test
    fun `the prompt names the count only when favourites go, and says to make folders when there are none`() {
        val none = DefaultGroups.prompt(unreachable = 0, folderCount = 2)
        val some = DefaultGroups.prompt(unreachable = 3, folderCount = 2)
        val bare = DefaultGroups.prompt(unreachable = 0, folderCount = 0)
        assertFalse(none.detail.contains("3"))
        assertTrue(some.detail.contains("3"))
        assertTrue(some.detail.length > none.detail.length)
        assertTrue(bare.message.length > none.message.length)
    }

    @Test
    fun `first run Home sends the viewer to Saved Messages, the first folder, or nowhere`() {
        assertEquals(BrowseSection.of(BrowseTab.Saved), DefaultGroups.firstStep(hidden = false, folders).target)
        assertEquals(BrowseSection.Folder(3, "Films"), DefaultGroups.firstStep(hidden = true, folders).target)
        val bare = DefaultGroups.firstStep(hidden = true, emptyList())
        assertNull(bare.target)
        assertNull(bare.button)
    }

    @Test
    fun `turning the option on unstars the stranded favourites in the same write, off brings nothing back`() = runBlocking {
        val dir = Files.createTempDirectory("groups")
        try {
            val store = SettingsStore(SettingsStore.openDataStore(dir.toFile().resolve(SettingsStore.FILE_NAME)))
            assertFalse(store.hideDefaultGroups.first())
            store.setChatFilter(ChatFilter.Channels.name)
            listOf(1L, 2L, 3L).forEach { store.toggleFavorite(it) }
            store.setHideDefaultGroups(true, unstar = setOf(2L, 3L))
            assertTrue(store.hideDefaultGroups.first())
            assertEquals(setOf(1L), store.favorites.first())
            store.setHideDefaultGroups(false)
            assertFalse(store.hideDefaultGroups.first())
            assertEquals(setOf(1L), store.favorites.first())
            // The chip over Chats is kept while hidden, so it is the same one when Chats is back.
            assertEquals(ChatFilter.Channels, ChatFilter.decode(store.chatFilter.first()))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
