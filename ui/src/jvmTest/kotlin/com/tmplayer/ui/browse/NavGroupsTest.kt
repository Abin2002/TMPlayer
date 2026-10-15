package com.tmplayer.ui.browse

import com.tmplayer.data.ChatFolderSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavGroupsTest {

    private fun tab(tab: BrowseTab) = NavEntry.Section(BrowseSection.of(tab))

    @Test
    fun `every section sits in the one group, a folder too`() {
        for (entry in BrowseTab.entries) assertEquals(entry.name, NavGroup.Watch, navGroupOf(BrowseSection.of(entry)))
        assertEquals(NavGroup.Watch, navGroupOf(BrowseSection.Folder(3, "Films")))
        assertEquals(NavGroup.Watch, navGroupOf(NavEntry.Downloads))
    }

    @Test
    fun `five entries, Home, History, Favourites, Chats and Downloads, and no Saved Messages`() {
        val groups = navGroups(browseSections(emptyList()))
        assertEquals(listOf(NavGroup.Watch), groups.map { it.group })
        assertEquals(
            listOf(tab(BrowseTab.Home), tab(BrowseTab.History), tab(BrowseTab.Favorites), tab(BrowseTab.Chats), NavEntry.Downloads),
            groups.single().entries,
        )
    }

    @Test
    fun `badges count favourites on Favourites and unread chats on Chats, nothing elsewhere`() {
        assertEquals("3", navBadge(BrowseSection.of(BrowseTab.Favorites), favoriteCount = 3, unreadChats = 5))
        assertEquals("5", navBadge(BrowseSection.of(BrowseTab.Chats), favoriteCount = 3, unreadChats = 5))
        assertEquals(null, navBadge(BrowseSection.of(BrowseTab.Chats), favoriteCount = 3, unreadChats = 0))
        assertEquals(null, navBadge(BrowseSection.of(BrowseTab.Saved), favoriteCount = 3, unreadChats = 5))
    }

    @Test
    fun `folders are chips on the Chats page, not sidebar entries`() {
        val sections = browseSections(listOf(ChatFolderSummary(3, "Films"), ChatFolderSummary(7, "Family")))
        val listed = navGroups(sections).flatMap { it.entries }.filterIsInstance<NavEntry.Section>().map { it.section }
        assertFalse(listed.any { it is BrowseSection.Folder })
        val chips = chatPageChips(listOf(ChatFolderSummary(3, "Films"), ChatFolderSummary(7, "Family")))
        assertEquals(ChatPageChip.Filter(ChatFilter.All), chips.first())
        assertEquals(listOf("Films", "Family"), chips.filterIsInstance<ChatPageChip.Folder>().map { it.label })
        val films = BrowseSection.Folder(3, "Films")
        assertEquals(BrowseSection.of(BrowseTab.Chats), films.navSection)
        assertTrue(chips.last { it is ChatPageChip.Folder && it.folder.id == 3 }.isSelected(films, ChatFilter.All))
        assertFalse(ChatPageChip.Filter(ChatFilter.All).isSelected(films, ChatFilter.All))
    }

    @Test
    fun `Downloads can be left out`() {
        val groups = navGroups(browseSections(emptyList()), withDownloads = false)
        assertFalse(NavEntry.Downloads in groups[0].entries)
        assertEquals(listOf(tab(BrowseTab.Home), tab(BrowseTab.History), tab(BrowseTab.Favorites), tab(BrowseTab.Chats)), groups[0].entries)
    }

    @Test
    fun `Watch starts open and the rest closed`() {
        NavGroupState.reset()
        val open = NavGroupState.open.value
        assertEquals(setOf(NavGroup.Watch), open)
        assertTrue(NavGroupState.isOpen(NavGroup.Watch, open, current = null))
        assertFalse(NavGroupState.isOpen(NavGroup.Chats, open, current = null))
        assertFalse(NavGroupState.isOpen(NavGroup.Folders, open, current = null))
    }

    @Test
    fun `the current group is always open and cannot be folded`() {
        val allClosed = emptySet<NavGroup>()
        for (group in NavGroup.entries) {
            assertTrue(NavGroupState.isOpen(group, allClosed, current = group))
            assertFalse(NavGroupState.canToggle(group, current = group))
        }
        assertTrue(NavGroupState.canToggle(NavGroup.Chats, current = NavGroup.Watch))
        assertFalse(NavGroupState.isOpen(NavGroup.Chats, allClosed, current = NavGroup.Watch))
    }

    @Test
    fun `Watch has no heading, so it is always open and never folds`() {
        assertFalse(NavGroup.Watch.headed)
        assertTrue(NavGroup.Chats.headed && NavGroup.Folders.headed)
        val allClosed = emptySet<NavGroup>()
        assertTrue(NavGroupState.isOpen(NavGroup.Watch, allClosed, current = NavGroup.Chats))
        assertTrue(NavGroupState.isOpen(NavGroup.Watch, allClosed, current = null))
        assertFalse(NavGroupState.canToggle(NavGroup.Watch, current = NavGroup.Chats))
    }

    @Test
    fun `folds last for the run and a launch starts folded again`() {
        NavGroupState.reset()
        NavGroupState.toggle(NavGroup.Chats)
        assertEquals(setOf(NavGroup.Watch, NavGroup.Chats), NavGroupState.open.value)
        NavGroupState.toggle(NavGroup.Chats)
        assertEquals(setOf(NavGroup.Watch), NavGroupState.open.value)

        NavGroupState.toggle(NavGroup.Folders)
        NavGroupState.reset()
        assertEquals(NavGroupState.DEFAULT_OPEN, NavGroupState.open.value)
    }

    @Test
    fun `the chips follow Telegram's tab bar, then the filters no folder already is`() {
        val folders = listOf(
            ChatFolderSummary(1, "Unread", icon = "Unread"),
            ChatFolderSummary(2, "Films", icon = "Custom"),
            ChatFolderSummary(3, "Channels", icon = "Channels"),
            ChatFolderSummary(4, "Bots", icon = "Bots"),
        )
        // "All chats" where Telegram puts it: second here, as a Premium account may have moved it.
        val chips = chatPageChips(folders, mainListPosition = 1)
        assertEquals(listOf("Unread", "All chats", "Films", "Channels", "Bots"), chips.take(5).map { it.label })
        // No second Unread or Channels; Groups, People and Archived are TMPlayer's own.
        assertEquals(
            listOf(ChatFilter.Groups, ChatFilter.People, ChatFilter.Archived),
            chips.drop(5).map { (it as ChatPageChip.Filter).filter },
        )
        assertEquals(com.tmplayer.ui.icons.TmIcons.Unread, chips.first().icon)
        // A position past the end is the end, and no folders is "All chats" and every filter.
        assertEquals(ChatPageChip.Filter(ChatFilter.All), chatPageChips(folders, mainListPosition = 99)[4])
        assertEquals(ChatFilter.entries.map { ChatPageChip.Filter(it) }, chatPageChips(emptyList()))
    }

    @Test
    fun `a folder named like a filter stands for it, by name too`() {
        val chips = chatPageChips(listOf(ChatFolderSummary(5, "groups", icon = "Custom")))
        assertFalse(chips.any { it == ChatPageChip.Filter(ChatFilter.Groups) })
    }
}
